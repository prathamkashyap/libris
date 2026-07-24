# Library Management System API Contract

**Status:** v1.0.0 release contract  
**Base path:** `/api`  
**Format:** JSON request/response bodies; dates use `yyyy-MM-dd`.

This contract is derived from the frozen architecture document. It is updated before implementation changes reach frontend consumers.

## Conventions

- `GET` reads, `POST` creates or invokes a domain action, `PUT` updates, and `DELETE` removes an eligible resource.
- Successful creation returns `201 Created`; successful reads/updates return `200 OK`; successful delete and return actions return `204 No Content`.
- `400` represents validation or business-rule errors; `401` no authenticated session; `403` insufficient role; `404` missing resource; `409` uniqueness, protected deletion, or state conflict.
- Responses never include raw passwords, password hashes, or JPA entity internals.
- API clients must safely handle `204` without attempting to parse a JSON body.

## Authentication and profile

| Method | Path | Authority | Request | Success response |
| --- | --- | --- | --- | --- |
| POST | `/auth/login` | Public | `username`, `password` | `200` safe authenticated-user data; session established. |
| POST | `/auth/logout` | Authenticated | None | `204`; session invalidated. |
| GET | `/auth/me` | Authenticated | None | `200` account id, username, role, display name. |
| GET | `/profile` | Authenticated | None | `200` current non-sensitive profile. |

## Dashboard

| Method | Path | Authority | Success response |
| --- | --- | --- | --- |
| GET | `/dashboard` | ADMIN, LIBRARIAN | `200` with `totalStudents`, `totalLibrarians`, `totalBooks`, `borrowedBooks`, `availableBooks`. |

## Books

| Method | Path | Authority | Request | Success response |
| --- | --- | --- | --- | --- |
| GET | `/books?search={text}` | Authenticated | Optional search query | `200` book array. |
| GET | `/books/{id}` | Authenticated | None | `200` book. |
| POST | `/books` | ADMIN, LIBRARIAN | title, author, isbn, publishedDate | `201` created book; `409` with `ISBN already exists.` for a duplicate ISBN. |
| PUT | `/books/{id}` | ADMIN, LIBRARIAN | title, author, isbn, publishedDate | `200` updated book; `409` for a duplicate ISBN. |
| DELETE | `/books/{id}` | ADMIN, LIBRARIAN | None | `204`, or `409` if history prevents deletion. |

`BookResponse`: `id`, `title`, `author`, `isbn`, `publishedDate`, `available`.

## Students and librarians

| Resource | Methods | Authority | Create fields | Response fields |
| --- | --- | --- | --- | --- |
| `/students` | GET, GET `/{id}`, POST, PUT `/{id}`, DELETE `/{id}` | ADMIN, LIBRARIAN | username, password, name, email, phone | id, accountId, username, name, email, phone, role. |
| `/librarians` | GET, GET `/{id}`, POST, PUT `/{id}`, DELETE `/{id}` | ADMIN | username, password, name, age, phone | id, accountId, username, name, age, phone, role. |

Update requests omit immutable username and include only permitted mutable fields. Password change/reset is not inferred from ordinary profile update; it requires an explicit future contract if added.

## Borrow records

| Method | Path | Authority | Request | Success response |
| --- | --- | --- | --- | --- |
| GET | `/borrow-records?status={BORROWED|RETURNED}` | ADMIN/LIBRARIAN; student scope later | Optional status | `200` record array. |
| POST | `/borrow-records` | ADMIN, LIBRARIAN | bookId, studentId, borrowerName, borrowerEmail, borrowerPhone, borrowDate | `201` created record. |
| POST | `/borrow-records/{id}/return` | ADMIN, LIBRARIAN | None | `204`; return date recorded and book restored to available. |

`BorrowRecordResponse`: `id`, `bookId`, `bookTitle`, `studentId`, `borrowerName`, `borrowerEmail`, `borrowerPhone`, `borrowDate`, `returnDate`, `status`.

## Error response

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

Validation errors populate `fieldErrors` with `field` and `message`; for example, an invalid email returns `Invalid email address.` for the affected email field. The application does not return stack traces, database details, or sensitive values.
