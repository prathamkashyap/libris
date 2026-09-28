#!/usr/bin/env python3
"""
Tests for Phase 5B baselines and the evaluation protocol.

These tests import the shipped implementation from ``phase5_forecasting`` and
``phase5b_calendar_baselines``. Nothing is reimplemented here, so a stale copy of
the logic cannot pass while the real code is broken.

The centre of gravity is the moving-average protocol. The original Phase 5B
implementation let the W-month window slide off the end of the frozen training
data and silently substituted a mean over the whole training history, so
"3-month MA" and "6-month MA" were not moving averages for most of the horizon.
These tests pin the corrected behaviour:

- static holdout anchors the window at the origin and holds it constant
- rolling origin anchors the window at the target month and slides it
- both give full window coverage for the whole horizon
- neither can ever read the target month or a later month
- the series-mean fallback still works when a window genuinely cannot be filled

Coverage additionally includes UNCATEGORIZED series behaviour and per-regime
metric grouping.

Run:  python3 -m unittest discover -s scripts/dev-seed -p "test_phase5*.py"
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import phase5_forecasting as f  # noqa: E402
import phase5b_calendar_baselines as p5b  # noqa: E402

ORIGIN = "2025-12"
SERIES = "BOOK|Fiction"


def record(month, demand, item_type="BOOK", category="Fiction"):
    """One demand-grid row."""
    year, mon = month.split("-")
    return {
        "month": month,
        "item_type": item_type,
        "category": category,
        "demand": demand,
    }


def flat_grid(first_month, last_month, values, series=SERIES, item_type="BOOK", category="Fiction"):
    """Densified grid with an explicit demand value per month."""
    return [
        record(m, values.get(m, 0), item_type, category)
        for m in f.month_sequence(first_month, last_month)
    ]


class TestMovingAverageWindow(unittest.TestCase):
    """Window arithmetic for the trailing-mean baseline."""

    def test_three_month_window_is_the_three_prior_months(self):
        grid = [
            record("2025-10", 8),
            record("2025-11", 9),
            record("2025-12", 10),
        ]
        result = f.forecast_point(
            f.build_history(grid), SERIES, "2026-01", ORIGIN,
            f.STATIC_HOLDOUT, "trailing_mean", window=3,
        )
        self.assertEqual(result["forecast"], 9.0)
        self.assertEqual(result["window_start"], "2025-10")
        self.assertEqual(result["window_end"], "2025-12")
        self.assertEqual(result["window_covered"], 3)

    def test_six_month_window_is_the_six_prior_months(self):
        values = {"2025-07": 5, "2025-08": 6, "2025-09": 7,
                  "2025-10": 8, "2025-11": 9, "2025-12": 10}
        result = f.forecast_point(
            f.build_history(flat_grid("2025-07", "2025-12", values)), SERIES,
            "2026-01", ORIGIN, f.STATIC_HOLDOUT, "trailing_mean", window=6,
        )
        self.assertEqual(result["forecast"], 7.5)
        self.assertEqual(result["window_start"], "2025-07")
        self.assertEqual(result["window_covered"], 6)

    def test_window_spans_the_year_boundary(self):
        """A window reaching back past January must wrap into the prior year."""
        values = {"2025-11": 4, "2025-12": 6, "2026-01": 100}
        grid = flat_grid("2025-11", "2026-01", values)
        result = f.forecast_point(
            f.build_history(grid), SERIES, "2026-02", ORIGIN,
            f.ROLLING_ORIGIN, "trailing_mean", window=3,
        )
        self.assertEqual(result["window_start"], "2025-11")
        self.assertEqual(result["window_end"], "2026-01")
        self.assertEqual(result["forecast"], (4 + 6 + 100) / 3)

    def test_older_months_outside_the_window_are_ignored(self):
        values = {"2025-01": 999, "2025-10": 8, "2025-11": 9, "2025-12": 10}
        result = f.forecast_point(
            f.build_history(flat_grid("2025-01", "2025-12", values)), SERIES,
            "2026-01", ORIGIN, f.STATIC_HOLDOUT, "trailing_mean", window=3,
        )
        self.assertEqual(result["forecast"], 9.0)

    def test_window_of_one_is_the_last_month(self):
        values = {"2025-11": 7, "2025-12": 10}
        result = f.forecast_point(
            f.build_history(flat_grid("2025-11", "2025-12", values)), SERIES,
            "2026-01", ORIGIN, f.STATIC_HOLDOUT, "trailing_mean", window=1,
        )
        self.assertEqual(result["forecast"], 10)

    def test_window_uses_only_prior_months(self):
        """The target month's own demand must never enter its own forecast."""
        values = {"2025-10": 8, "2025-11": 9, "2025-12": 10, "2026-01": 500}
        result = f.forecast_point(
            f.build_history(flat_grid("2025-10", "2026-01", values)), SERIES,
            "2026-01", ORIGIN, f.STATIC_HOLDOUT, "trailing_mean", window=3,
        )
        self.assertEqual(result["forecast"], 9.0)

    def test_missing_window_is_required(self):
        with self.assertRaises(ValueError):
            f.forecast_point({}, SERIES, "2026-01", ORIGIN,
                             f.STATIC_HOLDOUT, "trailing_mean", window=0)


class TestStaticHoldoutAnchoring(unittest.TestCase):
    """
    Regression: the window must not slide off the end of the frozen training
    data. Under a static holdout it is anchored at the origin for the whole
    horizon, which is what keeps "3-month MA" a 3-month moving average.
    """

    def setUp(self):
        # 36 training months, then a 7-month horizon.
        self.training = flat_grid("2023-01", "2025-12",
                                  {f"{y}-{m:02d}": 10 for y in (2023, 2024, 2025)
                                   for m in range(1, 13)})
        self.grid = self.training + [record(m, 10) for m in
                                     f.month_sequence("2026-01", "2026-07")]
        self.test = [record(m, 10) for m in f.month_sequence("2026-01", "2026-07")]

    def test_anchor_is_the_origin_for_every_horizon_month(self):
        for window in (3, 6):
            for month in [r["month"] for r in self.test]:
                point = f.forecast_point(
                    f.build_history(self.grid), SERIES, month, ORIGIN,
                    f.STATIC_HOLDOUT, "trailing_mean", window=window,
                )
                self.assertEqual(point["window_end"], ORIGIN,
                                 f"window={window} month={month} must anchor at origin")
                self.assertEqual(point["window_covered"], window)

    def test_forecast_is_constant_across_the_horizon(self):
        """A frozen-origin level estimate is the same number every month."""
        rows = f.forecast_all(self.grid, self.test, ORIGIN,
                              f.STATIC_HOLDOUT, "trailing_mean", window=3)
        self.assertEqual(len({r["forecast"] for r in rows}), 1)

    def test_no_fallbacks_anywhere_in_the_horizon(self):
        """The defect this test guards: silent substitution of a series mean."""
        for window in (3, 6):
            rows = f.forecast_all(self.grid, self.test, ORIGIN,
                                  f.STATIC_HOLDOUT, "trailing_mean", window=window)
            cov = f.coverage(rows)
            self.assertEqual(cov["fallback"], 0, f"window={window} fell back")
            self.assertEqual(cov["full_window"], cov["points"], f"window={window}")
            self.assertEqual(cov["full_window_pct"], 100.0)

    def test_later_months_still_use_a_full_window(self):
        """
        The concrete regression: with the window sliding, 2026-04 and beyond
        had zero visible window months and collapsed to a 36-month mean.
        """
        for window, degenerate_from in ((3, "2026-04"), (6, "2026-07")):
            for month in f.month_sequence(degenerate_from, "2026-08"):
                point = f.forecast_point(
                    f.build_history(self.grid), SERIES, month, ORIGIN,
                    f.STATIC_HOLDOUT, "trailing_mean", window=window,
                )
                self.assertEqual(point["window_covered"], window,
                                 f"{month} lost its window with window={window}")
                self.assertEqual(point["source"], f.SOURCE_TRAILING)

    def test_static_holdout_cannot_see_test_period_data(self):
        """Test-period demand must be invisible even though it is in the grid."""
        poisoned = list(self.grid)
        for r in poisoned:
            if r["month"] >= "2026-01":
                r["demand"] = 9999
        clean = f.forecast_all(self.grid, self.test, ORIGIN,
                               f.STATIC_HOLDOUT, "trailing_mean", window=3)
        dirty = f.forecast_all(poisoned, self.test, ORIGIN,
                               f.STATIC_HOLDOUT, "trailing_mean", window=3)
        self.assertEqual([r["forecast"] for r in clean], [r["forecast"] for r in dirty])

    def test_static_holdout_matches_an_explicit_last_window_mean(self):
        values = {m: 100 + i for i, m in enumerate(f.month_sequence("2023-01", "2025-12"))}
        grid = flat_grid("2023-01", "2025-12", values)
        test = [record(m, 0) for m in f.month_sequence("2026-01", "2026-07")]
        expected = sum(values[m] for m in f.month_sequence("2025-10", "2025-12")) / 3
        rows = f.forecast_all(grid, test, ORIGIN, f.STATIC_HOLDOUT,
                              "trailing_mean", window=3)
        self.assertTrue(all(abs(r["forecast"] - expected) < 1e-9 for r in rows))


class TestRollingOriginAnchoring(unittest.TestCase):
    """Rolling origin: the window slides with the target month."""

    def setUp(self):
        self.training = flat_grid("2023-01", "2025-12",
                                  {m: 10 for m in f.month_sequence("2023-01", "2025-12")})
        self.grid = self.training + [record(m, 0) for m in
                                     f.month_sequence("2026-01", "2026-07")]
        self.test = [record(m, 0) for m in f.month_sequence("2026-01", "2026-07")]

    def test_window_ends_at_the_prior_month(self):
        for month in [r["month"] for r in self.test]:
            point = f.forecast_point(
                f.build_history(self.grid), SERIES, month, ORIGIN,
                f.ROLLING_ORIGIN, "trailing_mean", window=3,
            )
            self.assertEqual(point["window_end"], f.shift_month(month, -1))

    def test_window_slides_forward_across_the_horizon(self):
        rows = f.forecast_all(self.grid, self.test, ORIGIN,
                              f.ROLLING_ORIGIN, "trailing_mean", window=3)
        self.assertEqual([r["window_end"] for r in rows],
                         [f.shift_month(m, -1) for m in
                          f.month_sequence("2026-01", "2026-07")])

    def test_rolling_origin_tracks_rising_demand(self):
        """With a visible sliding window the forecast must follow the trend."""
        months = f.month_sequence("2023-01", "2026-07")
        values = {m: i for i, m in enumerate(months, start=1)}
        grid = [record(m, values[m]) for m in months]
        test = [record(m, values[m]) for m in f.month_sequence("2026-01", "2026-07")]

        rolling = f.forecast_all(grid, test, ORIGIN, f.ROLLING_ORIGIN,
                                 "trailing_mean", window=3)
        static = f.forecast_all(grid, test, ORIGIN, f.STATIC_HOLDOUT,
                                "trailing_mean", window=3)

        self.assertEqual(len({r["forecast"] for r in rolling}), 7,
                         "rolling forecast must vary month to month")
        self.assertEqual(len({r["forecast"] for r in static}), 1,
                         "static forecast must be a frozen level")
        self.assertGreater(rolling[-1]["forecast"], rolling[0]["forecast"])
        # First rolling point averages 2025-10, 2025-11, 2025-12 actuals.
        self.assertAlmostEqual(rolling[0]["forecast"],
                               (values["2025-10"] + values["2025-11"] + values["2025-12"]) / 3,
                               places=9)

    def test_rolling_origin_has_full_coverage(self):
        for window in (3, 6):
            rows = f.forecast_all(self.grid, self.test, ORIGIN,
                                  f.ROLLING_ORIGIN, "trailing_mean", window=window)
            cov = f.coverage(rows)
            self.assertEqual(cov["fallback"], 0, f"window={window} fell back")
            self.assertEqual(cov["full_window_pct"], 100.0)

    def test_rolling_origin_cannot_see_the_target_month(self):
        point = f.forecast_point(
            f.build_history(self.grid + [record("2026-01", 500)]), SERIES,
            "2026-01", ORIGIN, f.ROLLING_ORIGIN, "trailing_mean", window=3,
        )
        self.assertNotEqual(point["forecast"], 500)
        self.assertEqual(point["window_covered"], 3)

    def test_structural_guard_rejects_a_window_reaching_the_target(self):
        """
        The leakage invariant is enforced by code, not by convention: a window
        that would read the target month raises instead of forecasting.
        """
        grid = [record(m, 1) for m in f.month_sequence("2024-01", "2025-12")]
        # Target sits inside the training range, so an origin-anchored window
        # would include the target month itself.
        with self.assertRaises(ValueError):
            f.forecast_point(f.build_history(grid), SERIES, "2025-06", "2025-12",
                             f.STATIC_HOLDOUT, "trailing_mean", window=3)

    def test_forecast_all_rejects_targets_inside_the_training_range(self):
        grid = [record(m, 1) for m in f.month_sequence("2023-01", "2025-12")]
        with self.assertRaises(ValueError):
            f.forecast_all(grid, [record("2025-06", 1)], ORIGIN,
                           f.STATIC_HOLDOUT, "seasonal_naive")

    def test_forecast_all_accepts_targets_beyond_the_origin(self):
        grid = [record(m, 1) for m in f.month_sequence("2023-01", "2025-12")]
        rows = f.forecast_all(grid, [record("2026-01", 1)], ORIGIN,
                              f.STATIC_HOLDOUT, "seasonal_naive")
        self.assertEqual(len(rows), 1)

    def test_seasonal_naive_is_protocol_invariant_for_this_horizon(self):
        """
        For a horizon inside one year of the origin, t-12 always precedes the
        origin, so the frozen and sliding information sets coincide.
        """
        grid = flat_grid("2023-01", "2025-12",
                         {m: 5 for m in f.month_sequence("2023-01", "2025-12")})
        test = [record(m, 7) for m in f.month_sequence("2026-01", "2026-07")]
        static = f.forecast_all(grid, test, ORIGIN, f.STATIC_HOLDOUT, "seasonal_naive")
        rolling = f.forecast_all(grid, test, ORIGIN, f.ROLLING_ORIGIN, "seasonal_naive")
        self.assertEqual([r["forecast"] for r in static], [r["forecast"] for r in rolling])


class TestFallback(unittest.TestCase):
    """The series-mean fallback: reachable, correct, and reported."""

    def test_seasonal_fallback_when_twelve_months_are_missing(self):
        grid = [record("2025-01", 10), record("2025-02", 20)]
        point = f.forecast_point(
            f.build_history(grid), SERIES, "2026-05", ORIGIN,
            f.STATIC_HOLDOUT, "seasonal_naive",
        )
        self.assertEqual(point["source"], f.SOURCE_SERIES_MEAN)
        self.assertEqual(point["forecast"], 15.0)
        self.assertEqual(point["window_covered"], 0)
        self.assertEqual(point["window_requested"], 1)

    def test_moving_average_fallback_when_window_predates_history(self):
        """No window month is visible, so the series mean is the only option."""
        grid = flat_grid("2025-01", "2025-01", {"2025-01": 6})
        point = f.forecast_point(
            f.build_history(grid), SERIES, "2026-01", ORIGIN,
            f.STATIC_HOLDOUT, "trailing_mean", window=3,
        )
        self.assertEqual(point["source"], f.SOURCE_SERIES_MEAN)
        self.assertEqual(point["forecast"], 6.0)
        self.assertEqual(point["window_covered"], 0)
        self.assertEqual(point["window_requested"], 3)

    def test_partial_window_averages_only_observed_months(self):
        grid = flat_grid("2025-12", "2025-12", {"2025-12": 10})
        point = f.forecast_point(
            f.build_history(grid), SERIES, "2026-01", ORIGIN,
            f.STATIC_HOLDOUT, "trailing_mean", window=3,
        )
        self.assertEqual(point["window_covered"], 1)
        self.assertEqual(point["source"], f.SOURCE_TRAILING_PARTIAL)
        self.assertEqual(point["forecast"], 10.0)

    def test_partial_window_is_not_reported_as_full_coverage(self):
        grid = flat_grid("2025-12", "2025-12", {"2025-12": 10})
        point = f.forecast_point(
            f.build_history(grid), SERIES, "2026-01", ORIGIN,
            f.STATIC_HOLDOUT, "trailing_mean", window=3,
        )
        self.assertLess(point["window_covered"], point["window_requested"])
        rows = [{**point, "actual": 0}]
        cov = f.coverage(rows)
        self.assertEqual(cov["full_window"], 0)
        self.assertEqual(cov["partial_window"], 1)
        self.assertEqual(cov["fallback"], 0)

    def test_zero_fallback_for_an_unknown_series(self):
        point = f.forecast_point(
            f.build_history([record("2025-01", 10)]), "BOOK|Never-Seen", "2026-01",
            ORIGIN, f.STATIC_HOLDOUT, "seasonal_naive",
        )
        self.assertEqual(point["source"], f.SOURCE_ZERO)
        self.assertEqual(point["forecast"], 0.0)

    def test_fallback_respects_the_protocol_information_set(self):
        """A fallback must average visible history only, never the holdout."""
        # 2025-06 is absent, so t-12 for 2026-06 cannot be looked up and the
        # fallback engages under both protocols.
        training = [record(m, 10) for m in f.month_sequence("2023-01", "2025-12")
                    if m != "2025-06"]
        grid = training + [record("2026-01", 777)]
        target = "2026-06"

        static = f.forecast_point(
            f.build_history(grid), SERIES, target, ORIGIN,
            f.STATIC_HOLDOUT, "seasonal_naive",
        )
        rolling = f.forecast_point(
            f.build_history(grid), SERIES, target, ORIGIN,
            f.ROLLING_ORIGIN, "seasonal_naive",
        )
        self.assertEqual(static["source"], f.SOURCE_SERIES_MEAN)
        self.assertEqual(rolling["source"], f.SOURCE_SERIES_MEAN)
        # Static sees only the 35 training months; rolling also sees 2026-01.
        self.assertEqual(static["forecast"], 10.0)
        self.assertAlmostEqual(rolling["forecast"], (10 * 35 + 777) / 36, places=9)

    def test_fallback_is_visible_in_coverage_counts(self):
        grid = flat_grid("2023-01", "2025-12",
                         {m: 10 for m in f.month_sequence("2023-01", "2025-12")})
        test = [record(m, 10) for m in f.month_sequence("2026-01", "2026-07")]
        rows = f.forecast_all(grid, test, ORIGIN, f.STATIC_HOLDOUT,
                              "seasonal_naive", label="Seasonal Naive")
        # t-12 for 2026-01..2026-07 is 2025-01..2025-07, all present: no fallback.
        self.assertEqual(f.coverage(rows)["fallback"], 0)

    def test_shipped_grid_never_triggers_the_fallback(self):
        """
        Documents the audit finding that the fallback is unreachable against the
        current seed, and pins the reason: the grid is complete and the horizon is
        shorter than the 12-month seasonal lag.
        """
        grid = flat_grid("2023-01", "2025-12",
                         {m: 3 for m in f.month_sequence("2023-01", "2025-12")})
        test = [record(m, 3) for m in f.month_sequence("2026-01", "2026-07")]
        rows = f.forecast_all(grid, test, ORIGIN, f.STATIC_HOLDOUT, "seasonal_naive")
        self.assertEqual(len(rows), 7)
        self.assertTrue(all(r["source"] == f.SOURCE_SEASONAL for r in rows))
        for window in (3, 6):
            rows = f.forecast_all(grid, test, ORIGIN, f.STATIC_HOLDOUT,
                                  "trailing_mean", window=window)
            self.assertTrue(all(r["source"] == f.SOURCE_TRAILING for r in rows))


class TestUncategorizedSeries(unittest.TestCase):
    """
    UNCATEGORIZED must behave as a normal series end to end.

    The audit found the current seed only ever produces NEWSPAPER|UNCATEGORIZED,
    so the NULL-category branch for books and magazines was never exercised. These
    tests drive that branch through the real classification and forecasting code.
    """

    def test_newspaper_series_key(self):
        self.assertEqual(f.series_key("NEWSPAPER", f.UNCATEGORIZED),
                         f"NEWSPAPER|{f.UNCATEGORIZED}")

    def test_uncategorized_newspaper_forecasts_like_any_series(self):
        grid = [record("2025-01", 9, "NEWSPAPER", f.UNCATEGORIZED),
                record("2025-07", 4, "NEWSPAPER", f.UNCATEGORIZED)]
        test = [record("2026-01", 11, "NEWSPAPER", f.UNCATEGORIZED)]
        rows = f.forecast_all(grid, test, ORIGIN, f.STATIC_HOLDOUT, "seasonal_naive")
        self.assertEqual(rows[0]["forecast"], 9)
        self.assertEqual(rows[0]["source"], f.SOURCE_SEASONAL)

    def test_uncategorized_book_forecast(self):
        grid = [record("2025-01", 7, "BOOK", f.UNCATEGORIZED)]
        test = [record("2026-01", 2, "BOOK", f.UNCATEGORIZED)]
        rows = f.forecast_all(grid, test, ORIGIN, f.STATIC_HOLDOUT, "seasonal_naive")
        self.assertEqual(rows[0]["forecast"], 7)
        self.assertEqual(rows[0]["category"], f.UNCATEGORIZED)

    def test_uncategorized_does_not_collide_with_a_real_category(self):
        grid = [record("2025-01", 7, "BOOK", "Fiction"),
                record("2025-01", 40, "BOOK", f.UNCATEGORIZED)]
        history = f.build_history(grid)
        self.assertEqual(history[("BOOK|Fiction", "2025-01")], 7)
        self.assertEqual(history[(f"BOOK|{f.UNCATEGORIZED}", "2025-01")], 40)

    def test_null_category_books_flow_through_aggregation_to_forecast(self):
        """End-to-end: raw NULL category -> series -> seasonal forecast."""
        from datetime import date

        raw = []
        for month, count in ((1, 6), (7, 4)):
            for i in range(count):
                raw.append(f.classify_borrow_row(
                    date(2025, month, i + 1), book_id=100 + i, book_category=None))
        grid = f.densify_monthly_grid(f.aggregate_monthly_demand(raw), "2025-01", "2025-12")
        uncategorized = [r for r in grid if r["category"] == f.UNCATEGORIZED]
        self.assertEqual(len(uncategorized), 12)
        self.assertEqual(sum(r["demand"] for r in uncategorized), 10)

        test = [record("2026-01", 3, "BOOK", f.UNCATEGORIZED)]
        rows = f.forecast_all(grid, test, ORIGIN, f.STATIC_HOLDOUT, "seasonal_naive")
        self.assertEqual(rows[0]["forecast"], 6)

    def test_item_type_metrics_separate_uncategorized_newspapers(self):
        grid = [record("2025-01", 3, "BOOK", "Fiction"),
                record("2025-01", 10, "NEWSPAPER", f.UNCATEGORIZED)]
        test = [record("2026-01", 6, "BOOK", "Fiction"),
                record("2026-01", 12, "NEWSPAPER", f.UNCATEGORIZED)]
        rows = f.forecast_all(grid, test, ORIGIN, f.STATIC_HOLDOUT, "seasonal_naive")
        by_type = f.metrics_by(rows, "item_type")
        self.assertEqual(by_type["BOOK"]["mae"], 3.0)
        self.assertEqual(by_type["NEWSPAPER"]["mae"], 2.0)
        self.assertEqual(by_type["NEWSPAPER"]["n"], 1)


class TestProtocolInformationSets(unittest.TestCase):
    """split_history is the only gate on what a forecaster may read."""

    def setUp(self):
        self.history = f.build_history(flat_grid(
            "2025-10", "2026-03", {m: 1 for m in f.month_sequence("2025-10", "2026-03")}))

    def test_static_holdout_freezes_at_the_origin(self):
        visible = f.split_history(self.history, "2026-06", ORIGIN, f.STATIC_HOLDOUT)
        self.assertEqual(sorted({m for _s, m in visible}),
                         ["2025-10", "2025-11", "2025-12"])

    def test_rolling_origin_excludes_the_target_month(self):
        visible = f.split_history(self.history, "2026-01", ORIGIN, f.ROLLING_ORIGIN)
        self.assertEqual(sorted({m for _s, m in visible}),
                         ["2025-10", "2025-11", "2025-12"])
        self.assertNotIn("2026-01", {m for _s, m in visible})

    def test_rolling_origin_grows_with_the_target(self):
        early = f.split_history(self.history, "2026-01", ORIGIN, f.ROLLING_ORIGIN)
        late = f.split_history(self.history, "2026-03", ORIGIN, f.ROLLING_ORIGIN)
        self.assertEqual(len(late), len(early) + 2)

    def test_rolling_origin_never_exposes_the_target_month(self):
        for target in f.month_sequence("2025-10", "2026-03"):
            visible = f.split_history(self.history, target, ORIGIN, f.ROLLING_ORIGIN)
            for _series, month in visible:
                self.assertLess(f.compare_months(month, target), 0,
                                f"rolling-origin leaked {month} into {target}")

    def test_static_holdout_never_exposes_the_horizon(self):
        """
        A frozen origin is target-independent, so the invariant is not per
        target: nothing at or after the first holdout month may be visible.
        """
        horizon_start = "2026-01"
        self.assertLess(f.compare_months(ORIGIN, horizon_start), 0,
                        "origin must precede the holdout")
        for target in f.month_sequence("2026-01", "2026-03"):
            visible = f.split_history(self.history, target, ORIGIN, f.STATIC_HOLDOUT)
            for _series, month in visible:
                self.assertLess(f.compare_months(month, horizon_start), 0,
                                f"static-holdout leaked {month} into the horizon")

    def test_either_protocol_excludes_the_target_month_for_horizon_targets(self):
        for target in f.month_sequence("2026-01", "2026-03"):
            for protocol in f.PROTOCOLS:
                visible = f.split_history(self.history, target, ORIGIN, protocol)
                for _series, month in visible:
                    self.assertLess(f.compare_months(month, target), 0,
                                    f"{protocol} leaked {month} into {target}")

    def test_unknown_protocol_raises(self):
        with self.assertRaises(ValueError):
            f.split_history(self.history, "2026-01", ORIGIN, "random-k-fold")

    def test_series_mean_over_visible_history(self):
        history = f.build_history([
            record("2025-01", 10), record("2025-02", 20),
            record("2025-01", 100, "BOOK", "History"),
        ])
        self.assertEqual(f.series_mean(history, SERIES), 15.0)
        self.assertEqual(f.series_mean(history, "BOOK|History"), 100.0)
        self.assertEqual(f.series_mean(history, "BOOK|Missing"), 0.0)


class TestPhase5BReportContents(unittest.TestCase):
    """The generated report must state the protocol and the numbers."""

    def setUp(self):
        grid = flat_grid("2023-01", "2025-12",
                         {m: 4 for m in f.month_sequence("2023-01", "2025-12")})
        grid += [record(m, 5) for m in f.month_sequence("2026-01", "2026-07")]
        grid = f.add_calendar_regime(grid)
        test = f.add_calendar_regime([record(m, 5) for m in f.month_sequence("2026-01", "2026-07")])
        self.results = {}
        for protocol in (f.STATIC_HOLDOUT, f.ROLLING_ORIGIN):
            results, by_label = p5b.run_baselines(grid, test, ORIGIN, protocol)
            self.results[protocol] = (results, by_label)
        self.context = {
            "first_month": "2023-01", "last_month": "2026-07",
            "truncated_months": ["2026-08"], "total_observations": len(grid),
            "series_count": 1, "series_keys": [SERIES],
            "series_by_item_type": {"BOOK": 1}, "origin": ORIGIN,
            "horizon_start": "2026-01", "train_count": 43, "test_count": 7,
        }
        self.report = p5b.generate_report(
            self.context,
            [(protocol, results) for protocol, (results, _byl) in self.results.items()],
            {"BREAK": 10},
        )

    def test_report_covers_all_three_baselines(self):
        for label in ("Seasonal Naive", "3-Month MA", "6-Month MA"):
            self.assertIn(label, self.report)

    def test_report_names_both_protocols(self):
        self.assertIn("static holdout", self.report.lower())
        self.assertIn("rolling origin", self.report.lower())

    def test_report_states_the_horizon(self):
        self.assertIn("2026-01 to 2026-07", self.report)
        self.assertIn("2023-01 to 2025-12", self.report)

    def test_report_flags_the_excluded_month(self):
        self.assertIn("2026-08", self.report)
        self.assertIn("unobserved", self.report)

    def test_report_shows_window_coverage(self):
        self.assertIn("WINDOW COVERAGE", self.report)
        self.assertIn("(100.0%)", self.report)

    def test_report_keeps_the_synthetic_caveat(self):
        self.assertIn("do NOT represent real academic calendar data", self.report)
        self.assertIn("do NOT establish real-world forecasting performance", self.report)

    def test_baseline_labels(self):
        self.assertEqual(p5b.baseline_label("seasonal_naive", None), "Seasonal Naive")
        self.assertEqual(p5b.baseline_label("trailing_mean", 3), "3-Month MA")
        self.assertEqual(p5b.baseline_label("trailing_mean", 6), "6-Month MA")

    def test_run_baselines_reports_metrics_and_coverage(self):
        results, _ = self.results[f.STATIC_HOLDOUT]
        self.assertEqual(len(results), 3)
        for result in results:
            self.assertEqual(result["metrics"]["n"], 7)
            self.assertEqual(result["coverage"]["full_window"], 7)
            self.assertEqual(result["coverage"]["fallback"], 0)


if __name__ == "__main__":
    unittest.main()
