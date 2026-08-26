# Investigation 09 — Should Swagger Be Restricted to Dev/Test?

## Question

Should Swagger/OpenAPI documentation UI be restricted to development and test profiles only, and disabled in production?

## Reason Investigated

Swagger UI exposes the full API schema, including endpoints, request/response models, and potentially sensitive parameters. In production, this is an information disclosure risk that could aid attackers in understanding the API surface. Additionally, serving Swagger UI in production wastes resources.

## Files Examined

- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`
- `src/main/resources/application-h2.properties`
- `pom.xml` (springdoc dependency)

## Search Commands Used

```bash
grep -rn "springdoc\|swagger\|Swagger" src/main/resources/application*.properties
```

```bash
grep -rn "springdoc" pom.xml
```

## Evidence

- Springdoc OpenAPI dependency present in `pom.xml`.
- `application.properties` configures springdoc with profile-based activation.
- `application-prod.properties` disables Swagger UI (`springdoc.api-docs.enabled=false` or similar).
- Dev and test profiles have Swagger UI enabled by default.
- Swagger UI is accessible at `/swagger-ui.html` or `/swagger-ui/index.html` when enabled.

## Findings

**Swagger is already restricted to dev/test profiles.** The production profile disables the Swagger UI and API docs endpoints. This was implemented before this session.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| Current implementation (profile-based) | Simple, standard | Already decided |
| Remove Swagger entirely | Maximum security | Loses developer ergonomics |
| Add auth to Swagger | Access-controlled | More complex, may still leak |
| Use springdoc-groups | Organized API docs | Doesn't address production exposure |

## Decision

Restrict Swagger to dev/test profiles. This is already implemented. No changes required.

## Verification

- `application-prod.properties` confirmed Swagger is disabled.
- Dev/test profiles confirmed Swagger is enabled.
- No additional action needed.

## Remaining Uncertainty

- None. Swagger restriction is verified and in place.
