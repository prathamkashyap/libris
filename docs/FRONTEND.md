# Frontend

The browser client is a multi-page application served by Spring Boot from
`backend/src/main/resources/static/`. Each page loads native JavaScript ES
modules; the app does not require a frontend bundler.

## Structure

- HTML pages live at the static root.
- Shared styles are in `styles.css`.
- Page behavior and API wrappers are in `js/` and `js/api/`.
- Shared utilities are in `js/utils/`.
- Shared page components are in `components/`.
- The supplied canonical mark is `assets/brand/libris-mark.svg`, with the
  matching raster file `assets/brand/libris-mark.png`.

Use actual paths under `static/` when adding references. Previous paths such
as `main.js`, `login.css`, and `assets/library-mark.svg` are not current.

## Requests and authentication

API calls use Fetch with same-origin session credentials. The client obtains
CSRF state from `/api/auth/csrf` and sends `X-XSRF-TOKEN` on unsafe requests.
Registration sends the backend DTO fields; password confirmation is checked
client-side only. Backend validation remains authoritative. The registration
error utility displays recognized `fieldErrors` beside inputs and keeps a
general fallback for unexpected responses.

## Accessibility and verification

Preserve semantic labels, useful alternative text for the mark, keyboard
operation, focus states, and responsive layouts. Focused Node tests cover the
registration error-mapping utility; they do not replace browser testing across
viewports.
