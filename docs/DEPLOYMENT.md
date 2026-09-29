# Deploying Libris to Oracle Cloud Infrastructure

Target: a single OCI Compute instance running the Spring Boot 3.5 (Java 21)
application together with MySQL, both as Docker containers, behind a
host-level TLS reverse proxy.

> **Nothing in this document is provisioned yet.** Every OCI resource, DNS
> record, certificate and secret below is a manual step you perform. The
> repository only contains the files needed to run them once they exist.

Railway and Render were the previous deployment targets. Their configuration
files (`railway.json`, `render.yaml`) and the second, divergent
`backend/Dockerfile` / `backend/docker-compose.yml` pair have been removed.
There is now exactly one `Dockerfile` and one `docker-compose.yml`, at the
repository root, used for local runs and for OCI. See
[Historical deployments](#12-historical-deployments).

---

## 1. Prerequisites

### On the OCI Compute instance

| Requirement | Notes |
|---|---|
| OCI Compute instance | Ubuntu 22.04/24.04 or Oracle Linux 9. Adequate sizing below. |
| Docker Engine | 24+ with the Compose v2 plugin (`docker compose version`). |
| Outbound HTTPS | Required to pull images and Maven dependencies. Use a VCN NAT gateway or a Service Gateway. |
| TCP 22 | Restrict the source CIDR to your own IP or an OCI Bastion. |
| TCP 443 | Open to the internet for public users. |
| A domain name | Optional but recommended — required for a publicly trusted certificate. |
| TLS certificate + key | From OCI Certificates Management, Let's Encrypt, or a commercial CA. |
| Free disk | 30 GB minimum; 50 GB recommended to hold the image, Maven cache and MySQL volume. |

Do **not** open TCP 3306 or 8080 in the VCN security list or the instance
firewall. The application publishes on `127.0.0.1` and reaches MySQL over the
private Docker bridge network.

### Sizing

Both containers share the instance, so size for MySQL plus the JVM heap.

| Shape (2 OCPU / 16 GB) | Notes |
|---|---|
| VM.Standard.E2.1.Micro (1/8 OCPU, 1 GB) | Local evaluation only. Not a production target. |
| VM.Standard.E2.1.Small (1 OCPU, 2 GB) | Minimum workable. Set `JAVA_OPTS` to `MaxRAMPercentage=45`. |
| VM.Standard.A1.Flex (2 OCPU, 12 GB) | Recommended starting point for production. |
| VM.Standard.E4.Flex (2 OCPU, 16 GB) | Recommended if the archive is large or reporting is slow. |

`JAVA_OPTS` in `.env` controls heap sizing. `-XX:MaxRAMPercentage=70.0` (the
default) means the JVM takes 70% of the memory it can see. On a shared instance
where MySQL also runs, lower this to `45`-`55`.

### Locally, before touching OCI

```bash
git clone https://github.com/prathamkashyap/libris.git
cd libris
cp .env.example .env        # then fill in the three required values
./mvnw clean verify         # tests + Spotless + JaCoCo gate
docker compose up -d
curl -fsS http://127.0.0.1:8080/actuator/health
```

---

## 2. Build

Two options. Both produce the same artifact; pick one.

### A. Build on the instance (simplest)

```bash
git clone https://github.com/prathamkashyap/libris.git
cd libris
git checkout main
docker compose build
```

`docker compose build` invokes the root multi-stage `Dockerfile`, which compiles
with the Maven wrapper inside the build and ships only a JRE runtime layer with
a non-root `app` user. Expect 5–10 minutes on the first build (dependency
download) and ~1 minute on later builds.

### Architecture

No `--platform` argument is needed. The `Dockerfile` is architecture-neutral and
builds for `linux/amd64` and `linux/arm64` on the same machine, so the same
command produces the right image on an Ampere A1 (ARM64) instance and on an
x86 instance.

`VM.Standard.A1.Flex` is ARM64, and that path has been verified rather than
assumed: the image was built and run on both architectures, with the JVM
reporting `os.arch` of `aarch64` and `amd64` respectively and
`/actuator/health` returning `{"status":"UP"}` in both. A CI job
(`docker-build` in `.github/workflows/ci.yml`) rebuilds the image for both
architectures on every push to `main` to keep that true. See
[TESTING.md § CI Integration](TESTING.md#ci-integration).

### B. Build elsewhere, push to OCI Container Registry

Useful when the instance has restricted egress or you want reproducible image
digests.

```bash
# On a workstation
docker build -t <REGION>.ocir.io/<TENANCY_NS>/libris:1.0.0 .

# Log in to OCI Container Registry (Interactive mode, auth token as password)
docker login <REGION>.ocir.io

docker push <REGION>.ocir.io/<TENANCY_NS>/libris:1.0.0
```

Then in `docker-compose.yml` replace the `build:` block on the `app` service
with:

```yaml
    image: <REGION>.ocir.io/<TENANCY_NS>/libris:1.0.0
```

Option B is the only way to get an immutable image reference. Pinning by digest
(`libris@sha256:...`) is stronger still:

```bash
docker image inspect --format '{{index .RepoDigests 0}}' <REGION>.ocir.io/<TENANCY_NS>/libris:1.0.0
```

### Notes on the build

- Tests are skipped inside the Docker build (`-DskipTests`). Run `./mvnw clean
  verify` before building; GitHub Actions (`.github/workflows/ci.yml`) does this
  on every push and pull request to `main`.
- The build requires no cloud credentials and no secrets.
- The application JAR is `backend/target/libris-0.0.1-SNAPSHOT.jar` when built
  on a workstation. Docker builds are self-contained; nothing outside the
  container is needed at runtime.

---

## 3. Environment variables

`docker-compose.yml` reads every setting from the environment. Copy the
template and fill it in:

```bash
cp .env.example .env
chmod 600 .env
```

Compose uses the `${VAR:?message}` form for the required values, so **the stack
refuses to start while any of them is empty** — there is no fallback password
anywhere in the repository.

### Required

| Variable | Purpose |
|---|---|
| `LMS_DB_ROOT_PASSWORD` | MySQL root password. Bootstrap and healthcheck only; the application never connects as root. |
| `LMS_DB_PASSWORD` | Password for the dedicated application MySQL account. |
| `LMS_ADMIN_PASSWORD` | Password applied to the `admin` account by `AdminSeeder` on every start. |

Generate them, do not invent them:

```bash
openssl rand -base64 32   # LMS_DB_ROOT_PASSWORD, LMS_DB_PASSWORD
openssl rand -base64 24   # LMS_ADMIN_PASSWORD
```

### Optional

| Variable | Default | Purpose |
|---|---|---|
| `LMS_ADMIN_USERNAME` | `admin` | Admin account name. |
| `LMS_DB_USERNAME` | `libris` | Application MySQL account created by the `mysql` image. |
| `LMS_DB_NAME` | `librarydb` | Schema name. |
| `LMS_DB_URL` | `jdbc:mysql://mysql:3306/librarydb?...` | JDBC URL. Override for an external database. |
| `LMS_BIND_ADDRESS` | `127.0.0.1` | Host address the app port is published on. |
| `JAVA_OPTS` | `MaxRAMPercentage=70.0` | JVM heap sizing. |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | empty | Google OAuth2 student login. Blank keeps OAuth disabled. |

### Handling secrets on OCI

`.env` is git-ignored and `.dockerignore` excludes `**/.env*` from the build
context, so it is never baked into an image. Two supported options:

1. **`.env` on the instance** — simplest. Write it over SSH, `chmod 600`, and
   restrict it to the deploying user. Remove it once you move to option 2.
2. **OCI Vault / OCI Secrets** — better. Store the three required values in a
   vault, then inject them into the `libris.service` unit's `EnvironmentFile=`
   target. The secrets never exist as a long-lived file you can `cat`.

`.env` must not be committed, copied into support bundles, or placed in
Object Storage unencrypted.

> **Precedence trap — read this before your first deploy.** Docker Compose
> resolves `${VAR}` from the **process environment first**, and only falls back
> to `.env`. An `LMS_ADMIN_PASSWORD` left exported in your shell, in `~/.zshrc`,
> in a CI job, or in a systemd unit will silently win over the value in `.env`,
> and `AdminSeeder` will apply *that* password to the admin account. Always
> check what Compose actually resolved before starting:
>
> ```bash
> docker compose config | grep -E 'LMS_(ADMIN|DB)'
> ```
>
> If that output does not match `.env`, something in your environment is
> overriding it. This is the single most likely cause of "I set the admin
> password and the old one still works".

---

## 4. Database setup

There is nothing to run by hand. The `mysql` service image creates the schema
(`MYSQL_DATABASE`), the application account (`MYSQL_USER` /
`MYSQL_PASSWORD`) and grants that account privileges on that schema only.
`libris` cannot create databases, drop the schema, or read `mysql.*`.

Startup ordering is handled by compose:

```yaml
    depends_on:
      mysql:
        condition: service_healthy
```

The `mysql` healthcheck (`mysqladmin ping`) must pass before the JVM starts, so
the first Flyway migration never races an initialising server.

Data persists in the named volume `mysql-data` (`/var/lib/mysql`). Removing the
volume destroys the database:

```bash
docker compose down        # safe — keeps the volume
docker compose down -v     # DESTRUCTIVE — deletes all data
```

### Moving MySQL to a dedicated instance or managed service

Override `LMS_DB_URL` (and `LMS_DB_USERNAME` / `LMS_DB_PASSWORD`) and set
`useSSL=true` plus `requireSSL=true` when the database is not on the same host.
Also grant the account from the application's source address. Remember to
provision the database and the account first — the application will not create
them for you.

---

## 5. Application startup

```bash
cd libris
docker compose up -d
docker compose ps
docker compose logs -f app
```

Startup order on a first run:

1. `mysql` starts, initialises the data directory, applies the grant, reports healthy.
2. `app` starts as the non-root `app` user, connects over the bridge network.
3. Flyway applies `V1`–`V5` to the empty schema.
4. `AdminSeeder` creates the `admin` account with `LMS_ADMIN_PASSWORD`.
5. Tomcat binds `0.0.0.0:8080` inside the container; Docker publishes it on `127.0.0.1:8080`.
6. The container healthcheck polls `/actuator/health`.

Expect roughly **60–90 seconds** from `docker compose up -d` to a healthy
`app`, longer on a small shape. That is why both the Dockerfile and
`docker-compose.yml` use a 180-second `start_period`; do not shorten it or
Docker will mark a correctly-booting container unhealthy. Scale OCI
provisioning health checks and load balancer timeouts accordingly.

The `prod` profile is active (`SPRING_PROFILES_ACTIVE=prod`). It disables
Swagger UI and the OpenAPI document, restricts the actuator surface to
`/actuator/health`, and requires `LMS_ADMIN_PASSWORD` to be supplied from the
environment.

### Log in

Browse to the reverse proxy URL and sign in as `LMS_ADMIN_USERNAME` /
`LMS_ADMIN_PASSWORD`. **Change the admin password immediately after first
login**, then update `LMS_ADMIN_PASSWORD` in `.env` so restarts do not revert
it — `AdminSeeder` re-applies the configured value on every start.

---

## 6. Flyway migration behaviour

Schema changes are owned by Flyway, never by Hibernate
(`spring.jpa.hibernate.ddl-auto=none` in both the base and `prod`
configuration). Adding a migration is the only supported way to change the
schema.

Current migrations, in `backend/src/main/resources/db/migration/`:

| Version | File | Change |
|---|---|---|
| V1 | `V1__baseline.sql` | Full baseline schema |
| V2 | `V2__student_profile_email_unique.sql` | Unique index on `student_profiles.email` |
| V3 | `V3__borrow_record_due_date.sql` | `borrow_records.due_date` |
| V4 | `V4__book_category.sql` | `books.category` |
| V5 | `V5__borrow_records_indexes.sql` | Indexes on `return_date` and `due_date` |

Behaviour in production:

- Migrations run automatically inside `app` startup, before the web server
  accepts traffic. There is no separate migration job and no migration container.
- Flyway records applied versions in the `flyway_schema_history` table. On every
  later start it validates that recorded checksums still match the files
  (`spring.flyway.validate-on-migrate=true`) and **refuses to start** on a
  mismatch. Never edit an applied migration file — add a new `V6__*.sql`.
- `baseline-on-migrate` is **false** in production
  (`application-prod.properties`), deliberately. With it enabled, Flyway finds a
  non-empty schema that has no `flyway_schema_history`, records the current state
  as V1, and then applies only migrations newer than V1. A database restored from
  anywhere that did not carry that table would be left at V1 with V2–V5 never
  applied, and the application would start and look healthy on an incomplete
  schema. Refusing to start is the better failure. The three cases:

  | Situation | Behaviour |
  |---|---|
  | Fresh empty database (first boot, or after `docker compose down -v`) | Schema is empty, so there is nothing to baseline. Flyway applies V1 → V5. Unaffected by the setting. |
  | Restore of a `scripts/backup-mysql.sh` dump | The dump is logical and includes `flyway_schema_history`, so Flyway validates the checksums and skips what is already applied. |
  | Populated schema with no `flyway_schema_history` (unexpected) | Flyway **refuses to start** and reports it. Do not "fix" this by re-enabling the flag: inspect the schema first, then adopt it deliberately with `flyway baseline` on a single run and re-enable migrations. Verify the result with the query in §7. |
- A failed migration aborts startup. The `app` container exits non-zero, restarts
  per §9, and keeps failing. Fix the migration or the database state, then
  `docker compose up -d app`.
- Rolling back a migration is not automated. Restore from backup (§10).

### The Flyway / MySQL version warning

On startup you will see this line in the logs:

```
Flyway upgrade recommended: MySQL 8.4 is newer than this version of Flyway
and support has not been tested. The latest supported version of MySQL is 8.1.
```

The bundled Flyway is 11.7.2 (managed by `spring-boot-starter-parent` 3.5.0),
whose tested support matrix stops at MySQL 8.1. It is a **warning, not a
failure**: V1–V5 have been verified to apply and validate cleanly against
MySQL 8.4. MySQL 8.4 is used because it is the current LTS and 8.0 is past
end-of-life. If you would rather stay inside Flyway's tested range, set
`image: mysql:8.0` in `docker-compose.yml` — accept that you are then running
an unsupported major version.

To add a migration:

```bash
# backend/src/main/resources/db/migration/V6__add_xxx.sql
docker compose up -d --build app
docker compose logs app | grep -i flyway
```

---

## 7. Health checks

```bash
# From the host (app is published on loopback)
curl -fsS http://127.0.0.1:8080/actuator/health
# {"status":"UP"}
```

`/actuator/health` is the only actuator endpoint exposed under the `prod`
profile. It is unauthenticated by design (load balancers and
`docker compose` healthchecks need it) but returns no component detail, and
returns HTTP 503 when any health indicator — including the MySQL connection
pool and Flyway — is down.

```bash
# Container health state
docker compose ps
# Verify Flyway applied everything
docker compose exec mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root -N -e \
   "SELECT version, description, success FROM librarydb.flyway_schema_history ORDER BY installed_rank"'
```

If a restored database looks stale, the `success` column will show `0` for the
failed version, or the highest applied version will be lower than V5.

For OCI Load Balancer health checks, use:

| Setting | Value |
|---|---|
| Protocol / Port | TCP 8080, or HTTP `/actuator/health` |
| Interval | 10 s |
| Timeout | 5 s |
| Reject threshold | 3 consecutive failures |

Point the load balancer at the instance's private IP on port 8080 only if you
have deliberately set `LMS_BIND_ADDRESS=0.0.0.0` and restricted 8080 in the
security list. The recommended setup terminates TLS on the host and never
exposes 8080.

---

## 8. HTTPS and reverse proxy

The application itself does not terminate TLS. It is published on `127.0.0.1:8080`
and relies on `server.forward-headers-strategy=framework` to honour
`X-Forwarded-*` headers from the proxy. Session cookies are issued with
`Secure` and `SameSite=Lax` (`application-prod.properties`), so **plain HTTP is
not a working production mode** — logins will not persist without TLS.

### Option: nginx on the host (recommended)

Install nginx and obtain a certificate. Using Certbot:

```bash
sudo apt-get update && sudo apt-get install -y nginx certbot python3-certbot-nginx
sudo certbot --nginx -d libris.example.com
```

`/etc/nginx/sites-available/libris`:

```nginx
server {
    listen 443 ssl http2;
    server_name libris.example.com;

    ssl_certificate     /etc/letsencrypt/live/libris.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/libris.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    # CSRF tokens and session cookies must not be rewritten or dropped.
    add_header Strict-Transport-Security "max-age=31536000" always;

    client_max_body_size 10m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host  $host;
        proxy_read_timeout 60s;
    }
}

server {
    listen 80;
    server_name libris.example.com;
    return 301 https://$host$request_uri;
}
```

```bash
sudo ln -sf /etc/nginx/sites-available/libris /etc/nginx/sites-enabled/libris
sudo nginx -t && sudo systemctl reload nginx
```

Renewal is automatic (`certbot.timer`).

### Option: OCI Load Balancer

Point a Flexible Load Balancer at the instance and use OCI Certificates
Management for the listener certificate. It still forwards plaintext HTTP to the
origin, so keep `LMS_BIND_ADDRESS=127.0.0.1` only if you add a host-level
proxy; for a load balancer, set `LMS_BIND_ADDRESS=0.0.0.0` and restrict 8080
to the load balancer's subnet in the security list.

### OCI security list and instance firewall

| Rule | Source | Purpose |
|---|---|---|
| TCP 22 | Your IP CIDR (or a Bastion subnet) | SSH administration |
| TCP 443 | `0.0.0.0/0` | HTTPS |
| ICMP | Your IP CIDR | Optional diagnostics |
| Stateful | — | Keep egress rules for image and package pulls |

`3306` and `8080` stay closed. Mirror the security list in the instance
firewall (`Oracle Linux` uses `firewalld`, `Ubuntu` uses `ufw`):

```bash
# Oracle Linux
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --permanent --add-port=22/tcp
sudo firewall-cmd --reload

# Ubuntu
sudo ufw allow OpenSSH && sudo ufw allow 443/tcp && sudo ufw enable
```

---

## 9. Restart behaviour

Both services use `restart: unless-stopped`, so after a container exits — crash
or normal — Docker restarts it, and Compose restarts the stack when the Docker
daemon or the instance reboots.

`stop_grace_period: 30s` gives the JVM time to finish in-flight requests and
close the datasource before `SIGKILL`. Spring's graceful shutdown is enabled by
default in Boot 3.

The container has no automatic crash-loop breaker. If the `app` container fails
repeatedly — for example because `LMS_ADMIN_PASSWORD` is unset — it will restart
forever. Check the logs rather than assuming it is transient:

```bash
docker compose logs --tail=100 app
docker inspect libris-app --format '{{.RestartCount}} {{.State.ExitCode}}'
```

To keep the stack running across reboots, ensure Docker is enabled:

```bash
sudo systemctl enable --now docker
```

### Optional: a systemd unit

If you prefer the instance to manage startup rather than relying on an
interactive `docker compose up`, create `/etc/systemd/system/libris.service`:

```ini
[Unit]
Description=Libris
Requires=docker.service
After=docker.service network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/libris
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now libris.service
```

This also gives you a place to point `EnvironmentFile=` at OCI Vault-injected
secrets, and lets you drain connections with `systemctl stop libris` before
maintenance.

---

## 10. Backups

The MySQL volume is the only stateful component. Back it up off the instance.

```bash
# Logical dump of schema and data, gzipped, keeping the 14 newest
scripts/backup-mysql.sh
# -> backups/libris-20260928T113000Z.sql.gz
```

Restore:

```bash
docker compose stop app
gunzip -c backups/libris-20260928T113000Z.sql.gz | \
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root librarydb'
docker compose start app
docker compose logs -f app | grep -i flyway
```

### Schedule it

```bash
sudo crontab -e
# 02:17 daily — off the hour so it does not collide with everything else
17 2 * * * /opt/libris/scripts/backup-mysql.sh >> /var/log/libris-backup.log 2>&1
```

### Get the dumps off the instance

A dump sitting in the same block volume does not survive losing the instance.
Copy each one to Object Storage:

```bash
oci os object put \
  --namespace "<TENANCY_NAMESPACE>" \
  --bucket-name "<BACKUP_BUCKET>" \
  --file "backups/libris-$(date -u +%Y%m%dT%H%M%SZ).sql.gz" \
  --name "libris/libris-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
```

Use a bucket with versioning and a retention policy. Run the cron as a
principal limited to that one bucket.

### Test the restore

An untested backup is not a backup. At least once, restore a dump into a
scratch instance and confirm `flyway_schema_history` shows V1–V5 and that
`/api/auth/login` succeeds.

---

## 11. Troubleshooting

### The stack will not start

```bash
docker compose config --quiet          # validates interpolation
docker compose up -d                   # surfaces "VAR is required"
```

A `${VAR:?...}` message means `.env` is missing or that value is empty.

### `Access denied for user ... using password: YES`

The application account and its password disagree, or the account does not
exist. MySQL 8.0.30+ and 8.4 need an explicit timezone during the handshake —
the default `LMS_DB_URL` already includes `connectionTimeZone=UTC`. See
`DEBUG.md` for the full history of this failure mode.

```bash
docker compose exec mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root -e \
   "SELECT user, host, plugin FROM mysql.user"'
```

If the `MYSQL_USER`/`MYSQL_PASSWORD` in `.env` changed after the volume was
initialised, the official image only applies them on first initialisation. The
existing account keeps its old password — either restore the old value or
reset it:

```bash
docker compose exec mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root -e \
   "ALTER USER '\''libris'\''@'\''%'\'' IDENTIFIED BY '\''<new-password>'\''; FLUSH PRIVILEGES"'
```

### App restarts in a loop

```bash
docker compose logs --tail=200 app
```

Common causes:

| Message | Fix |
|---|---|
| `Access denied for user 'libris'@...` | `MYSQL_USER`/`MYSQL_PASSWORD` are only applied by the mysql image on **first** initialisation of the data volume. Changing them in `.env` later has no effect — see below. |
| `Could not resolve placeholder 'LMS_ADMIN_PASSWORD'` | Not set. The `prod` profile has no default. |
| `LMS_ADMIN_PASSWORD must not be the default value` | `AdminSeeder` rejects the legacy `ChangeMe123!`. |
| `Validate failed: applied migration checksum mismatch` | A migration file was edited after being applied. Restore it, or `flyway repair` against a deliberate decision. |
| `Unable to determine Dialect` / connection refused | MySQL unhealthy, or the JDBC URL is wrong. `docker compose ps`. |
| `OutOfMemoryError` | Lower `MaxRAMPercentage` in `JAVA_OPTS`. |

### The admin password is not the one in `.env`

Two separate causes, check both:

1. **Environment override.** Compose prefers your process environment over
   `.env`. Run `docker compose config | grep LMS_ADMIN_PASSWORD` and compare.
2. **A stale MySQL account.** The `libris` account's password is fixed when the
   data volume is first initialised. Changing `LMS_DB_PASSWORD` in `.env`
   afterwards does not update it, so the app can no longer connect. Either
   restore the old value, reset the account, or accept the data loss and
   start clean:

```bash
# DESTRUCTIVE — deletes the database
docker compose down -v
```

To keep the data and just fix the account:

```bash
docker compose exec mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root -e \
   "ALTER USER '\''libris'\''@'\''%'\'' IDENTIFIED BY '\''<new-password>'\''; FLUSH PRIVILEGES"'
```

### Health check returns 503

The endpoint reflects real state — this is a genuine signal, not a bug. Pull the
component detail locally to find out what is down:

```bash
SPRING_PROFILES_ACTIVE= docker compose exec app \
  wget -qO- http://127.0.0.1:8080/actuator/health
```

An unauthenticated request outside the `prod` profile returns component detail;
run it only on the instance, over SSH.

### Logins succeed but immediately redirect back to the login page

Session cookies are `Secure`, so they are dropped over plain HTTP. The
reverse proxy in §8 is not terminating TLS, or `X-Forwarded-Proto` is not being
set. Verify:

```bash
curl -sI http://127.0.0.1:8080/login.html | grep -i set-cookie
# outside TLS you should see the cookie missing or stripped by the browser
```

Also confirm the `Set-Cookie` carries `Secure` and `SameSite=Lax` and that
nginx is not rewriting the `Set-Cookie` header.

### `docker compose pull` fails or the build is very slow

The instance has no working egress. It needs a NAT gateway or Service Gateway
route, plus security list egress rules.

### Disk filling up

```bash
df -h
docker system df
du -sh /var/lib/docker
```

In-container logs are rotated at 10 MB × 5 per service. `/opt/libris/backups`
grows 14 dumps at a time — raise a bucket lifecycle rule for the off-instance
copy.

### Getting logs

```bash
docker compose logs -f app                 # JSON on stdout
docker compose logs --since 1h app
docker exec libris-app sh -c 'tail -f /app/logs/libris.log'   # rolling file inside the container
docker exec libris-app sh -c 'tail -f /app/logs/libris-error.log'
```

The container's log directory is part of the container filesystem, so it does
not survive `docker compose down`. Ship stdout off the instance if you need
longer retention — `journald`, Fluent Bit to OCI Logging, or the OCI Logging
agent.

---

## 12. Historical deployments

Railway was the previous target and ran at `https://libris-lms.up.railway.app`
while that deployment was live. Its configuration has been removed:

- `railway.json` — Railway-specific build and restart policy.
- `render.yaml` — Render Blueprint.
- `backend/Dockerfile`, `backend/docker-compose.yml`, `backend/.env.example` —
  a second, divergent pair. They disagreed with the root pair on the MySQL
  major version, ran the container as root, and had no healthcheck.

The root `Dockerfile` and root `docker-compose.yml` supersede all of them and
are the single supported path. The one assumption carried over from Railway is
`server.port=${PORT:8080}`, which is harmless and lets the app bind a
platform-assigned port. Nothing else about the Railway topology — platform-
managed MySQL, edge TLS termination, platform-injected database variables —
applies to OCI, where MySQL is a container on the same instance.
