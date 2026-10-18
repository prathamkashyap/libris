#!/usr/bin/env sh
# Logical MySQL backup for the Libris stack.
#
# Dumps the schema and data from the running compose-managed MySQL container.
#
# Credentials are never passed on any command line and are never printed.
# By default the dump runs with the MySQL account that the container already
# has in its own environment, so no secret crosses the host/container boundary
# at all. An operator may instead nominate a dedicated least-privilege backup
# account in .env (LMS_DB_BACKUP_USERNAME / LMS_DB_BACKUP_PASSWORD); its
# credentials are then piped to the container on stdin and used through a
# 0600 defaults-file, never through argv.
#
# The .env file is *parsed*, never executed: Compose env-file syntax is not
# shell syntax, and sourcing it executes arbitrary commands (and fails outright
# on unquoted values that contain spaces, such as JAVA_OPTS).
#
# Usage:
#   scripts/backup-mysql.sh [output-directory]
#
# Default output directory: ./backups (git-ignored).
#
# Environment overrides:
#   LMS_ENV_FILE         path to the env file (default: <repo>/.env)
#   LMS_COMPOSE_SERVICE  compose service to dump from (default: mysql)
#
# On OCI this is intended to run from the host cron/service timer. A copy of
# each dump should also leave the instance — see docs/DEPLOYMENT.md, "Backups".

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
out_dir=${1:-"$repo_dir/backups"}
env_file=${LMS_ENV_FILE:-"$repo_dir/.env"}
service=${LMS_COMPOSE_SERVICE:-mysql}
timestamp=$(date -u +%Y%m%dT%H%M%SZ)

die() {
  echo "error: $*" >&2
  exit 1
}

# ---------------------------------------------------------------------------
# Safe dotenv reader.
#
# Prints the value of KEY from a Compose-style env file without executing the
# file. Only the exact literal key is matched, so no caller-supplied text is
# ever interpreted as a pattern.
#
# Matches the Compose env-file semantics that compose-go implements:
#
#   KEY=value                     unquoted; an inline comment introduced by a
#                                 space and '#' is stripped, trailing spaces
#                                 are trimmed, and a '#' that is not preceded
#                                 by a space stays literal
#   KEY="quoted value"            quotes removed, \" and \\ unescaped; any '#'
#                                 inside the quotes is literal
#   KEY='quoted value'            quotes removed, contents literal
#   export KEY=value
#
# A later assignment wins, matching Compose's own precedence.
# ---------------------------------------------------------------------------
dotenv_get() {
  [ -f "$1" ] || return 1
  _raw=$(sed -n \
    "s/^[[:space:]]*\(export[[:space:]]\{1,\}\)\{0,1\}$2[[:space:]]*=[[:space:]]*//p" \
    "$1" | tail -n 1)
  case "$_raw" in
    \"*\")
      # Double-quoted: strip the surrounding quotes, then unescape. Inline
      # comments do not apply inside quotes.
      _v=${_raw#\"}
      _v=${_v%\"}
      printf '%s\n' "$_v" | sed -e 's/\\"/"/g' -e 's/\\\\/\\/g'
      ;;
    \'*\')
      # Single-quoted: entirely literal.
      _v=${_raw#\'}
      _v=${_v%\'}
      printf '%s\n' "$_v"
      ;;
    *)
      # Unquoted: ' #' starts a comment; '#' not preceded by a space is
      # literal, so passwords containing '#' survive.
      printf '%s\n' "$_raw" | sed -e 's/ #.*$//' -e 's/[[:space:]]*$//'
      ;;
  esac
}

if [ ! -f "$env_file" ]; then
  die "$env_file not found (copy .env.example to .env first)"
fi

db_name=$(dotenv_get "$env_file" LMS_DB_NAME || true)
[ -n "$db_name" ] || db_name=librarydb

backup_user=$(dotenv_get "$env_file" LMS_DB_BACKUP_USERNAME || true)
backup_password=$(dotenv_get "$env_file" LMS_DB_BACKUP_PASSWORD || true)

# Either both or neither: a half-configured account is a silent-failure trap.
if [ -n "$backup_user" ] && [ -z "$backup_password" ]; then
  die "LMS_DB_BACKUP_USERNAME is set but LMS_DB_BACKUP_PASSWORD is empty in $env_file"
fi
if [ -n "$backup_password" ] && [ -z "$backup_user" ]; then
  die "LMS_DB_BACKUP_PASSWORD is set but LMS_DB_BACKUP_USERNAME is empty in $env_file"
fi

# Restrictive by default: dumps and the directory that holds them are not
# world-readable, because a dump contains the whole database.
umask 077
mkdir -p "$out_dir"
chmod 700 "$out_dir"

partial_sql="$out_dir/.libris-$timestamp.sql.partial"
final_sql="$out_dir/libris-$timestamp.sql"

cleanup() {
  rm -f "$partial_sql" "$partial_sql.gz"
}
trap cleanup EXIT HUP INT TERM

# ---------------------------------------------------------------------------
# Dump.
#
# Credentials reach mysqldump through one of two routes, neither of which
# involves a command-line argument:
#
#  1. Dedicated backup account: the credentials are written to a 0600
#     defaults-file *inside* the container, fed on stdin, and removed on exit.
#  2. Default: the container's own MYSQL_USER / MYSQL_PASSWORD environment
#     variables are used, so no secret is transmitted or printed at all.
#
# --no-tablespaces avoids requiring the global PROCESS privilege.
# ---------------------------------------------------------------------------
if [ -n "$backup_user" ]; then
  inner_script=$(cat <<'INNER_SCRIPT'
set -eu
umask 077
# A unique file per invocation: a fixed path would let concurrent backups
# overwrite each other's credentials, and umask alone does not tighten the
# permissions of a file that already exists.
cnf=$(mktemp /tmp/.libris-backup-client.XXXXXX)
trap 'rm -f "$cnf"' EXIT HUP INT TERM
cat > "$cnf"
chmod 600 "$cnf"
mysqldump --defaults-extra-file="$cnf" \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  --hex-blob \
  --set-gtid-purged=OFF \
  --no-tablespaces \
  "$1"
INNER_SCRIPT
)
  echo "Dumping MySQL to $final_sql (account: $backup_user)"
  printf '[client]\nuser=%s\npassword=%s\n' \
    "$backup_user" "$backup_password" |
    docker compose --env-file "$env_file" exec -T "$service" \
      sh -c "$inner_script" _ "$db_name" > "$partial_sql"
else
  inner_script=$(cat <<'INNER_SCRIPT'
set -eu
export MYSQL_PWD="$MYSQL_PASSWORD"
mysqldump --user="$MYSQL_USER" \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  --hex-blob \
  --set-gtid-purged=OFF \
  --no-tablespaces \
  "$1"
INNER_SCRIPT
)
  echo "Dumping MySQL to $final_sql (account: container MYSQL_USER)"
  docker compose --env-file "$env_file" exec -T "$service" \
    sh -c "$inner_script" _ "$db_name" > "$partial_sql"
fi

# A failed dump can still leave a partial file on disk; refuse to publish one
# as if it were a usable backup.
[ -s "$partial_sql" ] || die "dump produced no output (mysqldump produced nothing)"

gzip -9 "$partial_sql"
gzip -t "$partial_sql.gz" || die "compressed dump failed its integrity check"

size=$(wc -c < "$partial_sql.gz" | tr -d ' ')
if [ "$size" -lt 1024 ]; then
  die "dump is only $size bytes — refusing to publish an implausibly small backup"
fi

mv "$partial_sql.gz" "$final_sql.gz"
chmod 600 "$final_sql.gz"

echo "Wrote $final_sql.gz ($size bytes)"

# Keep the 14 most recent local dumps.
ls -1t "$out_dir"/libris-*.sql.gz 2>/dev/null | tail -n +15 | xargs -r rm -f

exit 0
