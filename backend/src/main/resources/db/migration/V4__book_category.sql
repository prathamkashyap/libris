-- V4__book_category.sql — add category to books table
ALTER TABLE books
    ADD COLUMN category VARCHAR(100) NULL AFTER author;
