#!/usr/bin/env python3
"""
Phase 5A — Monthly Category Demand + Seasonal Naive Baseline

Offline synthetic-data development prototype for Libris demand forecasting.
Aggregates monthly borrowing demand by item type and category, then implements
a seasonal naive baseline forecast using same-month-previous-year approach.

This is a methodology-development prototype, NOT a production recommendation system.
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

# Temporal boundaries
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
    This is the correct target definition for demand forecasting.
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
        
        # Determine item type and category
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


def seasonal_naive_forecast(train_data, test_months):
    """
    Generate seasonal naive forecasts: use same month from previous year.
    
    For series without prior-year observation, use historical mean.
    """
    # Build historical lookup from training data
    historical = {}
    for record in train_data:
        key = f"{record['item_type']}|{record['category']}"
        historical[key] = record["demand"]
    
    forecasts = []
    forecast_keys = set()
    
    for record in test_months:
        month_key = record["month"]
        series_key = f"{record['item_type']}|{record['category']}"
        series_key_year_month = f"{series_key}|{month_key}"
        
        # Calculate previous year month
        year, month = map(int, month_key.split("-"))
        prev_year = year - 1
        prev_month_key = f"{prev_year:04d}-{month:02d}"
        prev_key = f"{series_key}|{prev_month_key}"
        
        if prev_key in historical:
            forecast = historical[prev_key]
        else:
            # Fallback: use historical mean for this series
            series_demands = [v for k, v in historical.items() if k.startswith(series_key)]
            forecast = sum(series_demands) / len(series_demands) if series_demands else 0
        
        forecasts.append({
            "month": month_key,
            "item_type": record["item_type"],
            "category": record["category"],
            "actual": record["demand"],
            "forecast": forecast,
        })
        
        forecast_keys.add(series_key)
    
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


def generate_report(demand_data, forecasts, metrics):
    """Generate development report."""
    report = []
    report.append("=" * 70)
    report.append("  PHASE 5A — MONTHLY CATEGORY DEMAND + SEASONAL NAIVE BASELINE")
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
    report.append("  BASELINE METHOD")
    report.append("  " + "-" * 50)
    report.append("  Seasonal naive: forecast month t using demand from same calendar month in previous year")
    report.append("  Fallback: historical mean for series without prior-year observation")
    
    report.append("")
    report.append("  FORECAST METRICS")
    report.append("  " + "-" * 50)
    report.append(f"  MAE: {metrics['mae']:.4f}")
    report.append(f"  RMSE: {metrics['rmse']:.4f}")
    report.append(f"  Forecast points: {metrics['n']}")
    
    report.append("")
    report.append("  CATEGORY/ITEM-TYPE REPRESENTATION")
    report.append("  " + "-" * 50)
    report.append("  BOOK → actual book category (or UNCATEGORIZED if NULL)")
    report.append("  MAGAZINE → actual magazine category (or UNCATEGORIZED if NULL)")
    report.append("  NEWSPAPER → item_type = NEWSPAPER, category = UNCATEGORIZED (no catalog column)")
    
    report.append("")
    report.append("  SYNTHETIC SEASONALITY CAVEAT")
    report.append("  " + "-" * 50)
    report.append("  The synthetic dataset includes semester_factor() that reduces borrowing by 40% during")
    report.append("  summer/winter breaks (months 1,2,7,8). This is a development assumption, not measured")
    report.append("  real Libris behavior. Real academic calendars and local patterns may differ significantly.")
    
    report.append("")
    report.append("  METHODOLOGY LIMITATIONS")
    report.append("  " + "-" * 50)
    report.append("  - This is an offline synthetic-data prototype only")
    report.append("  - Not a production collection recommendation system")
    report.append("  - Results do not establish real-world forecasting performance")
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
    
    # Split train/test
    train_data = [r for r in demand_data if r["month"] <= TRAIN_END.strftime("%Y-%m")]
    test_data = [r for r in demand_data if r["month"] >= TEST_START.strftime("%Y-%m")]
    print(f"  Train: {len(train_data)} observations")
    print(f"  Test: {len(test_data)} observations")
    
    # Generate forecasts
    print("Generating seasonal naive forecasts...")
    forecasts = seasonal_naive_forecast(train_data, test_data)
    print(f"  {len(forecasts)} forecast points")
    
    # Calculate metrics
    print("Calculating forecast metrics...")
    metrics = calculate_metrics(forecasts)
    print(f"  MAE: {metrics['mae']:.4f}")
    print(f"  RMSE: {metrics['rmse']:.4f}")
    
    # Generate outputs
    print("Generating development outputs...")
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # Monthly demand CSV
    demand_path = os.path.join(OUTPUT_DIR, "phase5a_monthly_demand.csv")
    with open(demand_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["month", "item_type", "category", "demand"])
        writer.writeheader()
        writer.writerows(demand_data)
    print(f"  {demand_path}")
    
    # Forecast CSV
    forecast_path = os.path.join(OUTPUT_DIR, "phase5a_forecast.csv")
    with open(forecast_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["month", "item_type", "category", "actual", "forecast"])
        writer.writeheader()
        writer.writerows(forecasts)
    print(f"  {forecast_path}")
    
    # Report
    report_text = generate_report(demand_data, forecasts, metrics)
    report_path = os.path.join(OUTPUT_DIR, "phase5a_report.txt")
    with open(report_path, "w") as f:
        f.write(report_text)
    print(f"  {report_path}")
    
    print()
    print(report_text)
    print()
    print("Phase 5A COMPLETE.")


if __name__ == "__main__":
    main()
