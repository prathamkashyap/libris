#!/usr/bin/env python3
"""
Phase 5B — Monthly Demand Forecasting with Academic-Calendar Baselines

Offline synthetic-data development prototype for Libris demand forecasting.
Extends Phase 5A seasonal naive baseline with moving-average baselines and
synthetic academic-calendar regime analysis.

This is a methodology-development prototype, NOT a production recommendation system.
Calendar regimes are SYNTHETIC based on semester_factor() in seed_generator.py
and do not represent real academic calendar data.
"""
import os
import csv
import math
from datetime import date, datetime
from collections import defaultdict

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

# Category fallback
UNCATEGORIZED = "UNCATEGORIZED"


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
    
    IMPORTANT: Counts ALL borrow events by borrow_date, regardless of return status.
    Active loans (return_date = NULL) contribute to demand in their borrow month.
    This is the correct target definition for demand forecasting (Phase 5A semantics).
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
    
    with conn.cursor() as cur:
        cur.execute(query)
        rows = cur.fetchall()
    
    data = []
    for row in rows:
        borrow_date, book_id, magazine_id, newspaper_id, book_category, magazine_category = row
        
        # Determine item type and category (Phase 5A semantics)
        if book_id is not None:
            item_type = "BOOK"
            category = book_category if book_category else UNCATEGORIZED
        elif magazine_id is not None:
            item_type = "MAGAZINE"
            category = magazine_category if magazine_category else UNCATEGORIZED
        elif newspaper_id is not None:
            item_type = "NEWSPAPER"
            category = UNCATEGORIZED  # Newspapers have no catalog category
        else:
            continue  # Skip records with no item reference
        
        data.append({
            "borrow_date": borrow_date,
            "item_type": item_type,
            "category": category,
        })
    
    return data


def aggregate_monthly_demand(data):
    """Aggregate demand by month, item_type, and category."""
    monthly_demand = defaultdict(lambda: defaultdict(int))
    
    for record in data:
        borrow_date = record["borrow_date"]
        item_type = record["item_type"]
        category = record["category"]
        
        month_key = borrow_date.strftime("%Y-%m")
        series_key = f"{item_type}|{category}"
        
        monthly_demand[month_key][series_key] += 1
    
    # Convert to sorted list
    result = []
    for month_key in sorted(monthly_demand.keys()):
        for series_key in sorted(monthly_demand[month_key].keys()):
            item_type, category = series_key.split("|")
            result.append({
                "month": month_key,
                "item_type": item_type,
                "category": category,
                "demand": monthly_demand[month_key][series_key],
            })
    
    return result


def ensure_all_months(demand_data):
    """Ensure all months in the date range are represented, even with zero demand."""
    all_months = []
    current = DATE_START
    while current <= DATE_END:
        all_months.append(current.strftime("%Y-%m"))
        current = date(current.year, current.month + 1, 1) if current.month < 12 else date(current.year + 1, 1, 1)
    
    # Get all unique series
    series_keys = set()
    for record in demand_data:
        series_key = f"{record['item_type']}|{record['category']}"
        series_keys.add(series_key)
    
    # Add zero-demand months for missing combinations
    result = list(demand_data)
    existing_keys = {(r["month"], f"{r['item_type']}|{r['category']}") for r in result}
    
    for month_key in all_months:
        for series_key in series_keys:
            if (month_key, series_key) not in existing_keys:
                item_type, category = series_key.split("|")
                result.append({
                    "month": month_key,
                    "item_type": item_type,
                    "category": category,
                    "demand": 0,
                })
    
    return sorted(result, key=lambda x: (x["month"], x["item_type"], x["category"]))


def get_calendar_regime(month_key):
    """
    Determine synthetic academic calendar regime based on semester_factor() logic.
    
    This matches the exact logic from seed_generator.py:245-252:
    - BREAK: months 1,2,7,8 (40% reduction, factor=0.60)
    - NORMAL: months 3,4,5,9,10,11 (no reduction, factor=1.00)
    - REDUCED: months 6,12 (25% reduction, factor=0.75)
    
    CAVEAT: These are SYNTHETIC regimes for development only.
    They do not represent real academic calendar data.
    """
    year, month = map(int, month_key.split("-"))
    
    if month in (1, 2, 7, 8):
        return "BREAK"
    elif month in (3, 4, 5, 9, 10, 11):
        return "NORMAL"
    else:  # months 6, 12
        return "REDUCED"


def add_calendar_regime(demand_data):
    """Add calendar regime label to each demand record."""
    result = []
    for record in demand_data:
        record_with_regime = record.copy()
        record_with_regime["regime"] = get_calendar_regime(record["month"])
        result.append(record_with_regime)
    return result


def seasonal_naive_forecast(train_data, test_months):
    """
    Generate seasonal naive forecasts: use same month from previous year.
    
    For series without prior-year observation, use historical mean.
    Uses ONLY training data for lookup.
    """
    # Build historical lookup from training data
    historical = {}
    for record in train_data:
        key = f"{record['item_type']}|{record['category']}|{record['month']}"
        historical[key] = record["demand"]
    
    forecasts = []
    
    for record in test_months:
        month_key = record["month"]
        series_key = f"{record['item_type']}|{record['category']}"
        
        # Calculate previous year month
        year, month = map(int, month_key.split("-"))
        prev_year = year - 1
        prev_month_key = f"{prev_year:04d}-{month:02d}"
        prev_key = f"{series_key}|{prev_month_key}"
        
        if prev_key in historical:
            forecast = historical[prev_key]
        else:
            # Fallback: use historical mean for this series (training only)
            series_demands = [v for k, v in historical.items() if k.startswith(series_key)]
            forecast = sum(series_demands) / len(series_demands) if series_demands else 0
        
        forecasts.append({
            "month": month_key,
            "item_type": record["item_type"],
            "category": record["category"],
            "actual": record["demand"],
            "forecast": forecast,
            "regime": record["regime"],
        })
    
    return forecasts


def moving_average_forecast(train_data, test_months, window_months):
    """
    Generate moving average forecasts using trailing window.
    
    window_months: number of trailing months to average (3 or 6)
    Uses ONLY training data strictly before forecast month.
    """
    # Build historical lookup from training data
    historical = {}
    for record in train_data:
        key = f"{record['item_type']}|{record['category']}|{record['month']}"
        historical[key] = record["demand"]
    
    forecasts = []
    
    for record in test_months:
        month_key = record["month"]
        series_key = f"{record['item_type']}|{record['category']}"
        
        # Collect trailing months from historical data
        year, month = map(int, month_key.split("-"))
        trailing_demands = []
        
        for offset in range(1, window_months + 1):
            # Calculate trailing month
            trailing_year = year
            trailing_month = month - offset
            if trailing_month < 1:
                trailing_year -= 1
                trailing_month += 12
            
            trailing_month_key = f"{trailing_year:04d}-{trailing_month:02d}"
            trailing_key = f"{series_key}|{trailing_month_key}"
            
            if trailing_key in historical:
                trailing_demands.append(historical[trailing_key])
        
        if trailing_demands:
            forecast = sum(trailing_demands) / len(trailing_demands)
        else:
            # Fallback: use historical mean for this series (training only)
            series_demands = [v for k, v in historical.items() if k.startswith(series_key)]
            forecast = sum(series_demands) / len(series_demands) if series_demands else 0
        
        forecasts.append({
            "month": month_key,
            "item_type": record["item_type"],
            "category": record["category"],
            "actual": record["demand"],
            "forecast": forecast,
            "regime": record["regime"],
        })
    
    return forecasts


def calculate_metrics(forecasts):
    """Calculate MAE and RMSE for forecasts."""
    n = len(forecasts)
    if n == 0:
        return {"mae": 0, "rmse": 0, "n": 0}
    
    sum_abs_error = 0
    sum_squared_error = 0
    
    for f in forecasts:
        error = f["actual"] - f["forecast"]
        sum_abs_error += abs(error)
        sum_squared_error += error ** 2
    
    mae = sum_abs_error / n
    rmse = math.sqrt(sum_squared_error / n)
    
    return {"mae": mae, "rmse": rmse, "n": n}


def calculate_metrics_by_regime(forecasts):
    """Calculate metrics broken down by calendar regime."""
    regimes = {}
    for f in forecasts:
        regime = f["regime"]
        if regime not in regimes:
            regimes[regime] = []
        regimes[regime].append(f)
    
    regime_metrics = {}
    for regime, regime_forecasts in regimes.items():
        metrics = calculate_metrics(regime_forecasts)
        regime_metrics[regime] = metrics
    
    return regime_metrics


def calculate_metrics_by_item_type(forecasts):
    """Calculate metrics broken down by item type."""
    item_types = {}
    for f in forecasts:
        item_type = f["item_type"]
        if item_type not in item_types:
            item_types[item_type] = []
        item_types[item_type].append(f)
    
    item_type_metrics = {}
    for item_type, type_forecasts in item_types.items():
        metrics = calculate_metrics(type_forecasts)
        item_type_metrics[item_type] = metrics
    
    return item_type_metrics


def summarize_demand_by_regime(demand_data):
    """Summarize total demand by calendar regime."""
    regime_demand = defaultdict(int)
    for record in demand_data:
        regime_demand[record["regime"]] += record["demand"]
    
    return dict(regime_demand)


def generate_report(demand_data, forecasts_dict, metrics_dict):
    """Generate development report."""
    report = []
    report.append("=" * 70)
    report.append("  PHASE 5B — MONTHLY DEMAND FORECASTING WITH CALENDAR BASELINES")
    report.append("=" * 70)
    report.append(f"  Run at: {datetime.now().isoformat()}")
    report.append("")
    
    report.append("  DATASET")
    report.append("  " + "-" * 50)
    report.append(f"  Date range: {DATE_START} to {DATE_END}")
    report.append(f"  Total monthly observations: {len(demand_data)}")
    
    # Count unique series
    series = set(f"{r['item_type']}|{r['category']}" for r in demand_data)
    report.append(f"  Unique series (item_type|category): {len(series)}")
    
    # Count by item type
    item_type_counts = defaultdict(int)
    for r in demand_data:
        item_type_counts[r['item_type']] += 1
    report.append(f"  Series by item_type: {dict(item_type_counts)}")
    
    report.append("")
    report.append("  TEMPORAL SPLIT")
    report.append("  " + "-" * 50)
    report.append(f"  Training: {DATE_START} to {TRAIN_END}")
    report.append(f"  Test: {TEST_START} to {DATE_END}")
    
    # Count train/test observations
    train_data = [r for r in demand_data if r["month"] <= TRAIN_END.strftime("%Y-%m")]
    test_data = [r for r in demand_data if r["month"] >= TEST_START.strftime("%Y-%m")]
    report.append(f"  Train observations: {len(train_data)}")
    report.append(f"  Test observations: {len(test_data)}")
    
    report.append("")
    report.append("  SYNTHETIC ACADEMIC CALENDAR REGIMES")
    report.append("  " + "-" * 50)
    report.append("  Based on semester_factor() from seed_generator.py")
    report.append("  BREAK: months 1,2,7,8 (40% demand reduction)")
    report.append("  NORMAL: months 3,4,5,9,10,11 (no reduction)")
    report.append("  REDUCED: months 6,12 (25% demand reduction)")
    
    # Demand by regime
    regime_demand = summarize_demand_by_regime(demand_data)
    report.append(f"  Total demand by regime: {regime_demand}")
    
    report.append("")
    report.append("  BASELINE METHODS")
    report.append("  " + "-" * 50)
    report.append("  1. Seasonal naive: forecast(t) = demand(t - 12 months)")
    report.append("  2. 3-month MA: forecast(t) = mean(demand(t-1), demand(t-2), demand(t-3))")
    report.append("  3. 6-month MA: forecast(t) = mean(demand(t-1), ..., demand(t-6))")
    report.append("  All baselines use ONLY historical observations before forecast month")
    
    report.append("")
    report.append("  FORECAST METRICS (TEST PERIOD)")
    report.append("  " + "-" * 50)
    
    for baseline_name, metrics in metrics_dict.items():
        report.append(f"  {baseline_name}:")
        report.append(f"    MAE: {metrics['mae']:.4f}")
        report.append(f"    RMSE: {metrics['rmse']:.4f}")
        report.append(f"    Forecast points: {metrics['n']}")
        report.append("")
    
    report.append("  METRICS BY CALENDAR REGIME")
    report.append("  " + "-" * 50)
    
    for baseline_name, forecasts in forecasts_dict.items():
        regime_metrics = calculate_metrics_by_regime(forecasts)
        report.append(f"  {baseline_name}:")
        for regime in ["BREAK", "NORMAL", "REDUCED"]:
            if regime in regime_metrics:
                m = regime_metrics[regime]
                report.append(f"    {regime}: MAE={m['mae']:.4f}, RMSE={m['rmse']:.4f}, n={m['n']}")
        report.append("")
    
    report.append("  METRICS BY ITEM TYPE")
    report.append("  " + "-" * 50)
    
    for baseline_name, forecasts in forecasts_dict.items():
        item_type_metrics = calculate_metrics_by_item_type(forecasts)
        report.append(f"  {baseline_name}:")
        for item_type in ["BOOK", "MAGAZINE", "NEWSPAPER"]:
            if item_type in item_type_metrics:
                m = item_type_metrics[item_type]
                report.append(f"    {item_type}: MAE={m['mae']:.4f}, RMSE={m['rmse']:.4f}, n={m['n']}")
        report.append("")
    
    report.append("  TARGET DEFINITION")
    report.append("  " + "-" * 50)
    report.append("  Demand = ALL borrow events by borrowDate, regardless of return status")
    report.append("  Active loans (return_date = NULL) contribute to demand in borrow month")
    report.append("  Inherited from Phase 5A semantics")
    
    report.append("")
    report.append("  SERIES DEFINITION")
    report.append("  " + "-" * 50)
    report.append("  BOOK: actual category (or UNCATEGORIZED if NULL)")
    report.append("  MAGAZINE: actual category (or UNCATEGORIZED if NULL)")
    report.append("  NEWSPAPER: item_type = NEWSPAPER, category = UNCATEGORIZED")
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
    
    report.append("")
    report.append("=" * 70)
    report.append("  VERDICT")
    report.append("=" * 70)
    report.append("  Phase 5B baseline methodology prototype complete.")
    report.append("  Three simple baselines evaluated with synthetic calendar regime analysis.")
    report.append("  No ranking or winner declared - empirical metrics reported only.")
    report.append("=" * 70)
    
    return "\n".join(report)


def main():
    print("Phase 5B — Monthly Demand Forecasting with Calendar Baselines")
    print(f"Run at: {datetime.now().isoformat()}")
    print()
    
    # Load data
    print("Loading synthetic circulation data...")
    conn = connect()
    borrow_data = load_borrow_data(conn)
    print(f"  Loaded {len(borrow_data)} borrow records (all borrow events by borrow_date)")
    conn.close()
    
    # Aggregate monthly demand
    print("Aggregating monthly demand by item type and category...")
    demand_data = aggregate_monthly_demand(borrow_data)
    print(f"  {len(demand_data)} monthly observations")
    
    # Ensure all months are represented
    print("Ensuring all months in date range are represented...")
    demand_data = ensure_all_months(demand_data)
    print(f"  {len(demand_data)} monthly observations (including zero-demand)")
    
    # Add calendar regime labels
    print("Adding synthetic academic calendar regime labels...")
    demand_data = add_calendar_regime(demand_data)
    print(f"  Calendar regimes assigned")
    
    # Split train/test
    train_data = [r for r in demand_data if r["month"] <= TRAIN_END.strftime("%Y-%m")]
    test_data = [r for r in demand_data if r["month"] >= TEST_START.strftime("%Y-%m")]
    print(f"  Train: {len(train_data)} observations")
    print(f"  Test: {len(test_data)} observations")
    
    # Generate forecasts for each baseline
    print("Generating baseline forecasts...")
    
    forecasts_dict = {}
    metrics_dict = {}
    
    # 1. Seasonal naive
    print("  Seasonal naive...")
    seasonal_forecasts = seasonal_naive_forecast(train_data, test_data)
    seasonal_metrics = calculate_metrics(seasonal_forecasts)
    forecasts_dict["Seasonal Naive"] = seasonal_forecasts
    metrics_dict["Seasonal Naive"] = seasonal_metrics
    print(f"    MAE: {seasonal_metrics['mae']:.4f}, RMSE: {seasonal_metrics['rmse']:.4f}")
    
    # 2. 3-month moving average
    print("  3-month moving average...")
    ma3_forecasts = moving_average_forecast(train_data, test_data, 3)
    ma3_metrics = calculate_metrics(ma3_forecasts)
    forecasts_dict["3-Month MA"] = ma3_forecasts
    metrics_dict["3-Month MA"] = ma3_metrics
    print(f"    MAE: {ma3_metrics['mae']:.4f}, RMSE: {ma3_metrics['rmse']:.4f}")
    
    # 3. 6-month moving average
    print("  6-month moving average...")
    ma6_forecasts = moving_average_forecast(train_data, test_data, 6)
    ma6_metrics = calculate_metrics(ma6_forecasts)
    forecasts_dict["6-Month MA"] = ma6_forecasts
    metrics_dict["6-Month MA"] = ma6_metrics
    print(f"    MAE: {ma6_metrics['mae']:.4f}, RMSE: {ma6_metrics['rmse']:.4f}")
    
    # Generate outputs
    print("Generating development outputs...")
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # Monthly demand CSV with calendar regimes
    demand_path = os.path.join(OUTPUT_DIR, "phase5b_monthly_demand.csv")
    with open(demand_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["month", "item_type", "category", "demand", "regime"])
        writer.writeheader()
        writer.writerows(demand_data)
    print(f"  {demand_path}")
    
    # Forecast CSVs
    for baseline_name, forecasts in forecasts_dict.items():
        safe_name = baseline_name.replace(" ", "_").replace("-", "").lower()
        forecast_path = os.path.join(OUTPUT_DIR, f"phase5b_{safe_name}_forecast.csv")
        with open(forecast_path, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=["month", "item_type", "category", "actual", "forecast", "regime"])
            writer.writeheader()
            writer.writerows(forecasts)
        print(f"  {forecast_path}")
    
    # Report
    report_text = generate_report(demand_data, forecasts_dict, metrics_dict)
    report_path = os.path.join(OUTPUT_DIR, "phase5b_report.txt")
    with open(report_path, "w") as f:
        f.write(report_text)
    print(f"  {report_path}")
    
    print()
    print(report_text)
    print()
    print("Phase 5B COMPLETE.")


if __name__ == "__main__":
    main()