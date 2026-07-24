# Delivery Board

## Done

- [x] Day 1: reviewed source artifacts and froze the master architecture.
- [x] Day 1: created requirements traceability, API contract, architecture entry point, and ER diagram.
- [x] Day 2: built responsive production frontend shell from the existing prototype.
- [x] Day 3: completed frontend interaction prototypes and reusable validation/modal boundary.
- [x] Day 4: bootstrapped Spring Boot/MySQL persistence.
- [x] Day 5: implemented and compiled the Books API.
- [x] Day 6: implemented and compiled Students and Librarians APIs.
- [x] Day 7: implemented and compiled Borrow Records, dashboard, and domain errors.
- [x] Day 8: replaced temporary frontend data with Fetch API integration.
- [x] Day 9: added Spring Security, BCrypt, sessions, roles, and CSRF forwarding.
- [x] Day 10: added QA checklist, test matrix, and delivery documentation.
- [x] Hardening: added MockMvc integration coverage, repository safeguards, JSON 401/403 handlers, audit fields, lazy mappings, ISBN uniqueness, and AuthService.
- [x] Hardening: corrected Spring Security 6.5 SPA CSRF token handling and added browser-equivalent login/logout regression coverage.
- [x] Release preparation: duplicate ISBN conflict response, field-level email validation, restored branding, documentation reorganization, and v1.0.0 release notes.

## Planned


## Deferred scope

- [ ] Categories, pagination, advanced search, fines, notifications, Docker, Swagger, JWT, reservations, and physical-copy modelling.

## External verification pending

- [x] Run the application against a local MySQL instance and verify the browser CSRF/session authentication flow.
- [ ] Execute the remaining documented manual/browser test matrix for CRUD, borrow/return, role restrictions, validation, and mobile layout.
- [ ] Regenerate remaining desktop duplicate-ISBN, invalid-email, and return-book screenshots from the current release UI.
