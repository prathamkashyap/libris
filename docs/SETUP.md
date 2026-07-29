# Setup

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
| `LMS_DB_PASSWORD` | **Yes** | — | MySQL password |
| `LMS_ADMIN_PASSWORD` | **Yes** | — | Initial admin password (required on first startup if no admin exists) |
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
export LMS_ADMIN_PASSWORD=ChangeMe123!
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

The H2 profile uses an in-memory database with `ddl-auto=create-drop`. You must set `LMS_ADMIN_PASSWORD` or the application will fail on startup.

---

## Admin Bootstrap

The application uses a `CommandLineRunner` (`AdminSeeder`) to create the initial administrator:

- **Username:** `admin` (hardcoded)
- **Password:** Read from `LMS_ADMIN_PASSWORD` configuration property
- **Behavior:**
  - If an `admin` account already exists, the seeder does nothing.
  - If no admin exists and `LMS_ADMIN_PASSWORD` is set, the admin is created with BCrypt-hashed password.
  - If no admin exists and `LMS_ADMIN_PASSWORD` is not set or blank, the application fails with `IllegalStateException`.
- **No hardcoded default credentials.** The admin password must always be provided via configuration.

---

## Swagger UI

Once running, explore the API interactively at <http://localhost:8080/swagger-ui/index.html>.

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

The frontend requests `GET /api/auth/csrf` on page load, receives a readable `XSRF-TOKEN` cookie, and forwards it as `X-XSRF-TOKEN` for state-changing Fetch requests. Authentication is session-based (`JSESSIONID` cookie); no JWT is used in v1.0.0. See [SECURITY.md](SECURITY.md) for the full security model.
