#!/usr/bin/env python3
"""
Development-only historical data generator for Libris (Library Management System).
Generates ~10,000 realistic synthetic borrow records with latent borrower profiles
for ML overdue-risk prediction development.

Reproducible: fixed seed ensures identical output across runs.
Isolated: writes only to the dev MySQL database; never touches H2 or test configs.
"""
import os
import random
import string
import sys
from datetime import date, datetime, timedelta
from decimal import Decimal

import pymysql

SEED = 42
DB_HOST = "127.0.0.1"
DB_PORT = 3306
DB_USER = "root"
DB_PASS = os.environ.get("LMS_DB_PASSWORD", "")
DB_NAME = os.environ.get("LMS_DB_NAME", "librarydb")

TARGET_COMPLETED_LOANS = 10000
TARGET_ACTIVE_LOANS = 300
TARGET_TOTAL_LOANS = TARGET_COMPLETED_LOANS + TARGET_ACTIVE_LOANS

NUM_STUDENTS = 1000
NUM_BOOKS = 500
NUM_MAGAZINES = 50
NUM_NEWSPAPERS = 30

DATE_RANGE_START = date(2023, 1, 1)
DATE_RANGE_END = date(2026, 8, 31)

SIMULATED_TODAY = date(2026, 7, 15)

DEFAULT_DUE_DAYS = 14
CUSTOM_DUE_PROBABILITY = 0.30
CUSTOM_DUE_OPTIONS = [7, 10, 10, 14, 14, 21, 21, 28, 30]

BOOK_CATEGORIES = [
    "Fiction", "Non-Fiction", "Science", "History", "Technology",
    "Mathematics", "Literature", "Philosophy", "Art", "Biography",
    "Economics", "Psychology", "Reference", "Education", "Travel",
]
MAGAZINE_CATEGORIES = [
    "Science", "Technology", "Fashion", "Sports", "Travel",
    "Finance", "Health", "Education", "Literature", "Art",
]

BOOK_TITLE_WORDS = [
    "Echoes", "Horizon", "Quantum", "Silent", "Crimson", "Iron",
    "Galactic", "Twilight", "Infinite", "Golden", "Shadow", "Crystal",
    "Emerald", "Phoenix", "Nebula", "Atlas", "Phantom", "Apex",
    "Vortex", "Zenith", "Pulse", "Cipher", "Nova", "Eclipse",
    "Prism", "Vector", "Forge", "Oracle", "Bastion", "Meridian",
    "Chronicle", "Spectrum", "Nexus", "Paradox", "Labyrinth", "Aether",
    "Catalyst", "Haven", "Rogue", "Empire", "Drift",
    "Keystone", "Pinnacle", "Solstice", "Tempest", "Reckoning",
    "Monolith", "Crescent", "Reverie", "Onyx", "Vanguard",
]
BOOK_TITLE_SUFFIXES = [
    "of Tomorrow", "Unbound", "Chronicles", "Awakening", "Protocol",
    "Requiem", "Legacy", "Frontier", "Symphony", "Descent",
    "Paradigm", "Odyssey", "Genesis", "Dominion", "Remnant",
    "Labyrinth", "Insurrection", "Resonance", "Meridian", "Epoch",
    "Cascade", "Verdict", "Revenant", "Synthesis", "Crucible",
]
BOOK_AUTHORS = [
    "Alice Hartwell", "Benjamin Cross", "Catherine Drake", "David Eames",
    "Eleanor Frost", "Frederick Grey", "Grace Holloway", "Henry Irvine",
    "Isabel Jensen", "James Kirkland", "Katherine Lyon", "Liam Monroe",
    "Mia Northcott", "Nathan Owens", "Olivia Pierce", "Patrick Quinn",
    "Quinn Reeves", "Rachel Stone", "Samuel Turner", "Victoria Vance",
    "William Hartley", "Xena Young", "Yusuf Zane", "Zara Bennett",
    "Arthur Penhaligon", "Beatrice Lane", "Clarence Dunbar",
    "Diana Rutherford", "Edmund Blackwell", "Fiona Carmichael",
]
MAGAZINE_TITLES = [
    "Tech Frontier", "Science Today", "World Finance", "Health Plus",
    "Fashion Forward", "Traveler Weekly", "Sports Digest", "Edu Review",
    "Art & Culture", "Nature Scope", "Mind Journal", "Future Cities",
    "Digital Trends", "Green Living", "Global Markets", "Fitness Now",
    "Creative Spaces", "Discovery Monthly", "Youth Today", "Ocean Views",
    "Space Horizons", "Urban Living", "Food & Life", "Book Worm Digest",
    "Climate Watch", "Data Insight", "Quantum Leap", "Bio Frontier",
    "Sky & Earth", "Human Stories",
]
NEWSPAPER_TITLES = [
    "The Daily Chronicle", "Metro Times", "The Morning Herald",
    "City Gazette", "The Evening Post", "The Weekly Dispatch",
    "Sunrise Tribune", "The Metropolitan", "Capital News",
    "The National Register", "The Civic Journal", "Regional Express",
    "The Broadsheet", "The Outlook", "The Sentinel",
    "Public Ledger", "The Standard", "The Observer", "The Review",
    "The Banner",
]
FIRST_NAMES = [
    "Aarav", "Vivaan", "Aditya", "Vihaan", "Arjun", "Sai", "Reyansh",
    "Krishna", "Ishaan", "Shaurya", "Atharv", "Advik", "Pranav",
    "Advaith", "Aarush", "Anika", "Diya", "Myra", "Sara", "Aanya",
    "Aadhya", "Ananya", "Prisha", "Riya", "Kavya", "Ishita", "Nisha",
    "Priya", "Pooja", "Neha", "Meera", "Simran", "Komal",
    "Pari", "Aisha", "Zoya", "Hina", "Fatima", "Sunita", "Emma",
    "Liam", "Olivia", "Noah", "Ava", "Ethan", "Sophia", "Mason",
    "Isabella", "Lucas", "Mia", "Oliver", "Charlotte", "James",
    "Amelia", "Benjamin", "Harper", "Elijah", "Evelyn", "William",
    "Alexander", "Daniel", "Matthew", "Samuel", "Nathan",
    "Caleb", "Henry", "Sebastian", "Gabriel", "Anthony",
    "Abigail", "Ella", "Grace", "Chloe", "Lily",
    "Maya", "Stella", "Leah", "Aria", "Hannah",
    "Elena", "Penelope", "Nora", "Layla", "Riley",
    "Zoe", "Lucy", "Audrey", "Brooklyn", "Savannah",
]
LAST_NAMES = [
    "Sharma", "Patel", "Kumar", "Singh", "Gupta", "Reddy", "Nair",
    "Iyer", "Mishra", "Joshi", "Desai", "Mehta", "Kapoor", "Malhotra",
    "Chopra", "Verma", "Tiwari", "Rao", "Chatterjee", "Mukherjee",
    "Banerjee", "Das", "Sen", "Ghosh", "Bose", "Pillai", "Menon",
    "John", "Smith", "Johnson", "Williams", "Brown", "Jones", "Davis",
    "Miller", "Wilson", "Moore", "Taylor", "Anderson", "Thomas",
    "Jackson", "White", "Harris", "Martin", "Thompson", "Garcia",
    "Clark", "Lewis", "Robinson", "Walker", "Young", "Allen",
    "King", "Wright", "Scott", "Torres", "Nguyen", "Hill",
    "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell",
    "Mitchell", "Carter", "Roberts", "Green", "Evans",
]


def connect():
    return pymysql.connect(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USER,
        password=DB_PASS,
        database=DB_NAME,
        autocommit=False,
    )


def rand_phone(rng):
    prefix = rng.choice(["+91", "+1", "+44", "+61", "+81"])
    digits = "".join(str(rng.randint(0, 9)) for _ in range(10))
    return f"{prefix}-{digits[:3]}-{digits[3:6]}-{digits[6:]}"


def rand_isbn(rng):
    prefix = rng.choice(["978", "979"])
    group = str(rng.randint(0, 9999)).zfill(5)
    pub = str(rng.randint(0, 99999)).zfill(5)
    title_num = str(rng.randint(0, 999999)).zfill(6)
    check = str(rng.randint(0, 9))
    return f"{prefix}-{group}-{pub}-{title_num}-{check}"


def rand_title(rng):
    w1 = rng.choice(BOOK_TITLE_WORDS)
    w2 = rng.choice(BOOK_TITLE_SUFFIXES)
    return f"{w1} {w2}"


def rand_name(rng):
    return f"{rng.choice(FIRST_NAMES)} {rng.choice(LAST_NAMES)}"


def rand_email(name, rng):
    clean = name.lower().replace(" ", ".")
    clean = "".join(c for c in clean if c.isalnum() or c == ".")
    suffix = rng.randint(1, 9999)
    domains = ["gmail.com", "yahoo.com", "outlook.com", "university.edu", "proton.me"]
    return f"{clean}{suffix}@{rng.choice(domains)}"


class BorrowerProfile:
    def __init__(self, name, email, phone, profile_type, rng):
        self.name = name
        self.email = email
        self.phone = phone
        self.profile_type = profile_type
        self.rng = rng
        self.total_borrows = 0
        self.overdue_count = 0
        self.total_days_to_return = 0

        if profile_type == "reliable":
            self.overdue_probability = 0.03
            self.return_early_probability = 0.40
            self.return_on_time_probability = 0.45
            self.return_late_probability = 0.12
            self.return_very_late_probability = 0.03
            self.borrow_frequency = 0.7
        elif profile_type == "moderate":
            self.overdue_probability = 0.15
            self.return_early_probability = 0.25
            self.return_on_time_probability = 0.35
            self.return_late_probability = 0.25
            self.return_very_late_probability = 0.05
            self.borrow_frequency = 0.5
        elif profile_type == "habitual_late":
            self.overdue_probability = 0.45
            self.return_early_probability = 0.05
            self.return_on_time_probability = 0.15
            self.return_late_probability = 0.50
            self.return_very_late_probability = 0.20
            self.borrow_frequency = 0.4
        elif profile_type == "new_student":
            self.overdue_probability = 0.25
            self.return_early_probability = 0.10
            self.return_on_time_probability = 0.30
            self.return_late_probability = 0.40
            self.return_very_late_probability = 0.10
            self.borrow_frequency = 0.3
        else:
            self.overdue_probability = 0.10
            self.return_early_probability = 0.30
            self.return_on_time_probability = 0.40
            self.return_late_probability = 0.20
            self.return_very_late_probability = 0.05
            self.borrow_frequency = 0.5

    def days_to_return(self, borrow_date):
        r = self.rng.random()
        if r < self.return_early_probability:
            return max(1, int(self.rng.gauss(7, 3)))
        elif r < self.return_early_probability + self.return_on_time_probability:
            return max(8, int(self.rng.gauss(12, 2)))
        elif r < (
            self.return_early_probability
            + self.return_on_time_probability
            + self.return_late_probability
        ):
            return max(15, int(self.rng.gauss(20, 4)))
        else:
            return max(22, int(self.rng.gauss(30, 7)))

    def record_borrow(self, was_overdue):
        self.total_borrows += 1
        if was_overdue:
            self.overdue_count += 1


def semester_factor(dt):
    month = dt.month
    if month in (1, 2, 7, 8):
        return Decimal("0.60")
    elif month in (3, 4, 5, 9, 10, 11):
        return Decimal("1.00")
    else:
        return Decimal("0.75")


def pick_due_days(rng):
    if rng.random() < CUSTOM_DUE_PROBABILITY:
        return rng.choice(CUSTOM_DUE_OPTIONS)
    return DEFAULT_DUE_DAYS


def generate_books(rng):
    books = []
    for _ in range(NUM_BOOKS):
        title = rand_title(rng)
        author = rng.choice(BOOK_AUTHORS)
        isbn = rand_isbn(rng)
        category = rng.choice(BOOK_CATEGORIES)
        pub_date = DATE_RANGE_START + timedelta(
            days=rng.randint(0, (DATE_RANGE_END - DATE_RANGE_START).days)
        )
        books.append({
            "title": title,
            "author": author,
            "isbn": isbn,
            "category": category,
            "published_date": pub_date,
        })
    return books


def generate_magazines(rng):
    magazines = []
    used_titles = set()
    for _ in range(NUM_MAGAZINES):
        title = rng.choice(MAGAZINE_TITLES)
        attempts = 0
        while title in used_titles and attempts < 50:
            title = rng.choice(MAGAZINE_TITLES)
            attempts += 1
        used_titles.add(title)
        publisher = rng.choice(BOOK_AUTHORS) + " Publishing"
        issue_date = DATE_RANGE_START + timedelta(
            days=rng.randint(0, (DATE_RANGE_END - DATE_RANGE_START).days)
        )
        category = rng.choice(MAGAZINE_CATEGORIES)
        featured = f"{rng.choice(BOOK_TITLE_WORDS)}: {rng.choice(BOOK_TITLE_WORDS)} {rng.choice(BOOK_TITLE_SUFFIXES)}"
        magazines.append({
            "title": title,
            "publisher": publisher,
            "issue_date": issue_date,
            "category": category,
            "featured_article": featured,
        })
    return magazines


def generate_newspapers(rng):
    newspapers = []
    used_titles = set()
    for _ in range(NUM_NEWSPAPERS):
        title = rng.choice(NEWSPAPER_TITLES)
        attempts = 0
        while title in used_titles and attempts < 50:
            title = rng.choice(NEWSPAPER_TITLES)
            attempts += 1
        used_titles.add(title)
        publisher = rng.choice(BOOK_AUTHORS) + " Media"
        pub_date = DATE_RANGE_START + timedelta(
            days=rng.randint(0, (DATE_RANGE_END - DATE_RANGE_START).days)
        )
        headlines = "; ".join(
            f"{rng.choice(BOOK_TITLE_WORDS)} {rng.choice(BOOK_TITLE_SUFFIXES)}"
            for _ in range(rng.randint(2, 4))
        )
        newspapers.append({
            "title": title,
            "publisher": publisher,
            "publication_date": pub_date,
            "top_headlines": headlines[:500],
        })
    return newspapers


def generate_students(rng, conn):
    profile_types = (
        ["reliable"] * 350
        + ["moderate"] * 300
        + ["habitual_late"] * 150
        + ["new_student"] * 125
        + ["power_user"] * 75
    )
    rng.shuffle(profile_types)

    borrowers = []
    now = datetime.now()
    with conn.cursor() as cur:
        for i in range(NUM_STUDENTS):
            name = rand_name(rng)
            email = rand_email(name, rng)
            phone = rand_phone(rng)
            username = f"student_{i+1:05d}"
            password_hash = "$2a$10$dummyHashForDevSeedDataOnlyNoRealAuth"

            cur.execute(
                "INSERT INTO accounts (created_at, updated_at, enabled, password_hash, role, username) "
                "VALUES (%s, %s, %s, %s, %s, %s)",
                (now, now, 1, password_hash, "STUDENT", username),
            )
            account_id = cur.lastrowid

            cur.execute(
                "INSERT INTO student_profiles (created_at, updated_at, email, name, phone, account_id) "
                "VALUES (%s, %s, %s, %s, %s, %s)",
                (now, now, email, name, phone, account_id),
            )
            profile_id = cur.lastrowid

            pt = profile_types[i] if i < len(profile_types) else "moderate"
            borrower = BorrowerProfile(name, email, phone, pt, rng)
            borrowers.append((profile_id, borrower))

    conn.commit()
    return borrowers


def assign_item_popularity(rng):
    item_weights = {}
    for i in range(NUM_BOOKS):
        item_weights[("book", i + 1)] = rng.expovariate(1.0) + 0.1
    for i in range(NUM_MAGAZINES):
        item_weights[("magazine", i + 1)] = rng.expovariate(1.5) + 0.1
    for i in range(NUM_NEWSPAPERS):
        item_weights[("newspaper", i + 1)] = rng.expovariate(2.0) + 0.1
    total = sum(item_weights.values())
    return {k: v / total for k, v in item_weights.items()}


def weighted_choice(items_weights, rng):
    items = list(items_weights.keys())
    weights = list(items_weights.values())
    return rng.choices(items, weights=weights, k=1)[0]


def generate_loans(rng, borrowers, item_weights, conn):
    item_ids = {}
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM books ORDER BY id")
        item_ids["book"] = [row[0] for row in cur.fetchall()]
        cur.execute("SELECT id FROM magazines ORDER BY id")
        item_ids["magazine"] = [row[0] for row in cur.fetchall()]
        cur.execute("SELECT id FROM newspapers ORDER BY id")
        item_ids["newspaper"] = [row[0] for row in cur.fetchall()]

    item_pop = {}
    for (itype, iid), pop in item_weights.items():
        idx = iid - 1
        if itype == "book" and idx < len(item_ids["book"]):
            item_pop[("book", item_ids["book"][idx])] = pop
        elif itype == "magazine" and idx < len(item_ids["magazine"]):
            item_pop[("magazine", item_ids["magazine"][idx])] = pop
        elif itype == "newspaper" and idx < len(item_ids["newspaper"]):
            item_pop[("newspaper", item_ids["newspaper"][idx])] = pop

    total_days = (SIMULATED_TODAY - DATE_RANGE_START).days
    records_to_insert = []
    loan_count = 0
    max_concurrent = 3

    active_by_item = {}
    student_active = {i: 0 for i in range(len(borrowers))}

    for day_offset in range(total_days):
        current_date = DATE_RANGE_START + timedelta(days=day_offset)

        returned_today = []
        for key, info in list(active_by_item.items()):
            if info["return_date"] <= current_date:
                returned_today.append(key)
        for key in returned_today:
            info = active_by_item.pop(key)
            student_active[info["profile_idx"]] -= 1

        num_borrowers_today = rng.randint(8, 25)
        sf = float(semester_factor(current_date))

        for _ in range(num_borrowers_today):
            if loan_count >= TARGET_COMPLETED_LOANS:
                break

            available_items = {
                k: v for k, v in item_pop.items() if k not in active_by_item
            }
            if not available_items:
                break

            profile_idx = rng.randint(0, len(borrowers) - 1)
            student_id, borrower = borrowers[profile_idx]

            if student_active[profile_idx] >= max_concurrent:
                continue

            effective_freq = borrower.borrow_frequency * sf
            if rng.random() > effective_freq:
                continue

            item_type, item_id = weighted_choice(available_items, rng)

            days_to_return = borrower.days_to_return(current_date)
            return_dt = current_date + timedelta(days=days_to_return)
            if return_dt > SIMULATED_TODAY:
                return_dt = SIMULATED_TODAY

            due_days = pick_due_days(rng)
            due_dt = current_date + timedelta(days=due_days)
            is_overdue = return_dt > due_dt
            borrower.record_borrow(is_overdue)

            item_key = (item_type, item_id)
            active_by_item[item_key] = {
                "return_date": return_dt,
                "profile_idx": profile_idx,
            }
            student_active[profile_idx] += 1

            book_id = item_id if item_type == "book" else None
            magazine_id = item_id if item_type == "magazine" else None
            newspaper_id = item_id if item_type == "newspaper" else None

            records_to_insert.append((
                book_id, magazine_id, newspaper_id,
                student_id,
                borrower.name, borrower.email, borrower.phone,
                current_date, due_dt, return_dt,
                item_type, item_id,
            ))
            loan_count += 1

        if loan_count % 1000 == 0 and loan_count > 0:
            print(f"  Generated {loan_count}/{TARGET_COMPLETED_LOANS} completed loans...")

    print(f"  Completed loans generated: {loan_count}")

    active_count = 0
    active_target = TARGET_ACTIVE_LOANS
    active_window_days = 25

    active_start_day = (SIMULATED_TODAY - DATE_RANGE_START).days - active_window_days

    for day_offset in range(max(0, active_start_day), (SIMULATED_TODAY - DATE_RANGE_START).days):
        current_date = DATE_RANGE_START + timedelta(days=day_offset)
        if active_count >= active_target:
            break

        days_before_today = (SIMULATED_TODAY - current_date).days
        if days_before_today <= 10:
            candidates_today = rng.randint(25, 40)
        elif days_before_today <= 17:
            candidates_today = rng.randint(18, 30)
        else:
            candidates_today = rng.randint(12, 22)
        sf = float(semester_factor(current_date))

        for _ in range(candidates_today):
            if active_count >= active_target:
                break

            available_items = {
                k: v for k, v in item_pop.items() if k not in active_by_item
            }
            if not available_items:
                break

            profile_idx = rng.randint(0, len(borrowers) - 1)
            student_id, borrower = borrowers[profile_idx]

            if student_active[profile_idx] >= max_concurrent:
                continue

            effective_freq = borrower.borrow_frequency * sf
            if rng.random() > effective_freq:
                continue

            item_type, item_id = weighted_choice(available_items, rng)

            due_days = pick_due_days(rng)
            due_dt = current_date + timedelta(days=due_days)

            item_key = (item_type, item_id)
            active_by_item[item_key] = {
                "return_date": date(9999, 12, 31),
                "profile_idx": profile_idx,
                "is_active": True,
            }
            student_active[profile_idx] += 1

            book_id = item_id if item_type == "book" else None
            magazine_id = item_id if item_type == "magazine" else None
            newspaper_id = item_id if item_type == "newspaper" else None

            records_to_insert.append((
                book_id, magazine_id, newspaper_id,
                student_id,
                borrower.name, borrower.email, borrower.phone,
                current_date, due_dt, None,
                item_type, item_id,
            ))
            active_count += 1

    print(f"  Active loans generated: {active_count}")

    total_inserted = len(records_to_insert)
    print(f"  Total records to insert: {total_inserted}")

    print("  Inserting borrow records into database...")
    batch_size = 500
    with conn.cursor() as cur:
        for i in range(0, len(records_to_insert), batch_size):
            batch = records_to_insert[i : i + batch_size]
            cur.executemany(
                "INSERT INTO borrow_records "
                "(created_at, updated_at, book_id, magazine_id, newspaper_id, "
                "student_id, borrower_name, borrower_email, borrower_phone, "
                "borrow_date, due_date, return_date) "
                "VALUES "
                "(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                [
                    (datetime.now(), datetime.now(), rec[0], rec[1], rec[2],
                     rec[3], rec[4], rec[5], rec[6], rec[7], rec[8], rec[9])
                    for rec in batch
                ],
            )
    conn.commit()
    print(f"  Inserted {total_inserted} borrow records.")

    update_item_availability(records_to_insert, conn)
    generate_audit_logs(rng, records_to_insert, borrowers, conn)

    return records_to_insert


def update_item_availability(records, conn):
    print("  Updating item availability status...")
    still_borrowed = {}
    for rec in records:
        item_type = rec[10]
        item_id = rec[11]
        return_date = rec[9]
        if return_date is None:
            still_borrowed[(item_type, item_id)] = True

    with conn.cursor() as cur:
        for (itype, iid) in still_borrowed:
            if itype == "book":
                cur.execute("UPDATE books SET available = 0 WHERE id = %s", (iid,))
            elif itype == "magazine":
                cur.execute("UPDATE magazines SET available = 0 WHERE id = %s", (iid,))
            elif itype == "newspaper":
                cur.execute("UPDATE newspapers SET available = 0 WHERE id = %s", (iid,))
    conn.commit()
    print(f"  Marked {len(still_borrowed)} items as unavailable (currently borrowed).")


def generate_audit_logs(rng, records, borrowers, conn):
    print("  Generating audit logs...")
    logs = []
    for rec in records:
        book_id, magazine_id, newspaper_id = rec[0], rec[1], rec[2]
        student_id = rec[3]
        borrower_name = rec[4]
        borrow_date = rec[7]
        return_date = rec[9]
        item_type = rec[10]
        item_id = rec[11]

        if item_type == "book":
            entity_type = "BOOK"
        elif item_type == "magazine":
            entity_type = "MAGAZINE"
        else:
            entity_type = "NEWSPAPER"

        borrow_ts = datetime.combine(borrow_date, datetime.min.time().replace(
            hour=rng.randint(8, 17),
            minute=rng.randint(0, 59),
            second=rng.randint(0, 59),
        ))
        logs.append((
            "BORROW", student_id, "STUDENT", borrower_name,
            f"Borrowed: {item_type}_{item_id} by {borrower_name}",
            book_id or magazine_id or newspaper_id,
            entity_type, borrow_ts,
        ))

        if return_date is not None:
            return_ts = datetime.combine(return_date, datetime.min.time().replace(
                hour=rng.randint(8, 17),
                minute=rng.randint(0, 59),
                second=rng.randint(0, 59),
            ))
            logs.append((
                "RETURN", student_id, "STUDENT", borrower_name,
                f"Returned: {item_type}_{item_id} by {borrower_name}",
                book_id or magazine_id or newspaper_id,
                entity_type, return_ts,
            ))

    batch_size = 1000
    with conn.cursor() as cur:
        for i in range(0, len(logs), batch_size):
            batch = logs[i : i + batch_size]
            cur.executemany(
                "INSERT INTO audit_logs "
                "(created_at, updated_at, action, actor_id, actor_role, actor_username, "
                "description, entity_id, entity_type, timestamp) "
                "VALUES "
                "(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                [
                    (datetime.now(), datetime.now()) + log
                    for log in batch
                ],
            )
    conn.commit()
    print(f"  Inserted {len(logs)} audit log entries.")


def main():
    print("Libris Development Data Generator (Phase 3B — Corrected)")
    print(f"Seed: {SEED} | Simulated TODAY: {SIMULATED_TODAY}")
    print(f"Target: {TARGET_COMPLETED_LOANS} completed + {TARGET_ACTIVE_LOANS} active loans")
    print(f"Scale: {NUM_STUDENTS} students, {NUM_BOOKS} books, {NUM_MAGAZINES} magazines, {NUM_NEWSPAPERS} newspapers")
    print()

    rng = random.Random(SEED)

    print("Connecting to database...")
    conn = connect()
    print(f"Connected to {DB_HOST}:{DB_PORT}/{DB_NAME}")

    try:
        print("\n[Step 1] Generating item catalogs...")
        books = generate_books(rng)
        magazines = generate_magazines(rng)
        newspapers = generate_newspapers(rng)
        print(f"  {len(books)} books, {len(magazines)} magazines, {len(newspapers)} newspapers")

        print("\n[Step 2] Inserting items...")
        with conn.cursor() as cur:
            now = datetime.now()
            cur.executemany(
                "INSERT INTO books (created_at, updated_at, title, author, isbn, category, published_date, available) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, 1)",
                [(now, now) + tuple(b.values()) for b in books],
            )
            cur.executemany(
                "INSERT INTO magazines (created_at, updated_at, title, publisher, issue_date, category, featured_article, available) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, 1)",
                [(now, now) + tuple(m.values()) for m in magazines],
            )
            cur.executemany(
                "INSERT INTO newspapers (created_at, updated_at, title, publisher, publication_date, top_headlines, available) "
                "VALUES (%s, %s, %s, %s, %s, %s, 1)",
                [(now, now) + tuple(n.values()) for n in newspapers],
            )
        conn.commit()
        print("  Items inserted.")

        print("\n[Step 3] Creating student accounts and profiles...")
        borrowers = generate_students(rng, conn)
        print(f"  Created {len(borrowers)} student accounts/profiles")

        print("\n[Step 4] Computing item popularity weights...")
        item_weights = assign_item_popularity(rng)

        print("\n[Step 5] Generating loan history...")
        records = generate_loans(rng, borrowers, item_weights, conn)

        print("\n[Step 6] Done. Development database seeded.")
        print(f"  Simulated TODAY: {SIMULATED_TODAY}")
        print(f"  Total records: {len(records)}")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
