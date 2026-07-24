# Local Setup

## Prerequisites

- Java 21 or later
- MySQL 8+ or MySQL-compatible local instance
- A database user able to create and update `librarydb`

## Configuration

The application reads these optional environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `LMS_DB_URL` | `jdbc:mysql://localhost:3306/librarydb?...` | MySQL JDBC URL |
| `LMS_DB_USERNAME` | `root` | MySQL user |
| `LMS_DB_PASSWORD` | local development default | MySQL password |

Do not commit local credentials. Prefer environment variables or an ignored local properties file.

## Run

From the repository root:

```bash
./mvnw spring-boot:run
```

Open <http://localhost:8080>. Hibernate creates/updates the development schema. The local seed account is `admin` / `ChangeMe123!`; replace it before deploying outside local development.

## CSRF and Sessions

The frontend requests `/api/auth/csrf`, receives a readable `XSRF-TOKEN` cookie, and forwards it as `X-XSRF-TOKEN` for state-changing Fetch requests. Authentication is session-based; no JWT is used in v1.0.0.
