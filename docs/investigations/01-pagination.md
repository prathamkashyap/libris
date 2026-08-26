# Investigation 01 — Is Pagination Real or Deferred?

## Question

Is pagination actually implemented across the application's REST controllers, or is it deferred/placeholder logic?

## Reason Investigated

Pagination is a critical performance feature for a Library Management System with potentially thousands of records. If pagination is only partially implemented or absent, the application risks loading entire datasets into memory, causing performance degradation and OOM errors. This investigation aimed to verify the completeness and consistency of pagination support.

## Files Examined

- `src/main/java/com/lms/controller/BookController.java`
- `src/main/java/com/lms/controller/MagazineController.java`
- `src/main/java/com/lms/controller/NewspaperController.java`
- `src/main/java/com/lms/controller/StudentController.java`
- `src/main/java/com/lms/controller/LibrarianController.java`
- `src/main/java/com/lms/controller/BorrowRecordController.java`
- `src/main/java/com/lms/controller/AuditController.java`
- `src/main/java/com/lms/controller/ReportController.java`

## Search Commands Used

```bash
grep -rn "Pageable\|@RequestParam.*page\|@RequestParam.*size\|Page<" src/main/java/com/lms/controller/
```

```bash
grep -rn "PageRequest.of\|page =\|size =" src/main/java/com/lms/controller/
```

## Evidence

- 8 controller methods accept `page` and `size` as `@RequestParam` parameters.
- Default values: `page = 0`, `size = 10` across all controllers.
- Exception: `AuditController` defaults `size = 20` (audit logs benefit from denser pages).
- All controllers return `Page<T>` or equivalent paginated wrapper types.
- `Pageable` objects are constructed via `PageRequest.of(page, size)`.
- Repository layer uses Spring Data's `Pageable` parameter consistently.

## Findings

Pagination is **fully implemented and functional** across all listed controllers. The implementation follows a consistent pattern:

1. Controllers accept `page` (0-indexed) and `size` query parameters.
2. Default values ensure sensible behavior even when parameters are omitted.
3. `AuditController` uses a larger default page size (20) appropriate for audit log review.
4. Spring Data `Pageable` is passed through to repository queries, which translate it to SQL `LIMIT`/`OFFSET`.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| Keep as-is | Works, consistent | Already decided |
| Remove pagination | Simpler | Memory issues at scale |
| Cursor-based pagination | Better for large datasets | More complex, not needed at this scale |

## Decision

Pagination is implemented and working. No code changes required. Documentation has been corrected to accurately reflect the current state.

## Verification

- Confirmed 8 controllers with `page`/`size` parameters.
- Default values verified in source code.
- `PageRequest.of()` usage confirmed.
- No controllers found returning unbounded result sets.

## Remaining Uncertainty

- None. Pagination is verified as implemented and functional.
