# Current project state and evidence boundaries

This document summarizes the checked-in implementation. It is not a live
production monitor. Operational details below are explicitly qualified where
they come from earlier operator reports rather than a check in this task.

## Source-level state

- Backend: Java 21, Spring Boot 3.5, Spring MVC, Spring Security, Spring Data
  JPA, and Flyway for the MySQL schema.
- Frontend: static multi-page HTML/CSS and vanilla JavaScript ES modules,
  served by Spring Boot. The supplied canonical mark is under
  `backend/src/main/resources/static/assets/brand/`.
- Data: MySQL in the Compose deployment and H2 in isolated development/tests.
- Main catalog and circulation capabilities include books, magazines,
  newspapers, student/librarian profiles, borrow/return records, dashboards,
  analytics, reports, and audit records.
- Registration creates student accounts and returns backend validation
  errors; the frontend maps recognized field errors to the registration form.
- Book availability is a property of a book row. The schema has no separate
  copy/inventory-item entity.
- An explicitly invoked Node tool under `scripts/catalog-seed/` manages an
  illustrative catalog through authenticated APIs. It is not a startup seed.

See [API](API.md), [database](DATABASE.md), [security](SECURITY.md),
[testing](TESTING.md), and [catalog seed](CATALOG_SEED.md) for details.

## Deployment evidence (historical operator reports)

Earlier operator reports described `https://libris.opik.net` on Oracle Linux
9.8 / OCI, Caddy terminating HTTPS, the app loopback-published on
`127.0.0.1:8080`, and MySQL on a named Compose volume without a host port.
They also reported a Dynu IPv4 updater timer. None of these facts was
re-queried for this documentation task; treat them as potentially stale and
verify before an operational change. The reported application image digest
does not establish a source Git revision. The Dockerfile currently embeds no
Git SHA, so exact production source provenance is unknown.

The earlier operator report said the 20 illustrative rows (14 books, 3
magazines, 3 newspapers) were visible in production. This is historical
verification, not a current catalog query or a claim about real physical
inventory.

## Test evidence

The repository defines separate Maven, Python, and Node test suites; see
[TESTING.md](TESTING.md) and `.github/workflows/ci.yml`. Older documents record
test counts from their own dates. Those counts are not current run results.
