# Testing

> **Source of truth as of:** 30 July 2026

**Framework:** JUnit 5 + Spring Boot Test + MockMvc  
**Database:** H2 in MySQL compatibility mode (`MODE=MySQL;DATABASE_TO_LOWER=TRUE`)  
**Schema strategy:** `create-drop` per test run  
**Security testing:** `spring-security-test` (`csrf()`, `user().roles()`)

See [ARCHITECTURE.md](ARCHITECTURE.md) for system context and [API.md](API.md) for endpoint reference.

---

## Running Tests

### Full suite

```bash
./mvnw clean test
```

### Single test class

```bash
./mvnw test -Dtest=LibraryManagementIntegrationTest
```

### With Maven verify (includes CI checks)

```bash
./mvnw clean verify
```

---

## Test Inventory

### Integration Tests

#### `LibraryManagementIntegrationTest`

**Type:** MockMvc + admin session  
**Methods:** 4

| Test | What It Covers |
|------|----------------|
| `unauthenticatedAndForbiddenResponsesUseApiErrorShape` | Unauthenticated GET → 401; LIBRARIAN accessing `/api/librarians` → 403 |
| `fullBooksPeopleBorrowReturnAndLogoutFlow` | Book CRUD, student create, librarian create, borrow book, return book, duplicate ISBN conflict, validation errors, book deletion with history → 409, logout |
| `invalidBookRequestReturnsFieldErrors` | Book with empty title → 400 with `VALIDATION_ERROR` and `fieldErrors` |
| `duplicateIsbnReturnsConflictAndValidationReturnsFieldMessage` | Duplicate ISBN → 409 `CONFLICT`; invalid student email → 400 `VALIDATION_ERROR` |

**Coverage:** Authentication, full CRUD + borrow/return lifecycle, validation errors, ISBN conflicts, role checks (401/403), logout.

#### `BrowserCsrfFlowIntegrationTest`

**Type:** Real CSRF cookie/header flow (not MockMvc `csrf()`)  
**Methods:** 1

| Test | What It Covers |
|------|----------------|
| `browserCookieAndHeaderCsrfFlowAuthenticatesAndLogsOut` | CSRF bootstrap → login with CSRF header → session reuse → logout → post-logout rejection |

**Coverage:** Verifies the browser-equivalent CSRF flow works correctly with `SpaCsrfTokenRequestHandler`.

### Repository Tests

#### `BookRepositoryTest`

**Type:** Spring Data JPA repository  
**Methods:** 1

| Test | What It Covers |
|------|----------------|
| `persistsAuditTimestampsAndRejectsDuplicateIsbn` | Verifies `createdAt` and `updatedAt` are auto-populated; verifies ISBN uniqueness constraint at DB level |

**Coverage:** Audit timestamp population, ISBN uniqueness constraint at DB level.

### Summary

| Test Class | Type | Methods | Total |
|------------|------|---------|-------|
| `LibraryManagementIntegrationTest` | Integration (MockMvc) | 4 | |
| `BrowserCsrfFlowIntegrationTest` | Integration (CSRF flow) | 1 | |
| `BookRepositoryTest` | Repository | 1 | |
| **Total** | | | **6** |

---

## What Is Tested

| Area | Tested | Test File |
|------|:------:|-----------|
| Admin login | ✅ | `LibraryManagementIntegrationTest` |
| Unauthenticated access → 401 | ✅ | `LibraryManagementIntegrationTest` |
| Forbidden access → 403 | ✅ | `LibraryManagementIntegrationTest` |
| Student CRUD (create) | ✅ | `LibraryManagementIntegrationTest` |
| Librarian CRUD (create) | ✅ | `LibraryManagementIntegrationTest` |
| Book CRUD (create, search) | ✅ | `LibraryManagementIntegrationTest` |
| Book validation errors | ✅ | `LibraryManagementIntegrationTest` |
| Duplicate ISBN conflict | ✅ | `LibraryManagementIntegrationTest` |
| Borrow workflow | ✅ | `LibraryManagementIntegrationTest` |
| Unavailable book borrow rejection | ✅ | `LibraryManagementIntegrationTest` |
| Return workflow | ✅ | `LibraryManagementIntegrationTest` |
| Already-returned rejection | ✅ | `LibraryManagementIntegrationTest` |
| Book deletion with history → 409 | ✅ | `LibraryManagementIntegrationTest` |
| Logout | ✅ | `LibraryManagementIntegrationTest` |
| CSRF cookie/header exchange | ✅ | `BrowserCsrfFlowIntegrationTest` |
| Audit timestamp population | ✅ | `BookRepositoryTest` |
| ISBN DB uniqueness constraint | ✅ | `BookRepositoryTest` |
| Email validation message | ✅ | `LibraryManagementIntegrationTest` |

---

## What Is NOT Tested

| Area | Status | Notes |
|------|:------:|-------|
| Student/librarian update (PUT) | ❌ | No test |
| Student/librarian delete (DELETE) | ❌ | No test |
| Book update (PUT) | ❌ | No test |
| Book delete with no history (success case) | ❌ | No test |
| Dashboard values | ❌ | No test |
| Profile endpoint | ❌ | No test |
| Username uniqueness (students/librarians) | ❌ | No test |
| STUDENT role access restrictions (books GET only) | ❌ | No test |
| LIBRARIAN role functional access | ❌ | Only 403 tested, not functional access |
| Frontend JavaScript | ❌ | No JS tests exist |
| Unit tests (isolated service logic) | ❌ | All tests are integration or repository level |
| Magazine/Newspaper CRUD | ❌ | No tests |

---

## Test Infrastructure

### H2 Configuration (`src/test/resources/application.properties`)

```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
```

### Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-test` | MockMvc, AssertJ, JUnit 5 |
| `spring-security-test` | `csrf()`, `user().roles()` |
| `h2` | In-memory database for tests |
| `hamcrest` | Assertion matchers |

---

## Manual Test Matrix

The black-box test matrix is defined in [testing/black-box-test-cases.csv](testing/black-box-test-cases.csv) with 14 cases. All have status `PENDING LOCAL MYSQL` — they require a running MySQL instance and have not been executed in CI.

Representative cases:

| ID | Scenario | Expected Result |
|----|----------|-----------------|
| BB-01 | Login with valid credentials | 200, session established |
| BB-02 | Login with wrong password | 401, clear failure message |
| BB-03 | Create book with valid input | 201, book appears as available |
| BB-04 | Create book without title | 400 with field error |
| BB-05 | Request non-existent book | 404 structured error |
| BB-06 | Borrow available book | 201, book unavailable |
| BB-07 | Borrow unavailable book | 400 `BOOK_UNAVAILABLE` |
| BB-08 | Return active record | 204, book available |
| BB-09 | Return same record again | 400 `ALREADY_RETURNED` |
| BB-10 | Delete book with history | 409, history retained |
| BB-11 | Librarian attempts librarian management | 403 |
| BB-12 | Student attempts book mutation | 403 |
| BB-13 | Narrow-screen layout | Content readable, actions usable |
| BB-14 | Logout then protected endpoint | 401 |

---

## CI Integration

GitHub Actions runs `mvn clean verify` on push to `main`. This compiles the project, runs all tests, and fails the build on any test failure.

```yaml
# .github/workflows/ci.yml
- name: Build and test
  run: mvn clean verify
```

---

## Gaps and Recommendations

| Priority | Gap | Recommendation |
|----------|-----|----------------|
| High | No student/librarian update or delete tests | Add MockMvc tests for PUT and DELETE endpoints |
| High | No STUDENT role authorization test | Add tests verifying STUDENT can only GET books |
| High | No dashboard or profile tests | Add MockMvc tests for these endpoints |
| Medium | No unit tests for services | Add isolated service tests for borrow availability, return idempotency |
| Medium | No frontend JavaScript tests | Consider adding vitest or jest for JS modules |
| Medium | Magazine/Newspaper untested | Add MockMvc tests for these CRUD endpoints |
| Low | Manual tests not executed | Run black-box matrix against local MySQL |
