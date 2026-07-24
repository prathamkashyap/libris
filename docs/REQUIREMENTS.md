# Library Management System - Requirements Traceability

**Status:** v1.0.0 release baseline  
**Authority:** Derived from [Architecture](ARCHITECTURE.md). The master document is authoritative if this file conflicts with it.

## Functional requirements

| ID | Requirement | Planned milestone | Acceptance evidence |
| --- | --- | --- | --- |
| FR-01 | Users can log in, log out, and obtain safe current-session identity information. | Day 9 | API/security tests BB-01, BB-02, BB-14. |
| FR-02 | Admins and librarians can view dashboard totals for students, librarians, books, borrowed books, and available books. | Day 7 | Dashboard API and UI test. |
| FR-03 | Authorised staff can create, list, view, update, search, and delete eligible books. | Day 5 | Books API tests including validation, not-found, and deletion conflict cases. |
| FR-04 | Admins and librarians can manage student accounts and profiles atomically. | Day 6 | Student CRUD API and account/profile transaction test. |
| FR-05 | Only admins can manage librarian accounts and profiles. | Day 6/9 | Librarian CRUD and role-authorisation test. |
| FR-06 | Staff can borrow an available book, creating a durable borrow record and marking the book unavailable in one transaction. | Day 7 | BB-06 and service/integration test. |
| FR-07 | Staff can return an active borrow record, recording the return date and restoring availability in one transaction. | Day 7 | BB-08 and service/integration test. |
| FR-08 | The system rejects an unavailable-book borrow and an already-returned record with documented errors. | Day 7 | BB-07 and BB-09. |
| FR-09 | Authenticated users can view their own non-sensitive profile information. | Day 9 | Profile API/UI test. |
| FR-10 | The frontend works on desktop and mobile and evolves the existing indigo/teal prototype. | Days 2-3, 8 | Viewport/manual UI tests. |

## Non-functional requirements

| ID | Requirement | Design control |
| --- | --- | --- |
| NFR-01 | Maintain a layered Browser -> Controller -> Service -> Repository -> MySQL architecture. | Package boundaries and code review. |
| NFR-02 | Do not expose JPA entities or password hashes through the API. | Request/response DTOs only. |
| NFR-03 | Store only BCrypt password hashes and enforce server-side role checks. | Spring Security on Day 9. |
| NFR-04 | Preserve borrow history and prevent destructive cascades from removing audit data. | FK/delete policy and service validation. |
| NFR-05 | Return documented HTTP statuses and uniform JSON errors. | Controller advice and API contract. |
| NFR-06 | Treat responsive, keyboard-accessible, readable UI behaviour as a release requirement. | Mobile-first CSS and manual checks. |
| NFR-07 | Keep documentation, API contract, tests, and CHANGELOG current with implementation. | Definition of Done and daily changelog. |

## Scope boundaries

The first release excludes Categories, pagination, advanced search, fines, notifications, Docker, Swagger, JWT, reservations, and physical-copy modelling. They must not enter implementation without an approved ADR update.
