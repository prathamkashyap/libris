# Architecture

Libris is a Java 21 / Spring Boot application with a static multi-page browser
frontend. Spring Boot serves HTML, CSS, and native JavaScript ES modules from
`backend/src/main/resources/static/`; pages call the same-origin JSON API.

```text
Browser pages and ES modules
        │ same-origin Fetch API, session cookie, CSRF header
        ▼
Spring MVC controllers → transactional services → Spring Data JPA → MySQL
                                                   └──────────────→ H2 in dev/tests
```

Controllers handle HTTP and DTO conversion. Services implement business rules
and transactions. Repositories perform persistence. Spring Security applies
authorization by URL pattern. Flyway owns versioned MySQL schema changes;
Hibernate schema generation is not the production migration mechanism.

## Runtime profiles

- `h2`: local in-memory development with JPA `create-drop`; Flyway disabled.
- Default/Docker: MySQL with Flyway migrations.
- `prod`: requires production database/admin configuration, secure session
  cookies and forwarded-header support; limits Actuator to health and disables
  SpringDoc.
- `oauth`: optional Google OIDC; verify its configuration and existing-account
  requirements before enabling.

## Catalog and circulation

Books, magazines, and newspapers are catalog entities. A book has one
availability flag; there is no physical-copy entity, so the model cannot
describe multiple copies of one title. Borrow records refer to student and
catalog entities and retain circulation history. Consult JPA mappings and
Flyway scripts for exact constraints and relationships.

## Deployment boundary

The root Dockerfile and `docker-compose.yml` define the supported container
stack. The app service is loopback-published by default; MySQL is internal to
the Compose network. Public TLS termination is host-level in the
operator-reported deployment. See [DEPLOYMENT.md](DEPLOYMENT.md); repository
configuration does not prove what is currently deployed.
