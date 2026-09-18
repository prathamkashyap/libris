-- V5__borrow_records_indexes.sql — add indexes for overdue and active-loan query patterns
--
-- Trade-offs:
--   + Accelerates queries that filter on return_date (active loans, history) and due_date (overdue checks)
--   + These columns have no index beyond the PK; every full-table scan on borrow_records pays the cost
--   - Adds marginal write amplification on INSERT/UPDATE of borrow_records (index maintenance)
--   - Increases storage proportional to row count ( negligible at expected scale )
--
-- Not adding an index on student_id: InnoDB automatically indexes foreign-key columns.
-- Two single-column indexes are chosen over a composite because return_date is queried
-- independently in most access paths; the MySQL optimizer can combine them efficiently.

CREATE INDEX idx_borrow_records_return_date ON borrow_records (return_date);

CREATE INDEX idx_borrow_records_due_date ON borrow_records (due_date);
