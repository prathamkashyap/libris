# Frontend

> **Source of truth as of:** 30 July 2026

The frontend is a **multi-page application (MPA)** served as static resources from Spring Boot. Each page is a standalone HTML file with its own JavaScript module. Pages are connected by standard `<a href>` navigation in a sidebar rail — there is no client-side router.

See [ARCHITECTURE.md](ARCHITECTURE.md) for system context and [SECURITY.md](SECURITY.md) for the CSRF bootstrap flow.

---

## Page Structure

| Page | HTML File | JS Module | Purpose |
|------|-----------|-----------|---------|
| Dashboard | `index.html` | `main.js` | Aggregate statistics, recent activity |
| Books | `books.html` | `books.js` | Book catalogue list, search, add |
| Book Details | `book-details.html` | `book-details.js` | Single book view |
| Magazines | `magazines.html` | `magazines.js` | Magazine catalogue list |
| Newspapers | `newspapers.html` | `newspapers.js` | Newspaper catalogue list |
| Students | `students.html` | `students.js` | Student list, add |
| Student Profile | `student-profile.html` | `student-profile.js` | Single student view |
| Librarians | `librarians.html` | `librarians.js` | Librarian list, add |
| Librarian Profile | `librarian-profile.html` | `librarian-profile.js` | Single librarian view |
| Borrow Records | `borrow.html` | `borrow.js` | Borrow history, borrow/return actions |
| Analytics | `analytics.html` | `analytics.js` | Analytics dashboard |
| Reports | `reports.html` | `reports.js` | Report generation and CSV exports |
| Roles & Permissions | `roles.html` | — | Role reference page |
| Settings | `settings.html` | — | Application settings |
| Profile | `profile.html` | `profile.js` | Current user profile |
| Login | `login.html` | — | Authentication form |
| Register | `register.html` | — | Self-registration (unimplemented) |
| Add Book | `add-book.html` | — | Dedicated book creation form |

---

## JavaScript Module Graph

```text
index.html
└── js/main.js (type="module")
      ├── components/modal.js
      └── js/api/
            ├── http.js              → requestJson utility, CSRF header, credentials
            ├── auth-api.js          → csrf, login, logout, me
            ├── books-api.js         → list, create
            ├── magazines-api.js     → list, create
            ├── newspapers-api.js    → list, create
            ├── students-api.js      → list, create
            ├── librarians-api.js    → list, create
            ├── borrow-api.js        → list, create, returnBook
            ├── dashboard-api.js     → get
            ├── student-dashboard-api.js → getDashboard, getBorrowHistory
            ├── analytics-api.js     → analytics endpoints
            └── report-api.js        → CSV export endpoints

js/utils/
├── toast.js          → Temporary notification messages
├── confirm.js        → Confirmation dialogs
├── esc.js            → XSS-safe HTML escaping
└── pagination.js     → Page navigation controls
```

### API modules

Each API module (`books-api.js`, `students-api.js`, etc.) imports `requestJson` from `http.js` and exports typed functions for its resource. All non-GET requests automatically include the `X-XSRF-TOKEN` header from the `XSRF-TOKEN` cookie.

**Note:** The frontend JS API modules (`booksApi`, `studentsApi`, `librariansApi`) currently expose `list()` and `create()` methods. The backend supports PUT and DELETE endpoints, but the frontend has no UI to invoke them — the "Actions" column in each table renders a `—` dash placeholder.

### http.js — the Fetch helper

`http.js` exports `requestJson(url, options)` which:
- Sets `Content-Type: application/json` when a body exists.
- Uses `credentials: 'include'` for session cookies.
- Reads `XSRF-TOKEN` from cookies and attaches `X-XSRF-TOKEN` header on non-GET requests.
- Parses error responses into a uniform shape.
- Handles `204 No Content` without attempting JSON parse.

### main.js — the dashboard orchestrator

`main.js` is the entry point for the dashboard page. It:
1. Checks for an existing session via `authApi.me()`.
2. Loads role-specific data (student vs. admin/librarian) in parallel.
3. Renders dashboard cards, recent activity, and sidebar user info.
4. Wires the logout button.

---

## CSS Architecture

The frontend uses **two stylesheets**: a root-level `styles.css` (fonts, animations, and global styles) and `css/styles.css` (design tokens and CSS custom properties). Each HTML page loads `styles.css` from the root. Design tokens are implemented as CSS custom properties in `css/styles.css`.

| File | Purpose |
|------|---------|
| `styles.css` (root) | Fonts (Fraunces, Inter, IBM Plex Mono), animations, global layout and component styles |
| `css/styles.css` | Design tokens as CSS custom properties (`:root`), palette, radii, gradients |

### Design tokens (CSS custom properties)

The color scheme and typography are defined as CSS custom properties at `:root`:

- **Colors:** Indigo, violet, amber, green, red on a dark canvas. Semantic status colors for available/borrowed states.
- **Typography:** Google Fonts — `Fraunces` (headings), `Inter` (body), `IBM Plex Mono` (code).
- **Spacing, borders, shadows** — consistent values used throughout.

### Responsive breakpoints

| Breakpoint | Behavior |
|------------|----------|
| `< 680px` | Mobile: sidebar collapses, single-column layout, cards stack |
| `680px – 1024px` | Tablet: sidebar visible, two-column cards |
| `> 1024px` | Desktop: full sidebar, multi-column grid |

### Component styles

- **Cards:** White background, subtle shadow, rounded corners.
- **Tables:** Responsive horizontal scroll on mobile.
- **Badges:** Status indicators (Available/Borrowed/Returned) with semantic colors.
- **Buttons:** Primary (indigo gradient), ghost (neutral with border hover).
- **Modals:** Centered overlay with form fields, validation, and action buttons.
- **Toast notifications:** Temporary messages in `#toast-region`, auto-removed after 3.2s.
- **Sidebar rail:** Fixed left navigation with icons, labels, and user info.

---

## Modal System

`components/modal.js` provides a reusable accessible modal dialog. It defines 4 form types declaratively:

| Form Type | Fields | Purpose |
|-----------|--------|---------|
| `book` | title, author, isbn, publishedDate | Create/edit book |
| `student` | username, password, name, email, phone | Create student |
| `librarian` | username, password, name, age, phone | Create librarian |
| `borrow` | bookId, studentId, borrowDate | Record a borrow |

The modal handles:
- Field rendering from declarative definitions.
- Client-side validation (required fields, email format).
- Server-side `fieldErrors` rendering next to each field.
- General error display in a form-level summary.
- Safe DOM insertion (no `innerHTML` for untrusted content).

---

## Navigation

The sidebar rail (`<nav class="rail">`) contains two sections:

**Catalog:** Dashboard, Books, Magazines, Newspapers, Students, Librarians, Borrow Records, Analytics, Reports, Roles & Permissions.

**System:** Settings, Profile, Log out.

Navigation uses standard `<a href="page.html">` links. The active page is marked with `class="active"` on the corresponding `<a>` element. There is no client-side routing — each navigation triggers a full page load.

---

## Security in the Frontend

### CSRF flow

1. On page load, `auth-api.js` calls `GET /api/auth/csrf` to obtain the `XSRF-TOKEN` cookie.
2. `http.js` reads the cookie and attaches `X-XSRF-TOKEN` header on all non-GET requests.
3. The `SpaCsrfTokenRequestHandler` on the backend accepts the raw token for Fetch requests.

### XSS protection

`esc()` function (inline in `main.js` and as `utils/esc.js`) escapes `& < > ' "` in all rendered user content. All dynamic content is escaped before DOM insertion.

### Session management

- Session cookie is `HttpOnly` and `SameSite=Strict`.
- `credentials: 'include'` ensures cookies are sent with Fetch requests.
- On logout, the session is invalidated and the browser redirects to login.

---

## Known Gaps

| Gap | Description |
|-----|-------------|
| No PUT/DELETE UI | Backend supports update and delete endpoints, but the frontend has no UI to invoke them |
| `register.html` | Self-registration page exists but is not wired to any backend endpoint |
| `roles.html`, `settings.html` | Static pages with no dynamic data |
| No frontend tests | No JavaScript test files exist |
