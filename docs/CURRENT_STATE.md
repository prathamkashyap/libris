# CURRENT_STATE.md

> Engineering knowledge base — Library Management System (Project A)
> Last updated: 2026-08-01

---

## 1. Release

| Field | Value |
|-------|-------|
| Version | `0.0.1-SNAPSHOT` (unreleased) |
| Branch | `feature/v1.1-analytics-reports-docker` |
| Total commits | 31 |
| Session commits | 8 (Phase 0–6) |

---

## 2. Test Suite

**13 passing tests** across 4 test classes:

| Test File | Type | Methods | Coverage |
|-----------|------|---------|----------|
| `CrudIntegrationTest` | Integration (MockMvc) | 7 | Magazine/Newspaper CRUD, Student/Librarian update+delete, Dashboard, Audit, duplicate username |
| `LibraryManagementIntegrationTest` | Integration (MockMvc) | 4 | Login, full CRUD + borrow/return, validation, ISBN conflicts, 401/403 |
| `BrowserCsrfFlowIntegrationTest` | Integration (real CSRF flow) | 1 | CSRF bootstrap → login → session reuse → logout → post-logout rejection |
| `BookRepositoryTest` | Repository | 1 | Audit timestamp population, ISBN uniqueness constraint |

Tests use H2 in MySQL compatibility mode (`create-drop` schema strategy).

---

## 3. Backend

| Component | Detail |
|-----------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Security | Spring Security 6.5 (session-based, BCrypt) |
| Data | Spring Data JPA |
| Database | MySQL 8 (production), H2 in MySQL-compatibility mode (tests) |
| Validation | `spring-boot-starter-validation` |
| API Docs | SpringDoc OpenAPI 2.8.6 (Swagger UI) |
| OAuth2 | Google OAuth2 client |
| Formatting | Spotless 2.44.3 — Google Java Format 1.25.2 |
| Build | Maven + Maven Wrapper |

### 3.1 Codebase Metrics

| Category | Count |
|----------|-------|
| REST controllers | 14 |
| Transactional services | 11 |
| JPA repositories | 8 |
| Entities | 8 (+ 1 superclass `AuditableEntity`, 3 enums) |
| DTOs | 26 request/response records |
| Security classes | 5 |

### 3.2 Key Dependencies

```
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-validation
spring-boot-starter-security
spring-boot-starter-oauth2-client
spring-boot-starter-actuator
logstash-logback-encoder 8.0
mysql-connector-j (runtime)
h2 (runtime, test)
springdoc-openapi-starter-webmvc-ui 2.8.6
spring-boot-starter-test
spring-security-test
```

---

## 4. Frontend

| Aspect | Detail |
|--------|--------|
| Architecture | Multi-page application (MPA) served by Spring Boot |
| Files | 57 HTML/JS/CSS files in `static/` |
| JS | Vanilla JS with ES modules |
| HTTP | Fetch API |
| Themes | Dual-theme: dark blue + rosy pink |
| Transitions | CSS animations, no flash on page load |
| Decorative | Rose petals (pink mode), cosmic particles (blue mode) |

---

## 5. Infrastructure

| Component | Detail |
|-----------|--------|
| Containerization | Docker Compose (MySQL + app, optional phpMyAdmin via `--profile dev`) |
| CI | GitHub Actions — `mvn spotless:check` then `mvn clean verify` |
| Monitoring | Spring Boot Actuator — `/actuator/health`, `/actuator/info`, `/actuator/metrics` |
| Logging | Structured JSON via `logstash-logback-encoder` with `traceId`/`spanId` MDC |
| Secrets | Environment variables (`.env` file for Docker, shell exports for local dev) |

---

## 6. Key Features

- **Session-based authentication** with Spring Security and BCrypt password hashing
- **Role-based authorization** — ADMIN, LIBRARIAN, STUDENT enforced via URL-pattern matching in `SecurityConfig`
- **Books, magazines, newspapers CRUD** with searchable catalogues
- **Student and librarian management** with linked account creation and profile maintenance
- **Borrow/return workflow** with availability protection, ISBN uniqueness enforcement, and preserved history
- **Audit logging** — server-side event tracking with `created_at` / `updated_at` timestamps
- **Dashboard statistics** — aggregate counts of students, librarians, books, borrowed, and available
- **Analytics and reports** endpoints with monthly trends, top books, top readers, overdue summaries
- **Server-side validation** with field-level frontend feedback and uniform `ApiErrorResponse` JSON
- **OAuth2 login** via Google
- **Swagger UI** at `/swagger-ui/index.html` (restricted to ADMIN/LIBRARIAN in production)

---

## 7. Recent Changes (Session: Phase 0–6)

### Phase 0 — Spotless + Cleanup
- Added Spotless Maven plugin (Google Java Format 1.25.2) and reformatted 88 Java files
- Fixed `AdminSeeder` to use SLF4J logger instead of `System.out`
- Fixed test infrastructure (H2 compatibility, schema strategy)
- Removed dead CSS from `login.html`
- Corrected docs (ARCHITECTURE.md, TESTING.md, README.md)
- Added CI formatting gate: `mvn spotless:check` step in `.github/workflows/ci.yml`

### Phase 0.5 — Account Orphaning Fix
- `StudentService.delete` now removes the associated `Account` before deleting the profile
- `LibrarianService.delete` now removes the associated `Account` before deleting the profile

### Phase 2 — N+1 Query Fixes
- `BorrowRecordRepository`: `@EntityGraph` / `JOIN FETCH` to eagerly load `Book`, `StudentProfile`
- `StudentProfileRepository`: `JOIN FETCH` on `Account` association
- `LibrarianProfileRepository`: `JOIN FETCH` on `Account` association

### Phase 3A — Flyway + Swagger Restriction (pre-session)
- Flyway migrations in place
- Swagger UI restricted to ADMIN/LIBRARIAN roles

### Phase 3B — Observability
- Spring Boot Actuator enabled with health/info/metrics endpoints
- Structured JSON logging via `logstash-logback-encoder` 8.0
- Custom `logback-spring.xml` with MDC traceId/spanId and `app` custom field

### Phase 4 — Integration Tests
- 7 new integration test methods in `CrudIntegrationTest`:
  - Magazine CRUD (create, list, update, delete)
  - Newspaper CRUD (create, list, update, delete)
  - Student update and delete with account cleanup
  - Librarian update and delete with account cleanup
  - Dashboard endpoint verification
  - Audit log endpoint verification
  - Duplicate username rejection

### Phase 6 — README Update
- README updated with Actuator, N+1 fixes, Spotless, new tests, and JSON logging sections

---

## 8. Reference Files

| File | Purpose |
|------|---------|
| `backend/pom.xml` | Build config, dependencies, Spotless plugin |
| `backend/src/main/resources/application.properties` | Runtime config, Actuator, OAuth2, logging |
| `backend/src/main/resources/logback-spring.xml` | Structured JSON logging layout |
| `backend/src/test/java/com/example/lms/CrudIntegrationTest.java` | 7 new integration tests |
| `backend/src/test/java/com/example/lms/LibraryManagementIntegrationTest.java` | 4 core integration tests |
| `backend/src/test/java/com/example/lms/BrowserCsrfFlowIntegrationTest.java` | CSRF flow test |
| `backend/src/test/java/com/example/lms/BookRepositoryTest.java` | Repository-level test |
| `.github/workflows/ci.yml` | CI pipeline (Spotless + Maven verify) |
| `README.md` | Project overview and documentation index |
