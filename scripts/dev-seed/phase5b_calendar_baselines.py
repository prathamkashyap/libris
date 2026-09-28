#!/usr/bin/env python3
"""
Phase 5B — Monthly Demand Forecasting with Academic-Calendar Baselines

Offline synthetic-data development prototype for Libris demand forecasting.
Extends Phase 5A with trailing moving-average baselines and synthetic
academic-calendar regime analysis.

All forecasting logic lives in phase5_forecasting.py, which is the single source
of truth shared with Phase 5A and the Phase 5 test suite.

EVALUATION PROTOCOL
-------------------
Both baselines are evaluated under two explicit protocols, because a trailing
moving average is a sliding statistic and a frozen-origin holdout cannot express
one when the forecast horizon exceeds the window:

* ``static-holdout`` (primary, the project's documented protocol): the
  information set is frozen at the origin. The W-month window is anchored at the
  origin, so every forecast in the horizon uses the last W observed months with
  full coverage.
* ``rolling-origin``: forecast(t) uses only months < t, and the window slides
  forward, which is the operational setting for a moving average.

The earlier implementation let the W-month window slide off the end of the
training data and silently substituted a mean over the whole training history
when no window month was visible. That made "3-month MA" and "6-month MA" measure
a 36-month mean for most of the horizon. Anchoring the window restores the
baseline to its definition; the per-point ``source``/``window_covered`` columns
make any future substitution visible instead of silent.

This is a methodology-development prototype, NOT a production recommendation
system. Calendar regimes are SYNTHETIC, based on semester_factor() in
seed_generator.py, and do not represent real academic calendar data.
"""
import os
import csv
import sys
from datetime import date, datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from phase5_forecasting import (  # noqa: E402
    MOVING_AVERAGE_WINDOWS,
    ROLLING_ORIGIN,
    SEASONAL_LAG,
    STATIC_HOLDOUT,
    UNCATEGORIZED,
    aggregate_monthly_demand,
    add_calendar_regime,
    coverage,
    densify_monthly_grid,
    filter_records_to_range,
    forecast_all,
    metrics,
    metrics_by,
    month_key,
)
from phase5a_demand_aggregation import (  # noqa: E402
    load_borrow_data,
    resolve_grid_bounds,
    split_train_test,
)

# Database configuration
DB_HOST = "127.0.0.1"
DB_PORT = 3306
DB_USER = "root"
DB_PASS = os.environ.get("LMS_DB_PASSWORD", "")
DB_NAME = os.environ.get("LMS_DB_NAME", "libris_ml_dev")

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")

# Temporal boundaries (same as Phase 5A)
DATE_START = date(2023, 1, 1)
DATE_END = date(2026, 8, 31)
TRAIN_END = date(2025, 12, 31)
TEST_START = date(2026, 1, 1)


REGIME_ORDER = ("BREAK", "NORMAL", "REDUCED")
ITEM_TYPE_ORDER = ("BOOK", "MAGAZINE", "NEWSPAPER")


def connect():
    import pymysql
    return pymysql.connect(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USER,
        password=DB_PASS,
        database=DB_NAME,
        autocommit=False,
    )


def baseline_label(method, window):
    """Human-readable baseline name used in reports and output filenames."""
    if method == "seasonal_naive":
        return "Seasonal Naive"
    return f"{window}-Month MA"


def run_baselines(demand_data, test_data, origin, protocol):
    """
    Run all three baselines under one protocol.

    Returns ``(ordered_results, results_by_label)`` where each result carries the
    forecast rows plus overall metrics and window coverage.
    """
    specs = [("seasonal_naive", None, baseline_label("seasonal_naive", None))]
    specs += [
        ("trailing_mean", w, baseline_label("trailing_mean", w))
        for w in MOVING_AVERAGE_WINDOWS
    ]

    ordered = []
    by_label = {}
    for method, window, label in specs:
        forecasts = forecast_all(
            demand_data, test_data, origin, protocol, method, window=window, label=label
        )
        result = {
            "label": label,
            "method": method,
            "window": window,
            "protocol": protocol,
            "forecasts": forecasts,
            "metrics": metrics(forecasts),
            "coverage": coverage(forecasts),
        }
        ordered.append(result)
        by_label[label] = result
    return ordered, by_label


def generate_report(context, results_by_protocol, regime_demand):
    """Generate the development report."""
    report = []
    report.append("=" * 70)
    report.append("  PHASE 5B — MONTHLY DEMAND FORECASTING WITH CALENDAR BASELINES")
    report.append("=" * 70)
    report.append(f"  Run at: {datetime.now().isoformat()}")
    report.append("")

    report.append("  DATASET")
    report.append("  " + "-" * 50)
    report.append(f"  Configured range: {DATE_START} to {DATE_END}")
    report.append(f"  Effective grid: {context['first_month']} to {context['last_month']}")
    report.append(f"  Total monthly observations: {context['total_observations']}")
    report.append(f"  Unique series (item_type|category): {context['series_count']}")
    report.append(f"  Series by item_type: {context['series_by_item_type']}")
    uncategorized = [s for s in context['series_keys'] if s.endswith("|" + UNCATEGORIZED)]
    report.append(f"  UNCATEGORIZED series: {len(uncategorized)} {sorted(uncategorized)}")

    if context["truncated_months"]:
        report.append(
            f"  NOTE: source data ends {context['last_month']}; configured months"
            f" {context['truncated_months']} excluded as unobserved"
        )

    report.append("")
    report.append("  EVALUATION PROTOCOLS")
    report.append("  " + "-" * 50)
    report.append(f"  Origin (last training month): {context['origin']}")
    report.append(f"  Training window: {context['first_month']} to {context['origin']}")
    report.append(f"  Forecast horizon: {context['horizon_start']} to {context['last_month']}")
    report.append(f"  Train observations: {context['train_count']}")
    report.append(f"  Test observations: {context['test_count']}")
    report.append("  1. Static holdout (primary): information set frozen at the origin.")
    report.append("     forecast(t) may use only months <= origin.")
    report.append("  2. Rolling origin: forecast(t) may use only months < t.")
    report.append("  Neither protocol can use the target month or any later month.")
    report.append("")
    report.append("  Moving-average anchoring under each protocol:")
    report.append(f"    static holdout: window anchored at {context['origin']}, held constant")
    report.append("                    across the horizon, full coverage at every point")
    report.append("    rolling origin: window anchored at the target month and slides")
    report.append("                    forward, full coverage at every point")
    report.append("  Rationale: a trailing W-month mean is a sliding statistic. With a frozen")
    report.append("  origin and a horizon longer than W, 'the W months before t' runs off the end")
    report.append("  of the training data. Anchoring the window keeps each baseline equal to its")
    report.append("  own definition for the whole horizon instead of degrading into a mean over")
    report.append("  the full training history.")

    report.append("")
    report.append("  SYNTHETIC ACADEMIC CALENDAR REGIMES")
    report.append("  " + "-" * 50)
    report.append("  Based on semester_factor() from seed_generator.py")
    report.append("  BREAK: months 1,2,7,8 (40% demand reduction)")
    report.append("  NORMAL: months 3,4,5,9,10,11 (no reduction)")
    report.append("  REDUCED: months 6,12 (25% demand reduction)")
    report.append(f"  Total demand by regime: {dict(regime_demand)}")

    report.append("")
    report.append("  BASELINE METHODS")
    report.append("  " + "-" * 50)
    report.append(f"  1. Seasonal naive: forecast(t) = demand(t - {SEASONAL_LAG} months)")
    for index, w in enumerate(MOVING_AVERAGE_WINDOWS, start=2):
        report.append(f"  {index}. {w}-month MA: forecast(t) = mean of the {w} months in the anchored window")
    report.append("  Fallback (both): mean of the series over visible history if the window")
    report.append("  cannot be filled; each point records whether this happened")

    for protocol, results in results_by_protocol:
        report.append("")
        title = "PRIMARY" if protocol == STATIC_HOLDOUT else "SECONDARY"
        report.append(f"  FORECAST METRICS — {protocol.upper()} ({title})")
        report.append("  " + "-" * 50)
        for result in results:
            m = result["metrics"]
            report.append(
                f"  {result['label']}: MAE {m['mae']:.4f} | RMSE {m['rmse']:.4f} | points {m['n']}"
            )

        report.append("")
        report.append(f"  WINDOW COVERAGE — {protocol.upper()}")
        report.append("  " + "-" * 50)
        for result in results:
            c = result["coverage"]
            report.append(
                f"  {result['label']}: full {c['full_window']}/{c['points']}"
                f" ({c['full_window_pct']:.1f}%) | partial {c['partial_window']}"
                f" | fallbacks {c['fallback']}"
            )

    primary = dict(results_by_protocol)[STATIC_HOLDOUT]
    report.append("")
    report.append("  METRICS BY CALENDAR REGIME (static holdout, primary)")
    report.append("  " + "-" * 50)
    for result in primary:
        by_regime = metrics_by(result["forecasts"], "regime")
        parts = [
            f"{regime} MAE={by_regime[regime]['mae']:.4f} RMSE={by_regime[regime]['rmse']:.4f}"
            f" n={by_regime[regime]['n']}"
            for regime in REGIME_ORDER
            if regime in by_regime
        ]
        report.append(f"  {result['label']}: " + " | ".join(parts))

    report.append("")
    report.append("  METRICS BY ITEM TYPE (static holdout, primary)")
    report.append("  " + "-" * 50)
    for result in primary:
        by_type = metrics_by(result["forecasts"], "item_type")
        parts = [
            f"{item_type} MAE={by_type[item_type]['mae']:.4f} RMSE={by_type[item_type]['rmse']:.4f}"
            f" n={by_type[item_type]['n']}"
            for item_type in ITEM_TYPE_ORDER
            if item_type in by_type
        ]
        report.append(f"  {result['label']}: " + " | ".join(parts))

    report.append("")
    report.append("  TARGET DEFINITION")
    report.append("  " + "-" * 50)
    report.append("  Demand = ALL borrow events by borrowDate, regardless of return status")
    report.append("  Active loans (return_date = NULL) contribute to demand in borrow month")
    report.append("  Inherited from Phase 5A semantics")

    report.append("")
    report.append("  SERIES DEFINITION")
    report.append("  " + "-" * 50)
    report.append(f"  BOOK: actual category (or {UNCATEGORIZED} if NULL)")
    report.append(f"  MAGAZINE: actual magazine category (or {UNCATEGORIZED} if NULL)")
    report.append(f"  NEWSPAPER: item_type = NEWSPAPER, category = {UNCATEGORIZED}")
    report.append("  Inherited from Phase 5A semantics")

    report.append("")
    report.append("  SYNTHETIC DATA CAVEAT")
    report.append("  " + "-" * 50)
    report.append("  This is an offline synthetic-data prototype only")
    report.append("  Academic calendar regimes are SYNTHETIC based on semester_factor()")
    report.append("  These do NOT represent real academic calendar data")
    report.append("  Results do NOT establish real-world forecasting performance")
    report.append("  Calendar-adjusted predictive modeling deferred until real calendar data exists")

    report.append("")
    report.append("  METHODOLOGY LIMITATIONS")
    report.append("  " + "-" * 50)
    report.append("  - Offline synthetic-data prototype only")
    report.append("  - Not a production collection recommendation system")
    report.append("  - No complex forecasting models evaluated (ARIMA, Prophet, XGBoost, etc.)")
    report.append("  - No real academic calendar data available")
    report.append("  - Newspapers have no category column in production schema")
    report.append("  - Single train/test split; no repeated or nested resampling, so the")
    report.append("    metric difference between adjacent baselines is not significance-tested")
    report.append("  - Seasonality is a synthetic generator assumption, so a seasonal baseline")
    report.append("    is advantaged relative to what real data would show")

    report.append("")
    report.append("=" * 70)
    report.append("  VERDICT")
    report.append("=" * 70)
    report.append("  Phase 5B baseline methodology prototype complete.")
    report.append("  Three simple baselines evaluated under two explicit protocols.")
    report.append("  No ranking or winner declared - empirical metrics reported only.")
    report.append("=" * 70)

    return "\n".join(report)


def main():
    print("Phase 5B — Monthly Demand Forecasting with Calendar Baselines")
    print(f"Run at: {datetime.now().isoformat()}")
    print()

    print("Loading synthetic circulation data...")
    conn = connect()
    borrow_data = load_borrow_data(conn)
    print(f"  Loaded {len(borrow_data)} borrow records (all borrow events by borrow_date)")
    conn.close()

    first_month, last_month, truncated = resolve_grid_bounds(borrow_data)
    if truncated:
        print(f"  Data ends {last_month}; excluding unobserved configured months {truncated}")

    # See phase5a: the grid is bounded, but densification derives its series
    # keys from every row it is given, so an out-of-window record would add a
    # series that is then scored as if it were real.
    in_range = filter_records_to_range(borrow_data, first_month, last_month)
    out_of_range = len(borrow_data) - len(in_range)
    if out_of_range:
        print(
            f"  Dropped {out_of_range} borrow records outside the analysis window "
            f"{first_month} to {last_month}"
        )
    borrow_data = in_range

    print("Aggregating monthly demand by item type and category...")
    demand_data = aggregate_monthly_demand(borrow_data)
    print(f"  {len(demand_data)} non-zero monthly observations")

    print("Densifying the monthly grid across observed months...")
    demand_data = densify_monthly_grid(demand_data, first_month, last_month)
    print(f"  {len(demand_data)} monthly observations (including zero-demand)")

    demand_data = add_calendar_regime(demand_data)
    train_data, test_data, origin = split_train_test(demand_data)
    print(f"  Origin: {origin} | Train: {len(train_data)} | Test: {len(test_data)}")

    if not test_data:
        raise SystemExit(
            f"No forecast horizon: the data covers {first_month} to {last_month}, which "
            f"does not extend past the training origin {origin}. There is nothing to "
            "score."
        )

    regime_demand = {}
    for record in demand_data:
        regime_demand[record["regime"]] = regime_demand.get(record["regime"], 0) + record["demand"]

    results_by_protocol = []
    primary_results = None
    for protocol in (STATIC_HOLDOUT, ROLLING_ORIGIN):
        print(f"Running baselines under {protocol}...")
        results, _by_label = run_baselines(demand_data, test_data, origin, protocol)
        for result in results:
            m = result["metrics"]
            c = result["coverage"]
            print(
                f"  {result['label']}: MAE {m['mae']:.4f} | RMSE {m['rmse']:.4f}"
                f" | points {m['n']} | full window {c['full_window']}/{c['points']}"
                f" | fallbacks {c['fallback']}"
            )
        results_by_protocol.append((protocol, results))
        if protocol == STATIC_HOLDOUT:
            primary_results = results

    context = {
        "first_month": first_month,
        "last_month": last_month,
        "truncated_months": truncated,
        "total_observations": len(demand_data),
        "series_count": len({f"{r['item_type']}|{r['category']}" for r in demand_data}),
        "series_keys": sorted({f"{r['item_type']}|{r['category']}" for r in demand_data}),
        "series_by_item_type": _series_by_item_type(demand_data),
        "origin": origin,
        "horizon_start": month_key(TEST_START),
        "train_count": len(train_data),
        "test_count": len(test_data),
    }

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    demand_path = os.path.join(OUTPUT_DIR, "phase5b_monthly_demand.csv")
    with open(demand_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["month", "item_type", "category", "demand", "regime"])
        writer.writeheader()
        writer.writerows(demand_data)
    print(f"  {demand_path}")

    for protocol, results in results_by_protocol:
        suffix = "" if protocol == STATIC_HOLDOUT else "_rolling_origin"
        for result in results:
            safe = result["label"].replace(" ", "_").replace("-", "").lower()
            path = os.path.join(OUTPUT_DIR, f"phase5b_{safe}{suffix}_forecast.csv")
            with open(path, "w", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=list(result["forecasts"][0].keys()))
                writer.writeheader()
                writer.writerows(result["forecasts"])
            print(f"  {path}")

    report_text = generate_report(context, results_by_protocol, regime_demand)
    report_path = os.path.join(OUTPUT_DIR, "phase5b_report.txt")
    with open(report_path, "w") as f:
        f.write(report_text)
    print(f"  {report_path}")

    print()
    print(report_text)
    print()
    print("Phase 5B COMPLETE.")


def _series_by_item_type(demand_data):
    counts = {}
    for r in demand_data:
        counts.setdefault(r["item_type"], set()).add(r["category"])
    return {k: len(v) for k, v in sorted(counts.items())}


if __name__ == "__main__":
    main()
