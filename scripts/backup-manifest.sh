#!/usr/bin/env sh
# Emit a JSON manifest describing a backup produced by scripts/backup-mysql.sh.
#
# A gzip integrity check proves an archive is not corrupt; it says nothing about
# whether the dump is complete or restorable. This manifest records what the
# artifact actually contains, so a later restore drill can compare the restored
# database against the backup it came from rather than against a hand-written
# list of expected tables.
#
# The manifest is derived from the dump file itself, not from the live
# database: it therefore describes the artifact that would actually be
# restored.
#
# Usage:
#   scripts/backup-manifest.sh <dump.sql.gz> [output.json]
#
# With no output path the JSON is written to stdout. No secrets are read.
#
# Written for POSIX awk: tuple splitting uses index() rather than escaped
# parentheses, because BSD awk rejects /\),\(/ while gawk accepts it.

set -eu

die() {
  echo "error: $*" >&2
  exit 1
}

dump=${1:-}
out=${2:-}

[ -n "$dump" ] || die "usage: backup-manifest.sh <dump.sql.gz> [output.json]"
[ -f "$dump" ] || die "$dump not found"

case "$dump" in
  *.gz) decompress="gzip -dc" ;;
  *) decompress="cat" ;;
esac

name=$(basename "$dump")
bytes=$(wc -c < "$dump" | tr -d ' ')
sha=$(sha256sum "$dump" | awk '{print $1}')

# An apostrophe, handed to awk via -v so the program itself can stay inside a
# single-quoted shell string.
SQ=$(printf "'")

# Tables and their row counts. Extended INSERT statements pack many rows into a
# single statement, so rows are counted from the tuples, not the statements.
# A table with no rows gets an explicit empty VALUES tuple and contributes 0.
scanned=$($decompress "$dump" | awk '
  /^CREATE TABLE/ {
    if (match($0, /`[A-Za-z0-9_]+`/)) {
      t = substr($0, RSTART + 1, RLENGTH - 2)
      order[++n] = t
    }
    next
  }
  /^INSERT INTO `/ {
    match($0, /^INSERT INTO `[A-Za-z0-9_]+`/)
    t = substr($0, RSTART + 13, RLENGTH - 14)
    rest = substr($0, RSTART + RLENGTH)
    sub(/^VALUES /, "", rest)
    sub(/;$/, "", rest)
    if (rest == "()") next
    k = 0
    p = rest
    while ((q = index(p, "),(")) > 0) { k++; p = substr(p, q + 3) }
    rows[t] += k + 1
    next
  }
  END {
    ts = ""
    cs = ""
    sep = ""
    for (i = 1; i <= n; i++) {
      t = order[i]
      c = (t in rows) ? rows[t] : 0
      ts = ts sprintf("%s\"%s\"", sep, t)
      cs = cs sprintf("%s\"%s\":%d", sep, t, c)
      sep = ","
    }
    if (n == 0) ts = "null"
    print ts
    print "{" cs "}"
  }
')

table_list=$(printf '%s\n' "$scanned" | sed -n 1p)
if [ "$table_list" = "null" ]; then table_list=""; fi
counts_map=$(printf '%s\n' "$scanned" | sed -n 2p)
[ -n "$counts_map" ] || counts_map="{}"

# Flyway versions are the first quoted field of each history tuple.
flyway_versions=$($decompress "$dump" | awk -v sq="$SQ" '
  function emit(r,   a, b, v) {
    a = index(r, sq)
    if (a == 0) return
    b = index(substr(r, a + 1), sq)
    if (b == 0) return
    v = substr(r, a + 1, b - 1)
    out = out "\"" v "\","
    last = v
  }
  /^INSERT INTO `flyway_schema_history`/ {
    s = $0
    sub(/^.*VALUES /, "", s)
    sub(/;$/, "", s)
    while ((q = index(s, "),(")) > 0) {
      emit(substr(s, 1, q))
      s = substr(s, q + 3)
    }
    emit(s)
  }
  END {
    sub(/,$/, "", out)
    if (out == "") out = "null"
    print out
    print last
  }
')

versions_json=$(printf '%s\n' "$flyway_versions" | sed -n 1p)
if [ "$versions_json" = "null" ]; then versions_json=""; fi
current=$(printf '%s\n' "$flyway_versions" | sed -n 2p)
[ -n "$versions_json" ] || versions_json="null"
[ -n "$current" ] || current="null"

recorded=$(date -u +%Y-%m-%dT%H:%M:%SZ)

emit_json() {
  echo "{"
  echo "  \"file\": \"$name\","
  echo "  \"bytes\": $bytes,"
  echo "  \"sha256\": \"$sha\","
  echo "  \"recorded_utc\": \"$recorded\","
  echo "  \"tables\": [$table_list],"
  echo "  \"row_counts\": $counts_map,"
  echo "  \"flyway\": {"
  echo "    \"versions\": [$versions_json],"
  echo "    \"current\": \"$current\""
  echo "  }"
  echo "}"
}

if [ -n "$out" ]; then
  emit_json > "$out"
  chmod 600 "$out"
  echo "Wrote $out"
else
  emit_json
fi
