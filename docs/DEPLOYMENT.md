# OCI deployment runbook

This guide describes the repository's Docker deployment model and the operator-reported Libris deployment. The live server and cloud configuration are not verified by reading this repository; confirm all current values and access before an operation.

## Reported production topology

Earlier operator reports describe Oracle Linux 9.8 on an OCI x86_64 VM, MySQL 8.4 in Compose, the `app` service published on `127.0.0.1:8080`, and Caddy reverse-proxying `libris.opik.net` with automatic HTTPS. The Compose MySQL service uses the named `mysql-data` volume and has no host port mapping. The reported Dynu updater is a root-owned one-shot service with an enabled 15-minute timer. These are historical operator reports, not a fresh health or configuration check.

Expected traffic path: public TCP 80/443 → host firewall and applicable OCI ingress rules → Caddy → `127.0.0.1:8080` → app container → MySQL over the private Compose network. Keep 8080, 3306, and rpcbind TCP/UDP 111 inaccessible from the public internet; preserve SSH access. The OCI route, public IP lifecycle, NSGs, and security lists must be checked in OCI Console before changing network rules.

## Preconditions

Before an update, confirm the correct checkout/source revision, clean or intentionally reviewed diff, backup/recovery state, current image architecture, service names, storage mapping, disk and memory headroom, and working SSH access. Do not run database migrations or deployment commands until the release-specific migration and recovery plan is reviewed.

The production VM has previously been reported as memory constrained. Build the image on a workstation for the verified server architecture (`linux/amd64` for the reported x86_64 instance); do not assume that architecture report remains current.

## Secrets and environment

The Compose project expects `LMS_DB_ROOT_PASSWORD`, `LMS_DB_PASSWORD`, and `LMS_ADMIN_PASSWORD`; `.env.example` documents the template. On the host, keep `.env` outside Git with restrictive ownership and permissions. Never paste its contents into a terminal transcript, support bundle, log, or issue. Do not use `docker compose config` or resolved container environment output as a secret verification technique; check presence/configuration privately without displaying values.

`AdminSeeder` reads the effective admin password at application startup and synchronizes the existing admin hash when it differs. The production profile has no fallback. Understand the effective Compose/systemd environment source before changing that value; do not rotate it as part of an image-only release.

## Build and transfer an application image

Build on a trusted workstation from the reviewed source tree, not on the low-memory production instance. Example for the operator-reported x86_64 host:

```sh
docker buildx build --platform linux/amd64 --load -t libris:local .
docker image inspect libris:local --format '{{.Os}}/{{.Architecture}} {{.Id}}'
docker image save libris:local | gzip > libris-image.tar.gz
shasum -a 256 libris-image.tar.gz
```

Transfer the archive through the approved secure channel. On the server, compare its checksum with the workstation's recorded checksum before loading it. Record the running app image ID and tag that exact image for rollback before replacing `libris:local`. Verify the rollback tag resolves to the recorded image ID. Keep the archive and rollback tag until production verification is complete.

Do not infer source provenance from a healthy image. The current Dockerfile does not embed a Git revision; record the reviewed source SHA alongside each future build or add build metadata in a separately reviewed source change.

## Application-only update

Use the actual deployment working directory and Compose environment source after verifying them. Earlier operator reports describe a project directory under the deployment account's home; confirm the current path rather than assuming it. With the project directory and `.env` verified:

```sh
docker load -i libris-image.tar.gz
docker compose --env-file .env up -d --no-deps --force-recreate --no-build app
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health
```

This recreates only `app`; `--no-deps` prevents Compose from recreating MySQL. Confirm the MySQL container identity and its `mysql-data` mount remain unchanged. Confirm health locally, through HTTPS, and with a browser login/session check appropriate to the release. Use sanitized, narrow logs if startup needs diagnosis.

Do not use `docker compose down` as an update command. `docker compose down -v` removes named volumes and is destructive. A restart, app-only recreation, full stack shutdown, and database restore are different operations.

## App-only rollback

Rollback is appropriate only if the application image is the identified cause and the prior image is compatible with the current schema/data. Tag the preserved prior image ID back to `libris:local`, then recreate only the app:

```sh
docker image tag <verified-rollback-image-id> libris:local
docker compose --env-file .env up -d --no-deps --force-recreate --no-build app
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health
```

Image rollback does **not** reverse Flyway migrations, application data changes, or catalog imports. Do not roll back across a schema change without a reviewed compatibility and database-recovery plan. Restore MySQL only from a verified backup using a separately reviewed procedure; a volume rollback is not implied by an app rollback.

## Compose storage and backups

The root `docker-compose.yml` defines the persistent named volume `mysql-data` mounted at `/var/lib/mysql`. Never delete it to repair credentials or a failed migration. The repository includes `scripts/backup-mysql.sh`; inspect its current options and confirm an off-instance copy and restore procedure before relying on it. A backup on the same VM is not disaster recovery. Do not assume an untested dump can be restored.

### Taking a backup

```sh
scripts/backup-mysql.sh                 # writes ./backups, keeps the 14 newest
scripts/backup-mysql.sh /secure/dumps  # explicit output directory
LIBRIS_BACKUP_KEEP=112 scripts/backup-mysql.sh /secure/dumps   # 28 days at 6-hourly
```

`LIBRIS_BACKUP_KEEP` sets how many local dumps survive; the oldest are pruned
after each successful run. The default of 14 is about 3.5 days at a six-hourly
cadence, which is rarely what a scheduled job wants.

The script **parses** `.env`; it never sources or executes it. Compose env-file
syntax is not shell syntax, and sourcing a `.env` that contains an unquoted,
space-separated value such as `JAVA_OPTS=-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError`
aborts the script. Reading the file as shell code would also execute anything
else in it.

Credentials never appear on a command line or in the script's output:

- By default the dump runs with the MySQL account the container already has in
  its own environment, so no secret crosses the host/container boundary.
- For a dedicated read-only account, set `LMS_DB_BACKUP_USERNAME` and
  `LMS_DB_BACKUP_PASSWORD` in `.env`. The credentials are piped to the container
  on stdin and used through a `0600` defaults-file that is removed on exit.
  Setting only one of the two is rejected rather than silently ignored.

To provision the least-privilege account (read plus the metadata privileges
`mysqldump` needs for `--routines`, `--triggers` and `--events`):

```sql
CREATE USER 'libris_backup'@'%' IDENTIFIED BY '<a generated secret>';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES ON librarydb.* TO 'libris_backup'@'%';
GRANT SHOW_ROUTINE ON *.* TO 'libris_backup'@'%';
FLUSH PRIVILEGES;
```

That account cannot insert, update or drop (`ERROR 1142`), which is the point.
Dumps and the output directory are created `0600`/`0700`; the script refuses to
publish a dump that failed, is implausibly small, or fails a gzip integrity
check, and it removes partial files on failure.

### Proving a backup can be restored

A valid archive is not proof of a working restore. Run the drill, which builds a
disposable database, backs it up, restores it into a second independent
database, and compares the two:

```sh
sh scripts/backup-mysql-restore-test.sh
```

It needs Docker, creates and destroys only its own throwaway containers, and
never reads the repository `.env` or contacts a production database. Run it
after changing the backup script, and periodically as an operational check.

### Recording what a backup contains

`gzip -t` proves an archive is not corrupt. It does not prove the dump is
complete or restorable. `scripts/backup-manifest.sh` records what an artifact
actually holds, so a later restore drill can compare the restored database
against the backup it came from:

```sh
scripts/backup-manifest.sh backups/libris-<timestamp>.sql.gz backup.json
```

The manifest contains the file's SHA-256 and size, the table list, per-table
row counts, and the applied Flyway version. It is derived from the dump file
itself, not from the live database, so it describes the artifact that would
actually be restored.

For diagnostics, prefer `docker compose ps`, `docker compose logs --since=<window> --tail=<count> app`, `df -h`, and `free -h`. Inspect and redact logs before sharing. Do not query or print secret-bearing environment/configuration output.

## Caddy, DNS, and HTTPS

The operator-reported configuration uses one host-level Caddy reverse proxy for `libris.opik.net`, proxying to `127.0.0.1:8080`. Caddy's automatic HTTPS requires correct public DNS and inbound reachability on TCP 80/443. Before activating or changing the proxy, confirm DNS A against the instance's current public IPv4, verify IPv6 before publishing AAAA, and inspect every applicable OCI NSG/security list plus the host firewall. Do not expose application or database ports.

The intended Caddy site block is:

```caddyfile
libris.opik.net {
    reverse_proxy 127.0.0.1:8080
}
```

Do not install a second competing proxy. Validate Caddy configuration before reload and preserve its persistent certificate data. Verify certificate hostname/validity, HTTPS response, HTTP redirect, app health, and session behavior from an independent client where possible. A local `curl` from the VM does not prove public reachability.

## Dynu IPv4 updater

Prior operator reports say the host uses a Dynu updater that discovers the VM's outbound IPv4 over HTTPS and updates only the A record for `libris.opik.net`. The credentials file must remain root-owned and private, outside the repository; never display its contents or put credentials in a command argument or unit file. Inspect the installed service/timer and sanitized status before changing anything. Do not update AAAA unless IPv6 is intentionally configured. DNS success does not prove HTTPS reachability.

## Health and service operations

The production profile exposes only unauthenticated `/actuator/health` with hidden component details; it returns non-healthy status when a configured health indicator is down. Caddy health is separate from the application probe. Check `docker compose ps`, the local health endpoint, Caddy systemd state, and the public URL independently.

The Compose services have restart policies. Do not create a new systemd Compose unit unless its startup/shutdown semantics have been reviewed; a unit using `docker compose down` can stop the stack and must not be added casually.

## Host and OCI network policy

The intended public ingress is TCP 80 and 443. Preserve an appropriately restricted SSH rule. Keep TCP 8080 and 3306 private, and TCP/UDP 111 blocked publicly unless a separately verified service requirement exists. Effective ingress can be granted by any attached NSG or subnet security list; inspect all of them before edits. Mirror only necessary 80/443 services in the host firewall after confirming the active interface/zone. Never replace all existing firewall rules.

## Troubleshooting safely

- **Missing/invalid configuration:** verify expected variable names and source privately; do not dump resolved config.
- **Database connection failure:** inspect container health and sanitized logs; remember MySQL bootstrap variables do not rotate credentials in an already initialized volume.
- **Migration failure:** stop and preserve logs/backup evidence. Do not edit applied migrations, run `down -v`, or improvise a baseline/repair.
- **Login not persistent:** check HTTPS, forwarded protocol, CSRF bootstrap, cookie presence/attributes in browser tools; redact cookie values.
- **DNS mismatch:** compare current instance public IPv4 and resolver results before changing Dynu. Do not infer public-IP assignment lifecycle from DNS.

## Evidence and provenance

The application image previously reported by the operator as deployed had digest `sha256:208d34943e4469cc280a3e7f791bd08fd4d5c59f32e0f2e2f1d028058ffff832`; that report did not establish a Git SHA. No current production check is performed by this guide. Record the source SHA, target platform, image ID/digest, archive checksum, deployment time, rollback image ID, health results, and preserved database identity for each release without recording secrets.
