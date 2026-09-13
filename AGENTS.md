# AGENTS.md — Libris (Library Management System)

Spring Boot 3.5 (Java 21) REST API + vanilla HTML/CSS/JS frontend served as
static resources from Spring Boot. MySQL (prod) / H2 in-memory (tests & dev).

**Current version:** `v1.1.0` — Libris branding, Swagger UI, book categories, circulation due dates, Railway live deployment.

## Build & test commands

All Maven commands run against `backend/`. The repo-root `./mvnw` wrapper
delegates to `backend/mvnw`, so both forms work:

```bash
./mvnw clean test            # run from repo root or backend/
./mvnw test -Dtest=BookRepositoryTest
./mvnw spotless:check        # CI formatting gate (Google Java Format)
./mvnw spotless:apply        # auto-format before committing
./mvnw clean verify          # CI-equivalent: spotless + tests + JaCoCo gate
./mvnw package -DskipTests   # build runnable jar
```

CI (`.github/workflows/ci.yml`) runs `mvn spotless:check` then
`mvn clean verify` with `working-directory: backend`.

## Required environment variables

| Variable | Required | Notes |
|----------|----------|-------|
| `LMS_DB_PASSWORD` | prod/dev MySQL, Docker | MySQL root password |
| `LMS_ADMIN_PASSWORD` | first startup | `AdminSeeder` creates `admin`; blank → defaults to `ChangeMe123!` |

Tests default `LMS_ADMIN_PASSWORD` to `ChangeMe123!` via
`backend/src/test/resources/application.properties`, so `./mvnw test` needs no env.

Main `application.properties` also defaults `lms.admin.password` to `ChangeMe123!`
if the env var is absent. **Always override this in production.**

Profiles (`application.properties`):
- `h2` — dev: in-memory H2, `ddl-auto=create-drop`, Flyway **disabled**.
- `docker` / (default) — MySQL, Flyway migrations (`db/migration/V1__baseline.sql`).
- `oauth` — opt-in Google OIDC; only enables login for existing STUDENT accounts whose profile email matches Google's claim.
- `prod` — disables Swagger/OpenAPI.

Main `application.properties` has `spring.jpa.hibernate.ddl-auto=none` +
Flyway **enabled**; never rely on `ddl-auto` for MySQL schema changes —
add a `V{version}__{name}.sql` migration under `src/main/resources/db/migration`
and bump the version. Current migrations:
- `V1__baseline.sql` — full baseline schema
- `V2__student_profile_email_unique.sql` — unique email constraint
- `V3__borrow_record_due_date.sql` — `due_date DATE` column on `borrow_records`
- `V4__book_category.sql` — `category VARCHAR(100)` on `books`

The H2/dev/test profiles disable Flyway and use `create-drop`, so migration files are **not** applied to them.

## Run locally

```bash
# H2 (no MySQL, no env var needed — defaults to ChangeMe123!)
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# Or with a custom admin password:
export LMS_ADMIN_PASSWORD=ChangeMe123!
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# Docker (needs LMS_DB_PASSWORD + LMS_ADMIN_PASSWORD in backend/.env)
cd backend && cp .env.example .env && docker compose up --build
# Dev profile adds phpMyAdmin on :8081:  docker compose --profile dev up --build
```

App runs at <http://localhost:8080>; Swagger UI at
`/swagger-ui.html` (public, no login needed); actuator at `/actuator` (public health endpoint).

## Architecture & conventions

```text
Browser → Fetch API → REST Controllers (/api/**) → @Transactional Services → Spring Data JPA → MySQL
```
- Single Maven module under `backend/`. Frontend lives in `backend/src/main/resources/static/`
  (static HTML pages, each with its own JS ES module — a multi-page app).
- Maven `artifactId` is `libris`, `spring.application.name=libris`.
- Authorization is enforced at the **URL-pattern** level in `SecurityConfig`
  (no method-level `@PreAuthorize`). Roles: `ADMIN`, `LIBRARIAN`, `STUDENT`.
- Public paths (no auth needed): `/login.html`, `/register.html`, `/css/**`, `/js/**`,
  `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/actuator/**`.
- CSRF: frontend calls `GET /api/auth/csrf` to set `XSRF-TOKEN` cookie, then
  sends it back as `X-XSRF-TOKEN` header; sessions use `JSESSIONID`. The
  `BrowserCsrfFlowIntegrationTest` mirrors this real flow.
- `ApiErrorResponse` is the uniform JSON error shape — controllers throw
  `ResponseStatusException` / custom exceptions, not raw messages.
- Auditing (`@EnableJpaAuditing`) auto-populates `createdAt`/`updatedAt` via
  a mapped-superclass entity base; `ddl-auto=none` means don't expect Hibernate
  to create/alter tables.
- Cache (`@EnableCaching`, simple type) is enabled; invalidate affected cache
  keys when mutating cached data.
- `lms.admin.username=admin` is hardcoded in `application.properties`.
- Spotless Google format is enforced — running `mvn spotless:check` must pass
  before CI. JaCoCo enforces **70% line coverage** on `verify`; adding
  untested code paths breaks CI.
- Mockito 5 needs an inline mock-maker agent; `pom.xml` configures the
  surefire `argLine` with the mockito-core jar. Don't override `argLine`
  without preserving `@{argLine}` (JaCoCo agent) and the mockito agent.
- Root `Dockerfile` is multi-stage (build → runtime); uses `java -jar /app/app.jar`.
  Railway auto-detects via `railway.json`; `render.yaml` exists for Render.
- Dynamic port: `server.port=${PORT:8080}` — Railway/Render auto-set `PORT`.

## Test layout

H2 + `create-drop` + Flyway disabled — tests run fully in isolation, no MySQL.
**35 tests** across 6 test classes, all passing.

| Class | Scope | Tests | Purpose |
|-------|-------|-------|---------|
| `LibraryManagementIntegrationTest` | MockMvc | 11 | login, CRUD, borrow/return, ISBN conflict, 401/403, logout, Swagger public access, book category, `/actuator/health` |
| `CrudIntegrationTest` | MockMvc | 8 | magazine/newspaper CRUD, student/librarian PUT+DELETE, dashboard, audit, duplicate username |
| `HardeningTest` | MockMvc + Unit | 13 | student deletion with borrow history (409), OverdueCalculator unit tests |
| `BrowserCsrfFlowIntegrationTest` | MockMvc | 1 | real CSRF cookie/header bootstrap→login→logout flow |
| `ArchitectureTest` | ArchUnit | 1 | ArchUnit dependency validation |
| `BookRepositoryTest` | Repository | 1 | audit timestamps + ISBN uniqueness at DB level |

Run one: `./mvnw test -Dtest=LibraryManagementIntegrationTest`.

## Docs worth referencing

`docs/ARCHITECTURE.md`, `docs/API.md`, `docs/SECURITY.md`, `docs/SETUP.md`,
`docs/TESTING.md`, `docs/DATABASE.md`, `docs/FRONTEND.md`, `docs/DEPLOYMENT.md`.

