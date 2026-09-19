# Technical Debt Register

> **Source of truth as of:** 3 August 2026
>
> **Purpose:** Track all known deferred features, code-level shortcuts, testing gaps, and infrastructure limitations in the Library Management System v1.0.0 baseline.

---

## Table of Contents

1. [Deferred Features (ARCHITECTURE.md §12)](#1-deferred-features-architecturemd-12)
2. [Code-Level Debt](#2-code-level-debt)
3. [Testing Debt](#3-testing-debt)
4. [Infrastructure Debt](#4-infrastructure-debt)

---

## 1. Deferred Features (ARCHITECTURE.md §12)

These features are intentionally out of scope for v1.0.0. Each has a rationale for deferral and should be re-evaluated for a future release.

### 1.1 JWT Authentication

| Item | Detail |
|------|--------|
| **Current state** | Session-based auth with `JSESSIONID`, BCrypt, CSRF |
| **Deferred** | JWT for separate clients, APIs, or stateless deployments |
| **Rationale** | v1.0.0 is a same-origin single web application. JWT adds token lifecycle, storage, and revocation complexity without solving a current requirement. |
| **Revisit when** | Project expands to mobile clients, third-party API consumers, or requires stateless microservice deployment. |
| **Reference** | ARCHITECTURE.md §2, ADR-005 |

### 1.2 Richer Sort Options and Search Filters

| Item | Detail |
|------|--------|
| **Current state** | Basic `search` query param (title/author substring match) and `page/size` pagination on list endpoints |
| **Deferred** | Richer sort options (by author, publish date, availability) and search filters (date ranges, available-only toggle) |
| **Rationale** | Basic search and pagination are sufficient for the current data volume. More options add UI and query complexity. |
| **Revisit when** | Catalogue grows beyond a few hundred items or user feedback requests advanced filtering. |
| **Reference** | ARCHITECTURE.md §12, ADR-013 |

### 1.3 Book Categories

| Item | Detail |
|------|--------|
| **Current state** | Books have no category/tag model. Magazines have a `category` field. |
| **Deferred** | Whether books have one category (FK) or many categories (join table) |
| **Rationale** | Requirements have not clarified cardinality. Premature modelling risks a schema rewrite. |
| **Revisit when** | Requirements define whether books belong to one or many categories. |
| **Reference** | ARCHITECTURE.md §12 |

### 1.4 Physical Book Copies

| Item | Detail |
|------|--------|
| **Current state** | One `Book` entity per ISBN with a denormalized `available` boolean. No copy tracking. |
| **Deferred** | Multiple independent copies of one ISBN that can be loaned separately |
| **Rationale** | Would require a `BookCopy` entity with per-copy availability and borrow-record linkage changes. |
| **Revisit when** | Multiple copies of one title must be loaned independently. |
| **Reference** | ARCHITECTURE.md §12 |

### 1.5 Fine System

| Item | Detail |
|------|--------|
| **Current state** | No `dueDate` field on `BorrowRecord`. No overdue calculation. |
| **Deferred** | Due date, overdue calculation, payment tracking, audit rules |
| **Rationale** | Fine logic is a significant domain expansion requiring new entities, rules, and potential payment integration. |
| **Revisit when** | Circulation policy requires enforcement of due dates and monetary penalties. |
| **Reference** | ARCHITECTURE.md §12 |

### 1.6 Reservations / Holds

| Item | Detail |
|------|--------|
| **Current state** | No reservation mechanism. Students can only borrow if `available == true`. |
| **Deferred** | Queue/fairness rules, hold expiry, notification triggers |
| **Rationale** | Adds reservation lifecycle, queue management, and fairness logic that is beyond baseline circulation. |
| **Revisit when** | Popular titles require waitlist or hold functionality. |
| **Reference** | ARCHITECTURE.md §12 |

### 1.7 Notifications

| Item | Detail |
|------|--------|
| **Current state** | No email, SMS, or in-app notification system |
| **Deferred** | Reminders for due dates, overdue alerts, availability updates |
| **Rationale** | Requires external service integration (email provider, SMS gateway) and notification scheduling. |
| **Revisit when** | Operational needs require proactive borrower communication. |
| **Reference** | ARCHITECTURE.md §12 |

### 1.8 Self-Service Student Registration

| Item | Detail |
|------|--------|
| **Current state** | Students are created only by ADMIN or LIBRARIAN via `/api/students` |
| **Deferred** | Self-registration endpoint for students to create their own accounts |
| **Rationale** | Requires additional validation, email verification, and role self-assignment safeguards. |
| **Revisit when** | Student onboarding needs to be self-service. |
| **Reference** | PROJECT_OVERVIEW.md §12 |

### 1.9 Soft Deletes

| Item | Detail |
|------|--------|
| **Current state** | Hard deletes only. Books with borrow history throw `ConflictException`. |
| **Deferred** | Archival flags, soft-delete semantics, audit actor fields |
| **Rationale** | Soft deletes require a `deleted` flag, query filtering, and restore logic. Not yet needed for baseline. |
| **Revisit when** | Regulatory or audit requirements mandate record retention over physical deletion. |
| **Reference** | ARCHITECTURE.md §12 |

### 1.10 Observability

| Item | Detail |
|------|--------|
| **Current state** | Actuator exposes `/health`, `/info`, `/metrics`. No structured logs, no metrics dashboards. |
| **Deferred** | Structured JSON logging, Prometheus metrics, Grafana dashboards, alerting |
| **Rationale** | Production-grade observability requires deployment infrastructure (Prometheus, Grafana, log aggregation). |
| **Revisit when** | Deployed to production with monitoring infrastructure available. |
| **Reference** | ARCHITECTURE.md §12 |

---

## 2. Code-Level Debt

### 2.1 ProfileController Directly Accesses Repository

| Item | Detail |
|------|--------|
| **File** | `controller/ProfileController.java:11` |
| **Issue** | `ProfileController` injects `AccountRepository` directly and queries it, bypassing the service layer. This is the only controller in the codebase that violates the layered architecture convention. |
| **Impact** | Business logic (if added later) would land in the controller. Inconsistent with all other controllers. |
| **Fix** | Route through `AuthService` or a dedicated `ProfileService`. |
| **Reference** | ARCHITECTURE.md §3.6 |

### 2.2 No Service Interfaces

| Item | Detail |
|------|--------|
| **Files** | All 11 classes in `service/` |
| **Issue** | All services are concrete `@Service` classes with no corresponding interface. Controllers inject the concrete type directly. |
| **Impact** | Reduces testability (cannot easily mock with a stub implementation), prevents swapping implementations, and tightens coupling between controller and service layers. |
| **Decision** | User chose to skip interfaces for this project. Documented here for completeness. |
| **Fix** | Extract interfaces for each service and inject interfaces in controllers. |

### 2.3 No MapStruct — Manual DTO Mapping

| Item | Detail |
|------|--------|
| **Files** | All services (`BookService.java:138-148`, `MagazineService`, `NewspaperService`, etc.) |
| **Issue** | Entity-to-DTO and DTO-to-entity mapping is done manually in each service method (`apply()`, `response()` private methods). No MapStruct or ModelMapper is used. |
| **Impact** | Mapping code is duplicated across services. Adding fields to entities requires updating every service's mapping method. Prone to field-miss errors. |
| **Fix** | Introduce MapStruct with `@Mapper` interfaces and `@Mapping` annotations. |

### 2.4 No equals/hashCode on Entities

| Item | Detail |
|------|--------|
| **Files** | All entity classes: `Account.java`, `StudentProfile.java`, `LibrarianProfile.java`, `Book.java`, `Magazine.java`, `Newspaper.java`, `BorrowRecord.java`, `AuditLog.java`, `AuditableEntity.java` |
| **Issue** | No entity class overrides `equals()` or `hashCode()`. All rely on the default `Object` identity-based equality. |
| **Impact** | Entities in different `EntityManager` persistence contexts comparing equal by reference but not by ID. Can cause subtle bugs in `Set`/`Map` usage, detached entity comparisons, and Hibernate cache behavior. |
| **Fix** | Implement `equals()` and `hashCode()` based on the `@Id` field, following the JPA best-practice (business key or ID-based equality). |

### 2.5 Hibernate ddl-auto=update in Production (RESOLVED)

| Item | Detail |
|------|--------|
| **File** | `application.properties:5` |
| **Issue** | Previously: `spring.jpa.hibernate.ddl-auto=update` was set for the production MySQL configuration. |
| **Status** | **RESOLVED** in Phase 6.2 — Flyway migrations adopted (V1–V5) and `ddl-auto=none` now set for production. |
| **Reference** | DATABASE.md §Schema Management, DECISIONS.md #16 |

### 2.6 Uncommitted Frontend Feature Branch Changes

| Item | Detail |
|------|--------|
| **Branch** | `feature/v1.1-analytics-reports-docker` (current working branch) |
| **Issue** | Frontend stabilization was committed (bb36a41, 768e9b6, be2c43e) but some working-tree modifications remain in `static/` (modal, sidebar, theme, CSS). Git status shows deleted `files/` directory and modified HTML/JS files. |
| **Files** | Various HTML pages, `js/app-init.js`, `components/sidebar-loader.js` |
| **Impact** | Working tree is not clean. Changes should be committed or stashed before merging to main. |
| **Fix** | Review remaining modifications and commit or stash. |

### 2.7 No JacocoCo Coverage Enforcement

| Item | Detail |
|------|--------|
| **File** | `pom.xml` |
| **Issue** | The `jacoco-maven-plugin` is not configured in the Maven build. No coverage thresholds are enforced. No coverage reports are generated. |
| **Impact** | Test coverage is not measured or tracked. Regressions in coverage are undetectable. |
| **Fix** | Add `jacoco-maven-plugin` to `pom.xml` with `prepare-agent` and `check` goals, enforce minimum coverage thresholds (e.g. 70% line coverage). |

### 2.8 No Performance Benchmarks

| Item | Detail |
|------|--------|
| **Issue** | No JMH microbenchmarks or performance baselines exist. |
| **Impact** | No way to detect performance regressions in entity mapping, query execution, or serialization. |
| **Fix** | Add JMH benchmarks for critical paths (book search, borrow transaction, DTO mapping). |

### 2.9 No Load Testing

| Item | Detail |
|------|--------|
| **Issue** | No load testing tools (Gatling, k6, JMeter) are configured. No concurrent-use scenarios are tested. |
| **Impact** | Unknown behavior under concurrent borrow/return operations. Database connection pool and transaction isolation are unvalidated under load. |
| **Fix** | Add a Gatling or k6 test plan covering concurrent borrows of the same book and list-endpoint pagination under load. |

---

## 3. Testing Debt

### 3.1 Current Test Coverage Summary

| Test File | What It Tests |
|-----------|---------------|
| `LibraryManagementIntegrationTest` | Books CRUD, borrow/return flow, validation, duplicate ISBN, logout |
| `CrudIntegrationTest` | Magazine CRUD, Newspaper CRUD, Student update/delete, Librarian update/delete, Dashboard counts, Audit log access, Duplicate username |
| `BrowserCsrfFlowIntegrationTest` | Browser-equivalent CSRF cookie/header login/logout flow |
| `BookRepositoryTest` | Book repository persistence and constraints |
| `TestBCrypt` | BCrypt password hashing (utility) |

### 3.2 Missing: Magazine/Newspaper Service-Layer Tests (RESOLVED)

| Item | Detail |
|------|--------|
| **Issue** | Previously: No dedicated unit or integration tests for `MagazineService` or `NewspaperService`. |
| **Status** | **RESOLVED** in Phase 6.2 — Added `MagazineServiceTest` and `NewspaperServiceTest` with 6 methods each covering CRUD audit events, borrow-history deletion guard, and NotFound handling. |
| **Untested scenarios** | Now tested: Borrow-history deletion guard, audit event publishing with full actor metadata, NotFound error handling. |

### 3.3 Missing: Dashboard/Analytics/Report Service Tests (RESOLVED)

| Item | Detail |
|------|--------|
| **Issue** | Previously: No tests for `AnalyticsService` and `ReportService`. |
| **Status** | **RESOLVED** in Phase 6.2 — Added `AnalyticsServiceTest` (16 methods) and `ReportServiceTest` (26 methods) covering dashboard analytics, overdue summary, top books/readers pagination, CSV generation, pagination boundaries, and defensive null handling. |
| **Untested scenarios** | Now tested: Dashboard totals accuracy, analytics trend calculations, overdue summary logic, CSV report generation, pagination boundaries. |

### 3.4 Missing: ProfileController Tests

| Item | Detail |
|------|--------|
| **Issue** | No tests for `ProfileController`. |
| **Untested scenarios** | Profile retrieval for authenticated user, profile response shape, handling of missing account. |
| **Impact** | The only controller that violates the layered architecture is also the only controller with no dedicated test. |

### 3.5 Missing: AuthController Tests Beyond Login

| Item | Detail |
|------|--------|
| **Issue** | `AuthController` exposes `/csrf`, `/login`, `/logout`, `/me`. Only the login→me→logout flow is tested in `BrowserCsrfFlowIntegrationTest`. |
| **Untested scenarios** | `/csrf` response shape validation, `/me` with different roles, `/me` after session expiry, OAuth2 login flow, CSRF token refresh behavior. |
| **Impact** | Auth edge cases and role-specific `/me` responses are unverified. |

### 3.6 Missing: End-to-End Browser Tests

| Item | Detail |
|------|--------|
| **Issue** | No Playwright, Selenium, or Cypress tests. All tests are MockMvc-based (server-side HTTP simulation). |
| **Untested scenarios** | Actual browser rendering, JavaScript execution, form submission, navigation, responsive layout, theme switching, modal interactions, toast notifications. |
| **Impact** | Frontend bugs (CSS regressions, JS module loading, navigation failures) are not caught by automation. Only manual screenshot evidence exists. |

---

## 4. Infrastructure Debt

### 4.1 No Flyway Migrations Created (RESOLVED)

| Item | Detail |
|------|--------|
| **File** | `src/main/resources/db/migration/` |
| **Issue** | Previously: Flyway dependencies were added to `pom.xml` but no migration scripts existed. |
| **Status** | **RESOLVED** in Phase 6.2 — Added V1–V5 Flyway migrations: V1 (baseline), V2 (student email unique), V3 (borrow record due_date), V4 (book category), V5 (borrow_records indexes). Production now uses `ddl-auto=none` with Flyway enabled. |
| **Current state** | Schema is managed by Flyway versioned migrations. |
| **Impact** | Versioned, repeatable schema management now in place. Production schema changes are controlled. |

### 4.2 No Production Logging Configuration

| Item | Detail |
|------|--------|
| **File** | `logback-spring.xml`, `application.properties` |
| **Issue** | Structured JSON logging is configured via `logback-spring.xml` with `logstash-logback-encoder`. However, no file-based appender with rotation is configured. Logs go to stdout only. |
| **Current state** | Structured JSON to stdout via `logstash-logback-encoder`. No log file, no rotation, no retention policy. |
| **Impact** | In production, logs are lost when the process restarts. No log rotation means unbounded stdout growth. |
| **Fix** | Add file-based appender with time-based rotation and size cap in `logback-spring.xml`. Configure retention policy. |

### 4.3 No Alerting/Monitoring Beyond Actuator Health

| Item | Detail |
|------|--------|
| **File** | `application.properties:14-16` |
| **Issue** | Actuator exposes `health`, `info`, and `metrics` endpoints. No Prometheus scrape endpoint. No Grafana dashboards. No alerting rules. |
| **Current state** | Health check is available at `/actuator/health` (shows details when authenticated). Metrics are exposed but not scraped. |
| **Impact** | No proactive alerting for downtime, high error rates, slow queries, or memory pressure. Operational visibility is limited to manual health-check inspection. |
| **Fix** | Add `micrometer-registry-prometheus` dependency. Configure Prometheus scrape endpoint. Create Grafana dashboards for JVM, HTTP, and database metrics. Define alert rules for error rate, latency, and availability. |

---

## Appendix: Quick Reference

| Category | Items | Severity |
|----------|-------|----------|
| Deferred Features | 10 items (JWT, pagination, categories, copies, fines, reservations, notifications, self-registration, soft deletes, observability) | Low (intentional) |
| Code-Level Debt | 6 items (ProfileController, no interfaces, manual mapping, no equals/hashCode, uncommitted changes, no benchmarks, no load tests) | Medium |
| Testing Debt | 3 gaps (ProfileController, AuthController, E2E browser tests) | Medium |
| Infrastructure Debt | 2 items (no production logging, no alerting/monitoring) | Medium (production readiness) |

---

## Related Documentation

- [Architecture](ARCHITECTURE.md) — §3.6 ProfileController note, §12 deferred features
- [Project Overview](PROJECT_OVERVIEW.md) — §12 future improvements
- [Security](SECURITY.md) — Auth flow, CSRF design
- [Database](DATABASE.md) — ER diagram, schema design
