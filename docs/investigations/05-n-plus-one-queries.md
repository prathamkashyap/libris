# Investigation 05 — Are There N+1 Query Problems?

## Question

Are there N+1 query problems in the application, particularly in service response mapping methods?

## Reason Investigated

N+1 queries are a common performance anti-ORM pattern. When fetching a list of N records, each triggering 1+ lazy-loaded associations, the result is N+1 (or more) SQL queries instead of a single JOIN. This causes severe performance degradation at scale.

## Files Examined

- `src/main/java/com/lms/service/BorrowRecordService.java`
- `src/main/java/com/lms/service/StudentService.java`
- `src/main/java/com/lms/service/LibrarianService.java`
- `src/main/java/com/lms/repository/BorrowRecordRepository.java`
- `src/main/java/com/lms/repository/StudentRepository.java`
- `src/main/java/com/lms/repository/LibrarianRepository.java`
- `src/main/java/com/lms/entity/BorrowRecord.java`
- `src/main/java/com/lms/entity/StudentProfile.java`
- `src/main/java/com/lms/entity/LibrarianProfile.java`

## Search Commands Used

```bash
grep -rn "LAZY\|EAGER\|@OneToMany\|@ManyToOne\|@OneToOne" src/main/java/com/lms/entity/
```

```bash
grep -rn "response\|toResponse\|mapToResponse" src/main/java/com/lms/service/
```

```bash
grep -rn "@EntityGraph\|JOIN FETCH\|JOIN FETCH\|@Query" src/main/java/com/lms/repository/
```

## Evidence

- `BorrowRecordService.response()` accesses 4 LAZY associations per record:
  - `book` (Book)
  - `magazine` (Magazine)
  - `newspaper` (Newspaper)
  - `student` (StudentProfile)
- `StudentService.response()` accesses LAZY `account` (Account) per student.
- `LibrarianService.response()` accesses LAZY `account` (Account) per librarian.
- Each LAZY association access triggers a separate SQL SELECT.
- **No `@EntityGraph` or `JOIN FETCH` was present** in any repository method at the time of investigation.
- Example: `borrowRecordService.getAll()` loading 50 records would execute 1 (list) + 50×4 (associations) = **201 SQL queries**.

## Findings

**Yes, significant N+1 query problems existed.** The LAZY associations were being accessed in a loop without pre-fetching, causing exponential SQL execution.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| `@EntityGraph` on repository methods | Declarative, Spring Data native | Can be verbose |
| `JOIN FETCH` in JPQL `@Query` | Full control | Manual JPQL required |
| `@Fetch(FetchMode.JOIN)` | Hibernate-native | Less portable, may cause issues with pagination |
| Eager fetching on entities | Simple | Always fetches, wastes bandwidth for single-entity access |

## Decision

Add `@EntityGraph` to all list repository methods and `LEFT JOIN FETCH` to JPQL search queries. This pre-fetches all required associations in a single SQL query per list operation.

## Verification

- Commit `bfd9341`: Added `@EntityGraph` to `BorrowRecordRepository`, `StudentRepository`, `LibrarianRepository` list methods.
- Commit `f1fcda0`: Added `LEFT JOIN FETCH` to JPQL search queries for proper association pre-fetching.
- Verified via SQL logging that list operations now execute a single query with JOINs.

## Remaining Uncertainty

- `@EntityGraph` on `Pageable` queries may not always produce optimal SQL (Hibernate may issue count query + main query). This is acceptable for the current scale.
