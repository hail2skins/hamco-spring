# Release review and cutover gates

The independent release review identified five distinct concerns; [HAM-19 through HAM-23](HAM_JIRA_TICKETS.md) track them. This is **not** a promotion approval. The public ASP.NET service and all Railway services are unchanged. Local tests use `hamco_spring_dev` or disposable PostgreSQL, **never** the separate `hamco_dev` database.

| Finding | Local outcome | Still required before replacing the site |
| --- | --- | --- |
| HAM-19: Boot JAR lacks React and direct-link routing | Fixed locally: Maven's `package` phase builds locked Vite dependencies and embeds its output in Boot static resources. Targeted MockMvc tests, artifact inspection, local HTTP checks, and desktop/mobile browser navigation exercised the **packaged JAR**, not Vite. | Recheck the built artifact and deep links in a deployment environment after that phase is separately authorized. |
| HAM-20: cross-origin executable icon script | Removed from `index.html`; free Font Awesome SVGs ship with the local bundle. Google Fonts stylesheets remain external; they are not executable JavaScript. | Decide whether to self-host fonts and set a strict CSP before production promotion. |
| HAM-21: content and first administrator | **Not resolved.** Spring's local note is demonstration data; the ASP.NET site has real published notes. Registration is off by default, and public first registration would grant admin. | Decide preservation vs explicitly approved fresh-content launch. Inventory the source notes/IDs/ownership and schema read-only; separately design mapping, dry run in an isolated Spring-owned namespace, backups/rollback and read-after-write validation. Import/provision a verified administrator **privately before traffic**; never open public registration to bootstrap it. Do not run Flyway against existing ASP.NET tables or `hamco_dev`. |
| HAM-22: production-mode safeguards | Spring checks a strong signing key and configured mail delivery when production mode is active; cookie `Secure` is tied to that mode. Local tests verify the branches, **not** the eventual deployment settings or inbox delivery. | Explicitly activate production mode, configure separate secrets, HTTPS base URL and real Mailjet delivery; prove startup fails on missing settings and a real verification/reset message arrives through the intended channel. Check cookies through the actual HTTPS proxy. No Railway inspection or changes have been made. |
| HAM-23: old ASP.NET API keys | Spring's pre-V3 *Spring-issued* fallback does not accept ASP.NET's BCrypt-of-raw-key hashes; preserving old keys is conditional. | Inventory active key owners and dependencies (without exporting raw keys). Either schedule owner-coordinated issuance/rotation and revoke old keys after switchover, **or** separately test a scoped BCrypt-of-raw-key compatibility verifier before importing anything. Never claim old keys work as-is. |

## Reproduce the local packaged-artifact checks

```bash
# Java 25, Node/npm and Docker are local prerequisites.
cd backend
./mvnw clean package
python3 scripts/check_jar_ui.py
# Start the resulting JAR on a free local port with the isolated
# hamco_spring_dev database, never with an ASP.NET or production database.
java -jar target/hamco-api-0.0.1-SNAPSHOT.jar --server.port=18081
# In another terminal, from frontend/: no Vite server is launched here.
HAMCO_PACKAGED_BASE_URL=http://127.0.0.1:18081 npx playwright test packaged.spec.ts
```

The Python check opens the actual JAR and checks `index.html`, linked JS/CSS, local imagery and absence of remote executable scripts. The browser test refreshes a direct note link, enters auth and admin page URLs, and checks that missing assets return 404 while protected/unknown APIs remain unauthorized. The full backend tests use disposable PostgreSQL and never require `hamco_spring_dev`.

**Decision boundary:** finish HAM-21/22/23 with explicit owner decisions and evidence before any production promotion. HAM-19 and HAM-20 make a local artifact safer; they do not migrate content, create a secure first admin, validate real mail, or deploy it.
