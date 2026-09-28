#!/usr/bin/env python3
"""
Phase 5 — shared demand-forecasting implementation.

Single source of truth for month arithmetic, the monthly demand grid, the
baseline forecast methods, and forecast accuracy metrics. Phase 5A, Phase 5B
and the Phase 5 test suite all import from this module, so the code under test
is the code that ships.

This module is deliberately import-safe: it never imports a database driver at
import time, so the unit tests run with no MySQL and no third-party packages.

EVALUATION PROTOCOLS
--------------------
Two leakage-free protocols are supported. Both forbid a forecast for month t
from using any observation of month t or later.

``static-holdout`` (the project's documented protocol)
    The information set is frozen at ``origin`` (the last training month). Every
    forecast in the horizon sees exactly the same observations: months <= origin.
    This is the protocol named in docs/CURRENT_STATE.md and required by
    scripts/dev-realdata/ML_POPULATION_DEFINITION.md ("Train on earlier loans,
    validate on later loans. Never the reverse.").

``rolling-origin``
    The forecast for month t sees every observation of months < t. The window
    slides forward as the horizon advances, which is the operational setting in
    which a trailing moving average is actually deployed (the library observes
    last month before forecasting next month).

Why the moving average needs an explicit protocol
-------------------------------------------------
A trailing W-month mean is a *sliding* statistic. Under a frozen-origin holdout
with a horizon longer than W, the "W months before month t" run off the end of
the training data. The earlier Phase 5B implementation silently discarded the
months it could not see and, when none were left, substituted a mean over the
whole 36-month training history. That made the reported "3-month MA" and
"6-month MA" metrics measure a 36-month mean for most of the horizon, so the
three baselines were not comparable.

``trailing_mean`` therefore fixes the anchor rather than the window:

* ``static-holdout`` anchors the window at ``origin`` for every target month,
  so the estimate is the level of the last W observed months, held constant
  across the horizon, with full window coverage everywhere.
* ``rolling-origin`` anchors the window at the target month, so the window
  slides and the method is a true moving average.

Every forecast point records which source produced it and how much of the
requested window was actually observed, so a partial or substituted forecast
can never again pass silently.
"""
import math
from collections import defaultdict

# Category fallback for items whose catalog category is NULL, and for item
# types that have no category column at all (newspapers).
UNCATEGORIZED = "UNCATEGORIZED"

# Seasonal naive uses a single observation from 12 months earlier.
SEASONAL_LAG = 12

# Trailing moving-average windows evaluated by Phase 5B.
MOVING_AVERAGE_WINDOWS = (3, 6)

STATIC_HOLDOUT = "static-holdout"
ROLLING_ORIGIN = "rolling-origin"
PROTOCOLS = (STATIC_HOLDOUT, ROLLING_ORIGIN)
METHODS = ("seasonal_naive", "trailing_mean")

# Forecast source labels, reported per point so a substituted forecast is visible.
SOURCE_SEASONAL = "seasonal_lookup"
SOURCE_TRAILING = "trailing_window"
SOURCE_TRAILING_PARTIAL = "trailing_window_partial"
SOURCE_SERIES_MEAN = "series_mean_fallback"
SOURCE_ZERO = "zero_fallback"


# --------------------------------------------------------------------------
# Month arithmetic
# --------------------------------------------------------------------------

def month_key(value):
    """Format a date as a ``YYYY-MM`` month key."""
    return f"{value.year:04d}-{value.month:02d}"


def parse_month(key):
    """Split a ``YYYY-MM`` month key into ``(year, month)``."""
    year, month = key.split("-")
    return int(year), int(month)


def shift_month(key, offset):
    """
    Shift a ``YYYY-MM`` month key by ``offset`` months.

    Negative offsets move backwards. January/December wrap across the year
    boundary, which is what makes the t-12 seasonal lookup and the year-spanning
    moving-average windows correct.
    """
    year, month = parse_month(key)
    total = year * 12 + (month - 1) + offset
    return f"{total // 12:04d}-{total % 12 + 1:02d}"


def month_sequence(start_key, end_key):
    """Inclusive list of month keys from ``start_key`` to ``end_key``."""
    start_year, start_month = parse_month(start_key)
    end_year, end_month = parse_month(end_key)
    total_start = start_year * 12 + (start_month - 1)
    total_end = end_year * 12 + (end_month - 1)
    if total_end < total_start:
        return []
    return [f"{t // 12:04d}-{t % 12 + 1:02d}" for t in range(total_start, total_end + 1)]


def compare_months(left, right):
    """Return -1/0/1 comparing two ``YYYY-MM`` month keys."""
    left_total = parse_month(left)[0] * 12 + parse_month(left)[1] - 1
    right_total = parse_month(right)[0] * 12 + parse_month(right)[1] - 1
    return (left_total > right_total) - (left_total < right_total)


# --------------------------------------------------------------------------
# Record classification and the monthly demand grid
# --------------------------------------------------------------------------

def series_key(item_type, category):
    """Composite key identifying one demand series."""
    return f"{item_type}|{category}"


def classify_borrow_row(borrow_date, book_id=None, magazine_id=None, newspaper_id=None,
                        book_category=None, magazine_category=None):
    """
    Map one ``borrow_records`` row to a demand record, or ``None`` to skip it.

    Demand target: all borrow events, counted by ``borrow_date`` regardless of
    return status, so an active loan contributes to its borrow month.

    The item type is resolved from the polymorphic foreign keys. A missing
    catalog category (NULL in ``books.category`` / ``magazines.category``) falls
    back to UNCATEGORIZED, as does every newspaper, because the production
    schema has no newspaper category column.
    """
    if book_id is not None:
        item_type = "BOOK"
        category = book_category if book_category else UNCATEGORIZED
    elif magazine_id is not None:
        item_type = "MAGAZINE"
        category = magazine_category if magazine_category else UNCATEGORIZED
    elif newspaper_id is not None:
        item_type = "NEWSPAPER"
        category = UNCATEGORIZED
    else:
        return None

    return {
        "borrow_date": borrow_date,
        "item_type": item_type,
        "category": category,
    }


def aggregate_monthly_demand(records):
    """Aggregate demand records into ``(month, series) -> count`` rows."""
    counts = defaultdict(lambda: defaultdict(int))
    for record in records:
        key = month_key(record["borrow_date"])
        counts[key][series_key(record["item_type"], record["category"])] += 1

    result = []
    for key in sorted(counts):
        for sk in sorted(counts[key]):
            item_type, category = sk.split("|", 1)
            result.append({
                "month": key,
                "item_type": item_type,
                "category": category,
                "demand": counts[key][sk],
            })
    return result


def observed_month_range(records):
    """
    Return ``(first_month, last_month)`` actually present in the source records.

    Computed from raw borrow events, *before* the grid is densified, so that
    months the generator never populated are distinguishable from months with
    genuinely zero demand.
    """
    months = sorted({month_key(r["borrow_date"]) for r in records})
    if not months:
        return None, None
    return months[0], months[-1]


def filter_records_to_range(records, first_month, last_month):
    """
    Drop borrow records whose month falls outside ``[first_month, last_month]``.

    ``resolve_grid_bounds`` bounds the *month* axis of the grid, but
    densification derives its series keys from every row it is handed. A
    category that appears only outside the resolved window would therefore be
    zero-filled across the whole grid and then scored as a real, all-zero
    series, silently inflating both the series count and the number of forecast
    points.

    Clipping only ever removes rows. It cannot manufacture an observation, and
    it cannot turn an unobserved month into observed zero demand: the caller
    still passes the resolved bounds to the densifier, so months the generator
    never emitted stay absent rather than becoming zeros.
    """
    return [
        record
        for record in records
        if compare_months(month_key(record["borrow_date"]), first_month) >= 0
        and compare_months(month_key(record["borrow_date"]), last_month) <= 0
    ]


def densify_monthly_grid(demand_data, first_month, last_month):
    """
    Extend the grid so every series has a row for every month in the range.

    Zero-demand months are real information (a category can be idle in a month)
    and must stay in the grid, but only across months the source data actually
    covers. Months past the observed range are not created: a month the data
    generator never emitted is a missing observation, not a zero.
    """
    all_months = month_sequence(first_month, last_month)

    series_keys = {series_key(r["item_type"], r["category"]) for r in demand_data}
    existing = {(r["month"], series_key(r["item_type"], r["category"])) for r in demand_data}

    result = list(demand_data)
    for key in all_months:
        for sk in sorted(series_keys):
            if (key, sk) not in existing:
                item_type, category = sk.split("|", 1)
                result.append({
                    "month": key,
                    "item_type": item_type,
                    "category": category,
                    "demand": 0,
                })

    return sorted(result, key=lambda r: (r["month"], r["item_type"], r["category"]))


# --------------------------------------------------------------------------
# Information sets
# --------------------------------------------------------------------------

def build_history(demand_data):
    """
    Build the ``(series, month) -> demand`` lookup.

    The month is part of the key. A series-only key lets each training month
    overwrite the previous one, which destroyed the seasonal-naive baseline in
    the original Phase 5A implementation.
    """
    return {
        (series_key(r["item_type"], r["category"]), r["month"]): r["demand"]
        for r in demand_data
    }


def split_history(history, target_month, origin, protocol):
    """
    Restrict the lookup to the observations a forecaster may legally see.

    ``static-holdout`` keeps only months <= origin. ``rolling-origin`` keeps
    only months < target_month. Neither can ever see the target month or any
    later month, so a forecast cannot leak its own outcome.
    """
    if protocol == STATIC_HOLDOUT:
        limit, inclusive = origin, True
    elif protocol == ROLLING_ORIGIN:
        limit, inclusive = target_month, False
    else:
        raise ValueError(f"unknown protocol: {protocol!r}")

    if inclusive:
        return {k: v for k, v in history.items() if compare_months(k[1], limit) <= 0}
    return {k: v for k, v in history.items() if compare_months(k[1], limit) < 0}


def series_mean(visible_history, series):
    """Mean of all visible observations for one series, or 0.0 if none exist."""
    values = [v for (s, _m), v in visible_history.items() if s == series]
    if not values:
        return 0.0
    return sum(values) / len(values)


# --------------------------------------------------------------------------
# Baseline forecast methods
# --------------------------------------------------------------------------

def forecast_point(history, series, target_month, origin, protocol, method, window=None):
    """
    Produce one forecast plus the diagnostics needed to audit it.

    Returns a dict with ``forecast``, ``source``, ``window_requested``,
    ``window_covered`` and ``window_start``/``window_end``. ``source`` names the
    code path that produced the number, so a substituted forecast is visible in
    the output instead of blending into the metrics.
    """
    visible = split_history(history, target_month, origin, protocol)

    if method == "seasonal_naive":
        # A single observation, the same calendar month one year earlier.
        # Note this is a point lookup, not a 12-month average.
        required = [shift_month(target_month, -SEASONAL_LAG)]
    elif method == "trailing_mean":
        if not window or window < 1:
            raise ValueError("trailing_mean requires a positive window_months")
        # The window is the W months ending at `anchor`, which is always strictly
        # before the target month so the target can never enter its own window.
        # Under a frozen origin the anchor is the origin, so the window holds
        # still across the horizon instead of sliding into the holdout; under
        # rolling-origin it is the last visible month, so the window slides.
        anchor = origin if protocol == STATIC_HOLDOUT else shift_month(target_month, -1)
        required = [shift_month(anchor, -offset) for offset in range(window - 1, -1, -1)]
    else:
        raise ValueError(f"unknown method: {method!r}")

    window_requested = len(required)
    # Structural leakage guard: whatever the protocol or anchoring, a forecast
    # may never be built from the month it is predicting or from any later month.
    for month in required:
        if compare_months(month, target_month) >= 0:
            raise ValueError(
                f"{method} window for {target_month} would read {month}, "
                "which is not strictly in the past"
            )

    observed = [visible[(series, m)] for m in required if (series, m) in visible]
    covered = len(observed)

    source = SOURCE_SEASONAL if method == "seasonal_naive" else SOURCE_TRAILING
    point = {
        "window_requested": window_requested,
        "window_covered": covered,
        "window_start": required[0],
        "window_end": required[-1],
    }

    if covered == window_requested:
        point["forecast"] = sum(observed) / covered
        point["source"] = source
        return point

    if covered > 0:
        # Some but not all of the window is visible. Averaging the observed
        # months beats discarding them, but the shortfall is recorded so a
        # partial window can never be mistaken for a full one.
        point["forecast"] = sum(observed) / covered
        point["source"] = SOURCE_TRAILING_PARTIAL
        return point

    # No window month is visible at all. Fall back to the mean of the series over
    # whatever history is visible, which is the only information left.
    values = [v for (s, _m), v in visible.items() if s == series]
    if values:
        point["forecast"] = sum(values) / len(values)
        point["source"] = SOURCE_SERIES_MEAN
        return point

    point["forecast"] = 0.0
    point["source"] = SOURCE_ZERO
    return point


def forecast_all(demand_data, test_months, origin, protocol, method, window=None, label=None):
    """
    Forecast every series for every month in ``test_months``.

    ``demand_data`` is the full grid; only the information set defined by
    ``protocol``/``origin`` is ever consulted, so passing the whole grid in does
    not leak test-period data.
    """
    if protocol not in PROTOCOLS:
        raise ValueError(f"unknown protocol: {protocol!r}")
    # Validate the request before the empty-horizon shortcut below, so a bad
    # method or window is still rejected rather than being masked by it.
    if method not in METHODS:
        raise ValueError(f"unknown method: {method!r}")
    if method == "trailing_mean" and (not window or window < 1):
        raise ValueError("trailing_mean requires a positive window_months")
    for record in test_months:
        if compare_months(record["month"], origin) <= 0:
            raise ValueError(
                f"forecast month {record['month']} is not after the origin {origin}; "
                "a holdout may only score months beyond the training data"
            )

    if not test_months:
        # An empty horizon has nothing to score. Return an empty result so the
        # caller does not have to guard its own indexing against it.
        return []

    if method == "seasonal_naive":
        # seasonal_naive reads a single observation SEASONAL_LAG months before
        # each target. That month is only in the information set if it does not
        # lie past this protocol's boundary: the origin under a frozen holdout,
        # the prior month under rolling origin. Past that point the method would
        # silently change itself into a series mean, so refuse the horizon
        # instead -- scoring a different estimator under the method's name is
        # exactly the failure the per-point audit columns exist to prevent.
        for record in test_months:
            lag_month = shift_month(record["month"], -SEASONAL_LAG)
            limit = origin if protocol == STATIC_HOLDOUT else shift_month(record["month"], -1)
            if compare_months(lag_month, limit) > 0:
                raise ValueError(
                    f"seasonal_naive cannot forecast {record['month']} under "
                    f"{protocol}: it needs the observation for {lag_month}, which is "
                    f"after the {protocol} information boundary {limit}. Use the "
                    f"{ROLLING_ORIGIN} protocol or a horizon within "
                    f"{SEASONAL_LAG} months of the origin."
                )

    history = build_history(demand_data)
    forecasts = []

    for record in test_months:
        series = series_key(record["item_type"], record["category"])
        point = forecast_point(
            history, series, record["month"], origin, protocol, method, window
        )
        row = {
            "month": record["month"],
            "item_type": record["item_type"],
            "category": record["category"],
            "actual": record["demand"],
            "forecast": point["forecast"],
            "method": label or method,
            "protocol": protocol,
            "source": point["source"],
            "window_requested": point["window_requested"],
            "window_covered": point["window_covered"],
            "window_start": point["window_start"],
            "window_end": point["window_end"],
        }
        for extra in ("regime",):
            if extra in record:
                row[extra] = record[extra]
        forecasts.append(row)

    return forecasts


# --------------------------------------------------------------------------
# Synthetic academic-calendar regimes
# --------------------------------------------------------------------------

def calendar_regime(key):
    """
    Synthetic academic-calendar regime for a month, matching
    ``semester_factor()`` in seed_generator.py.

    BREAK: months 1, 2, 7, 8 (40% reduction). NORMAL: months 3, 4, 5, 9, 10, 11.
    REDUCED: months 6, 12 (25% reduction).

    CAVEAT: these regimes are a synthetic generator assumption used for
    development analysis only. They are not real academic-calendar data.
    """
    _year, month = parse_month(key)
    if month in (1, 2, 7, 8):
        return "BREAK"
    if month in (3, 4, 5, 9, 10, 11):
        return "NORMAL"
    return "REDUCED"


def add_calendar_regime(demand_data):
    """Attach the synthetic regime label to each demand record."""
    return [dict(r, regime=calendar_regime(r["month"])) for r in demand_data]


# --------------------------------------------------------------------------
# Metrics
# --------------------------------------------------------------------------

def metrics(forecasts):
    """MAE, RMSE and point count over forecasts of ``actual`` vs ``forecast``."""
    n = len(forecasts)
    if n == 0:
        return {"mae": 0.0, "rmse": 0.0, "n": 0}

    sum_abs = 0
    sum_sq = 0
    for f in forecasts:
        error = f["actual"] - f["forecast"]
        sum_abs += abs(error)
        sum_sq += error * error

    return {"mae": sum_abs / n, "rmse": math.sqrt(sum_sq / n), "n": n}


def metrics_by(forecasts, field):
    """Group forecasts by a field (``regime``, ``item_type``, ``source`` ...) and
    compute metrics per group."""
    groups = defaultdict(list)
    for f in forecasts:
        groups[f[field]].append(f)
    return {k: metrics(v) for k, v in groups.items()}


def coverage(forecasts):
    """Summarise how much of the requested window was actually observed."""
    n = len(forecasts)
    if n == 0:
        return {
            "points": 0,
            "full_window": 0,
            "partial_window": 0,
            "fallback": 0,
            "full_window_pct": 0.0,
        }
    full = sum(1 for f in forecasts if f["window_covered"] == f["window_requested"])
    partial = sum(
        1
        for f in forecasts
        if 0 < f["window_covered"] < f["window_requested"]
    )
    fallback = sum(1 for f in forecasts if f["source"] in (SOURCE_SERIES_MEAN, SOURCE_ZERO))
    return {
        "points": n,
        "full_window": full,
        "partial_window": partial,
        "fallback": fallback,
        "full_window_pct": 100.0 * full / n,
    }
