# Local React UI milestone

The Vite/React frontend recreates the public and account/admin page paths from the pinned ASP.NET source (`c9b49be9aff2ac161c083fa225bf9c4176561e9d`). The production site was inspected **read-only** as the visual reference; the copied source stylesheet and four hamster masthead images live under `frontend/src/styles/source.css` and `frontend/public/img/`. React-specific corrections live in `frontend/src/styles/app.css`. The masthead rotates active slogans returned by Spring. The reference site's Font Awesome footer glyphs are now bundled from the free icon packages (Font Awesome by Fonticons, Inc., CC BY 4.0/MIT) rather than loaded as cross-origin JavaScript on login/admin pages. Google Fonts stylesheets remain external and are not executable scripts.

## Page surface

| Area | Browser paths | Behavior |
| --- | --- | --- |
| Public | `/`, `/about`, `/notes`, `/notes/:id` | Recent/list/detail notes from Spring, plaintext excerpts, sanitized Markdown detail, responsive Menu, rotating masthead, fallback/error states. |
| Accounts | `/auth/register`, `/auth/login`, `/auth/verification-pending`, `/auth/verify-email`, `/auth/forgot-password`, `/auth/reset-password` | Registration form only when Spring's public configuration permits it; login/cookie, verification redirect, reset and error notices. Registration is closed by default. |
| Admin notes | `/admin/notes`, `/create`, `/edit/:id`, `/delete/:id` | Published list and create/edit/delete confirmation through bearer-authorized Spring API. |
| Admin slogans | `/admin/slogans`, `/create`, `/edit/:id`, `/delete/:id` | List, active toggle, create/edit/delete through Spring API. |
| Admin keys | `/admin/apikeys`, `/create`, `/show/:id`, `/delete/:id` | Owner-scoped list/detail, one-time secret display, revocation rather than permanent deletion. |

The admin route gate distinguishes anonymous, non-admin, and admin users. The JWT exists only in React memory; an HttpOnly cookie can restore identity for safe reads on a reload but **cannot authorize writes**. After reloading an admin route, sign in again to manage content. In-app admin links use React Router navigation to preserve that in-memory token. A slow anonymous profile response cannot discard a newer login session. Failed login clears the password input so it does not linger in the DOM.

## Local development and verification

Use only `hamco_spring_dev`; never `hamco_dev`. Start PostgreSQL and Docker Desktop, then in separate shells:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd backend && ./mvnw test && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080

cd frontend && npm ci && npm run dev -- --port 5173
```

Open `http://127.0.0.1:5173/`. The local Vite `/api` proxy targets Spring on `127.0.0.1:18080`. `http://127.0.0.1:5173/api/notes` is JSON, not a page. Run the component/type/build and browser checks from `frontend/`:

```bash
npm test
npm run typecheck
npm run build
npx playwright install chromium
npm run test:e2e
```

Public browser tests run without credentials. The real admin browser tests are conditional on `HAMCO_E2E_ADMIN_PASSWORD` and require a **local-only, verified administrator** in `hamco_spring_dev` with email `local-admin@hamco.test`. Do not place its password in this repository or in shell history; inject it into the test process from a private local file. Tests create/edit/delete local notes and slogans, issue then revoke local API keys, and test that reload requires reauthentication. The backend rate-limits login; repeated browser suites within 15 minutes can return 429 and are not evidence of a broken form. Generated Playwright failure artifacts can capture form contents and are gitignored; remove them after a failed run.

Final local verification: **45 backend tests passed**, **27 frontend unit tests passed**, TypeScript typecheck and Vite build passed, and the real Spring-backed Playwright desktop/mobile suite reported **8 passed, 2 intentionally skipped** (each skip is a test for the opposite viewport). The UI was opened directly at `127.0.0.1:5173`; the login hero and form were compared with the read-only live reference at matching desktop dimensions. Port 8080 is an older API process and does **not** serve the React UI (`/` returned 404 at verification); do not direct a browser there for this milestone.

## Boundaries

- The API suite uses disposable Testcontainers PostgreSQL and tests registration → verification → login, including fake mail transport. The React registration and verification pages have unit/contract coverage, but **a real end-to-end email inbox journey has not been verified** without configured mail delivery and registration enabled. Do not describe an unverified human inbox as complete.
- The original UI milestone ran under Vite. The later [HAM-19 release review](RELEASE_READINESS.md) packaged the SPA in a Spring JAR and exercised it in desktop/mobile browsers locally. Railway configuration, live-content/administrator cutover, real mail and production promotion remain deferred; the local artifact is **not** release approval.
- Browser checks create data **only against the local Spring database**. The live ASP.NET reference is never a write-test target.
