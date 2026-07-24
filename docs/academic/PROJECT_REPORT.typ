// ==========================================================================
// Library Management System — Project Report
// Format: Typst
// ==========================================================================

// ---------------------------------------------------------------------------
// Document Setup
// ---------------------------------------------------------------------------

#let primary = rgb("#1a365d")
#let accent = rgb("#2b6cb0")
#let teal-accent = rgb("#2c7a7b")
#let light-bg = rgb("#f7fafc")
#let header-bg = rgb("#2d3748")
#let border-color = rgb("#cbd5e0")
#let note-bg = rgb("#ebf8ff")
#let note-border = rgb("#3182ce")

#set document(
  title: "Library Management System — Project Report",
  author: "Pratham Kashyap",
)

#set text(size: 11pt, lang: "en")
#set par(justify: true, leading: 0.65em)
#set heading(numbering: "1.1")

#show heading.where(level: 1): it => {
  pagebreak(weak: true)
  v(0.5em)
  text(fill: primary, size: 18pt, weight: "bold", it)
  v(0.3em)
  line(length: 100%, stroke: 1.5pt + accent)
  v(0.5em)
}

#show heading.where(level: 2): it => {
  v(0.4em)
  text(fill: accent, size: 14pt, weight: "bold", it)
  v(0.3em)
}

#show heading.where(level: 3): it => {
  v(0.3em)
  text(fill: teal-accent, size: 12pt, weight: "bold", it)
  v(0.2em)
}

// ---------------------------------------------------------------------------
// Helper Functions
// ---------------------------------------------------------------------------

#let note-box(body) = block(
  fill: note-bg, stroke: (left: 3pt + note-border), inset: (left: 12pt, rest: 10pt),
  radius: (right: 4pt), width: 100%, body,
)

#let layer-box(label, detail, fill-color) = rect(
  width: 100%, fill: fill-color, inset: 12pt, radius: 5pt,
  stroke: 0.5pt + rgb("#a0aec0"),
)[#align(center)[#text(weight: "bold")[#label] \ #text(size: 9pt)[#detail]]]

// ---------------------------------------------------------------------------
// Cover Page
// ---------------------------------------------------------------------------

#set page(paper: "a4", margin: (x: 2.5cm, y: 2.5cm), numbering: none)

#align(center + horizon)[
  #block(width: 85%)[
    #text(size: 13pt, fill: rgb("#718096"))[_Your University Name_]
    #v(0.2em)
    #text(size: 12pt, fill: rgb("#718096"))[_Department of Computer Science / Information Technology_]
    #v(2em)

    #line(length: 100%, stroke: 2pt + accent)
    #v(1em)
    #text(size: 32pt, fill: primary, weight: "bold")[Library Management System]
    #v(0.4em)
    #text(size: 14pt, fill: accent)[A Web Application for Library Operations]
    #v(1em)
    #line(length: 100%, stroke: 2pt + accent)

    #v(2.5em)
    #text(size: 13pt, weight: "bold")[Project Report]
    #v(0.3em)
    #text(size: 11pt)[Submitted in partial fulfillment of the requirements for the degree of \ _Bachelor of Technology / Bachelor of Engineering_]

    #v(3em)
    #table(
      columns: (1fr, 1fr),
      stroke: none,
      inset: 6pt,
      align: (center, center),
      [*Submitted by* \ Pratham Kashyap \ _Registration No._],
      [*Under the guidance of* \ _Guide Name_ \ _Designation_],
    )

    #v(3em)
    #text(size: 11pt)[*Academic Year 2025--2026*]
  ]
]

// ---------------------------------------------------------------------------
// Certificate
// ---------------------------------------------------------------------------

#pagebreak()

#align(center)[#text(size: 18pt, fill: primary, weight: "bold")[Certificate]]
#v(2em)

This is to certify that the project entitled *"Library Management System"*, submitted by *Pratham Kashyap* (Registration No.: \_\_\_\_\_\_\_\_\_\_) to the Department of \_\_\_\_\_\_\_\_\_\_, \_\_\_\_\_\_\_\_\_\_ University, in partial fulfillment of the requirements for the award of the degree of \_\_\_\_\_\_\_\_\_\_, is a bonafide record of the work carried out under my guidance and supervision.

#v(4em)

#grid(
  columns: (1fr, 1fr),
  align(left)[
    *Date:* \_\_\_\_\_\_\_\_\_\_ \
    *Place:* \_\_\_\_\_\_\_\_\_\_
  ],
  align(right)[
    *Guide Name* \
    _Designation_ \
    _Department_
  ],
)

#v(4em)

#align(center)[
  *Head of Department* \
  _Name_ \
  _Department_
]

// ---------------------------------------------------------------------------
// Declaration
// ---------------------------------------------------------------------------

#pagebreak()

#align(center)[#text(size: 18pt, fill: primary, weight: "bold")[Declaration]]
#v(2em)

I hereby declare that the project entitled *"Library Management System"* submitted to \_\_\_\_\_\_\_\_\_\_ University is a record of original work done by me under the guidance of \_\_\_\_\_\_\_\_\_\_, and this project has not been submitted for the award of any other degree, diploma, or fellowship.

#v(4em)

#align(right)[
  *Pratham Kashyap* \
  _Registration No._
]

#v(2em)
#align(left)[
  *Date:* \_\_\_\_\_\_\_\_\_\_ \
  *Place:* \_\_\_\_\_\_\_\_\_\_
]

// ---------------------------------------------------------------------------
// Acknowledgements
// ---------------------------------------------------------------------------

#pagebreak()

#align(center)[#text(size: 18pt, fill: primary, weight: "bold")[Acknowledgements]]
#v(2em)

I would like to express my sincere gratitude to my project guide, \_\_\_\_\_\_\_\_\_\_, for their valuable guidance and constant encouragement throughout the development of this project. I am also thankful to the Head of Department, \_\_\_\_\_\_\_\_\_\_, for providing the necessary facilities and support.

I would also like to thank my family and friends for their continuous support and motivation.

// ---------------------------------------------------------------------------
// Abstract
// ---------------------------------------------------------------------------

#pagebreak()

#align(center)[#text(size: 18pt, fill: primary, weight: "bold")[Abstract]]
#v(2em)

This project presents the design and implementation of a Library Management System --- a full-stack web application built to automate core library operations. The system handles book catalogue management, student and librarian account administration, and the complete borrow/return lifecycle with real-time availability tracking.

The backend is developed using Spring Boot 3.5 with Java 21, employing a layered MVC architecture with Spring Data JPA for persistence and Spring Security for authentication and authorization. The frontend is a responsive single-page application built with vanilla JavaScript, HTML, and CSS, communicating with the backend through a RESTful API.

The system enforces role-based access control across three user roles (Administrator, Librarian, Student), implements server-side and client-side validation, and provides CSRF protection for secure form submissions. The project includes 24 REST endpoints, 5 database tables, and a suite of integration tests covering authentication, CRUD operations, and business rule enforcement.

*Keywords:* Library Management, Spring Boot, REST API, Spring Security, Single-Page Application, Role-Based Access Control.

// ---------------------------------------------------------------------------
// Table of Contents
// ---------------------------------------------------------------------------

#pagebreak()
#set page(numbering: "1", number-align: center)
#counter(page).update(1)

#text(size: 20pt, fill: primary, weight: "bold")[Table of Contents]
#v(0.5em)
#outline(indent: 1.5em, depth: 3)

// =========================================================================
// Chapter 1: Introduction
// =========================================================================

= Introduction

== Background

Libraries are fundamental to educational institutions, providing access to knowledge resources for students and faculty. Managing a library's inventory, tracking borrowed materials, and maintaining borrower records is a complex operational task that benefits significantly from automation.

Traditional manual approaches to library management --- paper-based registers, card catalogues, and physical sign-out sheets --- are prone to errors, difficult to search, and impossible to scale. A digital library management system addresses these challenges by providing a centralized, secure, and searchable platform for all library operations.

== Problem Statement

The project addresses the need for a web-based library management system that:
- Maintains an accurate, searchable catalogue of books with real-time availability status.
- Tracks the complete lifecycle of book loans --- from borrowing to return --- with borrower records preserved for audit.
- Manages student and librarian accounts with appropriate access restrictions.
- Provides a responsive interface accessible on desktop and mobile devices.
- Enforces data integrity through server-side validation and transactional business rules.

== Objectives

+ Design and implement a layered MVC architecture using Spring Boot and Spring Security.
+ Build a responsive single-page application (SPA) with role-based navigation.
+ Implement CRUD operations for books, students, and librarians with server-side validation.
+ Develop a borrow/return workflow that atomically manages book availability.
+ Enforce role-based access control with three distinct user roles.
+ Protect the application with session-based authentication and CSRF protection.
+ Write integration tests to verify core functionality and security behavior.

== Scope and Limitations

The v1.0.0 release focuses on core library operations. The following features are intentionally deferred for future development: book categories, pagination, advanced search, fines, notifications, Docker deployment, Swagger/OpenAPI, JWT authentication, reservations, and physical-copy modelling.

// =========================================================================
// Chapter 2: Requirements Analysis
// =========================================================================

= Requirements Analysis

== Functional Requirements

#figure(
  table(
    columns: (auto, 1fr, auto),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[ID],
      text(fill: white, weight: "bold")[Requirement],
      text(fill: white, weight: "bold")[Status],
    ),
    [FR-01], [Users can log in, log out, and view current session information], [Implemented],
    [FR-02], [Admins and librarians can view dashboard with aggregate statistics], [Implemented],
    [FR-03], [Authorized staff can create, list, search, update, and delete books], [Implemented],
    [FR-04], [Admins and librarians can manage student accounts and profiles], [Implemented],
    [FR-05], [Only admins can manage librarian accounts and profiles], [Implemented],
    [FR-06], [Staff can borrow an available book, creating a record and marking the book unavailable], [Implemented],
    [FR-07], [Staff can return an active borrow record, restoring book availability], [Implemented],
    [FR-08], [The system rejects unavailable-book borrows and duplicate returns with clear errors], [Implemented],
    [FR-09], [Authenticated users can view their own profile information], [Implemented],
    [FR-10], [The frontend is responsive and works on desktop and mobile], [Implemented],
  ),
  caption: [Functional requirements and implementation status.],
)

== Non-Functional Requirements

#figure(
  table(
    columns: (auto, 1fr),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[ID],
      text(fill: white, weight: "bold")[Requirement],
    ),
    [NFR-01], [Maintain a layered Browser → Controller → Service → Repository → MySQL architecture],
    [NFR-02], [Never expose JPA entities or password hashes through the API],
    [NFR-03], [Store only BCrypt password hashes and enforce server-side role checks],
    [NFR-04], [Preserve borrow history and prevent destructive cascades from removing audit data],
    [NFR-05], [Return documented HTTP status codes and uniform JSON error responses],
    [NFR-06], [Responsive, keyboard-accessible, readable UI across viewports],
  ),
  caption: [Non-functional requirements.],
)

== Use Cases

The system supports three actors with distinct use cases:

#figure(
  table(
    columns: (auto, 1fr),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[Actor],
      text(fill: white, weight: "bold")[Use Cases],
    ),
    [*Administrator*], [Log in/out · View dashboard · Manage books (CRUD + search) · Manage students (CRUD) · Manage librarians (CRUD) · Borrow/return books · View profile],
    [*Librarian*], [Log in/out · View dashboard · Manage books (CRUD + search) · Manage students (CRUD) · Borrow/return books · View profile],
    [*Student*], [Log in/out · Browse books · View profile],
  ),
  caption: [Use cases by actor role.],
)

// =========================================================================
// Chapter 3: System Architecture
// =========================================================================

= System Architecture

== Technology Stack

#figure(
  table(
    columns: (auto, 1fr, auto),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[Layer],
      text(fill: white, weight: "bold")[Technology],
      text(fill: white, weight: "bold")[Version],
    ),
    [Backend runtime], [Java (OpenJDK)], [21],
    [Application framework], [Spring Boot], [3.5.0],
    [Security], [Spring Security], [6.5],
    [Persistence], [Spring Data JPA + Hibernate], [Managed by Boot],
    [Validation], [Jakarta Bean Validation (Hibernate Validator)], [Managed by Boot],
    [Production database], [MySQL], [8+],
    [Test database], [H2 (MySQL compatibility mode)], [Managed by Boot],
    [Build tool], [Apache Maven (with wrapper)], [Wrapper-managed],
    [Test framework], [JUnit 5 + MockMvc + Spring Security Test], [Managed by Boot],
    [Frontend], [Vanilla HTML / CSS / JavaScript (ES Modules)], [---],
    [Typography], [Google Fonts (Inter, Poppins)], [CDN],
  ),
  caption: [Technology stack.],
)

== Layered Architecture

The application follows a strict four-layer MVC architecture within a single deployable Spring Boot monolith. Each layer communicates only with its immediate neighbor, enforcing separation of concerns.

#figure(
  block(width: 75%, inset: 16pt, fill: light-bg, radius: 6pt, stroke: 0.5pt + border-color)[
    #stack(dir: ttb, spacing: 6pt,
      layer-box([Browser SPA], [HTML · CSS · JavaScript · Fetch API], rgb("#ebf8ff")),
      align(center)[#text(size: 14pt, fill: accent)[▼ JSON + CSRF Cookie/Header ▼]],
      layer-box([REST Controllers], [7 controllers · Request validation · HTTP semantics], rgb("#fefcbf")),
      align(center)[#text(size: 14pt, fill: accent)[▼ DTOs ▼]],
      layer-box([Transactional Services], [6 services · Business rules · DTO mapping], rgb("#fed7d7")),
      align(center)[#text(size: 14pt, fill: accent)[▼ Entities ▼]],
      layer-box([JPA Repositories], [5 repository interfaces · Derived query methods], rgb("#c6f6d5")),
      align(center)[#text(size: 14pt, fill: accent)[▼ JDBC ▼]],
      layer-box([MySQL Database], [5 tables · Hibernate schema management], rgb("#e9d8fd")),
    )
  ],
  caption: [Layered architecture — each layer communicates only with its immediate neighbor.],
) <fig:layers>

== Package Structure

#figure(
  table(
    columns: (auto, auto, 1fr),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[Package],
      text(fill: white, weight: "bold")[Classes],
      text(fill: white, weight: "bold")[Responsibility],
    ),
    [`com.example.lms`], [1], [Application entry point and admin seed],
    [`config`], [1], [BCrypt `PasswordEncoder` bean],
    [`controller`], [7], [REST endpoints and request validation],
    [`dto`], [14], [Immutable Java records for request/response transfer],
    [`entity`], [7], [JPA entities, mapped superclass, role enum],
    [`exception`], [4], [Domain exceptions and centralized error handler],
    [`repository`], [5], [JPA repository interfaces],
    [`security`], [5], [Spring Security configuration and handlers],
    [`service`], [6], [Transactional business logic],
  ),
  caption: [Package structure with class counts.],
)

== Project Structure

```
Library Management System/
├── backend/
│   ├── pom.xml                        Build configuration
│   ├── src/main/java/.../lms/
│   │   ├── controller/                7 REST controllers
│   │   ├── dto/                       14 Java record DTOs
│   │   ├── entity/                    JPA entities + Role enum
│   │   ├── exception/                 Custom exceptions + handler
│   │   ├── repository/                JPA repository interfaces
│   │   ├── security/                  Security configuration
│   │   └── service/                   Transactional services
│   └── src/main/resources/
│       ├── application.properties
│       └── static/                    SPA frontend
│           ├── index.html             7-page SPA shell
│           ├── css/                   Design tokens, layout, components
│           ├── js/                    SPA orchestrator + API clients
│           └── components/            Reusable modal component
├── docs/                              Project documentation
└── screenshots/                       Desktop and mobile evidence
```

// =========================================================================
// Chapter 4: Database Design
// =========================================================================

= Database Design

The database consists of five tables managed by Hibernate's automatic schema generation from JPA entity annotations. All entities inherit audit fields (`created_at`, `updated_at`) from a common `AuditableEntity` mapped superclass.

== Entity-Relationship Diagram

#figure(
  block(width: 90%, inset: 16pt, fill: light-bg, radius: 6pt, stroke: 0.5pt + border-color)[
    #set text(size: 9.5pt)
    #grid(
      columns: (1fr, auto, 1fr),
      gutter: 12pt,
      stack(dir: ttb, spacing: 12pt,
        rect(fill: rgb("#ebf8ff"), inset: 10pt, radius: 4pt, stroke: 0.5pt + accent, width: 100%)[
          #text(weight: "bold", fill: accent)[accounts] \
          `id` BIGINT PK (auto-increment) \
          `username` VARCHAR(50) UNIQUE NOT NULL \
          `password_hash` VARCHAR(100) NOT NULL \
          `role` VARCHAR(20) NOT NULL \
          `enabled` BOOLEAN NOT NULL \
          `created_at` · `updated_at`
        ],
        rect(fill: rgb("#fefcbf"), inset: 10pt, radius: 4pt, stroke: 0.5pt + rgb("#d69e2e"), width: 100%)[
          #text(weight: "bold", fill: rgb("#975a16"))[books] \
          `id` BIGINT PK (auto-increment) \
          `title` VARCHAR(200) NOT NULL \
          `author` VARCHAR(200) \
          `isbn` VARCHAR(50) UNIQUE \
          `published_date` DATE \
          `available` BOOLEAN NOT NULL \
          `created_at` · `updated_at`
        ],
      ),
      align(center + horizon)[
        #stack(dir: ttb, spacing: 24pt,
          text(size: 8pt)[1 ←→ 0..1],
          text(size: 8pt)[1 ←→ 0..1],
          text(size: 8pt)[1 ←→ 0..\*],
        )
      ],
      stack(dir: ttb, spacing: 12pt,
        rect(fill: rgb("#f0fff4"), inset: 10pt, radius: 4pt, stroke: 0.5pt + rgb("#38a169"), width: 100%)[
          #text(weight: "bold", fill: rgb("#276749"))[student\_profiles] \
          `id` BIGINT PK \
          `account_id` FK → accounts (UNIQUE) \
          `name` VARCHAR(100) NOT NULL \
          `email` VARCHAR(100) NOT NULL \
          `phone` VARCHAR(20) NOT NULL \
          `created_at` · `updated_at`
        ],
        rect(fill: rgb("#faf5ff"), inset: 10pt, radius: 4pt, stroke: 0.5pt + rgb("#805ad5"), width: 100%)[
          #text(weight: "bold", fill: rgb("#553c9a"))[librarian\_profiles] \
          `id` BIGINT PK \
          `account_id` FK → accounts (UNIQUE) \
          `name`, `age`, `phone` \
          `created_at` · `updated_at`
        ],
        rect(fill: rgb("#fed7d7"), inset: 10pt, radius: 4pt, stroke: 0.5pt + rgb("#e53e3e"), width: 100%)[
          #text(weight: "bold", fill: rgb("#9b2c2c"))[borrow\_records] \
          `id` BIGINT PK \
          `book_id` FK → books NOT NULL \
          `student_id` FK → student\_profiles \
          `borrower_name/email/phone` NOT NULL \
          `borrow_date` DATE NOT NULL \
          `return_date` DATE (nullable) \
          `created_at` · `updated_at`
        ],
      ),
    )
  ],
  caption: [Entity-relationship diagram.],
) <fig:er>

== Table Relationships

#figure(
  table(
    columns: (1fr, auto, 1fr, 1fr),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[From],
      text(fill: white, weight: "bold")[Cardinality],
      text(fill: white, weight: "bold")[To],
      text(fill: white, weight: "bold")[Join Column],
    ),
    [`accounts`], [1 : 0..1], [`student_profiles`], [`account_id` (UNIQUE, NOT NULL)],
    [`accounts`], [1 : 0..1], [`librarian_profiles`], [`account_id` (UNIQUE, NOT NULL)],
    [`books`], [1 : 0..\*], [`borrow_records`], [`book_id` (NOT NULL)],
    [`student_profiles`], [1 : 0..\*], [`borrow_records`], [`student_id` (nullable)],
  ),
  caption: [Entity relationships with join columns.],
)

== Design Decisions

- *Account-Profile separation:* Authentication credentials are stored in `accounts`, while role-specific data (email for students, age for librarians) is stored in separate profile tables. This allows shared login logic with distinct profile schemas.
- *Borrower snapshotting:* The `borrow_records` table stores a snapshot of the borrower's name, email, and phone at borrow time, ensuring the historical record remains accurate even if the student's profile is later updated.
- *Availability tracking:* A boolean `available` flag on the `books` table is toggled atomically within the same transaction as borrow/return operations, preventing race conditions.

// =========================================================================
// Chapter 5: Implementation
// =========================================================================

= Implementation

== Security Implementation

Security was implemented using Spring Security 6.5 with the following components:

=== Authentication

The authentication pipeline uses `BCryptPasswordEncoder` for password hashing, `AccountUserDetailsService` for loading user credentials from the database, and `DaoAuthenticationProvider` to tie them together. On successful login, a `SecurityContext` is created and stored in an HTTP session.

```java
// AuthService.java — Login logic
public AuthenticatedUserResponse login(LoginRequest request,
                                       HttpSession session) {
    var auth = authManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.username(), request.password()));
    var ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(auth);
    SecurityContextHolder.setContext(ctx);
    session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, ctx);
    // ... return user response
}
```

A seed `admin` account with a BCrypt-hashed password is created on first startup via a `CommandLineRunner` in the application class.

=== CSRF Protection

The application uses a SPA-compatible CSRF scheme. The server issues a readable `XSRF-TOKEN` cookie, and the JavaScript frontend reads this cookie and sends it back as an `X-XSRF-TOKEN` header on state-changing requests. A custom `SpaCsrfTokenRequestHandler` was implemented to handle the token validation correctly under Spring Security 6.5, which defaults to XOR-encoded tokens that a JavaScript client cannot produce from a cookie value.

=== Authorization

Role-based access is enforced through URL-pattern matching in the `SecurityFilterChain`. The rules are evaluated in declaration order:

#figure(
  table(
    columns: (1fr, auto, auto, auto, auto),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[Resource],
      text(fill: white, weight: "bold")[Admin],
      text(fill: white, weight: "bold")[Librarian],
      text(fill: white, weight: "bold")[Student],
      text(fill: white, weight: "bold")[Public],
    ),
    [Login, CSRF, static assets], [✓], [✓], [✓], [✓],
    [Books (read only)], [✓], [✓], [✓], [—],
    [Books (create, update, delete)], [✓], [✓], [—], [—],
    [Students (all operations)], [✓], [✓], [—], [—],
    [Borrow records, Dashboard], [✓], [✓], [—], [—],
    [Librarians (all operations)], [✓], [—], [—], [—],
    [Profile], [✓], [✓], [✓], [—],
  ),
  caption: [Role-permission matrix.],
) <fig:rbac>

Unauthorized requests receive JSON error responses --- `401 Unauthorized` for unauthenticated users (via `RestAuthenticationEntryPoint`) and `403 Forbidden` for insufficient roles (via `RestAccessDeniedHandler`).

== Backend Implementation

=== Controllers

All 7 controllers follow a consistent pattern: constructor injection of their service, thin delegation with no business logic, and `@Valid` annotation on request bodies.

#figure(
  table(
    columns: (auto, auto, 1fr),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[Controller],
      text(fill: white, weight: "bold")[Endpoints],
      text(fill: white, weight: "bold")[Responsibility],
    ),
    [`AuthController`], [4], [CSRF bootstrap, login, logout, current user],
    [`BookController`], [5], [Book CRUD with search],
    [`BorrowRecordController`], [3], [Borrow, return, list records],
    [`DashboardController`], [1], [Aggregate statistics],
    [`LibrarianController`], [5], [Librarian CRUD (admin-only)],
    [`ProfileController`], [1], [Current user profile],
    [`StudentController`], [5], [Student CRUD],
  ),
  caption: [Controller summary.],
)

=== Services and Business Rules

Services contain all business logic and act as the transactional boundary. Key business rules enforced at the service layer:

- *ISBN uniqueness:* Checked before save and also caught as a database constraint violation fallback.
- *Username uniqueness:* Verified before creating student or librarian accounts.
- *Book availability:* Only available books can be borrowed; the flag is toggled atomically within the transaction.
- *Return validation:* Already-returned records cannot be returned again.
- *Deletion protection:* Books with borrow history cannot be deleted (preserves audit trail).

=== DTOs and Validation

All 14 DTOs are Java `record` types, providing immutable data transfer. Request DTOs carry Jakarta Bean Validation annotations:

#figure(
  table(
    columns: (auto, auto, 1fr),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[DTO],
      text(fill: white, weight: "bold")[Field],
      text(fill: white, weight: "bold")[Constraints],
    ),
    [`BookRequest`], [`title`], [`@NotBlank @Size(max=200)`],
    [`StudentRequest`], [`password`], [`@NotBlank @Size(min=8, max=100)`],
    [], [`email`], [`@NotBlank @Email @Size(max=100)`],
    [`LibrarianRequest`], [`age`], [`@Min(18) @Max(100)`],
    [`BorrowRequest`], [`bookId`], [`@NotNull`],
    [`LoginRequest`], [`username`], [`@NotBlank`],
  ),
  caption: [Key validation constraints (abbreviated).],
)

Separate `*UpdateRequest` DTOs exist for students and librarians, which include profile fields but omit the password to prevent accidental credential resets during profile updates.

=== Exception Handling

A centralized `GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain exceptions to a uniform `ApiErrorResponse` JSON structure:

#figure(
  table(
    columns: (1fr, auto, auto),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[Exception],
      text(fill: white, weight: "bold")[HTTP Status],
      text(fill: white, weight: "bold")[Error Code],
    ),
    [`ResourceNotFoundException`], [404], [`NOT_FOUND`],
    [`ConflictException`], [409], [`CONFLICT`],
    [`BusinessRuleException`], [400], [Dynamic (e.g. `BOOK_UNAVAILABLE`)],
    [`MethodArgumentNotValidException`], [400], [`VALIDATION_ERROR` + field errors],
  ),
  caption: [Exception-to-HTTP mapping.],
)

== Frontend Implementation

=== SPA Architecture

The frontend is a single-page application contained in one `index.html` file with 7 `<section>` elements, each representing a page. Navigation uses `location.hash` to toggle page visibility without a full page reload. No frontend framework or build tooling is used --- the application is served directly as static files.

=== CSS Architecture

Five CSS files implement a layered design system:
+ *`tokens.css`* — CSS custom properties for colors, spacing, and typography.
+ *`base.css`* — Resets and body typography.
+ *`layout.css`* — App shell grid, header, and navigation.
+ *`components.css`* — Cards, tables, buttons, badges, modals, and toasts.
+ *`responsive.css`* — Mobile breakpoint overrides for responsive layout.

The design uses an indigo/teal color scheme with Inter (body) and Poppins (headings) typefaces.

=== JavaScript Modules

The JavaScript is organized into ES modules:
- *`main.js`* — SPA orchestrator handling routing, rendering, and event wiring.
- *`http.js`* — Centralized Fetch wrapper handling CSRF headers, credentials, and error parsing.
- *6 API modules* — Thin wrappers for each backend resource (auth, books, borrow, dashboard, librarians, students).
- *`modal.js`* — Reusable form modal component with 4 form definitions and client-side + server-side validation rendering.

An `esc()` function escapes HTML special characters in all rendered user content to prevent XSS.

== API Reference

#set text(size: 9pt)
#figure(
  table(
    columns: (auto, auto, auto, auto, auto),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[\#],
      text(fill: white, weight: "bold")[Method],
      text(fill: white, weight: "bold")[URL],
      text(fill: white, weight: "bold")[Auth],
      text(fill: white, weight: "bold")[Purpose],
    ),
    [1], [GET], [`/api/auth/csrf`], [Public], [Bootstrap CSRF cookie],
    [2], [POST], [`/api/auth/login`], [Public], [Authenticate and create session],
    [3], [POST], [`/api/auth/logout`], [Any], [Invalidate session],
    [4], [GET], [`/api/auth/me`], [Any], [Current session identity],
    [5], [GET], [`/api/books`], [Any], [List/search books],
    [6], [GET], [`/api/books/\{id\}`], [Any], [Get single book],
    [7], [POST], [`/api/books`], [Staff], [Create book],
    [8], [PUT], [`/api/books/\{id\}`], [Staff], [Update book],
    [9], [DEL], [`/api/books/\{id\}`], [Staff], [Delete book],
    [10], [GET], [`/api/students`], [Staff], [List students],
    [11], [GET], [`/api/students/\{id\}`], [Staff], [Get student],
    [12], [POST], [`/api/students`], [Staff], [Create student + account],
    [13], [PUT], [`/api/students/\{id\}`], [Staff], [Update student],
    [14], [DEL], [`/api/students/\{id\}`], [Staff], [Delete student],
    [15], [GET], [`/api/librarians`], [Admin], [List librarians],
    [16], [GET], [`/api/librarians/\{id\}`], [Admin], [Get librarian],
    [17], [POST], [`/api/librarians`], [Admin], [Create librarian + account],
    [18], [PUT], [`/api/librarians/\{id\}`], [Admin], [Update librarian],
    [19], [DEL], [`/api/librarians/\{id\}`], [Admin], [Delete librarian],
    [20], [GET], [`/api/borrow-records`], [Staff], [List borrow records],
    [21], [POST], [`/api/borrow-records`], [Staff], [Borrow a book],
    [22], [POST], [`/api/borrow-records/\{id\}/return`], [Staff], [Return a book],
    [23], [GET], [`/api/dashboard`], [Staff], [Dashboard statistics],
    [24], [GET], [`/api/profile`], [Any], [Current user profile],
  ),
  caption: [Complete API endpoint reference. "Staff" = Admin or Librarian. "Any" = any authenticated user.],
) <fig:endpoints>
#set text(size: 11pt)

// =========================================================================
// Chapter 6: Application Workflows
// =========================================================================

= Application Workflows

== Authentication Flow

+ The browser loads `index.html`, which initializes the JavaScript module graph.
+ The frontend calls `GET /api/auth/csrf` to obtain the CSRF token cookie.
+ It then calls `GET /api/auth/me` to check for an existing session (silently handles 401).
+ The user submits the login form → `POST /api/auth/login` with credentials and the CSRF header.
+ The backend authenticates via `DaoAuthenticationProvider` → BCrypt verification → creates a session.
+ The frontend stores the user identity and loads all data pages in parallel.
+ On logout, the session is invalidated server-side and the frontend returns to the login page.

== Borrow Workflow

+ Staff opens the "Record a borrow" modal and fills in the Book ID, Student ID, and borrow date.
+ `POST /api/borrow-records` sends the request with CSRF protection.
+ The service validates that the book exists and is available, and that the student exists.
+ Borrower contact details are snapshotted from the student profile into the borrow record.
+ The book's `available` flag is set to `false` within the same transaction.
+ The frontend displays a success toast and refreshes all data tables.

If the book is already borrowed, the system returns `400 Bad Request` with code `BOOK_UNAVAILABLE`.

== Return Workflow

+ Staff clicks the "Return" button on an active borrow record.
+ `POST /api/borrow-records/\{id\}/return` is sent with CSRF protection.
+ The service verifies the record exists and has not already been returned.
+ `return_date` is set to today's date and the book's `available` flag is restored to `true`.
+ The frontend shows a "Book returned" toast.

If the record was already returned, the system returns `400 Bad Request` with code `ALREADY_RETURNED`.

// =========================================================================
// Chapter 7: Screenshots
// =========================================================================

= Screenshots

== Authentication

#figure(
  image("/screenshots/desktop/authentication.png", width: 90%),
  caption: [Login page — session-based authentication with CSRF protection.],
)

== Dashboard

#figure(
  image("/screenshots/desktop/dashboard.png", width: 90%),
  caption: [Admin dashboard showing aggregate statistics: total students, librarians, books, borrowed, and available.],
)

== Book Management

#grid(
  columns: (1fr, 1fr),
  gutter: 12pt,
  figure(
    image("/screenshots/desktop/books.png", width: 100%),
    caption: [Books catalogue with search functionality.],
  ),
  figure(
    image("/screenshots/desktop/add_book.png", width: 100%),
    caption: [Add book modal with form validation.],
  ),
)

#figure(
  image("/screenshots/desktop/book_added.png", width: 90%),
  caption: [Book successfully added — shown with success toast notification.],
)

== Student and Librarian Management

#grid(
  columns: (1fr, 1fr),
  gutter: 12pt,
  figure(
    image("/screenshots/desktop/students.png", width: 100%),
    caption: [Students list view.],
  ),
  figure(
    image("/screenshots/desktop/librarians.png", width: 100%),
    caption: [Librarians list view (admin-only).],
  ),
)

#grid(
  columns: (1fr, 1fr),
  gutter: 12pt,
  figure(
    image("/screenshots/desktop/add_student.png", width: 100%),
    caption: [Add student modal.],
  ),
  figure(
    image("/screenshots/desktop/add_librarian.png", width: 100%),
    caption: [Add librarian modal.],
  ),
)

== Borrow Records

#grid(
  columns: (1fr, 1fr),
  gutter: 12pt,
  figure(
    image("/screenshots/desktop/record_a_borrow.png", width: 100%),
    caption: [Record a borrow modal.],
  ),
  figure(
    image("/screenshots/desktop/saved_borrow_record.png", width: 100%),
    caption: [Borrow record saved with book marked as borrowed.],
  ),
)

#figure(
  image("/screenshots/desktop/borrow_records.png", width: 90%),
  caption: [Borrow records table showing active and returned loans.],
)

== Profile

#figure(
  image("/screenshots/desktop/profile.png", width: 90%),
  caption: [User profile page showing non-sensitive account information.],
)

== Validation and Error Handling

#grid(
  columns: (1fr, 1fr),
  gutter: 12pt,
  figure(
    image("/screenshots/desktop/invalid_details.png", width: 100%),
    caption: [Server-side validation error rendering.],
  ),
  figure(
    image("/screenshots/desktop/duplicate_username.png", width: 100%),
    caption: [Duplicate username conflict (409).],
  ),
)

== Mobile Responsive Design

#grid(
  columns: (1fr, 1fr, 1fr),
  gutter: 10pt,
  figure(
    image("/screenshots/mobile/mobile_authentication.png", width: 100%),
    caption: [Mobile login.],
  ),
  figure(
    image("/screenshots/mobile/mobile_dashboard.png", width: 100%),
    caption: [Mobile dashboard.],
  ),
  figure(
    image("/screenshots/mobile/mobile_borrow_returned.png", width: 100%),
    caption: [Mobile borrow return.],
  ),
)

// =========================================================================
// Chapter 8: Testing
// =========================================================================

= Testing

== Test Infrastructure

The project uses JUnit 5 with Spring Boot Test and MockMvc for integration testing. Tests run against an H2 database in MySQL compatibility mode with `create-drop` schema management, ensuring a clean database for each test run.

== Test Coverage

#figure(
  table(
    columns: (auto, 1fr, auto),
    stroke: 0.5pt + border-color,
    fill: (_, y) => if y == 0 { header-bg } else if calc.odd(y) { light-bg },
    table.header(
      text(fill: white, weight: "bold")[Test Class],
      text(fill: white, weight: "bold")[Covers],
      text(fill: white, weight: "bold")[Methods],
    ),
    [`LibraryManagementIntegrationTest`], [Authentication, CRUD lifecycle, borrow/return, validation errors, ISBN conflicts, role authorization (401/403), logout], [4],
    [`BrowserCsrfFlowIntegrationTest`], [Full browser-equivalent CSRF flow: cookie bootstrap → header exchange → login → session reuse → logout → post-logout rejection], [1],
    [`BookRepositoryTest`], [JPA auditing timestamp population, ISBN uniqueness constraint at database level], [1],
  ),
  caption: [Test classes and coverage areas.],
)

== Key Test Scenarios

+ *Full lifecycle test:* Creates a student, librarian, and book → borrows the book → verifies unavailability → returns it → verifies re-availability → confirms deletion conflict → logs out.
+ *Security test:* Verifies unauthenticated requests receive `401 Unauthorized` and librarians accessing admin-only endpoints receive `403 Forbidden`.
+ *CSRF regression test:* Performs the exact browser flow (cookie → header → login → session → logout) to verify the custom CSRF handler works correctly.
+ *Validation test:* Submits invalid book data and verifies field-level error responses.
+ *Conflict test:* Creates a book with a duplicate ISBN and verifies the `409 Conflict` response.

== Test Execution

All tests are executed with:

```bash
./mvnw clean test
```

The tests pass against the H2 in-memory database without requiring a running MySQL instance.

// =========================================================================
// Chapter 9: Future Scope
// =========================================================================

= Future Scope

The following enhancements are planned for future releases:

+ *Pagination:* Add paginated result sets to all list endpoints to support large datasets.
+ *Update and Delete UI:* Build frontend forms and confirmation dialogs for the update and delete operations that already exist in the backend.
+ *Advanced Search:* Full-text search for books and filtering by availability, date range, and borrower for borrow records.
+ *Password Management:* Password-change endpoint and optional password-reset flow.
+ *Book Categories:* Categorization system for browsing and filtering by genre or subject.
+ *Fine System:* Overdue calculation and fine tracking for late returns.
+ *Notifications:* Email or in-app notifications for due dates and overdue books.
+ *Containerized Deployment:* Docker and Docker Compose setup for MySQL and the Spring Boot application.
+ *API Documentation:* Auto-generated Swagger/OpenAPI documentation.
+ *Database Migrations:* Introduction of Flyway or Liquibase for production-safe schema evolution.

// =========================================================================
// Chapter 10: Conclusion
// =========================================================================

= Conclusion

The Library Management System was designed and implemented as a full-stack web application that demonstrates competency across the software development lifecycle. The project successfully delivers:

- A clean, four-layered MVC architecture with proper separation of concerns.
- A secure authentication and authorization system using Spring Security with BCrypt, sessions, and CSRF protection.
- A RESTful API with 24 endpoints, uniform error handling, and server-side validation.
- A responsive single-page application with real-time search, modal forms, and mobile-friendly design.
- A transactional borrow/return workflow that maintains data integrity and preserves audit history.
- Integration tests covering critical paths: authentication, authorization, CRUD operations, business rule enforcement, and CSRF flow.

The project provides a solid foundation for future enhancements including pagination, advanced search, deployment infrastructure, and expanded user-facing features. The architecture and code organization are designed to accommodate these additions without requiring significant restructuring.

// =========================================================================
// References
// =========================================================================

= References

+ Spring Boot Documentation, VMware Inc. #link("https://docs.spring.io/spring-boot/docs/current/reference/html/")
+ Spring Security Reference, VMware Inc. #link("https://docs.spring.io/spring-security/reference/")
+ Spring Data JPA Reference, VMware Inc. #link("https://docs.spring.io/spring-data/jpa/reference/")
+ Jakarta Bean Validation Specification. #link("https://beanvalidation.org/")
+ MDN Web Docs — Fetch API. #link("https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API")
+ OWASP CSRF Prevention Cheat Sheet. #link("https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html")
+ MySQL 8.0 Reference Manual, Oracle Corp. #link("https://dev.mysql.com/doc/refman/8.0/en/")
