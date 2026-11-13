# What was tested

All tests ran in the agent's sandbox (4 CPUs, OpenJDK 17.0.21, Docker 29.1.3, OSV-Scanner 2.7.1), except the checks that GitHub ran on the pull request ([GitHub checks on the pull request](#github-checks-on-the-pull-request)). Every sandbox run below has its logs and JUnit reports in the evidence archive.

| Measure | Before: `500158f`, Spring Boot 4.1.0 | After: `407b5f3` | Evidence |
|---------|--------------------------------------|------------------|----------|
| Tests in the Maven build, with Docker | 81 in 18 classes, 0 failures, 0 skipped | 116 in 22 classes, 0 failures, 0 skipped | `raw/baseline-docker/`, `raw/final-4/` |
| Tests in the Gradle build | Not run on `500158f`. 81 after the version change alone. | 116, 0 failures, 0 skipped | `raw/remediated-gradle-test-results/`, `raw/final-4/gradle-test-results/` |
| Known advisories, in each of three scans | 20 | 0 | [Vulnerability scans](#vulnerability-scans) |
| Guard test | 6 of 7 fail against Spring Boot 4.1.0, as intended | 7 of 7 pass | `raw/guard-check-4.1.0/`, `raw/final-4/` |
| Line coverage (JaCoCo, Maven) | 94.0 %, 313 of 333 lines | 94.5 %, 312 of 330 lines | `raw/baseline-docker/jacoco.csv`, `raw/final-4/jacoco.csv` |
| Branch coverage | 82.0 %, 100 of 122 branches | 84.4 %, 103 of 122 branches | Same |
| QA against the running application | Not applicable | 18 of 18 test cases pass | `raw/qa-run-1794584301/report.md` |
| GitHub checks on the pull request | Not applicable | The Maven and Gradle builds, OSV-Scanner and QA pass; QA 20 of 20 test cases | `raw/github/pr-checks-c85ae9e.txt` |

## Tests added

35 tests, from 81 to 116. The request asked for unit, integration and failure-scenario tests:

| Test class | Tests | Kind | What it checks |
|------------|-------|------|----------------|
| `PatchedDependencyVersionsTests` (new) | 7 | Unit | Each of the six libraries resolves at or above the version that fixes its advisories. It reads the versions from the jar manifests on the test classpath, so it checks what Maven or Gradle actually resolved. The seventh test checks the version comparison itself, for example that 11.0.3 is lower than 11.0.25. |
| `DatabaseUnavailableAtStartupTests` (new) | 2 | Failure scenario | With the `mysql` or the `postgres` profile and a database that cannot be reached, startup fails within seconds with the connection error instead of starting half-working. |
| `PostgresOutageIntegrationTests` (new) | 1 | Failure scenario, integration | Stops PostgreSQL under a running application. Pages that need the database show the generic error page without internal details, `/vets` keeps working if it was cached, `/actuator/health` returns 503, and the application recovers without a restart when the database is back. Uses Testcontainers. |
| `UntrustedInputIntegrationTests` (new) | 22 | Integration, failure scenario | Through Tomcat, Spring MVC, Spring Data JPA and the H2 database: 9 out-of-range page numbers, up to 2,147,483,647, redirect to page 1 and keep the search; 6 malformed numbers return HTTP 400; 5 unknown ids show the error page; no response contains a stack trace, exception text or a JDBC URL; JSON errors omit the message; 200 out-of-range `/vets.html` pages leave no cache entry. |
| `OwnerControllerTests` | 18 → 19 | Unit | A huge page number builds a query whose offset fits in an `int`. |
| `VetControllerTests` | 3 → 5 | Unit | Out-of-range pages, including 429,496,731 and 2,147,483,647, redirect without running the paged query, and page 1 queries `PageRequest.of(0, 5)`. |

The other 16 test classes are unchanged and pass.

## Tests that failed before each fix

A test proves a fix only if it fails without it. The agent ran each new test against the code before the fix:

| Fix | Before the fix | After the fix | Evidence |
|-----|----------------|---------------|----------|
| Very large page numbers, `3a5a0bc` | 44 tests, 7 failures: 5 in `UntrustedInputIntegrationTests`, where the response was HTTP 500 instead of a redirect, and 1 each in `OwnerControllerTests` and `VetControllerTests`, where the query offset was 10,737,418,230, above the `int` limit of 2,147,483,647 | 44 tests, 0 failures | `raw/tdd-red-overflow.log` (03:54), `raw/tdd-green-overflow.log` (03:55) |
| Vets page cache, `0e8563f` | 27 tests, 2 failures: `outOfRangeVetPagesAreNotCached` found a cache entry for page 3, and `showVetListRedirectsOutOfRangePagesWithoutQueryingThem` found the paged query | 30 tests, 0 failures | `raw/tdd-red-f15.log` (14:47), `raw/tdd-green-f15.log` (14:48) |
| Spring Boot 4.1.2, `d74cc16` | The guard test fails against Spring Boot 4.1.0, below | Passes | `raw/guard-check-4.1.0/`, `raw/final-4/` |

The agent found the page number defect at 03:33, before writing tests for it, by sending untrusted input to the running application (`raw/overflow-discovery/`). The first run of the new tests at 03:47 also failed on it ([Failures during the work](#failures-during-the-work)).

## The guard test against the vulnerable version

At 15:24, the agent checked out `500158f` in a separate worktree, copied `PatchedDependencyVersionsTests` into it and ran `./mvnw -B test -Dtest=PatchedDependencyVersionsTests` against Spring Boot 4.1.0. 7 tests ran and 6 failed, one for each vulnerable library, with messages such as "tomcat-embed-core resolved to 11.0.22; 11.0.25 is the first version without the known advisories". The seventh, the version comparison test, passed. Maven exited with 1. Evidence: `raw/guard-check-4.1.0/mvn-test.log` and `env.txt`; the script is `scripts/guard-check-4.1.0.sh`.

## Vulnerability scans

| Scan | Spring Boot 4.1.0 | Spring Boot 4.1.2 | Evidence |
|------|-------------------|-------------------|----------|
| OSV-Scanner on the CycloneDX SBOM of the jar | 20 advisories in 6 packages | No issues found | `raw/osv-scanner-sbom-baseline.json`, `raw/osv-scanner-sbom-remediated.json`, `raw/final-4/osv-sbom.txt` |
| OSV-Scanner on `pom.xml`, with transitive resolution | 20 advisories in 6 packages | No issues found | `raw/osv-scanner-pom-baseline.json`, `raw/osv-scanner-pom-remediated.json`, `raw/final-4/osv-pom.txt` |
| OSV.dev API, queried for each of the 171 or 172 resolved artifacts | 20 advisories | 0 | `raw/osv-api-baseline.json`, `raw/osv-api-remediated.json` |

The running application reports the fixed versions in its own SBOM endpoint, `/actuator/sbom/application` (QA run 2, test case 8).

## Test runs

| Time | Code | What ran | Result | Evidence |
|------|------|----------|--------|----------|
| 03:02 | `500158f` | `./mvnw verify`, before Docker was installed | 79 tests, 0 failures, 2 skipped (`MySqlIntegrationTests`). The 2 `PostgresIntegrationTests` tests were not counted at all. 29 s. | `raw/baseline-mvn-verify.log`, `raw/baseline-surefire/` |
| 03:22 | `500158f` with Spring Boot 4.1.2, committed as `d74cc16` | `./mvnw verify` with Docker | 81 tests, 0 failures, 0 skipped | `raw/remediated-mvn-verify.log`, `raw/remediated-surefire/` |
| 03:29 | Same | `./gradlew build` | 81 tests, 0 failures, 0 skipped | `raw/remediated-gradle-build.log`, `raw/remediated-gradle-test-results/` |
| 03:47 to 14:48 | Working tree | The red and green runs above | As above | `raw/tdd-*.log` |
| 03:51 to 03:53 | `d74cc16` with the new tests, before they were committed as `23d0c67` | The 4 new test classes, then `./mvnw verify` with Docker | 26 tests, then 107 tests in 22 classes, 0 failures, 0 skipped | `raw/commit-checks/before-23d0c67-new-tests.log`, `raw/commit-checks/before-23d0c67-mvn-verify.log` |
| 03:56 to 03:57 | `23d0c67` with the page number fix, before it was committed as `3a5a0bc` | `./mvnw verify` with Docker | 114 tests in 22 classes, 0 failures, 0 skipped | `raw/commit-checks/before-3a5a0bc-mvn-verify.log` |
| 13:48 to 13:51 | `7a27efc` | Maven, both scans, hook tests, Gradle | 114 tests in each build, 0 failures. No issues found. 6 hook tests pass. | `raw/final/` |
| 14:54 to 14:57 | `2aff817` | Same | 116 tests in each build, 0 failures | `raw/final-2/` |
| 15:08 to 15:11 | `b851d19` | Same | 116 tests in each build, 0 failures | `raw/final-3/` |
| 15:24 | `500158f` with the guard test | Guard test only | 7 tests, 6 failures, as intended | `raw/guard-check-4.1.0/` |
| 15:31 to 15:33 | `407b5f3` | Maven, both scans, hook tests, Gradle | Maven: 116 tests, 0 failures, 0 skipped, 1 min 1 s. Gradle: 116 tests, 0 failures, 0 skipped. No issues found. 6 hook tests pass. | `raw/final-4/` |
| 15:34 to 15:35 | `500158f` | `./mvnw verify` with Docker, in a fresh clone, for a like-for-like baseline | 81 tests, 0 failures, 0 skipped, 50 s | `raw/baseline-docker/` |

Each `final*` run was made by a script with no uncommitted changes to tracked files; `env.txt` in each directory records the commit, the tool versions, the exit codes and the times. The scripts are `scripts/final-tests*.sh` and `scripts/baseline-docker.sh`.

## QA against the running application

The QA skill built the branch, started it with the in-memory H2 database and tested it like a user: pages through a real Chrome browser, the JSON and Actuator endpoints with curl. It ran non-interactively in `droid exec`, with the same prompt that `.github/workflows/qa.yml` uses.

| Run | Code | Result | Evidence |
|-----|------|--------|----------|
| QA run 1, 13:34 to 13:40 | `f9bbb32` with the QA skill, before it was committed | 16 of 16 test cases pass | `raw/qa-run-1794576862/report.md` and screenshots |
| QA run 2, 15:38 to 15:43 | `407b5f3` | 18 of 18 test cases pass | `raw/qa-run-1794584301/report.md` and screenshots |
| QA in GitHub Actions, 16:32 to 16:41 | `204566f`, the merge commit that GitHub built for the pull request at `c85ae9e` | 20 of 20 test cases pass | `raw/github/qa-ci-run-43463692800/qa-results/report.md` and screenshots |

QA run 2 covered the changed code first: owner and vets paging with huge, negative and malformed page numbers, 200 distinct out-of-range vets pages, the library versions in the SBOM endpoint, and the JSON, XML and Actuator endpoints. It then covered the pages that the change could affect: the home page, owner search, adding and editing owners, pets and visits with validation, the error pages and the language switch. Run 1 reported two problems with the browser tool, a navigation race and date inputs, and suggested skill updates for them; both are now in the Known Failure Modes of `.factory/skills/qa-web/SKILL.md`.

## Probes against the built jar

`scripts/probe_findings.py` started the jar on H2 and on PostgreSQL and checked the findings made while documenting the code:

| Finding | Jar from `7a27efc`, 14:13 | Jar from `407b5f3`, 15:38 |
|---------|---------------------------|----------------------------|
| F15: cached pages after 5,000 distinct page numbers and a full garbage collection | 5,000 `PageImpl` objects | 1, for page 2 |
| F16: page 2 of a search that found six owners | Links to `/owners?page=2`, which lists owners 6 to 10 of all owners | Same; still open |
| F18: search for `davis` | 2 owners on H2, none on PostgreSQL | Same; still open |
| F20: error message for an invalid new owner | Never shown | Same; still open |

The probe also checked that the generated database password appears in none of its output files (`secret-check.txt`). Evidence: `raw/findings-probe/`, `raw/findings-probe-final/`.

## Workflows and the hook

| Check | Result | Evidence |
|-------|--------|----------|
| actionlint 1.7.12 on all workflows | Exit 0 | `raw/lint/workflow-lint.txt` (16:01, `c6b60ff`) |
| GitHub's JSON schemas for workflows, Dependabot and issue forms (check-jsonschema) | All valid | Same |
| The report comment steps of `qa.yml` in a local harness | 25 of 25 cases pass | `scripts/qa_post_step_test.py`, `scripts/qa_post_step_test.out` |
| The upstream Maven and Gradle workflows on `main` of `factory-aj-pahl/spring-petclinic`, after the seed push | Both passed | `raw/github/runs-after-seed.txt` |
| Hook unit tests | 6 pass | `raw/final-4/hook-unittest.log` |
| The hook in a new `droid exec` session | Let `echo hook-check-allowed` run and stopped a command that contained `gh pr merge` | `raw/hook-e2e-droid-exec.jsonl` |
| The hook in the main session | Stopped an Execute call whose text contained a merge command at 04:21:48 | `raw/hook-live-block-main-session.txt` |

The QA workflow ran on the pull request ([GitHub checks on the pull request](#github-checks-on-the-pull-request)). The wiki workflow runs only after a push to `main`, so it has not run ([not-completed.md](not-completed.md)).

## GitHub checks on the pull request

GitHub ran these checks after the pull request was opened. For pull request events, GitHub builds the merge commit `204566f`: the head `c85ae9e` merged into `main` at `500158f`.

| Check | Workflow | Result | Time |
|-------|----------|--------|------|
| `build (17)` | Java CI with Maven | Pass | 16:32:19 to 16:35:12 |
| `build (17)` | Java CI with Gradle | Pass | 16:32:20 to 16:35:20 |
| `scan-pr / osv-scan` | OSV-Scanner | Pass: the pull request adds no known vulnerability | 16:32:19 to 16:33:31 |
| `scan-full` | OSV-Scanner | Skipped, as intended: it runs on pushes to `main` and every week | |
| `qa` | QA | Pass: 20 of 20 test cases. Informational; it does not block merging. | 16:32:19 to 16:41:38 |

The QA check ran the QA skill with Droid on the GitHub runner, so the `FACTORY_API_KEY` secret is set; without it, the job skips QA and only reports that QA did not run. It built the merge commit, started it with the H2 database and tested 20 cases: the owner and vets page numbers around the new cap, the vets cache after 500 out-of-range requests, the library versions in the SBOM endpoint, the JSON, XML and Actuator endpoints, and the pages that the change could affect. It posted its report as a comment on the pull request. The report's Action Required section says that the `droid-control` plugin, which `.factory/settings.json` enables, is not installed on the runner, so the run drove the browser with `agent-browser` directly. Video evidence is off, so no evidence was lost. Whether `qa.yml` should install the plugin is for the reviewer to decide; nothing was changed for it.

Evidence: `raw/github/pr-checks-c85ae9e.txt` (the checks, the workflow runs and the comments on the pull request), `raw/github/qa-ci-job.json` (the steps of the QA job), `raw/github/qa-ci-comment.md` (the report comment), and `raw/github/qa-ci-run-43463692800/` (the artifact of the workflow run: the report, screenshots, the application log and the Droid output).

## Failures during the work

None of the final runs failed. These failures happened along the way; each was understood and dealt with:

| Time | What failed | Cause | What was done |
|------|-------------|-------|---------------|
| 03:33 | `/owners` and `/vets.html` returned HTTP 500 for huge page numbers. | A defect in the application | Put to the owner (decision 6), fixed in `3a5a0bc` with tests |
| 03:47 | 8 of 53 new tests failed. 7 were the page number defect. The eighth, `DatabaseUnavailableAtStartupTests` with the `mysql` profile, expected startup to fail, but it succeeded. | `MysqlTestApplication` replaces the configured MySQL connection with a Testcontainers database whenever the `mysql` profile is active in a test (F10). | The test excludes that class. F10 is a follow-up. |
| 04:16 | The nohttp check failed on a file the agent had created for this purpose. | Intended: it showed that the check also scans Markdown in `docs/`. | File deleted |
| 04:17 | 2 of 6 hook unit tests failed. The first version of the hook let a plain `git push` from `main` and `gh api --method DELETE` on a branch protection through. | Gaps in the hook | Fixed before `f9bbb32`; all 6 pass |
| 13:33 | The nohttp check failed on a file the agent had created for this purpose. | Intended: it showed that the new `qa-results/` exclusion does not switch the check off. | File deleted |
| 13:34 to 13:40 | QA run 1 hit a navigation race and date inputs in the browser tool. | Tool behavior | Worked around in the run; added to the skill's Known Failure Modes |
| 13:47 | The test harness for the QA report steps did not start. | It ran with the system Python, which lacks `ruamel.yaml`. | Rerun with the right interpreter: 25 of 25 pass |
| 14:50 | An attempt to show what the Docker-based tests do without Docker was invalid. | `DOCKER_HOST` pointed at a missing socket, but Testcontainers found the real one through `~/.testcontainers.properties`, so the tests ran with Docker. | The log is kept as `raw/no-docker-simulation-invalid.log`. The claim that `PostgresOutageIntegrationTests` is skipped without Docker stays Inferred. |
| 14:13 to 15:38 | A heap measurement for F15 did not measure the cache. | The heap grew by 3.4 MB before the fix and by 2.5 MB after it. | The figure was removed; the runbook counts cached pages instead ([inferred.md](inferred.md#corrections)). |
