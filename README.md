<div align="center">
  <img src="backend/src/main/resources/static/assets/library-mark.svg" alt="Libris Logo" width="120" />
  <h1>Libris — Library Management System</h1>
  <p><strong>A modern, responsive, and robust library management platform built with Spring Boot 3.5 and Vanilla ES Modules.</strong></p>

  [![Build Status](https://img.shields.io/github/actions/workflow/status/prathamkashyap/library-management-system/ci.yml?branch=main&style=for-the-badge&logo=github)](https://github.com/prathamkashyap/library-management-system/actions)
  [![Coverage](https://img.shields.io/badge/coverage-70%25%2B-success?style=for-the-badge)](https://github.com/prathamkashyap/library-management-system)
  [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)
  [![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=spring-boot)](https://spring.io/projects/spring-boot)
  [![Java](https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=java)](https://openjdk.org/projects/jdk/21/)

</div>

---

Libris is a comprehensive Library Management System designed for educational institutions. It features a secure REST API powered by Java 21 and Spring Boot, paired with an elegant, dual-theme frontend (Ember Dark / Verdigris Light) built entirely without heavy SPA frameworks.

## ✨ Key Features

| Feature Area | Description |
|---|---|
| **Secure Identity** | Session-based authentication (BCrypt) + Opt-in **Google OpenID Connect (SSO)**. |
| **Role-Based Access** | Strictly enforced permissions for `ADMIN`, `LIBRARIAN`, and `STUDENT` across all endpoints. |
| **Complete Cataloging** | Track Books, Magazines, and Newspapers with ISBN validation, book categories/genres, and availability locks. |
| **Borrowing Workflow** | Automated checkout, 14-day due date defaults, real-time overdue tracking, and return history. |
| **Interactive API Docs** | Auto-generated **Swagger UI** at `/swagger-ui.html` (OpenAPI 3.0) — browse and test all endpoints live. |
| **Analytics & Reports** | Dashboard analytics, CSV exports (inventory and borrowing history with due dates). |
| **Modern UX/UI** | Fast, vanilla ES Modules frontend with dynamic shell, command palette, custom CSS themes, and responsive design. |
| **Audit & Observability** | JPA auditing (`created_at`/`updated_at`), **Structured JSON Logging** for ELK/Datadog, Actuator health/metrics. |
| **Production Ready** | Docker Compose, Flyway versioned migrations, CSRF protection, Railway/Render 1-click deployment configs. |

## 🏗️ Architecture Overview

```mermaid
flowchart LR
    Browser["Browser UI<br/>(Vanilla JS / ES Modules)"] --> Fetch["Fetch API<br/>(w/ CSRF Token)"]
    Fetch --> Controller["REST Controllers<br/>(/api/**)"]
    Controller --> Service["Transactional Services<br/>(@Service)"]
    Service --> Repository["Spring Data JPA<br/>(@Repository)"]
    Repository --> Database[("MySQL 8<br/>(Flyway Migrated)")]
```

## 🚀 Quick Start

### Option A — Docker Compose (Local)

```bash
git clone https://github.com/prathamkashyap/library-management-system.git
cd library-management-system/backend
cp .env.example .env
# Edit .env: set LMS_DB_PASSWORD and LMS_ADMIN_PASSWORD
docker compose up --build
```

Navigate to <http://localhost:8080>. Log in with `admin` / your `LMS_ADMIN_PASSWORD`.  
Swagger UI: <http://localhost:8080/swagger-ui.html>

### Option B — H2 In-Memory (No Docker)

```bash
export LMS_ADMIN_PASSWORD=ChangeMe123!
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2 -f backend/pom.xml
```

### Option C — Deploy to Railway (Recommended)

1. Go to [railway.app](https://railway.app) → **New Project** → **Deploy from GitHub repo** → select this repo.
2. Add a **MySQL** database plugin.
3. Set env vars: `LMS_DB_URL`, `LMS_DB_USERNAME`, `LMS_DB_PASSWORD`, `LMS_ADMIN_PASSWORD`, `SPRING_PROFILES_ACTIVE=docker`.
4. Railway auto-detects `railway.json` and the multi-stage `Dockerfile`.

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for Render, Fly.io, and Hugging Face Spaces guides.

## 📋 API Documentation

| Endpoint | Description |
|---|---|
| `/swagger-ui.html` | Interactive Swagger UI (try any endpoint live — no login required) |
| `/v3/api-docs` | Raw OpenAPI 3.0 JSON spec |
| `/actuator/health` | Health check probe |

## 🧪 Running Tests

```bash
./mvnw clean verify -f backend/pom.xml
```

- **20 tests** across 4 test classes (integration, CSRF flow, repository, unit)
- H2 in-memory — no MySQL or extra env vars needed
- JaCoCo enforces **≥ 70% line coverage**
- Spotless enforces **Google Java Format**

## 📚 Documentation

| Doc | Contents |
|---|---|
| [API.md](docs/API.md) | All endpoints, request/response shapes, error codes |
| [DATABASE.md](docs/DATABASE.md) | ER diagram, table schemas, Flyway migration log |
| [SECURITY.md](docs/SECURITY.md) | CSRF, session, OAuth2 OIDC, role matrix |
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | Railway, Render, Fly.io, Hugging Face, Docker Compose |
| [FRONTEND.md](docs/FRONTEND.md) | ES module architecture, component injection, theming |
| [CURRENT_STATE.md](docs/CURRENT_STATE.md) | Live test counts, coverage, feature inventory |

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.


</div>

---

Libris is a comprehensive Library Management System designed for educational institutions. It features a secure REST API powered by Java 21 and Spring Boot, paired with an elegant, dual-theme frontend (Ember Dark / Verdigris Light) built entirely without heavy SPA frameworks.

## ✨ Key Features

| Feature Area | Description |
|---|---|
| **Secure Identity** | Session-based authentication (BCrypt) + Opt-in **Google OpenID Connect (SSO)**. |
| **Role-Based Access** | Strictly enforced permissions for `ADMIN`, `LIBRARIAN`, and `STUDENT` across controllers and UI. |
| **Complete Cataloging** | Track and manage Books, Magazines, and Newspapers with ISBN validation and availability locks. |
| **Borrowing Workflow** | Automated checkout limits, return history tracking, and overdue analytics. |
| **Modern UX/UI** | Fast, vanilla ES Modules frontend with dynamic shell, custom CSS properties, and responsive design. |
| **Audit & Observability** | Automatic JPA auditing (`created_at` / `updated_at`) and **Structured JSON Logging** for ELK/Datadog. |
| **Production Ready** | Robust Docker Compose setup, Flyway migrations, CSRF protection, and Actuator metrics. |

## 🏗️ Architecture Overview

```mermaid
flowchart LR
    Browser["Browser UI<br/>(Vanilla JS / ES Modules)"] --> Fetch["Fetch API<br/>(w/ CSRF Token)"]
    Fetch --> Controller["REST Controllers<br/>(@RestController)"]
    Controller --> Service["Transactional Services<br/>(@Service)"]
    Service --> Repository["Spring Data JPA<br/>(@Repository)"]
    Repository --> Database[("MySQL 8<br/>(Flyway Migrated)")]
```

For an in-depth architectural breakdown, including ADRs and schema definitions, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## 🚀 Quick Start (Docker)

The recommended way to run Libris locally is via Docker. Ensure you have [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) installed.

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/libris.git
   cd libris/backend
   ```
2. **Configure Environment:**
   ```bash
   cp .env.example .env
   # Edit .env and set secure values for LMS_DB_PASSWORD and LMS_ADMIN_PASSWORD
   ```
3. **Launch the stack:**
   ```bash
   docker compose up --build
   ```

Navigate to `http://localhost:8080`. Log in using the username `admin` and the password you defined in `.env`.

*Note: To run without Docker using the embedded H2 database, run `./mvnw spring-boot:run -Dspring-boot.run.profiles=h2`.*

## 📸 Screenshots

| Dashboard (Ember Theme) | Catalog (Verdigris Theme) |
|---|---|
| *![Dashboard Mockup](dashboard_mockup.png)* | *![Catalog Mockup](catalog_mockup.png)* |
| **Analytics Overview** | **Student Management** |
| *![Analytics Mockup](analytics_mockup.png)* | *![Student Mockup](student_mockup.png)* |

*(Full evidence in `screenshots/desktop` and `screenshots/mobile`)*

## 📚 Documentation

Dive deeper into the system's technical details:

- [API Contract](docs/API.md): Request/response schemas and error codes.
- [Database Setup](docs/DATABASE.md): ER diagram and migration strategies.
- [Frontend Architecture](docs/FRONTEND.md): Component injection and module boundaries.
- [Security Model](docs/SECURITY.md): CSRF flow, OAuth implementation, and role matrices.
- [Production Readiness](docs/PRODUCTION_READINESS.md): Audit of deployment readiness, logging, and dependency hygiene.
- [Release Notes](docs/RELEASE_NOTES_v1.0.1.md): Latest changes in `v1.0.1`.

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details on our code of conduct, development environment setup, and the process for submitting Pull Requests.

To run the test suite locally:
```bash
./mvnw clean verify
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
