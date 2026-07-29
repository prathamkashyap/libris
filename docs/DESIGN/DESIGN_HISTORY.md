# Design History

This document records the pre-implementation design context: the resources reviewed, the alternatives considered, and the decisions that shaped the final architecture. It is a historical reference — the implementation is the source of truth.

See [ARCHITECTURE.md](../ARCHITECTURE.md) for the as-built system design.

---

## Existing Resources Reviewed

### HTML prototype — retained as the visual and interaction starting point

The original single-page HTML prototype contained Home, Books, and Borrow Records views; responsive CSS; an indigo/teal design system; cards; status badges; a search field; add/edit/borrow modals; toast feedback; and client-side dummy data.

**Decisions retained from the prototype:**
- The calm indigo/teal visual language, card treatment, typography hierarchy, status badges, modal/dialog pattern, and toast feedback concept.
- Client-rendered, SPA-like navigation experience (later evolved into the MPA structure).
- Vanilla JavaScript rather than introducing a framework.
- Extended rather than replaced — Login, Dashboard, Students, Librarians, Profile, and Logout look native to the prototype.

**Not carried forward:**
- In-memory `books` and `records` arrays as application data.
- Single-file implementation.
- "Due back" field (not in the frozen data model).

### Reference ASP.NET MVC PDFs — functional reference, not UI or code template

The PDFs (`BACKEND DEVELOPEMENT AND DB INTEGRATION.pdf` and `PART 2.pdf`) document an earlier MVC learning implementation demonstrating:
- Book CRUD with validation and error handling.
- Borrow forms with borrower details.
- Book availability changes on borrow/return.
- Dashboard with counts, Student/Librarian CRUD, Login/Logout.

**Retained:** Functional scope and business rules.  
**Rejected:** Bootstrap MVC UI, direct SQL in controllers, plaintext/mock login, cascade-style history loss.

### Meeting screenshots — evidence of scope evolution

Confirmed that Books was the original module, with Students, Librarians, Login, Dashboard, and Logout added later. A `Categories` reference folder was noted but not adopted as a baseline module.

### Prior planning material

Spring Boot notes and planning research surveyed alternatives (controller-to-repository CRUD, service layer, borrowed flag, server-side templates, JavaScript frameworks). Adopted decisions consolidated into the architecture; retired source files removed from the release tree.

---

## Technology Alternatives Considered

### Server-rendered Thymeleaf/JSP/MVC views

Rejected because the working prototype is already client-rendered and the project benefits from a REST boundary. Introducing server templates would duplicate frontend work.

### React, Angular, Vue, Vaadin

Rejected due to build pipeline and framework overhead. The project scope is moderate and vanilla JavaScript is sufficient.

### AdminLTE/CoreUI or Bootstrap screens

Rejected to preserve the existing bespoke prototype visual direction.

### H2 or embedded database

Rejected for production — MySQL provides realistic foreign keys and durable setup. H2 used only for tests.

### Raw JDBC/direct SQL in controllers

Rejected — couples HTTP handling, SQL, mapping, and business rules. Spring Data JPA repositories plus services selected.

### Direct entity serialisation

Rejected — exposes persistence design and risks leaking password hashes or internal fields. DTOs mandatory.

### JWT for first release

Deferred — adds token issuance, storage, expiry, refresh, and revocation complexity. Server sessions are simpler for same-origin academic deployment.

---

## Database Design Alternatives

### Option A: One `users` table

Rejected — single wide table weakens normalization and makes role evolution awkward.

### Option B: Fully separate `users`, `students`, `librarians` tables

Not adopted as written — the useful insight (separate role data) was refined into one shared account table with strict profile one-to-one relationships.

### Borrowed flag only on `books`

Rejected — loses loan history, cannot model completed loans, cannot identify double returns.

**Selected:** Separate `accounts`, `student_profiles`, `librarian_profiles`, `books`, and `borrow_records` tables with normalized relationships.

---

## Frozen Architecture Decisions

The following decisions were frozen before implementation:

1. The final UI evolves from the reviewed HTML prototype; it does not copy the ASP.NET/Bootstrap reference UI.
2. The complete baseline functional scope is Login, Dashboard, Books, Students, Librarians, Borrow Records, Profile, and Logout.
3. The selected stack is Java 21, Spring Boot, Spring Data JPA, Hibernate, MySQL, HTML, CSS, JavaScript, Fetch API, Spring Security, and BCrypt.
4. The architecture is Browser → Fetch → Controller → Service → Repository → JPA/Hibernate → MySQL.
5. Controllers do not contain direct SQL or core business rules; services own transactions and domain rules.
6. The database begins with `accounts`, `student_profiles`, `librarian_profiles`, `books`, and `borrow_records`.
7. Authentication identity is separate from profile data; usernames are unique; passwords are BCrypt hashes.
8. JPA entities are never directly exposed through the REST API; request/response DTOs are mandatory.
9. Borrow history uses a dedicated record with foreign keys plus name/email/phone snapshots.
10. Borrow and return update record state and book availability atomically.
11. Historical borrow information is protected; deletion policies must not silently cascade-delete audit records.
12. REST endpoints use `/api` and plural resource naming; the canonical loan resource is `/api/borrow-records`.
13. The client is served from the Spring application and uses Fetch with session credentials.
14. Authentication uses Spring Security session-based login/logout and role controls.
15. Same-origin CSRF protection is deliberately configured when sessions are active.
16. Responsive/mobile-first behaviour is required for all modules.
17. `API.md`, README, CHANGELOG, diagrams, screenshots, and black-box tests are maintained daily.

---

## Traceability Summary

| Source Evidence | Retained Decision | Explicitly Not Carried Forward |
|-----------------|-------------------|-------------------------------|
| HTML prototype | Visual system, cards, modals, toast, Books/Borrow interaction, responsive intent | In-memory arrays; single-file implementation |
| ASP.NET PDFs | Book CRUD, borrow/return rules, validation, errors, dashboard, students/librarians functional scope | Bootstrap/MVC UI; direct SQL in controllers; plaintext login |
| Meeting screenshots | Expanded modules, shared navigation, dashboard counts | Category module as baseline |
| Spring notes | Layered services/repositories, JPA, API/testing documentation rationale | Direct entity exposure; arbitrary frontend framework adoption |
| Deep research report | REST + Fetch, MySQL, profiles, DTOs, session security, staged plan | Provisional CSRF disabling; ambiguous `/api/borrow` naming |
