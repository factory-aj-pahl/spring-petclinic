# What the agent observed

This page lists what the agent read, ran or measured itself. Conclusions drawn from these observations are in [inferred.md](inferred.md). Paths that start with `raw/` are in the evidence archive ([raw-evidence.md](raw-evidence.md)).

## Readiness

The state of upstream commit `500158f` when the work started:

| Area | Observed | Evidence |
|------|----------|----------|
| Build and test | One command, `./mvnw verify`, checks formatting and links, runs the tests and packages the jar. Before Docker was installed, it passed in 29 seconds: 79 tests, 0 failures, 2 skipped. The 2 skipped tests were `MySqlIntegrationTests`; the 2 `PostgresIntegrationTests` tests did not appear in the count at all. | `raw/baseline-mvn-verify.log`, `raw/baseline-surefire/` (03:02) |
| The same commit with Docker | 81 tests in 18 classes, 0 failures, 0 skipped. Line coverage 94.0 % (313 of 333 lines), branch coverage 82.0 % (100 of 122 branches). | `raw/baseline-docker/` (15:34, run for a like-for-like comparison) |
| Agent instructions | No `AGENTS.md` and no `.factory/` configuration | `git ls-tree -r --name-only 500158f` |
| Dependency updates and scanning | No Dependabot configuration and no vulnerability scan in CI | Same |
| Documentation | No runbook and no dependency documentation. The README pointed to the H2 console, which returns 404 on Spring Boot 4 unless the `spring-boot-h2console` module is added. The database setup notes used the old `docker-compose` command and MySQL 5.1. | README and `src/main/resources/db/*/petclinic_db_setup_*.txt` at `500158f`, live run |
| Review controls | No CODEOWNERS file. Upstream `main` reports `protected: false` and has no rulesets. | `raw/github/upstream-settings.txt` |
| CI | `deploy-and-test-cluster.yml` used `actions/checkout@v2`, which declares the retired Node 12 runtime. The Maven and Gradle workflows used the frozen `adopt` JDK distribution, and the Gradle workflow cached Maven's `~/.m2`. | `.github/workflows/` at `500158f` |
| Templates | `.github/dco.yml` asks for a DCO sign-off. There were no pull request or issue templates. | `git ls-tree -r --name-only 500158f` |

From these observations, the agent estimated Level 1 of 5 on Factory's Agent Readiness model, held back from Level 2 by the missing agent instructions, dependency automation, documentation and review controls. This is the agent's own estimate; the official readiness report was not run.

What the pull request changes for readiness:

| Gap | After this pull request |
|-----|-------------------------|
| No agent instructions | `AGENTS.md`: commands, checks that fail the build, tests that need Docker, and what agents must not do |
| No dependency updates or scanning | Dependabot for Maven, Gradle and GitHub Actions, OSV-Scanner on every pull request, and `PatchedDependencyVersionsTests` in both builds |
| No runbook or dependency documentation | `docs/RUNBOOK.md`, `docs/DEPENDENCIES.md`, corrected README and setup notes |
| No limits on what an agent may do | `.factory/hooks.json` and `.factory/hooks/guard_release_actions.py` |
| No pull request or issue templates | A pull request template, and issue forms for bugs and dependency vulnerabilities |
| Outdated CI settings | Fixed in `c565970` |
| No QA automation | The QA skill (`.factory/skills/qa*`) and the informational `.github/workflows/qa.yml` |
| No generated code documentation | `.github/workflows/droid-wiki-refresh.yml`. The wiki itself stays out of the repository (decision 9). |
| No branch protection or CODEOWNERS | Unchanged. It needs an owner and, for this private repository, a paid GitHub plan (F13). |
| Docker-based tests skipped without notice | Unchanged and documented (F9) |

## Dependencies

The agent read these sources:

- `pom.xml`, `build.gradle`, the resolved dependency list and tree (171 artifacts on Spring Boot 4.1.0, 172 on 4.1.2), and the CycloneDX SBOM of the jar
- `src/main/resources/application*.properties` and the SQL scripts in `src/main/resources/db/`
- `docker-compose.yml`, `k8s/db.yml` and `k8s/petclinic.yml`
- `.github/workflows/*.yml`, `.github/dco.yml`, `.devcontainer/`, `.gitpod.yml` and `src/test/jmeter/petclinic_test_plan.jmx`
- The Java code, for outbound calls, caching, Actuator use and service bindings

It confirmed these by running them:

| Dependency or behavior | How it was exercised |
|------------------------|----------------------|
| H2, MySQL 9.7 and PostgreSQL 18.4 through their profiles | The test suite, with Testcontainers and Docker Compose, and the probe runs on H2 and PostgreSQL |
| Caffeine as the cache manager | `/actuator/caches` in a live run |
| Every Actuator endpoint exposed without authentication | Live run (F1) |
| `/livez` and `/readyz` exist only with the property that `k8s/petclinic.yml` sets | Live run |
| Readiness stays UP during a database outage | `PostgresOutageIntegrationTests` (F2) |
| `docker compose up mysql` creates an empty, root-owned `conf.d` directory | Reproduced (F7) |
| No outbound network calls | Code search: `src/main` has no HTTP client, mail or messaging code |

The resulting inventory is `docs/DEPENDENCIES.md`. [inferred.md](inferred.md) explains its labels.

## Vulnerabilities

| Observation | Evidence |
|-------------|----------|
| On Spring Boot 4.1.0, three scans report the same 20 advisories in six packages: 7 critical, 7 high and 6 moderate. The scans are OSV-Scanner on the SBOM, OSV-Scanner on `pom.xml`, and a direct OSV.dev API query for all 171 resolved artifacts. | `raw/osv-scanner-sbom-baseline.json`, `raw/osv-scanner-pom-baseline.json`, `raw/osv-api-baseline.json` |
| Spring Boot 4.1.1 manages Tomcat 11.0.24 and Jackson 3.1.5, which leaves 11 of the 20 advisories open. Spring Boot 4.1.2 manages Tomcat 11.0.27, Spring Framework 7.0.10, Jackson 3.1.7, PostgreSQL driver 42.7.14 and Log4j 2.25.5, each at or above the first fixed version. | `raw/bom/boot-bom-versions.txt` |
| On Spring Boot 4.1.2, all three scans report 0. | `raw/osv-scanner-sbom-remediated.json`, `raw/osv-scanner-pom-remediated.json`, `raw/osv-api-remediated.json`, `raw/final-4/osv-pom.txt`, `raw/final-4/osv-sbom.txt` |
| The running application reports the patched versions in its SBOM endpoint. | QA run 2, test case 8 (`raw/qa-run-1794584301/report.md`) |

The advisories, with severity, first fixed version and reachability, are in `docs/DEPENDENCIES.md` under "Advisories fixed by the Spring Boot 4.1.2 upgrade".

## Defects found in the application

| Defect | Observed | Evidence |
|--------|----------|----------|
| Very large page numbers | `/owners` and `/vets.html` returned HTTP 500 for page numbers from 429,496,731 to 2,147,483,647, because the query offset, (page - 1) × 5, no longer fits in an `int`. The agent found it at 03:33 by sending untrusted input to the running application, and the new tests failed on it at 03:47. Fixed in `3a5a0bc` (decision 6). | `raw/overflow-discovery/`, `raw/tdd-before-overflow-fix.log`, `raw/tdd-red-overflow.log` |
| F15, unbounded vets cache | 5,000 distinct page numbers on `/vets.html` left 5,000 cached pages (`PageImpl` objects after a full garbage collection) in the jar built from `7a27efc`. In the jar built from `407b5f3`, after the fix, they leave 1, for page 2, the only one of those pages that exists. Fixed in `0e8563f` (decision 11). | `raw/findings-probe/h2-probe.json`, `raw/findings-probe-final/h2-probe.json` |
| F16 | A search that found six owners linked its second page as `/owners?page=2`, which showed owners 6 to 10 of all owners. Still open. | `raw/findings-probe-final/h2-probe.json` |
| F18 | A search for `davis` finds two owners on H2 and none on PostgreSQL. Still open. | `raw/findings-probe-final/h2-probe.json`, `raw/findings-probe-final/pg-probe.json` |
| F20 | The error message for an invalid new owner form never shows. Still open. | `raw/findings-probe-final/h2-probe.json` |

The other follow-ups are in `docs/RUNBOOK.md`, each with its evidence.

## GitHub

| Observation | Evidence |
|-------------|----------|
| The `factory-aj-pahl` account can only read `spring-projects/spring-petclinic`, so the pull request could not go there. | Session, prompt 1 ([approval-record.md](approval-record.md), decision 2) |
| The Factory Droid GitHub App token cannot create repositories: HTTP 403 for `createRepository`. | Session, prompt 2 (decision 5) |
| At 13:12:52, `factory-aj-pahl/spring-petclinic` was private and empty, and the account had admin access. | Session ([approval-record.md](approval-record.md)) |
| After the one-time seed push, `main` is `500158f` and reports `protected: false`. GitHub refuses rulesets: "Upgrade to GitHub Pro or make this repository public to enable this feature." The branch protection, Actions permissions and secrets endpoints answer HTTP 403, "Resource not accessible by integration". | `raw/seed-main/push.log`, `raw/seed-main/repo-settings.txt` |
| The seed push started the upstream Maven and Gradle workflows on `main`. Both passed, so GitHub Actions runs in this repository. | `raw/github/runs-after-seed.txt` |

## Policy enforcement

| Observation | Evidence |
|-------------|----------|
| In a new `droid exec` session, the hook let `echo hook-check-allowed` run and stopped a command that contained `gh pr merge` before it ran. | `raw/hook-e2e-droid-exec.jsonl` |
| In the main session at 04:21:48, the hook stopped an Execute call whose text contained a merge command, so nothing ran. | `raw/hook-live-block-main-session.txt`, `raw/session/session-evidence.txt` |
| The hook's unit tests check that it blocks merging and approving pull requests, deleting branch protection, force pushes, pushes of tags or to `main` or `master` (including a plain `git push` from `main`), releases, image and artifact publishing, and cluster changes, and that it allows everyday commands such as pushing a feature branch, `gh pr create`, `./mvnw verify` and `kubectl get pods`. | `.factory/hooks/test_guard_release_actions.py`, `raw/final-4/hook-unittest.log` |
| Every decision in the approval record was asked with a question prompt and answered before the agent acted on it. | `raw/session/session-evidence.txt` |
