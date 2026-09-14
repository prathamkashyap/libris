# Phase 5A — Real-Data Readiness Audit (Source-Verified)

| Field | Value |
|---|---|
| **Audit date** | 2026-09-14 |
| **Repository HEAD** | `700d281e57e211816466f8223321d2000c2650f1` (main) |
| **Database provenance** | **No database reachable** |
| **Production data actually queried** | **No** |
| **pymysql installed** | No (`ModuleNotFoundError`) |

---

## 1. Cleanup Status

| Artifact | Status | Commit |
|---|---|---|
| `PHASE5_PRODUCTION_READINESS.md` | **Deleted** from repository | `700d281` |
| `phase5a_readiness.py` | **Deleted** from repository | `700d281` |
| `readiness_monitor.py` DATE_FORMAT | **Fixed** (`'%Y-%m'` confirmed at line 131) | `700d281` |
| `ML_POPULATION_DEFINITION.md` | **Exists** (not modified per scope fence) | — |

**Note on `ML_POPULATION_DEFINITION.md`:** The user asked this audit to confirm that its content explicitly states it is not independently validated. It does not — the file contains no such caveat. Per scope constraints, this file was not modified. This discrepancy is reported here only.

---

## 2. Source-Verified Data Model

### 2.1 BorrowRecord Entity

**File:** `backend/src/main/java/com/example/lms/entity/BorrowRecord.java`

| Line | Field | Column | Type | Nullable | Notes |
|---|---|---|---|---|---|
| 9-10 | `id` | `id` | `Long` | NO | PK, `@GeneratedValue(IDENTITY)` |
| 13-14 | `book` | `book_id` | `Book` | **YES** | `@ManyToOne(LAZY)`, no `optional=false` |
| 17-18 | `magazine` | `magazine_id` | `Magazine` | **YES** | `@ManyToOne(LAZY)`, no `optional=false` |
| 21-22 | `newspaper` | `newspaper_id` | `Newspaper` | **YES** | `@ManyToOne(LAZY)`, no `optional=false` |
| 25-26 | `student` | `student_id` | `StudentProfile` | **YES** | `@ManyToOne(LAZY)`, `@JoinColumn` has no `nullable=false` |
| 29 | `borrowerName` | `borrower_name` | `String` | NO | `length=100` |
| 32 | `borrowerEmail` | `borrower_email` | `String` | NO | `length=100` |
| 35 | `borrowerPhone` | `borrower_phone` | `String` | NO | `length=20` |
| 38-39 | `borrowDate` | `borrow_date` | `LocalDate` | NO | |
| 41-42 | `dueDate` | `due_date` | `LocalDate` | **YES** | Added by V3 migration |
| 44-45 | `returnDate` | `return_date` | `LocalDate` | **YES** | NULL = active loan |
| + inherited | `createdAt` | `created_at` | `Instant` | NO | `@CreatedDate` from `AuditableEntity` (line 12-13) |
| + inherited | `updatedAt` | `updated_at` | `Instant` | NO | `@LastModifiedDate` from `AuditableEntity` (line 16-17) |

**Key observations:**
- **No cascade, no orphanRemoval** on any `@ManyToOne` relationship.
- **No `@NamedEntityGraph`** on the entity class itself. EntityGraph annotations are on repository methods.
- **Polymorphic FK:** Exactly one of `book_id`, `magazine_id`, `newspaper_id` should be set. Enforced at the service layer (`BorrowRecordService.java:83-89`), not at the DB level.

### 2.2 Other Entities (ML-relevant summary)

| Entity | File | Table | Key fields for ML | Notes |
|---|---|---|---|---|
| `Book` | `entity/Book.java:6` | `books` | `id`, `category` (line 19, nullable, V4), `isbn` (unique) | No relationships |
| `Magazine` | `entity/Magazine.java:6` | `magazines` | `id`, `category` (line 22-23, nullable) | No relationships |
| `Newspaper` | `entity/Newspaper.java:6` | `newspapers` | `id` only — **no `category` column** | No relationships |
| `StudentProfile` | `entity/StudentProfile.java:5` | `student_profiles` | `id`, `name`, `email`, `phone` | `@OneToOne(Account)`, `optional=false` |
| `Account` | `entity/Account.java:5` | `accounts` | `id`, `username`, `role` | Role enum: `ADMIN`, `LIBRARIAN`, `STUDENT` |

---

## 3. Due-Date and Overdue Semantics

### 3.1 Due-Date Assignment (Borrow Time)

**File:** `backend/src/main/java/com/example/lms/service/BorrowRecordService.java:100`

```java
record.setDueDate(r.dueDate() != null ? r.dueDate() : r.borrowDate().plusDays(14));
```

If the request provides a `dueDate`, it is used. Otherwise, `borrowDate + 14 days` is stored. This means **all new borrow records have a non-null `dueDate`**. Pre-V3 records may have `dueDate = NULL` (V3 migration: `V3__borrow_record_due_date.sql:3` adds `due_date DATE NULL`).

### 3.2 Effective Due-Date Calculation

**File:** `backend/src/main/java/com/example/lms/util/OverdueCalculator.java:11-19`

```java
static LocalDate effectiveDueDate(BorrowRecord record) {
    if (record == null) return null;
    return record.getDueDate() != null
        ? record.getDueDate()
        : record.getBorrowDate() != null
            ? record.getBorrowDate().plusDays(14)
            : null;
}
```

This is the **canonical** implementation. It handles three cases:
1. `dueDate` present → use it directly
2. `dueDate` null, `borrowDate` present → `borrowDate + 14`
3. Both null → `null`

### 3.3 Overdue Detection

**File:** `backend/src/main/java/com/example/lms/util/OverdueCalculator.java:21-32`

```java
static boolean isOverdue(BorrowRecord record) {
    if (record == null) return false;
    LocalDate dueDate = effectiveDueDate(record);
    if (dueDate == null) return false;
    LocalDate compareDate = record.getReturnDate() != null
        ? record.getReturnDate()
        : LocalDate.now();
    return compareDate.isAfter(dueDate);
}
```

Logic: Compares `returnDate` (if returned) or `LocalDate.now()` (if still on loan) against the effective due date. A record is overdue if the compare date is strictly after the due date.

### 3.4 Inline Fallback Duplication

**File:** `backend/src/main/java/com/example/lms/service/ReportService.java:115`

```java
var dueDate = r.getDueDate() != null ? r.getDueDate() : r.getBorrowDate().plusDays(14);
```

This is an **inline copy** of the `+14` fallback, not using `OverdueCalculator.effectiveDueDate()`. Used in the borrowing-history CSV export. Functionally equivalent but not DRY.

### 3.5 ReportService Overdue Detection

**File:** `backend/src/main/java/com/example/lms/service/ReportService.java:165-170`

```java
var records = borrowRecords.findByReturnDateIsNull().stream()
    .filter(OverdueCalculator::isOverdue)
    .toList();
```

Uses `OverdueCalculator::isOverdue` correctly. However, this loads **all** unreturned records into memory (no pagination at line 168).

---

## 4. Deletion / History Constraints

### 4.1 Student Deletion

**File:** `backend/src/main/java/com/example/lms/service/StudentService.java:118-140`

```java
@Transactional
public void delete(Long id) {
    var p = student(id);                                   // line 120
    if (borrowRecords.existsByStudentId(id))               // line 121
        throw new ConflictException("A student with borrow history cannot be deleted."); // line 122
    var name = p.getName();                                // line 123
    var account = p.getAccount();                          // line 124
    students.delete(p);                                    // line 125
    accounts.delete(account);                              // line 126
    // audit event...
}
```

- **Line 121:** Checks `borrowRecords.existsByStudentId(id)` (`BorrowRecordRepository.java:37`).
- Returns HTTP **409 Conflict** if the student has any borrow records.
- If the student has no borrow history, deletes `StudentProfile` then `Account` manually (no cascade).
- **Impact on ML:** Historical borrow records with a deleted student retain `student_id` FK. But the 409 guard **prevents** deletion of students with any borrow history, so ML-population records cannot be orphaned through the application API.

### 4.2 Librarian Deletion

**File:** `backend/src/main/java/com/example/lms/service/LibrarianService.java:104-124`

```java
@Transactional
public void delete(Long id) {
    var p = librarian(id);                     // line 106
    var name = p.getName();                    // line 107
    var account = p.getAccount();              // line 108
    librarians.delete(p);                      // line 109
    accounts.delete(account);                  // line 110
}
```

**No borrow-history check.** Librarians are deleted unconditionally. Librarians do not appear in the ML population (only students do), so this has no ML impact.

### 4.3 Item Deletion (Book / Magazine / Newspaper)

All three follow the same pattern:

| Service | File:Line | Check |
|---|---|---|
| `BookService` | `service/BookService.java:107` | `records.existsByBookId(id)` |
| `MagazineService` | `service/MagazineService.java:55` | `records.existsByMagazineId(id)` |
| `NewspaperService` | `service/NewspaperService.java:55` | `records.existsByNewspaperId(id)` |

All throw **409 Conflict** if **any** borrow history exists (active or returned). This preserves historical data integrity.

---

## 5. Persistence / Schema Observations

### 5.1 Database Configuration

**File:** `backend/src/main/resources/application.properties`

| Line | Property | Value |
|---|---|---|
| 5 | `spring.jpa.hibernate.ddl-auto` | `none` |
| 7 | `spring.jpa.properties.hibernate.jdbc.time_zone` | `UTC` |
| 13 | `spring.flyway.enabled` | `true` |
| 15 | `spring.flyway.baseline-on-migrate` | `true` |

**Profile-specific:**

| File | Key Setting |
|---|---|
| `application-h2.properties` | `ddl-auto=create-drop`, Flyway disabled |
| `application-docker.properties` | MySQL URL defaults to Docker hostname `mysql` |
| `application-prod.properties` | Swagger disabled (`springdoc.*.enabled=false`) |

### 5.2 Flyway Migrations

| Migration | File | Purpose | ML-Relevant Schema Changes |
|---|---|---|---|
| V1 | `db/migration/V1__baseline.sql` (131 lines) | Full baseline schema | `borrow_records.student_id BIGINT NULL` (line ~100), no `due_date`, no `books.category` |
| V2 | `db/migration/V2__student_profile_email_unique.sql` (6 lines) | Unique email constraint | None |
| V3 | `db/migration/V3__borrow_record_due_date.sql` (3 lines) | `ALTER TABLE borrow_records ADD COLUMN due_date DATE NULL` | `due_date` now available; pre-V3 records have NULL |
| V4 | `db/migration/V4__book_category.sql` (3 lines) | `ALTER TABLE books ADD COLUMN category VARCHAR(100) NULL` | `category` now available on books; pre-V4 books have NULL |

### 5.3 Schema Compatibility with ML

Based on source-verified entity definitions:

| ML Requirement | Source | Status |
|---|---|---|
| `borrow_date` (NOT NULL) | `BorrowRecord.java:38-39` | Always present |
| `return_date` (nullable) | `BorrowRecord.java:44-45` | Present; NULL = active |
| `due_date` (nullable) | `BorrowRecord.java:41-42` + V3 migration | Present; NULL possible for pre-V3 records; `effectiveDueDate()` handles this |
| `student_id` (nullable) | `BorrowRecord.java:25-26` | Present but nullable; walk-in borrows allowed |
| `book.category` (nullable) | `Book.java:19` + V4 migration | Present; NULL possible for pre-V4 books |
| `magazine.category` (nullable) | `Magazine.java:22-23` | Present; nullable |
| `newspaper.category` | — | **Does not exist** (Newspaper.java has no `category` field) |
| Label derivation | `OverdueCalculator.java:11-19` | `effectiveDueDate()` handles NULL `due_date`; label = `returnDate > effectiveDueDate` |

### 5.4 Category Gap

- **Books:** `category` VARCHAR(100), nullable (V4). Pre-V4 books have `NULL`.
- **Magazines:** `category` VARCHAR(100), nullable. May have `NULL` if admin did not set.
- **Newspapers:** **No `category` column at all** (`Newspaper.java` has no `category` field; `V1__baseline.sql` defines no such column).

---

## 6. Readiness Monitor Status

### 6.1 DATE_FORMAT Fix

**File:** `scripts/dev-realdata/readiness_monitor.py:131`

```python
SELECT DATE_FORMAT(borrow_date, '%Y-%m') as month, COUNT(*) as cnt
```

The corrected expression uses `'%Y-%m'` (single percent signs). The previous `'%%Y-%%m'` sent literal `%%` to MySQL, which interprets `%%` as a literal `%` character, producing `%Y-%m` as a static string rather than formatting dates. This collapsed all months into a single bucket.

### 6.2 Monitor Execution

```
$ python3 scripts/dev-realdata/readiness_monitor.py --json
ERROR: pymysql not installed. Run: pip install pymysql
EXIT_CODE=1
```

**pymysql is not installed** in the current environment. The monitor cannot connect to any database.

### 6.3 Database Provenance

| Question | Answer |
|---|---|
| Is MySQL running locally? | Unknown / unreachable (no pymysql) |
| Is Railway production DB reachable? | **No** — no credentials or network access from this environment |
| Is a synthetic database available? | No |
| Is a local dev database available? | No |

---

## 7. Blocking Conditions

| # | Condition | Status | Impact |
|---|---|---|---|
| 1 | No production DB reachable | **BLOCKED** | Cannot measure real-data population size, temporal coverage, overdue rates, or category coverage |
| 2 | `pymysql` not installed | **BLOCKED** | Cannot run `readiness_monitor.py` |
| 3 | `ML_POPULATION_DEFINITION.md` lacks validation caveat | **NOTED** | File exists but does not state it is unverified; not modified per scope |

---

## 8. What This Audit Can and Cannot Say

### CAN say (source-verified):

- The application's data model supports all 12 frozen ML features (schema-level compatibility confirmed via entity source).
- `OverdueCalculator.effectiveDueDate()` correctly handles NULL `due_date` with `borrowDate + 14` fallback.
- The `readiness_monitor.py` DATE_FORMAT bug is fixed (`'%Y-%m'` at line 131).
- Student deletion is guarded by a borrow-history check (409 Conflict).
- Item deletion is guarded by a borrow-history check (409 Conflict).
- Newspaper entity has no `category` column.
- All timestamps use UTC (`application.properties:7`).
- Schema is managed by Flyway (V1–V4); `ddl-auto=none`.

### CANNOT say (no DB reachable):

- How many completed loans exist
- How many distinct borrowers exist
- What the temporal range of `borrow_date` values is
- What the overdue rate is
- What the category coverage is
- Whether any `due_date` values are NULL in practice
- Whether real-data thresholds (500 loans, 100 borrowers, 6 months, 50 overdue, 50 on-time, 80% category, 90% due-date) are met

---

## 9. Verdict

```
real-data readiness: not assessable, no production DB reachable
```

The application source confirms schema-level compatibility with the frozen ML pipeline. However, no production database was reachable during this audit (`pymysql` not installed; no credentials available). Real-data readiness — population size, temporal coverage, overdue rates, and category coverage — cannot be measured without a functioning connection to a production or staging database.
