# CURRENT_STATE.md

> Engineering knowledge base — Libris Library Management System
> Last updated: 2026-09-13

---

## 1. Release

| Field | Value |
|-------|-------|
| Version | `v1.1.0` |
| Maven artifactId | `libris` |
| Spring application name | `libris` |
| Branch | `main` |
| Live deployment | Railway (Docker + MySQL) at `https://libris-lms.up.railway.app` |

---

## 2. Test Suite

**35 passing tests** across 6 test classes:

| Test File | Type | Methods | Coverage |
|-----------|------|---------|----------|
| `LibraryManagementIntegrationTest` | Integration (MockMvc) | 11 | Login, CRUD, borrow/return, ISBN conflict, 401/403, self-registration, registration validation, register CSRF, profile, Swagger public access, book category, `/actuator/health` |
| `CrudIntegrationTest` | Integration (MockMvc) | 8 | Magazine/Newspaper CRUD, Student/Librarian update+delete, Dashboard counts, Audit log, duplicate username, duplicate email |
| `HardeningTest` | MockMvc + Unit | 13 | Student deletion with borrow history (409), student deletion without history (success), OverdueCalculator effectiveDueDate/isOverdue/daysOverdue unit tests |
| `BrowserCsrfFlowIntegrationTest` | Integration (real CSRF flow) | 1 | CSRF bootstrap, login with CSRF header, session reuse, authenticated `/me`, logout, post-logout rejection |
| `ArchitectureTest` | ArchUnit | 1 | Controllers must not depend on repositories |
| `BookRepositoryTest` | Repository | 1 | Audit timestamp population, ISBN uniqueness constraint |

Tests use H2 in MySQL compatibility mode (`create-drop` schema strategy, Flyway disabled).

Note: `TestBCrypt.java` exists as a standalone main class for manual BCrypt verification; it is not a JUnit test and is not counted by Maven.

---

## 3. Backend

| Component | Detail |
|-----------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Security | Spring Security 6.5 (session-based, BCrypt, CSRF via `SpaCsrfTokenRequestHandler`) |
| Data | Spring Data JPA |
| Database | MySQL 8 (production), H2 in MySQL-compatibility mode (tests/dev) |
| Validation | `spring-boot-starter-validation` |
| API Docs | SpringDoc OpenAPI 2.8.6 (enabled by default; `prod` profile disables) |
| OAuth2 | Google OAuth2 client (opt-in via `oauth` profile) |
| Formatting | Spotless 2.44.3 — Google Java Format 1.25.2 |
| Build | Maven + Maven Wrapper |
| Logging | Structured JSON via `logstash-logback-encoder` 8.0 |
| Caching | Spring Cache (simple type) |

### 3.1 Codebase Metrics

| Category | Count |
|----------|-------|
| REST controllers | 14 |
| Transactional services | 11 |
| JPA repositories | 8 |
| Entities | 8 (+ 1 superclass `AuditableEntity`, 3 enums) |
| DTO source files | 27 |
| Security classes | 6 |

### 3.2 Schema Management

- **Production:** `spring.jpa.hibernate.ddl-auto=none` + Flyway enabled
- **Flyway migrations:** V1 (baseline), V2 (student email unique), V3 (borrow record due_date), V4 (book category)
- **Tests/Dev (H2):** `ddl-auto=create-drop`, Flyway disabled
- **AdminSeeder:** Creates `admin` account on startup; reads `lms.admin.password` from config; main config defaults to `ChangeMe123!`; throws `IllegalStateException` if resolved password is blank; updates existing admin password if config differs

---

## 4. Frontend

| Aspect | Detail |
|--------|--------|
| Architecture | Multi-page application (MPA) served by Spring Boot |
| Static files | 67 files in `backend/src/main/resources/static/` |
| JS | Vanilla JS with ES modules |
| HTTP | Fetch API |
| Themes | Dual-theme: dark cool blue-slate canvas / verdigris light — shared emerald/teal accent |

---

## 5. Infrastructure

| Component | Detail |
|-----------|--------|
| Containerization | Docker Compose (MySQL + app, optional phpMyAdmin via `--profile dev`) |
| CI | GitHub Actions — `mvn spotless:check` then `mvn clean verify` |
| Monitoring | Spring Boot Actuator — `/actuator/health`, `/actuator/info`, `/actuator/metrics` |
| Logging | Structured JSON via `logstash-logback-encoder` with `traceId`/`spanId` MDC |
| Coverage | JaCoCo enforces ≥ 70% line coverage |
| Secrets | Environment variables (`.env` file for Docker, shell exports for local dev) |

---

## 6. Key Features

- **Session-based authentication** with Spring Security and BCrypt password hashing
- **Role-based authorization** — ADMIN, LIBRARIAN, STUDENT enforced via URL-pattern matching in `SecurityConfig`
- **Books, magazines, newspapers CRUD** with searchable catalogues and book categories
- **Student and librarian management** with linked account creation, profile maintenance, and update/delete
- **Borrow/return workflow** with availability protection, ISBN uniqueness enforcement, due dates, and preserved history
- **Student deletion hardening** — students with borrow history return HTTP 409 Conflict
- **Audit logging** — server-side event tracking with `created_at` / `updated_at` timestamps
- **Dashboard statistics** — aggregate counts of students, librarians, books, borrowed, and available
- **Analytics and reports** endpoints with monthly trends, top books, top readers, overdue summaries
- **Server-side validation** with field-level frontend feedback and uniform `ApiErrorResponse` JSON
- **OAuth2 login** via Google (opt-in)
- **Swagger UI** at `/swagger-ui.html` — public when SpringDoc enabled; disabled by `prod` profile

---

## 7. ML / Data Readiness Boundary

- **Synthetic ML pipeline** (`scripts/dev-seed/`): Frozen feasibility benchmark, not deployed to application. Verified and reproducible with seed 42: logistic regression ROC-AUC `0.735`, PR-AUC `0.617`, F1 `0.602` at threshold `0.25`, temporal F1 CV `0.067`.
- **Real-data readiness** (`scripts/dev-realdata/`): `readiness_monitor.py` is a legitimate script that connects to MySQL and executes real queries. Its month-bucketing `DATE_FORMAT` literal bug has been fixed.
- **Phase 5 artifacts**: `PHASE5_PRODUCTION_READINESS.md` and `phase5a_readiness.py` were removed after an independent audit found fabricated/static claims; they must not be treated as evidence.
- **Real-data ML evaluation**: Blocked until the readiness monitor passes against a populated real database and a source-verified readiness assessment is created.

---

## 8. Recent Changes (Phase 6 Session)

### Phase 6 — Hardening & Repository Cleanup
- `StudentService.delete()` now checks `borrowRecords.existsByStudentId(id)` — returns 409 Conflict for students with borrow history
- `BorrowRecordRepository.existsByStudentId(Long studentId)` added
- `OverdueCalculator` utility: `effectiveDueDate()`, `isOverdue()`, `daysOverdue()`
- `HardeningTest` — 2 integration tests + 11 unit tests for OverdueCalculator
- `ArchitectureTest` — ArchUnit rule: controllers must not depend on repositories
- Spotless, JaCoCo, 35 tests all passing

### Phase 6.1 — Documentation Consistency
- Verified and corrected README, CURRENT_STATE, SETUP, DEPLOYMENT, AGENTS against source
- Identified and removed `PHASE5_PRODUCTION_READINESS.md` after independent audit found substantially fabricated claims
- Identified and removed `phase5a_readiness.py` after verification showed it generated static text without database access

---

## 9. Reference Files

| File | Purpose |
|------|---------|
| `backend/pom.xml` | Build config, dependencies, Spotless, JaCoCo, SpringDoc |
| `backend/src/main/resources/application.properties` | Runtime config, Flyway, Actuator, OAuth2, logging |
| `backend/src/main/resources/application-prod.properties` | Disables SpringDoc in production |
| `backend/src/main/resources/logback-spring.xml` | Structured JSON logging layout |
| `backend/src/test/resources/application.properties` | Test H2 config, admin default |
| `backend/src/test/java/com/example/lms/HardeningTest.java` | Student deletion + OverdueCalculator tests |
| `backend/src/test/java/com/example/lms/ArchitectureTest.java` | ArchUnit dependency rule |
| `.github/workflows/ci.yml` | CI pipeline (Spotless + Maven verify) |
| `scripts/dev-seed/MODEL_SPECIFICATION.md` | Frozen synthetic ML benchmark spec |
| `scripts/dev-realdata/ML_POPULATION_DEFINITION.md` | Real-data ML population and PIT rules (Phase 5 batch; not independently validated) |
| `scripts/dev-realdata/readiness_monitor.py` | Real-data readiness monitor (month-bucketing fix applied) |
| `README.md` | Project overview and documentation index |
