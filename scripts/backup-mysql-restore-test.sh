#!/usr/bin/env sh
# Restore drill for scripts/backup-mysql.sh.
#
# Proves that a dump produced by the backup script can actually be restored,
# by building a throwaway database, seeding it, taking a backup, restoring into
# an independent second database, and comparing the two.
#
# This is the check a gzip integrity test cannot make. A dump can be a valid,
# uncorrupted archive and still be unrestorable.
#
# It creates and destroys only its own disposable containers. It never reads
# the repository .env, never contacts a production database, and refuses to run
# if LMS_ENV_FILE is pointed at anything it did not create.
#
# Usage: sh scripts/backup-mysql-restore-test.sh
#
# Requirements: Docker with the Compose v2 plugin. Exits non-zero on failure.

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
backup_script="$repo_dir/scripts/backup-mysql.sh"

work=$(mktemp -d "${TMPDIR:-/tmp}/libris-restore-drill.XXXXXX")

# Every drill gets a unique identity derived from its private temporary
# directory. Fixed container names would let cleanup remove a container that
# belongs to something else if startup failed partway or a name collided.
run_id=$(basename "$work" | tr -cd 'A-Za-z0-9')
src_db="libris_drill_src_${run_id}"
dst_db="libris_drill_dst_${run_id}"
root_pw=synthetic-drill-root-pw
app_pw=synthetic-drill-app-pw

# Only resources this invocation actually created are ever removed.
created_src=0
created_dst=0
network=""

die() {
  echo "error: $*" >&2
  exit 1
}

# Refuse to run if a name we intend to use is already taken, rather than
# risking an interaction with a container we did not create.
assert_name_free() {
  if docker ps -a --format '{{.Names}}' | grep -qx "$1"; then
    die "container name '$1' already exists; refusing to use or remove it"
  fi
}

cleanup() {
  echo
  echo "Cleaning up this drill's disposable resources..."
  [ "$created_src" -eq 1 ] && docker rm -f "$src_db" >/dev/null 2>&1 || true
  [ "$created_dst" -eq 1 ] && docker rm -f "$dst_db" >/dev/null 2>&1 || true
  [ -n "$network" ] && docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT HUP INT TERM

command -v docker >/dev/null 2>&1 || die "docker is required for the restore drill"
docker info >/dev/null 2>&1 || die "the Docker daemon is not reachable"

echo "restore drill: temporary directory $work"

# ---------------------------------------------------------------------------
echo
echo "1/5 starting a disposable source database"
assert_name_free "$src_db"
cat > "$work/docker-compose.yml" <<EOF
services:
  mysql:
    image: mysql:8.4
    container_name: $src_db
    environment:
      MYSQL_ROOT_PASSWORD: $root_pw
      MYSQL_DATABASE: drildb
      MYSQL_USER: drilluser
      MYSQL_PASSWORD: $app_pw
EOF
cat > "$work/.env" <<EOF
LMS_DB_NAME=drildb
LMS_DB_USERNAME=drilluser
LMS_DB_PASSWORD=$app_pw
JAVA_OPTS=-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom
EOF

(cd "$work" && docker compose up -d >/dev/null) && created_src=1

# The project name is derived from the temporary directory, which is unique
# per run; remember it so cleanup removes it too.
network=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$src_db" 2>/dev/null || echo "")

ready=0
i=0
while [ "$i" -lt 90 ]; do
  if docker exec "$src_db" mysqladmin ping -h 127.0.0.1 -u root -p"$root_pw" >/dev/null 2>&1; then
    ready=1
    break
  fi
  i=$((i + 1))
  sleep 2
done
[ "$ready" -eq 1 ] || die "source database did not become ready"

echo "2/5 seeding synthetic rows"
docker exec -i "$src_db" mysql -u root -p"$root_pw" drildb 2>/dev/null <<'SQL'
CREATE TABLE IF NOT EXISTS flyway_schema_history (
  installed_rank INT PRIMARY KEY, version VARCHAR(50), description VARCHAR(200),
  type VARCHAR(20), script VARCHAR(200), checksum INT, installed_by VARCHAR(100),
  installed_on TIMESTAMP, execution_time INT, success BOOL);
CREATE TABLE IF NOT EXISTS books (id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(255), isbn VARCHAR(20));
INSERT INTO flyway_schema_history VALUES (1,'5','indexes','SQL','V5__borrow_records_indexes.sql',1,'root',NOW(),100,1);
INSERT INTO books (title, isbn) VALUES ('Dune','9780441013593'),('Neuromancer','9780441569595'),('Snow Crash','9780553380958');
SQL

# ---------------------------------------------------------------------------
echo "3/5 taking a backup with scripts/backup-mysql.sh"
(cd "$work" && LMS_ENV_FILE="$work/.env" LMS_COMPOSE_SERVICE=mysql \
  sh "$backup_script" "$work/out") || die "the backup script failed"

dump=$(find "$work/out" -name 'libris-*.sql.gz' | head -1)
[ -n "$dump" ] || die "no dump was produced"
echo "   produced $(basename "$dump")"

# ---------------------------------------------------------------------------
echo "4/5 restoring into an independent database"
assert_name_free "$dst_db"
if docker run -d --name "$dst_db" \
  -e MYSQL_ROOT_PASSWORD="$root_pw" -e MYSQL_DATABASE=drillrestore \
  mysql:8.4 >/dev/null; then
  created_dst=1
fi

ready=0
i=0
while [ "$i" -lt 90 ]; do
  if docker exec "$dst_db" mysqladmin ping -h 127.0.0.1 -u root -p"$root_pw" >/dev/null 2>&1; then
    ready=1
    break
  fi
  i=$((i + 1))
  sleep 2
done
[ "$ready" -eq 1 ] || die "restore target did not become ready"

gunzip -c "$dump" | docker exec -i "$dst_db" mysql -u root -p"$root_pw" drillrestore 2>/dev/null ||
  die "restoring the dump failed"

# ---------------------------------------------------------------------------
echo "5/5 comparing source and restored data"
source_counts=$(docker exec "$src_db" mysql -N -B -u root -p"$root_pw" drildb -e \
  "SELECT CONCAT((SELECT COUNT(*) FROM books),':',(SELECT COUNT(*) FROM flyway_schema_history));" 2>/dev/null)
restored_counts=$(docker exec "$dst_db" mysql -N -B -u root -p"$root_pw" drillrestore -e \
  "SELECT CONCAT((SELECT COUNT(*) FROM books),':',(SELECT COUNT(*) FROM flyway_schema_history));" 2>/dev/null)

echo "   source   books:flyway = $source_counts"
echo "   restored books:flyway = $restored_counts"

if [ "$source_counts" != "$restored_counts" ]; then
  die "restored data does not match the source"
fi

source_rows=$(docker exec "$src_db" mysql -N -B -u root -p"$root_pw" drildb -e "SELECT title FROM books ORDER BY id;" 2>/dev/null)
restored_rows=$(docker exec "$dst_db" mysql -N -B -u root -p"$root_pw" drillrestore -e "SELECT title FROM books ORDER BY id;" 2>/dev/null)
if [ "$source_rows" != "$restored_rows" ]; then
  die "restored rows do not match the source rows"
fi

echo
echo "restore drill passed: the dump was restored and the data matched."
exit 0
