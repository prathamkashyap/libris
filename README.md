# Libris

![Libris mark](backend/src/main/resources/static/assets/brand/libris-mark.svg)

Libris is a library-management web application for maintaining a catalog, managing student and librarian accounts, and recording circulation. It combines a Java 21 / Spring Boot application, MySQL persistence, and a multi-page HTML, CSS, and vanilla JavaScript frontend served by Spring Boot.

The project includes session login and logout, public student registration, role-based access for `ADMIN`, `LIBRARIAN`, and `STUDENT`, books/magazines/newspapers, search and pagination, borrow/return workflows, dashboards, analytics, CSV reports, audit events, CSRF protection, and Flyway migrations. Catalog availability is stored as a Boolean on each catalog row. There is no separate physical-copy model, so availability does not represent multiple independently borrowable copies.

The public demo is [libris.opik.net](https://libris.opik.net). Operator reports say that an OCI deployment is running there; this repository does not currently embed build provenance sufficient to prove that the live image was built from a particular Git commit. The starter catalog is illustrative sample data, not verified physical inventory.

## Technology

- Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA
- MySQL 8.4 for the Compose stack; H2 for tests and the local `h2` profile
- Flyway migrations (`V1`–`V5` in the current source tree)
- Static multi-page frontend with native JavaScript ES modules
- Docker Compose and GitHub Actions

The canonical mark is provided at `backend/src/main/resources/static/assets/brand/libris-mark.svg` and `.png`.

## Run locally

Requirements: Java 21. The Maven wrapper is included. From the repository root:

```sh
./mvnw spotless:check
./mvnw clean verify
```

For an isolated H2 run, set `LMS_ADMIN_PASSWORD` in your local shell using a non-echoing prompt or secret manager before starting the app. Do not use production credentials or put a password literal in shell history:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

The app listens on port 8080 by default. The H2 profile is in-memory and uses `create-drop`; its data is temporary. See [docs/SETUP.md](docs/SETUP.md) for Docker/MySQL setup and environment variables. Production sessions use Secure cookies, so a plain-HTTP local run is suitable for development checks but not a production-like sign-in test.

## Tests and checks

```sh
./mvnw spotless:check
./mvnw clean verify
python3 -m unittest discover -s scripts/dev-seed -p 'test_phase5*.py'
node --test scripts/catalog-seed/*.test.mjs
```

The Maven, Python, and catalog-tooling suites are separate. Passing one does not imply the others passed. CI runs Maven formatting/verification, Phase 5 Python tests, and build-only Docker checks for `linux/amd64` and `linux/arm64`; CI does not publish or deploy an image.

## Repository guide

- `backend/` — Java application, migrations, static frontend, and tests
- `scripts/dev-seed/` — synthetic development data and offline forecasting experiments
- `scripts/dev-realdata/` — real-data readiness tooling and methodology; not a production ML service
- `scripts/catalog-seed/` — explicitly invoked authenticated catalog importer; see [docs/CATALOG_SEED.md](docs/CATALOG_SEED.md)
- `docs/` — setup, API, architecture, security, database, testing, and operational guides
- `Dockerfile`, `docker-compose.yml` — the supported container stack

Start with [docs/SETUP.md](docs/SETUP.md), then consult [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the operator-reported OCI/Caddy/Dynu deployment model. Deployment details in that guide are not a live check.

## Limitations

- A book row and its `available` flag do not model copy-level inventory.
- The starter catalog contains clearly labeled illustrative entries; it is not a verified collection or news feed.
- Public registration creates student accounts. Email verification and CAPTCHA are not part of the current registration flow; operators should assess abuse controls before using it for unrestricted public enrollment.
- The synthetic forecasting work is exploratory/offline and is not integrated into the application.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Keep changes focused, update relevant documentation, and run the checks applicable to the change.

## License

See [LICENSE](LICENSE).
