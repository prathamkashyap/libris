# Local Development Setup

This guide explains how to run the Libris (Library Management System) locally.

## Prerequisites

- **Java 21+** (required by Spring Boot 3.5)
- **Maven** (wrapper included: `./mvnw`)
- **MySQL 8.0+** running locally or via Docker

---

## 1. Configure Environment Variables

Copy the example file and edit values for your environment:

```bash
cp .env.example .env
```

Edit `.env` and set the required values:

| Variable | Description | Required |
|----------|-------------|----------|
| `LMS_DB_URL` | JDBC URL for MySQL | No (has default) |
| `LMS_DB_USERNAME` | Database username | No (default: `root`) |
| `LMS_DB_PASSWORD` | Database password | **Yes** |
| `LMS_ADMIN_PASSWORD` | Initial admin password | **Required on first startup if no admin exists** |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID | No (for OAuth login) |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret | No (for OAuth login) |

### Example `.env`

```properties
# Database (required)
LMS_DB_PASSWORD=your_mysql_root_password

# Optional - defaults work for standard local MySQL
# LMS_DB_URL=jdbc:mysql://localhost:3306/librarydb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
# LMS_DB_USERNAME=root

# Admin password - required on first startup if no admin exists
# LMS_ADMIN_PASSWORD=your_strong_admin_password

# Optional - Google OAuth2
# GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com
# GOOGLE_CLIENT_SECRET=xxx
```

---

## 2. Start MySQL

If you don't have MySQL running locally, use Docker:

```bash
docker run -d \
  --name lms-mysql \
  -e MYSQL_ROOT_PASSWORD=your_mysql_root_password \
  -e MYSQL_DATABASE=librarydb \
  -p 3306:3306 \
  mysql:8.0
```

> **Note**: The `LMS_DB_PASSWORD` in your `.env` must match `MYSQL_ROOT_PASSWORD` above.

---

## 3. Run the Application

```bash
cd backend
./mvnw spring-boot:run
```

The application will start at **http://localhost:8080**

---

## 4. Verify It Works

1. Open http://localhost:8080
2. Login with the admin user:
   - **Username**: `admin`
   - **Password**: the value you set in `LMS_ADMIN_PASSWORD`

> **Important**: The admin account is created automatically on first startup if no admin exists. **In production, always set `LMS_ADMIN_PASSWORD` to a strong random value before first startup.** The password is hashed with BCrypt before being stored in the database. If an administrator already exists, the seeder does nothing.

---

## 5. Run Tests

```bash
cd backend
./mvnw test
```

---

## Common Issues

### "Public Key Retrieval is not allowed"

Ensure your JDBC URL includes `allowPublicKeyRetrieval=true` (already in the default).

### "Access denied for user 'root'@'localhost'"

Check that `LMS_DB_PASSWORD` in `.env` matches your MySQL root password.

### Port 8080 already in use

Change the port in `.env` or `application.properties`:

```properties
server.port=8081
```

### Tests fail with OAuth2 errors

Tests use dummy values in `src/test/resources/application.properties`. No action needed.

---

## Environment Variable Reference

All configuration is via environment variables (or `.env` file via Spring Boot's `ConfigDataImport`).

| Property | Env Var | Default |
|----------|---------|---------|
| `spring.datasource.url` | `LMS_DB_URL` | `jdbc:mysql://localhost:3306/librarydb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `spring.datasource.username` | `LMS_DB_USERNAME` | `root` |
| `spring.datasource.password` | `LMS_DB_PASSWORD` | *(required)* |
| `lms.admin.password` | `LMS_ADMIN_PASSWORD` | *(required on first startup if no admin exists)* |
| `spring.security.oauth2.client.registration.google.client-id` | `GOOGLE_CLIENT_ID` | *(optional)* |
| `spring.security.oauth2.client.registration.google.client-secret` | `GOOGLE_CLIENT_SECRET` | *(optional)* |

---

## Initial Admin Account Bootstrap

The application uses a secure bootstrap mechanism for the initial administrator:

- **Username**: `admin` (configurable via `LMS_ADMIN_USERNAME` in future)
- **Password**: Read from `LMS_ADMIN_PASSWORD` environment variable
- **Storage**: Password is hashed with BCrypt before being stored in the database
- **Behavior**: 
  - If an admin account already exists, the seeder does nothing
  - If no admin exists and `LMS_ADMIN_PASSWORD` is set, the admin is created
  - If no admin exists and `LMS_ADMIN_PASSWORD` is not set, the application will fail to start with a clear error message
- **Security**: No default credentials are documented or hardcoded. The password is only ever provided via environment variable.