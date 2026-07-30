# API Contract

> **Source of truth as of:** 30 July 2026

**Version:** v1.0.0  
**Base path:** `/api`  
**Format:** JSON request/response bodies; dates use `yyyy-MM-dd`.

See [ARCHITECTURE.md](ARCHITECTURE.md) for system context and [DATABASE.md](DATABASE.md) for entity details.

---

## Conventions

- `GET` reads, `POST` creates or invokes a domain action, `PUT` updates, `DELETE` removes.
- Successful creation returns `201 Created` with a `Location` header; successful reads/updates return `200 OK`; successful delete and return actions return `204 No Content`.
- `400` validation or business-rule errors; `401` no authenticated session; `403` insufficient role; `404` missing resource; `409` uniqueness, protected deletion, or state conflict.
- Responses never include raw passwords, password hashes, or JPA entity internals.
- API clients must safely handle `204` without attempting to parse a JSON body.
- All responses use the uniform `ApiErrorResponse` shape on error (see [Error Codes](#error-codes)).

---

## Authentication and Session

| Method | Path | Authority | Request | Success Response |
|--------|------|-----------|---------|------------------|
| GET | `/auth/csrf` | Public | — | `200` Spring `CsrfToken` (sets `XSRF-TOKEN` cookie) |
| POST | `/auth/login` | Public | `LoginRequest` JSON | `200` `AuthenticatedUserResponse`; session established |
| POST | `/auth/logout` | Any authenticated | — | `204`; session invalidated |
| GET | `/auth/me` | Any authenticated | — | `200` `AuthenticatedUserResponse` |

### LoginRequest

```json
{
  "username": "admin",
  "password": "example-password"
}
```

### AuthenticatedUserResponse

```json
{
  "accountId": 1,
  "username": "admin",
  "role": "ADMIN",
  "displayName": "System Administrator"
}
```

**CSRF bootstrap:** On page load, the frontend calls `GET /api/auth/csrf` to obtain the `XSRF-TOKEN` cookie. All subsequent non-GET requests include the `X-XSRF-TOKEN` header. See [SECURITY.md](SECURITY.md) for the full CSRF flow.

---

## Dashboard

| Method | Path | Authority | Success Response |
|--------|------|-----------|------------------|
| GET | `/dashboard` | ADMIN, LIBRARIAN | `200` `DashboardResponse` |

### DashboardResponse

```json
{
  "totalStudents": 6,
  "totalLibrarians": 2,
  "totalBooks": 50,
  "borrowedBooks": 3,
  "availableBooks": 47
}
```

---

## Books

| Method | Path | Authority | Request | Success Response |
|--------|------|-----------|---------|------------------|
| GET | `/books?search={text}` | Any authenticated | Optional search query (matches title or author) | `200` `BookResponse[]` |
| GET | `/books/{id}` | Any authenticated | — | `200` `BookResponse` |
| POST | `/books` | ADMIN, LIBRARIAN | `BookRequest` JSON | `201` `BookResponse` |
| PUT | `/books/{id}` | ADMIN, LIBRARIAN | `BookRequest` JSON | `200` `BookResponse` |
| DELETE | `/books/{id}` | ADMIN, LIBRARIAN | — | `204`; or `409` if borrow history exists |

### BookRequest

```json
{
  "title": "Clean Code",
  "author": "Robert C. Martin",
  "isbn": "9780132350884",
  "publishedDate": "2008-08-01"
}
```

| Field | Validation |
|-------|------------|
| `title` | `@NotBlank`, `@Size(max=200)` |
| `author` | `@Size(max=200)` |
| `isbn` | `@Size(max=50)` |
| `publishedDate` | No constraint |

### BookResponse

```json
{
  "id": 5,
  "title": "Clean Code",
  "author": "Robert C. Martin",
  "isbn": "9780132350884",
  "publishedDate": "2008-08-01",
  "available": true
}
```

**Search behavior:** The `search` parameter matches against `title` (contains, case-insensitive) OR `author` (contains, case-insensitive). The frontend also accepts `?query=` as an alias.

**Delete protection:** A book with any borrow history cannot be deleted. The service checks `BorrowRecordRepository.existsByBookId(bookId)` and throws `ConflictException` if true.

**ISBN uniqueness:** Enforced at both the service level (pre-check via `existsByIsbn`) and the database level (`@Column(unique=true)`). Duplicate ISBN returns `409 Conflict` with code `CONFLICT`.

---

## Students

| Method | Path | Authority | Request | Success Response |
|--------|------|-----------|---------|------------------|
| GET | `/students` | ADMIN, LIBRARIAN | — | `200` `StudentResponse[]` |
| GET | `/students/{id}` | ADMIN, LIBRARIAN | — | `200` `StudentResponse` |
| POST | `/students` | ADMIN, LIBRARIAN | `StudentRequest` JSON | `201` `StudentResponse` |
| PUT | `/students/{id}` | ADMIN, LIBRARIAN | `StudentUpdateRequest` JSON | `200` `StudentResponse` |
| DELETE | `/students/{id}` | ADMIN, LIBRARIAN | — | `204` |

### StudentRequest (create)

```json
{
  "username": "alice.smith",
  "password": "initial-password",
  "name": "Alice Smith",
  "email": "alice@example.com",
  "phone": "555-0101"
}
```

| Field | Validation |
|-------|------------|
| `username` | `@NotBlank`, `@Size(max=50)` |
| `password` | `@NotBlank`, `@Size(min=8, max=100)` |
| `name` | `@NotBlank`, `@Size(max=100)` |
| `email` | `@NotBlank`, `@Email`, `@Size(max=100)` |
| `phone` | `@NotBlank`, `@Size(max=20)` |

### StudentUpdateRequest (update)

```json
{
  "username": "alice.smith",
  "name": "Alice Smith",
  "email": "alice@example.com",
  "phone": "555-0101"
}
```

Same as `StudentRequest` minus `password`. **Note:** The `username` field is included and mutable — the service re-checks uniqueness if changed. Password change/reset is not part of the current API.

### StudentResponse

```json
{
  "id": 12,
  "accountId": 24,
  "username": "alice.smith",
  "name": "Alice Smith",
  "email": "alice@example.com",
  "phone": "555-0101",
  "role": "STUDENT"
}
```

**Account creation:** `POST /students` atomically creates an `Account` (with BCrypt-hashed password and `STUDENT` role) and a linked `StudentProfile` in a single `@Transactional` method.

---

## Librarians

| Method | Path | Authority | Request | Success Response |
|--------|------|-----------|---------|------------------|
| GET | `/librarians` | ADMIN | — | `200` `LibrarianResponse[]` |
| GET | `/librarians/{id}` | ADMIN | — | `200` `LibrarianResponse` |
| POST | `/librarians` | ADMIN | `LibrarianRequest` JSON | `201` `LibrarianResponse` |
| PUT | `/librarians/{id}` | ADMIN | `LibrarianUpdateRequest` JSON | `200` `LibrarianResponse` |
| DELETE | `/librarians/{id}` | ADMIN | — | `204` |

### LibrarianRequest (create)

```json
{
  "username": "jane.doe",
  "password": "initial-password",
  "name": "Jane Doe",
  "age": 30,
  "phone": "555-0202"
}
```

| Field | Validation |
|-------|------------|
| `username` | `@NotBlank`, `@Size(max=50)` |
| `password` | `@NotBlank`, `@Size(min=8, max=100)` |
| `name` | `@NotBlank`, `@Size(max=100)` |
| `age` | `@Min(18)`, `@Max(100)` |
| `phone` | `@NotBlank`, `@Size(max=20)` |

### LibrarianUpdateRequest (update)

Same as `LibrarianRequest` minus `password`. The `username` field is included and mutable.

### LibrarianResponse

```json
{
  "id": 5,
  "accountId": 15,
  "username": "jane.doe",
  "name": "Jane Doe",
  "age": 30,
  "phone": "555-0202",
  "role": "LIBRARIAN"
}
```

---

## Borrow Records

| Method | Path | Authority | Request | Success Response |
|--------|------|-----------|---------|------------------|
| GET | `/borrow-records?status={BORROWED\|RETURNED}` | ADMIN, LIBRARIAN | Optional status filter | `200` `BorrowRecordResponse[]` |
| GET | `/borrow-records/my` | Any authenticated | — | `200` `BorrowRecordResponse[]` for current user |
| POST | `/borrow-records` | ADMIN, LIBRARIAN | `BorrowRequest` JSON | `201` `BorrowRecordResponse` |
| POST | `/borrow-records/{id}/return` | ADMIN, LIBRARIAN | — | `204`; return date set, book restored |

### BorrowRequest

```json
{
  "bookId": 5,
  "studentId": 12,
  "borrowerName": "Alice Smith",
  "borrowerEmail": "alice@example.com",
  "borrowerPhone": "555-0101",
  "borrowDate": "2026-07-22"
}
```

| Field | Validation |
|-------|------------|
| `bookId` | `@NotNull` |
| `studentId` | `@NotNull` |
| `borrowerName` | `@NotBlank`, `@Size(max=100)` |
| `borrowerEmail` | `@NotBlank`, `@Email`, `@Size(max=100)` |
| `borrowerPhone` | `@NotBlank`, `@Size(max=20)` |
| `borrowDate` | `@NotNull` |

**Important:** The `borrowerName`, `borrowerEmail`, and `borrowerPhone` fields are validated but **overwritten** with the student profile's data in the service layer. The request fields exist for API completeness but the snapshot always reflects the actual student profile.

### BorrowRecordResponse

```json
{
  "id": 7,
  "bookId": 5,
  "bookTitle": "Clean Code",
  "studentId": 12,
  "borrowerName": "Alice Smith",
  "borrowerEmail": "alice@example.com",
  "borrowerPhone": "555-0101",
  "borrowDate": "2026-07-22",
  "returnDate": null,
  "status": "BORROWED"
}
```

**Status derivation:** `status` is derived — `"BORROWED"` if `returnDate` is null, `"RETURNED"` otherwise.

---

## Profile

| Method | Path | Authority | Success Response |
|--------|------|-----------|------------------|
| GET | `/profile` | Any authenticated | `200` `AuthenticatedUserResponse` |

Returns the same shape as `/auth/me` — the current authenticated user's non-sensitive identity information.

---

## Magazines

| Method | Path | Authority | Request | Success Response |
|--------|------|-----------|---------|------------------|
| GET | `/magazines` | Any authenticated | — | `200` `MagazineResponse[]` |
| GET | `/magazines/{id}` | Any authenticated | — | `200` `MagazineResponse` |
| POST | `/magazines` | ADMIN, LIBRARIAN | `MagazineRequest` JSON | `201` `MagazineResponse` |
| PUT | `/magazines/{id}` | ADMIN, LIBRARIAN | `MagazineRequest` JSON | `200` `MagazineResponse` |
| DELETE | `/magazines/{id}` | ADMIN, LIBRARIAN | — | `204`; or `409` if borrow history exists |

### MagazineRequest

```json
{
  "title": "National Geographic",
  "publisher": "National Geographic Society",
  "issueDate": "2026-07-01",
  "category": "Science",
  "featuredArticle": "Climate Change Effects"
}
```

### MagazineResponse

```json
{
  "id": 3,
  "title": "National Geographic",
  "publisher": "National Geographic Society",
  "issueDate": "2026-07-01",
  "category": "Science",
  "featuredArticle": "Climate Change Effects",
  "available": true
}
```

---

## Newspapers

| Method | Path | Authority | Request | Success Response |
|--------|------|-----------|---------|------------------|
| GET | `/newspapers` | Any authenticated | — | `200` `NewspaperResponse[]` |
| GET | `/newspapers/{id}` | Any authenticated | — | `200` `NewspaperResponse` |
| POST | `/newspapers` | ADMIN, LIBRARIAN | `NewspaperRequest` JSON | `201` `NewspaperResponse` |
| PUT | `/newspapers/{id}` | ADMIN, LIBRARIAN | `NewspaperRequest` JSON | `200` `NewspaperResponse` |
| DELETE | `/newspapers/{id}` | ADMIN, LIBRARIAN | — | `204`; or `409` if borrow history exists |

### NewspaperRequest

```json
{
  "title": "The Daily Times",
  "publisher": "Times Media Group",
  "publicationDate": "2026-07-25",
  "topHeadlines": "Economy grows 3% in Q2"
}
```

### NewspaperResponse

```json
{
  "id": 4,
  "title": "The Daily Times",
  "publisher": "Times Media Group",
  "publicationDate": "2026-07-25",
  "topHeadlines": "Economy grows 3% in Q2",
  "available": true
}
```

---

## Student Dashboard

| Method | Path | Authority | Success Response |
|--------|------|-----------|------------------|
| GET | `/student/dashboard` | STUDENT | `200` `StudentDashboardResponse` |

Returns the current student's dashboard with active borrows and borrow history.

---

## Librarian Dashboard

| Method | Path | Authority | Success Response |
|--------|------|-----------|------------------|
| GET | `/librarian/dashboard` | ADMIN, LIBRARIAN | `200` `DashboardResponse` |

Returns the librarian-facing dashboard view (reuses the main dashboard service).

---

## Analytics

| Method | Path | Authority | Success Response |
|--------|------|-----------|------------------|
| GET | `/analytics/dashboard` | ADMIN, LIBRARIAN | `200` `AnalyticsDashboardResponse` |
| GET | `/analytics/trends` | ADMIN, LIBRARIAN | `200` Monthly borrowing trend data |
| GET | `/analytics/top-books` | ADMIN, LIBRARIAN | `200` `TopBookResponse[]` |
| GET | `/analytics/top-readers` | ADMIN, LIBRARIAN | `200` `TopReaderResponse[]` |
| GET | `/analytics/overdue` | ADMIN, LIBRARIAN | `200` `OverdueSummaryResponse` |

---

## Audit

| Method | Path | Authority | Success Response |
|--------|------|-----------|------------------|
| GET | `/audit` | ADMIN | `200` `AuditLogResponse[]` |
| GET | `/audit/{id}` | ADMIN | `200` `AuditLogResponse` |

Returns the entity change audit trail. Supports filtering by action, entity type, actor, and date range.

---

## Reports

| Method | Path | Authority | Success Response |
|--------|------|-----------|------------------|
| GET | `/reports/inventory` | ADMIN, LIBRARIAN | `200` CSV inventory export |
| GET | `/reports/borrowing` | ADMIN, LIBRARIAN | `200` CSV borrowing export |
| GET | `/reports/overdue` | ADMIN, LIBRARIAN | `200` CSV overdue export |
| GET | `/reports/students` | ADMIN, LIBRARIAN | `200` CSV students export |

---

## Error Codes

All error responses use the uniform `ApiErrorResponse` shape:

```json
{
  "timestamp": "2026-07-22T18:52:00Z",
  "status": 400,
  "code": "BOOK_UNAVAILABLE",
  "message": "The selected book is not available for borrowing.",
  "path": "/api/borrow-records",
  "fieldErrors": []
}
```

### HTTP Status Codes

| Status | Meaning | When |
|--------|---------|------|
| `200` | OK | Successful read or update |
| `201` | Created | Successful resource creation (includes `Location` header) |
| `204` | No Content | Successful delete or return |
| `400` | Bad Request | Validation error or business rule violation |
| `401` | Unauthorized | No authenticated session |
| `403` | Forbidden | Authenticated but insufficient role |
| `404` | Not Found | Resource does not exist |
| `409` | Conflict | Uniqueness violation, protected deletion, or state conflict |

### Application Error Codes

| Code | HTTP Status | Meaning | Thrown By |
|------|-------------|---------|-----------|
| `BOOK_UNAVAILABLE` | 400 | The selected book is not available for borrowing | `BorrowRecordService` |
| `ALREADY_RETURNED` | 400 | The borrow record has already been returned | `BorrowRecordService` |
| `CONFLICT` | 409 | ISBN already exists, username already in use, or book has borrow history | `BookService`, `StudentService`, `LibrarianService` |
| `NOT_FOUND` | 404 | Requested entity does not exist | All services |
| `VALIDATION_ERROR` | 400 | Bean Validation failure on one or more fields | `GlobalExceptionHandler` |
| `UNAUTHORIZED` | 401 | No session or invalid credentials | `RestAuthenticationEntryPoint` |
| `FORBIDDEN` | 403 | Insufficient role for the requested resource | `RestAccessDeniedHandler` |

### Field Errors

When `code` is `VALIDATION_ERROR`, the `fieldErrors` array contains:

```json
{
  "fieldErrors": [
    { "field": "email", "message": "Invalid email address." },
    { "field": "password", "message": "Password must be between 8 and 100 characters." }
  ]
}
```

The application does not return stack traces, database details, or sensitive values. `server.error.include-message=never` prevents Spring Boot from leaking error details.
