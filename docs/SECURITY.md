# Security model and operational notes

This page describes source-level behavior. It is not a penetration-test report
or proof that a particular deployment has the same configuration.

## Authentication and authorization

Spring Security uses server-side sessions and BCrypt password hashing. Roles
are `ADMIN`, `LIBRARIAN`, and `STUDENT`; `SecurityConfig` applies access rules
to URL patterns. Public registration creates student accounts. `AdminSeeder`
provisions the configured administrator and synchronizes the existing admin
password hash during startup. The production profile requires
`LMS_ADMIN_PASSWORD`; the seeder rejects the legacy built-in default. Never
treat sample/local configuration as a production credential.

## CSRF, cookies, and HTTPS

State-changing browser requests require CSRF protection. The frontend obtains
the CSRF cookie through `/api/auth/csrf` and submits its token in
`X-XSRF-TOKEN`; authentication uses a session cookie. The `prod` profile sets
Secure and SameSite=Lax session-cookie behavior and supports forwarded headers
for TLS termination. Browser login should be tested over HTTPS.

## Public and management endpoints

Public routes include login, CSRF bootstrap, student registration, static
resources, and client-facing configuration. Review the config DTO before
adding fields to `/api/config`. In production, only Actuator health is exposed,
details are hidden, and SpringDoc is disabled. Do not infer production
exposure from base-profile properties.

## Known risks and limits

- Public self-registration has no verified email-confirmation or CAPTCHA flow.
- Login failure tracking is in-memory, not a durable distributed rate limiter.
- Do not assume a password reset or email recovery workflow exists without
  checking the current implementation.
- Public deployment requires HTTPS, appropriately narrow OCI ingress, and
  host firewall rules. Keep 8080 and 3306 private and avoid exposing unrelated
  host services.

Never log passwords, password hashes, session IDs, CSRF tokens, cookies,
authorization headers, or secret-bearing environment output. Use narrow,
sanitized diagnostics.
