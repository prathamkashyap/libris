#!/usr/bin/env python3
"""
Tests for Phase 5A monthly demand aggregation and the shared forecasting core.

These tests import the shipped implementation from ``phase5_forecasting`` and
``phase5a_demand_aggregation``. Nothing is reimplemented here, so a stale copy of
the logic cannot pass while the real code is broken.

Coverage:
- month arithmetic, including the year-boundary wrap the t-12 lookup relies on
- record classification: item type, NULL-category fallback, UNCATEGORIZED
- monthly aggregation and grid densification
- seasonal lookup keyed by month (regression for the series-only-key overwrite)
- static-holdout and rolling-origin information sets
- forecast metrics

Run:  python3 -m unittest discover -s scripts/dev-seed -p "test_phase5*.py"
"""
import os
import sys
import unittest
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import phase5_forecasting as f  # noqa: E402
import phase5a_demand_aggregation as p5a  # noqa: E402


def record(year, month, item_type, category, demand):
    """One demand-grid row, as densify_monthly_grid would produce."""
    return {
        "month": f"{year:04d}-{month:02d}",
        "item_type": item_type,
        "category": category,
        "demand": demand,
    }


def borrow(borrow_date, book_id=None, magazine_id=None, newspaper_id=None,
           book_category=None, magazine_category=None):
    """One raw borrow_records row, in the column order of the load query."""
    return (borrow_date, book_id, magazine_id, newspaper_id, book_category, magazine_category)


class TestMonthArithmetic(unittest.TestCase):
    """Month keys must be comparable and must wrap across year boundaries."""

    def test_month_key_formats_iso_month(self):
        self.assertEqual(f.month_key(date(2026, 8, 31)), "2026-08")
        self.assertEqual(f.month_key(date(2023, 1, 1)), "2023-01")

    def test_shift_month_moves_backwards(self):
        self.assertEqual(f.shift_month("2026-03", -1), "2026-02")
        self.assertEqual(f.shift_month("2026-03", -12), "2025-03")

    def test_shift_month_wraps_january_into_previous_year(self):
        self.assertEqual(f.shift_month("2026-01", -1), "2025-12")
        self.assertEqual(f.shift_month("2026-01", -12), "2025-01")

    def test_shift_month_wraps_december_into_next_year(self):
        self.assertEqual(f.shift_month("2025-12", 1), "2026-01")
        self.assertEqual(f.shift_month("2025-12", 13), "2027-01")

    def test_shift_month_zero_is_identity(self):
        self.assertEqual(f.shift_month("2026-07", 0), "2026-07")

    def test_twelve_month_lag_preserves_calendar_month(self):
        for month in range(1, 13):
            self.assertEqual(
                f.parse_month(f.shift_month(f"2026-{month:02d}", -12)),
                (2025, month),
            )

    def test_month_sequence_is_inclusive(self):
        self.assertEqual(
            f.month_sequence("2025-11", "2026-02"),
            ["2025-11", "2025-12", "2026-01", "2026-02"],
        )

    def test_month_sequence_is_empty_when_inverted(self):
        self.assertEqual(f.month_sequence("2026-02", "2026-01"), [])

    def test_compare_months_orders_across_years(self):
        self.assertEqual(f.compare_months("2025-12", "2026-01"), -1)
        self.assertEqual(f.compare_months("2026-01", "2025-12"), 1)
        self.assertEqual(f.compare_months("2026-01", "2026-01"), 0)


class TestRecordClassification(unittest.TestCase):
    """Polymorphic borrow_records rows map to the right demand series."""

    def test_book_uses_its_catalog_category(self):
        row = borrow(date(2026, 3, 2), book_id=7, book_category="Fiction")
        self.assertEqual(
            f.classify_borrow_row(*row),
            {"borrow_date": date(2026, 3, 2), "item_type": "BOOK", "category": "Fiction"},
        )

    def test_magazine_uses_its_catalog_category(self):
        row = borrow(date(2026, 3, 2), magazine_id=4, magazine_category="Science")
        classified = f.classify_borrow_row(*row)
        self.assertEqual(classified["item_type"], "MAGAZINE")
        self.assertEqual(classified["category"], "Science")

    def test_book_with_null_category_falls_back_to_uncategorized(self):
        row = borrow(date(2026, 3, 2), book_id=7, book_category=None)
        self.assertEqual(f.classify_borrow_row(*row)["category"], f.UNCATEGORIZED)

    def test_magazine_with_null_category_falls_back_to_uncategorized(self):
        row = borrow(date(2026, 3, 2), magazine_id=4, magazine_category=None)
        self.assertEqual(f.classify_borrow_row(*row)["category"], f.UNCATEGORIZED)

    def test_empty_string_category_falls_back_to_uncategorized(self):
        row = borrow(date(2026, 3, 2), book_id=7, book_category="")
        self.assertEqual(f.classify_borrow_row(*row)["category"], f.UNCATEGORIZED)

    def test_newspaper_is_always_uncategorized(self):
        row = borrow(date(2026, 3, 2), newspaper_id=2, book_category="ignored")
        classified = f.classify_borrow_row(*row)
        self.assertEqual(classified["item_type"], "NEWSPAPER")
        self.assertEqual(classified["category"], f.UNCATEGORIZED)

    def test_row_without_any_item_reference_is_skipped(self):
        self.assertIsNone(f.classify_borrow_row(*borrow(date(2026, 3, 2))))

    def test_book_wins_when_several_fks_are_populated(self):
        row = borrow(date(2026, 3, 2), book_id=7, magazine_id=4, newspaper_id=2,
                     book_category="Fiction", magazine_category="Science")
        classified = f.classify_borrow_row(*row)
        self.assertEqual(classified["item_type"], "BOOK")
        self.assertEqual(classified["category"], "Fiction")

    def test_uncategorized_books_form_a_single_shared_series(self):
        """NULL-category books must merge into one series, not one per title."""
        classified = [
            f.classify_borrow_row(*borrow(date(2026, 3, i + 1), book_id=i + 1, book_category=None))
            for i in range(5)
        ]
        keys = {f.series_key(c["item_type"], c["category"]) for c in classified}
        self.assertEqual(keys, {f"BOOK|{f.UNCATEGORIZED}"})


class TestAggregation(unittest.TestCase):
    """Monthly aggregation and grid densification."""

    def test_counts_all_borrow_events_by_month(self):
        raw = [
            {"borrow_date": date(2026, 3, 2), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2026, 3, 20), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2026, 4, 1), "item_type": "BOOK", "category": "Fiction"},
        ]
        rows = f.aggregate_monthly_demand(raw)
        self.assertEqual(
            rows,
            [
                {"month": "2026-03", "item_type": "BOOK", "category": "Fiction", "demand": 2},
                {"month": "2026-04", "item_type": "BOOK", "category": "Fiction", "demand": 1},
            ],
        )

    def test_series_are_kept_separate(self):
        raw = [
            {"borrow_date": date(2026, 3, 1), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2026, 3, 1), "item_type": "BOOK", "category": "History"},
        ]
        rows = f.aggregate_monthly_demand(raw)
        self.assertEqual(len(rows), 2)
        self.assertEqual({r["category"] for r in rows}, {"Fiction", "History"})

    def test_densify_fills_missing_month_with_zero(self):
        rows = [record(2026, 3, "BOOK", "Fiction", 5)]
        grid = f.densify_monthly_grid(rows, "2026-01", "2026-03")
        self.assertEqual(
            [(r["month"], r["demand"]) for r in grid],
            [("2026-01", 0), ("2026-02", 0), ("2026-03", 5)],
        )

    def test_densify_covers_every_series_in_every_month(self):
        rows = [
            record(2026, 1, "BOOK", "Fiction", 1),
            record(2026, 3, "BOOK", "History", 1),
        ]
        grid = f.densify_monthly_grid(rows, "2026-01", "2026-03")
        self.assertEqual(len(grid), 3 * 2)
        self.assertEqual(
            sum(r["demand"] for r in grid), 2, "densification must not invent demand"
        )

    def test_densify_does_not_create_months_past_the_observed_end(self):
        """A month the generator never emitted is missing data, not zero demand."""
        rows = [record(2026, 7, "BOOK", "Fiction", 4)]
        grid = f.densify_monthly_grid(rows, "2026-07", "2026-07")
        self.assertEqual([r["month"] for r in grid], ["2026-07"])

    def test_observed_month_range_reads_raw_records(self):
        raw = [
            {"borrow_date": date(2023, 5, 1), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2026, 7, 14), "item_type": "BOOK", "category": "Fiction"},
        ]
        self.assertEqual(f.observed_month_range(raw), ("2023-05", "2026-07"))

    def test_observed_month_range_on_empty_input(self):
        self.assertEqual(f.observed_month_range([]), (None, None))


class TestSeasonalLookup(unittest.TestCase):
    """Regression coverage for the month-keyed seasonal lookup."""

    def test_history_key_includes_month(self):
        history = f.build_history([
            record(2023, 1, "BOOK", "Fiction", 10),
            record(2024, 1, "BOOK", "Fiction", 15),
        ])
        self.assertEqual(history[("BOOK|Fiction", "2023-01")], 10)
        self.assertEqual(history[("BOOK|Fiction", "2024-01")], 15)

    def test_series_only_key_would_overwrite_months(self):
        """Documents the defect the month key fixes: later months clobber earlier."""
        history = f.build_history([
            record(2023, 1, "BOOK", "Fiction", 10),
            record(2024, 1, "BOOK", "Fiction", 15),
        ])
        series_only = {}
        for (series, _month), value in history.items():
            series_only[series] = value  # what a series-only key does
        self.assertEqual(series_only["BOOK|Fiction"], 15)
        self.assertEqual(len(history), 2, "month key must keep both observations")

    def test_forecast_uses_same_month_previous_year(self):
        grid = [
            record(2023, 1, "BOOK", "Fiction", 10),
            record(2024, 1, "BOOK", "Fiction", 12),
            record(2025, 1, "BOOK", "Fiction", 15),
        ]
        result = f.forecast_point(
            f.build_history(grid), "BOOK|Fiction", "2026-01", "2025-12",
            f.STATIC_HOLDOUT, "seasonal_naive",
        )
        self.assertEqual(result["forecast"], 15)
        self.assertEqual(result["source"], f.SOURCE_SEASONAL)

    def test_forecast_does_not_average_the_year(self):
        """t-12 is a point lookup, not a 12-month mean."""
        grid = [record(2025, m, "BOOK", "Fiction", 100) for m in range(1, 13)]
        grid[11] = record(2025, 12, "BOOK", "Fiction", 4)
        result = f.forecast_point(
            f.build_history(grid), "BOOK|Fiction", "2026-12", "2025-12",
            f.STATIC_HOLDOUT, "seasonal_naive",
        )
        self.assertEqual(result["forecast"], 4, "must be the single t-12 observation")
        self.assertEqual(result["window_requested"], 1)

    def test_forecast_is_zero_when_series_absent(self):
        result = f.forecast_point(
            {}, "BOOK|Fiction", "2026-01", "2025-12", f.STATIC_HOLDOUT, "seasonal_naive",
        )
        self.assertEqual(result["forecast"], 0.0)
        self.assertEqual(result["source"], f.SOURCE_ZERO)

    def test_forecast_all_emits_audit_columns(self):
        grid = [record(2025, 1, "BOOK", "Fiction", 15)]
        test = [record(2026, 1, "BOOK", "Fiction", 18)]
        rows = f.forecast_all(grid, test, "2025-12", f.STATIC_HOLDOUT, "seasonal_naive")
        self.assertEqual(len(rows), 1)
        for field in ("actual", "forecast", "source", "protocol", "window_requested",
                      "window_covered", "window_start", "window_end"):
            self.assertIn(field, rows[0])
        self.assertEqual(rows[0]["actual"], 18)
        self.assertEqual(rows[0]["window_start"], "2025-01")
        self.assertEqual(rows[0]["window_end"], "2025-01")

    def test_unknown_method_is_rejected(self):
        with self.assertRaises(ValueError):
            f.forecast_point({}, "BOOK|Fiction", "2026-01", "2025-12",
                             f.STATIC_HOLDOUT, "arima")

    def test_unknown_protocol_is_rejected(self):
        grid = [record(2025, 1, "BOOK", "Fiction", 15)]
        with self.assertRaises(ValueError):
            f.forecast_all(grid, grid, "2025-12", "k-fold", "seasonal_naive")


class TestPhase5ABoundaries(unittest.TestCase):
    """The Phase 5A static-holdout split and data-coverage guard."""

    def test_split_uses_configured_origin(self):
        grid = [
            record(2023, 1, "BOOK", "Fiction", 1),
            record(2025, 12, "BOOK", "Fiction", 2),
            record(2026, 1, "BOOK", "Fiction", 3),
            record(2026, 7, "BOOK", "Fiction", 4),
        ]
        train, test, origin = p5a.split_train_test(grid)
        self.assertEqual(origin, "2025-12")
        self.assertEqual([r["month"] for r in train], ["2023-01", "2025-12"])
        self.assertEqual([r["month"] for r in test], ["2026-01", "2026-07"])

    def test_origin_month_is_inclusive_in_training(self):
        grid = [record(2025, 12, "BOOK", "Fiction", 2)]
        train, test, _ = p5a.split_train_test(grid)
        self.assertEqual(len(train), 1)
        self.assertEqual(test, [])

    def test_grid_bounds_clip_to_observed_data(self):
        """2026-08 is configured but never generated; it must be excluded."""
        raw = [
            {"borrow_date": date(2023, 1, 5), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2026, 7, 14), "item_type": "BOOK", "category": "Fiction"},
        ]
        first, last, truncated = p5a.resolve_grid_bounds(raw)
        self.assertEqual(first, "2023-01")
        self.assertEqual(last, "2026-07")
        self.assertEqual(truncated, ["2026-08"])

    def test_grid_bounds_do_not_extend_past_configured_end(self):
        raw = [
            {"borrow_date": date(2026, 1, 5), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2027, 6, 1), "item_type": "BOOK", "category": "Fiction"},
        ]
        _first, last, truncated = p5a.resolve_grid_bounds(raw)
        self.assertEqual(last, f.month_key(p5a.DATE_END))
        self.assertEqual(truncated, [])

    def test_grid_bounds_reject_empty_dataset(self):
        with self.assertRaises(SystemExit):
            p5a.resolve_grid_bounds([])

    def test_clipped_grid_excludes_the_fabricated_month(self):
        """End-to-end: the unobserved month never becomes a scored zero row."""
        raw = [
            {"borrow_date": date(2023, 1, 5), "item_type": "BOOK", "category": "Fiction"},
            {"borrow_date": date(2026, 7, 14), "item_type": "BOOK", "category": "Fiction"},
        ]
        first, last, _ = p5a.resolve_grid_bounds(raw)
        grid = f.densify_monthly_grid(f.aggregate_monthly_demand(raw), first, last)
        self.assertNotIn("2026-08", {r["month"] for r in grid})
        _train, test, origin = p5a.split_train_test(grid)
        rows = f.forecast_all(grid, test, origin, f.STATIC_HOLDOUT, "seasonal_naive")
        self.assertNotIn("2026-08", {r["month"] for r in rows})


class TestCalendarRegime(unittest.TestCase):
    """Synthetic regime labels must mirror seed_generator.semester_factor()."""

    def test_break_months(self):
        for month in (1, 2, 7, 8):
            self.assertEqual(f.calendar_regime(f"2023-{month:02d}"), "BREAK")

    def test_normal_months(self):
        for month in (3, 4, 5, 9, 10, 11):
            self.assertEqual(f.calendar_regime(f"2023-{month:02d}"), "NORMAL")

    def test_reduced_months(self):
        for month in (6, 12):
            self.assertEqual(f.calendar_regime(f"2023-{month:02d}"), "REDUCED")

    def test_regime_is_year_independent(self):
        for month in range(1, 13):
            self.assertEqual(
                f.calendar_regime(f"2023-{month:02d}"), f.calendar_regime(f"2026-{month:02d}")
            )

    def test_add_calendar_regime_labels_every_row(self):
        grid = f.add_calendar_regime([
            record(2026, 1, "BOOK", "Fiction", 1),
            record(2026, 6, "BOOK", "Fiction", 1),
        ])
        self.assertEqual([r["regime"] for r in grid], ["BREAK", "REDUCED"])

    def test_regime_propagates_into_forecasts(self):
        grid = f.add_calendar_regime([record(2025, 1, "BOOK", "Fiction", 15)])
        test = f.add_calendar_regime([record(2026, 1, "BOOK", "Fiction", 18)])
        rows = f.forecast_all(grid, test, "2025-12", f.STATIC_HOLDOUT, "seasonal_naive")
        self.assertEqual(rows[0]["regime"], "BREAK")


class TestMetrics(unittest.TestCase):
    """MAE/RMSE and coverage accounting."""

    def test_mae(self):
        rows = [
            {"actual": 10, "forecast": 8},
            {"actual": 20, "forecast": 22},
            {"actual": 30, "forecast": 25},
        ]
        self.assertAlmostEqual(f.metrics(rows)["mae"], (2 + 2 + 5) / 3, places=9)

    def test_rmse(self):
        rows = [
            {"actual": 10, "forecast": 8},
            {"actual": 20, "forecast": 22},
            {"actual": 30, "forecast": 25},
        ]
        self.assertAlmostEqual(f.metrics(rows)["rmse"], (11 ** 0.5), places=9)

    def test_perfect_forecast_scores_zero(self):
        rows = [{"actual": 7, "forecast": 7}] * 5
        self.assertEqual(f.metrics(rows)["mae"], 0.0)
        self.assertEqual(f.metrics(rows)["rmse"], 0.0)

    def test_zero_demand_months_are_scored(self):
        rows = [
            {"actual": 0, "forecast": 0},
            {"actual": 0, "forecast": 2},
            {"actual": 5, "forecast": 0},
        ]
        result = f.metrics(rows)
        self.assertAlmostEqual(result["mae"], 7 / 3, places=9)
        self.assertEqual(result["n"], 3)

    def test_metrics_on_empty_input(self):
        result = f.metrics([])
        self.assertEqual((result["mae"], result["rmse"], result["n"]), (0.0, 0.0, 0))

    def test_metrics_by_groups(self):
        rows = [
            {"actual": 10, "forecast": 8, "regime": "BREAK"},
            {"actual": 20, "forecast": 20, "regime": "NORMAL"},
        ]
        grouped = f.metrics_by(rows, "regime")
        self.assertEqual(grouped["BREAK"]["mae"], 2.0)
        self.assertEqual(grouped["NORMAL"]["mae"], 0.0)

    def test_coverage_counts_full_partial_and_fallback(self):
        rows = [
            {"window_requested": 3, "window_covered": 3, "source": f.SOURCE_TRAILING},
            {"window_requested": 3, "window_covered": 2, "source": f.SOURCE_TRAILING},
            {"window_requested": 3, "window_covered": 0, "source": f.SOURCE_SERIES_MEAN},
        ]
        result = f.coverage(rows)
        self.assertEqual(result["points"], 3)
        self.assertEqual(result["full_window"], 1)
        self.assertEqual(result["partial_window"], 1)
        self.assertEqual(result["fallback"], 1)
        self.assertAlmostEqual(result["full_window_pct"], 100 / 3, places=6)

    def test_coverage_on_empty_input(self):
        self.assertEqual(f.coverage([])["points"], 0)


if __name__ == "__main__":
    unittest.main()
