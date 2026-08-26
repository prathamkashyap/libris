# Investigation 03 — Can H2 Be Scoped to Test-Only?

## Question

Can the H2 database dependency be scoped to `test` only, removing it from the runtime classpath?

## Reason Investigated

H2 is an in-memory database typically used for testing. If it can be scoped to `test`, the production artifact would be smaller and would not include unnecessary dependencies. However, H2 is also commonly used for local development workflows.

## Files Examined

- `pom.xml`
- `src/main/resources/application.properties`
- `src/main/resources/application-h2.properties`
- `src/test/resources/application-test.properties`

## Search Commands Used

```bash
grep -rn "h2\|H2" pom.xml
```

```bash
grep -rn "spring.datasource\|jdbc:h2\|ddl-auto" src/main/resources/application*.properties
```

```bash
grep -rn "spring.profiles\|spring.config" src/main/resources/application*.properties
```

## Evidence

- `application-h2.properties` defines `spring.datasource.url=jdbc:h2:mem:lms;DB_CLOSE_DELAY=-1`.
- This profile is **not** test-only — it is used for local development (`spring.profiles.active=h2`).
- The `jdbc:h2:` URL scheme requires the H2 JDBC driver, which is provided by the `h2` dependency.
- If H2 is scoped to `test`, the `application-h2.properties` profile will fail at runtime with a `ClassNotFoundException` for the H2 driver.
- The `application-h2.properties` file lives under `src/main/resources`, not `src/test/resources`.

## Findings

**H2 cannot be scoped to test-only.** The `application-h2.properties` profile is used for local development and lives in `src/main/resources`. It requires the H2 driver at runtime.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| Keep H2 at runtime scope | Local dev works | Slightly larger artifact |
| Move H2 to test scope, remove h2 profile | Smaller artifact | Breaks local dev workflow |
| Use H2 test scope + external DB for dev | Clean separation | More setup complexity |
| Use a dev-only profile with H2, packaged separately | Best of both | Over-engineered for this project |

## Decision

H2 stays at runtime scope. It is required by the `h2` dev profile for local development. The marginal increase in artifact size is acceptable.

## Verification

- `application-h2.properties` confirmed under `src/main/resources`.
- `spring.datasource.url=jdbc:h2:...` confirmed requiring H2 driver.
- No profile-switching mechanism exists to conditionally exclude H2 at runtime.

## Remaining Uncertainty

- None. The dependency is necessary for the local dev workflow.
