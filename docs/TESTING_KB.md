> Historical snapshot — the detailed test counts and configuration examples
> below this file's former version no longer describe the current test tree.
> Use [TESTING.md](TESTING.md) for current commands and test boundaries.

# Testing knowledge base

The historical test inventory and troubleshooting notes were superseded as
the application, tests, and CI workflow changed. Current Maven, Python, and
Node commands are listed in [TESTING.md](TESTING.md), and the actual CI jobs
are defined in `.github/workflows/ci.yml`.

Test reports are date-specific evidence. A prior count or passing result must
not be presented as a result for the current checkout unless the corresponding
command is run and its output is checked. Never print credentials, password
values, cookies, CSRF tokens, session identifiers, or resolved environment
configuration when debugging tests.
