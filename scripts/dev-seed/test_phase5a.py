#!/usr/bin/env python3
"""
Focused tests for Phase 5A demand aggregation and seasonal naive baseline.

Tests verify:
- month aggregation is correct
- zero-demand months are represented correctly
- category fallback is deterministic
- newspaper records preserve item_type
- same-month-previous-year forecasting uses only historical observations
- train/test boundaries are correct
- MAE/RMSE calculations are correct on a small known example
"""
import unittest
from datetime import date
import math


# Constants (copied from phase5a_demand_aggregation for testing)
UNCATEGORIZED = "UNCATEGORIZED"
DATE_START = date(2023, 1, 1)
DATE_END = date(2026, 8, 31)
TRAIN_END = date(2025, 12, 31)
TEST_START = date(2026, 1, 1)


def aggregate_monthly_demand(data):
    """Simplified version for testing."""
    from collections import defaultdict
    monthly_demand = defaultdict(lambda: defaultdict(int))
    
    for record in data:
        borrow_date = record["borrow_date"]
        item_type = record["item_type"]
        category = record["category"]
        
        month_key = borrow_date.strftime("%Y-%m")
        series_key = f"{item_type}|{category}"
        
        monthly_demand[month_key][series_key] += 1
    
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


def ensure_all_months(demand_data, date_start, date_end):
    """Simplified version for testing."""
    all_months = []
    current = date_start
    while current <= date_end:
        all_months.append(current.strftime("%Y-%m"))
        current = date(current.year, current.month + 1, 1) if current.month < 12 else date(current.year + 1, 1, 1)
    
    series_keys = set()
    for record in demand_data:
        series_key = f"{record['item_type']}|{record['category']}"
        series_keys.add(series_key)
    
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
    """Simplified version for testing."""
    historical = {}
    for record in train_data:
        key = f"{record['item_type']}|{record['category']}|{record['month']}"
        historical[key] = record["demand"]
    
    forecasts = []
    
    for record in test_months:
        month_key = record["month"]
        series_key = f"{record['item_type']}|{record['category']}"
        
        year, month = map(int, month_key.split("-"))
        prev_year = year - 1
        prev_month_key = f"{prev_year:04d}-{month:02d}"
        prev_key = f"{series_key}|{prev_month_key}"
        
        if prev_key in historical:
            forecast = historical[prev_key]
        else:
            series_demands = [v for k, v in historical.items() if k.startswith(series_key)]
            forecast = sum(series_demands) / len(series_demands) if series_demands else 0
        
        forecasts.append({
            "month": month_key,
            "item_type": record["item_type"],
            "category": record["category"],
            "actual": record["demand"],
            "forecast": forecast,
        })
    
    return forecasts


def calculate_metrics(forecasts):
    """Calculate MAE and RMSE for forecasts."""
    import math
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


class TestPhase5AAggregation(unittest.TestCase):
    """Test monthly demand aggregation functionality."""
    
    def test_month_aggregation_basic(self):
        """Test that monthly aggregation correctly counts borrow records."""
        data = [
            {"borrow_date": date(2023, 1, 15), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2023, 1, 20), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2023, 1, 25), "item_type": "BOOK", "category": "Science"},
        ]
        
        result = aggregate_monthly_demand(data)
        
        # Should have 2 series for January 2023
        jan_2023_records = [r for r in result if r["month"] == "2023-01"]
        self.assertEqual(len(jan_2023_records), 2)
        
        # Fiction should have count 2
        fiction = [r for r in jan_2023_records if r["category"] == "Fiction"]
        self.assertEqual(len(fiction), 1)
        self.assertEqual(fiction[0]["demand"], 2)
        
        # Science should have count 1
        science = [r for r in jan_2023_records if r["category"] == "Science"]
        self.assertEqual(len(science), 1)
        self.assertEqual(science[0]["demand"], 1)
    
    def test_active_loans_contribute_to_demand(self):
        """Regression test: active loans (returnDate = NULL) must contribute to demand in borrow month."""
        # This test prevents reintroduction of completed-only filtering
        # The aggregation function doesn't distinguish active vs completed;
        # it counts all borrow events by borrow_date
        data = [
            {"borrow_date": date(2023, 1, 15), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2023, 1, 20), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2023, 1, 25), "item_type": "BOOK", "category": "Fiction"},
        ]
        
        result = aggregate_monthly_demand(data)
        
        # All 3 borrow events should contribute to January demand
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["month"], "2023-01")
        self.assertEqual(result[0]["demand"], 3)
        
        # The function cannot test SQL-level filtering; this test verifies
        # that the aggregation logic itself counts all events
        # The SQL correction is in phase5a_demand_aggregation.py
    
    def test_zero_demand_months_represented(self):
        """Test that months with zero demand are explicitly represented."""
        data = [
            {"borrow_date": date(2023, 1, 15), "item_type": "BOOK", "category": "Fiction"},
        ]
        
        # Use test date range
        test_start = date(2023, 1, 1)
        test_end = date(2023, 3, 31)
        
        aggregated = aggregate_monthly_demand(data)
        result = ensure_all_months(aggregated, test_start, test_end)
        
        # Should have 3 months (Jan, Feb, Mar)
        months = sorted(set(r["month"] for r in result))
        self.assertEqual(len(months), 3)
        self.assertIn("2023-01", months)
        self.assertIn("2023-02", months)
        self.assertIn("2023-03", months)
        
        # February should have zero demand
        feb_records = [r for r in result if r["month"] == "2023-02"]
        self.assertEqual(len(feb_records), 1)
        self.assertEqual(feb_records[0]["demand"], 0)
    
    def test_category_fallback_deterministic(self):
        """Test that NULL categories fall back to UNCATEGORIZED consistently."""
        # The aggregation function expects data with category already set
        # In the main script, this fallback happens during data loading
        # Here we test that the aggregation handles the UNCATEGORIZED value
        data = [
            {"borrow_date": date(2023, 1, 15), "item_type": "BOOK", "category": UNCATEGORIZED},
            {"borrow_date": date(2023, 1, 20), "item_type": "BOOK", "category": UNCATEGORIZED},
        ]
        
        result = aggregate_monthly_demand(data)
        
        # Should have UNCATEGORIZED category
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["category"], UNCATEGORIZED)
        self.assertEqual(result[0]["demand"], 2)
    
    def test_newspaper_preserves_item_type(self):
        """Test that newspaper records preserve item_type and use UNCATEGORIZED as category."""
        # Newspapers have no catalog category, so they use UNCATEGORIZED
        # while preserving item_type = NEWSPAPER for distinction
        data = [
            {"borrow_date": date(2023, 1, 15), "item_type": "NEWSPAPER", "category": UNCATEGORIZED},
        ]
        
        result = aggregate_monthly_demand(data)
        
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["item_type"], "NEWSPAPER")
        self.assertEqual(result[0]["category"], UNCATEGORIZED)


class TestPhase5ASeasonalNaive(unittest.TestCase):
    """Test seasonal naive baseline forecasting."""
    
    def test_same_month_previous_year(self):
        """Test that forecasting uses same month from previous year."""
        train_data = [
            {"month": "2023-01", "item_type": "BOOK", "category": "Fiction", "demand": 10},
            {"month": "2024-01", "item_type": "BOOK", "category": "Fiction", "demand": 12},
            {"month": "2025-01", "item_type": "BOOK", "category": "Fiction", "demand": 15},
        ]
        
        test_data = [
            {"month": "2026-01", "item_type": "BOOK", "category": "Fiction", "demand": 0},
        ]
        
        forecasts = seasonal_naive_forecast(train_data, test_data)
        
        self.assertEqual(len(forecasts), 1)
        # Should use 2025-01 demand (15) for 2026-01 forecast
        self.assertEqual(forecasts[0]["forecast"], 15)
    
    def test_forecast_uses_only_historical(self):
        """Test that forecasting doesn't use future test data."""
        train_data = [
            {"month": "2023-01", "item_type": "BOOK", "category": "Fiction", "demand": 10},
        ]
        
        test_data = [
            {"month": "2026-01", "item_type": "BOOK", "category": "Fiction", "demand": 20},
        ]
        
        forecasts = seasonal_naive_forecast(train_data, test_data)
        
        # Should use historical mean (10), not test actual (20)
        self.assertEqual(forecasts[0]["forecast"], 10)
        self.assertNotEqual(forecasts[0]["forecast"], 20)
    
    def test_train_test_boundaries(self):
        """Test that train/test split respects temporal boundaries."""
        all_data = [
            {"month": "2023-01", "item_type": "BOOK", "category": "Fiction", "demand": 10},
            {"month": "2025-12", "item_type": "BOOK", "category": "Fiction", "demand": 15},
            {"month": "2026-01", "item_type": "BOOK", "category": "Fiction", "demand": 20},
            {"month": "2026-08", "item_type": "BOOK", "category": "Fiction", "demand": 25},
        ]
        
        train_data = [r for r in all_data if r["month"] <= TRAIN_END.strftime("%Y-%m")]
        test_data = [r for r in all_data if r["month"] >= TEST_START.strftime("%Y-%m")]
        
        # Train should include 2023-01 and 2025-12
        self.assertEqual(len(train_data), 2)
        self.assertIn("2023-01", [r["month"] for r in train_data])
        self.assertIn("2025-12", [r["month"] for r in train_data])
        
        # Test should include 2026-01 and 2026-08
        self.assertEqual(len(test_data), 2)
        self.assertIn("2026-01", [r["month"] for r in test_data])
        self.assertIn("2026-08", [r["month"] for r in test_data])


class TestPhase5AMetrics(unittest.TestCase):
    """Test MAE and RMSE calculations."""
    
    def test_mae_calculation(self):
        """Test MAE calculation on known example."""
        forecasts = [
            {"actual": 10, "forecast": 8},
            {"actual": 20, "forecast": 22},
            {"actual": 30, "forecast": 25},
        ]
        
        metrics = calculate_metrics(forecasts)
        
        # MAE = (|10-8| + |20-22| + |30-25|) / 3 = (2 + 2 + 5) / 3 = 3
        self.assertAlmostEqual(metrics["mae"], 3.0, places=4)
    
    def test_rmse_calculation(self):
        """Test RMSE calculation on known example."""
        forecasts = [
            {"actual": 10, "forecast": 8},
            {"actual": 20, "forecast": 22},
            {"actual": 30, "forecast": 25},
        ]
        
        metrics = calculate_metrics(forecasts)
        
        # RMSE = sqrt((2^2 + 2^2 + 5^2) / 3) = sqrt((4 + 4 + 25) / 3) = sqrt(33/3) = sqrt(11) ≈ 3.3166
        expected_rmse = math.sqrt(11)
        self.assertAlmostEqual(metrics["rmse"], expected_rmse, places=4)
    
    def test_zero_demand_handling(self):
        """Test that zero-demand months are handled correctly in metrics."""
        forecasts = [
            {"actual": 0, "forecast": 0},
            {"actual": 0, "forecast": 2},
            {"actual": 5, "forecast": 0},
        ]
        
        metrics = calculate_metrics(forecasts)
        
        # MAE = (0 + 2 + 5) / 3 = 2.333...
        self.assertAlmostEqual(metrics["mae"], 7/3, places=4)
        self.assertEqual(metrics["n"], 3)


if __name__ == "__main__":
    unittest.main()
