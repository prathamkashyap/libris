# CURRENT_STATE.md 📊

> **Engineering knowledge base** — Libris Library Management System

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

**175 executed tests** across 17 test classes (175 `@Test` methods declared, none skipped):

| Test File | Type | Methods | Coverage |
|-----------|------|---------|----------|
| `LibraryManagementIntegrationTest` | Integration (MockMvc) | 16 | Login, CRUD, borrow/return, ISBN conflict, 401/403, self-registration, registration validation, register CSRF, profile, Swagger public access, book category, `/actuator/health`, BorrowRecord listing, status filtering, search |
| `CrudIntegrationTest` | Integration (MockMvc) | 8 | Magazine/Newspaper CRUD, Student/Librarian update+delete, Dashboard counts, Audit log, duplicate username, duplicate email |
| `HardeningTest` | MockMvc + Unit | 13 | Student deletion with borrow history (409), student deletion without history (success), OverdueCalculator effectiveDueDate/isOverdue/daysOverdue unit tests |
| `BrowserCsrfFlowIntegrationTest` | Integration (real CSRF flow) | 1 | CSRF bootstrap, login with CSRF header, session reuse, authenticated `/me`, logout, post-logout rejection |
| `ArchitectureTest` | ArchUnit | 1 | Controllers must not depend on repositories |
| `BookRepositoryTest` | Repository | 1 | Audit timestamp population, ISBN uniqueness constraint |
| `BorrowConcurrencyTest` | Integration (SpringBootTest) | 9 | Concurrent borrow/return race conditions, lock contention, cache eviction |
| `BorrowRecordServiceTest` | Unit (Mockito) | 34 | Borrow/return service logic, audit event publishing, pagination, defensive guards |
| `MagazineServiceTest` | Unit (Mockito) | 5 | Magazine CRUD audit events, borrow-history deletion guard |
| `NewspaperServiceTest` | Unit (Mockito) | 5 | Newspaper CRUD audit events, borrow-history deletion guard |
| `AnalyticsServiceTest` | Unit (Mockito) | 16 | Dashboard analytics, overdue summary, top books/readers pagination |
| `ReportServiceTest` | Unit (Mockito) | 29 | CSV generation, pagination boundaries, defensive null handling |
| `AuthServiceTest` | Unit (Mockito) | 1 | Failed login audit event publishing, audit failure handling |
| `SecurityHardeningTest` | Integration (MockMvc) | 23 | Failed login audit persistence, Swagger UI restriction, catch-all 500 handler |
| `ActiveOverdueQueryTest` | DataJPA | 5 | SQL-level overdue query semantics, boundary conditions |
| `OverdueReportQueryTest` | DataJPA | 7 | Overdue report pagination, keyset pagination, semantic equivalence |
| `BorrowRecordsIndexTest` | Integration (Flyway-enabled) | 1 | V5 Flyway migration verification, index existence |

Tests use H2 in MySQL compatibility mode (`create-drop` schema strategy, Flyway disabled for integration tests). `BorrowRecordsIndexTest` uses a dedicated H2 database with Flyway enabled to verify V5 migration application.

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
- **Flyway migrations:** V1 (baseline), V2 (student email unique), V3 (borrow record due_date), V4 (book category), V5 (borrow_records indexes: return_date, due_date)
- **Tests/Dev (H2):** `ddl-auto=create-drop`, Flyway disabled (except `BorrowRecordsIndexTest` which uses Flyway-enabled H2 for migration verification)
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
- **Audit logging** — server-side event tracking with `created_at` / `updated_at` timestamps; Magazine/Newspaper CRUD audit events; FAILED_LOGIN audit events
- **Dashboard statistics** — aggregate counts of students, librarians, books, borrowed, and available
- **Analytics and reports** endpoints with monthly trends, top books, top readers, overdue summaries (SQL-level optimized)
- **Operational logging** — authentication success/failure, access denied, validation errors, lock contention
- **Concurrency hardening** — pessimistic locking for borrow/return race protection; cache eviction on circulation changes
- **Database indexes** — V5 adds return_date and due_date indexes for overdue/active-loan query patterns
- **Overdue query optimization** — SQL-level predicates replace Java filtering; keyset pagination for reports
- **Server-side validation** with field-level frontend feedback and uniform `ApiErrorResponse` JSON
- **OAuth2 login** via Google (opt-in)
- **Swagger UI** at `/swagger-ui.html` — disabled by default; enabled via profile

---

## 7. ML / Data Readiness Boundary

- **Synthetic ML pipeline** (`scripts/dev-seed/`): Frozen feasibility benchmark, not deployed to application. Verified and reproducible with seed 42: logistic regression ROC-AUC `0.735`, PR-AUC `0.617`, F1 `0.602` at threshold `0.25`, temporal F1 CV `0.067`. Calibration deferred after Platt scaling degraded Brier score (0.1991→0.2307) and log loss (0.5797→0.6539) on synthetic validation data.
- **Phase 5 shared forecasting core** (`scripts/dev-seed/phase5_forecasting.py`): Single source of truth for month arithmetic, record classification, the monthly demand grid, the baseline methods, the evaluation protocols, and MAE/RMSE. Phase 5A, Phase 5B and both Phase 5 test modules import it; no forecasting logic is reimplemented in tests, so a stale copy can no longer pass while the shipped code is broken. Import-safe (no database driver at import time), so the unit tests need no MySQL and no third-party packages.
- **Phase 5A demand forecasting** (`scripts/dev-seed/phase5a_demand_aggregation.py`): Monthly demand aggregation with a seasonal-naive baseline. Historical lookup is keyed by `item_type|category|month` (a series-only key let training months overwrite each other). Static holdout: train 2023-01 through 2025-12 (origin 2025-12), forecast horizon 2026-01 through 2026-07, **182 forecast points** (26 series × 7 months). **MAE = 2.3791, RMSE = 3.4949**, full t-12 window coverage 182/182, zero fallbacks. Rolling origin gives identical figures (t-12 always precedes the origin within a one-year horizon). Demand target: ALL borrow events by borrowDate regardless of return status. No production integration.
- **Phase 5B demand forecasting baselines** (`scripts/dev-seed/phase5b_calendar_baselines.py`): Offline synthetic-data development prototype extending Phase 5A with three baselines: seasonal naive, trailing 3-month moving average, trailing 6-month moving average, plus synthetic academic-calendar regime analysis based on `semester_factor()` (BREAK/NORMAL/REDUCED). Calendar regimes are SYNTHETIC development assumptions and do not represent real academic calendar data. No production integration.

  **Evaluation protocol.** Static holdout is the primary protocol, matching `scripts/dev-realdata/ML_POPULATION_DEFINITION.md` ("Train on earlier loans, validate on later loans. Never the reverse"). Rolling origin is reported alongside it as an explicitly justified secondary protocol. A trailing W-month mean is a *sliding* statistic, and under a frozen origin with a horizon longer than W, "the W months before month t" runs off the end of the training data. The earlier implementation discarded the months it could not see and, when none were left, substituted a mean over the entire 36-month training history — so the reported "3-month MA" and "6-month MA" measured a 36-month mean for most of the horizon and the three baselines were not comparable. The window is now anchored explicitly: at the origin under static holdout (held constant across the horizon), at the prior month under rolling origin (slides forward). Every forecast point records `source`, `window_requested`, `window_covered`, `window_start` and `window_end`, so a partial or substituted forecast can no longer pass silently.

  **What the two protocols actually measure.** Under `STATIC_HOLDOUT` the trailing window is frozen at the origin, so the "3-month MA" and "6-month MA" rows are a level estimated from the three or six months ending 2025-12 and then held constant across the whole horizon. They are *not* values that move from one forecast point to the next, and the name "moving average" describes the window's shape at the origin rather than its behaviour over the horizon. Under `ROLLING_ORIGIN` the window slides (its `window_end` advances one month per forecast point), so the rolling-origin columns are the ones that describe a genuinely moving average. Read the two protocols as different estimators rather than as two scorings of the same estimator. `seasonal_naive` is unaffected by this distinction: it is a point lookup at t-12 under either protocol.

  **Corrected results on synthetic data** (`libris_ml_dev`, 9,575 borrow events, 26 series, grid 2023-01 through 2026-07, origin 2025-12, horizon 2026-01 through 2026-07, 182 forecast points per baseline):

  | Baseline | Static holdout MAE | Static holdout RMSE | Rolling origin MAE | Rolling origin RMSE | Window coverage |
  |---|---|---|---|---|---|
  | Seasonal naive | 2.3791 | 3.4949 | 2.3791 | 3.4949 | 182/182 (100%) |
  | 3-month MA | 2.2930 | 3.0714 | 2.5549 | 3.4288 | 182/182 (100%) |
  | 6-month MA | 2.3278 | 3.0784 | 2.3947 | 3.1621 | 182/182 (100%) |

  The previously documented figures (Seasonal naive 2.7933/4.2171; 3-month MA 3.1886/4.7722; 6-month MA 3.2042/4.8113 over 208 points) are **superseded and no longer valid**: they scored 26 fabricated zero rows for 2026-08 and reported a moving average that had degenerated into a series mean. No baseline is declared a winner; the split is single and not significance-tested, so the gaps between adjacent baselines are not claims of superiority.
- **Phase 5 data-coverage guard**: `seed_generator.py` stops emitting loans at `SIMULATED_TODAY` (2026-07-15), so the configured `DATE_END` of 2026-08-31 is never reached and real borrow data ends 2026-07-14. Densifying to `DATE_END` fabricated a whole all-zero month and scored every baseline against an observation that was never generated. `resolve_grid_bounds()` now clips the grid to the last observed month and the report names the excluded month explicitly. A month the generator never emitted is treated as missing data, not as zero demand. The guard covers the **month** axis on both ends (earlier than `DATE_START` and later than the effective end). A **series-range** guard additionally drops borrow records outside the resolved window before aggregation, because densification otherwise derives its series keys from every input row: a category seen only outside the window would otherwise be zero-filled across the grid and then scored as a real, all-zero series, silently inflating the series count and the number of forecast points. Neither guard invents observations — clipping only ever removes rows.
- **Phase 5 horizon limits are explicit, not silent**: `seasonal_naive` requires a single observation 12 months before each target. If the requested holdout extends far enough that some target's t-12 month falls after the origin, that month is by construction not in the information set, so `forecast_all` now raises a `ValueError` rather than falling through to the series mean. The shipped 7-month horizon (origin 2025-12, t-12 months 2025-01..2025-07) is well inside the limit and is unaffected. An empty forecast horizon returns an empty result instead of letting the caller index into it.
- **Phase 5 UNCATEGORIZED coverage**: the current seed produces only `NEWSPAPER|UNCATEGORIZED` (8,622 book and 610 magazine borrow events all carry a catalog category), so the NULL-category branch for books and magazines is never exercised end to end by the data. It is now covered by tests that drive the real `classify_borrow_row()` through aggregation to a seasonal forecast, and is documented as unexercised by the seed rather than implied to be covered by it.
- **Phase 5 fallback reachability**: the series-mean fallback is unreachable against the current seed (0 of 182 points) because the synthetic grid is complete and the horizon is shorter than the 12-month seasonal lag. This is now asserted by a test and stated in the reports, so the fallback is documented as a guarded code path rather than as an active component of the results.
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
- Spotless, JaCoCo, 175 executed tests all passing

### Phase 6.1 — Documentation Consistency
- Verified and corrected README, CURRENT_STATE, SETUP, DEPLOYMENT, AGENTS against source
- Identified and removed `PHASE5_PRODUCTION_READINESS.md` after independent audit found substantially fabricated claims
- Identified and removed `phase5a_readiness.py` after verification showed it generated static text without database access

### Phase 6.2 — Concurrency & Query Optimization Hardening
- Pessimistic locking for borrow/return race protection via `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `findByIdForBorrow`/`findByIdForReturn`
- Cache eviction on borrow/return via `@CacheEvict(cacheNames = "books", allEntries = true)`
- BorrowRecord N+1 prevention via `@EntityGraph` on `findAll(Pageable)`
- Magazine/Newspaper CRUD audit events with full actor metadata
- Failed login audit events with null actor fields; audit publication failure handled without swallowing BadCredentialsException
- Operational logging for authentication, access denied, validation errors, lock contention
- Catch-all exception handler for unhandled exceptions; re-throws AuthenticationException/AccessDeniedException
- V5 Flyway migration: `idx_borrow_records_return_date` and `idx_borrow_records_due_date` indexes
- SQL-level overdue query optimization via JPQL predicates replacing Java filtering
- Keyset pagination for overdue report generation
- Service-layer unit tests for BorrowRecordService, AnalyticsService, ReportService
- Frontend overdue status aligned with backend due-date semantics (removed hardcoded 14-day calculation)
- 175 executed tests across 17 test classes; all passing

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
