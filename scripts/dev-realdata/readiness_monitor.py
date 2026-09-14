#!/usr/bin/env python3
"""
Phase 5B — Real-Data Readiness Monitor

Periodically check whether the Libris production database has accumulated
enough historical circulation data to begin ML evaluation.

This script is READ-ONLY — it runs SELECT queries only and makes no
modifications to the database.

Usage:
    python3 readiness_monitor.py                    # default: localhost:3306/librarydb
    python3 readiness_monitor.py --host HOST --port PORT --db DB
    python3 readiness_monitor.py --json             # machine-readable output
"""
import argparse
import json
import os
import sys
from datetime import datetime, date

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")

# Minimum thresholds for ML readiness
THRESHOLDS = {
    "min_completed_loans": 500,
    "min_distinct_borrowers": 100,
    "min_months_coverage": 6,
    "min_overdue_examples": 50,
    "min_ontime_examples": 50,
    "min_borrowers_with_history": 50,
    "min_category_coverage_pct": 0.80,
}


def check_mysql():
    try:
        import pymysql
        return True
    except ImportError:
        return False


def connect_db(host, port, user, password, database):
    import pymysql
    return pymysql.connect(
        host=host, port=port, user=user, password=password, database=database,
        charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor
    )


def run_query(conn, sql, params=None):
    with conn.cursor() as cur:
        cur.execute(sql, params)
        return cur.fetchall()


def main():
    parser = argparse.ArgumentParser(description="Libris ML Readiness Monitor")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="")
    parser.add_argument("--db", default="librarydb")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    if not check_mysql():
        print("ERROR: pymysql not installed. Run: pip install pymysql")
        sys.exit(1)

    try:
        conn = connect_db(args.host, args.port, args.user, args.password, args.db)
    except Exception as e:
        print(f"ERROR: Cannot connect to database: {e}")
        print(f"  Host: {args.host}:{args.port}")
        print(f"  Database: {args.db}")
        print(f"  This is expected if Libris is not deployed or MySQL is not running.")
        sys.exit(1)

    results = {}

    # 1. Total counts
    r = run_query(conn, "SELECT COUNT(*) as cnt FROM borrow_records")
    results["total_borrow_records"] = r[0]["cnt"]

    r = run_query(conn, "SELECT COUNT(*) as cnt FROM books")
    results["total_books"] = r[0]["cnt"]

    r = run_query(conn, "SELECT COUNT(*) as cnt FROM magazines")
    results["total_magazines"] = r[0]["cnt"]

    r = run_query(conn, "SELECT COUNT(*) as cnt FROM newspapers")
    results["total_newspapers"] = r[0]["cnt"]

    r = run_query(conn, "SELECT COUNT(*) as cnt FROM student_profiles")
    results["total_students"] = r[0]["cnt"]

    # 2. Completed loans (return_date IS NOT NULL)
    r = run_query(conn, "SELECT COUNT(*) as cnt FROM borrow_records WHERE return_date IS NOT NULL")
    results["completed_loans"] = r[0]["cnt"]

    # 3. Completed loans with student (eligible for ML)
    r = run_query(conn, """
        SELECT COUNT(*) as cnt FROM borrow_records
        WHERE return_date IS NOT NULL AND student_id IS NOT NULL
    """)
    results["eligible_completed_loans"] = r[0]["cnt"]

    # 4. Active loans (return_date IS NULL)
    r = run_query(conn, "SELECT COUNT(*) as cnt FROM borrow_records WHERE return_date IS NULL")
    results["active_loans"] = r[0]["cnt"]

    # 5. Distinct borrowers (eligible loans)
    r = run_query(conn, """
        SELECT COUNT(DISTINCT student_id) as cnt FROM borrow_records
        WHERE return_date IS NOT NULL AND student_id IS NOT NULL
    """)
    results["distinct_borrowers"] = r[0]["cnt"]

    # 6. Date range
    r = run_query(conn, """
        SELECT MIN(borrow_date) as earliest, MAX(borrow_date) as latest
        FROM borrow_records WHERE student_id IS NOT NULL
    """)
    results["earliest_borrow_date"] = str(r[0]["earliest"]) if r[0]["earliest"] else None
    results["latest_borrow_date"] = str(r[0]["latest"]) if r[0]["latest"] else None

    # 7. Monthly loan counts
    r = run_query(conn, """
        SELECT DATE_FORMAT(borrow_date, '%Y-%m') as month, COUNT(*) as cnt
        FROM borrow_records
        WHERE student_id IS NOT NULL
        GROUP BY month ORDER BY month
    """)
    results["monthly_counts"] = {row["month"]: row["cnt"] for row in r}
    results["months_with_data"] = len(results["monthly_counts"])

    # 8. Overdue analysis (eligible completed loans)
    r = run_query(conn, """
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN return_date > COALESCE(due_date, DATE_ADD(borrow_date, INTERVAL 14 DAY))
                THEN 1 ELSE 0 END) as overdue,
            SUM(CASE WHEN return_date <= COALESCE(due_date, DATE_ADD(borrow_date, INTERVAL 14 DAY))
                THEN 1 ELSE 0 END) as ontime
        FROM borrow_records
        WHERE return_date IS NOT NULL AND student_id IS NOT NULL
    """)
    results["eligible_overdue"] = int(r[0]["overdue"] or 0)
    results["eligible_ontime"] = int(r[0]["ontime"] or 0)
    results["overdue_rate"] = (
        results["eligible_overdue"] / results["eligible_completed_loans"]
        if results["eligible_completed_loans"] > 0 else 0
    )

    # 9. Borrowers with history (>=5 prior loans)
    r = run_query(conn, """
        SELECT COUNT(*) as cnt FROM (
            SELECT student_id, COUNT(*) as loan_count
            FROM borrow_records
            WHERE student_id IS NOT NULL
            GROUP BY student_id HAVING loan_count >= 5
        ) sub
    """)
    results["borrowers_with_5plus_loans"] = r[0]["cnt"]

    # 10. Borrowers with 10+ loans
    r = run_query(conn, """
        SELECT COUNT(*) as cnt FROM (
            SELECT student_id, COUNT(*) as loan_count
            FROM borrow_records
            WHERE student_id IS NOT NULL
            GROUP BY student_id HAVING loan_count >= 10
        ) sub
    """)
    results["borrowers_with_10plus_loans"] = r[0]["cnt"]

    # 11. Category coverage
    r = run_query(conn, """
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN b.category IS NOT NULL THEN 1 ELSE 0 END) as with_category
        FROM borrow_records br
        JOIN books b ON br.book_id = b.id
        WHERE br.return_date IS NOT NULL AND br.student_id IS NOT NULL
    """)
    books_total = r[0]["total"]
    books_with_cat = int(r[0]["with_category"] or 0)

    r = run_query(conn, """
        SELECT COUNT(*) as total FROM borrow_records
        WHERE return_date IS NOT NULL AND student_id IS NOT NULL
        AND magazine_id IS NOT NULL
    """)
    mag_total = r[0]["total"]

    r = run_query(conn, """
        SELECT COUNT(*) as total FROM borrow_records
        WHERE return_date IS NOT NULL AND student_id IS NOT NULL
        AND newspaper_id IS NOT NULL
    """)
    newsp_total = r[0]["total"]

    all_eligible = results["eligible_completed_loans"]
    if all_eligible > 0:
        results["category_coverage_pct"] = books_with_cat / all_eligible if all_eligible > 0 else 0
    else:
        results["category_coverage_pct"] = 0
    results["books_with_category"] = books_with_cat
    results["books_without_category"] = books_total - books_with_cat

    # 12. due_date coverage
    r = run_query(conn, """
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN due_date IS NOT NULL THEN 1 ELSE 0 END) as with_due_date
        FROM borrow_records
        WHERE return_date IS NOT NULL AND student_id IS NOT NULL
    """)
    results["loans_with_due_date"] = int(r[0]["with_due_date"] or 0)
    results["due_date_coverage_pct"] = (
        results["loans_with_due_date"] / all_eligible if all_eligible > 0 else 0
    )

    # 13. Item type distribution
    r = run_query(conn, """
        SELECT
            SUM(CASE WHEN book_id IS NOT NULL THEN 1 ELSE 0 END) as books,
            SUM(CASE WHEN magazine_id IS NOT NULL THEN 1 ELSE 0 END) as magazines,
            SUM(CASE WHEN newspaper_id IS NOT NULL THEN 1 ELSE 0 END) as newspapers
        FROM borrow_records
        WHERE return_date IS NOT NULL AND student_id IS NOT NULL
    """)
    results["item_type_books"] = int(r[0]["books"] or 0)
    results["item_type_magazines"] = int(r[0]["magazines"] or 0)
    results["item_type_newspapers"] = int(r[0]["newspapers"] or 0)

    # 14. Missingness
    r = run_query(conn, """
        SELECT
            SUM(CASE WHEN borrow_date IS NULL THEN 1 ELSE 0 END) as missing_borrow_date,
            SUM(CASE WHEN return_date IS NULL THEN 1 ELSE 0 END) as missing_return_date,
            SUM(CASE WHEN student_id IS NULL THEN 1 ELSE 0 END) as missing_student_id
        FROM borrow_records
    """)
    results["missing_borrow_date"] = int(r[0]["missing_borrow_date"] or 0)
    results["missing_return_date"] = int(r[0]["missing_return_date"] or 0)
    results["missing_student_id"] = int(r[0]["missing_student_id"] or 0)

    # 15. Readiness assessment
    checks = {}
    checks["completed_loans"] = results["completed_loans"] >= THRESHOLDS["min_completed_loans"]
    checks["distinct_borrowers"] = results["distinct_borrowers"] >= THRESHOLDS["min_distinct_borrowers"]
    checks["months_coverage"] = results["months_with_data"] >= THRESHOLDS["min_months_coverage"]
    checks["overdue_examples"] = results["eligible_overdue"] >= THRESHOLDS["min_overdue_examples"]
    checks["ontime_examples"] = results["eligible_ontime"] >= THRESHOLDS["min_ontime_examples"]
    checks["borrowers_with_history"] = results["borrowers_with_5plus_loans"] >= THRESHOLDS["min_borrowers_with_history"]
    checks["category_coverage"] = results["category_coverage_pct"] >= THRESHOLDS["min_category_coverage_pct"]
    checks["due_date_coverage"] = results["due_date_coverage_pct"] >= 0.90

    results["readiness_checks"] = checks
    results["all_checks_pass"] = all(checks.values())

    conn.close()

    if args.json:
        os.makedirs(OUTPUT_DIR, exist_ok=True)
        json_path = os.path.join(OUTPUT_DIR, "readiness_status.json")
        with open(json_path, "w") as f:
            json.dump(results, f, indent=2, default=str)
        print(json.dumps(results, indent=2, default=str))
    else:
        print_report(results)


def print_report(r):
    print("=" * 70)
    print("  LIBRIS ML READINESS MONITOR")
    print("=" * 70)
    print(f"  Run at: {datetime.now().isoformat()}")
    print("")

    print("  DATA INVENTORY")
    print("  " + "─" * 50)
    print(f"  Total borrow records:       {r['total_borrow_records']:>8}")
    print(f"  Total books:                {r['total_books']:>8}")
    print(f"  Total magazines:            {r['total_magazines']:>8}")
    print(f"  Total newspapers:           {r['total_newspapers']:>8}")
    print(f"  Total students:             {r['total_students']:>8}")
    print("")

    print("  ML POPULATION (eligible completed loans)")
    print("  " + "─" * 50)
    print(f"  Completed loans:            {r['completed_loans']:>8}")
    print(f"  Eligible (with student):    {r['eligible_completed_loans']:>8}")
    print(f"  Active loans:               {r['active_loans']:>8}")
    print(f"  Distinct borrowers:         {r['distinct_borrowers']:>8}")
    print(f"  Overdue:                    {r['eligible_overdue']:>8}")
    print(f"  On-time:                    {r['eligible_ontime']:>8}")
    print(f"  Overdue rate:               {r['overdue_rate']:>7.1%}")
    print("")

    print("  TEMPORAL COVERAGE")
    print("  " + "─" * 50)
    print(f"  Earliest borrow date:       {r['earliest_borrow_date'] or 'N/A':>12}")
    print(f"  Latest borrow date:         {r['latest_borrow_date'] or 'N/A':>12}")
    print(f"  Months with data:           {r['months_with_data']:>8}")
    print("")

    print("  BORROWER HISTORY DEPTH")
    print("  " + "─" * 50)
    print(f"  Borrowers with >=5 loans:   {r['borrowers_with_5plus_loans']:>8}")
    print(f"  Borrowers with >=10 loans:  {r['borrowers_with_10plus_loans']:>8}")
    print("")

    print("  ITEM TYPE DISTRIBUTION (eligible)")
    print("  " + "─" * 50)
    print(f"  Books:                      {r['item_type_books']:>8}")
    print(f"  Magazines:                  {r['item_type_magazines']:>8}")
    print(f"  Newspapers:                 {r['item_type_newspapers']:>8}")
    print("")

    print("  DATA QUALITY")
    print("  " + "─" * 50)
    print(f"  Missing borrow_date:        {r['missing_borrow_date']:>8}")
    print(f"  Missing return_date:        {r['missing_return_date']:>8}")
    print(f"  Missing student_id:         {r['missing_student_id']:>8}")
    print(f"  Books with category:        {r['books_with_category']:>8}")
    print(f"  Books without category:     {r['books_without_category']:>8}")
    print(f"  Category coverage:          {r['category_coverage_pct']:>7.1%}")
    print(f"  Due date coverage:          {r['due_date_coverage_pct']:>7.1%}")
    print("")

    print("  MONTHLY LOAN COUNTS")
    print("  " + "─" * 50)
    for month in sorted(r["monthly_counts"].keys()):
        cnt = r["monthly_counts"][month]
        bar = "█" * min(cnt // 5, 40)
        print(f"  {month}:  {cnt:>5}  {bar}")
    print("")

    print("  READINESS CHECKS")
    print("  " + "─" * 50)
    for check, passed in r["readiness_checks"].items():
        status = "PASS" if passed else "FAIL"
        threshold_key = f"min_{check}"
        if threshold_key in THRESHOLDS:
            threshold = THRESHOLDS[threshold_key]
        else:
            threshold = "?"
        print(f"  [{status}] {check:30s} (threshold: {threshold})")

    print("")
    print("  " + "═" * 50)
    if r["all_checks_pass"]:
        print("  VERDICT: READY — sufficient data for Phase 5C")
        print("  Proceed with real-data PIT dataset construction.")
    else:
        failed = [k for k, v in r["readiness_checks"].items() if not v]
        print(f"  VERDICT: NOT READY — {len(failed)} checks failing")
        print(f"  Failed: {', '.join(failed)}")
        print("  Continue operating Libris and re-run this monitor periodically.")
    print("  " + "═" * 50)


if __name__ == "__main__":
    main()
