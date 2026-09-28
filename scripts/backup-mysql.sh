#!/usr/bin/env sh
# Logical MySQL backup for the Libris stack.
#
# Dumps the schema and data from the running compose-managed MySQL container.
# Credentials are read from the deployment .env file, never from argv, so they
# do not appear in the process list.
#
# Usage:
#   scripts/backup-mysql.sh [output-directory]
#
# Default output directory: ./backups (git-ignored).
#
# On OCI this is intended to run from the host cron/service timer. A copy of
# each dump should also leave the instance — see docs/DEPLOYMENT.md, "Backups".

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
out_dir=${1:-"$repo_dir/backups"}
env_file="$repo_dir/.env"
service=${LMS_COMPOSE_SERVICE:-mysql}
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
dump_file="$out_dir/libris-$timestamp.sql"

if [ ! -f "$env_file" ]; then
  echo "error: $env_file not found (copy .env.example to .env first)" >&2
  exit 1
fi

# Load the secret without echoing it.
set -a
# shellcheck disable=SC1090
. "$env_file"
set +a

if [ -z "${LMS_DB_ROOT_PASSWORD:-}" ]; then
  echo "error: LMS_DB_ROOT_PASSWORD is empty in $env_file" >&2
  exit 1
fi

mkdir -p "$out_dir"

echo "Dumping MySQL to $dump_file"
docker compose --env-file "$env_file" exec -T \
  -e MYSQL_PWD="$LMS_DB_ROOT_PASSWORD" \
  "$service" \
  mysqldump \
    --user=root \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --hex-blob \
    --set-gtid-purged=OFF \
    "${LMS_DB_NAME:-librarydb}" > "$dump_file"

gzip -9 "$dump_file"

size=$(wc -c < "$dump_file.gz" | tr -d ' ')
if [ "$size" -lt 1024 ]; then
  echo "error: dump is only $size bytes — treating as failed backup" >&2
  exit 1
fi

echo "Wrote $dump_file.gz ($size bytes)"

# Keep the 14 most recent local dumps.
ls -1t "$out_dir"/libris-*.sql.gz 2>/dev/null | tail -n +15 | xargs -r rm -f

exit 0
