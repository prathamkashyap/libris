# Changelog

All notable project changes are recorded here.

## v1.0.0 - 2026-07-23

### Added

- Spring Boot REST backend with Spring Data JPA, Hibernate, and MySQL.
- Spring Security session authentication, BCrypt passwords, CSRF protection, and role-based authorization.
- Books, students, librarians, dashboard, profile, borrow, and return workflows.
- Responsive HTML/CSS/JavaScript frontend with Fetch API integration.
- MockMvc integration coverage, browser-equivalent CSRF coverage, and repository safeguards.
- Release-ready setup, testing, API, architecture, and project-structure documentation.

### Fixed

- Duplicate ISBN now returns `409 Conflict` with `ISBN already exists.` instead of a generic server error.
- Invalid email validation now returns and displays `Invalid email address.` at the affected field.

### Changed

- Restored the indigo/teal open-book application mark in navigation, the sign-in view, and favicon metadata.
- Consolidated documentation under `docs/`, moved review evidence to `screenshots/`, and removed obsolete prototype/research artifacts from the release tree.

## Unreleased - CSRF browser-flow correction

### Fixed

- Configured a SPA-aware CSRF request handler so the raw `XSRF-TOKEN` cookie
  sent by the Fetch API in `X-XSRF-TOKEN` is validated correctly under Spring
  Security 6.5, while CSRF protection remains enabled.
- Added a MockMvc regression test that performs the browser-equivalent CSRF
  cookie and header exchange for login, session reuse, and logout.

## Day 1 - Architecture baseline

### Added

- Requirements traceability document derived from the frozen architecture.
- API contract baseline with endpoint, authority, JSON, status, and error conventions.
- Architecture entry point and Mermaid ER diagram.
- Day-wise delivery board.

### Verified

- The API contract uses the frozen `/api/borrow-records` resource name.
- The schema contains only the five approved baseline tables.
- No implementation code has been created against undocumented endpoints.
- Corrected the historical borrow sequence diagram to the frozen `/api/borrow-records` route.

## Day 2 - Responsive frontend shell

### Added

- Static Spring Boot frontend shell with approved Login, Dashboard, Books, Students, Librarians, Borrow Records, and Profile pages.
- Responsive navigation, desktop/mobile breakpoints, accessible skip link, focus styles, semantic tables, and prototype-aligned indigo/teal design tokens.
- Temporary Day 2 demo rendering, explicitly isolated in `js/main.js` for replacement by the Day 8 Fetch integration.

### Verified

- No unapproved module or UI framework was introduced.
- All approved modules can be reached through responsive navigation.

## Day 3 - Frontend interaction prototype

### Added

- Reusable accessible modal component for book, student, librarian, and borrow forms.
- Client-side required/email validation, safe toast feedback, and book filtering with empty state.
- A dedicated temporary interaction boundary that avoids treating browser demo data as the final source of truth.
- Shared Fetch helper with session credentials, JSON/error parsing, and safe handling for `204 No Content` responses.

### Verified

- The prototype supports the approved create/borrow user flows without adding a due-date, categories, or other deferred data fields.

## Day 4 - Spring Boot persistence foundation

### Added

- Java 21 Spring Boot project with Web, Validation, Data JPA, Security, MySQL, and test dependencies.
- The frozen entities: Account, StudentProfile, LibrarianProfile, Book, and BorrowRecord, plus the Role enum and focused repositories.
- MySQL configuration using environment-overridable local credentials and Hibernate schema update for active development.

### Verified

- No controllers, direct SQL, DTO shortcuts, or non-frozen database tables were introduced in this persistence milestone.

## Day 5 - Books API

### Added

- Documented `/api/books` CRUD controller, Book request/response DTOs, and service-owned search/update/delete behaviour.
- Global structured error responses for validation, not-found, and conflict paths.
- Deletion guard preventing a book with borrow history from being deleted.

### Verified

- `mvn -DskipTests compile` passes.
- The endpoint resource and DTO fields match `docs/API.md`; entities are not exposed from controllers.

## Day 6 - Students and Librarians APIs

### Added

- Transactional account/profile creation for student and librarian records with BCrypt hashes and unique-username protection.
- DTO-based list, get, create, update, and delete endpoints for the approved profile resources.
- Separate update DTOs, ensuring ordinary profile updates cannot reset a password.

### Verified

- `mvn -DskipTests compile` passes.
- Only the approved account/profile fields and REST resource names are used; future password-reset scope was not inferred.

## Day 7 - Borrow records, dashboard, and domain errors

### Added

- Canonical `/api/borrow-records` list, borrow, and return endpoints.
- Transactional borrow/return services that maintain book availability and preserve borrower snapshots from the selected student profile.
- Dashboard counts and structured `BOOK_UNAVAILABLE` / `ALREADY_RETURNED` error responses.

### Verified

- `mvn -DskipTests compile` passes.
- Borrow/return routes, snapshots, status strings, and dashboard response fields match the frozen API contract.

## Day 8 - Frontend/backend integration

### Added

- Centralized Fetch clients for every implemented resource and replacement of temporary rendered demo data.
- UI refresh after create/borrow/return actions, API-backed search, and common JSON/error/204 handling.

## Day 9 - Spring Security

### Added

- BCrypt-backed account authentication, session login/logout/current-user endpoints, role-based endpoint restrictions, and CSRF token forwarding.
- Development-only seeded admin account for local verification.

## Day 10 - QA and delivery documentation

### Added

- Executable black-box test matrix and complete local run instructions.

### Verified

- Java compilation, JavaScript syntax checks, and whitespace checks pass.
- Local MySQL was not running in this workspace, so live browser/API test execution is documented as pending rather than falsely marked passed.

## Hardening pass - verification, security, and persistence quality

### Added

- H2-backed MockMvc integration tests for authentication, CRUD flows, borrow/return, validation, logout, and JSON 401/403 failures.
- Repository test for ISBN uniqueness and auditing timestamps.
- Structured JSON authentication and authorisation handlers using `ApiErrorResponse`.
- `AuthService`, explicit lazy relationship mappings, database ISBN uniqueness, and Spring Data `createdAt`/`updatedAt` auditing.
- README architecture diagram and automated/manual verification guidance.

### Verified

- `mvn test` passes: full Spring request lifecycle and repository safeguards run against an isolated H2 database in MySQL compatibility mode.
- Live local-MySQL session authentication has been verified through the browser and direct HTTP flow: CSRF bootstrap, login, authenticated access, logout, and post-logout rejection all succeed. Full UI screenshot evidence remains pending.
