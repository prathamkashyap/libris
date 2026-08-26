# Release Notes - v1.0.1

**Release Date:** 2026-08-10

We are excited to announce Libris v1.0.1, a major stabilization and polish release that transitions the system into a production-ready state. This release addresses technical debt, refines the architecture, and resolves security regressions.

## Highlights

### Frontend Architecture Modernization
- **Shell Extraction:** The frontend shell (sidebar, topbar, command palette) has been fully extracted into modular HTML partials (`/components/*.html`).
- **Monolith Decomposition:** The monolithic `main.js` has been decomposed into page-specific ES modules (e.g., `books.js`, `dashboard.js`), improving load times and maintainability.
- **Dynamic Mounting:** Hardcoded navigation and shell components across 15+ pages have been replaced with dynamic mount points (`#rail`, `#topbar-root`, `#palette-root`).

### Security & Authentication
- **Opt-In OAuth:** Google OpenID Connect is now strictly opt-in via the `oauth` Spring profile, preventing startup failures when credentials are not configured.
- **Safe Identity Linking:** `CustomOidcUserService` now mandates a verified email address from Google before linking to a local library account. Automatic role assignment and account creation via OAuth have been removed for security.
- **Session Security:** Enforced strict CSRF protection via `SpaCsrfTokenRequestHandler` (sending the token in headers) across all endpoints.

### Repository Health & Production Readiness
- **Dependency Hygiene:** Cleaned up unused Maven dependencies. The `pom.xml` is now perfectly lean.
- **Structured Logging:** Integrated `logstash-logback-encoder` to provide structured JSON logging in production, facilitating aggregation in tools like ELK/Datadog.
- **Migration Safety:** Added `V2__student_profile_email_unique.sql` to enforce email uniqueness at the database level, which is a prerequisite for safe OAuth identity linking.
- **Clean Workspace:** Removed stale assets, mock data scripts, and MacOS system artifacts (`.DS_Store`) from the version control index.

## Upgrade Instructions

If you are upgrading from `v1.0.0`:
1. **Database:** Ensure there are no duplicate emails in the `student_profiles` table before starting the application, as the V2 Flyway migration will enforce a unique constraint.
2. **Environment Variables:** If you were relying on implicit OAuth, you must now explicitly enable the `oauth` profile and provide `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.

## What's Next?
Phase E (v1.1) will focus on new feature development, including:
- Self-registration workflows.
- Notifications & Reservations.
- Fine management system.
