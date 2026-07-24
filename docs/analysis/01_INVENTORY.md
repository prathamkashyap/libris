# Phase 1 — Repository Inventory

> Generated on 2026-07-24 from source code. Every file in the repository (excluding `target/`, `.git/`, `node_modules/`, `.idea/`, `.vscode/`) has been read and classified below.

---

## Controllers

```
[x] AuthController            — backend/src/main/java/com/example/lms/controller/AuthController.java
[x] BookController             — backend/src/main/java/com/example/lms/controller/BookController.java
[x] BorrowRecordController     — backend/src/main/java/com/example/lms/controller/BorrowRecordController.java
[x] DashboardController        — backend/src/main/java/com/example/lms/controller/DashboardController.java
[x] LibrarianController        — backend/src/main/java/com/example/lms/controller/LibrarianController.java
[x] ProfileController          — backend/src/main/java/com/example/lms/controller/ProfileController.java
[x] StudentController          — backend/src/main/java/com/example/lms/controller/StudentController.java
```

No controllers referenced in code could not be located. All 7 are present.

---

## Services

```
[x] AuthService                — backend/src/main/java/com/example/lms/service/AuthService.java
[x] BookService                — backend/src/main/java/com/example/lms/service/BookService.java
[x] BorrowRecordService        — backend/src/main/java/com/example/lms/service/BorrowRecordService.java
[x] DashboardService           — backend/src/main/java/com/example/lms/service/DashboardService.java
[x] LibrarianService           — backend/src/main/java/com/example/lms/service/LibrarianService.java
[x] StudentService             — backend/src/main/java/com/example/lms/service/StudentService.java
```

All 6 services are present. `AccountUserDetailsService` is classified under Security below (it implements `UserDetailsService`, not a domain service).

---

## Repositories

```
[x] AccountRepository          — backend/src/main/java/com/example/lms/repository/AccountRepository.java
[x] BookRepository             — backend/src/main/java/com/example/lms/repository/BookRepository.java
[x] BorrowRecordRepository     — backend/src/main/java/com/example/lms/repository/BorrowRecordRepository.java
[x] LibrarianProfileRepository — backend/src/main/java/com/example/lms/repository/LibrarianProfileRepository.java
[x] StudentProfileRepository   — backend/src/main/java/com/example/lms/repository/StudentProfileRepository.java
```

All 5 repositories are present. All extend `JpaRepository`.

---

## Entities / Models

```
[x] Account                    — backend/src/main/java/com/example/lms/entity/Account.java
[x] AuditableEntity            — backend/src/main/java/com/example/lms/entity/AuditableEntity.java          (abstract @MappedSuperclass)
[x] Book                       — backend/src/main/java/com/example/lms/entity/Book.java
[x] BorrowRecord               — backend/src/main/java/com/example/lms/entity/BorrowRecord.java
[x] LibrarianProfile           — backend/src/main/java/com/example/lms/entity/LibrarianProfile.java
[x] Role                       — backend/src/main/java/com/example/lms/entity/Role.java                      (enum: ADMIN, LIBRARIAN, STUDENT)
[x] StudentProfile             — backend/src/main/java/com/example/lms/entity/StudentProfile.java
```

All 7 entity/model types are present (5 concrete `@Entity` + 1 `@MappedSuperclass` + 1 enum).

---

## DTOs

```
[x] ApiErrorResponse           — backend/src/main/java/com/example/lms/dto/ApiErrorResponse.java             (record; nested FieldError record)
[x] AuthenticatedUserResponse  — backend/src/main/java/com/example/lms/dto/AuthenticatedUserResponse.java    (record)
[x] BookRequest                — backend/src/main/java/com/example/lms/dto/BookRequest.java                  (record; @NotBlank, @Size)
[x] BookResponse               — backend/src/main/java/com/example/lms/dto/BookResponse.java                 (record)
[x] BorrowRecordResponse       — backend/src/main/java/com/example/lms/dto/BorrowRecordResponse.java         (record)
[x] BorrowRequest              — backend/src/main/java/com/example/lms/dto/BorrowRequest.java                (record; @NotNull, @NotBlank, @Email, @Size)
[x] DashboardResponse          — backend/src/main/java/com/example/lms/dto/DashboardResponse.java            (record)
[x] LibrarianRequest           — backend/src/main/java/com/example/lms/dto/LibrarianRequest.java             (record; @NotBlank, @Size, @Min, @Max)
[x] LibrarianResponse          — backend/src/main/java/com/example/lms/dto/LibrarianResponse.java            (record)
[x] LibrarianUpdateRequest     — backend/src/main/java/com/example/lms/dto/LibrarianUpdateRequest.java       (record; no password field)
[x] LoginRequest               — backend/src/main/java/com/example/lms/dto/LoginRequest.java                 (record; @NotBlank)
[x] StudentRequest             — backend/src/main/java/com/example/lms/dto/StudentRequest.java               (record; @NotBlank, @Email, @Size)
[x] StudentResponse            — backend/src/main/java/com/example/lms/dto/StudentResponse.java              (record)
[x] StudentUpdateRequest       — backend/src/main/java/com/example/lms/dto/StudentUpdateRequest.java         (record; no password field)
```

All 14 DTOs are present. All are Java `record` types.

---

## Configuration Files

```
[x] application.properties     — backend/src/main/resources/application.properties                           (MySQL datasource, Hibernate ddl-auto=update, Jackson non_null, logging)
[x] application.properties     — backend/src/test/resources/application.properties                           (H2 in MySQL mode, ddl-auto=create-drop)
[x] PasswordConfig             — backend/src/main/java/com/example/lms/config/PasswordConfig.java            (@Configuration; BCryptPasswordEncoder bean)
[x] pom.xml                    — backend/pom.xml                                                              (Spring Boot 3.5.0, Java 21, 7 dependencies)
[x] maven-wrapper.properties   — backend/.mvn/wrapper/maven-wrapper.properties
[x] mvnw                       — backend/mvnw                                                                 (Maven wrapper script — Unix)
[x] mvnw.cmd                   — backend/mvnw.cmd                                                             (Maven wrapper script — Windows)
[x] mvnw (root symlink)        — mvnw                                                                         (root-level wrapper, 117 bytes — convenience redirect)
[x] .gitignore                 — .gitignore
```

---

## Security-Related Files

```
[x] SecurityConfig                — backend/src/main/java/com/example/lms/security/SecurityConfig.java           (@Configuration @EnableWebSecurity; CSRF, session, URL auth rules)
[x] AccountUserDetailsService     — backend/src/main/java/com/example/lms/security/AccountUserDetailsService.java (@Service implements UserDetailsService)
[x] RestAuthenticationEntryPoint  — backend/src/main/java/com/example/lms/security/RestAuthenticationEntryPoint.java (@Component; returns JSON 401)
[x] RestAccessDeniedHandler       — backend/src/main/java/com/example/lms/security/RestAccessDeniedHandler.java  (@Component; returns JSON 403)
[x] SpaCsrfTokenRequestHandler   — backend/src/main/java/com/example/lms/security/SpaCsrfTokenRequestHandler.java (plain-token for SPA, XOR for forms)
```

All 5 security files are present.

---

## Exception / Error Handling

```
[x] GlobalExceptionHandler     — backend/src/main/java/com/example/lms/exception/GlobalExceptionHandler.java (@RestControllerAdvice; 4 handlers)
[x] BusinessRuleException      — backend/src/main/java/com/example/lms/exception/BusinessRuleException.java  (RuntimeException with code field)
[x] ConflictException          — backend/src/main/java/com/example/lms/exception/ConflictException.java      (RuntimeException → 409)
[x] ResourceNotFoundException  — backend/src/main/java/com/example/lms/exception/ResourceNotFoundException.java (RuntimeException → 404)
```

---

## Application Entry Point

```
[x] LibraryManagementApplication — backend/src/main/java/com/example/lms/LibraryManagementApplication.java   (@SpringBootApplication @EnableJpaAuditing; seedAdmin CommandLineRunner)
```

---

## Views / Templates

No server-side template engine (Thymeleaf, JSP, Freemarker) is used. The frontend is a single-page application served as static files:

```
[x] index.html                 — backend/src/main/resources/static/index.html                                 (SPA shell; 7 page sections via data-page attributes)
```

---

## Frontend JavaScript Files

```
[x] main.js                   — backend/src/main/resources/static/js/main.js                                  (SPA orchestrator; routing, rendering, modal wiring)
[x] http.js                   — backend/src/main/resources/static/js/api/http.js                               (requestJson utility; CSRF header, credentials, error parsing)
[x] auth-api.js               — backend/src/main/resources/static/js/api/auth-api.js                           (csrf, login, logout, me, profile)
[x] books-api.js              — backend/src/main/resources/static/js/api/books-api.js                           (list with search, create)
[x] borrow-api.js             — backend/src/main/resources/static/js/api/borrow-api.js                         (list, create, returnBook)
[x] dashboard-api.js          — backend/src/main/resources/static/js/api/dashboard-api.js                       (get)
[x] librarians-api.js         — backend/src/main/resources/static/js/api/librarians-api.js                     (list, create)
[x] students-api.js           — backend/src/main/resources/static/js/api/students-api.js                       (list, create)
[x] modal.js                  — backend/src/main/resources/static/components/modal.js                           (openModal; 4 form definitions: book, student, librarian, borrow)
```

All 9 JavaScript files are present.

---

## Frontend CSS Files

```
[x] tokens.css                 — backend/src/main/resources/static/css/tokens.css         (CSS custom properties / design tokens; 331 bytes)
[x] base.css                   — backend/src/main/resources/static/css/base.css           (resets, typography, body; 590 bytes)
[x] layout.css                 — backend/src/main/resources/static/css/layout.css         (app shell, header, nav, page grid; 1,807 bytes)
[x] components.css             — backend/src/main/resources/static/css/components.css     (cards, tables, badges, buttons, modals, toasts; 3,627 bytes)
[x] responsive.css             — backend/src/main/resources/static/css/responsive.css     (mobile breakpoint overrides; 615 bytes)
```

All 5 CSS files are present. Total CSS: ~6,970 bytes (minified single-line format).

---

## Static Assets

```
[x] library-mark.svg           — backend/src/main/resources/static/assets/library-mark.svg  (brand icon used in header and login)
```

---

## Database Migrations

```
[ ] No migration files found. Schema is managed by Hibernate ddl-auto=update (production) and ddl-auto=create-drop (test).
```

---

## Test Files

```
[x] LibraryManagementIntegrationTest  — backend/src/test/java/com/example/lms/LibraryManagementIntegrationTest.java   (integration; MockMvc + admin session; 4 @Test methods)
[x] BrowserCsrfFlowIntegrationTest    — backend/src/test/java/com/example/lms/BrowserCsrfFlowIntegrationTest.java     (integration; real CSRF cookie/header flow; 1 @Test method)
[x] BookRepositoryTest                — backend/src/test/java/com/example/lms/BookRepositoryTest.java                  (repository; audit timestamps + ISBN uniqueness; 1 @Test method)
```

All 3 test files are present. Total of 6 `@Test` methods across them.

---

## Existing Documentation Files Found

```
[x] README.md                          — README.md
[x] ARCHITECTURE.md                    — docs/ARCHITECTURE.md                 (87,559 bytes — comprehensive)
[x] API.md                             — docs/API.md
[x] CHANGELOG.md                       — docs/CHANGELOG.md
[x] PROJECT_REPORT.md                  — docs/PROJECT_REPORT.md
[x] PROJECT_STRUCTURE.md               — docs/PROJECT_STRUCTURE.md
[x] REQUIREMENTS.md                    — docs/REQUIREMENTS.md
[x] SETUP.md                           — docs/SETUP.md
[x] TASKS.md                           — docs/TASKS.md
[x] TESTING.md                         — docs/TESTING.md
[x] er-diagram.md                      — docs/diagrams/er-diagram.md
[x] PROJECT_REPORT.html                — docs/report/PROJECT_REPORT.html      (HTML render of project report)
[x] README_PRINT.md                    — docs/report/README_PRINT.md
[x] print.css                          — docs/report/print.css                (print stylesheet for report)
[x] styles.css                         — docs/report/styles.css               (report styling; 22,577 bytes)
[x] black-box-test-cases.csv           — docs/testing/black-box-test-cases.csv
```

All 16 documentation/report files are present.

---

## Archive Contents Found

```
[ ] No archive/ directory exists. No archived code or previous implementations were found.
```

---

## Screenshots

```
Desktop (16 files):
[x] add_book.png               — screenshots/desktop/add_book.png
[x] add_librarian.png          — screenshots/desktop/add_librarian.png
[x] add_student.png            — screenshots/desktop/add_student.png
[x] authentication.png         — screenshots/desktop/authentication.png
[x] book_added.png             — screenshots/desktop/book_added.png
[x] books.png                  — screenshots/desktop/books.png
[x] borrow_records.png         — screenshots/desktop/borrow_records.png
[x] dashboard.png              — screenshots/desktop/dashboard.png
[x] duplicate_username.png     — screenshots/desktop/duplicate_username.png
[x] invalid_details.png        — screenshots/desktop/invalid_details.png
[x] librarians.png             — screenshots/desktop/librarians.png
[x] logout.png                 — screenshots/desktop/logout.png
[x] profile.png                — screenshots/desktop/profile.png
[x] record_a_borrow.png        — screenshots/desktop/record_a_borrow.png
[x] saved_borrow_record.png    — screenshots/desktop/saved_borrow_record.png
[x] students.png               — screenshots/desktop/students.png

Mobile (6 files):
[x] mobile_authentication.png  — screenshots/mobile/mobile_authentication.png
[x] mobile_borrow_returned.png — screenshots/mobile/mobile_borrow_returned.png
[x] mobile_dashboard.png       — screenshots/mobile/mobile_dashboard.png
[x] mobile_duplicate_isbn.png  — screenshots/mobile/mobile_duplicate_isbn.png
[x] mobile_failed_request.png  — screenshots/mobile/mobile_failed_request.png
[x] mobile_validation_failed.png — screenshots/mobile/mobile_validation_failed.png
```

---

## OS / IDE artifacts (ignored but noted)

```
[x] .DS_Store                  — backend/src/main/.DS_Store   (macOS metadata — not application code)
```

---

## Unclassified / Unresolved References

No files referenced in code (imports, routes, calls) were missing. All imports resolve within the repository.

---

## Codebase Metrics

```
Metrics
-------
Controllers:       7
Services:          6   (+ 1 UserDetailsService in security)
Repositories:      5
Entities:          7   (5 @Entity + 1 @MappedSuperclass + 1 enum)
DTOs:             14   (all Java records)
Exceptions:        4   (3 custom + 1 @RestControllerAdvice handler)
Config classes:    2   (PasswordConfig + SecurityConfig)
Views:             1   (index.html — SPA shell)
JS files:          9   (1 main + 1 http utility + 6 API modules + 1 modal component)
CSS files:         5   (tokens, base, layout, components, responsive)
Static assets:     1   (SVG brand mark)
Test files:        3   (2 integration + 1 repository test; 6 @Test methods total)
Endpoints:        24   (see breakdown below)
Roles:             3   (ADMIN, LIBRARIAN, STUDENT)
DB tables:         5   (accounts, books, borrow_records, librarian_profiles, student_profiles)
Migrations:        0   (Hibernate ddl-auto manages schema)
Documentation:    16   files across docs/ and docs/report/ and docs/testing/
Screenshots:      22   (16 desktop + 6 mobile)
Approximate LOC:  ~751 (application + test source; Java 506 + JS 99 + HTML 95 + CSS 6 lines + config 45)
```

### Endpoint Breakdown

| Controller | Method | Path | Count |
|---|---|---|---|
| AuthController | GET | `/api/auth/csrf` | |
| | POST | `/api/auth/login` | |
| | POST | `/api/auth/logout` | |
| | GET | `/api/auth/me` | **4** |
| BookController | GET | `/api/books` | |
| | GET | `/api/books/{id}` | |
| | POST | `/api/books` | |
| | PUT | `/api/books/{id}` | |
| | DELETE | `/api/books/{id}` | **5** |
| BorrowRecordController | GET | `/api/borrow-records` | |
| | POST | `/api/borrow-records` | |
| | POST | `/api/borrow-records/{id}/return` | **3** |
| DashboardController | GET | `/api/dashboard` | **1** |
| LibrarianController | GET | `/api/librarians` | |
| | GET | `/api/librarians/{id}` | |
| | POST | `/api/librarians` | |
| | PUT | `/api/librarians/{id}` | |
| | DELETE | `/api/librarians/{id}` | **5** |
| ProfileController | GET | `/api/profile` | **1** |
| StudentController | GET | `/api/students` | |
| | GET | `/api/students/{id}` | |
| | POST | `/api/students` | |
| | PUT | `/api/students/{id}` | |
| | DELETE | `/api/students/{id}` | **5** |
| **Total** | | | **24** |

---

### Note on LOC

The CSS and many Java files are written in a highly minified / single-line style (e.g., entire controller class bodies on a single line). The 751-line count reflects physical lines. If these were reformatted to conventional style, the equivalent LOC would be approximately **1,500–2,000 lines**.

---

*Phase 1 complete. Awaiting confirmation to proceed to Phase 2.*
