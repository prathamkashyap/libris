# Investigation 06 — Is AdminSeeder Leaking Password Info?

## Question

Does the `AdminSeeder` class leak sensitive information (passwords) through logging output?

## Reason Investigated

Security best practices dictate that passwords and credentials should never appear in logs. If `AdminSeeder` logs partial or full passwords, this constitutes a security vulnerability that could be exploited through log aggregation systems, CI/CD output, or terminal history.

## Files Examined

- `src/main/java/com/lms/seed/AdminSeeder.java`

## Search Commands Used

```bash
grep -rn "println\|System.out\|System.err\|password\|Password" src/main/java/com/lms/seed/AdminSeeder.java
```

## Evidence

- `AdminSeeder` contained **9 `System.out.println()` calls**.
- Several calls included partial password disclosure — specifically the **last 4 characters** of the admin password.
- `System.out.println` bypasses SLF4J/Logback, making it impossible to filter or suppress in production.
- The seeder runs on application startup, meaning the password info appears in every startup log.

## Findings

**Yes, `AdminSeeder` was leaking password information.** Partial password disclosure (last 4 chars) via `System.out.println` is a security risk. Additionally, using `System.out` instead of SLF4J violates logging best practices.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| Replace with SLF4J, remove password info | Clean, secure | None |
| Keep System.out, mask password | Still leaks structure | Bypasses log framework |
| Remove all logging from seeder | No leak | No audit trail |
| Use SLF4J with password masking | Structured logging | More code |

## Decision

Replace all `System.out.println` calls with SLF4J logging. Remove password disclosure entirely — no partial or full password should appear in any log output.

## Verification

- Commit `ffc308d` contains the fix.
- All `System.out.println` calls replaced with `log.info()` / `log.debug()`.
- No password information appears in any logging statement.

## Remaining Uncertainty

- None. Password disclosure is fully eliminated.
