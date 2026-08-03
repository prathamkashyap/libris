-- V1__baseline.sql — Flyway baseline migration for Library Management System
-- Generated from JPA entity definitions. Matches Hibernate ddl-auto=update output.

-- ============================================================
-- ACCOUNTS
-- ============================================================
CREATE TABLE IF NOT EXISTS accounts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    enabled         BIT(1)       NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_accounts_username UNIQUE (username)
);

-- ============================================================
-- STUDENT_PROFILES
-- ============================================================
CREATE TABLE IF NOT EXISTS student_profiles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id      BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(100) NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_student_profiles_account UNIQUE (account_id),
    CONSTRAINT fk_student_profiles_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

-- ============================================================
-- LIBRARIAN_PROFILES
-- ============================================================
CREATE TABLE IF NOT EXISTS librarian_profiles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id      BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    age             INT          NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_librarian_profiles_account UNIQUE (account_id),
    CONSTRAINT fk_librarian_profiles_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

-- ============================================================
-- BOOKS
-- ============================================================
CREATE TABLE IF NOT EXISTS books (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    author          VARCHAR(200),
    isbn            VARCHAR(50),
    published_date  DATE,
    available       BIT(1)       NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_books_isbn UNIQUE (isbn)
);

-- ============================================================
-- MAGAZINES
-- ============================================================
CREATE TABLE IF NOT EXISTS magazines (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    publisher        VARCHAR(200),
    issue_date       DATE,
    category         VARCHAR(100),
    featured_article VARCHAR(200),
    available        BIT(1)       NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL
);

-- ============================================================
-- NEWSPAPERS
-- ============================================================
CREATE TABLE IF NOT EXISTS newspapers (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    publisher         VARCHAR(200),
    publication_date  DATE,
    top_headlines     VARCHAR(500),
    available         BIT(1)       NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL
);

-- ============================================================
-- BORROW_RECORDS
-- ============================================================
CREATE TABLE IF NOT EXISTS borrow_records (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id          BIGINT,
    magazine_id      BIGINT,
    newspaper_id     BIGINT,
    student_id       BIGINT,
    borrower_name    VARCHAR(100) NOT NULL,
    borrower_email   VARCHAR(100) NOT NULL,
    borrower_phone   VARCHAR(20)  NOT NULL,
    borrow_date      DATE         NOT NULL,
    return_date      DATE,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    CONSTRAINT fk_borrow_records_book     FOREIGN KEY (book_id)     REFERENCES books(id),
    CONSTRAINT fk_borrow_records_magazine FOREIGN KEY (magazine_id) REFERENCES magazines(id),
    CONSTRAINT fk_borrow_records_newspaper FOREIGN KEY (newspaper_id) REFERENCES newspapers(id),
    CONSTRAINT fk_borrow_records_student  FOREIGN KEY (student_id)  REFERENCES student_profiles(id)
);

-- ============================================================
-- AUDIT_LOGS
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp       DATETIME(6)  NOT NULL,
    actor_id        BIGINT,
    actor_username  VARCHAR(50),
    actor_role      VARCHAR(20),
    action          VARCHAR(20)  NOT NULL,
    entity_type     VARCHAR(30)  NOT NULL,
    entity_id       BIGINT,
    description     VARCHAR(500) NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL
);
