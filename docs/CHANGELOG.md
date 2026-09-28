# Changelog 📝

> **All notable changes** to this project are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added

- Pessimistic locking for borrow/return race protection via `@Lock(LockModeType.PESSIMISTIC_WRITE)` on dedicated repository methods
- Failed login audit events with null actor fields (actorId, actorRole null for failed attempts)
- Magazine/Newspaper CRUD audit events with full actor metadata (id, username, role, IP, UA)
- Operational logging for authentication success/failure, access denied, validation errors, lock contention
- V5 Flyway migration: `idx_borrow_records_return_date` and `idx_borrow_records_due_date` indexes
- SQL-level overdue query optimization via JPQL predicates replacing Java filtering
- Keyset pagination for overdue report generation
- Service-layer unit tests for BorrowRecordService, AnalyticsService, ReportService, MagazineService, NewspaperService, AuthService
- Concurrency tests for borrow/return race conditions and cache eviction
- Query optimization tests for SQL-level overdue semantics and pagination
- Flyway-enabled integration test for V5 migration verification
- Catch-all exception handler for unhandled exceptions (re-throws AuthenticationException/AccessDeniedException)
- Phase 5A demand forecasting baseline with seasonal-naive forecasting (`scripts/dev-seed/phase5a_demand_aggregation.py`)
- Phase 5B demand forecasting baselines with seasonal naive, 3-month moving average, and 6-month moving average (`scripts/dev-seed/phase5b_calendar_baselines.py`)
- Synthetic academic-calendar regime analysis for demand forecasting (BREAK/NORMAL/REDUCED months based on semester_factor())
- Phase 5A/5B unit tests for forecasting methodology verification
- Phase 5 shared forecasting core (`scripts/dev-seed/phase5_forecasting.py`) as the single source of truth for month arithmetic, record classification, the demand grid, baseline methods, evaluation protocols and MAE/RMSE; import-safe so the unit tests need no database
- Explicit rolling-origin evaluation protocol reported alongside the static holdout, so a trailing moving average can be evaluated in the operational setting it is meant for
- Per-forecast-point audit columns (`source`, `window_requested`, `window_covered`, `window_start`, `window_end`) and per-baseline window-coverage reporting, so a partial or substituted forecast cannot pass silently
- Phase 5 data-coverage guard (`resolve_grid_bounds`) that clips the demand grid to the last month the data actually covers
- Phase 5 series-range guard: borrow records outside the configured analysis window are dropped before aggregation, so a category seen only outside that window can no longer contribute a scored series
- Phase 5 explicit horizon validation: `seasonal_naive` now rejects a holdout longer than the 12-month seasonal lag, and an empty forecast horizon returns an empty result instead of raising `IndexError` at the call site
- Phase 5 test coverage for the NULL-category UNCATEGORIZED branch of `classify_borrow_row()`, the series-mean fallback, partial trailing windows, and both protocols' information sets

### Changed

- Frontend overdue status aligned with backend due-date semantics (removed hardcoded 14-day calculation)
- Frontend overdue description changed from "past 14 days" to "past due date"
- AdminSeeder now rejects the default password `ChangeMe123!` and requires a strong password
- Main application.properties no longer provides a default admin password
- Overdue dashboard and report queries now use SQL-level predicates instead of Java filtering
- BorrowRecord pagination uses EntityGraph to prevent N+1 queries

### Fixed

- Stale book cache after circulation changes now evicted via `@CacheEvict(cacheNames = "books", allEntries = true)`
- BorrowRecord listing N+1 queries resolved via `@EntityGraph` on `findAll(Pageable)`
- Magazine/Newspaper audit events now published on all CRUD operations
- Failed login audit publication failure no longer swallows BadCredentialsException
- Phase 5A seasonal-naive historical lookup corrected to preserve monthly dimension (item_type|category|month) instead of series-only key (item_type|category), which caused multiple training months to overwrite each other
- Phase 5B trailing-window baseline no longer slides off the end of the frozen training data. The W-month window is now anchored explicitly: at the origin under static holdout, and at the prior month under rolling origin. Under static holdout the window is therefore *frozen* at the origin and the baseline is a level estimated once and held across the horizon, not a value that moves at each forecast point; the sliding window that genuinely moves between forecast points is the rolling-origin protocol, which is reported alongside it
- Phase 5B trailing-mean baseline no longer silently substitutes a mean over the entire 36-month training history when no window month is visible. That degeneration affected 104 of 182 forecast points (2026-04 onward for the 3-month window) and made the three baselines incomparable
- Phase 5 evaluation horizon no longer includes a fabricated 2026-08 all-zero month. `seed_generator.py` stops at `SIMULATED_TODAY` (2026-07-15) and real borrow data ends 2026-07-14, so 26 of the previous 208 scored rows were observations that were never generated. The horizon is now 2026-01 through 2026-07 (182 points)
- Phase 5 test suite no longer reimplements the forecasting logic. `test_phase5a.py` and `test_phase5b.py` import the shipped modules, so a stale copy of the logic can no longer pass while the real implementation is broken
- Phase 5 metrics recomputed from the corrected implementation. Seasonal naive MAE 2.7933→2.3791 and RMSE 4.2171→3.4949; 3-month MA MAE 3.1886→2.2930 and RMSE 4.7722→3.0714; 6-month MA MAE 3.2042→2.3278 and RMSE 4.8113→3.0784. The previously documented values are superseded and no longer valid
- The seasonal-naive figures move only because the horizon shrank from 208 to 182 points: the seasonal implementation itself was not changed in this release, and reproduces 2.3791/3.4949 on the 182-point horizon either way. Most of the trailing-window improvement is likewise attributable to dropping the fabricated 2026-08 month rather than to the window fix, so the arrow notation above should not be read as the effect of the window change alone

## [1.1.0] - 2026-09-19

### Added

- Concurrency hardening: pessimistic locking for borrow/return race protection
- Failed login audit events with null actor fields
- Magazine/Newspaper CRUD audit events with full actor metadata
- Operational logging for authentication, access denied, validation errors, lock contention
- V5 Flyway migration: return_date and due_date indexes on borrow_records
- SQL-level overdue query optimization replacing Java filtering
- Keyset pagination for overdue report generation
- Service-layer unit tests for BorrowRecordService, AnalyticsService, ReportService, MagazineService, NewspaperService, AuthService
- Concurrency tests for borrow/return race conditions and cache eviction
- Query optimization tests for SQL-level overdue semantics and pagination
- Flyway-enabled integration test for V5 migration verification
- Catch-all exception handler for unhandled exceptions

### Changed

- Frontend overdue status aligned with backend due-date semantics
- AdminSeeder now rejects default password and requires strong password
- Main application.properties no longer provides default admin password
- Overdue dashboard and report queries use SQL-level predicates
- BorrowRecord pagination uses EntityGraph to prevent N+1 queries

### Fixed

- Stale book cache after circulation changes now evicted
- BorrowRecord listing N+1 queries resolved via EntityGraph
- Magazine/Newspaper audit events now published on all CRUD operations
- Failed login audit publication failure no longer swallows BadCredentialsException

### Security

- Pessimistic locking prevents concurrent borrow/return race conditions
- Failed login audit trail without credential leakage
- Operational logging for security-relevant events
- Catch-all exception handler preserves AuthenticationException/AccessDeniedException semantics

## [1.0.0] - 2026-07-23

### Added

- Spring Boot 3.5 REST backend with Spring Data JPA, Hibernate, and MySQL.
- Spring Security session authentication with BCrypt password hashing.
- SPA-aware CSRF protection using `CookieCsrfTokenRepository` and a custom `SpaCsrfTokenRequestHandler` that accepts raw tokens from the `X-XSRF-TOKEN` header for Fetch requests while retaining XOR protection for form submissions.
- Role-based URL authorization for three roles: `ADMIN`, `LIBRARIAN`, `STUDENT`.
- Books, magazines, and newspapers CRUD with searchable catalogues.
- Student and librarian management with transactional account/profile creation.
- Borrow and return workflow with availability protection, ISBN uniqueness enforcement, and preserved borrower snapshots.
- Dashboard statistics (student, librarian, book, borrowed, available counts).
- Server-side audit timestamps (`created_at`, `updated_at`) on all entities via `@EnableJpaAuditing`.
- Global structured error responses (`ApiErrorResponse`) for validation, not-found, conflict, and business-rule violations.
- Responsive HTML/CSS/JavaScript frontend with multi-page architecture, Fetch API integration, modal forms, toast notifications, and XSS escaping.
- MockMvc integration tests for authentication, CRUD, borrow/return, validation, ISBN conflicts, role restrictions (401/403), and logout.
- Browser-equivalent CSRF flow integration test (`BrowserCsrfFlowIntegrationTest`).
- Repository test for ISBN uniqueness constraint and auditing timestamp population.
- Black-box test matrix (14 cases, pending local MySQL execution).
- Docker Compose configuration with optional phpMyAdmin profile.
- GitHub Actions CI pipeline (`mvn clean verify`).
- Release documentation: Architecture, API contract, Setup, Testing, Changelog, and this file.

### Fixed

- Duplicate ISBN now returns `409 Conflict` with `ISBN already exists.` instead of a generic server error.
- Invalid email validation now returns and displays `Invalid email address.` at the affected field.
- Configured SPA-aware CSRF request handler so the raw `XSRF-TOKEN` cookie sent by Fetch is validated correctly under Spring Security 6.5.

### Changed

- Restored the indigo/teal open-book application mark in navigation, the sign-in view, and favicon metadata.
- Consolidated documentation under `docs/`, moved review evidence to `screenshots/`, and removed obsolete prototype/research artifacts from the release tree.

### Security

- BCrypt-backed password hashing (`BCryptPasswordEncoder`).
- Session-based authentication with `SessionCreationPolicy.IF_REQUIRED`.
- CSRF bootstrap via `GET /api/auth/csrf` on page load.
- Structured 401/403 JSON error responses via `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler`.
- `server.error.include-message=never` prevents Spring Boot error detail leakage.

---

## Development History

The following records the day-by-day development process during the build phase.

### Day 1 — Architecture baseline

- Requirements traceability document derived from the frozen architecture.
- API contract baseline with endpoint, authority, JSON, status, and error conventions.
- Architecture entry point and Mermaid ER diagram.
- Verified: API contract uses the frozen `/api/borrow-records` resource name. Schema contains only the five approved baseline tables. No undocumented endpoints.

### Day 2 — Responsive frontend shell

- Static Spring Boot frontend shell with Login, Dashboard, Books, Students, Librarians, Borrow Records, and Profile pages.
- Responsive navigation, desktop/mobile breakpoints, accessible skip link, focus styles, semantic tables, and indigo/teal design tokens.
- Temporary demo rendering in `js/main.js` for replacement by Day 8 Fetch integration.

### Day 3 — Frontend interaction prototype

- Reusable accessible modal component for book, student, librarian, and borrow forms.
- Client-side required/email validation, safe toast feedback, and book filtering with empty state.
- Shared Fetch helper with session credentials, JSON/error parsing, and safe handling for `204 No Content`.

### Day 4 — Spring Boot persistence foundation

- Java 21 Spring Boot project with Web, Validation, Data JPA, Security, MySQL, and test dependencies.
- Frozen entities: `Account`, `StudentProfile`, `LibrarianProfile`, `Book`, `BorrowRecord`, plus the `Role` enum and repositories.
- MySQL configuration with environment-overridable credentials and Hibernate schema update.

### Day 5 — Books API

- `/api/books` CRUD controller, Book request/response DTOs, and service-owned search/update/delete behaviour.
- Global structured error responses for validation, not-found, and conflict paths.
- Deletion guard preventing a book with borrow history from being deleted.

### Day 6 — Students and Librarians APIs

- Transactional account/profile creation for student and librarian records with BCrypt hashes and unique-username protection.
- DTO-based list, get, create, update, and delete endpoints for the approved profile resources.
- Separate update DTOs ensuring ordinary profile updates cannot reset a password.

### Day 7 — Borrow records, dashboard, and domain errors

- Canonical `/api/borrow-records` list, borrow, and return endpoints.
- Transactional borrow/return services maintaining book availability and preserving borrower snapshots.
- Dashboard counts and structured `BOOK_UNAVAILABLE` / `ALREADY_RETURNED` error responses.

### Day 8 — Frontend/backend integration

- Centralized Fetch clients for every implemented resource.
- Replacement of temporary rendered demo data with live API calls.
- UI refresh after create/borrow/return actions, API-backed search, and common JSON/error/204 handling.

### Day 9 — Spring Security

- BCrypt-backed account authentication, session login/logout/current-user endpoints, role-based endpoint restrictions, and CSRF token forwarding.
- Development-only seeded admin account for local verification.

### Day 10 — QA and delivery documentation

- Executable black-box test matrix and complete local run instructions.
- Verified: Java compilation, JavaScript syntax checks, and whitespace checks pass.

### Hardening pass — verification, security, and persistence quality

- H2-backed MockMvc integration tests for authentication, CRUD flows, borrow/return, validation, logout, and JSON 401/403 failures.
- Repository test for ISBN uniqueness and auditing timestamps.
- Structured JSON authentication and authorization handlers.
- `AuthService`, explicit lazy relationship mappings, database ISBN uniqueness, and Spring Data auditing.
- Verified: `mvn test` passes against isolated H2 in MySQL compatibility mode.
