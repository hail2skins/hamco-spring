# HAM Jira ticket map

These tickets implement the [ASP.NET → Spring/React parity plan](HAMCO_SPRING_PARITY_PLAN.md). All tasks are children of the [HAM-1 epic](https://hamcois.atlassian.net/browse/HAM-1). The setup-only tickets were completed and marked Done; all implementation tickets remain To Do. Jira descriptions include the scope, acceptance criteria, plan step, and strict TDD expectation. **No feature implementation or Railway work has started.**

| Plan step | Ticket | Slice | Current status / boundary |
| --- | --- | --- | --- |
| Epic | [HAM-1](https://hamcois.atlassian.net/browse/HAM-1) | Spring Boot + React parity rewrite (local first) | To Do; parent for every task |
| 2 — setup | [HAM-2](https://hamcois.atlassian.net/browse/HAM-2) | Install/select pinned Java 25 and Maven | **Done** — shell verification and README |
| 2 — setup | [HAM-3](https://hamcois.atlassian.net/browse/HAM-3) | Provision isolated `hamco_spring_dev` | **Done** — empty DB; no migrations |
| 2 — setup | [HAM-4](https://hamcois.atlassian.net/browse/HAM-4) | Verify Node, Docker and setup docs | **Done** — disposable Docker container passed |
| 1 | [HAM-5](https://hamcois.atlassian.net/browse/HAM-5) | Capture read-only ASP.NET runtime parity baseline | To Do; no production writes |
| 3 | [HAM-6](https://hamcois.atlassian.net/browse/HAM-6) | Scaffold Spring Boot and React/TypeScript repositories | To Do; first code slice |
| 4 | [HAM-7](https://hamcois.atlassian.net/browse/HAM-7) | Flyway PostgreSQL baseline and integration tests | To Do |
| 5 | [HAM-8](https://hamcois.atlassian.net/browse/HAM-8) | Registration gate, validation, first-admin behavior | To Do |
| 6 | [HAM-9](https://hamcois.atlassian.net/browse/HAM-9) | BCrypt, JWT, cookies and authorization | To Do |
| 7 | [HAM-10](https://hamcois.atlassian.net/browse/HAM-10) | Verification, reset and email | To Do |
| 8 | [HAM-11](https://hamcois.atlassian.net/browse/HAM-11) | Public notes API and admin CRUD | To Do |
| 9 | [HAM-12](https://hamcois.atlassian.net/browse/HAM-12) | Public React layout and pages | To Do |
| 10 | [HAM-13](https://hamcois.atlassian.net/browse/HAM-13) | Slogans API and admin UI | To Do |
| 11 | [HAM-14](https://hamcois.atlassian.net/browse/HAM-14) | Admin notes UI | To Do |
| 12 | [HAM-15](https://hamcois.atlassian.net/browse/HAM-15) | API keys API/auth/admin UI | To Do |
| 13 | [HAM-16](https://hamcois.atlassian.net/browse/HAM-16) | Auth React pages and full local journeys | To Do |
| 14 | [HAM-17](https://hamcois.atlassian.net/browse/HAM-17) | Railway dev with existing PostgreSQL | **Deferred** until Art authorizes Railway phase |
| 15 | [HAM-18](https://hamcois.atlassian.net/browse/HAM-18) | Railway production and parity gate | **Deferred** until Art authorizes production phase |

**Working rule:** keep code changes mapped to the relevant HAM ticket and include its actual key in each commit message. Start with local parity, not Railway. Do not push without Art's explicit instruction. If a planned slice proves too large, split it into smaller child tasks before implementation rather than turning one ticket into a catch-all.
