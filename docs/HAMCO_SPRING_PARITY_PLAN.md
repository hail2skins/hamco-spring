# HAMCO ASP.NET → Spring Boot + React/TypeScript Implementation Plan

> **For Hermes:** Execute this plan one small ticket at a time using strict red–green–refactor TDD. Spring Boot 4.1.1 and the React/Vite UI now run locally: public pages, auth forms, admin CRUD screens and API security have test coverage. One-artifact JAR packaging and safe deep-link routing were implemented locally under HAM-19; Railway and the data/administrator cutover remain deferred. Every commit needs a relevant HAM identifier; Art explicitly authorized pushes for this workstream. See [the release review](RELEASE_READINESS.md) for the current boundary.

**Goal:** Recreate the ASP.NET HAMCO site and JSON API, matching its routes, content, forms, authorization and visual behavior, while using idiomatic Spring Boot and React/TypeScript and teaching the technologies through explanatory comments and README instructions.

**Architecture:** A single Maven Spring Boot backend exposes the existing `/api/...` contract; Vite serves and proxies the React SPA during local development, while `mvn package` embeds the compiled SPA and assets into the independently runnable Spring JAR. The SPA preserves public and admin URL paths and reproduces the Razor site's layout and interactions; a Spring service/repository boundary handles domain rules and PostgreSQL. Develop and verify locally first on the existing branch in the new `hamco_spring_dev` database, separate from `hamco_dev`. **Stop after local parity work until Art explicitly starts a dev/prod branch and Railway phase.** At that later phase, assess reusing each environment's existing PostgreSQL without destructive migrations or collisions; do not provision replacement Railway databases by default. Avoid reproducing demonstrated security flaws merely for literal fidelity; note such deviations and test the public behavior.

**Tech Stack:** Spring Boot 4.1.1 uses pinned Java 25.0.5, Maven Wrapper, Spring Web MVC/Security, Validation, Flyway + PostgreSQL, Actuator, JUnit 5, MockMvc and Testcontainers PostgreSQL. React 19 + TypeScript 7 + Vite 8 use Vitest, Testing Library and Playwright for the browser UI. Verify compatible release/dependency versions from official metadata when upgrading. Maven 3.10.0, Node 24.18.0, npm 11.16.0 and PostgreSQL 16.11 are available locally; Docker Desktop ran a `hello-world` container.

---

## 1. Source of truth and observed baseline

- Source repository: `hail2skins/hamco-aspnet`, default HEAD inspected at `c9b49be9aff2ac161c083fa225bf9c4176561e9d` (commit dated 2026-02-16); freeze this SHA in the parity checklist, then explicitly approve any subsequent upstream changes. Source paths below refer to this SHA. The new implementation uses the existing `hamco-spring` branch; the initial single-file snapshot is historical, not the current tree.
- The source is layered: `src/Hamco.Api/Controllers`, `Views`, `Middleware`, `wwwroot`; `src/Hamco.Core/Models`, `Services`, `Utilities`; `src/Hamco.Data/HamcoDbContext.cs` and `Migrations`; `src/Hamco.Services`; `tests/Hamco.Api.Tests` and `tests/Hamco.Core.Tests`. Ignore committed `bin/`, `obj/`, and `publish-test/` when deriving architecture.
- The pinned `src/Hamco.Services/ApiKeyService.cs` has a complete `CreateClaimsPrincipal` method; an earlier truncation claim was incorrect. The entire source commit is **not presumed buildable** without running its build. An existing `test-results.txt` is historical, not a passing test run. **The working runtime reference is `https://hamcois.com/`**: on 2026-10-21 its home, notes, about, login and register pages and public notes API loaded successfully. Compare source intent against observable live behavior; do not send test mutations to production.
- Existing local `hamco_dev` database is **not empty**: it contains legacy `Invites`, `Users`, `Villages` plus `users`, `notes`, `slogans`, `api_keys` and EF migrations. **Do not drop, alter, or reuse it.** The new `hamco_spring_dev` database was created under `art`; Flyway V1 now owns its four domain tables and a clearly labeled local demonstration note. Integration tests use disposable PostgreSQL containers.
- Source references: `src/Hamco.Api/Program.cs`, all controllers/views under `src/Hamco.Api`, `src/Hamco.Data/HamcoDbContext.cs`, the migration snapshot, `src/Hamco.Services/*`, `environment.example`, `App.csproj`, and `tests/*` at the frozen commit. Continue extending the annotated route/behavior matrix in `docs/parity-matrix.md` as implementation proceeds.

### API contract to preserve

| Route(s) | Access | Core behavior / test target |
| --- | --- | --- |
| `POST /api/auth/register` | Public, auth rate limit | Disabled unless `ALLOW_REGISTRATION=true` → 403; trimmed/lowercase email, first user Admin, unverified duplicate resends token, verified duplicate 409, new user + 20-minute email verification 200. Test validation and disabled path first. |
| `GET /api/auth/verify-email?token=` | Public | Consume hashed token once; redirect to `/auth/login?verified=1`, invalid/missing redirect with error query. |
| `POST /api/auth/login` | Public, rate limit | 401 invalid credentials, 403 unverified, 200 token/user data/roles/expiry. Login itself does not set cookie. |
| `POST /api/auth/forgot-password`, `POST /api/auth/reset-password` | Public, rate limit | Forgot always returns generic 200; hashed expiring reset token + email; reset invalid 400 or success 200 and consume token. |
| `GET /api/auth/profile` | Authenticated | 200 user profile or 401. |
| `POST /api/auth/cookie`, `GET /api/auth/logout` | Public in source | Set `AuthToken` cookie or delete it and redirect `/`; **validate JWT before setting cookie**, a deliberate security deviation from source. Examine CSRF implications before shipping. |
| `GET /api/notes`, `GET /api/notes/{id}` | Public | Exclude `deleted_at` rows; detail 404 if absent; list ordering is unspecified in API. |
| `POST /api/notes`, `PUT /api/notes/{id}`, `DELETE /api/notes/{id}` | Admin JWT or admin key | 201 create, 200 update, 204 hard delete; missing record 404; title/content validation; slug regenerates on title change; API-key created notes can have null user_id. |
| `GET/POST /api/slogans`, `PUT/DELETE /api/slogans/{id}` | Admin | GET all newest-first including inactive; POST trims, default active true; PUT partial text/active; delete hard; invalid text 400. |
| `GET/POST /api/admin/api-keys`, `GET/DELETE /api/admin/api-keys/{id}` | Admin | Creator-scoped list/detail; create plaintext key once in 201; DELETE revokes rather than deletes. **Require ownership on revoke**, unlike source. |

Source paths: `src/Hamco.Api/Controllers/AuthController.cs`, `NotesController.cs`, `Admin/SlogansController.cs`, `Admin/ApiKeysController.cs`, and request/response types in `src/Hamco.Core/Models/`.

### Browser/page contract to preserve

- Public: `GET /` (five newest notes and ~500-character excerpts), `/about`, `/notes` (newest first, ~100-character excerpts), `/notes/{id}` (Markdown detail), `/auth/register`, `/auth/verify-email`, `/auth/verification-pending`, `/auth/login`, `/auth/forgot-password`, `/auth/reset-password`.
- Admin: `/admin/notes` (list, create, edit, delete confirmation), `/admin/slogans` (list, create, edit, delete, toggle), `/admin/apikeys` (list, create, display newly generated key, revoke, delete). Preserve URLs, redirects, validation messages and visible outcomes, even though React will not POST HTML forms to server-rendered views.
- Recreate `Views/Shared/_Layout.cshtml`: Clean Blog/Bootstrap responsive navigation; role-sensitive Notes destination; public Home/About; login/register vs admin Slogans/API Keys; random slogan and masthead image; footer, headings, responsive spacing. Copy licensed/owned `wwwroot/img/HAMCO.jpg`, favicon, and `img/main/hammy1.png`–`hammy4.png`; review licenses/availability of external fonts, Font Awesome, Prism, Bootstrap, and Umami. The local UI now keeps the copied source stylesheet intact and puts React-only fixes in `app.css`; compare desktop/mobile screenshots against a **verified running reference** and label unverified visual assumptions.
- Source paths: `src/Hamco.Api/Controllers/{HomeController,NotesViewController,AuthViewController}.cs`, `Controllers/Admin/{AdminNotesController,AdminSlogansController,AdminApiKeysController}.cs`, `Views/**/*`, `wwwroot/**/*`.

### Data and domain mapping

| SQL table | Essential schema/behavior |
| --- | --- |
| `users` | text GUID `id` PK; required username/email/password_hash; created_at; booleans is_admin/is_email_verified; nullable verification/reset token hashes and expiries. The source **lacks a unique email index** despite a comment; in the new app add case-insensitive uniqueness transactionally and record this as a safety deviation. Avoid storing raw tokens. |
| `notes` | integer identity id; title and slug varchar(255), content text, nullable `user_id` FK ON DELETE SET NULL, created_at/updated_at, nullable deleted_at. No slug uniqueness; deletion currently hard-deletes. |
| `api_keys` | GUID text id; name varchar(255), BCrypt key_hash varchar(60), key_prefix varchar(8), is_admin, expires_at, created_at, required created_by_user_id varchar(36) (not a foreign key), is_active. Prefixes are not unique; plaintext is never stored. |
| `slogans` | integer identity id; text varchar(500), is_active, created_at, nullable created_by_user_id and updated_at. |

- Reproduce the **effective final schema**, not every EF migration artifact: `20260206234127_MakeNoteUserIdRequired` has empty `Up`/`Down` and nullable `notes.user_id` remains. Map old names and casing precisely if data migration becomes a requirement; a greenfield DB can use idiomatic snake_case with documented API compatibility.
- Slugs are lowercase ASCII-only, strip non `[a-z0-9\s-]`, whitespace→hyphens, collapse/trim hyphens. Validate max length rather than letting a DB overflow. Markdown supports advanced syntax and syntax highlighting; sanitize every rendered HTML output (source's plain path does not). Passwords use BCrypt strength 12; JWT HS256 issuer/audience, 60-minute expiry, zero clock skew; API keys are `hamco_sk_` + 64 lowercase hex characters, BCrypt-hashed, optional expiration, admin/user claims and `X-API-Key` fallback when no valid JWT principal. Check revocation immediately rather than retaining a five-minute stale positive cache. Verification/reset URL-safe random 32-byte tokens are stored as SHA-256 hashes and expire after 20 minutes. Random active slogan fallback: “Your trusted partner in Internet solutions”; four masthead images.
- Sources: `src/Hamco.Data/HamcoDbContext.cs`, `Migrations/*`, `src/Hamco.Core/Models/*`, `Utilities/SlugGenerator.cs`, `Services/{JwtService,PasswordHasher}.cs`, `src/Hamco.Services/{ApiKeyService,MarkdownService,SloganRandomizer,ImageRandomizer,MailjetTransactionalEmailService}.cs`.

## 2. Target repo structure (local backend and frontend implemented)

```text
README.md                     # teach local setup, architecture, run/test, Railway, parity status
AGENTS.md                     # durable TDD, comment policy, Jira commit and no-push rules
.gitignore                    # .env*, except example; target/, frontend/node_modules/, dist/, etc.
.env.example                  # placeholders only; no real passwords or production values
docs/parity-matrix.md         # source SHA, route/page/test and deliberate deviations
backend/pom.xml               # Maven Boot parent + wrapper in backend/.mvn/, backend/mvnw
backend/src/main/java/com/hamco/{HamcoApplication,config,auth,notes,slogans,apikeys,mail,shared}/
backend/src/main/resources/application.yml
backend/src/main/resources/db/migration/V1__baseline.sql
backend/src/test/java/com/hamco/...  # same packages; unit, MVC, DB integration tests
frontend/package.json         # Vite React TS, typecheck/lint/tests/browser tests
frontend/src/{app,pages,components,features,api,styles}/
frontend/public/img/...        # source brand imagery
frontend/tests/e2e/...        # Playwright parity scenarios
.github/workflows/ci.yml      # future build/test gates, no implicit deployments
```

Prefer feature-first packages with controller/service/repository/model/DTO near each other; do not mechanically port .NET layer/project boundaries. React consumes typed JSON DTOs via a small API client (OpenAPI generation is an option once endpoints stabilize). The local Vite proxy sends `/api` to `127.0.0.1:18080`; the development frontend runs on `127.0.0.1:5173`. Maven now packages the SPA as Spring static resources and forwards known page paths to `index.html`, excluding API and asset paths. Playwright exercises React Router deep links against both Vite and a separate local process running the packaged JAR; the latter is **not** a production deployment proof.

## 3. Step-by-step ticket-sized execution order (in progress)

The [HAM Jira ticket map](HAM_JIRA_TICKETS.md) assigns each numbered step a real ticket. Step 2 was split into three completed setup tickets (`HAM-2`–`HAM-4`), all under epic `HAM-1`; local-first work begins with baseline `HAM-5` and scaffold `HAM-6`. The Railway tickets `HAM-17` and `HAM-18` are deferred.

For **every code slice** below: write a highly commented behavior-focused failing test; run it and capture the intended failure; implement the smallest code that passes; rerun targeted and relevant suites; refactor and rerun; add a plain-English explanation of why the test matters. Comments should explain Spring/Java/React concepts and non-obvious choices, not narrate obvious syntax or rot into redundant line-by-line prose. Every commit needs its real `HAM-<number>` ticket; Art has explicitly authorized pushes during this implementation workstream.

1. **Baseline capture (read-only and docs):** The live reference `https://hamcois.com/` was confirmed up for public pages and notes API. Later capture safe redacted JSON/status/header behavior and public/admin screenshots at desktop/mobile. Mark source-only observations vs tested runtime behavior; do not perform test writes on the live production site. Create `docs/parity-matrix.md`. Decide whether data import is required (not assumed).
2. **README + local tools:** The setup pass installed `openjdk@25` 25.0.5 and Maven 3.10.0, configured `JAVA_HOME` and `PATH` in both `~/.zprofile` and `~/.zshrc`, and verified login/nonlogin interactive zsh. Node/npm, PostgreSQL and Docker were checked. The README documents real commands and results. The backend now includes a Maven Wrapper. The system `/usr/libexec/java_home` still does not list this Homebrew JDK without administrator-level symlink installation; shells have a working `JAVA_HOME`.
3. **Repository skeleton:** Scaffold Spring Initializr Maven Java app in `backend/` and Vite `react-ts` in `frontend/`, pin versions/lockfile/wrapper, configure `.gitignore` and `AGENTS.md`. First backend red test is Spring context load; first frontend red test is a real layout component assertion. Verify red→green, `./backend/mvnw test`, `npm --prefix frontend run test`, `npm --prefix frontend run build`.
4. **Database foundation:** `hamco_spring_dev` now has the Spring-owned Flyway V1 schema and a local demonstration note; never mutate `hamco_dev`. The Testcontainers PostgreSQL test asserts the four core tables, constraints and startup migration. Hibernate validates the schema rather than updating it. Remaining hardening: verify every index/FK explicitly and add sanitized configuration diagnostics; no secrets in logs.
5. **User registration guard + validation:** Tests for 403 when disabled, invalid fields, normalized email, first-admin bootstrapping, duplicate verified/unverified branches and concurrent uniqueness; implement only enough per red test. Ensure registration is disabled in production by default.
6. **Auth primitives:** BCrypt 12, HS256 issuer/audience/clock, bearer/cookie auth, role mapping, denial responses; test unknown/expired/forged token and Bearer-over-cookie precedence. Decide and test CSRF strategy before cookie writes and admin mutations; do not blindly port cookie endpoint vulnerability.
7. **Verification/password reset/email:** Tests for hashed, one-use, 20-minute tokens, generic forgot response, Mailjet request shape and failure handling with a fake HTTP service, and email delivery configuration. Mailjet sandbox/stub for local development to avoid silent no-email dead ends.
8. **Notes model/API:** Migrate `users`/`notes`, test CRUD HTTP status/DTO shapes, deleted_at filtering, slug edge cases, nullable author for admin API key, admin-only writes. Exercise a real PostgreSQL integration test, not H2 pretending to be Postgres.
9. **Public React site:** Recreate layout/branding, index/about/list/detail including excerpts, Markdown + code highlighting, mobile navigation and page metadata. For each page: fail a component test, implement, pass; add Playwright direct-link, keyboard and screenshot checks.
10. **Slogans API/admin:** Test list ordering, active toggle, fallback, CRUD validation; build React forms/list/toggle to match source and rerun browser flows.
11. **Admin notes UI:** Test permissions, create/edit/delete confirmation, form validation, slug behavior and navigation. Maintain equivalent visible messages; verify browser flows with real API responses.
12. **API keys API/admin:** Test secure generation/hashing, plaintext display once, creator-scoped visibility and revocation/deletion, bearer vs `X-API-Key`, expiration and immediate revocation. Implement admin list/create/show/revoke/delete pages. Resolve source's creator-ownership bug as a deliberate deviation.
13. **Auth React pages:** Registration availability, verification pending, login + cookie, logout, forgot/reset password, error query messages, authenticated nav and blocked admin routes. Browser-test the full journey with a Mailjet test double and a real PostgreSQL DB.

**Local-first stopping point:** Continue tightening API, browser, security and visual parity locally before attempting tasks 14–15. Art authorized commits and pushes for this local-first workstream; a Git push updates the remote but does not trigger Railway work by itself. Do not create dev/prod branches or configure Railway until Art explicitly begins that phase.

14. **Railway dev (deferred):** First inventory the **existing Railway dev PostgreSQL** ownership, schemas, migrations, permissions and backups; design a collision-free side-by-side Spring schema or database within that existing service, with explicit credentials and rollback. Do not touch existing ASP.NET tables without approval. The local HAM-19 package step builds and embeds the frontend; in the later authorized deployment, configure the app service to use that build, bind `0.0.0.0:${PORT}`, migrate only the approved namespace, expose `/actuator/health`, and repeat packaged-server deep-link, asset, auth and DB checks there. Production remains untouched.
15. **Railway production and parity gate:** After dev passes, inventory the **existing Railway production PostgreSQL** separately and propose the same safe side-by-side reuse rather than creating a replacement DB by default. Compare matrix row by row, run all backend unit/integration, frontend unit/typecheck/lint/build, Playwright desktop/mobile and manual visual/API checks. Require explicit production promotion authorization; document outstanding intentional differences and rollback plan.

## 4. Configuration mapping and `.env.example` deployment considerations

| New application variable (placeholder only) | Source equivalent / owner | Notes |
| --- | --- | --- |
| `DATABASE_URL=postgresql://<user>:<password>@localhost:5432/hamco_spring_dev` | `DATABASE_URL`; Railway's private URL for each environment | Convert to JDBC via a tested parser or set explicit `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<database>` plus username/password. Handle URL-encoded credentials, TLS mode, percent characters and Railway URL formats; never print secrets. Prefer Railway private networking, not `DATABASE_PUBLIC_URL`. |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | `PGUSER`/`PGPASSWORD`, or parsed `DATABASE_URL`; fallback `DB_USER`/`DB_PASSWORD` | Use **one** documented precedence rule. Railway PG* and POSTGRES_* variables belong to its DB service and need not all be copied to the app service. |
| `JWT_KEY`, `JWT_ISSUER`, `JWT_AUDIENCE` | Same names | Production key must be strong and per-environment; never commit it. Preserve issuer/audience compatibility only if migrating existing sessions. |
| `ALLOW_REGISTRATION=false` | Same name in source code; **omitted** in `environment.example` | Explicitly set per environment; source defaults to locked. |
| `MAILJET_API_KEY`, `MAILJET_API_SECRET`, `DEFAULT_FROM_EMAIL`, `DEFAULT_FROM_NAME` | Source service actual names | Source `environment.example` instead says `MAILJET_SECRET_KEY`, `FROM_EMAIL`, `FROM_NAME`; these **do not match running code**. Use actual service names in new `.env.example`. |
| `APP_BASE_URL` | Source `AuthController.BuildAbsoluteUrl` reads this name (omitted in `environment.example`) | Set distinct public HTTPS origins for Railway dev/prod verification/reset links. |
| `PORT` | Same name (Railway) | Default 8080 locally; Railway supplies service port. |
| `DEVELOPMENT_MODE` | Source cookie Secure toggle | Do not let a dev flag disable Secure cookies in production; use environment-aware settings. |

Do **not** copy source `environment.example`'s password-shaped literals, `DATABASE_PUBLIC_URL`, `PGDATA`, `POSTGRES_*`, `RAILWAY_DEPLOYMENT_DRAINING_SECONDS` or `SSL_CERT_DAYS` into the app's required variable list unless deployment genuinely consumes them. `ASPNETCORE_*` names do not apply to Java. `.env` stays untracked; README must show how to load local values explicitly (Spring does not load dotenv by default), and each Railway service/environment receives its own variables. Verify `APP_BASE_URL` and scheme/forwarded header behavior when deploying behind Railway.

## 5. Verification commands and evidence required when implementation starts

```bash
# Inspect toolchain; Java 25 is pinned in the shell config rather than rolling openjdk.
export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version && javac -version
./backend/mvnw -f backend/pom.xml test
npm --prefix frontend ci
npm --prefix frontend run test -- --run
npm --prefix frontend run typecheck
npm --prefix frontend run build
# Later, after installing Playwright browsers and starting app/test DB:
npm --prefix frontend run test:e2e
```

Each test file belongs beside its feature. Examples: `backend/src/test/java/com/hamco/notes/NotesApiTest.java`, `backend/src/test/java/com/hamco/site/SiteConfigApiTest.java`, `frontend/src/features/auth/auth.test.tsx`, and `frontend/tests/e2e/{public,admin}.spec.ts`. The local browser tests exercise public routes and authenticated CRUD through Spring; a real inbox verification journey still requires configured mail delivery. See [UI_MILESTONE.md](UI_MILESTONE.md) for fixture constraints and observed visual comparison.

## 6. Open decisions and explicit risks

1. **Reference baseline:** `https://hamcois.com/` is the confirmed live runtime reference. Buildability of the full pinned source has not been verified; its `ApiKeyService.cs` is complete, contrary to the initial partial-file assessment. Use public, read-only runtime observations to resolve source-vs-runtime discrepancies. Source comments and README can be stale (e.g. registration README says open; source defaults disabled).
2. **Exactness vs safety:** plan preserves public API/UI but intentionally fixes unvalidated auth cookie, missing unique email, insecure Markdown render, admin API-key ownership gaps and stale positive key cache. Obtain approval for these deviations; do not silently reproduce vulnerabilities.
3. **Data migration:** greenfield is assumed. If old notes/users/keys must migrate, plan a separate migration ticket for ID, bcrypt, JWT and token compatibility; no live DB writes until scope agreed.
4. **CSRF and SPA auth:** cookie-backed admin writes require robust CSRF defense or an alternative bearer-token design. Changing auth transport can alter the original browser/API behavior; choose and test explicitly.
5. **Frontend fidelity:** moving Razor HTML forms to React changes wire-level POST/redirect behavior. Match user-visible pages and preserve JSON API contracts; document differences.
6. **Comment volume:** provide abundant *educational* comments in source and tests, but prefer durable README architectural explanations to noisy syntax repetition.
7. **Deployment:** source's Railway `DATABASE_SCHEMA=dev` is a proposal in docs, not implemented behavior. Reuse existing Railway dev/prod PostgreSQL environments where safe, with isolation from current ASP.NET tables and independent backups. Confirm actual grants/schema ownership and buildpack Java 25 support before designing migrations or choosing Dockerfile vs native build.
8. **Jira:** `HAM-1` through `HAM-23` were created; the release-review slices are `HAM-19`–`HAM-23`. See [the ticket map](HAM_JIRA_TICKETS.md). Use the real ticket for every commit; do not invent keys. Art approved pushes for this workstream.

**First local milestone (2026-10-21):** tools installed; separate `hamco_spring_dev` created and migrated; Spring Boot 4.1.1 backend generated; failing-then-green context, migration and public-notes HTTP tests exercised against disposable PostgreSQL. Later local slices added authentication, writes, the React frontend, single-artifact packaging and desktop/mobile browser verification. **Not performed:** real inbox verification, approved live-content/first-admin cutover, Railway inspection/changes or deployment. See [release gates](RELEASE_READINESS.md).
