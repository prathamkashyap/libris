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

## Protected route families

Controllers provide routes for books, magazines, newspapers, students,
librarians, borrow records, profiles, dashboards, analytics, audit records,
and reports. Definitions are in
`backend/src/main/java/com/example/lms/controller/`; `SecurityConfig` defines
authorization by URL pattern. Do not infer permissions from this list alone.

Borrow-record routes include listing, the current student's records, creation,
and return. Dashboard and analytics controllers expose separate staff/student
views. Some list routes accept search or pagination parameters; consult the
corresponding controller and DTO for exact parameter names and response shape.

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
