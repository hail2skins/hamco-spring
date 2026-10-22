# Local API contract and verification

This is the Spring Boot API milestone against the pinned ASP.NET source commit
`c9b49be9aff2ac161c083fa225bf9c4176561e9d`. The source audit establishes
*intended* behavior; public production GETs in `parity-matrix.md` establish
*observed* behavior. We do not exercise production write or auth endpoints.
The HTML/MVC pages and React client are separate frontend work, not API routes.

## Routes

| Method | Route | Access | Response / important behavior |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public, registration flag | 403 if disabled; 200 with verification required; 409 if email already verified. First account becomes admin. |
| GET | `/api/auth/verify-email?token=...` | Public | 302 to login with success/error query marker; one-time token. |
| POST | `/api/auth/login` | Public | 200 JWT, userId, email, roles, expiresAt; 401 invalid credentials; 403 unverified. |
| POST | `/api/auth/forgot-password` | Public | 200 generic message for known and unknown addresses. |
| POST | `/api/auth/reset-password` | Public | 200 after one-time reset, 400 invalid/expired token. |
| GET | `/api/auth/profile` | JWT or safe-method auth cookie | 200 profile, 401 without valid auth. The source intentionally returns an empty token and minimum expiry here. |
| POST | `/api/auth/cookie` | Public with valid JWT in body | 200; HttpOnly, SameSite=Strict auth cookie. |
| GET | `/api/auth/logout` | Public | 302 to `/`, clears cookie. |
| GET | `/api/notes` and `/api/notes/{id}` | Public | 200 published notes; 404 unknown ID. |
| GET | `/api/site/config` | Public | 200 `{ "registrationEnabled": false }` by default; reflects `ALLOW_REGISTRATION`, without revealing secrets. |
| GET | `/api/site/slogan` | Public | 200 `{ "text": "..." }` with a random active slogan or the site's fallback. |
| POST | `/api/notes` | Admin JWT or admin API key | 201 and Location; validates title/content, generates slug. |
| PUT, DELETE | `/api/notes/{id}` | Admin JWT or admin API key | 200 updated, 204 deleted, 404 unknown ID. Delete is hard; the nullable `deleted_at` column remains for legacy compatibility. |
| GET, POST | `/api/slogans` | Admin JWT or admin API key | 200 list, 201 create; validates text. |
| PUT, DELETE | `/api/slogans/{id}` | Admin JWT or admin API key | 200 updated, 204 deleted, 404 unknown ID. |
| GET | `/api/admin/api-keys` and `/{id}` | Admin **user JWT** | Owner-scoped metadata; never reveals raw key/hash. |
| POST | `/api/admin/api-keys` | Admin **user JWT** | 201; secret shown exactly once. |
| DELETE | `/api/admin/api-keys/{id}` | Admin **user JWT** | 204 revoke, 404 missing/not owned. |
| GET | `/actuator/health` | Public | Local health status, not an ASP.NET route. |

Invalid models return 400. Unauthenticated protected calls return 401; an authenticated
non-admin receives 403. Auth registration/login/forgot/reset share an IP-based
five-request/15-minute limit (429 and `Retry-After`), configurable in tests.

## Deliberate security deviations

- The ASP.NET cookie endpoint accepts an arbitrary token string. Spring verifies
  the JWT signature, issuer, audience, expiration and verified user first.
  Cookies authenticate only GET/HEAD/OPTIONS; writes require an explicit bearer
  or API key. This avoids cookie-driven CSRF when sessions are stateless.
- The source's eight-character key prefix is identical for every generated
  key. Spring keeps that *display* field for compatibility, but indexes a
  SHA-256 digest of the entire high-entropy secret and verifies BCrypt before
  authorizing. A nullable legacy path permits *Spring-issued* keys from before
  migration V3 to authenticate until rotated: Spring BCrypt-hashes the SHA-256
  digest, while ASP.NET BCrypt-hashes the **raw** key. Imported ASP.NET hashes
  do **not** work as-is; decide a tested compatibility path or owner-coordinated
  rotation under [HAM-23](RELEASE_READINESS.md). No positive cache delays revocation.
- The source's key-management route can be guarded by an admin API key. Here
  it additionally requires a human admin JWT: an automation secret cannot
  create or enumerate further credentials.
- Emails and reset tokens are stored only as hashes; no raw password, token,
  or key belongs in Git, server logs or API-key metadata. Production profiles
  refuse to start without a strong JWT secret and explicit Mailjet delivery.

## Local verification and configuration

`cd backend && ./mvnw test` runs MockMvc integration tests, PostgreSQL 16
Testcontainers, Flyway migrations and a fake HTTP transport for Mailjet. These
tests exercise registration → email verification → login → admin authorization,
cookie/profile/logout, password reset/replay rejection, note/slogan writes,
API-key creation/use/revocation, cross-owner restrictions and negative paths.
They never write to `hamco_dev` or the live ASP.NET service. **The Mailjet
adapter is transport-tested but actual delivery is not proven without real
credentials.** The default local mail sink captures links in memory for tests;
for manually verifying an inbox, configure Mailjet separately.

The local server reads `hamco_spring_dev` by default; see `.env.example` for
variable names. It does **not** load `.env` automatically. Registration defaults
to disabled (`ALLOW_REGISTRATION=false`). A blank `JWT_KEY` generates an
*ephemeral* local signing key on every restart; supply a distinct strong key
and Mailjet credentials before any real deployment. No Railway work is part
of this milestone.

At the API milestone the 39-test suite passed; after the public site routes
were added, the backend suite reached 45 passing tests. A separate packaged server
on `127.0.0.1:18080` reported: health 200, notes list/detail 200, unknown note
404, unauthenticated profile/slogans/key management 401, bad key 401, disabled
registration 403. The existing process on port 8080 was left untouched; it may
still be serving the earlier read-only build. Check the process before assuming
which version a browser request reached.
