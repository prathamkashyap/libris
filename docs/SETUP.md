# Setup 🚀

> **Local development** • **Docker** • **Environment configuration**

How to run the Library Management System locally and with Docker.

See [ARCHITECTURE.md](ARCHITECTURE.md) for system context and [TESTING.md](TESTING.md) for running tests.

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java | 21+ | Required by Spring Boot 3.5 |
| Maven | (wrapper included) | Use `./mvnw` — no global install needed |
| MySQL | 8.0+ | For production profile |
| Docker | (optional) | For Docker Compose setup |

---

## Environment Variables

All configuration is via environment variables (or a `.env` file via Spring Boot's `ConfigDataImport`).

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `LMS_DB_URL` | No | `jdbc:mysql://localhost:3306/librarydb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | MySQL JDBC URL |
| `LMS_DB_USERNAME` | No | `root` | MySQL username |
| `LMS_DB_PASSWORD` | Prod / Docker | — | MySQL password (not needed for H2) |
| `LMS_ADMIN_PASSWORD` | Prod / Docker | — | Admin password; must be set to a strong value (main config throws `IllegalStateException` if blank/null) |
| `GOOGLE_CLIENT_ID` | No | — | Google OAuth2 client ID (for OAuth login) |
| `GOOGLE_CLIENT_SECRET` | No | — | Google OAuth2 client secret |

### Example `.env`

```properties
# Database (required)
LMS_DB_PASSWORD=your_mysql_root_password

# Admin password (required on first startup)
LMS_ADMIN_PASSWORD=your_strong_admin_password

# Optional - defaults work for standard local MySQL
# LMS_DB_URL=jdbc:mysql://localhost:3306/librarydb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
# LMS_DB_USERNAME=root

# Optional - Google OAuth2
# GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com
# GOOGLE_CLIENT_SECRET=xxx
```

---

## Docker Compose (Recommended)

Prerequisites: [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/).

```bash
cd backend
cp .env.example .env
# Edit .env — set LMS_DB_PASSWORD and LMS_ADMIN_PASSWORD
docker compose up --build
```

Open <http://localhost:8080>. Login with `admin` / your `LMS_ADMIN_PASSWORD`.

To include phpMyAdmin (port 8081):

```bash
docker compose --profile dev up --build
```

---

## Local Development with MySQL

### 1. Start MySQL

If you don't have MySQL running locally, use Docker:

```bash
docker run -d \
  --name lms-mysql \
  -e MYSQL_ROOT_PASSWORD=your_mysql_root_password \
  -e MYSQL_DATABASE=librarydb \
  -p 3306:3306 \
  mysql:8.0
```

The `LMS_DB_PASSWORD` in your `.env` must match `MYSQL_ROOT_PASSWORD`.

### 2. Configure environment

```bash
cd backend
cp .env.example .env
# Edit .env — set LMS_DB_PASSWORD and LMS_ADMIN_PASSWORD
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

Open <http://localhost:8080>. Login with `admin` / your `LMS_ADMIN_PASSWORD`.

---

## Local Development with H2 (No MySQL)

For quick development without MySQL:

```bash
cd backend
export LMS_ADMIN_PASSWORD=YourStrongPassword123!
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

The H2 profile uses an in-memory database with `ddl-auto=create-drop`. The main `application.properties` no longer provides a default admin password — you must set `LMS_ADMIN_PASSWORD` for the application to start.

---

## Admin Bootstrap

The application uses a `CommandLineRunner` (`AdminSeeder`) to create the initial administrator:

- **Username:** `admin` (hardcoded via `lms.admin.username`)
- **Password:** Read from `lms.admin.password` configuration property (main config defaults to `ChangeMe123!`)
- **Behavior:**
  - If an `admin` account already exists and the password matches, no change is made.
  - If an `admin` account exists but the password differs, it is updated.
  - If no `admin` exists, a new account is created with `ROLE_ADMIN`.
  - If the resolved password is null or blank, the application fails with `IllegalStateException`.
- **Production:** Always set `LMS_ADMIN_PASSWORD` to a strong value. The `ChangeMe123!` default is for local development only.

---

## Swagger UI

Once running, explore the API interactively at <http://localhost:8080/swagger-ui.html>. Swagger is public when SpringDoc is enabled; the `prod` profile disables it.

---

## Common Issues

### "Public key retrieval is not allowed"

Ensure your JDBC URL includes `allowPublicKeyRetrieval=true` (already in the default).

### "Access denied for user 'root'@'localhost'"

Check that `LMS_DB_PASSWORD` matches your MySQL root password.

### Port 8080 already in use

Change the port in `application.properties`:

```properties
server.port=8081
```

### Tests fail with OAuth2 errors

Tests use dummy values in `src/test/resources/application.properties`. No action needed.

---

## CSRF and Sessions

The frontend requests `GET /api/auth/csrf` on page load, receives a readable `XSRF-TOKEN` cookie, and forwards it as `X-XSRF-TOKEN` for state-changing Fetch requests. Authentication is session-based (`JSESSIONID` cookie); no JWT is used. See [SECURITY.md](SECURITY.md) for the full security model.
