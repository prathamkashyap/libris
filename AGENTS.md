# Libris contributor and agent instructions

Libris is a Java 21 / Spring Boot 3.5 application with a MySQL database and a static HTML/CSS/vanilla JavaScript ES-module frontend served by Spring Boot. The supported Compose files are at the repository root.

## Build and test

Run Maven commands from the repository root using the included wrapper:

```sh
./mvnw spotless:check
./mvnw clean test
./mvnw clean verify
./mvnw test -Dtest=LibraryManagementIntegrationTest
```

`clean verify` runs formatting/build/test checks and the configured JaCoCo gate. Run the Python Phase 5 tests separately with `python3 -m unittest discover -s scripts/dev-seed -p 'test_phase5*.py'`. The catalog importer has independent Node tests: `node --test scripts/catalog-seed/*.test.mjs`. Do not describe a focused suite as a full-suite result.

CI has three jobs: Maven Spotless/verify, the stdlib-only Phase 5 Python tests, and build-only Docker checks for `linux/amd64` and `linux/arm64`. The Docker job does not publish or deploy an image. CI's dual-architecture check is a portability requirement; prior operator evidence identifies the OCI production VM as x86_64, not Ampere ARM64. Verify current deployment details before relying on historical reports.

## Configuration and profiles

Important environment variable names:

| Name | Use |
|---|---|
| `LMS_DB_ROOT_PASSWORD` | Compose MySQL bootstrap and health check |
| `LMS_DB_PASSWORD` | Application's least-privilege MySQL account |
| `LMS_ADMIN_PASSWORD` | AdminSeeder credential; required in `prod` |
| `LMS_DB_URL` | JDBC URL; required in `prod`, supplied by Compose |
| `LMS_ADMIN_USERNAME` | Optional admin username; defaults to `admin` |

Never print resolved Compose configuration or environment values to verify secrets. The base configuration has a legacy default admin password that `AdminSeeder` explicitly rejects. `prod` has no password fallback. Set a strong, unique value through a protected local environment or `.env`; never document an actual password as a usable default.

- `h2`: in-memory development database, `create-drop`, Flyway disabled.
- default/Compose: MySQL and Flyway migrations.
- `prod`: requires explicit datasource/admin settings, uses Secure/SameSite=Lax session cookies, trusts forwarded headers, disables SpringDoc, and exposes Actuator health only.
- `oauth`: optional Google OIDC configuration; verify current security/service code before documenting account-linking behavior.

For schema changes, add a new versioned SQL migration under `backend/src/main/resources/db/migration/`. Do not edit an already applied migration or rely on Hibernate DDL for production schema changes.

## Architecture and security conventions

- Frontend static files live in `backend/src/main/resources/static/`.
- Controllers accept/return DTOs; services own domain rules and transactions; repositories own persistence queries.
- Roles are `ADMIN`, `LIBRARIAN`, and `STUDENT`; URL authorization is configured in `SecurityConfig`.
- Session authentication uses BCrypt and CSRF. The browser obtains the CSRF cookie from `GET /api/auth/csrf` and sends `X-XSRF-TOKEN` for state-changing requests.
- Do not disable CSRF, expose protected routes, weaken Secure cookies, or log passwords, password hashes, session IDs, cookies, CSRF tokens, authorization headers, or secret environment values.
- The production Compose app binds to host loopback at `127.0.0.1:8080`; MySQL has no host-published port. Do not expose 8080 or 3306 publicly.
- Catalog import is an explicit operator CLI, not application startup behavior. Follow `docs/CATALOG_SEED.md`; production writes require separate authorization.

## Deployment boundary

The repository contains the root `Dockerfile` and `docker-compose.yml`. The operator-reported OCI deployment uses Linux AMD64, Caddy as the host reverse proxy, and Dynu for IPv4 updates; this is historical operational evidence, not automatically proven current state. See `docs/DEPLOYMENT.md`. Do not build on a memory-constrained production host. For application updates, preserve the named MySQL volume and recreate only the `app` service; image rollback does not reverse schema migrations or application data changes.

## Git and documentation

Follow `CONTRIBUTING.md`, including the policy against automated-agent co-author trailers and generated tool attribution. Preserve legitimate human co-author attribution and accurate Git metadata. Keep user changes and untracked files intact; stage explicit paths only. Update documentation when behavior changes and distinguish source-backed behavior from runtime/deployment evidence.
