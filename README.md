# HAMCO Spring Boot rewrite

This repository is recreating the running [HAMCO ASP.NET site](https://hamcois.com/) and its [source repository](https://github.com/hail2skins/hamco-aspnet) with a Spring Boot API and, later, a React/TypeScript frontend. We are learning Spring through **strict red–green–refactor TDD**, with educational comments in code and tests. **The local API layer is implemented; the frontend has not been scaffolded.**

## Start here

- [ASP.NET → Spring/React parity plan](docs/HAMCO_SPRING_PARITY_PLAN.md) — source inventory, API/page mapping, proposed project structure, configuration mapping, and small TDD slices.
- [HAM Jira ticket map](docs/HAM_JIRA_TICKETS.md) — one ticket per parity step, with completed setup split into three early HAMs; Railway tickets are deferred.
- [Observed runtime parity baseline](docs/parity-matrix.md) — read-only public HTTP evidence from the running ASP.NET site, with unverified areas labeled.
- [Local API contract and verification](docs/API_CONTRACT.md) — all implemented routes, authentication modes, intentional security deviations, and test/runtime evidence.
- [Project idea](IDEA.md) — original scope.
- [Working reference site](https://hamcois.com/) — use public/read-only checks for runtime parity; never exercise write endpoints on production merely to test a rewrite.

The plan is also mirrored in Second Brain at **Projects → Hamco Blog → docs → Hamco Spring Boot Parity Plan** and linked from the Hamco Blog hub. The repo copy is canonical; synchronize the wiki mirror when revising it.

## Local prerequisite setup — completed October 21, 2026

The host is Apple Silicon macOS. Versions observed after setup:

| Tool | Version / result | Purpose |
| --- | --- | --- |
| Homebrew `openjdk@25` | Java and `javac` **25.0.5** | Pinned Java 25; do not rely on Homebrew's rolling `openjdk` (it also installed Java 27 as a Maven dependency). |
| Maven | **3.10.0**, running with Java **25.0.5** | The backend includes a Maven Wrapper (`backend/mvnw`) to pin the build tool. |
| Node / npm | **24.18.0 / 11.16.0** | Bootstrap and build Vite + React/TypeScript. Project-specific dependencies will be locked when scaffolded, not installed globally. |
| PostgreSQL client/server | **16.11**, `pg_isready` accepts connections on localhost:5432 | Local development. |
| Docker Desktop | Docker client/server **29.9.2** at setup verification; `docker run --rm hello-world` succeeded | Runs the disposable PostgreSQL containers used by backend integration tests. |
| Spring Boot | **4.1.1** | Generated from official Spring Initializr; Java 25 and Maven Wrapper. |

### Reproduce / verify the Java setup

Homebrew's Java 25 JDK was installed using `brew install openjdk@25 maven`. The shell must explicitly select Java 25, because Maven's Homebrew dependency may pull a newer rolling JDK. Both `~/.zprofile` (login shells) and `~/.zshrc` (non-login interactive shells) now contain:

```zsh
export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

Open a new shell, or source the relevant file, then verify:

```bash
java -version
javac -version
mvn -version                 # its `Java version` must say 25.0.5
node -v && npm -v
pg_isready
psql --version
docker info --format 'Docker server {{.ServerVersion}}'
```

Both login and non-login interactive `zsh` were checked and report Java/Maven running on **25.0.5**. The macOS `/usr/libexec/java_home` registry still does not list this Homebrew JDK: the optional system-wide registration requires an administrator-owned symlink under `/Library/Java/JavaVirtualMachines`. `JAVA_HOME` and `PATH` make it work correctly for these shells and Maven without sudo. If an IDE or GUI app specifically requires `java_home`, address that separately rather than changing system Java implicitly.

The Homebrew install printed a permission error while its **automatic cleanup** tried to remove an unrelated Tailscale package and returned nonzero. It nonetheless installed Maven and Java 25; both were independently verified afterward. To avoid that unrelated cleanup attempt on future installations, use `HOMEBREW_NO_INSTALL_CLEANUP=1 brew install ...`. Do not treat a package-manager exit code alone as proof a tool is unusable—or claim success without exercising it.

Docker Desktop was installed already but not running; it was started with `open -a Docker`, and a disposable `hello-world` container completed. On a fresh session, start Docker before Testcontainers tests if `docker info` cannot reach the daemon.

### Local PostgreSQL isolation

A **separate** database was created for this rewrite: `hamco_spring_dev`, owned by local role `art`. Starting Spring Boot applied `V1__baseline.sql`, creating `users`, `notes`, `api_keys`, `slogans` and Flyway's schema-history table. A single note titled “Local Spring Boot demo” (ID 1) was inserted **only into this local database** for the HTTP demonstration; it is not copied from production. The old `hamco_dev` database contains other work and must not be modified or used for migrations.

```bash
# Read-only connection and isolation check:
psql -d hamco_spring_dev -c 'select current_database(), current_user;'
psql -d hamco_spring_dev -c "select count(*) as public_tables from pg_tables where schemaname='public';"
```

The backend uses this DB for local development and disposable Testcontainers PostgreSQL 16 for integration tests; the tests never write to `hamco_spring_dev`. Configuration defaults are in `backend/src/main/resources/application.yml` and can be overridden with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` (environment variables; no automatic dotenv loader). Do not reuse the development DB as a test DB or copy password-like values from the ASP.NET example into the repo.

### Run and test the local API

From the repository root (start Docker Desktop first if it is stopped):

```bash
cd backend
./mvnw test                       # JUnit + disposable PostgreSQL via Testcontainers
./mvnw spring-boot:run            # local server on http://localhost:8080
```

In another terminal:

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/api/notes
curl -i http://localhost:8080/api/notes/1
curl -i http://localhost:8080/api/notes/999999
curl -i http://localhost:8080/api/auth/profile  # 401 without a token
curl -i http://localhost:8080/api/slogans       # 401 without admin auth
```

After the full API suite passed (39 tests), a **separate newly packaged process** on `127.0.0.1:18080` returned health **200 UP**, notes list/detail **200**, missing note **404**, unauthenticated profile/slogans/key management **401**, invalid API key **401**, and registration-disabled **403**. Flyway reached migration V3 on `hamco_spring_dev`. The older process on port **8080 was not restarted**; check which version it serves before evaluating. The notes sample in the local database is labeled demonstration data, not production content. Positive registration → verification → login → notes/slogans/key-management flows run against disposable PostgreSQL in integration tests; see the [API contract](docs/API_CONTRACT.md).

The TDD sequence began with failing context, migration and public-notes tests; auth, admin writes, slogans, API keys, email and security contracts were added red before implementation. The key-lookup and invalid-presented-key regressions were reproduced red, then fixed green. Tests use disposable containers and never write to `hamco_dev`. For environment variables, see [`.env.example`](.env.example); Spring does not load a dotenv file automatically. Registration is off by default, and the local email sink captures verification links in memory for tests. A human inbox test requires configured Mailjet credentials; none are committed.

### Railway later—not part of local development

The current phase is **local implementation and verification only** on the existing branch. No dev/prod branch creation, Railway configuration, or deployment is planned until Art starts that phase. A Git push updates the remote repository but does not itself mean deployment; pushes still require Art's explicit instruction, and commits require a relevant Jira identifier.

When deployment work is authorized, assess reusing the existing Railway dev and production PostgreSQL services rather than creating replacement database services. Before any migration, separately inventory each environment's schemas, tables, privileges, backup/restore path, connection variables and namespace strategy. Do not point Spring Flyway at an existing ASP.NET schema until collision and rollback questions are resolved. No Railway service or variable was changed in this setup pass.

## Current milestone and next boundary

The pinned ASP.NET source contains an apparently truncated `ApiKeyService.cs`; compare the **running live site** against our local API whenever source and behavior disagree. The [API contract](docs/API_CONTRACT.md) records where unsafe source behavior was deliberately changed. Production checks remain read-only.

**API milestone:** Spring Boot 4.1.1 serves registration/login/verification/reset/profile/cookie/logout, public and admin notes, admin slogans, and owner-scoped API-key management/authentication with strict access control. Tests cover real Spring HTTP dispatch, database migrations and full local auth flows. The React/MVC pages and Railway environments are **not** part of this API-only marker. A push of code does not deploy the service.
