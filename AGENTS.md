# AGENTS.md — Libris (Library Management System)

Spring Boot 3.5 (Java 21) REST API + vanilla HTML/CSS/JS frontend served as
static resources from Spring Boot. MySQL (prod) / H2 in-memory (tests & dev).

**Current version:** `v1.1.0` — Libris branding, Swagger UI, book categories, circulation due dates, Oracle Cloud Infrastructure deployment target.

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
| `LMS_DB_ROOT_PASSWORD` | OCI stack only | MySQL root password; used solely for container bootstrap and the mysql healthcheck. The app never connects as root |
| `LMS_DB_PASSWORD` | prod/dev MySQL, Docker, OCI | Password for the application account (`libris` on the OCI stack) |
| `LMS_ADMIN_PASSWORD` | Prod / Docker / OCI | `AdminSeeder` rejects blank/null. **The `prod` profile has no default**, so an unset value fails startup; always set it in production |
| `LMS_DB_URL` | OCI stack | Full JDBC URL. Supplied by `docker-compose.yml`; the `prod` profile reads it with no fallback |

Tests supply their own `LMS_ADMIN_PASSWORD` via
`backend/src/test/resources/application.properties`, so `./mvnw test` needs no env.
It must not be the literal `ChangeMe123!`, which `AdminSeeder` rejects.

Main `application.properties` also defaults `lms.admin.password` to `ChangeMe123!`
if the env var is absent. **Always override this in production.**

Profiles (`application.properties`):
- `h2` — dev: in-memory H2, `ddl-auto=create-drop`, Flyway **disabled**.
- `docker` / (default) — MySQL, Flyway migrations (`db/migration/V1__baseline.sql`).
- `oauth` — opt-in Google OIDC; only enables login for existing STUDENT accounts whose profile email matches Google's claim.
- `prod` — production invariants restated explicitly: Secure/SameSite=Lax session
  cookies, `forward-headers-strategy=framework`, actuator narrowed to `health`
  only, datasource read from `LMS_DB_URL` with no `MYSQL_URL` fallback, and
  `spring.flyway.baseline-on-migrate=false`. Swagger/OpenAPI are already off in
  the base config; this profile repeats the setting rather than relying on it.

Main `application.properties` has `spring.jpa.hibernate.ddl-auto=none` +
Flyway **enabled**; never rely on `ddl-auto` for MySQL schema changes —
add a `V{version}__{name}.sql` migration under `src/main/resources/db/migration`
and bump the version. Current migrations:
- `V1__baseline.sql` — full baseline schema
- `V2__student_profile_email_unique.sql` — unique email constraint
- `V3__borrow_record_due_date.sql` — `due_date DATE` column on `borrow_records`
- `V4__book_category.sql` — `category VARCHAR(100)` on `books`
- `V5__borrow_records_indexes.sql` — indexes on `borrow_records.return_date` and `due_date`

The H2/dev/test profiles disable Flyway and use `create-drop`, so migration files are **not** applied to them.

## Run locally

```bash
# H2 (no MySQL, no env var needed — defaults to ChangeMe123!)
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# Or with a custom admin password:
export LMS_ADMIN_PASSWORD=ChangeMe123!
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# OCI production stack (repo root; needs the three secrets in ./.env)
cp .env.example .env && $EDITOR .env
docker compose up -d --build
```

App runs at <http://localhost:8080>; Swagger UI at
`/swagger-ui.html` (public when SpringDoc is enabled, disabled by `prod` profile); actuator at `/actuator` (public health endpoint).

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
  `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/actuator/**`,
  `/api/auth/login`, `/api/auth/csrf`, `/api/auth/register`.
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
- The **root** `Dockerfile` and `docker-compose.yml` are the single supported
  deployment path: a multi-stage build (Java 21) onto a non-root `eclipse-temurin:21-jre-alpine`
  runtime, plus MySQL 8.4 on a `mysql-data` volume. There is no `backend/Dockerfile`
  and no `backend/docker-compose.yml`; the duplicate pair was removed so there is one
  image definition. `railway.json` and `render.yaml` were removed with it — do not
  reintroduce a second Dockerfile or a platform-specific deploy file.
- The container runs as user `app`, honours `JAVA_OPTS` (the ENTRYPOINT is shell-form
  and `exec`s java, so the variable is actually expanded), and carries a HEALTHCHECK
  with a 180s start-period for cold starts.
- In the OCI stack the app is published on `127.0.0.1:8080` only; TLS terminates at a
  host reverse proxy. **3306 and 8080 must stay closed** in the OCI security list and
  the instance firewall.
- Logical backups: `scripts/backup-mysql.sh` (writes to the git-ignored `backups/`,
  keeps the 14 newest). Schedule it from host cron and copy dumps off the instance.
- Dynamic port: `server.port=${PORT:8080}` — retained so a platform-assigned port still
  works; the OCI stack pins 8080.

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

### Phase 5 demand forecasting (Python, not part of Maven)

Standalone `unittest` suites under `scripts/dev-seed/`, run by the separate
`phase5` job in `.github/workflows/ci.yml` (Maven CI does not cover them).
They import the shipped implementation from
`scripts/dev-seed/phase5_forecasting.py` — do not reimplement forecasting logic
inside tests. The shared module is import-safe, so the tests need neither MySQL
nor any third-party package:

```bash
python3 -m unittest discover -s scripts/dev-seed -p "test_phase5*.py"   # 120 tests
```

**120 tests** across `test_phase5a.py` (month arithmetic, record classification,
aggregation, grid densification, series-range guard, seasonal lookup, metrics) and
`test_phase5b.py` (moving-average window, static-holdout vs rolling-origin
anchoring, horizon limits, fallback behaviour, UNCATEGORIZED series, leakage,
report contents).

The two entry points need a populated database (`LMS_DB_NAME` defaults to
`libris_ml_dev`, which is **not** the default of `seed_generator.py`
(`librarydb`) — set it explicitly):

```bash
export LMS_DB_PASSWORD=... LMS_DB_NAME=libris_ml_dev
python3 scripts/dev-seed/phase5a_demand_aggregation.py
python3 scripts/dev-seed/phase5b_calendar_baselines.py
```

Evaluation protocol: static holdout (origin 2025-12, horizon 2026-01..2026-07,
182 forecast points, 26 series) with rolling origin reported alongside. The
horizon is clipped to the last month the data actually covers — `seed_generator.py`
stops at `SIMULATED_TODAY` (2026-07-15), so 2026-08 is unobserved and must not be
densified into a scored zero row. See `docs/CURRENT_STATE.md` §7 for the numbers.

## Docs worth referencing

`docs/ARCHITECTURE.md`, `docs/API.md`, `docs/SECURITY.md`, `docs/SETUP.md`,
`docs/TESTING.md`, `docs/DATABASE.md`, `docs/FRONTEND.md`, `docs/DEPLOYMENT.md`,
`docs/CURRENT_STATE.md`, `scripts/dev-seed/MODEL_SPECIFICATION.md`,
`scripts/dev-seed/phase5_forecasting.py` (Phase 5 evaluation protocols and
baseline definitions),
`scripts/dev-realdata/ML_POPULATION_DEFINITION.md` (Phase 5 batch; not independently validated),
`scripts/dev-realdata/readiness_monitor.py`.

