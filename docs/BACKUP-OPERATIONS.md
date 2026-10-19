# Backup operations

How the Libris offsite backup works, how to validate it, and how to recover from
it. This document describes the workflow only; it contains no credentials,
key material, object identifiers, or production data.

## Architecture

A backup run is executed by `libris-backup.service` via
`/usr/local/sbin/libris-backup-run` (version-controlled here as
`deploy/systemd/libris-backup-run`). Each run has five stages:

1. **Logical dump.** `scripts/backup-mysql.sh` runs `mysqldump` inside the
   compose-managed MySQL container and writes a gzip archive
   (`libris-<UTC timestamp>.sql.gz`) into `/var/lib/libris-backups`. The dump is
   staged under a partial name and renamed into place only after a non-empty
   output, a `gzip -t` integrity check, and a minimum-size check all pass. The
   script never places a database password on a command line and reads its env
   file with a parser rather than sourcing it.
2. **Manifest.** `scripts/backup-manifest.sh` derives a JSON manifest from the
   dump artifact itself, recording its SHA-256, size, table list, per-table row
   counts, and Flyway migration versions. Because the manifest describes the
   artifact, a later restore can be reconciled against it.
3. **Encryption.** `scripts/libris-backup-offsite.py` encrypts the dump to the
   configured OpenPGP recipient with `gpg`, then validates the resulting
   ciphertext as a public-key encrypted message for an encryption-capable
   subkey of the pinned recipient fingerprint. Invalid or malformed output is
   rejected before anything is transmitted.
4. **Upload.** The ciphertext is uploaded to Backblaze B2 under the authorized
   `backups/` prefix. Object names derive from the dump filename, so each run
   has a unique, unambiguous identity and a retried run is detectable.
5. **Readback verification.** The uploader downloads the object it just wrote
   and compares the SHA-256 of the downloaded bytes against the local
   ciphertext. Offsite success is only recorded when that comparison matches.

`scripts/backup-offsite-run.sh` is the stage that ties these together. It
requires an explicit dump path, confirms the dump is inside the backup
directory, re-verifies its gzip integrity and its manifest SHA-256, and refuses
to proceed if the local status file does not already record success. It is
idempotent: if the object is already recorded in the status file, a repeat run
does nothing.

## Local versus offsite status

Local backup success and offsite delivery are recorded separately and must not
be conflated:

- `last-run.status` holds the local result plus a nested `offsite` result. The
  uploader merges only the `offsite` key, so an offsite outcome never rewrites
  the local one. The runner writes the local status *before* invoking the
  offsite stage, because a later rewrite would discard the merged result.
- `LAST_OFFSITE_FAILURE` is written only on offsite failure, and removed on
  offsite success.
- The runner's exit codes separate the cases: `0` local and offsite succeeded,
  `1` the local dump failed, `20` the local dump is intact but the offsite
  transfer failed, `21` the local dump is intact but the offsite stage is not
  installed. A missing offsite wrapper is fatal by design; it is never silently
  skipped, because a silent skip would make the local backup look healthy while
  nothing left the instance.
- A failed offsite transfer never removes, truncates, or invalidates the local
  dump, its manifest, or the local retention set.

## Credential scope

There is currently **no valid B2 application key for this workflow**. All
previous application keys have been deleted, and offsite delivery should be
treated as unverified until a replacement is created and checked. See
[Prerequisite before the next live upload](#prerequisite-before-the-next-live-upload).

Any application key that is created for this workflow **must** be
least-privilege. The uploader enforces the required scope and aborts on any
mismatch, so a key that does not match the table below will not run:

| Required scope | Value |
|---|---|
| Bucket | the project's offsite backup bucket |
| Authorized prefix | `backups/` |
| Capabilities | `listBuckets`, `listFiles`, `readFiles`, `writeFiles` |

The key must **not** hold `deleteFiles`, `deleteBuckets`, `writeKeys`,
`deleteKeys`, `writeBuckets`, or `shareFiles`. At run time the uploader asserts
the exact capability set and the exact bucket and prefix, and aborts if the
key is scoped differently. An object created by this workflow cannot be deleted
through it, which is intentional: expiry is governed by the bucket lifecycle
rule, not by the backup key.

Credentials are read from a root-owned `0600` file on the backup host and passed
to the uploader by path only. They never appear in command-line arguments,
source files, log output, or status files. The uploader redacts authorization
tokens and key material from every message it emits, including error paths.

## Key separation

The GPG private key never leaves the operator's workstation. The backup host
holds only the public recipient key, in a dedicated keyring under the backup
directory. That host-side keyring contains the recipient's public key and its
encryption-capable subkey and contains no secret key material. It is created
once, at install time, by exporting the public key from a workstation keyring
and importing it on the host; the private key is never exported or transferred.

The host keyring lives inside a path the service is already permitted to write,
which lets `gpg` start its agent from scratch under the service's sandbox. This
was validated explicitly: a cold-start test with an empty keyring and no agent
confirmed that encryption and packet validation succeed under the production
unit's restrictions, including `ProtectSystem=strict` and `ProtectHome=read-only`.

## Retention

- **Local.** `backup-mysql.sh` keeps the newest `LIBRIS_BACKUP_KEEP` dumps and
  prunes older ones; the runner exports that value so the retention recorded in
  the status file always matches the retention actually applied. The unit file
  sets a retention of 112 local dumps.
- **Remote.** The bucket lifecycle rule is scoped to the `backups/` prefix: it
  hides objects after 30 days and deletes previously hidden objects one day
  later. Remote retention is therefore roughly 30 days and is independent of
  how often backups run. The lifecycle rule is bucket configuration and is not
  managed or verified by this workflow.

## Schedule

`deploy/systemd/libris-backup.timer` is configured for four runs per day at
00:15, 06:15, 12:15, and 18:15 UTC, with up to 15 minutes of randomized delay
and a one-minute accuracy window. `Persistent=true` causes a single catch-up
run after the host was down at a scheduled time. Overlap is impossible: systemd
will not start a second instance of a unit that is already active.

**The production timer is intentionally disabled.** It must not be enabled
without explicit review and approval. Enabling it changes production behaviour
and is a separate decision from deploying this workflow.

## Validating a backup

After any run, confirm all of the following:

1. The service reports success and `last-run.status` names the dump and
   records an `offsite` result of `ok`.
2. The dump's SHA-256 matches the `sha256` recorded in its manifest.
3. `gzip -t` succeeds on the dump.
4. The manifest's table list and row counts match the dump.
5. `LAST_OFFSITE_FAILURE` is absent.
6. No `.gpg`, `.verify-*`, or `.partial` files remain in the backup directory;
   the uploader removes its own temporary ciphertext.

## Performing a recovery test

A recovery test proves the offsite path end to end, not merely that an archive
is intact. The procedure used for the validated run was:

1. Create a uniquely named disposable MySQL container using an official image
   matching production. It publishes no ports, uses no network, and keeps its
   data directory on a size-limited ephemeral filesystem. No host directory or
   named volume is attached.
2. Retrieve the exact encrypted object using the backup key's read capability.
   Verify the downloaded ciphertext against the SHA-256 recorded in
   `last-run.status`, and independently against the object hash reported by the
   storage provider.
3. Decrypt on the workstation, where the private key lives, and stream the
   recovered plaintext directly into the disposable container. The plaintext
   is never written to disk; only its digest is recorded, and it is compared
   against the dump's SHA-256 on the host.
4. Confirm the expected schema and tables exist, that table integrity checks
   succeed, and that restored row counts reconcile with the manifest and that
   the Flyway history matches it.
5. Remove the container and confirm the local volume count is unchanged. Do not
   use `docker system prune` or `docker volume prune`.

Do not restore into the production database, and do not run the application
against the restored instance.

## Validated end-to-end run

A full production backup and offsite recovery test was completed successfully on
2026-10-19. One manually triggered production cycle produced a dump and its
manifest, encrypted to the configured recipient, uploaded, read back and
checksum-verified against the stored object. The encrypted object was then
retrieved, decrypted on the workstation, and streamed into a disposable MySQL
instance matching the production version. The recovered plaintext matched the
production dump's SHA-256 exactly; every expected table was restored, table
integrity checks passed, and restored row counts and Flyway history reconciled
with the manifest. The temporary container and all temporary files were removed,
and no production database was written to.

## Prerequisite before the next live upload

**A valid, verified B2 application key must be recreated and confirmed working
before another live offsite upload is attempted.** Existing application keys
have been deleted, so the credential currently held by the backup host must be
replaced by a newly created least-privilege key with the scope and capabilities
described above, and the uploader's authorization check must pass against it
before the workflow is considered operational.

Until that has happened, treat offsite delivery as unverified: local backups
continue to run, but a live upload should not be assumed to succeed.