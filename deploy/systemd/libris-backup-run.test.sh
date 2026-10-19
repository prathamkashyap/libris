#!/usr/bin/env sh
# Regression tests for deploy/systemd/libris-backup-run.
#
# Nothing here touches production. LIBRIS_BACKUP_DIR and LIBRIS_REPO_DIR point
# the runner at a temporary tree, and the dump, manifest and off-site scripts
# are stubs, so no database, no B2 and no GPG are involved.
#
# The behaviour under test is the exit-code contract: a missing off-site
# wrapper must never be reported as success.
#
# Run: sh deploy/systemd/libris-backup-run.test.sh
# Exits non-zero if any test fails.

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
runner="$script_dir/libris-backup-run"

tmpbase=${TMPDIR:-/tmp}
tmpbase=${tmpbase%/}
work=$(mktemp -d "$tmpbase/libris-runner-test.XXXXXX")

pass=0
fail=0
RC=0

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

# Build a fake deployment: a backup directory and a repository whose scripts
# are deterministic stubs.
setup_tree() {
  rm -rf "$work/backup" "$work/repo"
  mkdir -p "$work/backup" "$work/repo/scripts"

  cat > "$work/repo/scripts/backup-mysql.sh" <<'STUB'
#!/usr/bin/env sh
set -eu
out=$1
ts=$(date -u +%Y%m%dT%H%M%SZ)
printf 'CREATE TABLE t (id INT);\n' > "$out/.staging.sql"
gzip -9 "$out/.staging.sql"
mv "$out/.staging.sql.gz" "$out/libris-$ts.sql.gz"
chmod 600 "$out/libris-$ts.sql.gz"
STUB

  cat > "$work/repo/scripts/backup-manifest.sh" <<'STUB'
#!/usr/bin/env sh
set -eu
dump=$1
out=$2
printf '{"file":"%s","sha256":"%s"}\n' \
  "$(basename "$dump")" "$(sha256sum "$dump" | awk '{print $1}')" > "$out"
chmod 600 "$out"
STUB
  chmod +x "$work/repo/scripts/backup-mysql.sh" "$work/repo/scripts/backup-manifest.sh"
}

# The off-site wrapper stub. WRAPPER_EXIT selects the outcome.
setup_wrapper() {
  cat > "$work/repo/scripts/backup-offsite-run.sh" <<'STUB'
#!/usr/bin/env sh
echo "offsite stub invoked for $(basename "$1")"
exit "${WRAPPER_EXIT:-0}"
STUB
  chmod +x "$work/repo/scripts/backup-offsite-run.sh"
}

run_runner() {
  set +e
  LIBRIS_BACKUP_DIR="$work/backup" \
  LIBRIS_REPO_DIR="$work/repo" \
  WRAPPER_EXIT="${WRAPPER_EXIT:-0}" \
    bash "$runner" >"$work/stdout" 2>"$work/stderr"
  RC=$?
  set -e
}

echo "runner:"

# 1. Happy path: local backup succeeds and the off-site stage succeeds.
setup_tree
setup_wrapper
run_runner
if [ "$RC" -eq 0 ] && grep -q "local backup OK" "$work/stdout" &&
  [ -f "$work/backup/last-run.status" ] && [ ! -e "$work/backup/LAST_FAILURE" ]; then
  ok "a successful run with a working wrapper exits 0"
else
  no "a successful run with a working wrapper exits 0" "rc=$RC"
fi

# 2. The off-site stage is actually invoked with the dump path.
if grep -q "offsite stub invoked for libris-.*\.sql\.gz" "$work/stdout"; then
  ok "the off-site wrapper is invoked with the explicit dump path"
else
  no "the off-site wrapper is invoked with the explicit dump path" "not invoked"
fi

# 3. The status file records local success before the off-site result is known.
if grep -q '"status": "ok"' "$work/backup/last-run.status" &&
  grep -q '"offsite": "not_configured"' "$work/backup/last-run.status"; then
  ok "local success is recorded before the off-site stage runs"
else
  no "local success is recorded before the off-site stage runs" "status missing local result"
fi

# 4. A missing off-site wrapper is fatal, not a silent skip. This is the
#    regression this change exists to prevent.
setup_tree
run_runner
if [ "$RC" -eq 21 ] && grep -q "offsite stage not installed" "$work/stdout"; then
  ok "a missing off-site wrapper exits 21"
else
  no "a missing off-site wrapper exits 21" "rc=$RC"
fi

# 5. It is reported as a failure, never as success.
if [ "$RC" -ne 0 ] && ! grep -q "offsite transfer verified" "$work/stdout"; then
  ok "a missing off-site wrapper never reports off-site success"
else
  no "a missing off-site wrapper never reports off-site success" "claimed success"
fi

# 6. The failure is recorded durably so it survives a reboot.
if [ -f "$work/backup/LAST_FAILURE" ] &&
  grep -q "offsite wrapper missing" "$work/backup/LAST_FAILURE"; then
  ok "a missing off-site wrapper writes LAST_FAILURE"
else
  no "a missing off-site wrapper writes LAST_FAILURE" "marker not written"
fi

# 7. The local dump survives: the fault is in delivery, not in the backup.
if ls -1 "$work/backup"/libris-*.sql.gz >/dev/null 2>&1; then
  ok "the local dump is preserved when the wrapper is missing"
else
  no "the local dump is preserved when the wrapper is missing" "no dump on disk"
fi

# 8. The local success status is not corrupted by that failure.
if grep -q '"status": "ok"' "$work/backup/last-run.status"; then
  ok "local backup status stays ok when the wrapper is missing"
else
  no "local backup status stays ok when the wrapper is missing" "local status lost"
fi

# 9. An off-site transfer failure is still distinct from the missing-wrapper case.
setup_tree
setup_wrapper
WRAPPER_EXIT=20
run_runner
if [ "$RC" -eq 20 ] && [ ! -e "$work/backup/LAST_FAILURE" ]; then
  ok "an off-site transfer failure still exits 20 and is kept separate"
else
  no "an off-site transfer failure still exits 20 and is kept separate" "rc=$RC"
fi

# 10. A wrapper that fails for any other reason propagates unchanged.
setup_tree
setup_wrapper
WRAPPER_EXIT=1
run_runner
if [ "$RC" -eq 1 ]; then
  ok "an unexpected off-site exit status is propagated"
else
  no "an unexpected off-site exit status is propagated" "rc=$RC"
fi

echo "passed: $pass  failed: $fail"
[ "$fail" -eq 0 ]