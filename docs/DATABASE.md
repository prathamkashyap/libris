# Database

Production and Docker use MySQL. Local `h2` development and most tests use an
isolated in-memory H2 database; a migration-focused test enables Flyway on H2.
The production profile sets Hibernate schema generation to `none` and uses
Flyway.

## Schema evolution

Current checked-in migrations:

| Migration | Change |
|---|---|
| V1 | Baseline schema |
| V2 | Unique student profile email constraint |
| V3 | Borrow-record due date |
| V4 | Book category |
| V5 | Borrow-record return-date and due-date indexes |

Do not edit an already-applied migration. Add a new versioned SQL migration
for schema changes and review MySQL compatibility before deployment.

## Main entities and constraints

The model includes accounts and roles, student and librarian profiles, books,
magazines, newspapers, borrow records, and audit records. Profiles associate
with accounts; borrow records associate a student with a book, magazine, or
newspaper reference as represented by the entity mapping. Migrations and JPA
mappings are authoritative for exact foreign keys, nullable columns, lengths,
and indexes.

Books have a Boolean availability field and category; there is no separate
physical-copy entity. A catalog row count is not a count of real-world copies.
The starter catalog is illustrative data, not verified stock.

## Persistence and safety

The root Compose file declares a named MySQL volume. Ordinary container
recreation preserves it; `docker compose down -v` removes named volumes and
is destructive. Keep backups off-host and test a restore procedure before
relying on them. An application image rollback does not reverse migrations or
application data changes.

Production credentials are supplied through protected environment
configuration. Never print resolved Compose configuration or database
credentials when troubleshooting.
