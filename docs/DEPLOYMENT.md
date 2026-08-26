# Deployment Guide

Comprehensive instructions for containerizing and deploying the Libris Library Management System across cloud providers (Railway, Render, Fly.io, Hugging Face Spaces, and Docker VPS).

---

## 1. Architecture & Platform Overview

- **Stack:** Spring Boot 3.5 (Java 21) + Spring Security (CSRF + Session) + Spring Data JPA + Flyway migrations + vanilla JS MPA frontend.
- **Database:** MySQL 8+ in production (`ddl-auto=none`, Flyway versioned migrations). H2 in-memory for testing/development.
- **Dynamic Port:** Configured via `server.port=${PORT:8080}` to automatically bind to cloud platform port assignments.
- **Health Checks & Actuator:** Built-in Spring Boot Actuator at `/actuator/health` and `/actuator/info`.
- **API Documentation:** Interactive Swagger UI at `/swagger-ui.html` and OpenAPI 3 spec at `/v3/api-docs`.

---

## 2. Recommended Cloud Platforms

### Platform A: Railway (Recommended — Fastest & Easiest)

Railway natively provisions both the Spring Boot Docker container and a managed MySQL instance in the same project.

1. **Create a Railway Project:**
   - Go to [railway.app](https://railway.app) and click **New Project** → **Deploy from GitHub repo**.
   - Select the repository.
2. **Add a MySQL Database:**
   - Click **+ New** → **Database** → **Add MySQL**.
3. **Configure Environment Variables:**
   - In the application service **Variables** tab, set:
     - `LMS_DB_URL`: `${{MySQL.MYSQL_URL}}` (or `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`)
     - `LMS_DB_USERNAME`: `${{MySQL.MYSQLUSER}}`
     - `LMS_DB_PASSWORD`: `${{MySQL.MYSQLPASSWORD}}`
     - `LMS_ADMIN_PASSWORD`: `<your-secure-admin-password>`
     - `SPRING_PROFILES_ACTIVE`: `docker`
4. **Deploy:**
   - Railway uses `railway.json` and the multi-stage `Dockerfile` automatically.
   - Generate a public domain under service **Settings** → **Networking**.
   - Visit `https://<your-app>.up.railway.app` and `https://<your-app>.up.railway.app/swagger-ui.html`.

---

### Platform B: Render

Render deploys the Docker container as a Web Service and connects to a managed MySQL instance.

1. **Deploy with `render.yaml`:**
   - Push code to GitHub.
   - On [render.com](https://render.com), go to **Blueprints** → **New Blueprint Instance** and connect the repository.
2. **Manual Web Service:**
   - Create a **New Web Service** from your Git repository.
   - Choose **Docker** runtime.
   - Set environment variables:
     - `LMS_ADMIN_PASSWORD`: `<secure-password>`
     - `LMS_DB_URL`: `jdbc:mysql://<host>:<port>/<dbname>?createDatabaseIfNotExist=true&useSSL=false`
     - `LMS_DB_USERNAME`: `<db-user>`
     - `LMS_DB_PASSWORD`: `<db-password>`
     - `SPRING_PROFILES_ACTIVE`: `docker`
   - Health check path: `/actuator/health`.

---

### Platform C: Fly.io

1. Install Fly CLI: `curl -L https://fly.io/install.sh | sh`
2. Run `fly launch` in the repository root.
3. Attach MySQL database: `fly mysql create` and attach it.
4. Set secrets:
   ```bash
   fly secrets set LMS_ADMIN_PASSWORD="YourAdminPassword123!" LMS_DB_PASSWORD="your-db-password"
   ```
5. Deploy: `fly deploy`.

---

### Platform D: Hugging Face Spaces (Docker Space)

1. Create a new Space on [huggingface.co/spaces](https://huggingface.co/spaces) and choose **Docker** SDK.
2. Under Space **Settings** → **Variables and secrets**, add:
   - Secret `LMS_ADMIN_PASSWORD`: `<secure-password>`
   - Secret `LMS_DB_URL` / `LMS_DB_PASSWORD`: Remote MySQL URI (e.g. from Aiven, PlanetScale, or Supabase).
3. Push the repository to the Hugging Face Space git remote.

---

### Platform E: Self-Hosted Docker Compose (VPS / Server)

```bash
# 1. Clone repository
git clone https://github.com/prathamkashyap/library-management-system.git
cd library-management-system

# 2. Configure environment
cp .env.example .env
# Edit .env and set secure passwords:
# LMS_DB_PASSWORD=your_db_password
# LMS_ADMIN_PASSWORD=your_admin_password

# 3. Start stack with MySQL and App
docker compose up -d --build

# 4. View logs
docker compose logs -f backend
```

Access at <http://localhost:8080>.

---

## 3. Required Environment Variables Reference

| Variable | Required | Default | Notes |
|---|---|---|---|
| `LMS_ADMIN_PASSWORD` | **Yes** | — | Password used by `AdminSeeder` to bootstrap the initial `admin` account |
| `LMS_DB_PASSWORD` | Prod / Docker | — | Password for the MySQL database |
| `LMS_DB_USERNAME` | No | `root` | MySQL username |
| `LMS_DB_URL` | No | `jdbc:mysql://localhost:3306/librarydb...` | JDBC connection URL |
| `PORT` | No | `8080` | Server HTTP port (auto-set by Railway, Render, Fly.io, Cloud Run) |
| `SPRING_PROFILES_ACTIVE` | No | (default) | Set to `h2` for standalone in-memory dev; `docker` for MySQL container |
| `GOOGLE_CLIENT_ID` | Optional | — | Google OAuth2 client ID for student login |
| `GOOGLE_CLIENT_SECRET` | Optional | — | Google OAuth2 client secret |

---

## 4. Health Checks & Verification

- **Liveness & Readiness:** `GET /actuator/health`
- **Swagger Documentation:** `GET /swagger-ui.html`
- **OpenAPI JSON Spec:** `GET /v3/api-docs`
- **CSRF Token Bootstrap:** `GET /api/auth/csrf`
- **Actuator Metrics:** `GET /actuator/metrics`
 |
