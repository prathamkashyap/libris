# Investigation 02 — Do StudentService.delete() and LibrarianService.delete() Leave Orphaned Accounts?

## Question

When a student or librarian profile is deleted, is the associated `Account` entity also deleted, or does it become an orphaned record in the database?

## Reason Investigated

Orphaned accounts represent a security risk and data integrity issue. If a student or librarian profile is deleted but the account remains, it could allow unauthorized access or cause foreign key violations in future operations. The investigation was triggered by a potential data inconsistency bug.

## Files Examined

- `src/main/java/com/lms/service/StudentService.java`
- `src/main/java/com/lms/service/LibrarianService.java`
- `src/main/java/com/lms/entity/Account.java`
- `src/main/java/com/lms/entity/StudentProfile.java`
- `src/main/java/com/lms/entity/LibrarianProfile.java`
- `src/main/java/com/lms/repository/AccountRepository.java`
- `src/main/java/com/lms/repository/BorrowRecordRepository.java`

## Search Commands Used

```bash
grep -rn "\.delete\|\.remove\|accounts\." src/main/java/com/lms/service/StudentService.java
```

```bash
grep -rn "\.delete\|\.remove\|accounts\." src/main/java/com/lms/service/LibrarianService.java
```

```bash
grep -rn "FOREIGN\|CASCADE\|RESTRICT\|borrow_records" src/main/resources/application*.properties
```

## Evidence

- `StudentService.delete()` calls `students.delete(p)` where `p` is a `StudentProfile`.
- `LibrarianService.delete()` calls `librarians.delete(p)` where `p` is a `LibrarianProfile`.
- Neither method deletes the associated `Account` entity.
- `StudentProfile` has a `@OneToOne` relationship to `Account`.
- `BorrowRecord` has a `@ManyToOne` FK to `StudentProfile`.
- Hibernate's default FK behavior is `RESTRICT` — if active borrows exist, the delete will fail with a constraint violation.
- The `Account` record remains in the `accounts` table after profile deletion.

## Findings

**Yes, both `StudentService.delete()` and `LibrarianService.delete()` leave orphaned accounts.** The profile is deleted but the linked `Account` entity persists.

Additionally, `borrow_records` FK to `student_profiles` would block deletion if active borrows exist (Hibernate RESTRICT constraint).

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| Delete account after profile | Fixes orphaning | Requires careful ordering |
| Use `orphanRemoval = true` on relationship | Automatic cleanup | Hibernate-specific, may cascade unexpectedly |
| Soft-delete profiles | Preserves referential integrity | More complex, no actual cleanup |
| Add DB-level CASCADE DELETE | Automatic at DB level | Less portable, harder to control |

## Decision

Add `accounts.delete(account)` after `students.delete(p)` in `StudentService` and after `librarians.delete(p)` in `LibrarianService`. This ensures no orphaned accounts remain after profile deletion.

## Verification

- Commit `374560c` contains the fix.
- Both service methods now delete the account after deleting the profile.
- Verified that the account reference is obtained before the profile delete call (to avoid null reference after deletion).

## Remaining Uncertainty

- Edge case: if the account deletion fails after profile deletion succeeds, the profile is lost but account remains. A transaction rollback mechanism may be worth considering for production hardening.
