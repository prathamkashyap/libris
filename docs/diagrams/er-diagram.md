# ER Diagram

> **Source of truth as of:** 30 July 2026

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
        VARCHAR email
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

**Tables:** 8 (`accounts`, `student_profiles`, `librarian_profiles`, `books`, `magazines`, `newspapers`, `borrow_records`, `audit_logs`)

**Key design decisions:**
- `accounts.username` is unique; passwords stored as BCrypt hashes.
- `student_profiles.account_id` and `librarian_profiles.account_id` are unique one-to-one links.
- `books.isbn` is unique when supplied; `null` ISBN values remain permitted.
- `borrow_records` supports books, magazines, or newspapers via nullable FKs.
- Borrower name, email, and phone are snapshot fields copied from the student profile at borrow time.
- `audit_logs` stores denormalized actor/entity references for query performance.
- All entities inherit `created_at`/`updated_at` auditing timestamps via `AuditableEntity`.
