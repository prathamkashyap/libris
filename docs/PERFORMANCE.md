# Performance Engineering — Library Management System

> Engineering knowledge base for performance characteristics, fixes, and outstanding gaps.

---

## 1. N+1 Query Fixes

### Problem

`BorrowRecord` has four `@ManyToOne(fetch = FetchType.LAZY)` associations (`BorrowRecord.java:13-27`):

```
book, magazine, newspaper, student
```

Without eager fetching, listing 10 borrow records triggered **~40 additional SQL queries** (1 initial query + up to 4 lazy loads per row).

### Fixes Applied

#### BorrowRecordRepository (`repository/BorrowRecordRepository.java`)

7 methods annotated with `@EntityGraph`:

| Method | Line | Annotation |
|--------|------|------------|
| `findByReturnDateIsNull(Pageable)` | 11 | `@EntityGraph(attributePaths = {"book", "magazine", "newspaper", "student"})` |
| `findByReturnDateIsNotNull(Pageable)` | 15 | same |
| `findByStudentId(Long, Pageable)` | 19 | same |
| `findByStudentIdAndReturnDateIsNull(Long, Pageable)` | 23 | same |
| `findByStudentIdAndReturnDateIsNotNull(Long, Pageable)` | 27 | same |
| `findByBorrowDateBetween(LocalDate, LocalDate, Sort)` | 55 | same |
| `findAll(Sort)` | 59 | same |

Search method uses explicit JPQL fetch joins (line 62-66):

```sql
SELECT r FROM BorrowRecord r
  LEFT JOIN FETCH r.book
  LEFT JOIN FETCH r.magazine
  LEFT JOIN FETCH r.newspaper
  LEFT JOIN FETCH r.student
WHERE LOWER(r.borrowerName) LIKE LOWER(CONCAT('%',:q,'%'))
   OR LOWER(r.borrowerEmail) LIKE LOWER(CONCAT('%',:q,'%'))
```

#### StudentProfileRepository (`repository/StudentProfileRepository.java`)

| Method | Line | Fix |
|--------|------|-----|
| `findByAccountUsername(String)` | 13 | `@EntityGraph(attributePaths = {"account"})` |
| `findAll(Pageable)` | 16 | `@EntityGraph(attributePaths = {"account"})` |
| `search(String, Pageable)` | 19-21 | `LEFT JOIN FETCH s.account` in JPQL |

#### LibrarianProfileRepository (`repository/LibrarianProfileRepository.java`)

| Method | Line | Fix |
|--------|------|-----|
| `findAll(Pageable)` | 12 | `@EntityGraph(attributePaths = {"account"})` |
| `search(String, Pageable)` | 15-17 | `LEFT JOIN FETCH l.account` in JPQL |

### Impact

| Metric | Before | After |
|--------|--------|-------|
| SQL queries for 10 borrow records | ~41 | 1 |
| Lazy-load proxy hits | 40+ | 0 |
| `spring.jpa.open-in-view` | false (already set) | n/a |

---

## 2. Pagination

All 8 paginated controller endpoints accept `page` and `size` query parameters.

| Controller | Endpoint | Default page | Default size | File:Line |
|------------|----------|-------------|-------------|-----------|
| BookController | `GET /api/books` | 0 | 10 | `controller/BookController.java:30-31` |
| MagazineController | `GET /api/magazines` | 0 | 10 | `controller/MagazineController.java:30-31` |
| NewspaperController | `GET /api/newspapers` | 0 | 10 | `controller/NewspaperController.java:30-31` |
| StudentController | `GET /api/students` | 0 | 10 | `controller/StudentController.java:29-30` |
| LibrarianController | `GET /api/librarians` | 0 | 10 | `controller/LibrarianController.java:29-30` |
| BorrowRecordController | `GET /api/borrow-records` | 0 | 10 | `controller/BorrowRecordController.java:33-34` |
| BorrowRecordController | `GET /api/borrow-records/my` | 0 | 10 | `controller/BorrowRecordController.java:42-43` |
| AuditController | `GET /api/audit` | 0 | **20** | `controller/AuditController.java:39-40` |

ReportController (`GET /api/reports/*`) returns full CSV exports — no pagination. AnalyticsController returns non-paginated lists for trends/top-N.

Frontend API clients pass `page` and `size` params in requests (e.g., `js/api/books-api.js:3`).

---

## 3. Spring Boot Defaults

### Server

- **Embedded Tomcat** (default from `spring-boot-starter-web`)
- No custom `server.*` tuning in `application.properties`

### Connection Pool

- **HikariCP** (default pool from `spring-boot-starter-data-jpa`)
- No explicit pool configuration — uses HikariCP defaults:
  - `maximumPoolSize=10`
  - `minimumIdle=10`
  - `connectionTimeout=30000ms`
  - `idleTimeout=600000ms`
  - `maxLifetime=1800000ms`

### JSON Serialization

```properties
spring.jackson.default-property-inclusion=non_null
```

Jackson omits `null` fields from all JSON responses.

### Other Relevant Config

```properties
spring.jpa.open-in-view=false          # No OSIV — controllers can't access lazy proxies
spring.jpa.hibernate.ddl-auto=update   # Schema auto-migration (dev only)
```

---

## 4. Frontend Performance

### Static Resource Serving

All frontend assets served from `backend/src/main/resources/static/` via Spring Boot's default static resource handler. No CDN, no external asset host.

```
static/
├── *.html                 # 17 page files
├── styles.css             # Single stylesheet (2047 lines)
├── js/                    # ES modules
│   ├── api/               # API client modules
│   ├── utils/             # Shared utilities
│   └── *.js               # Page controllers
└── components/            # Shared components (sidebar, modal)
```

### CSS Custom Properties for Theme Switching

Theme switching uses CSS custom properties (`styles.css:84-125`). The `:root` block defines the dark-blue theme tokens (`--ink`, `--canvas`, `--panel`, etc.). The `[data-theme="pink"]` selector (starting line 1806) overrides select properties.

Switching themes only toggles the `data-theme` attribute on `<html>` — CSS cascades the new values without DOM re-rendering or class re-computation. `theme.js` manages state via `localStorage` and dispatches a `themechange` CustomEvent.

### ES Modules for Code Splitting

All JS files use native ES module imports (`import ... from "..."`). Each page loads only the modules it needs. Example from `js/books.js:1-7`:

```js
import { booksApi } from "/js/api/books-api.js";
import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";
import { esc } from "/js/utils/esc.js";
import { toast } from "/js/utils/toast.js";
import { confirmDialog } from "/js/utils/confirm.js";
import { renderPagination, renderPageInfo } from "/js/utils/pagination.js";
```

No bundler (Webpack, Vite, esbuild). Browser-native module loading.

---

## 5. Areas NOT Yet Optimized

### No Database Indexes Beyond PKs and Unique Constraints

- `BorrowRecord`: No index on `borrow_date`, `return_date`, `student_id`, `book_id`
- `AuditLog`: No index on `timestamp`, `entity_type`, `action`, `actor_username`
- Common filter columns (`borrower_name`, `borrower_email`) lack indexes

### No Caching

- No `@Cacheable` annotations on any repository or service method
- No Spring Cache abstraction configured
- Repeated identical queries hit the database every time

### No Lazy Bean Creation

- All `@Service` and `@RestController` beans instantiated at startup
- `DashboardService`, `ReportService`, `AnalyticsService` — potentially heavy — created eagerly

### No Connection Pool Tuning

- HikariCP runs at defaults (`maximumPoolSize=10`)
- No environment-specific pool sizing
- No metrics exposed on pool utilization

### No Query Count Monitoring

- `management.endpoints.web.exposure.include=health,info,metrics` — actuator is available
- No `p6spy` or Hibernate query logging in production profiles
- N+1 regressions would go undetected
