# Troubleshooting Libris

Use this guide for current development and deployment diagnostics. Never print passwords, environment values, cookies, session IDs, CSRF tokens, authorization headers, password hashes, or unfiltered logs. Do not use `docker compose config` as a secret-checking command because it can render resolved values.

## Check application and database health

From the directory containing the root `docker-compose.yml`:

```sh
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health
```

In the `prod` profile only the Actuator health endpoint is exposed, with health details hidden. For more context, inspect a narrow, sanitized log window:

```sh
docker compose logs --since=10m --tail=200 app
docker compose logs --since=10m --tail=200 mysql
```

Review locally before sharing logs; redact personal data and credentials.

## Startup or datasource failures

- Confirm the Compose services report healthy and the app uses the expected profile. Do not print environment values.
- The `prod` profile requires `LMS_DB_URL`, `LMS_DB_PASSWORD`, and `LMS_ADMIN_PASSWORD`; Compose supplies the JDBC URL and requires secrets through its configured inputs.
- `AdminSeeder` rejects an unset/blank password and the legacy built-in default. If startup fails at seeding, verify the secret source privately; do not place its value in a command, issue, or log.
- MySQL initialization variables create users and databases only when the data directory is first initialized. Changing `.env` later does not rotate an existing database account. Plan credential changes deliberately; do not reset the database as a troubleshooting shortcut.
- Flyway migration failures require inspecting the failed version and database backup state before action. Never edit an applied migration or use a destructive volume reset to bypass a mismatch.

## Sign-in, session, and CSRF

The browser flow is session based. It bootstraps `GET /api/auth/csrf`, sends the `XSRF-TOKEN` cookie as `X-XSRF-TOKEN` on state-changing requests, and retains `JSESSIONID`. Use browser Network tools to inspect method, status, and sanitized response shape; redact cookie and token values.

In production, Secure session cookies require HTTPS. If a sign-in request succeeds but a later protected request is unauthenticated, check whether the browser retained cookies and whether the TLS proxy forwards the original host/protocol correctly. Do not disable CSRF or Secure cookies to mask the issue.

For login failures, make at most a controlled attempt and inspect sanitized authentication events. Do not repeatedly retry an account that may be subject to in-memory failure tracking.

## Public endpoint surface

The production configuration disables SpringDoc and exposes only `/actuator/health` through Actuator. The security chain permits `/actuator/**`, so exposure is controlled by Actuator configuration as well as authorization. Do not assume development profile behavior matches production.

## Frontend issues

Static frontend files are served from `backend/src/main/resources/static/`. The canonical shared stylesheet is `styles.css`; page behavior is in ES modules below `js/`. For registration validation feedback, the frontend maps backend `fieldErrors` to fields and keeps a general message for unmapped errors. Verify the actual response schema before changing UI error handling.

## Historical notes

Older incidents about MySQL 9 authentication and earlier theme iterations have been removed from this current runbook because their configuration and UI details are not authoritative for the current tree. Use Git history if those historical investigations are needed; verify any old workaround against current dependencies before applying it.
