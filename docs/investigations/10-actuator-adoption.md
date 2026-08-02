# Investigation 10 — Should Actuator Be Added?

## Question

Should Spring Boot Actuator be added to the application for production monitoring and observability?

## Reason Investigated

Production applications require health checks, metrics, and operational visibility. Without Actuator, there is no standardized way to monitor application health, check readiness for load balancers, or export metrics to monitoring systems.

## Files Examined

- `pom.xml`
- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`
- `src/main/java/com/lms/config/` (any health/metrics configuration)

## Search Commands Used

```bash
grep -rn "actuator\|health\|metrics\|info" pom.xml
```

```bash
grep -rn "management\.\|endpoints\.\|endpoint\." src/main/resources/application*.properties
```

```bash
grep -rn "logstash\|logback" pom.xml
```

## Evidence

- `spring-boot-starter-actuator` added to `pom.xml`.
- Configured endpoints: `health`, `info`, `metrics`.
- `logstash-logback-encoder` dependency added for structured JSON logging.
- `management.endpoints.web.exposure.include` configured to expose only specific endpoints.
- Production profile limits exposed endpoints to minimize attack surface.
- Health endpoint available at `/actuator/health` for load balancer checks.

## Findings

**Actuator has been added with minimal, production-appropriate endpoints.** The application now exposes health checks, application info, and metrics. Structured JSON logging via Logstash encoder enables integration with ELK/Datadog/Grafana stacks.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| Actuator with minimal endpoints (chosen) | Secure, focused | Covers basics |
| Full Actuator exposure | Maximum visibility | Security risk in production |
| Custom health indicators | Tailored checks | More code to maintain |
| Micrometer + Prometheus | Rich metrics | Additional infrastructure |
| No monitoring | Simplest | Blind in production |

## Decision

Add Actuator for production monitoring. Expose minimal endpoints (health, info, metrics). Add `logstash-logback-encoder` for structured JSON logging.

## Verification

- `spring-boot-starter-actuator` confirmed in `pom.xml`.
- `logstash-logback-encoder` confirmed in `pom.xml`.
- `management.endpoints.web.exposure.include=health,info,metrics` confirmed in properties.
- `/actuator/health` endpoint functional.

## Remaining Uncertainty

- Custom health indicators (e.g., database connectivity, external service checks) may be added in the future.
- Metrics export to Prometheus/Grafana is not yet configured but is a natural next step.
