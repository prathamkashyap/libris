#!/usr/bin/env sh
# Regression tests for scripts/backup-mysql.sh.
#
# These tests never touch a real database or a real .env. `docker` is replaced
# with a stub that records the exact argument vector it was called with and,
# where relevant, the bytes piped to its stdin. That lets us assert both
# behaviour and credential hygiene. All credentials used below are synthetic.
#
# Run: sh scripts/backup-mysql.test.sh
# Exits non-zero if any test fails.
#
# run_backup [docker-exit] [docker-stdout] [read-stdin]
#   Knobs are passed as parameters rather than environment prefixes, because a
#   `VAR=x function` prefix leaks into the shell's environment in POSIX sh and
#   would silently change the behaviour of later tests.

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
backup_script="$repo_dir/scripts/backup-mysql.sh"
work=$(mktemp -d "${TMPDIR:-/tmp}/libris-backup-test.XXXXXX")

pass=0
fail=0
DEFAULT_DUMP=""

cleanup() { rm -rf "$work"; }
trap cleanup EXIT HUP INT TERM

ok() {
  pass=$((pass + 1))
  echo "  ok   - $1"
}

no() {
  fail=$((fail + 1))
  echo "  FAIL - $1"
  [ $# -gt 1 ] && echo "         $2"
  return 0
}

# A docker stub. Records argv one-per-line, optionally captures stdin, emits
# the supplied stdout, and exits with the supplied code.
make_docker_stub() {
  mkdir -p "$work/bin"
  cat > "$work/bin/docker" <<'STUB'
#!/usr/bin/env sh
: > "${DOCKER_ARGV_LOG:?}"
for a in "$@"; do
  printf '%s\n' "$a" >> "$DOCKER_ARGV_LOG"
done
if [ "${DOCKER_READ_STDIN:-0}" = "1" ]; then
  cat > "${DOCKER_STDIN_LOG:?}"
fi
[ -n "${DOCKER_STDOUT:-}" ] && printf '%s' "$DOCKER_STDOUT"
exit "${DOCKER_EXIT:-0}"
STUB
  chmod +x "$work/bin/docker"
}

# A date stub producing unique, increasing timestamps so that successive dumps
# get distinct filenames regardless of how fast they run.
make_date_stub() {
  cat > "$work/bin/date" <<'STUB'
#!/usr/bin/env sh
c=$(cat "${DATE_COUNTER:-/dev/null}" 2>/dev/null || echo 0)
c=$((c + 1))
printf '%s' "$c" > "${DATE_COUNTER:-/dev/null}"
printf '202601010000%04dZ\n' "$c"
STUB
  chmod +x "$work/bin/date"
}

run_backup() {
  _exit=${1:-0}
  _stdout=${2-$DEFAULT_DUMP}
  _read=${3:-0}
  _keep=${4-}
  set +e
  PATH="$work/bin:$PATH" \
    LMS_ENV_FILE="$work/.env" \
    DOCKER_ARGV_LOG="$work/argv.log" \
    DOCKER_STDIN_LOG="$work/stdin.log" \
    DOCKER_EXIT="$_exit" \
    DOCKER_STDOUT="$_stdout" \
    DOCKER_READ_STDIN="$_read" \
    DATE_COUNTER="$work/date.count" \
    LIBRIS_BACKUP_KEEP="$_keep" \
    sh "$backup_script" "$work/out" > "$work/stdout" 2> "$work/stderr"
  RC=$?
  set -e
}

# A dump body large enough to survive gzip and clear the size floor.
DEFAULT_DUMP="-- MySQL dump 10.13
CREATE TABLE books (id BIGINT PRIMARY KEY, title VARCHAR(255), isbn VARCHAR(20));
CREATE TABLE borrow_records (id BIGINT PRIMARY KEY, book_id BIGINT, returned DATE);
"
i=0
while [ "$i" -lt 120 ]; do
  DEFAULT_DUMP="$DEFAULT_DUMP
INSERT INTO books VALUES ($i,'Title number $i with padding text to bulk it up','ISBN000$i');"
  i=$((i + 1))
done

echo "backup-mysql.sh regression tests"

# ---------------------------------------------------------------------------
echo
echo "dotenv parsing"

make_docker_stub

# 1. The reported production failure: an unquoted, space-separated JAVA_OPTS.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_ROOT_PASSWORD=synthetic-root-secret
LMS_DB_PASSWORD=synthetic-app-secret
LMS_ADMIN_PASSWORD=synthetic-admin-secret
LMS_DB_NAME=librarydb
JAVA_OPTS=-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom
ENVEOF
run_backup
if [ "$RC" -eq 0 ] && ls "$work/out"/libris-*.sql.gz >/dev/null 2>&1; then
  ok "unquoted space-separated JAVA_OPTS does not break the script (regression)"
else
  no "unquoted space-separated JAVA_OPTS does not break the script (regression)" \
    "rc=$RC $(head -1 "$work/stderr")"
fi

# The old implementation sourced the env file; prove we no longer execute it.
canary="$work/pwned"
rm -f "$canary"
cat > "$work/.env" <<ENVEOF
LMS_DB_NAME=librarydb
EVIL=\$(touch $canary)
JAVA_OPTS=-XX:MaxRAMPercentage=70.0
ENVEOF
run_backup
if [ ! -e "$canary" ]; then
  ok "env file is parsed, not executed as shell code"
else
  no "env file is parsed, not executed as shell code" "canary file was created"
fi

# 2. Values containing spaces survive intact.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=library db
LMS_DB_BACKUP_USERNAME=backup user
LMS_DB_BACKUP_PASSWORD=synthetic backup pass
ENVEOF
run_backup 0 "$DEFAULT_DUMP" 1
if [ "$RC" -eq 0 ] &&
  grep -qx 'library db' "$work/argv.log" &&
  grep -q '^user=backup user$' "$work/stdin.log"; then
  ok "unquoted values keep their spaces"
else
  no "unquoted values keep their spaces" "rc=$RC argv=$(tr '\n' '|' < "$work/argv.log")"
fi

# 3. Quoted values are unquoted correctly (username arrives via the defaults file).
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME="quoted_db"
LMS_DB_BACKUP_USERNAME='single quoted'
LMS_DB_BACKUP_PASSWORD="double \"quoted\" secret"
ENVEOF
run_backup 0 "$DEFAULT_DUMP" 1
if [ "$RC" -eq 0 ] &&
  grep -qx 'quoted_db' "$work/argv.log" &&
  grep -q '^user=single quoted$' "$work/stdin.log" &&
  grep -q '^password=double "quoted" secret$' "$work/stdin.log"; then
  ok "double- and single-quoted values are parsed"
else
  no "double- and single-quoted values are parsed" \
    "rc=$RC stdin=$(tr '\n' '|' < "$work/stdin.log")"
fi

# 4. Later assignments win, as in Compose.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=first_db
LMS_DB_NAME=second_db
ENVEOF
run_backup
if [ "$RC" -eq 0 ] && grep -qx 'second_db' "$work/argv.log"; then
  ok "the last assignment wins"
else
  no "the last assignment wins" "argv=$(tr '\n' '|' < "$work/argv.log")"
fi

# 5. 'export' prefix is tolerated.
cat > "$work/.env" <<'ENVEOF'
export LMS_DB_NAME=exported_db
ENVEOF
run_backup
if [ "$RC" -eq 0 ] && grep -qx 'exported_db' "$work/argv.log"; then
  ok "export-prefixed keys are read"
else
  no "export-prefixed keys are read" "argv=$(tr '\n' '|' < "$work/argv.log")"
fi

# 6. Missing LMS_DB_NAME falls back to librarydb.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_PASSWORD=synthetic-app-secret
ENVEOF
run_backup
if [ "$RC" -eq 0 ] && grep -qx 'librarydb' "$work/argv.log"; then
  ok "missing LMS_DB_NAME defaults to librarydb"
else
  no "missing LMS_DB_NAME defaults to librarydb" "argv=$(tr '\n' '|' < "$work/argv.log")"
fi

# ---------------------------------------------------------------------------
echo
echo "credential hygiene"

# 7. Dedicated-account path: no secret anywhere in argv; the password is
#    delivered through stdin instead.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
LMS_DB_BACKUP_USERNAME=synthetic_backup_user
LMS_DB_BACKUP_PASSWORD=synthetic_backup_password
LMS_DB_ROOT_PASSWORD=synthetic_root_secret
ENVEOF
run_backup 0 "$DEFAULT_DUMP" 1
if [ "$RC" -eq 0 ] &&
  ! grep -q 'synthetic_backup_password' "$work/argv.log" &&
  ! grep -q 'synthetic_root_secret' "$work/argv.log" &&
  grep -q '^password=synthetic_backup_password$' "$work/stdin.log"; then
  ok "dedicated-account path keeps the password out of argv"
else
  no "dedicated-account path keeps the password out of argv" \
    "rc=$RC argv=$(tr '\n' '|' < "$work/argv.log")"
fi

# 8. Default path passes no password literal and does not use root; the
#    MYSQL_PWD it sets is a variable reference, not a value.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
LMS_DB_ROOT_PASSWORD=synthetic_root_secret
ENVEOF
run_backup
if [ "$RC" -eq 0 ] &&
  ! grep -q 'synthetic_root_secret' "$work/argv.log" &&
  grep -q 'export MYSQL_PWD="\$MYSQL_PASSWORD"' "$work/argv.log"; then
  ok "default path passes no password literal and does not use root"
else
  no "default path passes no password literal and does not use root" \
    "argv=$(tr '\n' '|' < "$work/argv.log")"
fi

# 9. Nothing secret may be written to stdout/stderr.
run_backup 0 "$DEFAULT_DUMP" 1
if ! grep -q 'synthetic_backup_password' "$work/stdout" "$work/stderr" &&
  ! grep -q 'synthetic_root_secret' "$work/stdout" "$work/stderr"; then
  ok "secrets are not printed to stdout or stderr"
else
  no "secrets are not printed to stdout or stderr" "output leaked a secret"
fi

# 7. Compose strips an inline comment from an unquoted value. A password with
#    " # comment" appended must resolve to the password alone, or authentication
#    fails against the server.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
LMS_DB_BACKUP_USERNAME=synthetic_backup_user
LMS_DB_BACKUP_PASSWORD=synthetic-secret # trailing comment
ENVEOF
run_backup 0 "$DEFAULT_DUMP" 1
if [ "$RC" -eq 0 ] && grep -q '^password=synthetic-secret$' "$work/stdin.log"; then
  ok "inline comment is stripped from an unquoted value"
else
  no "inline comment is stripped from an unquoted value" \
    "rc=$RC stdin=$(tr '\n' '|' < "$work/stdin.log")"
fi

# 8. A '#' not preceded by a space is literal and must survive: real passwords
#    contain '#'.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
LMS_DB_BACKUP_USERNAME=synthetic_backup_user
LMS_DB_BACKUP_PASSWORD=synthetic#se#cret
ENVEOF
run_backup 0 "$DEFAULT_DUMP" 1
if [ "$RC" -eq 0 ] && grep -q '^password=synthetic#se#cret$' "$work/stdin.log"; then
  ok "a '#' without a preceding space stays literal"
else
  no "a '#' without a preceding space stays literal" \
    "stdin=$(tr '\n' '|' < "$work/stdin.log")"
fi

# 9. Inside quotes a '#' is literal even when preceded by a space, and a
#    comment-looking suffix is part of the value.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
LMS_DB_BACKUP_USERNAME=synthetic_backup_user
LMS_DB_BACKUP_PASSWORD="quoted # not a comment"
ENVEOF
run_backup 0 "$DEFAULT_DUMP" 1
if [ "$RC" -eq 0 ] && grep -q '^password=quoted # not a comment$' "$work/stdin.log"; then
  ok "a quoted value keeps its '#' and comment-like text"
else
  no "a quoted value keeps its '#' and comment-like text" \
    "stdin=$(tr '\n' '|' < "$work/stdin.log")"
fi

# 10. The in-container defaults file must be unique per invocation, so that
#     concurrent backups cannot overwrite each other's credentials.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
LMS_DB_BACKUP_USERNAME=synthetic_backup_user
LMS_DB_BACKUP_PASSWORD=synthetic_backup_password
ENVEOF
run_backup 0 "$DEFAULT_DUMP" 1
if grep -q 'cnf=$(mktemp ' "$work/argv.log" &&
  ! grep -q 'cnf=/tmp/.libris-backup-client.cnf' "$work/argv.log" &&
  grep -q 'chmod 600 "\$cnf"' "$work/argv.log"; then
  ok "in-container credentials file is unique and explicitly chmod 600"
else
  no "in-container credentials file is unique and explicitly chmod 600" \
    "argv=$(tr '\n' '|' < "$work/argv.log")"
fi

# ---------------------------------------------------------------------------
echo
echo "failure handling"

rm -rf "$work/out"

# 10. Missing env file fails clearly and non-zero.
rm -f "$work/.env"
run_backup
if [ "$RC" -ne 0 ] && grep -q 'not found' "$work/stderr"; then
  ok "missing env file fails with a clear message"
else
  no "missing env file fails with a clear message" "rc=$RC"
fi

# 11. Half-configured backup account is rejected rather than silently ignored.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
LMS_DB_BACKUP_USERNAME=synthetic_backup_user
ENVEOF
run_backup
if [ "$RC" -ne 0 ] && grep -q 'LMS_DB_BACKUP_PASSWORD' "$work/stderr"; then
  ok "half-configured backup account is rejected"
else
  no "half-configured backup account is rejected" "rc=$RC"
fi

# 12. A failing dump must exit non-zero and leave no publishable archive.
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
ENVEOF
run_backup 3 "partial garbage"
leftovers=$(find "$work/out" -name 'libris-*.sql.gz' 2>/dev/null | wc -l | tr -d ' ')
if [ "$RC" -ne 0 ] && [ "$leftovers" = "0" ]; then
  ok "a failed dump exits non-zero and publishes no archive"
else
  no "a failed dump exits non-zero and publishes no archive" "rc=$RC leftovers=$leftovers"
fi

# 13. A silently-truncated dump is rejected by the size floor.
rm -rf "$work/out"
run_backup 0 "tiny"
leftovers=$(find "$work/out" -name 'libris-*.sql.gz' 2>/dev/null | wc -l | tr -d ' ')
if [ "$RC" -ne 0 ] && [ "$leftovers" = "0" ]; then
  ok "an implausibly small dump is rejected"
else
  no "an implausibly small dump is rejected" "rc=$RC leftovers=$leftovers"
fi

# 14. No partial files are left behind on failure.
if [ -z "$(find "$work/out" -name '*.partial*' 2>/dev/null)" ]; then
  ok "no .partial files are left behind"
else
  no "no .partial files are left behind" "$(find "$work/out" -name '*.partial*')"
fi

# 15. Successful dumps are not world-readable and the directory is private.
rm -rf "$work/out"
run_backup
produced=$(find "$work/out" -name 'libris-*.sql.gz' 2>/dev/null | head -1)
if [ "$RC" -eq 0 ] && [ -n "$produced" ]; then
  mode=$(ls -l "$produced" | cut -c1-10)
  dirmode=$(ls -ld "$work/out" | cut -c1-10)
  if [ "$mode" = "-rw-------" ] && [ "$dirmode" = "drwx------" ]; then
    ok "dumps are 0600 and the backup directory is 0700"
  else
    no "dumps are 0600 and the backup directory is 0700" "file=$mode dir=$dirmode"
  fi
else
  no "dumps are 0600 and the backup directory is 0700" "rc=$RC, no dump produced"
fi

# 16. Retention keeps the 14 newest.
rm -rf "$work/out"
make_date_stub
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
ENVEOF
i=0
while [ "$i" -lt 18 ]; do
  run_backup
  [ "$RC" -eq 0 ] || break
  i=$((i + 1))
done
count=$(ls -1 "$work/out"/libris-*.sql.gz 2>/dev/null | wc -l | tr -d ' ')
if [ "$i" -eq 18 ] && [ "$count" -le 14 ]; then
  ok "retention keeps at most 14 dumps (created 18, kept $count)"
else
  no "retention keeps at most 14 dumps" "created=$i kept=$count"
fi

# 17. LIBRIS_BACKUP_KEEP is honoured (28 days at a six-hourly cadence).
rm -rf "$work/out"
make_date_stub
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
ENVEOF
i=0
while [ "$i" -lt 8 ]; do
  run_backup 0 "$DEFAULT_DUMP" 0 5
  [ "$RC" -eq 0 ] || break
  i=$((i + 1))
done
count=$(ls -1 "$work/out"/libris-*.sql.gz 2>/dev/null | wc -l | tr -d ' ')
if [ "$i" -eq 8 ] && [ "$count" -eq 5 ]; then
  ok "LIBRIS_BACKUP_KEEP prunes to the configured count (created 8, kept $count)"
else
  no "LIBRIS_BACKUP_KEEP prunes to the configured count" "created=$i kept=$count"
fi

# 18. A non-numeric retention value is rejected rather than silently defaulted.
rm -rf "$work/out"
run_backup 0 "$DEFAULT_DUMP" 0 "not-a-number"
if [ "$RC" -ne 0 ] && grep -q 'LIBRIS_BACKUP_KEEP' "$work/stderr"; then
  ok "invalid LIBRIS_BACKUP_KEEP is rejected"
else
  no "invalid LIBRIS_BACKUP_KEEP is rejected" "rc=$RC"
fi

# 19. The manifest describes the artifact, not a hand-written list.
dump=$(find "$work/out" -name 'libris-*.sql.gz' 2>/dev/null | head -1)
rm -rf "$work/out"
cat > "$work/.env" <<'ENVEOF'
LMS_DB_NAME=librarydb
ENVEOF
DOCKER_STDOUT="$DEFAULT_DUMP" run_backup
dump=$(find "$work/out" -name 'libris-*.sql.gz' 2>/dev/null | head -1)
if [ -n "$dump" ]; then
  sh "$repo_dir/scripts/backup-manifest.sh" "$dump" "$work/manifest.json" >/dev/null 2>&1
  rc=$?
  if [ "$rc" -eq 0 ] && grep -q '"sha256"' "$work/manifest.json" &&
    grep -q '"row_counts"' "$work/manifest.json" &&
    grep -q '"flyway"' "$work/manifest.json"; then
    ok "manifest records sha256, row counts and Flyway version"
  else
    no "manifest records sha256, row counts and Flyway version" "rc=$rc"
  fi
  sum_a=$(sha256sum "$dump" | awk '{print $1}')
  sum_b=$(sed -n 's/.*"sha256": "\([a-f0-9]*\)".*/\1/p' "$work/manifest.json")
  if [ "$sum_a" = "$sum_b" ]; then
    ok "manifest sha256 matches the artifact"
  else
    no "manifest sha256 matches the artifact" "manifest=$sum_b actual=$sum_a"
  fi
else
  no "manifest records sha256, row counts and Flyway version" "no dump produced"
fi

echo
echo "passed: $pass  failed: $fail"
[ "$fail" -eq 0 ] || exit 1
exit 0
