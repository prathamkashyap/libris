# Documentation Audit — 2026-08-02

## Purpose and scope

This is an audit of the documentation as it exists on 2 August 2026. It is an evidence record, not a documentation synchronization pass. Statements below were checked against the current Project A source tree and configuration.

## Summary

The core architecture, API, database, deployment, and security documents are generally useful and mostly current. The primary accuracy problems are historical records that describe Flyway and production Swagger restrictions as implemented when neither exists, and frontend documents that still describe a deleted secondary stylesheet. Several reports also describe Docker and Swagger as deferred despite their current implementation.

## Documents already correct or substantially correct

| Document | Status | Notes |
| --- | --- | --- |
| `README.md` | Substantially correct | Current stack, local startup and Swagger URL match the project; refresh only after the UI migration is finalized. |
| `docs/ARCHITECTURE.md` | Correct | Accurately records MPA/session/CSRF architecture and the absence of migrations. |
| `docs/API.md` | Substantially correct | Verify endpoint examples after the student API contract is repaired. |
| `docs/DATABASE.md` | Correct | Correctly says there is no Flyway/Liquibase migration system. |
| `docs/DEPLOYMENT.md` | Substantially correct | Correctly identifies `ddl-auto=update` as a deployment risk. |
| `docs/SECURITY.md` | Substantially correct | Refresh authorization matrix after the security mismatch is fixed. |
| `docs/DECISIONS.md` | Correct historical record | Retain; the session-vs-JWT decision agrees with the implementation. |
| `docs/PERFORMANCE.md` | Substantially correct | Keep N+1 history; update only if pagination or dashboard queries change. |
| `docs/TECHNICAL_DEBT.md` | Substantially correct | Current migration, cache, and observability gaps are valuable. |

## Outdated or conflicting documents

| Document | Finding | Required future update |
| --- | --- | --- |
| `docs/CURRENT_STATE.md` | Says Flyway is in place and Swagger is production-restricted. `pom.xml` has no Flyway dependency and `SecurityConfig` permits Swagger paths. | Correct implementation status and remove the false completed-phase claim. |
| `docs/REVIEW_SUMMARY.md` | Says Flyway dependencies were added and Swagger is restricted by profile. Both claims are false. | Amend the recommendation register with verified outcomes. |
| `docs/AI_HANDOFF.md` | Carries the same incorrect Phase 3A summary. | Replace with current handoff state after the UI migration review. |
| `docs/IMPLEMENTATION_PLAN.md` | Marks Flyway/Swagger restriction as an earlier phase even though neither landed. | Re-plan them as open work and add the integration defects found in this audit. |
| `docs/investigations/04-flyway-migration.md` | States Flyway dependencies were added. | Preserve investigation history but add an explicit correction. |
| `docs/investigations/09-swagger-restriction.md` | States an `application-prod.properties` restriction exists. It does not. | Preserve investigation and add a correction with the actual exposure. |
| `docs/investigations/10-actuator-adoption.md` | References nonexistent `application-prod.properties`. | Correct the configuration inventory. |
| `docs/FRONTEND.md` | Describes two canonical stylesheets, including `static/css/styles.css`; that file is currently deleted in the worktree and no page links to it. | Synchronize after deciding the final shared-shell and stylesheet structure. |
| `docs/PROJECT_STRUCTURE.md` | Lists the obsolete `css/styles.css` and predates the new shared sidebar modules. | Regenerate its frontend inventory after migration acceptance. |
| `docs/TASKS.md`, `docs/REQUIREMENTS.md` | Still describe Docker and Swagger as deferred. | Mark implemented capabilities accurately and retain genuinely deferred scope. |
| `docs/report/PROJECT_REPORT.html`, `docs/academic/PROJECT_REPORT.typ` | Describe Docker/Swagger as out of scope. | Update only if these academic artifacts are intended to represent the current release; otherwise label them as the v1.0 historical report. |
| `docs/journal/ENGINEERING_JOURNAL.md` | Early entries claim Flyway, JWT, Thymeleaf, and PostgreSQL architecture, contradicting the repository. | Do not rewrite history; append a dated erratum explaining that the initial inventory was incorrect. |

## Uncommitted documentation awaiting classification

| Document | Audit outcome |
| --- | --- |
| `DEBUG.md` | Needs revision: useful migration observations, but it should not present unverified UI completion claims as settled facts. |
| `implementation_plan.md` | Obsolete duplicate of the documentation plan; it also references the now-removed CSS path. Archive rather than maintain two plans. |
| `docs/SECURITY_KB.md` | Needs revision after authorization and Swagger issues are resolved. |
| `docs/TESTING_KB.md` | Needs revision after test inventory is re-counted and the frontend integration suite is established. |
| `docs/journal/ENGINEERING_JOURNAL.md` | Keep as historical record, with the erratum above. |
| `docs/investigations/*` | Keep investigations 01–03 and 05–08 as history; revise 04, 09, and 10 for the false implementation claims. |

## Documentation to defer until code decisions are settled

1. Frontend architecture, project structure, setup screenshots, and implementation plan: after the theme migration is either accepted or revised.
2. Security and API documentation: after the Swagger, OAuth, authorization, and student-dashboard contracts are fixed and tested.
3. Academic exports: after deciding whether they are historical submissions or release-facing portfolio material.

## Evidence used

- `backend/pom.xml` has no Flyway dependency.
- `backend/src/main/resources/application.properties` has no production Swagger-disable configuration.
- `backend/src/main/java/com/example/lms/security/SecurityConfig.java` permits `/swagger-ui/**` and `/v3/api-docs/**`.
- Current pages load `static/styles.css`; none reference `static/css/styles.css`.

