# Security Knowledge Base — Library Management System (Project A)

> Engineering reference document. Derived from source code and [SECURITY.md](SECURITY.md).
> **Last verified:** 01 Aug 2026

---

## 1. Authentication Architecture

### Mechanism

| Component | File | Detail |
|-----------|------|--------|
| Password encoder | `config/PasswordConfig.java` | `BCryptPasswordEncoder` bean, default strength |
| UserDetailsService | `security/AccountUserDetailsService.java` | Loads `Account` from DB, maps to Spring Security `User` with `ROLE_` prefix; throws `UsernameNotFoundException` on miss |
| Authentication provider | `security/SecurityConfig.java:41-46` | `DaoAuthenticationProvider` wired with the UserDetailsService and BCrypt encoder |
| Authentication manager | `security/SecurityConfig.java:34-38` | Obtained from `AuthenticationConfiguration.getAuthenticationManager()` |
| Login endpoint | `POST /api/auth/login` | `AuthenticationManager.authenticate()` → creates `SecurityContext` → stores in HTTP session |
| OAuth2 login | `security/SecurityConfig.java:98` | `oauth2Login()` with `defaultSuccessUrl("/")`; Google client configured via `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` env vars |

### Login Flow

```
Browser  ──POST /api/auth/login {username, password}──►  AuthController
    AuthController  ──►  AuthService.login(credentials)
        AuthService  ──►  AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken)
            AuthManager  ──returns──►  Authentication
        AuthService  ──►  SecurityContextHolder → HTTP session
    AuthController  ──returns──►  AuthenticatedUserResponse {accountId, username, role, displayName}
    Browser  ◄──  200 OK + JSESSIONID cookie (HttpOnly)
```

### Logout Flow

1. `POST /api/auth/logout` with `X-XSRF-TOKEN` header.
2. `AuthService.logout()` invalidates the HTTP session and clears `SecurityContextHolder`.
3. Frontend nulls `currentUser` and redirects to login.

### OAuth2 Configuration

- Client: Google OAuth2.
- Client ID/Secret sourced from environment variables (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`).
- `defaultSuccessUrl("/")` — after successful Google login, user lands on the root page.
- No custom `TokenEndpoint` or `UserInfoEndpoint` overrides.

---

## 2. Authorization Model

### Role Definitions

| Role | Capabilities |
|------|-------------|
| **ADMIN** | Full system access. Creates/manages librarian accounts. Manages books, students, borrow records, dashboard. Only role seeded at startup. |
| **LIBRARIAN** | Manages books, students, and borrow records. Views dashboard and analytics. Cannot manage librarian accounts. |
| **STUDENT** | Views books (GET), own profile, and own borrow records. Cannot manage any records or access admin/librarian dashboards. |

### URL-Pattern Authorization (SecurityConfig)

Rules are evaluated in declaration order in `SecurityConfig.security()` (`SecurityConfig.java:48-101`):

| Priority | Pattern | Method | Access |
|----------|---------|--------|--------|
| 1 | `/api/auth/login`, `/api/auth/csrf`, static assets, `/login.html`, `/register.html` | * | `permitAll()` |
| 2 | `/api/auth/logout`, `/api/auth/me`, `/api/profile` | * | `authenticated()` |
| 3 | `/api/student/**` | * | ADMIN, LIBRARIAN, STUDENT |
| 4 | `/api/books/**`, `/api/magazines/**`, `/api/newspapers/**` | GET | `authenticated()` (any role) |
| 5 | `/api/librarians/**` | * | ADMIN only |
| 6 | `/api/analytics/**`, `/api/audit/**`, `/api/reports/**` | * | ADMIN, LIBRARIAN |
| 7 | `/api/books/**`, `/api/students/**`, `/api/borrow-records/**`, `/api/dashboard` | * | ADMIN, LIBRARIAN |
| 8 | `/api/librarian/dashboard` | * | ADMIN, LIBRARIAN |
| 9 | `anyRequest()` | * | `authenticated()` |

**Critical rule ordering:** Rule 4 (GET on books/magazines/newspapers) appears before Rule 7 (all methods on books), so STUDENTs can GET resources but POST/PUT/DELETE are blocked.

### Permissions Matrix

| Resource | Method | ADMIN | LIBRARIAN | STUDENT | Unauthenticated |
|----------|--------|:-----:|:---------:|:-------:|:---------------:|
| `/api/auth/csrf` | GET | ✅ | ✅ | ✅ | ✅ |
| `/api/auth/login` | POST | ✅ | ✅ | ✅ | ✅ |
| `/api/auth/me` | GET | ✅ | ✅ | ✅ | 401 |
| `/api/auth/logout` | POST | ✅ | ✅ | ✅ | 401 |
| `/api/books` | GET | ✅ | ✅ | ✅ | 401 |
| `/api/books` | POST | ✅ | ✅ | 403 | 401 |
| `/api/books/{id}` | PUT | ✅ | ✅ | 403 | 401 |
| `/api/books/{id}` | DELETE | ✅ | ✅ | 403 | 401 |
| `/api/magazines` | GET | ✅ | ✅ | ✅ | 401 |
| `/api/magazines` | POST | ✅ | ✅ | 403 | 401 |
| `/api/newspapers` | GET | ✅ | ✅ | ✅ | 401 |
| `/api/newspapers` | POST | ✅ | ✅ | 403 | 401 |
| `/api/students` | * | ✅ | ✅ | 403 | 401 |
| `/api/librarians` | * | ✅ | 403 | 403 | 401 |
| `/api/borrow-records` | GET | ✅ | ✅ | 403 | 401 |
| `/api/borrow-records/my` | GET | ✅ | ✅ | ✅ | 401 |
| `/api/borrow-records` | POST | ✅ | ✅ | 403 | 401 |
| `/api/borrow-records/{id}/return` | POST | ✅ | ✅ | 403 | 401 |
| `/api/dashboard` | GET | ✅ | ✅ | 403 | 401 |
| `/api/student/dashboard` | GET | ✅ | ✅ | ✅ | 401 |
| `/api/librarian/dashboard` | GET | ✅ | ✅ | 403 | 401 |
| `/api/analytics/*` | * | ✅ | ✅ | 403 | 401 |
| `/api/audit/*` | * | ✅ | 403 | 403 | 401 |
| `/api/reports/*` | * | ✅ | ✅ | 403 | 401 |
| `/api/profile` | GET | ✅ | ✅ | ✅ | 401 |

### No Method-Level Security

No `@PreAuthorize`, `@Secured`, or `@RolesAllowed` annotations are used. Authorization is purely URL-pattern-based.

---

## 3. CSRF Protection

### Implementation

| Aspect | Detail |
|--------|--------|
| Token repository | `CookieCsrfTokenRepository.withHttpOnlyFalse()` — `XSRF-TOKEN` cookie readable by JavaScript |
| SPA handler | `SpaCsrfTokenRequestHandler` (`security/SpaCsrfTokenRequestHandler.java:17-33`) |
| Frontend usage | `http.js` reads `XSRF-TOKEN` cookie, attaches `X-XSRF-TOKEN` header on non-GET requests |
| Bootstrap endpoint | `GET /api/auth/csrf` — triggers cookie generation; called on page load |

### SpaCsrfTokenRequestHandler Dual-Path Logic

The handler at `security/SpaCsrfTokenRequestHandler.java:30-33` uses a `RequestHeaderRequestMatcher("X-XSRF-TOKEN")` to detect SPA requests:

- **SPA request** (has `X-XSRF-TOKEN` header): resolves the raw (non-XOR-encoded) token via `CsrfTokenRequestAttributeHandler`. This allows SPAs to read and echo the cookie value directly.
- **Form submission** (no `X-XSRF-TOKEN` header): falls through to `XorCsrfTokenRequestAttributeHandler`, retaining Spring Security's XOR protection against BREACH attacks.

Both paths call `xor.handle()` in `handle()` (line 25) to ensure the CSRF token is bound to the response.

### Why CSRF Is Not Disabled

Session cookies are automatically included by the browser on same-origin requests. Without CSRF protection, a malicious site could submit state-changing requests (borrow, delete, create) on behalf of an authenticated user. The `SpaCsrfTokenRequestHandler` provides SPA-compatible CSRF protection without disabling it.

---

## 4. Session Management

| Property | Value |
|----------|-------|
| Creation policy | `IF_REQUIRED` — sessions created on login, not eagerly (`SecurityConfig.java:57`) |
| Cookie | `JSESSIONID`, `HttpOnly`, SameSite follows container defaults (not explicitly configured) |
| Frontend usage | `credentials: 'include'` on all Fetch requests |
| Session invalidation | On `POST /api/auth/logout`, `AuthService.logout()` invalidates session and clears `SecurityContextHolder` |

### Session Lifecycle

1. **Creation:** `POST /api/auth/login` → `AuthenticationManager.authenticate()` → `SecurityContext` stored in HTTP session → `JSESSIONID` cookie set.
2. **Use:** Browser sends `JSESSIONID` on each request. Spring Security loads `SecurityContext` from session.
3. **Destruction:** `POST /api/auth/logout` → `session.invalidate()` → `SecurityContextHolder.clearContext()` → `JSESSIONID` cookie expired.

---

## 5. Password Security

### BCrypt

- `BCryptPasswordEncoder` bean defined in `config/PasswordConfig.java:10-12`.
- Default strength (10 rounds).
- Hash stored in `Account.passwordHash`. No plaintext password ever persisted.

### No Password Disclosure (AdminSeeder Fix)

`AdminSeeder.java` (`config/AdminSeeder.java:20-61`):
- Password is injected via `@Value("${lms.admin.password}")` — no hardcoded values.
- Logs only boolean state (`"Admin account exists: {}"`, `"Admin password already up to date"`, `"Creating new admin account"`).
- Previous version may have logged partial password hashes or raw passwords; current version logs no password material at any point.
- Throws `IllegalStateException` if password is null/blank: `"LMS_ADMIN_PASSWORD environment variable is required."`.
- Password is encoded via `passwordEncoder.encode(adminPassword)` before persistence.

### Validation

- `adminPassword == null || adminPassword.isBlank()` check at startup prevents seeding with empty credentials.
- `passwordEncoder.matches(adminPassword, admin.getPasswordHash())` used to compare existing hash (constant-time comparison handled by BCrypt).

---

## 6. Input Validation

### @Valid on Request DTOs

All mutating endpoints accept `@Valid`-annotated request DTOs. Jakarta Bean Validation annotations enforce field constraints.

### Field-Level Error Messages

When validation fails, `MethodArgumentNotValidException` is caught in `GlobalExceptionHandler.java:31-39`:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
ApiErrorResponse invalid(MethodArgumentNotValidException e, HttpServletRequest r) {
    var fields = e.getBindingResult().getFieldErrors().stream()
        .map(f -> new ApiErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
        .toList();
    return error(400, "VALIDATION_ERROR", "Request validation failed.", r, fields);
}
```

### ApiErrorResponse Shape

```java
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    List<FieldError> fieldErrors) {
  public record FieldError(String field, String message) {}
}
```

All error responses follow this shape — consistent JSON envelope, no HTML error pages.

---

## 7. Error Handling

### Global Exception Handler

`GlobalExceptionHandler.java` (`exception/GlobalExceptionHandler.java:12-49`) handles:

| Exception | HTTP Status | Code | Behavior |
|-----------|-------------|------|----------|
| `ResourceNotFoundException` | 404 | `NOT_FOUND` | Returns error with message |
| `ConflictException` | 409 | `CONFLICT` | Returns error with message |
| `BusinessRuleException` | 400 | `e.getCode()` | Returns error with custom code |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | Returns field-level errors |

### Security Exception Handlers

| Handler | HTTP Status | JSON Code | Message |
|---------|-------------|-----------|---------|
| `RestAuthenticationEntryPoint` | 401 | `UNAUTHORIZED` | `"Authentication is required."` |
| `RestAccessDeniedHandler` | 403 | `FORBIDDEN` | `"You do not have permission to perform this action."` |

Both return `ApiErrorResponse` JSON — no HTML error pages, no stack traces.

### Production Error Suppression

```properties
server.error.include-message=never
spring.jackson.default-property-inclusion=non_null
```

- `include-message=never`: Prevents Spring Boot from including exception messages in default `/error` responses.
- `non_null`: Null fields omitted from JSON responses (no empty arrays or null `code` fields).

---

## 8. OAuth2 Configuration

| Aspect | Detail |
|--------|--------|
| Provider | Google |
| Client ID | `GOOGLE_CLIENT_ID` env var |
| Client Secret | `GOOGLE_CLIENT_SECRET` env var |
| Default success URL | `/` (root page) |
| Configuration | `oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/"))` in `SecurityConfig.java:98` |
| Account linking | Not implemented — OAuth2 login creates or matches by email from Google token |

---

## 9. Security-Related Fixes This Session

| Fix | File | Detail |
|-----|------|--------|
| AdminSeeder password logging removed | `AdminSeeder.java` | No password material is logged. Only boolean state (`exists`, `matches`) and actions (`Creating new admin account`, `Updating admin password`) are logged. |
| Test password injection via @Value | `AdminSeeder.java:23-24` | `@Value("${lms.admin.password}")` injects password from environment, avoiding hardcoded secrets in source. Throws `IllegalStateException` if null/blank. |

---

## 10. Remaining Security Concerns

| Gap | Impact | Mitigation |
|-----|--------|------------|
| No rate limiting on `/api/auth/login` | Brute-force login attempts possible | Deferred to future scope; consider Spring Rate Limiter or a reverse proxy rate limiter |
| No account lockout | Repeated failed logins not throttled | Deferred to future scope |
| No HTTPS enforcement in code | Session cookies and credentials could be intercepted in transit | Relies on deployment infrastructure (load balancer, reverse proxy, or container TLS termination) |
| No password reset/change | Users cannot self-service password recovery | Deferred to future scope |
| No CORS configuration | Not needed for same-origin deployment; would be required if frontend served separately | Add if separate frontend server is used |
| `ddl-auto=update` in production | Schema changes applied without migration versioning | Adopt Flyway or Liquibase for production |
| No CSP / security headers in code | Relies on deployment (nginx, reverse proxy) for `Content-Security-Policy`, `X-Content-Type-Options`, etc. | Consider adding via Spring Security's `HttpHeaders` or reverse proxy config |
