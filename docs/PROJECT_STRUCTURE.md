# Project Structure

> **Source of truth as of:** 31 July 2026

---

## 1. Repository Overview

```text
Library Management System/
├── README.md                          Project overview and quick start
├── DEBUG.md                           Known issues and debugging notes
├── mvnw                               Root Maven convenience wrapper
├── backend/
│   ├── pom.xml                        Spring Boot build configuration
│   ├── .mvn/                          Maven Wrapper configuration
│   ├── .env.example                   Environment variable template
│   ├── Dockerfile                     Production container image
│   ├── docker-compose.yml             Multi-service orchestration
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
│   │   └── static/                    ← FRONTEND LIVES HERE
│   └── src/test/                      3 test files, 6 @Test methods
├── docs/                              Documentation suite
│   ├── ARCHITECTURE.md                System design, layering, ADRs
│   ├── API.md                         REST endpoint reference
│   ├── DATABASE.md                    ER diagram, table definitions
│   ├── FRONTEND.md                    MPA structure, JS modules, CSS
│   ├── SECURITY.md                    Auth, authz, CSRF, sessions
│   ├── SETUP.md                       Local and Docker configuration
│   ├── TESTING.md                     Test inventory and coverage
│   ├── PROJECT_STRUCTURE.md           This file
│   └── ...
├── refer-themes/                      Reference theme prototypes
│   ├── dark blue theme/               Previous dark theme (18 files)
│   └── light pink theme/              Previous pink theme (16 files)
└── screenshots/
    ├── desktop/                       Desktop review evidence
    └── mobile/                        Mobile review evidence
```

---

## 2. Frontend File Inventory

All frontend files live in `backend/src/main/resources/static/`.

### 2.1 HTML Pages (19 files)

```
static/
├── index.html                 Dashboard            (204 lines)
├── books.html                 Books catalog        (162 lines)
├── book-details.html          Book detail view     (125 lines)
├── add-book.html              Add book form        (151 lines)
├── magazines.html             Magazines catalog    (148 lines)
├── newspapers.html            Newspapers catalog   (148 lines)
├── students.html              Student list         (145 lines)
├── student-profile.html       Student detail       (125 lines)
├── librarians.html            Librarian list       (147 lines)
├── librarian-profile.html     Librarian detail     (125 lines)
├── borrow.html                Borrow records       (144 lines)
├── analytics.html             Analytics dashboard  (220 lines)
├── reports.html               Reports/exports      (152 lines)
├── roles.html                 Roles & permissions  (168 lines)
├── settings.html              Settings (9 tabs)    (146 lines)
├── profile.html               User profile         (192 lines)
├── login.html                 Authentication       (639 lines)
├── register.html              Account info page    (27 lines)
```

### 2.2 JavaScript Modules (31 files)

```
js/
├── main.js                    Legacy monolith (326 lines) — dashboard orchestrator
├── dashboard.js               Dashboard page module (83 lines)
├── books.js                   Books page (273 lines)
├── book-details.js            Book detail page (198 lines)
├── magazines.js               Magazines page (148 lines)
├── newspapers.js              Newspapers page (147 lines)
├── students.js                Students page (175 lines)
├── student-profile.js         Student profile page (246 lines)
├── librarians.js              Librarians page (154 lines)
├── librarian-profile.js       Librarian profile page (168 lines)
├── borrow.js                  Borrow records page (186 lines)
├── analytics.js               Analytics page (264 lines)
├── reports.js                 Reports page (26 lines)
├── profile.js                 Profile page (47 lines)
│
├── api/
│   ├── http.js                HTTP boundary — CSRF, fetch wrapper (65 lines)
│   ├── auth-api.js            Auth endpoints (2 lines)
│   ├── books-api.js           Book CRUD (8 lines)
│   ├── magazines-api.js       Magazine CRUD (8 lines)
│   ├── newspapers-api.js      Newspaper CRUD (8 lines)
│   ├── students-api.js        Student CRUD (8 lines)
│   ├── librarians-api.js      Librarian CRUD (8 lines)
│   ├── borrow-api.js          Borrow ops (2 lines)
│   ├── dashboard-api.js       Dashboard stats (2 lines)
│   ├── analytics-api.js       Analytics endpoints (8 lines)
│   ├── report-api.js          CSV exports (6 lines)
│   └── student-dashboard-api.js  Student portal (12 lines)
│
└── utils/
    ├── esc.js                 XSS-safe HTML escaping (2 lines)
    ├── toast.js               Toast notifications (16 lines)
    ├── pagination.js          Pager rendering (39 lines)
    └── confirm.js             Confirmation dialogs (23 lines)

components/
└── modal.js                   Generic modal/form generator (80 lines)
```

### 2.3 Stylesheets (2 files)

```
├── styles.css                 Main stylesheet (1711 lines) — themes, components, layout
├── css/styles.css             Legacy/reference stylesheet (547 lines)
```

### 2.4 Assets

```
├── favicon.svg
├── assets/
│   ├── icons/                 (empty)
│   ├── images/                (empty)
│   └── library-mark.svg
```

---

## 3. Page Inventory

### 3.1 Sidebar Navigation

All authenticated pages share this sidebar:

```text
Catalog
  ├── Dashboard          → index.html
  ├── Books              → books.html
  ├── Magazines          → magazines.html
  ├── Newspapers         → newspapers.html
  ├── Students           → students.html
  ├── Librarians         → librarians.html
  ├── Borrow Records     → borrow.html
  ├── Analytics          → analytics.html
  ├── Reports            → reports.html
  └── Roles & Permissions → roles.html
System
  ├── Settings           → settings.html
  ├── Profile            → profile.html
  └── Log out            → .logout
```

### 3.2 Pages by Role Access

| Page | Admin | Librarian | Student | Public |
|------|:-----:|:---------:|:-------:|:------:|
| Dashboard | R/W | R/W | Own only | — |
| Books | R/W | R/W | R | — |
| Book Details | R | R | R | — |
| Add Book | W | W | — | — |
| Magazines | R/W | R/W | R | — |
| Newspapers | R/W | R/W | R | — |
| Students | R/W | R | — | — |
| Student Profile | R | R | — | — |
| Librarians | R/W | — | — | — |
| Librarian Profile | R | — | — | — |
| Borrow Records | R/W | R/W | Own only | — |
| Analytics | R | R | — | — |
| Reports | R | R | — | — |
| Roles | R | — | — | — |
| Settings | R | — | — | — |
| Profile | R | R | R | — |
| Login | — | — | — | Yes |
| Register | — | — | — | Info only |

### 3.3 Page Layout Patterns

**Pattern A — Dashboard (index.html)**
- Sidebar rail + sticky topbar with breadcrumb, search, notifications, avatar
- 5 stat cards in a row (books, students, active borrows, available, overdue)
- Line chart (monthly borrow trend)
- Donut chart (category split)
- Activity list (overdue alerts)
- Activity list (recent borrowings)
- Quick action grid (5 actions)

**Pattern B — Catalog Table (books, magazines, newspapers, librarians, borrow)**
- Sidebar rail + topbar
- Page header (eyebrow + title + subtitle)
- Toolbar: search input + filter chips + primary action button
- Data table with columns, badges, row actions
- Pagination footer (page numbers + total count)

**Pattern C — Card Grid (students, analytics)**
- Sidebar rail + topbar
- Page header
- Grid of cards (3-column for students, mixed for analytics)
- Each card: avatar/image + name + meta + stats + actions

**Pattern D — Detail View (book-details, student-profile, librarian-profile)**
- Sidebar rail + topbar
- Profile/hero banner with large image
- Metadata grid (2-column key-value pairs)
- Related data table or timeline
- Recommended items row

**Pattern E — Form (add-book)**
- Sidebar rail + topbar
- Page header
- 2-column form grid with labeled fields
- File upload dropzone
- Preview pane
- Form actions (validate, submit)

**Pattern F — Settings (settings.html)**
- Sidebar rail + topbar
- Tab bar (9 tabs)
- Tab panel content (key-value mini-rows)

**Pattern G — Login (login.html)**
- Full-screen animated background (no sidebar)
- Centered glassmorphic card
- Username/password form
- Animated SVG logo

---

## 4. Data Models

### 4.1 Core Entities

```text
Book
├── id: Long (auto-generated)
├── title: String (required, max 255)
├── author: String (required, max 255)
├── isbn: String (unique, max 13)
├── publishedDate: LocalDate
└── available: boolean (default true)

Magazine
├── id: Long
├── title: String
├── publisher: String
├── issueDate: String
└── available: boolean

Newspaper
├── id: Long
├── title: String
├── publicationDate: String
├── language: String
└── available: boolean

StudentProfile
├── id: Long
├── accountId: Long → Account
├── username: String (unique)
├── name: String
├── email: String
├── phone: String
└── role: String (always "STUDENT")

LibrarianProfile
├── id: Long
├── accountId: Long → Account
├── username: String (unique)
├── name: String
├── age: int
├── phone: String
└── role: String (always "LIBRARIAN")

BorrowRecord
├── id: Long
├── itemId: Long (polymorphic: Book/Magazine/Newspaper)
├── itemTitle: String (denormalized)
├── itemType: String (BOOK | MAGAZINE | NEWSPAPER)
├── studentId: Long → StudentProfile
├── borrowerName: String (denormalized)
├── borrowerEmail: String
├── borrowerPhone: String
├── borrowDate: LocalDate
├── returnDate: LocalDate (nullable)
└── status: String (BORROWED | RETURNED)

Account
├── id: Long
├── username: String (unique)
├── password: String (BCrypt encoded)
└── role: Role enum (ADMIN | LIBRARIAN | STUDENT)

AuditableEntity (superclass)
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

### 4.2 API Response Shapes

**Paginated response** (Spring Data Page):
```json
{
  "content": [...],
  "number": 0,
  "size": 20,
  "totalPages": 5,
  "totalElements": 100
}
```

**Dashboard response**:
```json
{
  "totalBooks": 182,
  "totalStudents": 45,
  "activeBorrows": 23,
  "availableBooks": 159,
  "totalLibrarians": 4,
  "borrowedBooks": 23,
  "overdueCount": 3
}
```

**Error response** (`ApiErrorResponse`):
```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "fieldErrors": [
    { "field": "isbn", "message": "ISBN must be unique" }
  ]
}
```

---

## 5. API Endpoints

### Authentication (`/api/auth`)
| Method | Path | Body/Params | Response |
|--------|------|-------------|----------|
| GET | `/api/auth/csrf` | — | Sets `XSRF-TOKEN` cookie |
| POST | `/api/auth/login` | `{ username, password }` | 200 OK |
| POST | `/api/auth/logout` | — | 200 OK |
| GET | `/api/auth/me` | — | `AuthenticatedUserResponse` |

### Books (`/api/books`)
| Method | Path | Params | Response |
|--------|------|--------|----------|
| GET | `/api/books` | `?page=&size=&search=` | Page of `BookResponse` |
| GET | `/api/books/{id}` | — | `BookResponse` |
| POST | `/api/books` | `BookRequest` body | `BookResponse` |
| PUT | `/api/books/{id}` | `BookRequest` body | `BookResponse` |
| DELETE | `/api/books/{id}` | — | 204 No Content |

### Magazines (`/api/magazines`)
| Method | Path | Params | Response |
|--------|------|--------|----------|
| GET | `/api/magazines` | `?page=&size=&search=` | Page of `MagazineResponse` |
| POST | `/api/magazines` | `MagazineRequest` body | `MagazineResponse` |
| PUT | `/api/magazines/{id}` | `MagazineRequest` body | `MagazineResponse` |
| DELETE | `/api/magazines/{id}` | — | 204 No Content |

### Newspapers (`/api/newspapers`)
| Method | Path | Params | Response |
|--------|------|--------|----------|
| GET | `/api/newspapers` | `?page=&size=&search=` | Page of `NewspaperResponse` |
| POST | `/api/newspapers` | `NewspaperRequest` body | `NewspaperResponse` |
| PUT | `/api/newspapers/{id}` | `NewspaperRequest` body | `NewspaperResponse` |
| DELETE | `/api/newspapers/{id}` | — | 204 No Content |

### Students (`/api/students`)
| Method | Path | Params | Response |
|--------|------|--------|----------|
| GET | `/api/students` | `?page=&size=&query=` | Page of `StudentResponse` |
| GET | `/api/students/{id}` | — | `StudentResponse` |
| POST | `/api/students` | `StudentRequest` body | `StudentResponse` |
| PUT | `/api/students/{id}` | `StudentUpdateRequest` body | `StudentResponse` |
| DELETE | `/api/students/{id}` | — | 204 No Content |

### Librarians (`/api/librarians`)
| Method | Path | Params | Response |
|--------|------|--------|----------|
| GET | `/api/librarians` | `?page=&size=&query=` | Page of `LibrarianResponse` |
| GET | `/api/librarians/{id}` | — | `LibrarianResponse` |
| POST | `/api/librarians` | `LibrarianRequest` body | `LibrarianResponse` |
| PUT | `/api/librarians/{id}` | `LibrarianUpdateRequest` body | `LibrarianResponse` |
| DELETE | `/api/librarians/{id}` | — | 204 No Content |

### Borrow Records (`/api/borrow-records`)
| Method | Path | Params | Response |
|--------|------|--------|----------|
| GET | `/api/borrow-records` | `?page=&size=&query=` | Page of `BorrowRecordResponse` |
| POST | `/api/borrow-records` | `BorrowRequest` body | `BorrowRecordResponse` |
| POST | `/api/borrow-records/{id}/return` | — | `BorrowRecordResponse` |

### Dashboard (`/api/dashboard`)
| Method | Path | Response |
|--------|------|----------|
| GET | `/api/dashboard` | `DashboardResponse` |

### Analytics (`/api/analytics`)
| Method | Path | Params | Response |
|--------|------|--------|----------|
| GET | `/api/analytics/dashboard` | — | `AnalyticsDashboardResponse` |
| GET | `/api/analytics/trends` | — | `MonthlyTrend[]` |
| GET | `/api/analytics/top-books` | `?limit=` | `TopBookResponse[]` |
| GET | `/api/analytics/top-readers` | `?limit=` | `TopReaderResponse[]` |
| GET | `/api/analytics/overdue` | — | `OverdueSummaryResponse` |

### Reports (`/api/reports`) — CSV Downloads
| Path | Format |
|------|--------|
| `/api/reports/inventory?format=csv` | Collection audit |
| `/api/reports/borrowing?format=csv&from=&to=` | Monthly circulation |
| `/api/reports/students?format=csv` | Student list |
| `/api/reports/overdue?format=csv` | Overdue summary |

### Profile (`/api/profile`)
| Method | Path | Response |
|--------|------|----------|
| GET | `/api/profile` | `AuthenticatedUserResponse` |

### Student Portal (`/api/student`)
| Method | Path | Response |
|--------|------|----------|
| GET | `/api/student/dashboard` | `StudentDashboardResponse` |
| GET | `/api/student/borrow-history` | Page of `BorrowRecordResponse` |
| GET | `/api/student/profile` | `StudentProfileResponse` |

**Total: 48 endpoints across 13 resource groups**

---

## 6. CSS Architecture

### 6.1 Theme System

Two themes controlled by `data-theme` attribute on `<html>`:

| Theme | Attribute | Default | Canvas Color |
|-------|-----------|---------|-------------|
| Dark Blue | (none — `:root`) | Default | `#0C1426` (deep navy) |
| Rosy Pink | `data-theme="pink"` | Toggle in Settings | `#FFF0F5` (light pink) |

Theme is persisted to `localStorage.getItem('theme')` and applied synchronously via inline `<script>` in each HTML `<head>`.

### 6.2 Design Tokens (CSS Custom Properties)

```text
Typography:
  --font-display: 'Fraunces', serif       (headings, stat values)
  --font-body: 'Inter', sans-serif        (body text)
  --font-mono: 'IBM Plex Mono', monospace (data, code)

Colors:
  --ink              Primary text
  --ink-soft         Secondary/muted text
  --indigo           Primary accent
  --indigo-deep      Deep accent (CTAs, active states)
  --violet           Secondary accent
  --lilac            Tertiary accent
  --amber            Warning/gold
  --green            Success
  --red              Error/danger
  --sky              Info blue

Layout:
  --canvas           Page background
  --canvas-light     Lighter canvas variant
  --panel            Card/panel background (with alpha)
  --panel-solid      Opaque panel background
  --glass-border     Panel border (with alpha)
  --glass-border-hover  Panel border on hover
  --line             Divider lines
  --line-soft        Subtle dividers

Shadows:
  --neu-shadow       Standard neumorphic shadow
  --neu-shadow-sm    Small neumorphic shadow
  --neu-shadow-lg    Large neumorphic shadow
  --neu-inset        Inset neumorphic shadow
  --shadow-sm        Legacy drop shadow
  --shadow-md        Medium drop shadow
  --shadow-lg        Large drop shadow

Glass:
  --blur             backdrop-filter: blur(20px) saturate(180%)
  --blur-heavy       backdrop-filter: blur(32px) saturate(200%)
  --glass-bg         Glass panel background (with alpha)
  --glass-bg-strong  Glass panel background (higher alpha)

Radii:
  --radius-sm: 14px
  --radius-md: 18px
  --radius-lg: 24px

Sidebar:
  --rail-w: 264px    Sidebar width
```

### 6.3 Component Styles (in styles.css)

| Component | Selector | Description |
|-----------|----------|-------------|
| Sidebar | `.rail` | Fixed glassmorphic sidebar with nav links |
| Topbar | `.topbar` | Sticky breadcrumb bar with search, notifications |
| Stat Cards | `.stat-card`, `.stat-card.accent` | Dashboard stat tiles with icons |
| Bento Grid | `.bento`, `.span-4/6/7/8/12` | 12-column grid layout |
| Cards | `.card` | Glass panel with heading + content |
| Data Table | `.table-card`, `table` | Full-width table with header, rows, pagination |
| People Grid | `.people-grid`, `.person-card` | 3-column card grid for users |
| Timeline | `.timeline`, `.tl-row` | Borrow record timeline |
| Forms | `.form-grid`, `.field` | 2-column form layout with inputs |
| Modal | `.modal`, `.modal-backdrop` | Centered glass modal with animation |
| Toast | `.toast`, `.toast-success/error/info` | Fixed-position notifications |
| Tabs | `.tabs`, `.tab-btn` | Horizontal tab bar |
| Badges | `.badge`, `.badge-avail/out/hold/warn` | Status pills |
| Charts | `.line-chart`, `.donut`, `.bar-chart` | SVG chart containers |
| Quick Actions | `.quick-grid`, `.quick` | Action button grid |
| Search | `.search` | Glassmorphic search input |
| Buttons | `.btn-primary`, `.btn-ghost` | Primary and secondary buttons |
| Toggle | `.tgl` | On/off toggle switch |
| Skeleton | `.skeleton`, `.skeleton-wrap` | Loading placeholder |

### 6.4 Animations

| Keyframe | Effect |
|----------|--------|
| `meshFloat` | Background mesh gradient movement |
| `fadeSlideUp` | Content entrance (upward) |
| `fadeSlideDown` | Content entrance (downward) |
| `fadeSlideLeft` | Sidebar entrance (from left) |
| `scaleIn` | Login card scale entrance |
| `glowPulse` | Glowing pulse effect |
| `shimmer` | Skeleton loading shimmer |
| `breathe` | Gentle scale breathing |
| `floatSoft` | Soft floating motion |
| `barGrow` | Bar chart grow animation |
| `donutDraw` | Donut chart stroke animation |
| `lineTrace` | Line chart trace animation |
| `toastIn` | Toast notification entrance |
| `modalIn` | Modal card entrance |
| `orbFloat1/2` | Ambient orb floating |
| `petalFall` | Pink theme: rose petal falling |
| `starFloat` | Blue theme: cosmic particle floating |

### 6.5 Responsive Breakpoints

| Breakpoint | Behavior |
|------------|----------|
| > 1080px | Full 5-column stat grid, 3-column people grid |
| ≤ 1080px | 3-column stat grid, 2-column people grid |
| ≤ 860px | Sidebar collapses (translateX), hamburger menu, 2-column grids |

---

## 7. JavaScript Patterns

### 7.1 Page Module Pattern

Every page JS module follows this pattern:

```javascript
// 1. Import API modules + utilities
import { booksApi } from "/js/api/books-api.js";
import { esc } from "/js/utils/esc.js";

// 2. Local state
const state = { page: 0, size: 10, search: '', totalPages: 0, items: [] };

// 3. Init on DOMContentLoaded
document.addEventListener("DOMContentLoaded", async () => {
  await initAuth();    // csrf() → me() → setCurrentUser()
  await loadData();    // Fetch and render
  wireSearch();        // Debounced search input
  wireModals();        // CRUD modal triggers
});

// 4. Data loading
async function loadData() {
  const data = await booksApi.list(state.page, state.size, state.search);
  state.items = data.content;
  state.totalPages = data.totalPages;
  renderTable();
}

// 5. Rendering (innerHTML templates)
function renderTable() { tbody.innerHTML = state.items.map(row => `...`).join(""); }

// 6. CRUD operations
async function createItem(formData) {
  await booksApi.create(formData);
  showToast("Created successfully", "success");
  await loadData();
}
```

### 7.2 HTTP Layer (`http.js`)

- Reads `XSRF-TOKEN` from cookies
- Sets `X-XSRF-TOKEN` header on non-GET requests
- Uses `credentials: "include"` for session cookies
- 401 → auto-redirect to `/login.html`
- Errors carry `{ status, code, fieldErrors[] }`

### 7.3 Component System

Only one reusable component exists: `components/modal.js`

- Generic modal generator with form definitions per entity type
- Each definition: `{ fields: [{ name, label, type, required }] }`
- Handles validation display, API error propagation, field-level errors
- All other "components" are inline innerHTML template strings

---

## 8. Counts Summary

| Category | Count |
|----------|-------|
| HTML pages | 19 (17 authenticated + 1 login + 1 register info) |
| JS modules | 31 (14 page + 12 API + 4 utility + 1 component) |
| CSS files | 2 (1 main + 1 legacy) |
| Java files | 57 (14 controllers + 11 services + 8 repositories + 8 entities + 24 DTOs + ...) |
| REST endpoints | 48 across 13 resource groups |
| Database entities | 8 + 1 superclass + 3 enums |
| API resource groups | 13 |
| Test files | 3 (6 @Test methods) |
| Design tokens | 35+ CSS custom properties |
| CSS animations | 18 keyframe definitions |

---

## 9. Files to Share for Redesign

For an AI tool to redesign the frontend, share these files:

| File | Why |
|------|-----|
| `styles.css` | The entire design system — this is what gets replaced |
| `index.html` | Most complex layout — dashboard with stats, charts, activity |
| `books.html` | Table + grid pattern — representative of all catalog pages |
| `login.html` | Standalone auth page — different layout entirely |
| `components/modal.js` | Only reusable component — form generation pattern |
| `js/books.js` | Representative page module — data fetching, CRUD, search, pagination |
| `js/api/http.js` | HTTP boundary — CSRF, auth, error handling |
| `settings.html` | Tabbed layout pattern |

Do NOT share: `main.js` (legacy), other page JS files (identical pattern to books.js), backend Java files, `register.html` (dead-end).
