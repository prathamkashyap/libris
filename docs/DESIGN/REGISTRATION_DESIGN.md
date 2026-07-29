# Self-Service Registration — Design Document

**Status:** Design only — implementation deferred to post-stabilization.
**Scope:** New feature, out of current v1.1 cleanup engagement.

---

## 1. Role Assignment

Self-registration creates **`STUDENT` accounts only**.

| Role | Self-Registration | Rationale |
|------|-------------------|-----------|
| `ADMIN` | No | Bootstrapped by `AdminSeeder`. There should never be more than 1-2 admins. |
| `LIBRARIAN` | No | Created by admins via `POST /api/librarians` (admin-only endpoint). Librarians are trusted staff. |
| `STUDENT` | **Yes** | Primary consumers of the library. Low-privilege role. No approval needed. |

---

## 2. Endpoint Design

```
POST /api/auth/register
```

### Request DTO: `RegisterRequest`

```java
public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 50)  String username,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Size(max = 100)          String name,
    @NotBlank @Email                     String email,
    @NotBlank @Size(max = 20)           String phone
) {}
```

### Validation Rules

| Field | Rules | Uniqueness |
|-------|-------|------------|
| `username` | 3-50 chars, `@NotBlank` | Unique across `accounts.username` |
| `password` | 8-100 chars, `@NotBlank` | N/A (hashed by `PasswordEncoder`) |
| `name` | 1-100 chars, `@NotBlank` | N/A |
| `email` | Valid email format, `@NotBlank` | Unique across `student_profiles.email` |
| `phone` | 1-20 chars, `@NotBlank` | N/A |

### Response

`201 Created` with `StudentResponse` body (same shape as `POST /api/students`).

### CSRF Handling

Same as login — requires `X-XSRF-TOKEN` header from the `XSRF-TOKEN` cookie. The endpoint is `permitAll()` so unauthenticated users can call it, but CSRF protection still applies.

---

## 3. Database Constraint

Add to `Flyway V1__initial_schema.sql`:

```sql
ALTER TABLE student_profiles ADD CONSTRAINT uq_student_profiles_email UNIQUE (email);
```

This constraint does not exist in the current Hibernate-managed schema. It must be added as part of the Flyway baseline migration, not now.

**Note:** The current codebase has no email uniqueness check at the database level. `StudentService.create()` does not check for duplicate emails — only duplicate usernames. This is a pre-existing gap that the registration feature would expose.

---

## 4. SecurityConfig Change

Add to the `permitAll()` matcher list:

```java
.requestMatchers("/api/auth/register").permitAll()
```

### Security Implications

| Concern | Mitigation |
|---------|-----------|
| Open endpoint allows mass account creation | Rate limiting (not in scope now, but recommended for production) |
| CSRF protection | Required — `SpaCsrfTokenRequestHandler` applies to all POST endpoints |
| Role escalation | Endpoint only creates `STUDENT` role. No way to specify `LIBRARIAN` or `ADMIN` via this endpoint. |
| Username enumeration | Returns `409 CONFLICT` with "Username is already in use." — same message for all conflicts. Does not reveal whether an account exists. |
| Email enumeration | Returns `409 CONFLICT` with "Email is already registered." — acceptable for self-service registration. |
| Password storage | Hashed by `BCryptPasswordEncoder` via `PasswordEncoder` bean — same as existing flow. |
| Session creation | If auto-login is implemented (Option A), a session is created immediately — same lifecycle as `POST /api/auth/login`. |

---

## 5. Post-Registration Behavior — Two Options

### Option A: Auto-Login After Registration

After successful registration, automatically authenticate the user and return an `AuthenticatedUserResponse` (same as `POST /api/auth/login`).

**Flow:**
1. `POST /api/auth/register` → creates Account + StudentProfile
2. Call `AuthenticationManager.authenticate()` with the new credentials
3. Create session via `SecurityContextHolder` + `HttpSession`
4. Publish `AuditAction.CREATE` event for the student
5. Publish `AuditAction.LOGIN` event
6. Return `AuthenticatedUserResponse` with `201 Created`

**Pros:**
- Better UX — user immediately lands in the app
- Consistent with how login works
- No extra round-trip

**Cons:**
- Endpoint does two things (register + login) — slightly more complex
- Session is created during registration — if something fails between save and session creation, the account exists but the user sees an error

### Option B: Register → Redirect to Login

After successful registration, return `201 Created` with a message, then the frontend redirects to `login.html`.

**Flow:**
1. `POST /api/auth/register` → creates Account + StudentProfile
2. Publish `AuditAction.CREATE` event
3. Return `201 Created` with `{"message": "Registration successful. Please sign in."}`
4. Frontend redirects to `/login.html`
5. User logs in normally via `POST /api/auth/login`

**Pros:**
- Simpler implementation — single responsibility endpoint
- No session management complexity
- Clear separation of concerns

**Cons:**
- Extra step for the user (must type credentials again)
- Worse UX — user just typed their password and has to type it again

---

## 6. Files to Create/Modify

| File | Change | New/Modified |
|------|--------|-------------|
| `RegisterRequest.java` | New DTO record | **New** |
| `RegisterService.java` | Registration + optional auto-login logic | **New** |
| `AuthController.java` | Add `POST /api/auth/register` endpoint | Modified |
| `SecurityConfig.java` | Add `/api/auth/register` to `permitAll()` | Modified |
| `StudentProfileRepository.java` | Add `existsByEmail()` for uniqueness check | Modified |
| `register.html` | Replace dead-end with real registration form | Modified |
| `js/api/auth-api.js` | Add `register()` method | Modified |

---

## 7. Error Responses

| Condition | HTTP Status | Code | Message |
|-----------|-------------|------|---------|
| Duplicate username | `409` | `CONFLICT` | "Username is already in use." |
| Duplicate email | `409` | `CONFLICT` | "Email is already registered." |
| Validation failure | `400` | `VALIDATION_ERROR` | "Request validation failed." + field errors |
| Missing CSRF token | `403` | `FORBIDDEN` | (handled by `SpaCsrfTokenRequestHandler`) |

---

## 8. Audit Events

| Event | Action | Entity Type | Description |
|-------|--------|-------------|-------------|
| Registration | `CREATE` | `STUDENT` | "Student self-registered: {name}" |
| Auto-login (Option A) | `LOGIN` | `ACCOUNT` | "User logged in: {username}" |

---

## 9. Implementation Order

This feature should be implemented **after** the following stabilization work:

1. Flyway baseline migration (with `UNIQUE(email)` constraint)
2. Correctness fixes (`StudentDashboardController` full-scan, `BorrowRequest` unused fields)
3. Expanded integration test coverage
4. Low-risk dedup (`itemTitle()` utility, configurable overdue-days)
5. Documentation updates

Then as its own commit on `feature/v1.1-analytics-reports-docker`.
