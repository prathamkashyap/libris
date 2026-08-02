# Engineering Decisions

This document records every significant engineering decision made during the development of the Library Management System (Project A). Each entry captures the decision, the context in which it was made, alternatives considered, rationale, trade-offs, and any relevant date or commit information.

---

## 1. Session-based Authentication over JWT

**Decision:** Use Spring Security session-based authentication with CSRF cookie protection instead of JWT tokens.

**Context:** The application is a server-rendered multi-page application (MPA) served entirely by Spring Boot. The frontend is vanilla JavaScript embedded in static HTML files. There is no separate SPA client or mobile app.

**Alternatives Considered:**
- **JWT tokens:** Stateless authentication, standard for APIs consumed by separate clients. Requires token storage on the client, refresh token rotation, and more complex security logic.
- **OAuth2 resource server:** Overkill for a single-server MPA; introduces unnecessary complexity with token introspection and validation.

**Rationale:** Session-based auth is the default behavior of Spring Security and requires zero additional configuration beyond `SessionCreationPolicy.IF_REQUIRED`. For a server-rendered MPA where the browser is the only client, sessions are simpler and more secure (no token leakage via localStorage). JWT is deferred to the future if a separate mobile or SPA client is introduced.

**Trade-offs:** Sessions are server-side state (requires sticky sessions or shared session store in a clustered deployment). JWT would scale horizontally without session affinity. For a portfolio project running on a single server, this trade-off is irrelevant.

**Date/Commit:** Initial security configuration.

---

## 2. Spring Data JPA over Raw JDBC

**Decision:** Use Spring Data JPA (Hibernate) as the persistence layer instead of raw JDBC or Spring JDBC Template.

**Context:** The application has 8 entity types (Account, Book, Magazine, Newspaper, StudentProfile, LibrarianProfile, BorrowRecord, AuditLog) with standard CRUD and search operations.

**Alternatives Considered:**
- **Raw JDBC / Spring JDBC Template:** Full control over SQL, no ORM overhead, no proxy magic. Requires writing all SQL by hand and manual result set mapping.
- **jOOQ:** Type-safe SQL, excellent for complex queries. Adds a code generation step and another dependency.
- **MyBatis:** SQL-centric with XML/annotation mappings. Less mainstream in the Spring ecosystem.

**Rationale:** Spring Data JPA provides repository abstraction with derived queries (`existsByIsbn`, `countByAvailable`), pagination support out of the box, and `@EntityGraph` for fetch optimization. The learning curve is minimal for a Spring Boot project, and the ecosystem (Hibernate, Spring Data) is well-documented.

**Trade-offs:** ORM abstraction can generate suboptimal SQL for complex joins. JPA proxies can cause lazy initialization issues outside transactions (mitigated by `spring.jpa.open-in-view=false`).

**Date/Commit:** Initial data layer setup.

---

## 3. H2 for Tests, MySQL for Production

**Decision:** Use H2 in MySQL-compatibility mode for all tests. MySQL for local development and production. H2 is included with `runtime` scope (not `test`) to support the `h2` dev profile.

**Context:** Tests need to be fast and isolated. Local development needs to run without a MySQL server. Production targets MySQL.

**Alternatives Considered:**
- **Testcontainers with MySQL:** Spins up a real MySQL Docker container for tests. More accurate but significantly slower (Docker overhead per test class).
- **H2 with default mode:** Faster but SQL dialect differences from MySQL can hide real bugs.
- **Embedded MySQL (wix-embedded-mysql):** Real MySQL in tests. Heavy dependency and licensing concerns.

**Rationale:** H2 in `MODE=MySQL` catches most dialect incompatibilities while keeping tests under a few seconds. The `runtime` scope on the `h2` dependency allows the `h2` Spring profile to be activated for local development without MySQL (`jdbc:h2:mem:lms;MODE=MySQL`). Test profile uses `create-drop` DDL for clean isolation.

**Trade-offs:** H2 is not 100% MySQL-compatible. Edge cases (e.g., specific JSON functions, certain constraint behaviors) may pass in H2 but fail in MySQL. Mitigated by integration testing against MySQL before deployment.

**Date/Commit:** Initial test and dev profile configuration.

---

## 4. Vanilla JS over React/Vue

**Decision:** Use vanilla JavaScript with ES modules for the frontend instead of a framework like React, Vue, or Angular.

**Context:** The application is a server-rendered MPA. Each HTML page is a standalone document served by Spring Boot's static resource handling. Frontend logic is limited to API calls, DOM manipulation, pagination rendering, and theme toggling.

**Alternatives Considered:**
- **React / Next.js:** Component-based UI, virtual DOM, rich ecosystem. Massive overhead for server-rendered pages that already work.
- **Vue:** Lighter than React but still requires a build step, npm tooling, and a runtime.
- **Thymeleaf / Server-side templates:** Tighter integration with Spring Boot but limits client-side interactivity and requires learning the template language.

**Rationale:** Vanilla JS with ES modules (`import`/`export`) provides adequate code organization without a build pipeline. The JS API layer (`js/api/*.js`) abstracts HTTP calls cleanly. The frontend complexity does not justify the overhead of a framework. ES modules are natively supported in modern browsers.

**Trade-offs:** No virtual DOM diffing (manual DOM updates). No component reuse model (repeated HTML patterns). No type safety in JS. For the scope of this application (15+ HTML pages, ~20 JS modules), this is acceptable.

**Date/Commit:** Initial frontend setup.

---

## 5. Spotless with Google Java Format

**Decision:** Use the Spotless Maven plugin with Google Java Format (style: GOOGLE) for automated code formatting. Enforced in CI before the build step.

**Context:** Multiple contributors or future contributors may have different formatting habits. Inconsistent formatting creates noisy diffs and slows code review.

**Alternatives Considered:**
- **AOSP style (4-space indent):** Same formatter, different convention. User explicitly chose GOOGLE style (2-space indent).
- **Checkstyle:** Rule-based, highly configurable. More setup effort and maintenance than a formatter.
- **EditorConfig only:** Enforces whitespace rules but not full formatting. Inconsistent across editors.
- **No enforcement:** Relies on developer discipline. Fails at scale.

**Rationale:** Spotless integrates directly into Maven (`mvn spotless:apply` to fix, `mvn spotless:check` to verify). Google Java Format is opinionated with zero configuration. The CI pipeline runs `spotless:check` before `mvn clean verify` to fail fast on formatting issues.

**Trade-offs:** Google Java Format enforces 2-space indentation, which may conflict with team preferences. The formatter is not configurable (by design). `spotless:apply` is a single command to fix, so friction is low.

**Date/Commit:** Build configuration established.

---

## 6. Logback JSON Logging over Log4j2

**Decision:** Use Spring Boot's default Logback with `logstash-logback-encoder` for structured JSON output instead of switching to Log4j2.

**Context:** The application runs on Spring Boot 3.5, which ships with Logback as the default logging framework. Structured (JSON) logs are needed for production log aggregation.

**Alternatives Considered:**
- **Log4j2 with JSON layout:** Log4j2 has better async performance and a JSON layout. Requires swapping Spring Boot's default logging starter, adding configuration complexity.
- **Logback with PatternLayout (text):** Simpler but not machine-parseable. Incompatible with log aggregation tools (ELK, Datadog, etc.).
- **SLF4J only (no implementation):** Not functional; needs a backend.

**Rationale:** `spring-boot-starter-actuator` and `spring-boot-starter-web` already pull in Logback. `logstash-logback-encoder:8.0` adds `LogstashEncoder` which outputs JSON with `traceId`, `spanId`, and custom fields (`app`). No dependency conflicts. Zero-configuration for the common case.

**Trade-offs:** Log4j2's async appender is measurably faster under high throughput. For this application's scale, the difference is negligible. Logback is the Spring Boot default, so less documentation to search when debugging.

**Date/Commit:** Logging configuration added with `logback-spring.xml`.

---

## 7. No Service Interfaces

**Decision:** Services are concrete classes annotated with `@Service` directly. No interface + implementation pattern.

**Context:** Services like `BookService`, `StudentService`, `LibrarianService` contain business logic with no need for multiple implementations or proxy-based AOP mocking.

**Alternatives Considered:**
- **Interface + Impl (e.g., `BookService` interface, `BookServiceImpl` class):** Standard Java EE pattern. Enables swapping implementations and easy mocking.
- **Abstract service classes:** Partial abstraction. Not commonly used in Spring.

**Rationale:** For a portfolio project with a single implementation of each service, interfaces add a layer of indirection with no practical benefit. Testing uses concrete classes directly (Spring Boot's test context caches beans). Mocking is done with Mockito on the concrete class when needed.

**Trade-offs:** If a second implementation is ever needed (e.g., for a different data source), refactoring to interfaces is trivial. The current approach is not a lock-in.

**Date/Commit:** Initial service layer design.

---

## 8. No MapStruct

**Decision:** Manual DTO mapping in service classes instead of using MapStruct or similar mapping libraries.

**Context:** The application has ~10 entity types and corresponding DTOs. Mapping logic is straightforward field copying (e.g., `Book` → `BookResponse`, `StudentRequest` → `StudentProfile`).

**Alternatives Considered:**
- **MapStruct:** Compile-time code generation, type-safe, zero reflection overhead. Requires annotation processor setup and Lombok coordination.
- **ModelMapper / ObjectMapper:** Runtime reflection-based mapping. Flexible but slower and harder to debug.
- **Manual mapping (current):** Plain Java code in service classes.

**Rationale:** Each mapping method is ~5 lines of straightforward field assignment. The number of entities does not justify adding a dependency and build-time annotation processing. Manual mapping is explicit, easy to debug, and has no hidden behavior.

**Trade-offs:** When adding a new field to an entity, the mapper must be updated manually (compiler won't catch missing fields). This is acceptable at the current scale. If the entity count grows significantly, MapStruct would become worthwhile.

**Date/Commit:** Initial DTO/entity mapping.

---

## 9. No equals/hashCode on Entities

**Decision:** JPA entities do not override `equals()` or `hashCode()`. Identity is based on database-assigned `@Id` fields only.

**Context:** All entities use `@GeneratedValue(strategy = GenerationType.IDENTITY)` for primary keys. Entities are not used in `Set` or as keys in `Map` within the application.

**Alternatives Considered:**
- **Natural key-based equals/hashCode (e.g., ISBN for Book):** Entities are equal if their business keys match. Complex to maintain across the entity lifecycle (transient vs. managed states).
- **Surrogate ID-based equals/hashCode:** Simple but broken before flush (ID is null for new entities).
- **Business key + equals by value:** Correct but verbose; requires careful handling of nullable fields.

**Rationale:** JPA identity by database ID is sufficient for this application's use case. Entities are fetched, modified, and persisted within a single transaction. They are not placed in collections where identity matters. The `@Id` field provides a stable identity once persisted.

**Trade-offs:** Two unsaved entity instances with the same field values will not be considered equal. This is not a problem because unsaved entities are never compared. If entities are ever used in `Set<Entity>` outside a transaction, this decision must be revisited.

**Date/Commit:** Initial entity design.

---

## 10. Account Orphaning Fix: Explicit Deletion in Service Layer

**Decision:** When deleting a profile (Student or Librarian), the associated Account is deleted explicitly in the service layer after the profile is deleted. Not using `cascade = CascadeType.REMOVE`, not using `@OnDelete`, not using orphan removal.

**Context:** Each `StudentProfile` and `LibrarianProfile` has a `@OneToOne` relationship to an `Account`. Deleting a profile must also delete the account to prevent orphaned accounts that can still authenticate.

**Alternatives Considered:**
- **`cascade = CascadeType.REMOVE` on `@OneToOne`:** Deleting the profile would cascade-delete the account automatically. However, the reverse is not true (deleting an account would cascade-delete the profile, which is not always desired).
- **`@OnDelete(action = OnDeleteAction.CASCADE)`:** Database-level foreign key cascade. Bypasses JPA lifecycle and event listeners. Harder to debug and audit.
- **Orphan removal (`orphanRemoval = true`):** Designed for collection relationships, not `@OneToOne`. Semantically incorrect here.
- **Explicit deletion in service layer (chosen):** `students.delete(p); accounts.delete(account);` — two lines, predictable order.

**Rationale:** Explicit deletion is visible in the code, follows a predictable order (profile first, then account, respecting FK constraints), and triggers JPA lifecycle events (audit listeners). No hidden cascade behavior. The service layer is the natural place for this business rule.

**Trade-offs:** If new entity types reference Account, the deletion logic must be updated in each service. Cascade would handle it automatically but with less control. The explicit approach is preferred for clarity.

**Date/Commit:** Student and Librarian service delete methods (`StudentService.java:114-119`, `LibrarianService.java:104-109`).

---

## 11. N+1 Fix: @EntityGraph on Repository Methods

**Decision:** Use `@EntityGraph(attributePaths = {...})` on repository query methods to eagerly load associations in a single query, instead of FETCH JOIN JPQL or DTO projections.

**Context:** `BorrowRecord` has four `@ManyToOne` associations: `book`, `magazine`, `newspaper`, `student`. The default `FetchType.LAZY` causes N+1 queries when the UI accesses `borrowRecord.getBook().getTitle()` in a loop.

**Alternatives Considered:**
- **`JOIN FETCH` in JPQL:** `SELECT r FROM BorrowRecord r JOIN FETCH r.book JOIN FETCH r.student ...` — explicit but verbose, breaks pagination (cannot use `JOIN FETCH` with `COUNT` queries), and must be duplicated per query method.
- **`@NamedEntityGraph` + `@EntityGraph`:** Same as chosen but declared at the entity level. More reusable but more boilerplate for this case.
- **DTO projections (records / `@SqlResultSetMapping`):** Maps directly to DTOs, no lazy loading. Requires writing every query as JPQL/native SQL and maintaining DTOs for every read shape.
- **`spring.jpa.properties.hibernate.fetch_size` or `batch.size`:** Reduces round trips but still multiple queries.

**Rationale:** `@EntityGraph` on the repository method is the most minimally invasive fix. It overrides `FetchType.LAZY` for specific queries without changing the entity model. Five methods in `BorrowRecordRepository` use this pattern (`findByReturnDateIsNull`, `findByReturnDateIsNotNull`, `findByStudentId`, `findByStudentIdAndReturnDateIsNull`, `findByStudentIdAndReturnDateIsNotNull`). The `search` method uses `JOIN FETCH` because `@EntityGraph` cannot be combined with custom JPQL `LIKE` queries.

**Trade-offs:** `@EntityGraph` always eagerly loads the specified paths, even if some are null. For `BorrowRecord`, only one of `book`/`magazine`/`newspaper` is non-null per record, so three extra LEFT JOINs are always executed. DTO projections would avoid this but at the cost of maintaining separate query mappings.

**Date/Commit:** BorrowRecord repository fix for N+1 queries (`BorrowRecordRepository.java:11-29`).

---

## 12. Actuator Endpoints: Health, Info, Metrics Only

**Decision:** Expose only `health`, `info`, and `metrics` Actuator endpoints. Do not expose `env`, `beans`, `configprops`, `heapdump`, or other sensitive endpoints.

**Context:** Actuator is included for production observability. Exposing sensitive endpoints (especially `env` which contains environment variables like database passwords) is a security risk.

**Alternatives Considered:**
- **Expose all endpoints with Spring Security protection:** More flexible but increases attack surface. Misconfiguration can leak secrets.
- **Expose none:** Defeats the purpose of Actuator.
- **Expose selectively via properties (chosen):** `management.endpoints.web.exposure.include=health,info,metrics` — explicit whitelist.

**Rationale:** `health` is needed for load balancer/readiness probes. `info` provides application metadata. `metrics` exposes Micrometer counters for monitoring. Other endpoints (`env`, `beans`, `configprops`, `heapdump`) expose internal state and secrets. The whitelist approach is secure by default.

**Trade-offs:** Debugging in production requires SSH access since `env` and `beans` are not exposed via HTTP. This is the intended behavior for a production deployment. Local development can use `spring-boot-devtools` or H2 console for debugging.

**Date/Commit:** Application properties configuration (`application.properties:14-16`).

---

## 13. CSS Dead Code Removal: html.light-mode / html.blue-mode Selectors

**Decision:** Removed CSS selectors targeting `html.light-mode` and `html.blue-mode` classes. The theme system uses `data-theme` attribute on the `<html>` element instead.

**Context:** The original CSS used class-based theme switching (`<html class="light-mode">`). The theme system was refactored to use `data-theme` attribute (`<html data-theme="light">`). Old selectors became dead code.

**Alternatives Considered:**
- **Keep both class and attribute selectors:** Maintains backward compatibility but adds confusion and dead code.
- **Migrate gradually:** Leave old selectors and remove later. Delays cleanup.
- **Remove immediately (chosen):** Clean removal of dead selectors. Theme is controlled solely by `data-theme`.

**Rationale:** Dead CSS selectors increase file size, confuse future maintainers, and can cause unexpected style overrides. The `data-theme` attribute approach is cleaner (attribute selectors are more semantic than class names for theming). Removing dead code is a standard hygiene practice.

**Trade-offs:** If any JavaScript code still references `.light-mode` or `.blue-mode` classes, it would break. Verified that `theme.js` uses `data-theme` exclusively.

**Date/Commit:** CSS cleanup commit.

---

## 14. Real Pagination with Page/Size Parameters

**Decision:** Implement real server-side pagination with `page` and `size` query parameters on all 8 controller list endpoints, returning `org.springframework.data.domain.Page<T>` with `totalPages`, `totalElements`, and `content`.

**Context:** Initially, all list endpoints returned `List<T>` (full result set). For a library system with potentially thousands of books, students, and borrow records, full result sets are unacceptable.

**Alternatives Considered:**
- **No pagination (original):** Return all records. Fine for development, broken for production.
- **Cursor-based pagination:** Better for real-time feeds but more complex, not supported by Spring Data's `PagingAndSortingRepository` out of the box.
- **Offset pagination (chosen):** Standard `page`/`size` params. Spring Data `PageRequest.of(page, size)` handles the rest. Returns `Page<T>` with total count.

**Rationale:** Offset pagination is the most common pattern for MPA list views. Spring Data provides it natively. The frontend `pagination.js` utility renders page buttons using `totalPages` from the response. All 8 controllers (`BookController`, `StudentController`, `LibrarianController`, `BorrowRecordController`, `MagazineController`, `NewspaperController`, `AuditController`, `ReportController`) accept `page` and `size` parameters with defaults (`page=0`, `size=10`).

**Trade-offs:** Offset pagination degrades at high page numbers (`OFFSET 10000` scans all prior rows). For this application's data volume, this is not a concern. Keyset/cursor pagination would be better for infinite scroll but is not needed here.

**Date/Commit:** Pagination refactoring across all controllers.

---

## 15. CI Ordering: Spotless Check Before Maven Verify

**Decision:** In the GitHub Actions CI pipeline, run `mvn spotless:check` before `mvn clean verify`. Fail fast on formatting violations before compiling and running tests.

**Context:** The CI pipeline (`ci.yml`) has a single job with sequential steps. Running formatting check first avoids wasting CI minutes on a build that will fail formatting anyway.

**Alternatives Considered:**
- **Spotless check after build:** Wastes CI time if formatting is wrong (compiles and tests run first).
- **Spotless check as a separate CI job:** Runs in parallel with the build. Faster wall time but doubles the number of jobs (and GitHub Actions minutes if both fail).
- **Spotless check integrated into `mvn verify`:** Would require a custom Maven profile or plugin order manipulation. Fragile.
- **Spotless check first in sequence (chosen):** `mvn spotless:check` → `mvn clean verify`. Simple, linear, fail-fast.

**Rationale:** Formatting violations are the most common and cheapest-to-fix CI failures. Running `spotless:check` first (typically <10 seconds) avoids waiting for a full Maven build (2-5 minutes) only to fail on whitespace. The CI YAML is straightforward with no matrix or parallel job complexity.

**Trade-offs:** Sequential steps mean total CI time is `spotless_time + build_time` rather than `max(spotless_time, build_time)` if parallelized. The 10-second spotless check does not justify the complexity of a separate job.

**Date/Commit:** CI pipeline configuration (`.github/workflows/ci.yml:23-29`).
