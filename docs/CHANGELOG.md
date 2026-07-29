# Changelog

All notable changes to this project are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

_No unreleased changes._

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
