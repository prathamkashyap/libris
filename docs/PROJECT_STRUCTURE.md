# Project structure

This map describes the repository layout; inspect the tree for the full file
inventory.

```text
.
├── .github/                 CI workflow and issue/PR templates
├── AGENTS.md                Contributor/automation instructions
├── backend/
│   ├── pom.xml               Maven build and test configuration
│   └── src/
│       ├── main/java/        Spring Boot controllers, services, entities, security
│       ├── main/resources/   Application properties, Flyway SQL, static frontend
│       └── test/             Java tests and test configuration
├── docs/                    API, architecture, setup, deployment, security, research
├── scripts/
│   ├── catalog-seed/         Explicit authenticated catalog import CLI and tests
│   ├── dev-realdata/         Research/readiness scripts and definitions
│   └── dev-seed/             Synthetic data and offline forecasting work
├── Dockerfile               Root application image definition
├── docker-compose.yml       MySQL and app services
├── mvnw                     Root Maven wrapper
└── README.md                Public project entry point
```

The frontend is served from
`backend/src/main/resources/static/`: HTML pages at the static root, shared
`styles.css`, ES modules in `js/`, components in `components/`, and canonical
SVG/PNG brand assets in `assets/brand/`. The project has no separate frontend
build directory in the supported app flow.

Deployment configuration is intentionally limited to the root Dockerfile and
Compose file. The live reverse proxy and Dynu updater are host-level
operational configuration, not checked-in deployment units. See
[DEPLOYMENT.md](DEPLOYMENT.md).
