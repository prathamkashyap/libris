# Engineering Journal — Library Management System (Project A)

A chronological record of every work session, covering objectives, completions, key commits, problems encountered, and notable findings.

---

## Session 1 — Initial Implementation

| Field | Value |
|-------|-------|
| **Date** | July 24–25, 2026 (before v1.0.0) |
| **Branch** | `main` |
| **Duration** | ~2 days |

### Objective

Build the complete Library Management System from scratch — backend API, frontend MPA, Docker infrastructure, and project documentation.

### Completed

- **Backend (Spring Boot 3.2 / Java 21)**
  - 14 REST controllers covering all domain entities
  - 11 service classes with business logic
  - 8 JPA entities with Flyway migrations
  - JWT-based authentication with role-based access control
  - Swagger/OpenAPI documentation
- **Frontend**
  - Multi-page architecture (MPA) with Thymeleaf templates
  - Client-side routing and shared UI components
- **Infrastructure**
  - Docker Compose setup for local development
  - PostgreSQL datasource configuration
- **Documentation**
  - README, ARCHITECTURE, SETUP, API, DATABASE, TESTING, DEPLOYMENT docs
  - CHANGELOG for v1.0.0

### Key Commits

| Commit | Date | Description |
|--------|------|-------------|
| `b7ecac3` | 2026-07-24 | `feat: complete Library Management System` — initial full implementation |
| `349e662` | 2026-07-25 | `chore: add AdminSeeder for admin user initialization` |
| `33d3420` | 2026-07-25 | `Implement authentication bootstrap and document datasource authentication issue` |
| `e97de87` | 2026-07-24 | `docs: organize project documentation for v1.0.0 release` |
| `8da1d86` | 2026-07-25 | `test: fix pagination assertion for Page response` |
| `7e7d601` | 2026-07-27 | `chore: add *.log to .gitignore` |

### Problems & Notes

- **Authentication bootstrap required careful handling of datasource.** Spring Boot's auto-configuration for security needed the datasource to be fully initialized before authentication filters could reference user tables. This required ordering `CommandLineRunner` beans and deferring schema-dependent security logic.
- **AdminSeeder pattern** was introduced to ensure a default admin account exists on first run, avoiding the chicken-and-egg problem of needing auth to create the first user.

---

## Session 2 — v1.1 Feature Development

| Field | Value |
|-------|-------|
| **Date** | July 27, 2026 |
| **Branch** | `main` |
| **Version** | v1.1 |

### Objective

Extend the system with Analytics, Audit, and Report features; add Docker multi-stage build; deliver SPA frontend modules for new features.

### Completed

- **Backend**
  - Analytics controller and service — aggregated borrow statistics, user activity metrics
  - Audit controller and service — system event logging and retrieval
  - Report controller and service — exportable reports for library operations
  - Swagger/OpenAPI annotations for all new endpoints
  - Docker multi-stage build for optimized production images
- **Frontend**
  - SPA pages for Analytics, Audit, and Reports views
  - JavaScript modules and API clients for new backend features
  - CSS updates for v1.1 integration
- **CI/CD**
  - GitHub Actions CI workflow added

### Key Commits

| Commit | Date | Description |
|--------|------|-------------|
| `d59622c` | 2026-07-27 | `feat(backend): add Analytics, Audit, and Report features with Swagger/OpenAPI` |
| `1bc9b48` | 2026-07-27 | `feat(backend): add Docker setup with multi-stage build` |
| `667ffed` | 2026-07-27 | `feat(frontend): add SPA pages, JS modules, and API clients for v1.1 features` |
| `abc5c7e` | 2026-07-27 | `docs: update README and DEBUG for v1.1, add CI workflow` |
| `c3e1843` | 2026-07-27 | `fix(frontend): update existing API clients and CSS for v1.1 integration` |

### Problems & Notes

- **Frontend migration from `script.js` to `main.js` required careful dependency management.** The monolithic `script.js` was being decomposed into modular files. Load order and global namespace dependencies between modules required sequential refactoring rather than a simple rename.
- The `c3e1843` fix commit was needed to reconcile existing frontend API clients and CSS with the new v1.1 backend endpoints — a sign that the frontend and backend were developed in parallel without a shared contract.

---

## Session 3 — Frontend Fixes

| Field | Value |
|-------|-------|
| **Date** | July 27–28, 2026 |
| **Branch** | `main` |

### Objective

Fix frontend regressions introduced during the v1.1 migration (Session 2).

### Completed

- Restored modal and toast rendering (`393c3d1`)
- Restored shared UI bootstrap after `script.js` migration (`6a772b8`)
- Restored theme toggle button in Settings > Appearance (`3b54fa0`)
- Ported pink theme CSS rules to loaded stylesheet (`916a439`)
- Removed orphaned registration JS from `main.js` (`4b67ec7`)
- Removed dead theme mockups, empty directories, and `.DS_Store` files (`6e558ff`)

### Key Commits

| Commit | Date | Description |
|--------|------|-------------|
| `393c3d1` | 2026-07-28 | `fix(frontend): restore modal and toast rendering` |
| `6a772b8` | 2026-07-28 | `fix(frontend): restore shared UI bootstrap after script.js migration` |
| `3b54fa0` | 2026-07-28 | `fix(frontend): restore theme toggle button in Settings > Appearance` |
| `916a439` | 2026-07-28 | `fix(frontend): port pink theme CSS rules to loaded stylesheet` |
| `4b67ec7` | 2026-07-27 | `chore: remove orphaned registration JS from main.js` |
| `6e558ff` | 2026-07-27 | `chore: remove dead theme mockups, empty directories, and .DS_Store` |
| `303b06b` | 2026-07-27 | `chore: remove unused repository methods` |

### Problems & Notes

- **Orphaned `script.js` references broke multiple pages.** After the `script.js` → `main.js` migration in Session 2, several HTML templates still referenced `<script src="script.js">`. Since the file was deleted, those pages silently failed — no JS errors in the console, but all interactive features (modals, toasts, theme toggle) stopped working. This was a cross-cutting regression that affected 5+ pages.
- The `35bac39` commit (self-service registration design doc) was also created during this session, suggesting documentation was being written alongside bug fixes.

---

## Session 4 — Documentation Reconciliation

| Field | Value |
|-------|-------|
| **Date** | July 29–30, 2026 |
| **Branch** | `main` |

### Objective

Reconcile all project documentation with the actual codebase state after the v1.1 feature work and frontend fixes.

### Completed

- Updated README to reflect current feature set and architecture
- Updated DEBUG guide with new debugging procedures
- Added self-service registration design document
- Added timestamps to documentation for traceability
- Reconciled documentation drift from implementation

### Key Commits

| Commit | Date | Description |
|--------|------|-------------|
| `16941c1` | 2026-07-29 | `docs: reconcile documentation with current implementation` |
| `9b0d2c8` | 2026-07-30 | `docs: add timestamps and reconcile documentation with source` |
| `35bac39` | 2026-07-27 | `docs: add self-service registration design document` |

### Problems & Notes

- **Documentation had drifted from implementation.** After rapid feature development (Sessions 1–3), documentation described features that didn't exist yet (e.g., pagination was documented as "deferred" but was already implemented in 8 endpoints). Other docs referenced removed files (`script.js`) or outdated API signatures.
- The `9b0d2c8` commit added timestamps to create an audit trail — a direct response to the drift problem.

---

## Session 5 — Phase 0: Quality Foundation

| Field | Value |
|-------|-------|
| **Date** | August 1, 2026 |
| **Branch** | `main` |
| **Phase** | Phase 0 — Quality Foundation |

### Objective

Improve the project to portfolio quality by addressing formatting, testing, performance, observability, and code hygiene.

### Completed

| Category | Work | Commit |
|----------|------|--------|
| **Formatting** | Added Spotless plugin and reformatted all 88 Java source files | `9bc85c0` |
| **Logging** | Fixed AdminSeeder SLF4J usage (was using `System.out.println`) | `ffc308d` |
| **Test Infrastructure** | Fixed test configuration for environment variable isolation | `ffc308d` |
| **Dead Code** | Removed dead CSS, unused repository methods, empty directories | `ffc308d` |
| **Docs** | Corrected documentation errors and misalignments | `ffc308d` |
| **CI** | Added Spotless check to GitHub Actions CI pipeline | `ffc308d` |
| **Data Integrity** | Fixed orphaned account deletion when removing students/librarians | `374560c` |
| **Performance** | Fixed N+1 queries in BorrowRecord list endpoints | `bfd9341` |
| **Performance** | Fixed N+1 queries in Student/Librarian list endpoints | `f1fcda0` |
| **Observability** | Added Spring Boot Actuator with health/info endpoints | `1c4c0a0` |
| **Observability** | Added structured JSON logging (Logback) | `1c4c0a0` |
| **Testing** | Added 7 new integration tests (Magazine, Newspaper, Student, Librarian CRUD) | `68f913a` |
| **Docs** | Updated README with Actuator, N+1 fixes, new tests, Spotless | `892337b` |

### Key Commits

| Commit | Date | Description |
|--------|------|-------------|
| `9bc85c0` | 2026-08-01 | `chore: add Spotless plugin and reformat all Java sources` |
| `ffc308d` | 2026-08-01 | `fix: Phase 0 — AdminSeeder logging, test infrastructure, docs corrections` |
| `374560c` | 2026-08-01 | `fix: delete orphaned accounts when removing students or librarians` |
| `bfd9341` | 2026-08-01 | `perf: fix N+1 queries in BorrowRecord list endpoints` |
| `f1fcda0` | 2026-08-01 | `perf: fix N+1 queries in Student/Librarian list endpoints` |
| `1c4c0a0` | 2026-08-01 | `ops: add Spring Boot Actuator and structured JSON logging` |
| `68f913a` | 2026-08-01 | `test: add integration tests for Magazine, Newspaper, Student, Librarian CRUD` |
| `892337b` | 2026-08-01 | `docs: update README with Actuator, N+1 fixes, new tests, Spotless` |

### Problems & Notes

- **`LMS_ADMIN_PASSWORD` env var override caused test failures.** The `AdminSeeder` read the admin password from an environment variable. During test execution, if this env var was set (e.g., from a local `.env`), the seeder would attempt to create an admin with a real password, colliding with test fixtures. Fixed by checking `spring.profiles.active` and skipping seeding in test profiles.
- **Pre-existing uncommitted feature branch changes complicated staging.** Some work-in-progress changes from feature branches were present in the working tree, requiring careful `git stash` and selective staging to avoid committing unrelated changes.
- **Interesting finding: Pagination was already implemented.** Documentation listed pagination as "deferred to post-v1.0", but the codebase already had pagination working across 8 endpoints (`Pageable` parameters, `Page<T>` responses). The documentation was stale — not the code.

---

## Session 6 — Phase D: Engineering Knowledge Base

| Field | Value |
|-------|-------|
| **Date** | August 1, 2026 |
| **Branch** | `main` |
| **Phase** | Phase D — Engineering Knowledge Base |

### Objective

Create comprehensive engineering documentation: state analysis, implementation plans, technical debt registry, decision logs, testing knowledge base, performance analysis, security review, and AI handoff materials.

### Completed

| Document | Purpose |
|----------|---------|
| `CURRENT_STATE.md` | Snapshot of the codebase state at this point in time |
| `IMPLEMENTATION_PLAN.md` | Phased plan for remaining work |
| `TECHNICAL_DEBT.md` | Registry of known technical debt items |
| `DECISIONS.md` | Architecture Decision Records (ADRs) |
| `TESTING_KB.md` | Testing knowledge base — patterns, coverage, gaps |
| `PERFORMANCE.md` | Performance analysis and optimization history |
| `SECURITY_KB.md` | Security knowledge base — controls, audit findings |
| `REVIEW_SUMMARY.md` | Code review summary and findings |
| `AI_HANDOFF.md` | Context document for AI-assisted development |
| `investigations/` | 10 investigation reports for specific technical questions |
| `phases/` | Phase completion reports |
| `journal/` | This engineering journal |

### Key Commits

Documentation created in this session (not yet committed — part of Phase D deliverables).

### Problems & Notes

- **Large documentation scope required parallel task agents.** The sheer volume of documentation (10+ documents, 10 investigation reports) was too much for sequential authoring. Parallel task agents were used to draft documents concurrently, with a final reconciliation pass to ensure consistency across all files.
- Documentation was cross-referenced against the actual codebase to avoid repeating the drift problem from Session 4.

### Next Steps

1. **Project B Final Audit** — Complete the final audit of Project B
2. **Phase 7 Planning** — Plan the next phase of development
3. **Commit Phase D deliverables** — Stage and commit all documentation created in this session

---

## Appendix: Full Commit History (31 commits)

| # | Commit | Date | Description |
|---|--------|------|-------------|
| 1 | `b7ecac3` | 2026-07-24 | feat: complete Library Management System |
| 2 | `e97de87` | 2026-07-24 | docs: organize project documentation for v1.0.0 release |
| 3 | `8da1d86` | 2026-07-25 | test: fix pagination assertion for Page response |
| 4 | `349e662` | 2026-07-25 | chore: add AdminSeeder for admin user initialization |
| 5 | `33d3420` | 2026-07-25 | Implement authentication bootstrap and document datasource authentication issue |
| 6 | `7e7d601` | 2026-07-27 | chore: add *.log to .gitignore |
| 7 | `d59622c` | 2026-07-27 | feat(backend): add Analytics, Audit, and Report features with Swagger/OpenAPI |
| 8 | `1bc9b48` | 2026-07-27 | feat(backend): add Docker setup with multi-stage build |
| 9 | `667ffed` | 2026-07-27 | feat(frontend): add SPA pages, JS modules, and API clients for v1.1 features |
| 10 | `abc5c7e` | 2026-07-27 | docs: update README and DEBUG for v1.1, add CI workflow |
| 11 | `c3e1843` | 2026-07-27 | fix(frontend): update existing API clients and CSS for v1.1 integration |
| 12 | `8502099` | 2026-07-27 | refactor(frontend): migrate script.js features into main.js and delete script.js |
| 13 | `35bac39` | 2026-07-27 | docs: add self-service registration design document |
| 14 | `6e558ff` | 2026-07-27 | chore: remove dead theme mockups, empty directories, and .DS_Store |
| 15 | `303b06b` | 2026-07-27 | chore: remove unused repository methods |
| 16 | `4b67ec7` | 2026-07-27 | chore: remove orphaned registration JS from main.js |
| 17 | `393c3d1` | 2026-07-28 | fix(frontend): restore modal and toast rendering |
| 18 | `6a772b8` | 2026-07-28 | fix(frontend): restore shared UI bootstrap after script.js migration |
| 19 | `3b54fa0` | 2026-07-28 | fix(frontend): restore theme toggle button in Settings > Appearance |
| 20 | `916a439` | 2026-07-28 | fix(frontend): port pink theme CSS rules to loaded stylesheet |
| 21 | `16941c1` | 2026-07-29 | docs: reconcile documentation with current implementation |
| 22 | `9b0d2c8` | 2026-07-30 | docs: add timestamps and reconcile documentation with source |
| 23 | `184c74d` | 2026-07-30 | feat(frontend): redesign login page and analytics dashboard |
| 24 | `9bc85c0` | 2026-08-01 | chore: add Spotless plugin and reformat all Java sources |
| 25 | `ffc308d` | 2026-08-01 | fix: Phase 0 — AdminSeeder logging, test infrastructure, docs corrections |
| 26 | `374560c` | 2026-08-01 | fix: delete orphaned accounts when removing students or librarians |
| 27 | `bfd9341` | 2026-08-01 | perf: fix N+1 queries in BorrowRecord list endpoints |
| 28 | `f1fcda0` | 2026-08-01 | perf: fix N+1 queries in Student/Librarian list endpoints |
| 29 | `1c4c0a0` | 2026-08-01 | ops: add Spring Boot Actuator and structured JSON logging |
| 30 | `68f913a` | 2026-08-01 | test: add integration tests for Magazine, Newspaper, Student, Librarian CRUD |
| 31 | `892337b` | 2026-08-01 | docs: update README with Actuator, N+1 fixes, new tests, Spotless |
