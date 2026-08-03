# AI Handoff — Library Management System

## Quick Start for Next AI
- Project location: /Users/prathamkashyap/Documents/Coding.nosync/projects/Library Management System
- Branch: feature/v1.1-analytics-reports-docker
- Latest commit: be2c43e (chore: stabilize repository and synchronize documentation)
- Total commits: 33
- Test status: 13 passing (BUILD SUCCESS)
- Spotless: passing

## Architecture
- Spring Boot 3.5 monolith, Java 21, Spring Security 6.5, Spring Data JPA
- MySQL (production), H2 (tests + local dev via h2 profile)
- MPA frontend served by Spring Boot, vanilla JS ES modules, Fetch API
- Session-based auth, CSRF via SpaCsrfTokenRequestHandler
- 14 controllers, 11 services, 8 repositories, 8 entities, 24+ DTOs

## Current Release Status
- v1.0.0: Released (initial commit b7ecac3)
- v1.1.0: In progress on feature branch (Phases 0–6 complete)
- v1.2.0: Planned (after Phases 4–6)
- v1.3.0: Planned (after Phase 7)

## Completed Phases
- Phase 0: Spotless + AdminSeeder + test infra + docs + CI
- Phase 0.5: Account orphaning fix
- Phase 2: N+1 query fixes
- Phase 3A: Flyway + Swagger restriction
- Phase 3B: Actuator + structured logging
- Phase 4: Integration tests (13 total)
- Phase 6: Documentation + README

## Open Bugs
- None identified

## Technical Debt
- See docs/TECHNICAL_DEBT.md (full list)
- Key items: No Flyway migrations created, no JacCoCo, no caching, no rate limiting

## Key Decisions
- See docs/DECISIONS.md (15 decisions documented)
- User overrides: No service interfaces, no MapStruct, no equals/hashCode

## Rejected Ideas
- Service interfaces (not needed for portfolio)
- MapStruct (manual DTO mapping sufficient)
- equals/hashCode on entities (JPA identity sufficient)

## Verification Status
- Spotless: PASSING
- All 13 tests: PASSING
- Build: SUCCESS

## Known Failing Tests
- None

## Recent Commits (last 8)
1. be2c43e chore: stabilize repository and synchronize documentation
2. 768e9b6 Batch 5-7: Refactored individual pages to use centralized modal
3. bb36a41 Batch 1-4: Infrastructure extraction, dead code cleanup, shared modal/theme
4. 892337b docs: update README with Actuator, N+1 fixes, new tests, Spotless
5. 68f913a test: add CrudIntegrationTest
6. 1c4c0a0 ops: Actuator + structured logging
7. f1fcda0 perf: N+1 fix Student/Librarian
8. bfd9341 perf: N+1 fix BorrowRecord

## Next Task
- Phase D: Complete (this document)
- Final Project B Retirement Audit
- Plan Phase 7 (v1.3.0 scope)
- Implement Phase 7

## Reading Order for New AI
1. docs/AI_HANDOFF.md (this file)
2. docs/CURRENT_STATE.md
3. docs/IMPLEMENTATION_PLAN.md
4. docs/DECISIONS.md
5. docs/TECHNICAL_DEBT.md
6. docs/TESTING_KB.md
7. docs/PERFORMANCE.md
8. docs/SECURITY_KB.md
9. docs/REVIEW_SUMMARY.md
10. docs/ARCHITECTURE.md
11. docs/API.md
12. docs/DATABASE.md

## Key Files
- backend/pom.xml — Dependencies and plugins
- backend/src/main/resources/application.properties — Main config
- backend/src/main/resources/application-h2.properties — H2 dev config
- backend/src/main/resources/logback-spring.xml — Structured logging
- backend/src/test/resources/application.properties — Test config
- .github/workflows/ci.yml — CI pipeline
- docs/ — Full documentation suite

## How to Build and Test
```bash
cd backend
./mvnw spotless:check          # Verify formatting
./mvnw spotless:apply           # Auto-fix formatting
./mvnw test                     # Run all 13 tests
./mvnw clean verify             # Full build + tests
```

## Environment Variables Required
- LMS_DB_URL (optional, has MySQL default)
- LMS_DB_USERNAME (optional, defaults to root)
- LMS_DB_PASSWORD (required)
- LMS_ADMIN_PASSWORD (required)
- GOOGLE_CLIENT_ID (optional, for OAuth2)
- GOOGLE_CLIENT_SECRET (optional, for OAuth2)
