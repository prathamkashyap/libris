# Testing Knowledge Base

> **Project:** Library Management System (Project A)
> **Last updated:** 01 August 2026
> **Purpose:** Engineering reference for writing, running, and maintaining automated tests.

---

## Table of Contents

1. [Test Inventory](#1-test-inventory)
2. [Test Infrastructure](#2-test-infrastructure)
3. [Test Configuration](#3-test-configuration)
4. [Test Patterns](#4-test-patterns)
5. [Known Test Issues](#5-known-test-issues)
6. [Coverage Gaps](#6-coverage-gaps)
7. [How to Run](#7-how-to-run)
8. [CI Integration](#8-ci-integration)

---

## 1. Test Inventory

### Overview

| Test Class | Type | Methods | Lines of Code |
|------------|------|---------|---------------|
| `LibraryManagementIntegrationTest` | Integration (MockMvc) | 4 | 222 |
| `CrudIntegrationTest` | Integration (MockMvc) | 7 | 263 |
| `BrowserCsrfFlowIntegrationTest` | Integration (CSRF flow) | 1 | 68 |
| `BookRepositoryTest` | Repository | 1 | 34 |
| **Total** | | **13** | **587** |

---

### `LibraryManagementIntegrationTest`

**File:** `backend/src/test/java/com/example/lms/LibraryManagementIntegrationTest.java`
**Lines:** 222
**Annotations:** `@SpringBootTest`, `@AutoConfigureMockMvc`

| Method | Lines | What It Covers |
|--------|-------|----------------|
| `login()` (BeforeEach) | 31–50 | Gets CSRF cookie from `/api/auth/csrf`, logs in as admin, captures session |
| `unauthenticatedAndForbiddenResponsesUseApiErrorShape()` | 52–65 | Unauthenticated GET `/api/books` → 401 `UNAUTHORIZED`; LIBRARIAN role accessing `/api/librarians` → 403 `FORBIDDEN` |
| `fullBooksPeopleBorrowReturnAndLogoutFlow()` | 68–169 | Full lifecycle: create student → create librarian → create book → search book → borrow book → duplicate borrow rejected (`UNAVAILABLE`) → return book → double-return rejected (`ALREADY_RETURNED`) → delete book with history → 409 `CONFLICT` → logout |
| `invalidBookRequestReturnsFieldErrors()` | 172–183 | POST book with empty title → 400 `VALIDATION_ERROR` with `fieldErrors` array |
| `duplicateIsbnReturnsConflictAndValidationReturnsFieldMessage()` | 186–221 | Duplicate ISBN → 409 `CONFLICT`; invalid student email → 400 `VALIDATION_ERROR` with field-specific message |

**Coverage scope:** Authentication, full CRUD + borrow/return lifecycle, validation errors, ISBN conflicts, role checks (401/403), logout.

---

### `CrudIntegrationTest`

**File:** `backend/src/test/java/com/example/lms/CrudIntegrationTest.java`
**Lines:** 263
**Annotations:** `@SpringBootTest`, `@AutoConfigureMockMvc`, `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)`

| Method | Order | Lines | What It Covers |
|--------|-------|-------|----------------|
| `login()` (BeforeEach) | — | 32–50 | Same CSRF + admin login pattern as `LibraryManagementIntegrationTest` |
| `authPost()` | — | 52–63 | Helper: authenticated POST with CSRF, returns response body string |
| `authGet()` | — | 65–74 | Helper: authenticated GET with CSRF, returns response body string |
| `magazineCrud()` | 1 | 80–117 | Create magazine → GET by ID → search → delete → GET returns 404 |
| `newspaperCrud()` | 2 | 123–145 | Create newspaper → GET by ID → delete |
| `studentUpdateAndDelete()` | 3 | 151–182 | Create student → PUT update name/email/phone → DELETE → GET returns 404 |
| `librarianUpdateAndDelete()` | 4 | 188–219 | Create librarian → PUT update name/age/phone → DELETE → GET returns 404 |
| `dashboardReturnsCounts()` | 5 | 225–229 | GET `/api/dashboard` → response contains `totalBooks` and `totalStudents` |
| `auditLogAccessibleByAdmin()` | 6 | 235–242 | GET `/api/audit?page=0&size=5` → 200 OK |
| `duplicateUsernameReturnsConflict()` | 7 | 248–262 | Create student with username → create another with same username → 409 `CONFLICT` |

**Coverage scope:** Magazine/Newspaper CRUD, Student/Librarian update+delete, Dashboard counts, Audit log access, Duplicate username conflict.

---

### `BrowserCsrfFlowIntegrationTest`

**File:** `backend/src/test/java/com/example/lms/BrowserCsrfFlowIntegrationTest.java`
**Lines:** 68
**Annotations:** `@SpringBootTest`, `@AutoConfigureMockMvc`

| Method | Lines | What It Covers |
|--------|-------|----------------|
| `browserCookieAndHeaderCsrfFlowAuthenticatesAndLogsOut()` | 29–67 | GET `/api/auth/csrf` → verify response shape (`headerName`) → login with CSRF cookie + header → GET `/api/auth/me` with session → logout → GET `/api/auth/me` returns 401 |

**Coverage scope:** Full browser-equivalent CSRF flow: CSRF bootstrap, login, session reuse, authenticated `/me`, logout, post-logout rejection. Validates the `SpaCsrfTokenRequestHandler` integration.

---

### `BookRepositoryTest`

**File:** `backend/src/test/java/com/example/lms/BookRepositoryTest.java`
**Lines:** 34
**Annotations:** `@SpringBootTest`

| Method | Lines | What It Covers |
|--------|-------|----------------|
| `persistsAuditTimestampsAndRejectsDuplicateIsbn()` | 19–25 | Save book → verify `createdAt` and `updatedAt` are non-null → save duplicate ISBN → expect `DataIntegrityViolationException` |

**Coverage scope:** Audit timestamp auto-population, ISBN uniqueness constraint at DB level.

---

## 2. Test Infrastructure

### Stack

| Component | Role |
|-----------|------|
| **JUnit 5** | Test framework (`@Test`, `@BeforeEach`, `@TestMethodOrder`) |
| **Spring Boot Test** | `@SpringBootTest` boots full application context |
| **MockMvc** | Simulates HTTP requests without starting a real server |
| **H2 in-memory database** | Fast, disposable, MySQL-compatible (`MODE=MySQL`) |
| **spring-security-test** | `SecurityMockMvcRequestPostProcessors.user()` for role-based tests |
| **Jackson ObjectMapper** | Parses JSON responses (`json.readTree()`) |

### How the Application Context Boots

1. `@SpringBootTest` starts the full Spring Boot application with all beans.
2. `@AutoConfigureMockMvc` auto-configures a `MockMvc` instance wired to the application's security filter chain.
3. The test `application.properties` overrides production config to use H2 instead of MySQL.
4. `spring.jpa.hibernate.ddl-auto=create-drop` creates the schema from JPA entities at startup and drops it at shutdown.
5. Each test class gets its own fresh H2 database instance (same DB name `lms-test` with `DB_CLOSE_DELAY=-1`).

### CSRF Flow in Tests

The application uses CSRF protection with a readable SPA cookie pattern. Every authenticated request must:

1. **Bootstrap CSRF:** `GET /api/auth/csrf` → returns `{ "headerName": "X-XSRF-TOKEN" }` and sets `XSRF-TOKEN` cookie.
2. **Login with CSRF:** `POST /api/auth/login` with:
   - Cookie: `XSRF-TOKEN` (from step 1)
   - Header: `X-XSRF-TOKEN` (same value as cookie)
   - Body: `{ "username": "...", "password": "..." }`
3. **Capture session:** The response creates a `JSESSIONID` cookie that persists across requests.
4. **Authenticated requests:** Every subsequent request sends the session + CSRF cookie + header.

This is NOT the Spring Security test `csrf()` helper — it's a real browser-equivalent flow.

### Role-Based Testing

For role-specific tests (e.g., LIBRARIAN forbidden access), use `SecurityMockMvcRequestPostProcessors`:

```java
mvc.perform(
    get("/api/librarians")
        .with(SecurityMockMvcRequestPostProcessors.user("lib").roles("LIBRARIAN")))
```

This bypasses the session-based login and injects a mock authenticated user with the specified role.

---

## 3. Test Configuration

### Test `application.properties`

**File:** `backend/src/test/resources/application.properties`

```properties
# H2 in-memory database with MySQL compatibility
spring.datasource.url=jdbc:h2:mem:lms-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# Schema created from entities, dropped on shutdown
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.open-in-view=false

# OAuth2 stubs (required for SecurityConfig to initialize)
spring.security.oauth2.client.registration.google.client-id=test
spring.security.oauth2.client.registration.google.client-secret=test
spring.security.oauth2.client.registration.google.scope=profile,email

# Admin credentials (overridable via env var)
lms.admin.username=admin
lms.admin.password=${LMS_ADMIN_PASSWORD:ChangeMe123!}
```

**Key points:**
- `DB_CLOSE_DELAY=-1` keeps the in-memory DB alive across multiple test methods in the same JVM.
- `DATABASE_TO_LOWER=TRUE` ensures case-insensitive string comparisons match MySQL behavior.
- `open-in-view=false` disables the OpenSessionInView filter (matching production).
- `lms.admin.password` uses Spring's `${ENV_VAR:default}` syntax — defaults to `ChangeMe123!` unless `LMS_ADMIN_PASSWORD` is set.

### Dev H2 Profile (`application-h2.properties`)

**File:** `backend/src/main/resources/application-h2.properties`

```properties
spring.datasource.url=jdbc:h2:mem:lms;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.security.oauth2.client.registration.google.client-id=dev
spring.security.oauth2.client.registration.google.client-secret=dev
```

This profile (`spring.profiles.active=h2`) is for local development without MySQL. It differs from the test config:
- DB name is `lms` (not `lms-test`).
- H2 console is enabled for browser-based inspection.
- `lms.admin.password` is inherited from the main `application.properties`.

### `@Value` Injection in Tests

All integration tests inject the admin password using `@Value`:

```java
@Value("${lms.admin.password}")
String adminPassword;
```

This reads the resolved value from Spring's `Environment`, which respects the `${LMS_ADMIN_PASSWORD:ChangeMe123!}` default. The password is used to construct the login JSON body:

```java
String loginBody = "{\"username\":\"admin\",\"password\":\"" 
    + adminPassword.replace("\"", "\\\"") + "\"}";
```

The `.replace("\"", "\\\"")` handles passwords containing double quotes.

---

## 4. Test Patterns

### Pattern 1: Login Helper (`@BeforeEach`)

Every integration test class that needs authentication uses this pattern:

```java
@BeforeEach
void login() throws Exception {
    // Step 1: Get CSRF cookie
    MvcResult csrfResult = mvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();
    csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
    Assertions.assertNotNull(csrfCookie, "CSRF endpoint must issue XSRF-TOKEN cookie");

    // Step 2: Login with CSRF
    String loginBody = "{\"username\":\"admin\",\"password\":\"" 
        + adminPassword.replace("\"", "\\\"") + "\"}";
    MvcResult result = mvc.perform(
            post("/api/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
        .andExpect(status().isOk())
        .andReturn();

    // Step 3: Capture session
    adminSession = (MockHttpSession) result.getRequest().getSession(false);
}
```

**Why this works:** The `adminSession` and `csrfCookie` fields are reused across all test methods in the class.

### Pattern 2: Authenticated POST (`authPost`)

`CrudIntegrationTest` defines a helper to reduce boilerplate:

```java
private String authPost(String url, String content) throws Exception {
    return mvc.perform(
            post(url)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
        .andReturn()
        .getResponse()
        .getContentAsString();
}
```

**Usage:**

```java
String created = authPost("/api/magazines", 
    "{\"title\":\"National Geographic\",\"publisher\":\"NatGeo\"}");
long id = json.readTree(created).path("id").asLong();
```

### Pattern 3: Authenticated GET (`authGet`)

```java
private String authGet(String url) throws Exception {
    return mvc.perform(
            get(url)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andReturn()
        .getResponse()
        .getContentAsString();
}
```

**Usage:**

```java
String body = authGet("/api/dashboard");
Assertions.assertTrue(body.contains("totalBooks"));
```

### Pattern 4: CSRF Cookie + Header on Every Request

Every authenticated request must include all three components:

```java
.session(adminSession)          // JSESSIONID cookie
.cookie(csrfCookie)             // XSRF-TOKEN cookie
.header("X-XSRF-TOKEN", csrfCookie.getValue())  // CSRF header
```

**Missing any of these results in:**
- Missing session → 401 Unauthorized
- Missing cookie → CSRF validation failure
- Missing header → CSRF validation failure

### Pattern 5: Role-Based Testing Without Session

For testing role restrictions without going through the login flow:

```java
mvc.perform(
        get("/api/librarians")
            .with(SecurityMockMvcRequestPostProcessors
                .user("lib").roles("LIBRARIAN")))
    .andExpect(status().isForbidden())
    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
```

### Pattern 6: Ordered Test Execution

`CrudIntegrationTest` uses `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` with `@Order(n)` to ensure tests run in sequence:

```java
@Test
@Order(1)
void magazineCrud() throws Exception { ... }

@Test
@Order(2)
void newspaperCrud() throws Exception { ... }
```

**Important:** Ordered tests share state through the `@BeforeEach` session. If an earlier test fails, subsequent tests may also fail.

### Pattern 7: Entity ID Extraction

Extract the ID from a create response for subsequent operations:

```java
MvcResult book = mvc.perform(post("/api/books")...)
    .andExpect(status().isCreated())
    .andReturn();
long bookId = json.readTree(book.getResponse().getContentAsString()).path("id").asLong();
```

### Pattern 8: Error Response Assertions

Assert error code and message shape:

```java
.andExpect(status().isConflict())
.andExpect(jsonPath("$.code").value("CONFLICT"))
.andExpect(jsonPath("$.message").value("ISBN already exists."));
```

For validation errors:

```java
.andExpect(status().isBadRequest())
.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
.andExpect(jsonPath("$.fieldErrors", not(empty())));
```

### Template: New Integration Test Class

```java
package com.example.lms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
class NewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Value("${lms.admin.password}")
    String adminPassword;

    private MockHttpSession adminSession;
    private Cookie csrfCookie;

    @BeforeEach
    void login() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk()).andReturn();
        csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        Assertions.assertNotNull(csrfCookie);

        String body = "{\"username\":\"admin\",\"password\":\"" 
            + adminPassword.replace("\"", "\\\"") + "\"}";
        MvcResult result = mvc.perform(
                post("/api/auth/login")
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk()).andReturn();
        adminSession = (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void exampleTest() throws Exception {
        mvc.perform(get("/api/some-endpoint")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
            .andExpect(status().isOk());
    }
}
```

---

## 5. Known Test Issues

### Issue: `LMS_ADMIN_PASSWORD` Environment Variable Override

**Symptom:** Tests fail with 401 Unauthorized when `LMS_ADMIN_PASSWORD` is set in the shell environment to a different value than `ChangeMe123!`.

**Root cause:** The test `application.properties` uses:

```properties
lms.admin.password=${LMS_ADMIN_PASSWORD:ChangeMe123!}
```

Spring resolves `${LMS_ADMIN_PASSWORD:ChangeMe123!}` by checking the environment variable first. If `LMS_ADMIN_PASSWORD` is set in your shell (e.g., for production or dev), it overrides the default. The `@Value("${lms.admin.password}")` in tests then receives the env var's value, but the login request uses that value against the H2 database where the admin was seeded with the default `ChangeMe123!`.

**Fix:**

```bash
# Option 1: Unset the env var before running tests
unset LMS_ADMIN_PASSWORD

# Option 2: Explicitly set it to the test default
LMS_ADMIN_PASSWORD=ChangeMe123! ./mvnw test

# Option 3: Use the Maven profile that handles this
./mvnw test -Dspring.profiles.active=test
```

**Prevention:** When running tests locally, always verify the env var doesn't leak from your shell profile. Check with `echo $LMS_ADMIN_PASSWORD`.

---

## 6. Coverage Gaps

Sourced from [TECHNICAL_DEBT.md §3](TECHNICAL_DEBT.md) and [TESTING.md](TESTING.md):

### High Priority

| Gap | Untested Scenarios | Impact |
|-----|-------------------|--------|
| Student/Librarian update (PUT) | Full update flow, partial update, field validation | Missing from integration tests (addressed in `CrudIntegrationTest`) |
| Student/Librarian delete (DELETE) | Delete success, delete with dependencies | Missing from integration tests (addressed in `CrudIntegrationTest`) |
| STUDENT role access restrictions | STUDENT can only GET books, cannot mutate | Only 401/403 tested, not functional access |
| Dashboard values | `totalBooks`, `totalStudents` accuracy | Only response shape tested, not calculation correctness |
| Profile endpoint | `/api/auth/me` for different roles | Only tested via `BrowserCsrfFlowIntegrationTest` |

### Medium Priority

| Gap | Untested Scenarios | Impact |
|-----|-------------------|--------|
| Magazine/Newspaper service-layer edge cases | Borrow-history deletion guard, availability transitions, audit events | Only controller endpoints tested via `CrudIntegrationTest` |
| Dashboard/Analytics/Report services | Trend calculations, overdue summaries, CSV generation | Zero automated verification (TECHNICAL_DEBT.md §3.3) |
| AuthController edge cases | `/csrf` response shape, `/me` after session expiry, OAuth2 flow | Only login→me→logout tested |
| Unit tests for services | Isolated borrow availability, return idempotency | All tests are integration or repository level |

### Low Priority

| Gap | Untested Scenarios | Impact |
|-----|-------------------|--------|
| Frontend JavaScript | Rendering, form submission, navigation | No JS tests exist (TECHNICAL_DEBT.md §3.6) |
| End-to-end browser tests | Actual browser rendering, theme switching, modal interactions | Only MockMvc-based tests (TECHNICAL_DEBT.md §3.5) |
| Black-box test matrix | 14 manual test cases in `testing/black-box-test-cases.csv` | Status: `PENDING LOCAL MYSQL` |
| No JacocoCo coverage enforcement | No coverage thresholds, no reports | Coverage not measured (TECHNICAL_DEBT.md §2.7) |

---

## 7. How to Run

### Full Test Suite

```bash
# From the backend directory
cd backend
./mvnw clean test
```

**Expected output:**
```
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Single Test Class

```bash
./mvnw test -Dtest=LibraryManagementIntegrationTest
./mvnw test -Dtest=CrudIntegrationTest
./mvnw test -Dtest=BrowserCsrfFlowIntegrationTest
./mvnw test -Dtest=BookRepositoryTest
```

### Single Test Method

```bash
./mvnw test -Dtest=LibraryManagementIntegrationTest#fullBooksPeopleBorrowReturnAndLogoutFlow
```

### With Maven Verify (CI mode)

```bash
./mvnw clean verify
```

This compiles, runs tests, and performs any additional Maven checks.

### With Explicit Password (bypass env var issue)

```bash
LMS_ADMIN_PASSWORD=ChangeMe123! ./mvnw test
```

### With H2 Dev Profile (manual testing)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

Then access H2 console at `http://localhost:8080/h2-console`.

### Spotless Format Check

```bash
./mvnw spotless:check    # Verify formatting
./mvnw spotless:apply    # Auto-fix formatting
```

**Note:** Spotless uses Google Java Format. Tests must conform to this style.

---

## 8. CI Integration

### GitHub Actions

The project uses GitHub Actions for continuous integration. The workflow runs on push to `main` and on pull requests.

**Workflow file:** `.github/workflows/ci.yml`

```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      
      - name: Build and test
        working-directory: backend
        run: mvn clean verify
      
      - name: Check formatting
        working-directory: backend
        run: mvn spotless:check
```

### What CI Does

1. **Checkout** the repository.
2. **Set up JDK 21** with Maven caching.
3. **Run `mvn clean verify`** — compiles, runs all tests, fails on any test failure.
4. **Run `mvn spotless:check`** — fails if code doesn't conform to Google Java Format.

### CI Environment

- **OS:** Ubuntu latest
- **Java:** JDK 21 (Temurin)
- **Database:** H2 in-memory (no external database required)
- **No `LMS_ADMIN_PASSWORD` env var** — uses default `ChangeMe123!`

### Why Tests Pass in CI but May Fail Locally

If tests fail locally but pass in CI, check:

1. `LMS_ADMIN_PASSWORD` is set in your shell — unset it or override.
2. Port conflicts — MockMvc doesn't use a real port, so this shouldn't matter.
3. Maven wrapper version — ensure `./mvnw` is executable and up to date.
4. JDK version — CI uses JDK 21; ensure local matches.

---

## Related Documentation

- [TESTING.md](TESTING.md) — Test inventory and running tests
- [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md) — §3 Testing Debt for gaps
- [ARCHITECTURE.md](ARCHITECTURE.md) — System context and layered architecture
- [SECURITY.md](SECURITY.md) — Auth flow and CSRF design
- [API.md](API.md) — Endpoint reference
