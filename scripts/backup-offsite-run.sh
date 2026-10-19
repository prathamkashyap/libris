#!/usr/bin/env sh
# Offsite transfer stage for the Libris offsite backup.
#
# Runs *after* scripts/backup-mysql.sh has published a dump and the backup
# runner has recorded that dump as locally successful. This script transfers
# exactly that dump and nothing else.
#
# Design rules, each of which is covered by scripts/backup-offsite-run.test.sh:
#
#  * The local backup outcome is never rewritten. A failure here leaves the
#    dump, its manifest, the retention set and last-run.status untouched, and
#    is reported through a separate marker, LAST_OFFSITE_FAILURE.
#  * Only a dump that has already passed its local checks is transferred. The
#    gzip integrity check and the manifest's recorded SHA-256 are re-verified
#    here, so a truncated or swapped artifact is refused rather than uploaded.
#  * The dump is passed to the uploader explicitly. The uploader's
#    newest-dump fallback is never used, so a concurrent or retried run cannot
#    cause the wrong dump to be transferred.
#  * Re-running the same dump is a no-op, so a retried service start cannot
#    upload the same object twice.
#  * Credentials are read from a 0600 root-owned file and passed by path only.
#    They are never placed in argv, in this script, or in any log line.
#
# Usage:
#   scripts/backup-offsite-run.sh <dump.sql.gz>
#
# Environment overrides (all explicit; nothing is inferred from the host):
#   LIBRIS_BACKUP_DIR      backup directory (default /var/lib/libris-backups)
#   LIBRIS_BACKUP_STATUS   status file (default $LIBRIS_BACKUP_DIR/last-run.status)
#   LIBRIS_B2_CREDENTIALS  B2 credential file (REQUIRED, no default)
#   LIBRIS_OFFSITE_UPLOADER  reviewed uploader (default alongside this script)
#   LIBRIS_B2_BUCKET       bucket name   (default libris-backups)
#   LIBRIS_B2_BUCKET_ID    bucket id, pinned (default 38fd5596187d47ffa5110c1d)
#   LIBRIS_B2_PREFIX       object prefix (default backups/)
#   LIBRIS_BACKUP_RECIPIENT GPG recipient (default libris-backup@local)
#   LIBRIS_GPG_HOME        GPG home      (default /root/.gnupg)
#
# Exit status:
#   0   the offsite object is present and verified
#   20  the local backup is intact but the offsite transfer failed
#   1   a precondition failed; nothing was transferred
#
# 20 is deliberately distinct from 1: the caller can tell "the backup itself is
# in trouble" from "the backup is fine but it did not leave the instance", and
# can page on the second without treating the first as a new condition.

set -eu

log() { printf '%s offsite %s\n' "$(date -u +%FT%TZ)" "$*"; }

die() {
  log "ERROR: $*"
  exit 1
}

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# The dump is named by the caller rather than discovered, so no glob and no
# "newest file" heuristic can select an artifact this run did not produce.
dump=${1:-}
[ -n "$dump" ] || die "usage: backup-offsite-run.sh <dump.sql.gz>"

# Resolve the backup directory before deriving anything from it. Comparing a
# `pwd`-resolved path against an unresolved one is not safe: a caller may
# legitimately spell the same directory with a trailing or doubled slash.
BACKUP_DIR=${LIBRIS_BACKUP_DIR:-/var/lib/libris-backups}
[ -d "$BACKUP_DIR" ] || die "backup directory $BACKUP_DIR does not exist"
BACKUP_DIR=$(CDPATH= cd -- "$BACKUP_DIR" && pwd)

# The credential path is required, never defaulted. Naming the file explicitly
# is what guarantees this workflow uses the key the operator intended: a
# default path would let a misconfiguration silently transfer with whatever
# key last occupied it, including an over-privileged one.
CREDENTIALS=${LIBRIS_B2_CREDENTIALS:-}
[ -n "$CREDENTIALS" ] ||
  die "LIBRIS_B2_CREDENTIALS must name the B2 credential file explicitly"

STATUS_FILE=${LIBRIS_BACKUP_STATUS:-$BACKUP_DIR/last-run.status}
UPLOADER=${LIBRIS_OFFSITE_UPLOADER:-$script_dir/libris-backup-offsite.py}
BUCKET=${LIBRIS_B2_BUCKET:-libris-backups}
BUCKET_ID=${LIBRIS_B2_BUCKET_ID:-38fd5596187d47ffa5110c1d}
PREFIX=${LIBRIS_B2_PREFIX:-backups/}
RECIPIENT=${LIBRIS_BACKUP_RECIPIENT:-libris-backup@local}
GPG_HOME=${LIBRIS_GPG_HOME:-/root/.gnupg}

OFFSITE_EXIT=20

# Confine the transfer to the backup directory. This is what keeps a synthetic
# canary fixture, a stray archive, or any other file on the host from being
# uploaded by the production workflow.
[ -f "$dump" ] || die "$dump is not a file"
case "$dump" in
  *.sql.gz) ;;
  *) die "refusing to transfer $dump: the dump must be a .sql.gz artifact" ;;
esac

dump_dir=$(CDPATH= cd -- "$(dirname -- "$dump")" && pwd)
[ "$dump_dir" = "$BACKUP_DIR" ] ||
  die "refusing to transfer $dump: it is outside the backup directory $BACKUP_DIR"

[ -r "$dump" ] || die "$dump is not readable"

# --- local success must already be recorded ---------------------------------
# The runner writes this file only after the dump has passed its own checks.
# Without it there is no evidence the dump is complete, so nothing is sent.
[ -f "$STATUS_FILE" ] || die "status file $STATUS_FILE is missing"
local_status=$(sed -n 's/.*"status": *"\([a-z]*\)".*/\1/p' "$STATUS_FILE" | head -1)
[ "$local_status" = "ok" ] ||
  die "refusing to transfer: $STATUS_FILE reports status '$local_status', not 'ok'"

# --- the artifact must still be the one the manifest describes --------------
gzip -t "$dump" || die "$dump failed its gzip integrity check"

manifest="$dump.manifest.json"
[ -f "$manifest" ] || die "manifest $manifest is missing"

dump_sha=$(sha256sum "$dump" | awk '{print $1}')
manifest_sha=$(sed -n 's/.*"sha256": *"\([a-f0-9]*\)".*/\1/p' "$manifest" | head -1)
[ -n "$manifest_sha" ] || die "manifest $manifest records no sha256"
[ "$dump_sha" = "$manifest_sha" ] ||
  die "refusing to transfer: $dump does not match the sha256 in its manifest"

# --- a repeated run must not upload the same object twice -------------------
expected_object="${PREFIX}$(basename -- "$dump").gpg"
recorded_object=$(sed -n 's/.*"object": *"\([^"]*\)".*/\1/p' "$STATUS_FILE" | head -1)
if [ "$recorded_object" = "$expected_object" ]; then
  log "already transferred as $expected_object; nothing to do"
  exit 0
fi

[ -f "$UPLOADER" ] || die "uploader $UPLOADER is missing"
[ -r "$CREDENTIALS" ] || die "credentials $CREDENTIALS are missing"

log "transferring $(basename -- "$dump") as $expected_object"

# The dump path is passed explicitly; the uploader's newest-dump fallback is
# deliberately unreachable from here.
if "$UPLOADER" \
  --dump-file "$dump" \
  --backup-dir "$BACKUP_DIR" \
  --credentials "$CREDENTIALS" \
  --bucket "$BUCKET" \
  --bucket-id "$BUCKET_ID" \
  --prefix "$PREFIX" \
  --recipient "$RECIPIENT" \
  --gpg-home "$GPG_HOME" \
  --status-file "$STATUS_FILE" \
  --failure-file "$BACKUP_DIR/LAST_OFFSITE_FAILURE"
then
  log "offsite transfer verified: $expected_object"
  exit 0
fi

# The local dump, its manifest and its retention set are untouched. The
# uploader has already written the failure marker and merged a failed offsite
# status into the status file; the local "status" key is preserved.
log "offsite transfer FAILED; the local backup remains intact"
exit "$OFFSITE_EXIT"