# Project Structure

```text
Library Management System/
├── README.md
├── mvnw                         Root Maven convenience wrapper
├── backend/
│   ├── pom.xml                  Spring Boot build configuration
│   ├── .mvn/                    Maven Wrapper configuration
│   ├── src/main/java/           Controllers, services, repositories, entities, DTOs, security
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── static/              HTML, CSS, JavaScript, reusable modal, SVG brand asset
│   └── src/test/                MockMvc integration and repository tests
├── docs/
│   ├── ARCHITECTURE.md          Master architecture and ADR record
│   ├── API.md                   REST API contract
│   ├── REQUIREMENTS.md          Requirements traceability
│   ├── SETUP.md                 Local configuration and run guide
│   ├── TESTING.md               Automated/manual verification guidance
│   ├── CHANGELOG.md             Release history
│   ├── TASKS.md                 Delivery and deferred-scope board
│   ├── diagrams/                ER diagram
│   └── testing/                 Black-box test matrix
└── screenshots/
    ├── desktop/                 Desktop review evidence
    └── mobile/                  Mobile review evidence
```

Generated `backend/target/`, local secrets, IDE files, operating-system files, and temporary output are excluded by `.gitignore` and are not part of the release.
