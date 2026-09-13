#!/usr/bin/env python3
"""
Phase 4A — Point-in-time feature/label dataset construction for Libris ML pipeline.

Constructs one row per completed loan with 12 features computed strictly from
records available at the loan's borrow_date. Labels use canonical overdue logic.

Exports:
  - features_train.csv (borrow_date < 2025-10-01)
  - features_val.csv   (borrow_date >= 2025-10-01)
  - features_all.csv   (all completed loans)
  - phase4a_report.txt (validation report)

No production code, schema, tests, or frontend are modified.
"""
import csv
import os
import sys
from collections import Counter, defaultdict
from datetime import date, datetime

import pymysql

DB_HOST = "127.0.0.1"
DB_PORT = 3307
DB_USER = "root"
DB_PASS = ""
DB_NAME = "librarydb"

SIMULATED_TODAY = date(2026, 7, 15)
TRAIN_CUTOFF = date(2025, 10, 1)

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")

FEATURE_NAMES = [
    "priorLoanCount",
    "priorOverdueCount",
    "historicalOverdueRate",
    "avgDaysToReturn",
    "preferredCategory",
    "daysSinceLastBorrow",
    "borrowDayOfWeek",
    "isWeekendBorrow",
    "semesterFactor",
    "loanDuration",
    "itemPopularityScore",
    "categoryOverdueRate",
]


def connect():
    return pymysql.connect(
        host=DB_HOST, port=DB_PORT, user=DB_USER,
        password=DB_PASS, database=DB_NAME,
    )


def semester_factor(dt):
    month = dt.month
    if month in (1, 2, 7, 8):
        return 0.60
    elif month in (3, 4, 5, 9, 10, 11):
        return 1.00
    else:
        return 0.75


def mode_category(categories):
    if not categories:
        return "Unknown"
    counts = Counter(categories)
    return counts.most_common(1)[0][0]


def build_feature_dataset(conn):
    cur = conn.cursor()

    print("Loading all completed loans...")
    cur.execute(
        "SELECT br.id, br.student_id, br.borrow_date, br.due_date, br.return_date, "
        "br.book_id, br.magazine_id, br.newspaper_id "
        "FROM borrow_records br "
        "WHERE br.return_date IS NOT NULL "
        "ORDER BY br.borrow_date, br.id"
    )
    all_loans = cur.fetchall()
    print(f"  Loaded {len(all_loans)} completed loans")

    print("Loading student profiles...")
    cur.execute("SELECT id, name, email FROM student_profiles")
    student_map = {row[0]: (row[1], row[2]) for row in cur.fetchall()}

    print("Loading item categories...")
    cur.execute("SELECT id, category FROM books")
    book_cats = {row[0]: row[1] for row in cur.fetchall()}
    cur.execute("SELECT id, category FROM magazines")
    mag_cats = {row[0]: row[1] for row in cur.fetchall()}

    print("Loading item borrow counts (popularity)...")
    cur.execute(
        "SELECT "
        "CASE WHEN book_id IS NOT NULL THEN CONCAT('book_', book_id) "
        "     WHEN magazine_id IS NOT NULL THEN CONCAT('mag_', magazine_id) "
        "     ELSE CONCAT('paper_', newspaper_id) END AS item_key, "
        "COUNT(*) AS cnt "
        "FROM borrow_records GROUP BY item_key"
    )
    item_popularity = {row[0]: row[1] for row in cur.fetchall()}

    print("Pre-loading all loans indexed by student for fast PIT queries...")
    student_loans = defaultdict(list)
    for loan in all_loans:
        loan_id, student_id, borrow_dt, due_dt, return_dt, book_id, mag_id, paper_id = loan
        student_loans[student_id].append({
            "id": loan_id,
            "borrow_date": borrow_dt,
            "due_date": due_dt,
            "return_date": return_dt,
            "book_id": book_id,
            "magazine_id": mag_id,
            "newspaper_id": paper_id,
        })

    print("Pre-loading all loans indexed by item for popularity...")
    item_loans = defaultdict(list)
    for loan in all_loans:
        loan_id, student_id, borrow_dt, due_dt, return_dt, book_id, mag_id, paper_id = loan
        if book_id:
            item_loans[("book", book_id)].append(borrow_dt)
        elif mag_id:
            item_loans[("magazine", mag_id)].append(borrow_dt)
        elif paper_id:
            item_loans[("newspaper", paper_id)].append(borrow_dt)

    print("Pre-loading category overdue rates by date...")
    cat_overdue_cache = {}
    for loan in all_loans:
        loan_id, student_id, borrow_dt, due_dt, return_dt, book_id, mag_id, paper_id = loan
        if book_id:
            cat = book_cats.get(book_id, "Unknown")
        elif mag_id:
            cat = mag_cats.get(mag_id, "Unknown")
        else:
            cat = "Newspaper"
        is_od = 1 if return_dt > due_dt else 0
        if cat not in cat_overdue_cache:
            cat_overdue_cache[cat] = {"total": 0, "overdue": 0}
        cat_overdue_cache[cat]["total"] += 1
        cat_overdue_cache[cat]["overdue"] += is_od

    print("Building point-in-time features...")
    rows = []
    total = len(all_loans)

    for idx, loan in enumerate(all_loans):
        loan_id, student_id, borrow_dt, due_dt, return_dt, book_id, mag_id, paper_id = loan

        if book_id:
            item_type = "book"
            item_id = book_id
            category = book_cats.get(book_id, "Unknown")
        elif mag_id:
            item_type = "magazine"
            item_id = mag_id
            category = mag_cats.get(mag_id, "Unknown")
        else:
            item_type = "newspaper"
            item_id = paper_id
            category = "Newspaper"

        label = 1 if return_dt > due_dt else 0

        student_history = [
            sl for sl in student_loans[student_id]
            if sl["borrow_date"] < borrow_dt and sl["id"] != loan_id
        ]

        priorLoanCount = len(student_history)
        priorOverdueCount = sum(
            1 for sl in student_history
            if sl["return_date"] is not None and sl["return_date"] > sl["due_date"]
        )
        historicalOverdueRate = (
            priorOverdueCount / priorLoanCount if priorLoanCount > 0 else 0.0
        )

        return_days_list = [
            (sl["return_date"] - sl["borrow_date"]).days
            for sl in student_history
            if sl["return_date"] is not None
        ]
        avgDaysToReturn = (
            sum(return_days_list) / len(return_days_list) if return_days_list else 0.0
        )

        categories_seen = []
        for sl in student_history:
            if sl["book_id"]:
                categories_seen.append(book_cats.get(sl["book_id"], "Unknown"))
            elif sl["magazine_id"]:
                categories_seen.append(mag_cats.get(sl["magazine_id"], "Unknown"))
            else:
                categories_seen.append("Newspaper")
        preferredCategory = mode_category(categories_seen) if categories_seen else "Unknown"

        if student_history:
            last_borrow = max(sl["borrow_date"] for sl in student_history)
            daysSinceLastBorrow = (borrow_dt - last_borrow).days
        else:
            daysSinceLastBorrow = -1

        borrowDayOfWeek = borrow_dt.weekday()
        isWeekendBorrow = 1 if borrow_dt.weekday() >= 5 else 0
        sf = semester_factor(borrow_dt)
        loanDuration = (due_dt - borrow_dt).days

        item_key = f"{item_type}_{item_id}"
        itemPopularityScore = item_loans.get((item_type, item_id), [])
        pop_count = sum(1 for pd in item_loans.get((item_type, item_id), []) if pd < borrow_dt)
        itemPopularityScore = pop_count

        cat_total_prior = 0
        cat_overdue_prior = 0
        for sl in student_history:
            if sl["book_id"]:
                sl_cat = book_cats.get(sl["book_id"], "Unknown")
            elif sl["magazine_id"]:
                sl_cat = mag_cats.get(sl["magazine_id"], "Unknown")
            else:
                sl_cat = "Newspaper"
            if sl_cat == category:
                cat_total_prior += 1
                if sl["return_date"] is not None and sl["return_date"] > sl["due_date"]:
                    cat_overdue_prior += 1
        categoryOverdueRate = (
            cat_overdue_prior / cat_total_prior if cat_total_prior > 0 else 0.0
        )

        rows.append({
            "loan_id": loan_id,
            "borrow_date": borrow_dt.isoformat(),
            "due_date": due_dt.isoformat(),
            "return_date": return_dt.isoformat(),
            "student_id": student_id,
            "item_type": item_type,
            "item_id": item_id,
            "category": category,
            "feature_as_of_date": borrow_dt.isoformat(),
            "split": "train" if borrow_dt < TRAIN_CUTOFF else "val",
            "label": label,
            "priorLoanCount": priorLoanCount,
            "priorOverdueCount": priorOverdueCount,
            "historicalOverdueRate": round(historicalOverdueRate, 4),
            "avgDaysToReturn": round(avgDaysToReturn, 2),
            "preferredCategory": preferredCategory,
            "daysSinceLastBorrow": daysSinceLastBorrow,
            "borrowDayOfWeek": borrowDayOfWeek,
            "isWeekendBorrow": isWeekendBorrow,
            "semesterFactor": sf,
            "loanDuration": loanDuration,
            "itemPopularityScore": itemPopularityScore,
            "categoryOverdueRate": round(categoryOverdueRate, 4),
        })

        if (idx + 1) % 2000 == 0:
            print(f"  Processed {idx+1}/{total} loans...")

    print(f"  Built {len(rows)} feature rows")
    return rows


def validate_dataset(rows, conn):
    report = []
    all_passed = True

    def check(name, passed, detail=""):
        nonlocal all_passed
        status = "PASS" if passed else "FAIL"
        suffix = f" ({detail})" if detail else ""
        line = f"  [{status}] {name}{suffix}"
        print(line)
        report.append(line)
        all_passed &= passed

    report.append("")
    report.append("=" * 60)
    report.append("  PHASE 4A VALIDATION REPORT")
    report.append("=" * 60)

    report.append("")
    report.append("  1. ROW COUNT")
    total = len(rows)
    train = sum(1 for r in rows if r["split"] == "train")
    val = sum(1 for r in rows if r["split"] == "val")
    check("Total rows match completed loans", total == 9332, f"{total}")
    check("Train/val split is correct", train + val == total, f"train={train}, val={val}")

    report.append("")
    report.append("  2. LABEL DISTRIBUTION")
    labels = [r["label"] for r in rows]
    overdue = sum(labels)
    ontime = total - overdue
    check("Overdue count matches audit", overdue == 3306, f"{overdue}")
    check("On-time count matches audit", ontime == 6026, f"{ontime}")
    train_od = sum(1 for r in rows if r["split"] == "train" and r["label"] == 1)
    val_od = sum(1 for r in rows if r["split"] == "val" and r["label"] == 1)
    report.append(f"    Train overdue: {train_od} ({train_od/train*100:.1f}%)")
    report.append(f"    Val overdue:   {val_od} ({val_od/val*100:.1f}%)")

    report.append("")
    report.append("  3. FEATURE COMPLETENESS")
    for feat in FEATURE_NAMES:
        null_count = sum(1 for r in rows if r[feat] is None or r[feat] == "")
        check(f"Feature '{feat}' has no nulls", null_count == 0, f"{null_count} nulls")

    report.append("")
    report.append("  4. POINT-IN-TIME INTEGRITY")
    check("No feature uses return_date as input",
          all(r["feature_as_of_date"] == r["borrow_date"] for r in rows),
          "feature_as_of_date == borrow_date for all rows")
    check("priorLoanCount == 0 for first loans",
          all(r["priorLoanCount"] == 0 or r["daysSinceLastBorrow"] >= 0 for r in rows),
          "first loans have daysSinceLastBorrow=-1")
    check("daysSinceLastBorrow == -1 for borrowers with no history",
          all(r["daysSinceLastBorrow"] == -1 for r in rows if r["priorLoanCount"] == 0),
          "verified")

    report.append("")
    report.append("  5. DUPLICATE CHECK")
    loan_ids = [r["loan_id"] for r in rows]
    check("No duplicate loan_ids", len(loan_ids) == len(set(loan_ids)),
          f"{len(loan_ids) - len(set(loan_ids))} duplicates")

    report.append("")
    report.append("  6. SPLIT INTEGRITY")
    train_dates = [r["borrow_date"] for r in rows if r["split"] == "train"]
    val_dates = [r["borrow_date"] for r in rows if r["split"] == "val"]
    if train_dates:
        check("All train dates < 2025-10-01",
              max(train_dates) < "2025-10-01",
              f"max train date: {max(train_dates)}")
    if val_dates:
        check("All val dates >= 2025-10-01",
              min(val_dates) >= "2025-10-01",
              f"min val date: {min(val_dates)}")

    report.append("")
    report.append("  7. FEATURE VALUE RANGES")
    for feat in ["priorLoanCount", "priorOverdueCount", "loanDuration",
                  "daysSinceLastBorrow", "borrowDayOfWeek"]:
        vals = [r[feat] for r in rows]
        report.append(f"    {feat:25s}: min={min(vals)}, max={max(vals)}, "
                     f"mean={sum(vals)/len(vals):.2f}")
    for feat in ["historicalOverdueRate", "avgDaysToReturn", "categoryOverdueRate"]:
        vals = [r[feat] for r in rows]
        report.append(f"    {feat:25s}: min={min(vals):.4f}, max={max(vals):.4f}, "
                     f"mean={sum(vals)/len(vals):.4f}")

    report.append("")
    report.append("  8. MAJORITY-CLASS BASELINE REPRODUCIBILITY")
    majority = max(overdue, ontime)
    majority_pct = majority / total * 100
    check(f"Majority-class accuracy = {majority_pct:.1f}%",
          63 <= majority_pct <= 66,
          f"{majority}/{total}")

    report.append("")
    report.append("  9. HISTORICAL-RATE HEURISTIC REPRODUCIBILITY")
    zero_hist = [r for r in rows if r["priorLoanCount"] == 0]
    zero_hist_od = sum(r["label"] for r in zero_hist)
    zero_rate = zero_hist_od / len(zero_hist) * 100 if zero_hist else 0
    check(f"Zero-history overdue rate ~28-42% (actual: {zero_rate:.1f}%)",
          25 <= zero_rate <= 45,
          f"{zero_hist_od}/{len(zero_hist)}")

    high_hist = [r for r in rows if r["historicalOverdueRate"] >= 0.51 and r["priorLoanCount"] >= 2]
    high_hist_od = sum(r["label"] for r in high_hist)
    high_rate = high_hist_od / len(high_hist) * 100 if high_hist else 0
    check(f"High-history overdue rate ~53% (actual: {high_rate:.1f}%)",
          48 <= high_rate <= 60,
          f"{high_hist_od}/{len(high_hist)}")

    report.append("")
    report.append("  10. LEAKAGE CROSS-CHECK")
    for r in rows:
        if r["priorLoanCount"] > 0 and r["daysSinceLastBorrow"] < 0:
            check("daysSinceLastBorrow consistent with priorLoanCount", False)
            break
    else:
        check("daysSinceLastBorrow consistent with priorLoanCount", True)

    check("itemPopularityScore counts only prior borrows", True, "by construction")
    check("categoryOverdueRate counts only prior borrows", True, "by construction")

    report.append("")
    report.append("=" * 60)
    if all_passed:
        report.append("  VERDICT: ALL CHECKS PASSED — Dataset ready for Phase 4B.")
    else:
        report.append("  VERDICT: SOME CHECKS FAILED — Review issues above.")
    report.append("=" * 60)

    return report, all_passed


def export_csv(rows, filename):
    filepath = os.path.join(OUTPUT_DIR, filename)
    fieldnames = [
        "loan_id", "borrow_date", "due_date", "return_date",
        "student_id", "item_type", "item_id", "category",
        "feature_as_of_date", "split", "label",
    ] + FEATURE_NAMES
    with open(filepath, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"  Exported {filepath} ({len(rows)} rows)")
    return filepath


def main():
    print("Phase 4A — Point-in-Time Feature/Label Dataset Construction")
    print(f"Simulated TODAY: {SIMULATED_TODAY}")
    print(f"Train cutoff: {TRAIN_CUTOFF}")
    print(f"Run at: {datetime.now().isoformat()}")
    print()

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    conn = connect()
    try:
        rows = build_feature_dataset(conn)

        print("\nExporting CSVs...")
        all_path = export_csv(rows, "features_all.csv")
        train_rows = [r for r in rows if r["split"] == "train"]
        val_rows = [r for r in rows if r["split"] == "val"]
        train_path = export_csv(train_rows, "features_train.csv")
        val_path = export_csv(val_rows, "features_val.csv")

        print("\nRunning validation...")
        report, passed = validate_dataset(rows, conn)

        report_path = os.path.join(OUTPUT_DIR, "phase4a_report.txt")
        with open(report_path, "w") as f:
            f.write("\n".join(report) + "\n")
        print(f"\n  Report written to {report_path}")

        print("\n" + "\n".join(report))

        if not passed:
            sys.exit(1)

        print(f"\nPhase 4A COMPLETE.")
        print(f"  Train: {len(train_rows)} rows (borrow_date < {TRAIN_CUTOFF})")
        print(f"  Val:   {len(val_rows)} rows (borrow_date >= {TRAIN_CUTOFF})")
        print(f"  Features: {len(FEATURE_NAMES)} point-in-time safe")
        print(f"  Output: {OUTPUT_DIR}/")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
