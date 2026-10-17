# Local setup

Libris requires Java 21. The Maven wrapper is included, so a global Maven installation is not required. Docker Engine with the Compose v2 plugin is needed for the bundled MySQL stack.

## H2 development profile

From the repository root, set `LMS_ADMIN_PASSWORD` locally using a non-echoing prompt or a secret manager, then start Spring Boot. This guide intentionally does not include a password literal or a copy-paste assignment that could enter shell history.

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

The `h2` profile uses an in-memory database and `create-drop`; its data disappears when the process exits. The password is used by the startup `AdminSeeder`. Do not reuse production credentials or place a real password in source control.

## Docker Compose with MySQL

The supported Compose file is at the repository root. Create a private `.env` from the template, set the required values privately, and start the stack:

```sh
cp .env.example .env
chmod 600 .env
# Edit .env locally; do not display it or include it in support output.
docker compose up -d --build
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health
```

Compose requires `LMS_DB_ROOT_PASSWORD`, `LMS_DB_PASSWORD`, and `LMS_ADMIN_PASSWORD`. The app connects with the dedicated database account, not MySQL root. The root Compose stack also publishes optional phpMyAdmin only when its `dev` profile is enabled; do not enable that profile on a public host without separately reviewing its exposure.

The app is published on `127.0.0.1:8080` by default. MySQL is available to the app on the Compose network and has no host port mapping. In the `prod` profile, Secure session cookies require HTTPS for a usable browser login; local HTTP is suitable for development smoke checks, not production-like session testing.

## Environment configuration

| Variable | Purpose |
|---|---|
| `LMS_DB_ROOT_PASSWORD` | MySQL container bootstrap/health check |
| `LMS_DB_PASSWORD` | Libris MySQL account password |
| `LMS_ADMIN_PASSWORD` | Password seeded for the configured admin account |
| `LMS_ADMIN_USERNAME` | Optional admin username; default `admin` |
| `LMS_DB_NAME` | Optional database name; Compose default `librarydb` |
| `LMS_DB_USERNAME` | Optional app database account; Compose default `libris` |
| `LMS_DB_URL` | Optional JDBC URL; Compose supplies the internal MySQL URL by default |
| `LMS_BIND_ADDRESS` | Host bind address; default `127.0.0.1` |
| `JAVA_OPTS` | JVM options, including heap sizing |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Optional OAuth client configuration; verify OAuth profile requirements before enabling |

The base properties retain a legacy admin-password fallback for non-production configuration, but `AdminSeeder` rejects that known value. The `prod` profile requires `LMS_ADMIN_PASSWORD` with no fallback. Never check configuration by printing resolved Compose output or environment variables; verify required values privately and only report presence/absence.

## Database and migrations

MySQL uses the named Compose volume `mysql-data`. Ordinary `docker compose down` preserves named volumes; `docker compose down -v` deletes them and is destructive. Do not run it as a routine reset or troubleshooting step.

Production schema is managed by Flyway migrations under `backend/src/main/resources/db/migration/`; current files are V1 through V5. Add a new migration rather than editing an applied migration. H2 development/tests use `create-drop` and do not exercise all MySQL-specific behavior; the migration integration test uses Flyway-enabled H2.

## API, login, and docs

Open the application at <http://localhost:8080>. SpringDoc is disabled by default in the base configuration; enablement is profile/configuration dependent. The production profile disables Swagger/OpenAPI and exposes only Actuator health.

The frontend bootstraps CSRF with `GET /api/auth/csrf`, then sends `X-XSRF-TOKEN` on state-changing requests. Authentication is session based. See [SECURITY.md](SECURITY.md), [API.md](API.md), and [TESTING.md](TESTING.md).

## Developer checks

```sh
./mvnw spotless:check
./mvnw clean test
./mvnw clean verify
python3 -m unittest discover -s scripts/dev-seed -p 'test_phase5*.py'
node --test scripts/catalog-seed/*.test.mjs
```

These are separate suites; report exactly which commands were run and passed.
