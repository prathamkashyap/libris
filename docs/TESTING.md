# Testing

## Automated Tests

Run all tests from the repository root:

```bash
./mvnw clean test
```

The suite uses H2 in MySQL compatibility mode and covers:

- Authentication, logout, session reuse, and protected routes.
- Browser-equivalent CSRF cookie/header login and logout flow.
- Books, students, librarians, borrow, and return workflows.
- Validation errors, duplicate ISBN conflict handling, and JSON 401/403 responses.
- Repository-level ISBN uniqueness and auditing timestamps.

## Manual Verification

Use [testing/black-box-test-cases.csv](testing/black-box-test-cases.csv) against a local MySQL instance. Capture release evidence in `../screenshots/desktop` and `../screenshots/mobile` for login, CRUD, validation, borrow/return, role restrictions, and responsive navigation.
