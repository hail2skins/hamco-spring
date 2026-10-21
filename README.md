# HAMCO Spring Boot rewrite

This repository will recreate the running [HAMCO ASP.NET site](https://hamcois.com/) and its [source repository](https://github.com/hail2skins/hamco-aspnet) with a Spring Boot API and React/TypeScript frontend. We are learning Spring through **strict red–green–refactor TDD**, with educational comments in code and tests. **Application scaffolding and implementation have not started.**

## Start here

- [ASP.NET → Spring/React parity plan](docs/HAMCO_SPRING_PARITY_PLAN.md) — source inventory, API/page mapping, proposed project structure, configuration mapping, and small TDD slices.
- [HAM Jira ticket map](docs/HAM_JIRA_TICKETS.md) — one ticket per parity step, with completed setup split into three early HAMs; Railway tickets are deferred.
- [Observed runtime parity baseline](docs/parity-matrix.md) — read-only public HTTP evidence from the running ASP.NET site, with unverified areas labeled.
- [Project idea](IDEA.md) — original scope.
- [Working reference site](https://hamcois.com/) — use public/read-only checks for runtime parity; never exercise write endpoints on production merely to test a rewrite.

The plan is also mirrored in Second Brain at **Projects → Hamco Blog → docs → Hamco Spring Boot Parity Plan** and linked from the Hamco Blog hub. The repo copy is canonical; synchronize the wiki mirror when revising it.

## Local prerequisite setup — completed October 21, 2026

The host is Apple Silicon macOS. Versions observed after setup:

| Tool | Version / result | Purpose |
| --- | --- | --- |
| Homebrew `openjdk@25` | Java and `javac` **25.0.5** | Pinned Java 25; do not rely on Homebrew's rolling `openjdk` (it also installed Java 27 as a Maven dependency). |
| Maven | **3.10.0**, running with Java **25.0.5** | Bootstrap the backend; the future project will commit a Maven Wrapper. |
| Node / npm | **24.18.0 / 11.16.0** | Bootstrap and build Vite + React/TypeScript. Project-specific dependencies will be locked when scaffolded, not installed globally. |
| PostgreSQL client/server | **16.11**, `pg_isready` accepts connections on localhost:5432 | Local development. |
| Docker Desktop | Docker client/server **29.9.2** at final verification; `docker run --rm hello-world` succeeded | Testcontainers integration tests when the backend exists. |
| Spring Initializr | **4.1.1.RELEASE** latest non-preview version observed | Recheck before generating the backend; no Spring app exists yet. |

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

A **new, empty** database was created for this rewrite: `hamco_spring_dev`, owned by local role `art`. It currently has **zero public tables**. The old `hamco_dev` database contains other work and must not be modified or used for migrations.

```bash
# Read-only connection and isolation check:
psql -d hamco_spring_dev -c 'select current_database(), current_user;'
psql -d hamco_spring_dev -c "select count(*) as public_tables from pg_tables where schemaname='public';"
```

The future backend should use this DB locally and Testcontainers PostgreSQL for isolated integration tests. Do not reuse a dev database as a test database. No `.env`, DB schema, table, or application user was created in this setup pass. Local credentials and configuration precedence will be documented when the application exists; never copy password-like values from the ASP.NET example into the repo.

### Railway later—not part of local development

The current phase is **local implementation and verification only** on the existing branch. No dev/prod branch creation, Railway configuration, or deployment is planned until Art starts that phase. A Git push updates the remote repository but does not itself mean deployment; pushes still require Art's explicit instruction, and commits require a relevant Jira identifier.

When deployment work is authorized, assess reusing the existing Railway dev and production PostgreSQL services rather than creating replacement database services. Before any migration, separately inventory each environment's schemas, tables, privileges, backup/restore path, connection variables and namespace strategy. Do not point Spring Flyway at an existing ASP.NET schema until collision and rollback questions are resolved. No Railway service or variable was changed in this setup pass.

## Readiness / next boundary

The public home, notes, about, login, register pages and `GET /api/notes` on the live site loaded successfully. The pinned source checkout contains an apparently truncated `ApiKeyService.cs`; use the **working live site** as the runtime reference when source and behavior disagree. Unsafe source behaviors are recorded as proposed security deviations in the plan, not implemented or silently approved here.

**Ready for a separately authorized first TDD slice.** The `backend/` Maven project, Maven Wrapper, `frontend/` app, test suites, `.env.example`, and application migrations have deliberately **not** been created yet. No commit, push, or deployment has been made.
