# Spring Boot MySQL Authentication Issue

## Summary

The project currently fails to start because Spring Boot cannot authenticate to MySQL during datasource initialization.

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

## Recent Changes

- Added admin bootstrap using `LMS_ADMIN_PASSWORD`
- Moved datasource configuration to environment variables
- Changed authentication to username-based login
- Removed hardcoded administrator password

---

## Verified

### MySQL

- MySQL Community Server 9.0.1
- MySQL Connector/J 9.2.0
- `librarydb` exists
- All required tables exist

### Manual Connection

The following works successfully:

```bash
mysql -h localhost -P 3306 -u root -p
```

Database queries execute normally.

### Authentication Plugin

```sql
SELECT user, host, plugin
FROM mysql.user
WHERE user='root';
```

Result:

```
root | localhost | caching_sha2_password
```

### Environment Variables

Verified:

```
LMS_DB_URL
LMS_DB_USERNAME
LMS_DB_PASSWORD
LMS_ADMIN_PASSWORD
```

Maven also resolves these correctly.

### Configuration Checks

Verified:

- No duplicate datasource configuration
- No profile-specific application.properties
- No custom DataSource bean
- No additional datasource configuration classes
- `PasswordConfig` only provides BCryptPasswordEncoder
- `AdminSeeder` is not responsible because startup fails before it executes
- Hardcoding the datasource password produces the same error

---

## Current Blocker

Spring Boot/HikariCP cannot authenticate to MySQL although the same credentials work successfully using the MySQL CLI.

The root cause is still unknown.

Potential investigation areas:

- Spring Boot datasource auto-configuration
- JDBC URL resolution
- HikariCP configuration
- MySQL Connector/J
- Runtime differences between JDBC and the MySQL CLI