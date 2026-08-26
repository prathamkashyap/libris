# Investigation 07 — Why Were Tests Failing with 401 on Login?

## Question

Why were integration tests failing with HTTP 401 (Unauthorized) on the login endpoint?

## Reason Investigated

Tests that previously passed began failing with 401 responses on login. This suggested a credential mismatch — either the test's hardcoded credentials no longer matched the seeded admin account, or an environment variable was overriding test configuration.

## Files Examined

- `src/test/java/com/lms/integration/` (all integration test classes)
- `src/main/resources/application-h2.properties`
- `src/test/resources/application-test.properties`
- `src/main/java/com/lms/seed/AdminSeeder.java`
- `src/main/java/com/lms/service/AccountService.java`

## Search Commands Used

```bash
grep -rn "LMS_ADMIN_PASSWORD\|lms.admin.password\|admin.*password" src/
```

```bash
grep -rn "hardcoded\|\"admin\|\"ChangeMe\|\"test" src/test/java/com/lms/integration/
```

```bash
grep -rn "@Value.*lms.admin" src/test/java/com/lms/integration/
```

## Evidence

- `application-h2.properties` contained `lms.admin.password=admin123`.
- The `LMS_ADMIN_PASSWORD` environment variable was set in the shell (value: `admin123`).
- Spring Boot property precedence: env vars > properties files > defaults.
- Integration tests **hardcoded** the password as a string literal (e.g., `"ChangeMe123!"`) instead of using `@Value`.
- `AdminSeeder` uses `@Value("${LMS_ADMIN_PASSWORD:ChangeMe123!}")` — the env var overrode the default, seeding with `admin123`.
- Tests sent `"ChangeMe123!"` but the account was seeded with `admin123` → 401.
- The env var was invisible in the properties files, making debugging difficult.

## Findings

**The `LMS_ADMIN_PASSWORD=admin123` environment variable was overriding the test's `lms.admin.password=ChangeMe123!` via `application-h2.properties`.** Tests used hardcoded strings instead of `@Value`, so they never picked up the actual configured password.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| Remove env var override | Simple | Fragile if someone sets it again |
| Remove lms.admin.password from application-h2.properties | Clean | Still env var can override |
| Use @Value in tests + sensible default | Correct, resilient | Slightly more code |
| All of the above | Defense in depth | Best approach |

## Decision

Three-pronged fix:
1. Remove `lms.admin.password` from `application-h2.properties` (was overriding tests).
2. Use `@Value` in test classes instead of hardcoded strings.
3. Use `${LMS_ADMIN_PASSWORD:ChangeMe123!}` as the default in `AdminSeeder` so tests get a predictable password unless an env var is explicitly set.

## Verification

- Commit `ffc308d` contains the fix.
- Integration tests now use `@Value("${lms.admin.password:ChangeMe123!}")`.
- `application-h2.properties` no longer contains `lms.admin.password`.
- Tests pass with or without the `LMS_ADMIN_PASSWORD` env var.

## Remaining Uncertainty

- Developers who set `LMS_ADMIN_PASSWORD` in their shell will need to be aware it affects local dev. This is acceptable and documented behavior.
