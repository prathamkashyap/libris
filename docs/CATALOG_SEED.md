# Illustrative starter catalog

`scripts/catalog-seed/` contains an explicitly invoked Node.js importer for a
small illustrative catalog. It is not run by Spring Boot, Docker Compose,
Flyway, or application startup, and it does not connect directly to MySQL.
The importer uses Libris's normal authenticated catalog APIs and their
validation, authorization, and audit behavior.

## Dataset and limits

The versioned dataset contains 14 books, 3 magazines, and 3 newspapers across
computing, science, fiction, and general knowledge. Sample book and magazine
categories begin with `Sample —`; newspaper titles say `Sample edition` and
their headline text says that the entry is illustrative, not current news.
Optional ISBNs and publication dates are omitted rather than guessed.

The application schema represents availability on each catalog row. It does
not represent multiple physical copies of one title, and these illustrative
rows do not establish that any physical inventory exists. A book row is marked
available by the application's normal create behavior and contributes one row
to total and available book counts.

A prior production-operation report recorded that all 20 sample rows were
created and verified through the application, with book and available-book
counts increasing by 14. This is historical operator evidence; this guide and
the importer change do not perform a new production check.

The importer never creates users or changes accounts, roles, password hashes,
borrowing records, returns, fines, or overdue records.

## Prerequisites and authentication

- Node.js 20 or newer.
- A running Libris instance and a user with the `ADMIN` or `LIBRARIAN` role.
- An interactive terminal. The username is prompted normally; the password is
  read with terminal echo disabled. Do not put credentials in arguments,
  environment variables, repository files, shell history, or chat.
- HTTPS for remote instances. HTTP is allowed only for localhost. The default
  remote origin is `https://libris.opik.net`.

The CLI bootstraps the existing CSRF cookie/header flow, keeps session cookies
in process memory, verifies the authenticated role, and logs out on completion.
Production preview still creates authentication audit events. Libris allows
one active session per account, so signing in through the CLI can expire that
account's existing browser session.

## Preview and apply

Preview is the default mode and makes no catalog writes:

```sh
node scripts/catalog-seed/import-catalog.mjs --preview
```

It authenticates, reads the dashboard and every page of the three catalog
collections, then reports planned actions. It does not create, update, or
delete catalog rows. Review its output before a separately authorized apply.

Apply to the default production origin requires both explicit apply mode and a
production confirmation flag:

```sh
node scripts/catalog-seed/import-catalog.mjs --apply --confirm-production
```

The confirmation flag is a command guard, not a substitute for operator
approval. The importer checks the user's role, reads all catalog pages, and
stops before writing if a normalized title has multiple matches or one
non-exact match. Exact matches are skipped. Before each create request it
checks the title again. Since the catalog schema does not impose a unique
natural key, suspend concurrent catalog editing during apply; this narrows but
cannot eliminate a race with manual edits.

No automatic retry is made for a create request with an uncertain outcome. A
pending manifest entry is retained before sending the request. If the response
is lost or lacks a valid record ID, stop and reconcile the catalog and audit
history before another apply. A matching row found during reconciliation is
not automatically claimed as importer-created. Do not blindly retry or edit
the manifest to clear an unresolved entry.

For a trusted non-production remote instance, `--base-url` selects the origin
and `--allow-custom-origin` explicitly permits a custom HTTPS hostname. Verify
the certificate and hostname before entering credentials. `--manifest` can
select another absolute manifest path, which must remain outside the repository
in a private directory.

## External manifest

The default manifest is stored outside the checkout at:

```text
$HOME/.local/state/libris-catalog-seed/manifest.json
```

The importer requires an absolute path outside the repository. The parent
directory must belong to the current user and deny group/other access (normally
mode `700`); the manifest is written with mode `600`. A private lock file
serializes runs using the same manifest. The manifest records the sample
version, collection, titles/keys, creation timestamps, created record IDs, and
unresolved pending creates. It contains no login credentials, but its IDs and
catalog details are operational data and should remain private. Never commit,
move, or publish it.

Deleting the local manifest does not undo production rows. It destroys the
importer's ownership and reconciliation record, making safe rollback harder.
Preserve it until the catalog records are intentionally retained or rollback
is complete.

## Verification and rollback

After apply, the importer checks the created rows through the paginated catalog
APIs and verifies the dashboard deltas: the number of books created must match
the increases in total and available books; student, librarian, and borrowed
book counts must not change. A failed verification stops the run and retains
the manifest for reconciliation; it does not make speculative repairs.

Rollback requires an authenticated `ADMIN` or `LIBRARIAN` role, checked by the
rollback workflow before reading the manifest or querying candidate rows. The
application's own endpoint authorization also remains in force. Rollback
selects only manifest-recorded IDs whose current rows still exactly match the
versioned sample data. It refuses unresolved pending creates and changed or
ambiguous rows. It checks borrowing history and leaves any borrowed row in
place. Preview removals first:

```sh
node scripts/catalog-seed/import-catalog.mjs --rollback-preview
```

Only after separately approving removal, apply against production with:

```sh
node scripts/catalog-seed/import-catalog.mjs --rollback-apply --confirm-production
```

Rollback can partially complete if a later delete fails. Each confirmed
successful deletion is removed from the manifest immediately; preserve the
manifest and reconcile any uncertain delete before retrying. The application
also prevents deletion of catalog rows with borrowing history. No account or
circulation data is removed.

## Local tests

Run the isolated tests without production credentials or database access:

```sh
node --test scripts/catalog-seed/*.test.mjs
git diff --check
```

The tests use a fake API and temporary manifests. They cover role gating,
create/skip behavior, conflicts, pagination, uncertain create responses,
dashboard deltas, account/circulation preservation, rollback history guards,
CSRF/session behavior, HTTPS/origin checks, and manifest permissions and
locking. They do not contact production or replace an approved operational
preview.
