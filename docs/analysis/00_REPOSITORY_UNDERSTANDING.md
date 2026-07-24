# Phase 0 — Repository Understanding

> Reverse-engineered from source code on 2026-07-24. The implementation is the primary source of truth.

---

## What Problem Does This Software Solve?

The Library Management System (LMS) is a web application that automates the day-to-day operations of a small library. It manages a **book catalogue**, **student and librarian user accounts**, and the **borrow/return lifecycle** of books. The system tracks which books are available, who has borrowed them, and provides a dashboard with aggregate statistics (total students, librarians, books, borrowed, available). It replaces manual tracking of library loans with a digital, role-secured workflow.

## Who Are the Users / Roles?

The system defines three roles via the `Role` enum (`ADMIN`, `LIBRARIAN`, `STUDENT`):

| Role | Capabilities (from `SecurityConfig` and service logic) |
|------|-------------------------------------------------------|
| **ADMIN** | Full access. Creates and manages librarian accounts (`/api/librarians/**`). Manages books, students, borrow records, and dashboard. Only role seeded at startup. |
| **LIBRARIAN** | Manages books, students, and borrow records. Can view the dashboard. Cannot manage other librarian accounts. |
| **STUDENT** | Can view books (GET `/api/books/**`) and their own profile (`/api/profile`). Cannot manage any records or access the dashboard. |

A default `admin` / `ChangeMe123!` account is seeded on first startup by a `CommandLineRunner` in `LibraryManagementApplication`.

## What Is the Overall Architecture Style?

The application follows a **layered MVC architecture** within a single deployable Spring Boot 3.5 monolith:

```
Browser (SPA)  →  Fetch API  →  REST Controllers  →  Services  →  Repositories  →  MySQL
```

- **Presentation layer**: A single-page application (SPA) built with vanilla HTML, CSS, and JavaScript. Served as static resources from Spring Boot (`/static`). Hash-based client-side routing (`location.hash`).
- **API layer**: Seven `@RestController` classes under `/api/**` producing/consuming JSON.
- **Service layer**: Six `@Service` classes containing transactional business logic, DTO mapping, and validation.
- **Persistence layer**: Five Spring Data JPA `JpaRepository` interfaces backed by Hibernate. No custom SQL — all queries use derived query methods.
- **Security layer**: Spring Security 6.5 with session-based authentication, BCrypt password hashing, CSRF cookie/header exchange (SPA-friendly), and role-based URL authorization.
- **Database**: MySQL (production) / H2 in MySQL-compatibility mode (tests). Schema managed by Hibernate `ddl-auto=update` — no migration files.

## What Are the Major Modules / Domains?

| Domain | Entities | Key operations |
|--------|----------|----------------|
| **Authentication & Security** | `Account`, `Role` | Login, logout, CSRF bootstrap, session management, current-user lookup |
| **Book Catalogue** | `Book` | CRUD, title/author search, ISBN uniqueness enforcement, availability tracking |
| **People Management** | `StudentProfile`, `LibrarianProfile` (each linked 1:1 to `Account`) | CRUD with account creation, username uniqueness, role assignment |
| **Borrow / Return** | `BorrowRecord` (links `Book` ↔ `StudentProfile`) | Borrow (marks book unavailable), return (marks book available), status filtering, history preservation |
| **Dashboard** | (no entity — aggregates from existing repositories) | Counts of students, librarians, total/borrowed/available books |
| **Profile** | (reads from `Account`) | Current authenticated user's non-sensitive info |

## How Does Data Generally Flow Through the System?

1. **Bootstrap**: The browser loads `index.html` and the JS module graph. On page load, `auth-api.js` fetches `/api/auth/csrf` to obtain a CSRF token cookie, then attempts `/api/auth/me` to check for an existing session.
2. **Authentication**: The user submits credentials via a login form. The `AuthController` delegates to `AuthService`, which authenticates via Spring Security's `AuthenticationManager`, creates an HTTP session, and returns the user's identity as JSON.
3. **Authorized API calls**: Once logged in, `main.js` calls all API modules in parallel (`dashboard`, `books`, `students`, `librarians`, `borrow-records`, `profile`) to populate the SPA. Each call flows through `http.js → requestJson()`, which attaches the CSRF header from the cookie and sends `credentials: "include"` for session cookies.
4. **Mutation flow** (example — borrow a book): The user opens a modal → submits a `BorrowRequest` → `BorrowRecordController` validates input → `BorrowRecordService` checks book availability and student existence → creates a `BorrowRecord`, marks the `Book` as unavailable → returns `BorrowRecordResponse`.
5. **Error handling**: `GlobalExceptionHandler` translates domain exceptions (`ResourceNotFoundException`, `ConflictException`, `BusinessRuleException`) and Bean Validation errors into a uniform `ApiErrorResponse` JSON shape. The frontend `http.js` parses these and surfaces toast messages.
6. **Persistence**: All entity mutations are transactional (`@Transactional`). The `AuditableEntity` mapped superclass auto-populates `created_at` / `updated_at` via Spring Data JPA auditing.

---

*Phase 0 complete. Awaiting confirmation to proceed to Phase 1.*
