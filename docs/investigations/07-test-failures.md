> Historical snapshot — records the test-configuration diagnosis from its
> original date. It is not current test setup guidance; see
> [TESTING.md](../TESTING.md).

# Investigation 07: test failures

This investigation recorded a prior mismatch between environment-provided
admin configuration and test login setup. Its original sample credential
values are intentionally not reproduced here. The general lesson remains:
Spring environment-variable precedence can affect test configuration, so
tests should use the same configured value as the seeder without printing it.

For current test properties and commands, inspect
`backend/src/test/resources/application.properties`, the test classes, and the
current [testing guide](../TESTING.md). Do not apply historical workarounds
without checking those sources.
