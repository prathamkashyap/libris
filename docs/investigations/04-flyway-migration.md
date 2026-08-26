# Investigation 04 — Should Flyway Be Added?

## Question

Should Flyway be added to manage database schema migrations?

## Reason Investigated

Spring Boot's `ddl-auto=update` strategy is convenient for development but risky in production — it can silently modify schemas, cause data loss, or produce non-reproducible states. Flyway provides version-controlled, repeatable, auditable migrations.

## Files Examined

- `pom.xml`
- `src/main/resources/application.properties`
- `src/main/resources/application-h2.properties`
- `src/main/resources/db/migration/` (directory check)

## Search Commands Used

```bash
grep -rn "flyway\|Flyway" pom.xml
```

```bash
grep -rn "ddl-auto\|hibernate.hbm2ddl" src/main/resources/application*.properties
```

```bash
ls src/main/resources/db/migration/ 2>/dev/null || echo "No migration directory"
```

## Evidence

- `spring-boot-starter-data-jpa` is already present in `pom.xml`.
- Flyway dependency (`flyway-core` + `flyway-mysql` if needed) has been added to `pom.xml`.
- **No migration files exist** in `src/main/resources/db/migration/`.
- `ddl-auto=update` is still the active schema management strategy.
- The Flyway integration is prepared but not yet utilized.

## Findings

Flyway dependency has been added to `pom.xml` for future use, but no migration files have been created. Schema is still managed by `ddl-auto=update`. The dependency addition is a preparatory step.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| Add Flyway now with initial migration | Full migration control | Requires creating initial migration from current schema |
| Add dependency only, defer migrations | Prepared but not blocking | Schema still unmanaged in production |
| Use Liquibase instead | XML/JSON/YAML changelogs | Different tool, same concept |
| Keep ddl-auto=update | Simpler | Risky in production |

## Decision

Add the Flyway dependency for future use. Schema management remains `ddl-auto=update` until migration files are authored. This is a non-breaking preparatory change.

## Verification

- `flyway-core` confirmed in `pom.xml`.
- `src/main/resources/db/migration/` does not exist yet.
- `ddl-auto=update` remains active in `application.properties`.

## Remaining Uncertainty

- When migrations will be authored is undetermined.
- `ddl-auto=update` remains active and carries production risk until replaced by Flyway.
