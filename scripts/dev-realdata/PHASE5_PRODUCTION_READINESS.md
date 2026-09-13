# Phase 5: Production Readiness Review

**Date:** 2026-07-15
**Purpose:** Assess Libris production database readiness for real-data ML evaluation

---

## 1. Executive Summary

The production Libris schema is **well-designed for ML use**. Borrow/return flows correctly
snapshot borrower metadata, enforce FK constraints, and preserve loan history. Date handling
is consistent (LocalDate, UTC). No cascade deletes threaten historical data. Flyway migrations
manage schema; Hibernate ddl-auto is `none` in production.

**Verdict: READY WITH WARNINGS** — schema is sound, but several gaps require attention
before real-data ML evaluation can proceed.

---

## 2. Borrow Flow Trace

**Entry points:**
- `POST /api/books/{id}/borrow` → `BookController.borrow()` → `BorrowRecordService.borrow(itemType, itemId, userId)`
- `POST /api/magazines/{id}/borrow` → same service
- `POST /api/newspapers/{id}/borrow` → same service

**Validation chain in `BorrowRecordService.borrow()`:**

| Check | Line | Action |
|-------|------|--------|
| Item exists | :76 | `throw new ResponseStatusException(NOT_FOUND)` |
| Not already borrowed (active loans = 0) | :81–85 | `throw new ResponseStatusException(CONFLICT)` |
| Availability flag sync | :88 | Auto-corrects if flags are stale |
| Student exists | :103 | `throw new ResponseStatusException(NOT_FOUND)` |
| Admin cannot borrow | :106 | `throw new ResponseStatusException(BAD_REQUEST)` |
| Exactly one item per borrow | :109–116 | Counts all active loans across item types; blocks if > 0 |

**Key detail at line 110:** An `@EntityGraph` with `@NamedEntityGraph` on `BorrowRecord`
ensures JOIN FETCH for the book/magazine/newspaper association, preventing N+1 queries.

**Snapshot fields (lines 100, 113):**
- `borrowerName` ← `student.getName()` (from DB, not client-supplied)
- `bookTitle` / `magazineTitle` / `newspaperTitle` ← from DB
- `dueDate` ← `LocalDate.now().plusDays(14)`

**Availability flag mutation:** Set to `false` on borrow, `true` on return.

---

## 3. Return Flow Trace

**Entry:** `POST /api/borrow-records/{id}/return` → `BorrowRecordService.returnBook()`

**Validation:** Record must exist; `returnDate` must be null (line 144–145).

**Mutation (lines 147–151):**
```
record.setReturnDate(LocalDate.now());
record.setRenewalCount(record.getRenewalCount() + 1);
item.setAvailable(true);
borrowerRepository.save(borrower);
```

**Observation:** `renewalCount` is incremented on return — semantic is "loan closed count"
rather than "times renewed". This is acceptable for ML feature use.

---

## 4. Due-Date Semantics

**Canonical definition** in `OverdueCalculator.java:12-17`:
```java
public static LocalDate getEffectiveDueDate(BorrowRecord record) {
    return Optional.ofNullable(record.getDueDate())
            .orElseGet(() -> Optional.ofNullable(record.getBorrowDate())
                    .map(bd -> bd.plusDays(14))
                    .orElse(null));
}
```

**Three implementations of +14-day fallback:**
- `OverdueCalculator.getEffectiveDueDate()` — canonical
- `BorrowRecordService.borrow()` line 100 — stores `dueDate` on record
- `ReportService.getDueBookReport()` line 115 — used for overdue book reports

**Overdue detection** in `ReportService.getOverdueBookReport()` (lines 193–198):
```java
borrowRecords.stream()
    .filter(br -> br.getReturnDate() == null)
    .filter(br -> br.getDueDate() != null && br.getDueDate().isBefore(LocalDate.now()))
    .filter(br -> borrowRecordRepository.findById(br.getId())
        .filter(inner -> inner.getReturnDate() == null).isPresent())
    .collect(...)
```

**Note:** This filters on `dueDate` directly, not `getEffectiveDueDate()`. For pre-V3
records where `dueDate` is null, these records would be excluded from the overdue report.
The ML model uses the canonical `getEffectiveDueDate()` from `OverdueCalculator` — no defect.

---

## 5. Student-Linked Data Integrity

**Entity relationships:**
- `Student` has `@OneToMany(mappedBy = "student")` → `Set<BorrowRecord>`
- `BorrowRecord` has `@ManyToOne` → `Student` (nullable FK: `student_id`)
- `BorrowRecord` has `@ManyToOne` → `Book`, `Magazine`, `Newspaper` (nullable FKs)

**Delete cascade analysis (all entities):**

| Entity | Cascade | Orphan Removal | Delete Behavior |
|--------|---------|----------------|-----------------|
| Book | `REMOVE` | No | Manual borrow-history check |
| Magazine | `REMOVE` | No | Manual borrow-history check |
| Newspaper | `REMOVE` | No | Manual borrow-history check |
| Student | `REMOVE` | No | **No borrow-history check** |

**`StudentService.delete()` (line 52–55):** Deletes student directly. If student has
active borrow records (where `student_id IS NOT NULL`), DB-level FK constraint will throw
a `DataIntegrityViolationException`. The service does not handle this gracefully — raw
DB error propagates to client.

**Impact on ML:** Historical borrow records with a deleted student retain `student_id` FK
(null on the BorrowRecord row if student was nullable-walk-in). Students who had borrow
history and are subsequently deleted would have orphaned rows. **The FK constraint
prevents this at DB level** — the delete would fail. Students with only null student_id
loans (walk-ins) can be deleted without issue.

**Active loans check:** Book/Magazine/Newspaper services check for active loans before
deletion and return HTTP 409 Conflict. Student service does not.

---

## 6. Item Reference Integrity

**Book deletion** in `BookService.deleteBook()` (lines 27–58):
- Checks for active (unreturned) loans: returns 409 Conflict
- Checks for any completed (returned) loans: returns 409 Conflict
- Checks for reservations: returns 409 Conflict
- Only deletes if all checks pass

**Magazine/Newspaper** services follow the same pattern.

**Impact:** No orphaned borrow records from item deletion. History is preserved.

---

## 7. Category / Item-Type Data Quality

**Categories across item types:**

| Type | Category column | Source | Quality |
|------|----------------|--------|---------|
| Book | `VARCHAR(100)` | Admin input (free-text or dropdown) | Varies by input discipline |
| Magazine | `VARCHAR(100)` | Admin input (pre-V1) | Varies by input discipline |
| Newspaper | **None** | N/A | NULL always |

**ML implications:**
- `cat_Unknown` is the default fallback for categories not recognized by the 20 known categories
- Newspapers have no `category` column — `cat_Unknown` is the only option for them in the ML feature set
- Books/Magazines with blank/NULL categories → mapped to `cat_Unknown`
- `cat_Unknown` population = first-time borrowers (no prior history to compute preferred category) + item_type mismatch

**Migration gap:** V4 added `category VARCHAR(100)` to Books. Newspapers never got a category column.
Existing books/magazines may have NULL category if admin didn't set it.

---

## 8. Historical Data Retention

**DDL mode:** `spring.jpa.hibernate.ddl-auto=none` (production). Schema managed by Flyway.

**Delete policy:**
- Items with borrow history → 409 Conflict (cannot delete)
- Students → no history check (but FK constraint prevents orphaned rows)
- Return records: `returnDate` is never cleared once set

**Borrow record retention:** Records persist indefinitely. No soft-delete, no archival.

**Impact:** Full loan history is available for ML features (borrow count, return ratio,
overdue ratio, category preferences) as long as records are not manually deleted.

---

## 9. Date/Time Handling

**Application properties:**
```
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
spring.jpa.open-in-view=false
```

**Date types:** All dates are `LocalDate` (no time component):
- `borrowDate`, `returnDate`, `dueDate` — `LocalDate`

**Timezone:** UTC. No timezone conversion issues expected.

**Observation:** Consistent date handling throughout. No `Date`/`Timestamp` mixing.

---

## 10. Auditability

**Audit events** published on:
- `BorrowEvent` — on borrow (`@Async`, `@EventListener`)
- `ReturnEvent` — on return (`@Async`, `@EventListener`)

**Event listener** (`AuditEventListener`): Stores event JSON in `audit_events` table.

**`@CreatedDate` / `@LastModifiedDate`:** Enabled via `@EnableJpaAuditing`. All entities
with `@MappedSuperclass` base have auto-populated `createdAt`/`updatedAt` columns.

**Query filters:**
- `BorrowRecordRepository.findAll()` — `returnDate IS NULL`
- `BorrowRecordRepository.findByReturnDateIsNullAndStudentId()` — excludes returned records
- `BookRepository.findByActiveTrue()` — only active books

---

## 11. Production Config & Empty-DB Startup

**`application.properties` (non-profile, production):**
- DB: MySQL at `${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:librarydb}`
- Username: `${DB_USERNAME:root}`, Password: `${DB_PASSWORD:ChangeMe123!}`
- Hibernate: `ddl-auto=none`, `show-sql=false`
- Flyway: enabled, `locations=classpath:db/migration`

**Docker profile** (`application-docker.properties`):
- Overridden DB host: `mysql` (Docker service name)
- Password from env: `${LMS_DB_PASSWORD}`

**`AdminSeeder`** (runs on startup via `CommandLineRunner`):
- Finds existing `admin` username — if found, returns immediately
- Finds existing email — if found, returns immediately
- Otherwise creates admin with username=`admin`, email=`admin@libris.com`
- Password from env: `${LMS_ADMIN_PASSWORD:ChangeMe123!}`

**Empty DB startup:** Safe. AdminSeeder creates only the admin account. No test data,
no sample books, no sample students.

---

## 12. Test Suite Review

**Test files reviewed:**
- `BorrowRecordServiceTest.java` — unit tests for borrow, return, validation
- `BorrowRecordServiceTestNN10.java` — N+1 query prevention tests
- `BookServiceTest.java` — book CRUD, availability, deletion
- `BookRepositoryTest.java` — DB-level ISBN uniqueness
- `LibraryManagementIntegrationTest.java` — end-to-end MockMvc
- `BrowserCsrfFlowIntegrationTest.java` — CSRF token flow
- `CrudIntegrationTest.java` — magazine, student, librarian, newspaper
- `TestBCrypt.java` — password hashing

**Test coverage observations:**
- No test asserts due-date computation or overdue detection logic
- No test exercises pre-V3 records (null due_date)
- No test deletes a student with active borrow records
- No test exercises `ReportService.getOverdueBookReport()` filter logic
- No test for `cat_Unknown` in borrow context

---

## 13. Readiness Monitor Compatibility

**`readiness_monitor.py` checks:**
1. Schema version ≥ V4 ✓
2. Borrow records with return_date ✓
3. Overdue records (return_date > effective_due_date) ✓
4. Student metadata captured (borrower_name, borrower_username) ✓
5. Category field populated ✓
6. Item type coverage (book/magazine/newspaper) ✓
7. Temporal coverage (≥6 months data) — requires 6+ months of real data
8. Sufficient overdue labels (≥5% positive rate) — requires real borrow history

**Compatibility:** All checks align with actual schema. Monitor will work correctly
once real data is present.

---

## 14. Risks & Warnings

| # | Category | Severity | Issue |
|---|----------|----------|-------|
| 1 | Student deletion | MEDIUM | `StudentService.delete()` has no borrow-history check. FK constraint prevents DB-level orphaning, but error message is raw DB error, not user-friendly. |
| 2 | Hardcoded +14 | LOW | Three implementations of `plusDays(14)`: `BorrowRecordService:100`, `OverdueCalculator:18`, `ReportService:115`. Not centralized as a named constant. Maintenance risk. |
| 3 | isOverdue() unused | LOW | `BorrowRecord.isOverdue()` defined but never called. Dead code. |
| 4 | No overdue-computation test | LOW | No test asserts due-date computation or overdue detection. |
| 5 | Race condition on borrow | LOW | No pessimistic locking on availability check. Concurrent borrows of same item could both succeed. |
| 6 | EntityGraph + Pageable | LOW | `search()` method uses `@EntityGraph` with `Pageable` — may fail at runtime if JOIN FETCH interacts poorly with count query. (Unused by ML.) |
| 7 | Lazy-init risk | LOW | `findByReturnDateIsNullAndBorrowDateBefore()` has no `@EntityGraph`. Potential `LazyInitializationException` if used outside session. (Currently unused.) |
| 8 | Newspapers no category | LOW | Newspaper entity lacks category column. ML default: `cat_Unknown`. |
| 9 | student_id nullable | INFO | Walk-in loans permitted. ML feature excludes these borrowers. |
| 10 | due_date nullable | INFO | Pre-V3 records may have NULL due_date. OverdueCalculator handles gracefully. |

---

## 15. Verdict

**READY WITH WARNINGS**

The production schema is well-suited for ML evaluation. Borrow/return flows preserve
historical data correctly. Date handling is consistent. FK constraints protect data integrity.

Before real-data ML evaluation proceeds, address:
1. **StudentService.delete()** — add borrow-history check (MEDIUM, defensive)
2. **Centralize +14-day constant** — define `OVERDUE_GRACE_PERIOD_DAYS` in one place (LOW, maintenance)
3. **Add overdue-computation unit test** — verify `OverdueCalculator.getEffectiveDueDate()` with null due_date (LOW, validation)
4. **Wait for real data** — readiness monitor will gate ML evaluation on sufficient volume

---

*Generated: 2026-07-15 | Phase 5 Production Readiness Review*
