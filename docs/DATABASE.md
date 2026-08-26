# Database

> **Source of truth as of:** 30 July 2026

**Engine:** MySQL 8 (production) / H2 in MySQL-compatibility mode (tests)  
**Schema management:** Hibernate `ddl-auto=update` (production) / `ddl-auto=create-drop` (tests)  
**Migrations:** None — no Flyway or Liquibase. Schema is generated from entity annotations.

See [ARCHITECTURE.md](ARCHITECTURE.md) for entity design rationale and [API.md](API.md) for endpoint reference.

---

## Entity Relationship Diagram

```mermaid
erDiagram
    ACCOUNTS {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR password_hash
        VARCHAR role
        BOOLEAN enabled
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    STUDENT_PROFILES {
        BIGINT id PK
        BIGINT account_id FK_UK
        VARCHAR name
        VARCHAR email UK
        VARCHAR phone
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    LIBRARIAN_PROFILES {
        BIGINT id PK
        BIGINT account_id FK_UK
        VARCHAR name
        VARCHAR phone
        INT age
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    BOOKS {
        BIGINT id PK
        VARCHAR title
        VARCHAR author
        VARCHAR category
        VARCHAR isbn UK
        DATE published_date
        BOOLEAN available
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    MAGAZINES {
        BIGINT id PK
        VARCHAR title
        VARCHAR publisher
        DATE issue_date
        VARCHAR category
        VARCHAR featured_article
        BOOLEAN available
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    NEWSPAPERS {
        BIGINT id PK
        VARCHAR title
        VARCHAR publisher
        DATE publication_date
        VARCHAR top_headlines
        BOOLEAN available
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    BORROW_RECORDS {
        BIGINT id PK
        BIGINT book_id FK
        BIGINT magazine_id FK
        BIGINT newspaper_id FK
        BIGINT student_id FK
        VARCHAR borrower_name
        VARCHAR borrower_email
        VARCHAR borrower_phone
        DATE borrow_date
        DATE due_date
        DATE return_date
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    AUDIT_LOGS {
        BIGINT id PK
        TIMESTAMP timestamp
        BIGINT actor_id
        VARCHAR actor_username
        VARCHAR actor_role
        VARCHAR action
        VARCHAR entity_type
        BIGINT entity_id
        VARCHAR description
        VARCHAR ip_address
        VARCHAR user_agent
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    ACCOUNTS ||--o| STUDENT_PROFILES : "has student profile"
    ACCOUNTS ||--o| LIBRARIAN_PROFILES : "has librarian profile"
    STUDENT_PROFILES ||--o{ BORROW_RECORDS : "borrows"
    BOOKS ||--o{ BORROW_RECORDS : "appears in"
    MAGAZINES ||--o{ BORROW_RECORDS : "appears in"
    NEWSPAPERS ||--o{ BORROW_RECORDS : "appears in"
```

---

## Table Definitions

### `accounts`

Authentication identity. An admin may have no profile.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto-increment | |
| `username` | `VARCHAR(50)` | `UNIQUE`, `NOT NULL` | Login identifier |
| `password_hash` | `VARCHAR(100)` | `NOT NULL` | BCrypt hash |
| `role` | `VARCHAR(20)` | `NOT NULL` | `ADMIN`, `LIBRARIAN`, or `STUDENT` |
| `enabled` | `BOOLEAN` | `NOT NULL`, default `true` | Account active state |
| `created_at` | `TIMESTAMP` | Auto-populated | Via `@CreatedDate` |
| `updated_at` | `TIMESTAMP` | Auto-populated | Via `@LastModifiedDate` |

### `student_profiles`

Student display and contact data. One-to-one with `accounts`.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto-increment | |
| `account_id` | `BIGINT` | `UNIQUE`, `NOT NULL`, FK → `accounts.id` | One profile per account |
| `name` | `VARCHAR(100)` | `NOT NULL` | Display name |
| `email` | `VARCHAR(100)` | `UNIQUE`, `NOT NULL` | Contact email (`uk_student_profiles_email`) |
| `phone` | `VARCHAR(20)` | `NOT NULL` | Contact phone |
| `created_at` | `TIMESTAMP` | Auto-populated | |
| `updated_at` | `TIMESTAMP` | Auto-populated | |

### `librarian_profiles`

Librarian display and contact data. One-to-one with `accounts`.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto-increment | |
| `account_id` | `BIGINT` | `UNIQUE`, `NOT NULL`, FK → `accounts.id` | One profile per account |
| `name` | `VARCHAR(100)` | `NOT NULL` | Display name |
| `phone` | `VARCHAR(20)` | `NOT NULL` | Contact phone |
| `age` | `INT` | `NOT NULL` | Inherited from reference scope |
| `created_at` | `TIMESTAMP` | Auto-populated | |
| `updated_at` | `TIMESTAMP` | Auto-populated | |

### `books`

Library catalogue. The `available` flag is a denormalized current-state field.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto-increment | |
| `title` | `VARCHAR(200)` | `NOT NULL` | Book title |
| `author` | `VARCHAR(200)` | | Book author |
| `category` | `VARCHAR(100)` | | Book category / genre |
| `isbn` | `VARCHAR(50)` | `UNIQUE` | ISBN identifier |
| `published_date` | `DATE` | | Publication date |
| `available` | `BOOLEAN` | `NOT NULL`, default `true` | `true` = available for borrow |
| `created_at` | `TIMESTAMP` | Auto-populated | |
| `updated_at` | `TIMESTAMP` | Auto-populated | |

### `magazines`

Magazine catalogue. The `available` flag is a denormalized current-state field.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto-increment | |
| `title` | `VARCHAR(200)` | `NOT NULL` | Magazine title |
| `publisher` | `VARCHAR(200)` | | Publisher name |
| `issue_date` | `DATE` | | Issue date |
| `category` | `VARCHAR(100)` | | Category (e.g., Science, Tech) |
| `featured_article` | `VARCHAR(200)` | | Featured article title |
| `available` | `BOOLEAN` | `NOT NULL`, default `true` | `true` = available for borrow |
| `created_at` | `TIMESTAMP` | Auto-populated | |
| `updated_at` | `TIMESTAMP` | Auto-populated | |

### `newspapers`

Newspaper catalogue. The `available` flag is a denormalized current-state field.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto-increment | |
| `title` | `VARCHAR(200)` | `NOT NULL` | Newspaper title |
| `publisher` | `VARCHAR(200)` | | Publisher name |
| `publication_date` | `DATE` | | Publication date |
| `top_headlines` | `VARCHAR(500)` | | Top headlines |
| `available` | `BOOLEAN` | `NOT NULL`, default `true` | `true` = available for borrow |
| `created_at` | `TIMESTAMP` | Auto-populated | |
| `updated_at` | `TIMESTAMP` | Auto-populated | |

### `borrow_records`

Durable loan history. Never deleted — records are preserved for audit.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto-increment | |
| `book_id` | `BIGINT` | nullable, FK → `books.id` | Borrowed book (null if magazine/newspaper) |
| `magazine_id` | `BIGINT` | nullable, FK → `magazines.id` | Borrowed magazine (null if book/newspaper) |
| `newspaper_id` | `BIGINT` | nullable, FK → `newspapers.id` | Borrowed newspaper (null if book/magazine) |
| `student_id` | `BIGINT` | nullable, FK → `student_profiles.id` | Borrowing student |
| `borrower_name` | `VARCHAR(100)` | `NOT NULL` | Snapshot from student profile |
| `borrower_email` | `VARCHAR(100)` | `NOT NULL` | Snapshot from student profile |
| `borrower_phone` | `VARCHAR(20)` | `NOT NULL` | Snapshot from student profile |
| `borrow_date` | `DATE` | `NOT NULL` | When the item was borrowed |
| `due_date` | `DATE` | nullable | Expected return date (default 14 days) |
| `return_date` | `DATE` | nullable | `NULL` = currently borrowed |
| `created_at` | `TIMESTAMP` | Auto-populated | |
| `updated_at` | `TIMESTAMP` | Auto-populated | |

### `audit_logs`

Server-side audit trail. Stores denormalized actor and entity references for query performance.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto-increment | |
| `timestamp` | `TIMESTAMP` | `NOT NULL` | When the action occurred |
| `actor_id` | `BIGINT` | | Account ID of the actor |
| `actor_username` | `VARCHAR(50)` | | Username of the actor |
| `actor_role` | `VARCHAR(20)` | | Role of the actor |
| `action` | `VARCHAR(20)` | `NOT NULL` | CREATE, UPDATE, DELETE, LOGIN, LOGOUT, BORROW, RETURN |
| `entity_type` | `VARCHAR(30)` | `NOT NULL` | BOOK, STUDENT, LIBRARIAN, MAGAZINE, NEWSPAPER, BORROW_RECORD, ACCOUNT, PROFILE |
| `entity_id` | `BIGINT` | | ID of the affected entity |
| `description` | `VARCHAR(500)` | `NOT NULL` | Human-readable description |
| `ip_address` | `VARCHAR(45)` | | Client IP address |
| `user_agent` | `VARCHAR(500)` | | Client user agent string |
| `created_at` | `TIMESTAMP` | Auto-populated | |
| `updated_at` | `TIMESTAMP` | Auto-populated | |

---

## Relationships

| From | To | Cardinality | FK Column | Constraints |
|------|----|-------------|-----------|-------------|
| `accounts` | `student_profiles` | 1 : 0..1 | `student_profiles.account_id` | `UNIQUE`, `NOT NULL` |
| `accounts` | `librarian_profiles` | 1 : 0..1 | `librarian_profiles.account_id` | `UNIQUE`, `NOT NULL` |
| `books` | `borrow_records` | 1 : 0..* | `borrow_records.book_id` | nullable |
| `magazines` | `borrow_records` | 1 : 0..* | `borrow_records.magazine_id` | nullable |
| `newspapers` | `borrow_records` | 1 : 0..* | `borrow_records.newspaper_id` | nullable |
| `student_profiles` | `borrow_records` | 1 : 0..* | `borrow_records.student_id` | nullable |

---

## Constraints and Indexes

| Table | Constraint | Purpose |
|-------|------------|---------|
| `accounts` | `username` UNIQUE | Prevent duplicate login identities |
| `student_profiles` | `account_id` UNIQUE, FK | Enforce one profile per account |
| `librarian_profiles` | `account_id` UNIQUE, FK | Enforce one profile per account |
| `books` | `isbn` UNIQUE | Prevent duplicate catalogue entries |
| `borrow_records` | `book_id` FK | Referential integrity to books |
| `borrow_records` | `magazine_id` FK | Referential integrity to magazines |
| `borrow_records` | `newspaper_id` FK | Referential integrity to newspapers |
| `borrow_records` | `student_id` FK (nullable) | Referential integrity to students |

Hibernate `ddl-auto=update` generates indexes for primary keys and unique constraints automatically. No additional custom indexes are defined.

---

## Auditing

All entities extend `AuditableEntity`, which provides:

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
```

JPA auditing is enabled by `@EnableJpaAuditing` on `LibraryManagementApplication`.

---

## Schema Management

- **Production:** `spring.jpa.hibernate.ddl-auto=update` — Hibernate generates or alters tables from entity annotations. No migration files exist.
- **Tests:** `spring.jpa.hibernate.ddl-auto=create-drop` — schema is created on test start and dropped on exit.
- **Seeding:** `AdminSeeder` (`CommandLineRunner`) creates an `admin` account with `ROLE_ADMIN` if no `admin` username exists. Reads `lms.admin.username` and `lms.admin.password` from configuration. Throws `IllegalStateException` if the password is null or blank.

**Known gap:** No Flyway or Liquibase migration strategy. Schema changes are applied directly by Hibernate. For production use, a migration tool should be adopted to provide versioned, repeatable schema management.

---

## Referential Actions

- **Deleting a book, magazine, or newspaper with borrow history:** `BookService.delete()`, `MagazineService.delete()`, and `NewspaperService.delete()` check for existing borrow records and throw `ConflictException` (409) if found. History is never cascade-deleted.
- **Deleting a profile:** No explicit `CascadeType` or `orphanRemoval` is configured on the `@OneToOne` relationship in `StudentProfile` or `LibrarianProfile`. Whether the linked `Account` is orphaned on profile deletion depends on Hibernate's FK constraint generation — this is a known gap.
