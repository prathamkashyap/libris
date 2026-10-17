# Documentation coverage and limits

This index identifies the current guides and the evidence they describe. A
documentation review can compare prose with source and configuration, but it
does not prove runtime behavior or production deployment state.

| Topic | Current guide | Primary evidence |
|---|---|---|
| Public overview and quick start | `../README.md` | Build files, source tree |
| Local setup | `SETUP.md` | Root Compose, Maven wrapper, profiles |
| API routes | `API.md` | Spring controllers, DTOs, security config |
| Architecture | `ARCHITECTURE.md` | Backend packages, frontend tree, profiles |
| Schema and migrations | `DATABASE.md`, `diagrams/er-diagram.md` | JPA entities and Flyway SQL |
| Authentication/security | `SECURITY.md` | Spring Security and profile properties |
| Frontend | `FRONTEND.md` | Static HTML/CSS/JS tree |
| Test commands | `TESTING.md` | Maven config, tests, CI workflow |
| Deployment | `DEPLOYMENT.md` | Dockerfile/Compose plus qualified operator reports |
| Catalog import | `CATALOG_SEED.md` | Import CLI, tests, manifest implementation |
| Research workflows | `scripts/dev-seed/`, `scripts/dev-realdata/` docs | Scripts and their tests/data assumptions |

Historical plans, investigations, journals, and dated reports retain their
original context. Their banners explain that they are not current setup or
deployment guidance. They should not be interpreted as fresh verification.

The Markdown inventory is reviewed during repository closeout, but links,
anchors, runtime claims, and external resources may still require separate
manual verification. Test results must always be reported with the exact
command and date they were run.
