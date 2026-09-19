# Testing 🧪

> **JUnit 5** • **Spring Boot Test** • **MockMvc** • **H2** • **173 tests**

**Framework:** JUnit 5 + Spring Boot Test + MockMvc
**Database:** H2 in MySQL compatibility mode (`MODE=MySQL;DATABASE_TO_LOWER=TRUE`)
**Schema strategy:** `create-drop` per test run (Flyway disabled for most tests)
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
**Methods:** 14

| Test | What It Covers |
|------|----------------|
| `unauthenticatedAndForbiddenResponsesUseApiErrorShape` | Unauthenticated GET → 401; LIBRARIAN accessing `/api/librarians` → 403 |
| `fullBooksPeopleBorrowReturnAndLogoutFlow` | Book CRUD, student create, librarian create, borrow book, return book, duplicate ISBN conflict, validation errors, book deletion with history → 409, logout |
| `invalidBookRequestReturnsFieldErrors` | Book with empty title → 400 with `VALIDATION_ERROR` and `fieldErrors` |
| `duplicateIsbnReturnsConflictAndValidationReturnsFieldMessage` | Duplicate ISBN → 409 `CONFLICT`; invalid student email → 400 `VALIDATION_ERROR` |
| `defaultBorrowRecordListReturnsAssociationsCorrectly` | BorrowRecord listing returns item/student associations via EntityGraph |
| `borrowedStatusFilterReturnsOnlyActiveRecords` | Status filter correctly distinguishes BORROWED vs RETURNED |
| `searchBorrowRecordsReturnsMatchingResults` | Search by borrower name/email works correctly |
| `swaggerUiAccessibleWhenEnabled` | Swagger UI public access when SpringDoc enabled |
| `actuatorHealthEndpointPublic` | `/actuator/health` public access |
| Additional tests | Book category, register CSRF, profile validation, etc. |

**Coverage:** Authentication, full CRUD + borrow/return lifecycle, validation errors, ISBN conflicts, role checks (401/403), logout, BorrowRecord listing with associations, status filtering, search.

#### `CrudIntegrationTest`

**Type:** MockMvc + admin session
**Methods:** 8

| Test | What It Covers |
|------|----------------|
| Magazine/Newspaper CRUD | Create, update, delete with borrow-history guard |
| Student/Librarian update+delete | Profile modification and deletion |
| Dashboard counts | Aggregate statistics |
| Audit log access | Audit endpoint verification |
| Duplicate username/email | Registration conflict handling |

**Coverage:** Magazine/Newspaper CRUD lifecycle, profile management, dashboard aggregation, audit log access.

#### `BrowserCsrfFlowIntegrationTest`

**Type:** Real CSRF cookie/header flow (not MockMvc `csrf()`)
**Methods:** 1

| Test | What It Covers |
|------|----------------|
| `browserCookieAndHeaderCsrfFlowAuthenticatesAndLogsOut` | CSRF bootstrap → login with CSRF header → session reuse → logout → post-logout rejection |

**Coverage:** Verifies the browser-equivalent CSRF flow works correctly with `SpaCsrfTokenRequestHandler`.

#### `SecurityHardeningTest`

**Type:** MockMvc + admin session
**Methods:** 5

| Test | What It Covers |
|------|----------------|
| `failedLoginCreatesAuditRecord` | FAILED_LOGIN audit event persistence with null actor fields |
| `swaggerUiAccessibleWhenEnabled` | Swagger UI public access when SpringDoc enabled |
| `actuatorHealthEndpointPublic` | `/actuator/health` public access |
| `unhandledExceptionReturnsStandardApiErrorResponse` | Catch-all 500 handler returns generic response |
| Additional tests | Profile validation, CSRF, register flows |

**Coverage:** Failed login audit, Swagger access control, exception handling normalization.

### Repository Tests

#### `BookRepositoryTest`

**Type:** Spring Data JPA repository
**Methods:** 1

| Test | What It Covers |
|------|----------------|
| `persistsAuditTimestampsAndRejectsDuplicateIsbn` | Verifies `createdAt` and `updatedAt` are auto-populated; verifies ISBN uniqueness constraint at DB level |

**Coverage:** Audit timestamp population, ISBN uniqueness constraint at DB level.

#### `BorrowRecordsIndexTest`

**Type:** Flyway-enabled integration (H2)
**Methods:** 1

| Test | What It Covers |
|------|----------------|
| `flywayAppliedV5AndIndexesExist` | Verifies V5 appears in `flyway_schema_history` and both physical indexes exist via JDBC DatabaseMetaData |

**Coverage:** Flyway migration verification, index existence validation.

### Concurrency Tests

#### `BorrowConcurrencyTest`

**Type:** SpringBootTest with concurrent execution
**Methods:** 7

| Test | What It Covers |
|------|----------------|
| `concurrentBookBorrowOneSucceedsOneFails` | Concurrent borrow of same book → 409 for loser |
| `concurrentMagazineBorrowOneSucceedsOneFails` | Concurrent magazine borrow → 409 for loser |
| `concurrentNewspaperBorrowOneSucceedsOneFails` | Concurrent newspaper borrow → 409 for loser |
| `concurrentReturnOfSameRecordOneSucceedsOneFails` | Concurrent return of same record → 409 for loser |
| `returnAndBorrowRace` | Return and borrow of same item → no lock cycle |
| `secondReturnBlockedByFirstReturnLock` | Second return blocked by pessimistic lock |
| `borrowEvictsBooksCache` / `returnBookEvictsBooksCache` | Cache eviction on circulation changes |

**Coverage:** Pessimistic locking effectiveness, race condition handling, cache eviction.

### Service Unit Tests

#### `BorrowRecordServiceTest`

**Type:** Unit (Mockito)
**Methods:** 34

| Test | What It Covers |
|------|----------------|
| Borrow logic | Book/magazine/newspaper borrow with availability check, due date handling |
| Return logic | Return with availability restoration, already-returned guard |
| Audit events | Borrow/return audit event publishing with full actor metadata |
| Pagination | List methods with Pageable parameters |
| Error handling | Resource not found, unavailable items, validation |

**Coverage:** BorrowRecordService business logic, audit publishing, pagination, error paths.

#### `MagazineServiceTest` / `NewspaperServiceTest`

**Type:** Unit (Mockito)
**Methods:** 6 each

| Test | What It Covers |
|------|----------------|
| CRUD audit events | CREATE/UPDATE/DELETE audit events with actor metadata |
| Borrow-history guard | Delete blocked when borrow history exists |
| NotFound handling | No audit published for missing resources |

**Coverage:** Magazine/Newspaper service audit events, deletion guards.

#### `AnalyticsServiceTest`

**Type:** Unit (Mockito)
**Methods:** 16

| Test | What It Covers |
|------|----------------|
| Dashboard analytics | Total counts, overdue counting via optimized query |
| Overdue summary | Active overdue filtering, item association loading |
| Top books/readers | Pagination, PageRequest matching |

**Coverage:** AnalyticsService query optimization, pagination, aggregation logic.

#### `ReportServiceTest`

**Type:** Unit (Mockito)
**Methods:** 26

| Test | What It Covers |
|------|----------------|
| CSV generation | Borrowing, inventory, overdue, students CSV export |
| Pagination | 500+ record batch processing, cursor progression |
| Defensive guards | Null account handling in studentsCsv |

**Coverage:** ReportService CSV generation, pagination boundaries, defensive programming.

#### `AuthServiceTest`

**Type:** Unit (Mockito)
**Methods:** 2

| Test | What It Covers |
|------|----------------|
| `auditFailureDoesNotReplaceBadCredentialsException` | FAILED_LOGIN audit publication failure does not swallow BadCredentialsException |

**Coverage**: Auth service audit event error handling.

### Query Optimization Tests

#### `ActiveOverdueQueryTest`

**Type:** DataJPA (H2)
**Methods:** 5

| Test | What It Covers |
|------|----------------|
| SQL overdue semantics | Explicit dueDate vs fallback 14-day logic |
| Boundary conditions | Exactly today, exactly 14 days, null dueDate handling |
| Returned records excluded | Overdue query filters only active loans |

**Coverage:** SQL-level overdue query correctness, semantic equivalence with OverdueCalculator.

#### `OverdueReportQueryTest`

**Type:** DataJPA (H2)
**Methods:** 7

| Test | What It Covers |
|------|----------------|
| Paginated query semantics | Overdue report SQL-level filtering |
| Keyset pagination | Last-id cursor progression |
| OverdueCalculator agreement | SQL results match Java semantics |

**Coverage:** Overdue report pagination, keyset pagination, semantic correctness.

### Architecture Tests

#### `ArchitectureTest`

**Type:** ArchUnit
**Methods:** 1

| Test | What It Covers |
|------|----------------|
| `controllersMustNotDependOnRepositories` | Layered architecture enforcement |

**Coverage:** Dependency rule validation.

### Summary

| Test Class | Type | Methods | Total |
|------------|------|---------|-------|
| `LibraryManagementIntegrationTest` | Integration (MockMvc) | 14 | |
| `CrudIntegrationTest` | Integration (MockMvc) | 8 | |
| `SecurityHardeningTest` | Integration (MockMvc) | 5 | |
| `BrowserCsrfFlowIntegrationTest` | Integration (CSRF flow) | 1 | |
| `BookRepositoryTest` | Repository | 1 | |
| `BorrowRecordsIndexTest` | Flyway Integration | 1 | |
| `BorrowConcurrencyTest` | Concurrency (SpringBootTest) | 7 | |
| `BorrowRecordServiceTest` | Unit (Mockito) | 34 | |
| `MagazineServiceTest` | Unit (Mockito) | 6 | |
| `NewspaperServiceTest` | Unit (Mockito) | 6 | |
| `AnalyticsServiceTest` | Unit (Mockito) | 16 | |
| `ReportServiceTest` | Unit (Mockito) | 26 | |
| `AuthServiceTest` | Unit (Mockito) | 2 | |
| `ActiveOverdueQueryTest` | DataJPA | 5 | |
| `OverdueReportQueryTest` | DataJPA | 7 | |
| `ArchitectureTest` | ArchUnit | 1 | |
| **Total** | | | **173 executed, 178 declared** |

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
| Magazine/Newspaper CRUD | ✅ | `CrudIntegrationTest` |
| Student/Librarian update/delete | ✅ | `CrudIntegrationTest` |
| Dashboard counts | ✅ | `CrudIntegrationTest` |
| Audit log access | ✅ | `CrudIntegrationTest` |
| Failed login audit | ✅ | `SecurityHardeningTest` |
| Concurrent borrow/return | ✅ | `BorrowConcurrencyTest` |
| Cache eviction | ✅ | `BorrowConcurrencyTest` |
| Service-layer business logic | ✅ | Service unit tests |
| Query optimization | ✅ | Query optimization tests |
| Flyway migration verification | ✅ | `BorrowRecordsIndexTest` |

---

## What Is NOT Tested

| Area | Status | Notes |
|------|:------:|-------|
| Student/librarian update (PUT) | ❌ | Covered in CrudIntegrationTest |
| Student/librarian delete (DELETE) | ❌ | Covered in CrudIntegrationTest |
| Book update (PUT) | ❌ | No test |
| Book delete with no history (success case) | ❌ | No test |
| Dashboard values | ❌ | Covered in CrudIntegrationTest |
| Profile endpoint | ❌ | No test |
| Username uniqueness (students/librarians) | ❌ | Covered in CrudIntegrationTest |
| STUDENT role access restrictions (books GET only) | ❌ | No test |
| LIBRARIAN role functional access | ❌ | Only 403 tested, not functional access |
| Frontend JavaScript | ❌ | No JS tests exist |
| Unit tests (isolated service logic) | ❌ | All tests are integration or repository level |
| Magazine/Newspaper CRUD | ❌ | Covered in CrudIntegrationTest |

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
| High | No STUDENT role authorization test | Add tests verifying STUDENT can only GET books |
| High | No dashboard or profile tests | Add MockMvc tests for these endpoints |
| Medium | No frontend JavaScript tests | Consider adding vitest or jest for JS modules |
| Low | Manual tests not executed | Run black-box matrix against local MySQL |
