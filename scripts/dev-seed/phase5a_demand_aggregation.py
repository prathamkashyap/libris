#!/usr/bin/env python3
"""
Phase 5A — Monthly Category Demand + Seasonal Naive Baseline

Offline synthetic-data development prototype for Libris demand forecasting.
Aggregates monthly borrowing demand by item type and category, then evaluates a
seasonal naive baseline (same calendar month, previous year).

All forecasting logic lives in phase5_forecasting.py, which is the single source
of truth shared with Phase 5B and the Phase 5 test suite.

This is a methodology-development prototype, NOT a production recommendation
system. Results are computed on synthetic development data and do not establish
real-world forecasting performance.
"""
import os
import csv
import sys
from datetime import date, datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from phase5_forecasting import (  # noqa: E402
    ROLLING_ORIGIN,
    SEASONAL_LAG,
    STATIC_HOLDOUT,
    UNCATEGORIZED,
    aggregate_monthly_demand,
    add_calendar_regime,
    compare_months,
    coverage,
    densify_monthly_grid,
    forecast_all,
    metrics,
    metrics_by,
    month_key,
    month_sequence,
    observed_month_range,
    shift_month,
)

# Database configuration
DB_HOST = "127.0.0.1"
DB_PORT = 3306
DB_USER = "root"
DB_PASS = os.environ.get("LMS_DB_PASSWORD", "")
DB_NAME = os.environ.get("LMS_DB_NAME", "libris_ml_dev")

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")

# Configured analysis window and the static-holdout origin.
# DATE_END is the configured ceiling; the effective end is additionally clipped
# to the last month the source data actually covers (see resolve_grid_bounds).
DATE_START = date(2023, 1, 1)
DATE_END = date(2026, 8, 31)
TRAIN_END = date(2025, 12, 31)
TEST_START = date(2026, 1, 1)



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


def load_borrow_data(conn):
    """Load borrow records with item type and category information.

    IMPORTANT: counts ALL borrow events by borrow_date, regardless of return
    status. Active loans (return_date = NULL) contribute to demand in their
    borrow month. This is the correct target definition for demand forecasting.
    """
    query = """
    SELECT
        br.borrow_date,
        br.book_id,
        br.magazine_id,
        br.newspaper_id,
        b.category as book_category,
        m.category as magazine_category
    FROM borrow_records br
    LEFT JOIN books b ON br.book_id = b.id
    LEFT JOIN magazines m ON br.magazine_id = m.id
    WHERE br.borrow_date IS NOT NULL
    ORDER BY br.borrow_date
    """

    from phase5_forecasting import classify_borrow_row

    with conn.cursor() as cur:
        cur.execute(query)
        rows = cur.fetchall()

    data = []
    for row in rows:
        record = classify_borrow_row(*row)
        if record is not None:
            data.append(record)
    return data


def resolve_grid_bounds(borrow_data):
    """
    Clip the configured analysis window to the months the data actually covers.

    seed_generator.py stops emitting loans at SIMULATED_TODAY (2026-07-15), so
    the configured DATE_END of 2026-08-31 is never reached. Densifying to
    DATE_END would fabricate a whole all-zero month and score every baseline
    against an observation that was never generated. A month the generator never
    emitted is missing data, not zero demand, so the grid ends at the last
    observed month.

    Returns ``(first_month, last_month, truncated_months)``.
    """
    configured_first = month_key(DATE_START)
    configured_last = month_key(DATE_END)
    observed_first, observed_last = observed_month_range(borrow_data)

    if observed_last is None:
        raise SystemExit("No borrow records found; cannot build a demand grid.")

    first = observed_first if compare_months(observed_first, configured_first) > 0 else configured_first
    last = observed_last if compare_months(observed_last, configured_last) < 0 else configured_last

    truncated = month_sequence(shift_month(last, 1), configured_last)
    return first, last, truncated


def split_train_test(demand_data):
    """Static-holdout split: everything up to and including the origin trains."""
    origin = month_key(TRAIN_END)
    horizon = month_key(TEST_START)
    train = [r for r in demand_data if compare_months(r["month"], origin) <= 0]
    test = [r for r in demand_data if compare_months(r["month"], horizon) >= 0]
    return train, test, origin


def generate_report(context, forecasts, primary_metrics, rolling_metrics):
    """Generate the development report."""
    report = []
    report.append("=" * 70)
    report.append("  PHASE 5A — MONTHLY CATEGORY DEMAND + SEASONAL NAIVE BASELINE")
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
            f"  NOTE: source data ends {context['last_month']}; "
            f"configured months {context['truncated_months']} excluded as unobserved"
        )

    report.append("")
    report.append("  EVALUATION PROTOCOL (static holdout)")
    report.append("  " + "-" * 50)
    report.append(f"  Origin (last training month): {context['origin']}")
    report.append(f"  Training window: {context['first_month']} to {context['origin']}")
    report.append(f"  Forecast horizon: {context['horizon_start']} to {context['last_month']}")
    report.append(f"  Train observations: {context['train_count']}")
    report.append(f"  Test observations: {context['test_count']}")
    report.append("  A forecast for month t may use only observations of months < t.")
    report.append("  The information set is frozen at the origin, so no test-period")
    report.append("  demand ever influences a forecast.")
    report.append("  Primary: static holdout. Secondary: rolling origin (window slides).")

    report.append("")
    report.append("  BASELINE METHOD")
    report.append("  " + "-" * 50)
    report.append(
        f"  Seasonal naive: forecast(t) = demand(t - {SEASONAL_LAG} months),"
        " looked up by item_type|category|month"
    )
    report.append("  Fallback: mean of the series over visible history if the t-12 month is absent")

    report.append("")
    report.append("  FORECAST METRICS")
    report.append("  " + "-" * 50)
    for label, m in (("Static holdout (primary)", primary_metrics), ("Rolling origin", rolling_metrics)):
        report.append(f"  {label}:")
        report.append(f"    MAE: {m['mae']:.4f}")
        report.append(f"    RMSE: {m['rmse']:.4f}")
        report.append(f"    Forecast points: {m['n']}")

    report.append("")
    report.append("  WINDOW COVERAGE (primary protocol)")
    report.append("  " + "-" * 50)
    cov = coverage(forecasts)
    report.append(f"  Forecast points: {cov['points']}")
    report.append(f"  Full t-12 window: {cov['full_window']} ({cov['full_window_pct']:.1f}%)")
    report.append(f"  Partial window: {cov['partial_window']}")
    report.append(f"  Series-mean fallback: {cov['fallback']}")

    report.append("")
    report.append("  METRICS BY ITEM TYPE (primary protocol)")
    report.append("  " + "-" * 50)
    for item_type, m in sorted(metrics_by(forecasts, "item_type").items()):
        report.append(f"    {item_type}: MAE={m['mae']:.4f}, RMSE={m['rmse']:.4f}, n={m['n']}")

    report.append("")
    report.append("  CATEGORY/ITEM-TYPE REPRESENTATION")
    report.append("  " + "-" * 50)
    report.append(f"  BOOK -> actual book category (or {UNCATEGORIZED} if NULL)")
    report.append(f"  MAGAZINE -> actual magazine category (or {UNCATEGORIZED} if NULL)")
    report.append(f"  NEWSPAPER -> item_type = NEWSPAPER, category = {UNCATEGORIZED} (no catalog column)")

    report.append("")
    report.append("  SYNTHETIC SEASONALITY CAVEAT")
    report.append("  " + "-" * 50)
    report.append("  The synthetic dataset includes semester_factor() that reduces borrowing by 40% during")
    report.append("  summer/winter breaks (months 1,2,7,8). This is a development assumption, not measured")
    report.append("  real Libris behavior. Real academic calendars and local patterns may differ significantly.")

    report.append("")
    report.append("  METHODOLOGY LIMITATIONS")
    report.append("  " + "-" * 50)
    report.append("  - Offline synthetic-data prototype only")
    report.append("  - Not a production collection recommendation system")
    report.append("  - Results do NOT establish real-world forecasting performance")
    report.append("  - The series-mean fallback is unreachable against the current seed, because the")
    report.append("    synthetic grid is complete and the horizon is shorter than 12 months")
    report.append("  - Newspapers have no category column in the production schema")
    report.append("  - Complex forecasting models (ARIMA, Prophet, XGBoost, etc.) not evaluated")

    report.append("")
    report.append("=" * 70)
    report.append("  VERDICT")
    report.append("=" * 70)
    report.append("  Phase 5A methodology prototype complete.")
    report.append("  Seasonal naive baseline provides reference for future method comparison.")
    report.append("=" * 70)

    return "\n".join(report)


def main():
    print("Phase 5A — Monthly Category Demand + Seasonal Naive Baseline")
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

    print("Aggregating monthly demand by item type and category...")
    demand_data = aggregate_monthly_demand(borrow_data)
    print(f"  {len(demand_data)} non-zero monthly observations")

    print("Densifying the monthly grid across observed months...")
    demand_data = densify_monthly_grid(demand_data, first_month, last_month)
    print(f"  {len(demand_data)} monthly observations (including zero-demand)")

    demand_data = add_calendar_regime(demand_data)
    train_data, test_data, origin = split_train_test(demand_data)
    print(f"  Origin: {origin} | Train: {len(train_data)} | Test: {len(test_data)}")

    print("Generating seasonal naive forecasts (static holdout)...")
    forecasts = forecast_all(
        demand_data, test_data, origin, STATIC_HOLDOUT, "seasonal_naive", label="Seasonal Naive"
    )
    primary_metrics = metrics(forecasts)
    print(f"  {len(forecasts)} forecast points | MAE {primary_metrics['mae']:.4f} | RMSE {primary_metrics['rmse']:.4f}")

    print("Generating seasonal naive forecasts (rolling origin)...")
    rolling = forecast_all(
        demand_data, test_data, origin, ROLLING_ORIGIN, "seasonal_naive", label="Seasonal Naive"
    )
    rolling_metrics = metrics(rolling)
    print(f"  {len(rolling)} forecast points | MAE {rolling_metrics['mae']:.4f} | RMSE {rolling_metrics['rmse']:.4f}")

    cov = coverage(forecasts)
    print(f"  Full t-12 window: {cov['full_window']}/{cov['points']} | fallbacks: {cov['fallback']}")

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

    demand_path = os.path.join(OUTPUT_DIR, "phase5a_monthly_demand.csv")
    with open(demand_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["month", "item_type", "category", "demand", "regime"])
        writer.writeheader()
        writer.writerows(demand_data)
    print(f"  {demand_path}")

    forecast_path = os.path.join(OUTPUT_DIR, "phase5a_forecast.csv")
    with open(forecast_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(forecasts[0].keys()))
        writer.writeheader()
        writer.writerows(forecasts)
    print(f"  {forecast_path}")

    rolling_path = os.path.join(OUTPUT_DIR, "phase5a_forecast_rolling_origin.csv")
    with open(rolling_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rolling[0].keys()))
        writer.writeheader()
        writer.writerows(rolling)
    print(f"  {rolling_path}")

    report_text = generate_report(context, forecasts, primary_metrics, rolling_metrics)
    report_path = os.path.join(OUTPUT_DIR, "phase5a_report.txt")
    with open(report_path, "w") as f:
        f.write(report_text)
    print(f"  {report_path}")

    print()
    print(report_text)
    print()
    print("Phase 5A COMPLETE.")


def _series_by_item_type(demand_data):
    counts = {}
    for r in demand_data:
        counts.setdefault(r["item_type"], set()).add(r["category"])
    return {k: len(v) for k, v in sorted(counts.items())}


if __name__ == "__main__":
    main()
