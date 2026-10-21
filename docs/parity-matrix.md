# HAMCO parity baseline — HAM-5

**Reference:** running site `https://hamcois.com/`, observed October 21, 2026. Source inspection pinned to `hail2skins/hamco-aspnet@c9b49be9aff2ac161c083fa225bf9c4176561e9d`. Source code and deployed runtime are distinct evidence: the pinned `ApiKeyService.cs` has a broken string and is not presumed buildable. All requests below were **public GETs only**; no production writes, login attempts, or private records were used.

| GET route | Live HTTP / content type | Evidence / replication target |
| --- | --- | --- |
| `/` | 200 `text/html` | Public home; navbar links Home, Notes, About, Register, Login. |
| `/about` | 200 `text/html` | Public about page. |
| `/notes` | 200 `text/html` | Title “Technical Notes & Articles”; public list and shared masthead/nav. |
| `/notes/1` | 200 `text/html` | Public detail route. |
| `/auth/login` | 200 `text/html` | Form fields `email`, `password`; no credentials submitted. |
| `/auth/register` | 200 `text/html` | Register page appears, but no form in observed DOM: registration availability must not be inferred from navbar link. |
| `/api/notes` | 200 `application/json` | Three notes observed; JSON array of objects with keys `id`, `title`, `slug`, `content`, `userId`, `createdAt`, `updatedAt`. No ordering promise inferred. |
| `/api/notes/1` | 200 `application/json` | Same seven keys for a single note. |
| `/api/notes/999999` | 404 `application/problem+json` | Missing note response; verify exact body later before matching it. |

**Visual snapshot, observed at 1280px viewport:** a wide black/white illustrated masthead, bold white centered page heading, white horizontal uppercase navigation, and a subtitle/slogan on `/notes`. The image and text can vary because the reference randomly selects the masthead and an active slogan; visual comparisons must control or tolerate this variation. The source assets are under `src/Hamco.Api/wwwroot/img/`. Further desktop/mobile screenshots and authenticated admin flows remain unverified at this point.

**Source-only contract, not yet runtime verified:** POST/PUT/DELETE status codes, auth cookie, JWT/API key permissions, slogans/admin flows, email verification/reset, schema and migrations. See the [plan](HAMCO_SPRING_PARITY_PLAN.md) for source paths and intended behavior. Do not probe production mutation endpoints just to fill these rows. Later local Spring tests must be red before implementation and green after, then compare the running local server with the observed public HTTP behavior.

**Data migration decision:** not made; the local rewrite uses a separate empty `hamco_spring_dev` database and will not copy production content without separate authorization. For demo parity, tests or local fixtures can provide representative data without claiming it came from production.
