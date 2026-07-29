# Deployment

How to build, containerize, and deploy the Library Management System.

See [SETUP.md](SETUP.md) for local development and [SECURITY.md](SECURITY.md) for security configuration.

---

## Docker

### Multi-stage Dockerfile

The Dockerfile uses a multi-stage build for a small production image:

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Image size:** The runtime stage uses `eclipse-temurin:21-jre` (not full JDK), keeping the production image small.

### Docker Compose

```yaml
services:
  mysql:
    image: mysql:9
    environment:
      MYSQL_ROOT_PASSWORD: ${LMS_DB_PASSWORD}
      MYSQL_DATABASE: librarydb
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build: .
    environment:
      SPRING_PROFILES_ACTIVE: docker
      LMS_DB_PASSWORD: ${LMS_DB_PASSWORD}
      LMS_ADMIN_PASSWORD: ${LMS_ADMIN_PASSWORD}
    ports:
      - "8080:8080"
    depends_on:
      mysql:
        condition: service_healthy

  phpmyadmin:            # Optional (profile: dev)
    image: phpmyadmin:latest
    environment:
      PMA_HOST: mysql
      PMA_USER: root
      PMA_PASSWORD: ${LMS_DB_PASSWORD}
    ports:
      - "8081:80"
    profiles:
      - dev

volumes:
  mysql-data:
```

### Quick start

```bash
cd backend
cp .env.example .env
# Edit .env — set LMS_DB_PASSWORD and LMS_ADMIN_PASSWORD
docker compose up --build
```

Open <http://localhost:8080>.

### With phpMyAdmin

```bash
docker compose --profile dev up --build
```

phpMyAdmin available at <http://localhost:8081>.

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `LMS_DB_PASSWORD` | **Yes** | MySQL root password (also used for `MYSQL_ROOT_PASSWORD` in Compose) |
| `LMS_ADMIN_PASSWORD` | **Yes** | Initial admin password (BCrypt-hashed on first startup) |
| `SPRING_PROFILES_ACTIVE` | For Docker | Set to `docker` in Compose; `h2` for H2 profile |
| `GOOGLE_CLIENT_ID` | No | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | No | Google OAuth2 client secret |

---

## Health Checks

### MySQL

Docker Compose includes a health check:

```yaml
healthcheck:
  test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
  interval: 10s
  timeout: 5s
  retries: 5
```

The `backend` service waits for MySQL to be healthy before starting (`condition: service_healthy`).

### Application

The application does not expose a dedicated health endpoint. Spring Boot Actuator is not included in the current dependencies. For production, consider adding:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

This would provide `/actuator/health` for load balancer probes.

---

## CI/CD

### GitHub Actions

```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: temurin
          cache: maven
      - name: Build and test
        run: mvn clean verify
        working-directory: backend
```

**Trigger:** Push or PR to `main`.  
**Action:** Compiles the project and runs all tests with `mvn clean verify`.  
**No deployment step:** CI verifies correctness only; deployment is manual.

---

## Production Considerations

| Concern | Current State | Recommendation |
|---------|---------------|----------------|
| Schema management | `ddl-auto=update` | Adopt Flyway or Liquibase for versioned migrations |
| Health checks | None exposed | Add Spring Boot Actuator |
| Logging | Default Spring Boot logging | Configure structured logging (JSON) for production |
| HTTPS | Not configured | Add reverse proxy (nginx, Caddy) or configure TLS in Spring Boot |
| Session persistence | In-memory (single instance) | Fine for single-instance; for clustering, use Spring Session with Redis/JDBC |
| Database backups | Not automated | Schedule `mysqldump` or use cloud-managed backups |
| Resource limits | Not configured | Set JVM heap (`-Xmx`), container memory limits |
| Graceful shutdown | Not configured | Add `server.shutdown=graceful` for zero-downtime deploys |

---

## Build Commands

| Command | Purpose |
|---------|---------|
| `./mvnw clean package -DskipTests` | Build JAR without tests |
| `./mvnw clean verify` | Build and run all tests (CI command) |
| `docker compose build` | Build Docker images |
| `docker compose up --build` | Build and start all services |
| `docker compose down -v` | Stop and remove volumes (data loss) |
