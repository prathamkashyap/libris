-- V3__borrow_record_due_date.sql — add due_date to borrow_records for loan period management
ALTER TABLE borrow_records
    ADD COLUMN due_date DATE NULL AFTER borrow_date;
