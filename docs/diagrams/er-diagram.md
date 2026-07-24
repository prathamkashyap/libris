# Initial ER Diagram

```mermaid
erDiagram
    ACCOUNTS {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR password_hash
        VARCHAR role
        BOOLEAN enabled
    }
    STUDENT_PROFILES {
        BIGINT id PK
        BIGINT account_id FK_UK
        VARCHAR name
        VARCHAR email
        VARCHAR phone
    }
    LIBRARIAN_PROFILES {
        BIGINT id PK
        BIGINT account_id FK_UK
        VARCHAR name
        VARCHAR phone
        INT age
    }
    BOOKS {
        BIGINT id PK
        VARCHAR title
        VARCHAR author
        VARCHAR isbn
        DATE published_date
        BOOLEAN available
    }
    BORROW_RECORDS {
        BIGINT id PK
        BIGINT book_id FK
        BIGINT student_id FK
        VARCHAR borrower_name
        VARCHAR borrower_email
        VARCHAR borrower_phone
        DATE borrow_date
        DATE return_date
    }
    ACCOUNTS ||--o| STUDENT_PROFILES : has
    ACCOUNTS ||--o| LIBRARIAN_PROFILES : has
    STUDENT_PROFILES ||--o{ BORROW_RECORDS : makes
    BOOKS ||--o{ BORROW_RECORDS : appears_in
```

This is the approved Day 1 schema baseline. Historical borrow records must not be cascade-deleted with their related account/profile/book.

