# Project Structure

> **Source of truth as of:** 30 July 2026

```text
Library Management System/
├── README.md                          Project overview and quick start
├── mvnw                               Root Maven convenience wrapper
├── backend/
│   ├── pom.xml                        Spring Boot build configuration
│   ├── .mvn/                          Maven Wrapper configuration
│   ├── .env.example                   Environment variable template
│   ├── src/main/java/com/example/lms/
│   │   ├── config/                    PasswordConfig, AdminSeeder, OpenApiConfig
│   │   ├── controller/                14 REST controllers
│   │   ├── dto/                       24 request/response records
│   │   ├── entity/                    8 entities + 1 superclass + 3 enums
│   │   ├── event/                     EntityAuditEvent, AuditEventListener
│   │   ├── exception/                 3 custom exceptions + global handler
│   │   ├── repository/                8 JPA repositories
│   │   ├── security/                  5 security classes
│   │   ├── service/                   11 transactional services
│   │   └── util/                      CurrentUser, StringUtils
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── application-h2.properties
│   │   └── static/                    Frontend (18 HTML, 31 JS, 2 CSS, assets)
│   └── src/test/                      3 test files, 6 @Test methods
├── docs/
│   ├── README.md                      (same as root README)
│   ├── ARCHITECTURE.md                System design, layering, ADRs
│   ├── API.md                         REST endpoint reference
│   ├── DATABASE.md                    ER diagram, table definitions
│   ├── SECURITY.md                    Auth, authz, CSRF, sessions
│   ├── FRONTEND.md                    MPA structure, JS modules, CSS
│   ├── SETUP.md                       Local and Docker configuration
│   ├── DEPLOYMENT.md                  Docker production guide
│   ├── TESTING.md                     Test inventory and coverage gaps
│   ├── CHANGELOG.md                   Versioned release history
│   ├── REQUIREMENTS.md                Requirements traceability
│   ├── TASKS.md                       Delivery and deferred-scope board
│   ├── PROJECT_OVERVIEW.md            Academic project report
│   ├── PROJECT_STRUCTURE.md           This file
│   ├── DESIGN/
│   │   ├── DESIGN_HISTORY.md          Pre-implementation ADR discussions
│   │   └── REGISTRATION_DESIGN.md     Registration feature design
│   ├── diagrams/
│   │   └── er-diagram.md              Mermaid ER diagram
│   ├── testing/
│   │   └── black-box-test-cases.csv   Manual test matrix
│   ├── report/                        HTML report and print styles
│   └── academic/                      Typst source and PDF
└── screenshots/
    ├── desktop/                       Desktop review evidence
    └── mobile/                        Mobile review evidence
```

**Counts (verified against source):**
- 14 controllers, 11 services, 8 repositories
- 8 entities + 3 enums + 1 superclass (AuditableEntity)
- 24 DTOs (9 request + 15 response)
- 48 API endpoints
- 18 HTML files, 31 JS files, 2 CSS files
- 3 test files, 6 @Test methods

Generated `backend/target/`, local secrets, IDE files, operating-system files, and temporary output are excluded by `.gitignore` and are not part of the release.
