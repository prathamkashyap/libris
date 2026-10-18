# Testing

Run commands from the repository root unless noted. These commands are
defined by the Maven wrapper, Node tests, Python test suite, and CI workflow.

## Local checks

```sh
./mvnw spotless:check
./mvnw clean test
./mvnw clean verify
python3 -m unittest discover -s scripts/dev-seed -p 'test_phase5*.py'
node --test scripts/catalog-seed/*.test.mjs
sh scripts/backup-mysql.test.sh
git diff --check
```

Maven `verify` includes formatting, tests, and the configured JaCoCo threshold.
Python Phase 5 tests run separately in CI. Catalog-tool tests use fake API
behavior and temporary manifests; they do not verify production services. The
backup-script suite stubs `docker`, so it needs neither a database nor a
dependency, and it asserts that no credential reaches a command line. Run
relevant suites during development and report precisely which commands ran.

`sh scripts/backup-mysql-restore-test.sh` is an additional, slower drill
requiring Docker: it backs up a disposable database, restores it into a second
one and compares the data. It is not part of CI because it needs a real MySQL.

## Test boundaries

H2 provides isolated tests, not full MySQL equivalence. A dedicated
Flyway-enabled H2 test checks the migration path, but release changes still
need a reviewed production migration plan where applicable. Browser behavior
and external HTTPS require browser/client verification; MockMvc tests do not
establish public deployment health.

Historical reports contain test counts from the date they were written. Those
counts are not current run results.
