# What the agent changed, and why

The pull request adds 16 commits to `500158f` before this evidence package, which is in its own commits after them. Each commit has one purpose and a message that explains why. Every commit has a `Signed-off-by` line for the DCO check and a `Co-authored-by: factory-droid[bot]` line that marks the agent's work. Together, `500158f..c6b60ff` changes 38 files: 2,261 lines added and 31 removed.

| Area | Files | Commits |
|------|-------|---------|
| Dependency versions | `pom.xml` and `build.gradle`, one line each | `d74cc16` |
| Application code | `OwnerController.java` and `VetController.java` | `3a5a0bc`, `0e8563f` |
| Tests | 4 new test classes, 2 extended | `23d0c67`, `3a5a0bc`, `0e8563f` |
| CI and dependency automation | 3 existing workflows fixed; new OSV-Scanner, QA and wiki workflows; Dependabot | `c565970`, `1f28c2c`, `f643efa`, `7a27efc`, `b851d19` |
| Agent instructions and limits | `AGENTS.md`, `.factory/hooks.json`, `.factory/hooks/`, `.factory/settings.json`, `.factory/skills/qa*` | `f9bbb32`, `f643efa`, `7a27efc`, `2aff817`, `b851d19` |
| GitHub templates | A pull request template and two issue forms | `f9bbb32` |
| Documentation | `docs/DEPENDENCIES.md`, `docs/RUNBOOK.md`, `README.md` and the two database setup notes | `0d8c589` to `c6b60ff` |

## The fix

Commit `d74cc16`, at 03:32:40, 37 minutes after the request. It changes the Spring Boot version in both build files:

```diff
 pom.xml
-    <version>4.1.0</version>
+    <version>4.1.2</version>
 build.gradle
-  id 'org.springframework.boot' version '4.1.0'
+  id 'org.springframework.boot' version '4.1.2'
```

Spring Boot manages the versions of the six vulnerable libraries, so the upgrade changes all of them:

| Library | Spring Boot 4.1.0 | Spring Boot 4.1.2 | Advisories | Version that fixes all of them |
|---------|-------------------|-------------------|------------|--------------------------------|
| tomcat-embed-core | 11.0.22 | 11.0.27 | 7 | 11.0.25 |
| spring-webmvc | 7.0.8 | 7.0.10 | 2 | 7.0.9 |
| jackson-databind | 3.1.4 | 3.1.7 | 7 | 3.1.7 |
| jackson-core | 3.1.4 | 3.1.7 | 2 | 3.1.7 |
| postgresql | 42.7.11 | 42.7.14 | 1 | 42.7.12 |
| log4j-api | 2.25.4 | 2.25.5 | 1 | 2.25.5 |

Why this change:

- **Why 4.1.2 and not 4.1.1.** Spring Boot 4.1.1 manages Tomcat 11.0.24 and Jackson 3.1.5, which would leave 11 of the 20 advisories open. Spring Boot 4.1.2 is the first release whose managed versions fix all 20. Evidence: `raw/bom/boot-bom-versions.txt`, read from the published Spring Boot BOMs.
- **Why the Spring Boot version and not six version overrides.** Spring Boot releases its managed versions as a set, and the Maven and Gradle builds read the same set. Overrides would pin six libraries to versions that no Spring Boot release ships together, and each override would have to be removed later. `docs/RUNBOOK.md` describes this as the upgrade procedure.
- **Why it is low risk.** It stays on the 4.1.x patch line. All 102 resolved versions that change are patch-level. One runtime dependency is added: postgresql 42.7.14 brings `org.checkerframework:checker-qual` 3.55.1, so the resolved list grows from 171 to 172 artifacts. Evidence: `raw/deps-all-baseline.txt`, `raw/deps-all-remediated.txt`, `raw/deptree-baseline.txt`, `raw/deptree-remediated.txt`.
- **No application code change.** With only the version changed, `./mvnw verify` and `./gradlew build` passed with all 81 existing tests at 03:22 and 03:29, including the MySQL and PostgreSQL tests ([testing.md](testing.md#test-runs)).
- **How it stays fixed.** `PatchedDependencyVersionsTests` (commit `23d0c67`) fails the build if any of the six libraries resolves below its fixed version. Dependabot and the OSV-Scanner workflow (commit `1f28c2c`) report new advisories.
- **How to undo it.** `docs/RUNBOOK.md`, section "Roll back": revert the commit, and the guard test then fails on purpose. The upgrade changes no schema or data scripts, so the database needs no rollback.

Each advisory, with its severity and whether the application can reach it, is in `docs/DEPENDENCIES.md` under "Advisories fixed by the Spring Boot 4.1.2 upgrade". About half cannot be reached in the default configuration, but the others, including two critical Tomcat advisories, cannot be ruled out. Fixing all 20 with one patch-level upgrade was cheaper and safer than judging each one.

## Defects fixed with the owner's approval

The agent found two defects in the application while testing and documenting it. Fixing them was outside the approved plan, so it asked first. The owner approved both fixes, each in its own commit with tests (decisions 6 and 11 in [approval-record.md](approval-record.md)).

### Very large page numbers, `3a5a0bc`

`/owners` and `/vets.html` returned HTTP 500 for page numbers from 429,496,731 to 2,147,483,647. The controllers built a Spring Data `PageRequest` whose offset, (page - 1) × 5, no longer fits in an `int`, and Spring Data JPA rejects that query with "Page offset exceeds Integer.MAX_VALUE". The redirect to page 1 for out-of-range pages, which the upstream project added in `500158f`, never ran for these numbers.

The fix caps the page at `Integer.MAX_VALUE / pageSize` before the query is built. The out-of-range check still compares the requested page with the number of pages, so these requests now redirect to page 1 like any other page that does not exist:

```java
int validatedPage = Math.min(Math.max(page, 1), Integer.MAX_VALUE / pageSize);
```

### Unbounded vets page cache (F15), `0e8563f`

`/vets.html` ran its paged query before the range check. That query is `@Cacheable("vets")` and keyed by its `PageRequest`, and the Caffeine cache has no size limit and no expiry. Every distinct page number, valid or not, added a cache entry that was never removed, so any client could grow the heap until the application ran out of memory. The application has no authentication. In the jar built from `7a27efc`, 5,000 distinct page numbers left 5,000 cached pages after a full garbage collection.

The fix checks the page against the cached list of all vets first, and runs the paged query only for pages that exist. This is the approach the owner chose in decision 11. The cache now holds the list plus one entry per existing page. In the jar built from `407b5f3`, the same 5,000 requests leave 1 cached page. Because only existing pages reach the query, `VetController` no longer needs the cap from `3a5a0bc`; `OwnerController` keeps it.

## Readiness and CI

| Commit | What it changes | Why |
|--------|-----------------|-----|
| `c565970` | `deploy-and-test-cluster.yml` uses `actions/checkout@v4` instead of v2. The Maven and Gradle workflows use the `temurin` JDK instead of `adopt`, and the Gradle workflow no longer caches `~/.m2`. | v2 declares the retired Node 12 runtime. `adopt` is the frozen AdoptOpenJDK build, which `actions/setup-java` v6 removes, so the first Dependabot update of that action would break both builds. A Gradle build has no use for Maven's cache. |
| `1f28c2c` | Dependabot opens weekly update pull requests for Maven, Gradle and GitHub Actions. An OSV-Scanner workflow, using Google's reusable workflows v2.7.1, fails pull requests that add a known vulnerability and reports all known ones on pushes to `main` and weekly. | Nothing watched for new advisories. Code scanning upload is off, because private repositories need GitHub Advanced Security for it; findings still fail the job and show in its log. |
| `f9bbb32` | `AGENTS.md`; a project hook, `.factory/hooks/guard_release_actions.py`, that stops Droid from merging, approving, pushing to `main` or `master`, force-pushing, pushing tags, releasing, publishing images or artifacts and changing a cluster; its unit tests; a pull request template; issue forms for bugs and dependency vulnerabilities; Python bytecode ignored by Git. | Agents had no instructions, and nothing in the repository limited what an agent may do. The hook enforces the requirement that people merge, release and deploy. Its limits are in [not-completed.md](not-completed.md#limits-of-this-evidence). |
| `f643efa` | The QA skill (`.factory/skills/qa`, `qa-web`, `qa-api`), `.factory/settings.json` for the browser plugin, and `.github/workflows/qa.yml`, which runs the skill on pull requests and keeps one report comment up to date. `qa-results/` is ignored by Git and by the nohttp check in both builds. | The request asked for the QA skill. The answers to the QA questionnaire (decision 10) set its flows, the H2 database and the informational check. |
| `7a27efc` | `.github/workflows/droid-wiki-refresh.yml` runs `/wiki` after each push to `main` and keeps the pages in one rolling pull request. It does not upload the wiki. | The request asked for the wiki skill. Decision 9 set "no upload" and its own commit in this pull request. |
| `b851d19` | Comments and the no-key report text in `qa.yml`: they no longer suggest adding `FACTORY_API_KEY` as a Dependabot secret without a warning (F21). | The final review of the workflows found F21 and F22. They need an owner decision, so nothing else changed for them. |

## Documentation

| Commit | What it changes |
|--------|-----------------|
| `0d8c589` | Adds `docs/DEPENDENCIES.md` and `docs/RUNBOOK.md`. Fixes stale notes after checking each one against a running application: the README pointed to the H2 console, which returns 404 on Spring Boot 4, and named the wrong MySQL test class; the database setup notes used the old `docker-compose` command, started both databases and named MySQL 5.1. |
| `2aff817` | Records F15 to F20 (decision 12) and corrects earlier statements: what `I18nPropertiesSyncTest` checks, when `/vets` survives a database outage, that only the Maven build adds Git and build information, and the test count with Docker. Removes the Kubernetes database credentials that the documents repeated. |
| `b851d19` | Records F21 and F22. F8 now covers both Compose ports. `AGENTS.md` now says what the hook does not check. |
| `44ddc86` | Labels the CI services that had not run in this repository as Inferred instead of Confirmed. |
| `d120c42` | Adds the five open questions that no file in the repository answers. |
| `407b5f3` | Adds a blank line so that other Markdown renderers do not read a heading as a table row. |
| `c6b60ff` | Records what the GitHub settings showed for F13 and F22, and removes a heap figure for F15 that did not measure the cache ([inferred.md](inferred.md#corrections)). |

What the documents contain:

- `docs/DEPENDENCIES.md`: every runtime, deployment, build, CI and developer-environment dependency with a Confirmed, Inferred or Unknown label and its evidence; the resolved library versions; the 20 advisories with severity, fixed version and reachability; the open questions; and how to keep the file current.
- `docs/RUNBOOK.md`: build, run and health check commands; the failure behavior, each row with the test that verifies it; vulnerability scanning; the QA and wiki workflows and their secrets; the upgrade and rollback procedures; and follow-ups F1 to F22 with their evidence.
- `AGENTS.md`: commands, the checks that fail the build, which tests need Docker, how to make changes, and what agents must not do.

## All commits

| # | Commit | Time | Purpose | Files | Decision |
|---|--------|------|---------|-------|----------|
| 1 | `d74cc16` | 03:32:40 | Upgrade Spring Boot to 4.1.2 | 2 | 1 |
| 2 | `23d0c67` | 03:54:03 | Dependency version guard and failure-scenario tests | 4 | 1 |
| 3 | `3a5a0bc` | 03:57:17 | Redirect instead of failing for very large page numbers | 5 | 6 |
| 4 | `c565970` | 04:15:54 | Fix outdated CI workflow settings | 3 | 1 |
| 5 | `1f28c2c` | 04:15:54 | Dependabot and OSV-Scanner | 2 | 1 |
| 6 | `0d8c589` | 04:16:55 | Dependency documentation, runbook, stale database notes | 5 | 1, 3 |
| 7 | `f9bbb32` | 04:22:51 | Agent instructions, the hook, templates | 9 | 1 |
| 8 | `f643efa` | 13:45:35 | QA skill and informational QA check | 14 | 1, 10 |
| 9 | `7a27efc` | 13:48:07 | Wiki refresh workflow | 5 | 1, 9 |
| 10 | `0e8563f` | 14:48:30 | Check the vets page range before the cached query (F15) | 3 | 11 |
| 11 | `2aff817` | 14:54:21 | Record F15 to F20, correct notes | 4 | 12 |
| 12 | `b851d19` | 15:08:44 | Record F21 and F22 | 3 | 1 |
| 13 | `44ddc86` | 15:20:24 | Label CI services that have not run as Inferred | 1 | 1 |
| 14 | `d120c42` | 15:21:22 | List the open questions | 1 | 1 |
| 15 | `407b5f3` | 15:31:18 | Blank line after the open questions table | 1 | 1 |
| 16 | `c6b60ff` | 15:48:00 | GitHub settings for F13 and F22, F15 correction | 2 | 1 |

Decision 1 is the approved plan, which covered the upgrade, the tests, the documentation, the readiness fixes, the QA and wiki skills and the pull request. Commits 4 and 5 have the same time because they were made one after the other in the same step. The application code and tests were last changed in commit 10. The last full test run was at commit 15, `407b5f3`; commit 16 and this package change only Markdown files and the checksum list.

To see one commit with its full message: `git show <commit>`.
