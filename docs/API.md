# API reference

Libris exposes a JSON REST API under `/api`. This guide summarizes route
families visible in the current controllers; request DTO validation and role
rules in source are authoritative when this summary is incomplete. Responses
can change with application revisions.

## Authentication and public routes

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/api/auth/csrf` | Bootstrap CSRF cookie/token flow |
| `POST` | `/api/auth/login` | Authenticate a username/password session |
| `POST` | `/api/auth/register` | Register a student account |
| `POST` | `/api/auth/logout` | Invalidate the current session |
| `GET` | `/api/auth/me` | Return current authenticated identity |
| `GET` | `/api/config` | Return client-facing configuration |

Registration is public but only creates student accounts. Login and other
state-changing requests use the same-origin session and CSRF flow.

## Resource routes

All routes below are under `/api`. Access is controlled by URL-pattern rules
in `SecurityConfig`; this table is a route index, not a permission grant.

| Route | Operations exposed by controller |
|---|---|
| `/books` | List/search, get by ID, create, update, delete |
| `/magazines` | List/search, get by ID, create, update, delete |
| `/newspapers` | List/search, get by ID, create, update, delete |
| `/students` | List/search, get by ID, create, update, delete |
| `/librarians` | List/search, get by ID, create, update, delete |
| `/borrow-records` | List, create borrow record, return by ID |
| `/borrow-records/my` | List current student's records |
| `/profile` | Read current user's profile |
| `/dashboard` | General dashboard summary |
| `/student/dashboard` | Student dashboard |
| `/librarian/dashboard` | Librarian dashboard |
| `/analytics/dashboard`, `/trends`, `/top-books`, `/top-readers`, `/overdue` | Analytics summaries |
| `/audit`, `/audit/{id}` | List audit records and read one by ID |
| `/reports/inventory`, `/borrowing`, `/overdue`, `/students` | CSV report routes |

List routes may accept search or pagination parameters. Exact parameter names,
default page sizes, validation constraints, and response wrappers are defined
by each controller, DTO, and service; inspect those before building a client.

## Errors and validation

Errors use the application's structured API error response. Bean-validation
failures can include a `fieldErrors` object; the registration UI maps known
fields to inline messages and keeps a general fallback for unexpected
responses. Clients should tolerate absent or malformed optional error details.

## CSRF, sessions, and operations

The browser obtains the CSRF cookie from `GET /api/auth/csrf` and sends its
value in `X-XSRF-TOKEN` for unsafe methods. The browser also retains the
session cookie. Never put token values in logs or reports.

The production profile exposes Actuator health only and disables SpringDoc
OpenAPI. Do not rely on base-profile exposure as the production contract.
