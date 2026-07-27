# Spring Boot MySQL Authentication Issue

## Summary

The project fails to connect to MySQL 9.0.1 from Spring Boot via HikariCP, despite the same credentials working from the MySQL CLI.

### Error

```
Access denied for user 'root'@'localhost' (using password: YES)
```

Hibernate subsequently reports:

```
Unable to determine Dialect without JDBC metadata
```

The application exits before any `CommandLineRunner` executes.

---

## Root Cause

MySQL 9.0.1 uses `caching_sha2_password` as the default authentication plugin. The MySQL Connector/J (9.x) handles this plugin correctly, but two conditions must be met for non-SSL connections:

1. **`allowPublicKeyRetrieval=true`** — allows the JDBC driver to request the server's RSA public key (already in the URL)
2. **`connectionTimeZone=SERVER` or `connectionTimeZone=UTC`** — MySQL 8.0.30+ and MySQL 9.x require an explicit timezone in the JDBC URL during the authentication handshake. Without it, the driver may fail with a misleading "Access denied" error even with correct credentials.

The CLI (`mysql -u root -p`) works because the native client handles the timezone implicitly during its own handshake protocol.

---

## Fix Applied

Added `&connectionTimeZone=UTC` to the JDBC URL:

```
spring.datasource.url=${LMS_DB_URL:jdbc:mysql://localhost:3306/librarydb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectionTimeZone=UTC}
```

If the connection still fails, try the alternatives below.

---

## Alternatives if Connection Still Fails

### 1. Verify the environment variable

The password is read from `LMS_DB_PASSWORD`. Ensure it is set correctly with no trailing whitespace:

```bash
echo ":$LMS_DB_PASSWORD:"  # should show the password between colons
```

### 2. Create a dedicated MySQL user (recommended over root)

```sql
CREATE USER 'lms'@'localhost' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON librarydb.* TO 'lms'@'localhost';
FLUSH PRIVILEGES;
```

Then set `LMS_DB_USERNAME=lms` and `LMS_DB_PASSWORD=your_secure_password`.

### 3. Use `mysql_native_password` (MySQL 9.0 only, removed in 9.1+)

If MySQL 9.0.1 still supports `mysql_native_password`:

```sql
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'your_password';
FLUSH PRIVILEGES;
```

### 4. Switch to H2 for development

An H2 in-memory profile is provided:

```bash
# Run with H2 instead of MySQL
SPRING_PROFILES_ACTIVE=h2 LMS_ADMIN_PASSWORD=admin123 mvn spring-boot:run
```

Or set the env var:

```bash
export SPRING_PROFILES_ACTIVE=h2
export LMS_ADMIN_PASSWORD=admin123
mvn spring-boot:run
```

The H2 profile uses MySQL-compatible mode and is configured in `application-h2.properties`.

---

## Environment Details

| Component | Version |
|-----------|---------|
| MySQL Server | 9.0.1 |
| MySQL Connector/J | 9.x (managed by Spring Boot 3.5.0) |
| Spring Boot | 3.5.0 |
| Java | 21 |
| Auth plugin | `caching_sha2_password` (MySQL 9 default) |
| JDBC URL params | `createDatabaseIfNotExist=true`, `useSSL=false`, `allowPublicKeyRetrieval=true`, `serverTimezone=UTC`, `connectionTimeZone=UTC` |

---

## Verification Steps

1. Start MySQL: `mysql.server start`
2. Ensure `librarydb` database exists: `mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS librarydb;"`
3. Run the app: `mvn spring-boot:run`
4. If it still fails, try with H2: `SPRING_PROFILES_ACTIVE=h2 LMS_ADMIN_PASSWORD=admin123 mvn spring-boot:run`
