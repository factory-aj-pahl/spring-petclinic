# Evidence package

This package records what Factory Droid observed, inferred, changed and tested while it fixed the known dependency vulnerabilities in Spring PetClinic, what it could not complete, and what it submitted for approval. A reviewer should be able to decide on the pull request from this package without rerunning the session. Each statement names its evidence. Paths that start with `raw/` or `scripts/` refer to the evidence archive that accompanies this package. [raw-evidence.md](raw-evidence.md) describes its files, and `raw-evidence.sha256` holds their SHA-256 checksums.

| Item | Value |
|------|-------|
| Repository | `factory-aj-pahl/spring-petclinic`, private. Its `main` branch is the unchanged upstream commit `500158f` of `spring-projects/spring-petclinic`. |
| Branch | `security/spring-boot-4.1.2-cve-remediation` |
| Agent | Factory Droid, session `5f856a0b-6321-404e-bcb7-7b7183376551` |
| Approver | The session user, GitHub account `factory-aj-pahl`. Every decision is in [approval-record.md](approval-record.md). |
| Date | 2026-11-13. All times are UTC. |

## Summary

| Question | Answer | Details |
|----------|--------|---------|
| What was wrong? | Spring Boot 4.1.0 brought in six libraries with 20 published advisories: 7 critical, 7 high and 6 moderate, in Tomcat, Spring MVC, Jackson, the PostgreSQL driver and Log4j API. The repository had no dependency documentation, no runbook and no failure-scenario tests. | [observed.md](observed.md) |
| What did the agent discover? | 35 dependencies, labeled Confirmed (23), Inferred (11) or Unknown (1), and 5 questions that only the owner can answer. 22 follow-ups, F1 to F22. Two defects in the application: page numbers from 429,496,731 up returned HTTP 500, and `/vets.html` cached every requested page number without a limit (F15). | [observed.md](observed.md), [inferred.md](inferred.md) |
| What did it change, and why? | It upgraded Spring Boot to 4.1.2, the first release that fixes all 20 advisories. It added a test that fails the build if a vulnerable version comes back, failure-scenario tests, dependency documentation, a runbook, and the readiness fixes listed in [observed.md](observed.md#readiness). With the owner's approval, it fixed the two defects in separate commits. | [changes.md](changes.md) |
| What was tested? | Maven and Gradle each ran 116 tests with Docker, against 81 before the change, with 0 failures and 0 skipped. Three vulnerability scans went from 20 findings to 0. The new guard test fails against Spring Boot 4.1.0. A QA run tested 18 cases against the running application, in a browser and with curl. On the pull request, GitHub's Maven and Gradle builds, OSV-Scanner and the QA check passed; QA tested 20 cases. | [testing.md](testing.md) |
| What failed? | Nothing in the final runs. Earlier, the new tests failed against the old code as intended, and problems in the agent's own work, such as two cases that the first version of the hook missed, were found and fixed. | [testing.md](testing.md#failures-during-the-work) |
| What remains uncertain? | Whether 10 of the 20 advisories could be reached. Who builds the image that Kubernetes runs, and how production is set up. Whether the wiki workflow works in GitHub Actions: it runs only after a push to `main`, and the agent cannot check its secret. | [inferred.md](inferred.md), [not-completed.md](not-completed.md) |
| Is it ready for approval? | In the agent's assessment, the dependency fix and its tests are ready for review, and GitHub's checks on the pull request pass. The agent cannot approve its own work, and a person must merge. The decisions left to the reviewer are below. | [Decisions for the reviewer](#decisions-for-the-reviewer) |

## Decisions for the reviewer

1. Review the pull request, then merge it or ask for changes. The pull request was opened through the `factory-aj-pahl` account, and GitHub does not let authors approve their own pull requests, so an approving review has to come from another collaborator. No rule on `main` requires that review yet (F13).
2. Check that the `FACTORY_WIKI_PR_TOKEN` secret is set before the first push to `main`; without it, the build checks do not run on the wiki refresh pull request. `FACTORY_API_KEY` is set, because the QA check ran on this pull request. Decide whether `qa.yml` should install the `droid-control` plugin, as the QA report asks ([testing.md](testing.md#github-checks-on-the-pull-request)).
3. Decide F21 (whether Dependabot pull requests get `FACTORY_API_KEY`) and F22 (add `permissions: contents: read` to three workflows). Neither was put to the approver during the session.
4. Protect `main` and add a CODEOWNERS file (F13). For this private repository, that needs a paid GitHub plan.
5. Rebuild and redeploy the Kubernetes image if Kubernetes matters (F4). Nothing was deployed.
6. Keep `docs/evidence/` on `main`, or leave its commits out when merging. The application does not need it.

The other follow-ups (F1 to F12, F14 and F16 to F20) are in `docs/RUNBOOK.md`. Each needs an owner decision, and none of them blocks this fix.

## The requested workflow

| Step | What the agent did | Result | Details |
|------|--------------------|--------|---------|
| 1. Assess readiness | Built and tested the project, and read its documentation, CI and GitHub settings. | Partly ready. One command builds and tests everything, but agent instructions, dependency automation, a runbook and review controls were missing. The agent estimated Level 1 of 5 on Factory's Agent Readiness model; the official readiness report was not run. | [observed.md](observed.md#readiness) |
| 2. Identify dependencies | Read the build files, the 171 resolved artifacts, the configuration, SQL scripts, Docker Compose file, Kubernetes manifests, workflows and development environments, and ran the application against each database. | `docs/DEPENDENCIES.md` | [observed.md](observed.md#dependencies) |
| 3. Confirmed, inferred or unknown | Labeled every dependency. Confirmed needs a test or a live run, not only a file that mentions the dependency. | 23 Confirmed, 11 Inferred, 1 Unknown, and 5 open questions. Three CI entries became Confirmed when GitHub's checks ran them. | [inferred.md](inferred.md) |
| 4. Plan for approval | Presented the findings and the plan at 03:06:30 and waited. | Approved at 03:08:53. In total, 12 decisions were asked and answered before the agent acted on them. | [approval-record.md](approval-record.md) |
| 5. Dependency documentation and runbook | Wrote `docs/DEPENDENCIES.md` and `docs/RUNBOOK.md`, and corrected stale notes in the README and the database setup files after checking each one against a running application. | Every failure behavior in the runbook names the test that verifies it. | [changes.md](changes.md#documentation) |
| 6. Remediate the vulnerability | Upgraded Spring Boot from 4.1.0 to 4.1.2 in `pom.xml` and `build.gradle`. | Commit `d74cc16`, 37 minutes after the request. 20 findings to 0. The upgrade needed no application code change. | [changes.md](changes.md#the-fix) |
| 7. Tests | Added a dependency version guard and tests for an unreachable database at startup, a database outage while the application runs, and untrusted input, and extended two controller test classes. | 35 more tests, from 81 to 116 | [testing.md](testing.md#tests-added) |
| 8. Run the tests | Ran the Maven and Gradle builds with Docker, three scans, the hook tests, the guard test against Spring Boot 4.1.0 and two QA runs. GitHub then ran the builds, OSV-Scanner and QA on the pull request. | All pass. | [testing.md](testing.md) |
| 9. Pull request | Pushed the feature branch and opened a pull request, ready for review. Did not merge, approve or deploy; a project hook blocks those commands for Droid. | [Pull request #1](https://github.com/factory-aj-pahl/spring-petclinic/pull/1), opened at 16:32:12 and open | [approval-record.md](approval-record.md) |
| 10. Evidence package | Wrote this package and collected the raw evidence. | This directory and the evidence archive | [raw-evidence.md](raw-evidence.md) |

## Evaluation criteria

| Criterion | Evidence |
|-----------|----------|
| Accuracy | Each statement names its source, and three independent scanners agree. The agent's corrections of its own statements are in [inferred.md](inferred.md#corrections). |
| Dependency discovery | `docs/DEPENDENCIES.md`, and how it was built: [observed.md](observed.md#dependencies) |
| Human effort required | 8 prompts with 19 questions, one manual task outside the session (creating the repository), no code or test work: [timeline.md](timeline.md#human-effort) |
| Quality of remediation | Why 4.1.2 and not 4.1.1, patch-level changes only, a guard against regressions, and a rollback procedure: [changes.md](changes.md#the-fix), `docs/RUNBOOK.md` |
| Quality of testing | Tests that failed before each fix and pass after it, the guard test failing on the vulnerable version, real MySQL and PostgreSQL databases, and QA in a browser, in the sandbox and in GitHub Actions: [testing.md](testing.md) |
| Policy enforcement | The approval record, the project hook and its live block, `AGENTS.md`, and their limits: [observed.md](observed.md#policy-enforcement), [not-completed.md](not-completed.md#limits-of-this-evidence) |
| Traceability and reproducibility | One purpose per commit, sign-off and co-author trailers, the scripts that produced the raw evidence, checksums, and the commands below |
| Quality of evidence | Raw logs, JUnit reports, scan output and screenshots behind every number: [raw-evidence.md](raw-evidence.md) |
| Identifying uncertainty | The Confirmed, Inferred and Unknown labels, the open questions, and what could not be completed: [inferred.md](inferred.md), [not-completed.md](not-completed.md) |
| Time from deficiency to reviewable remediation | [timeline.md](timeline.md#time-to-a-reviewable-fix) |

## How to check the main claims

Run these from the repository root with JDK 17 and Docker:

```bash
# Author, sign-off and co-author of each commit
git log --format='%h %an <%ae>%n%(trailers)' 500158f..HEAD

# Both builds with the full test count: 116 tests, 0 failures, 0 skipped
./mvnw verify
./gradlew build

# Vulnerability scans: "No issues found" for both
osv-scanner scan source -L pom.xml
osv-scanner scan source -L target/classes/META-INF/sbom/application.cdx.json

# Hook tests: 6 tests, OK
python3 -B -m unittest discover -s .factory/hooks

# The guard test must fail against Spring Boot 4.1.0: 7 tests, 6 failures
git worktree add --detach /tmp/guard-check 500158f
cp src/test/java/org/springframework/samples/petclinic/PatchedDependencyVersionsTests.java \
  /tmp/guard-check/src/test/java/org/springframework/samples/petclinic/
(cd /tmp/guard-check && ./mvnw -B test -Dtest=PatchedDependencyVersionsTests)
git worktree remove --force /tmp/guard-check
```

The code was tested in the sandbox at commit `407b5f3`. Later commits change only Markdown files and the checksum list under `docs/`, as `git diff --stat 407b5f3..HEAD` shows. GitHub's checks ran on the pull request at `c85ae9e` ([testing.md](testing.md#github-checks-on-the-pull-request)).

## Files

| File | Content |
|------|---------|
| [observed.md](observed.md) | What the agent read, ran and measured: readiness, dependencies, vulnerabilities, defects, GitHub settings, policy enforcement |
| [inferred.md](inferred.md) | Inferences and their basis, open questions, corrections |
| [changes.md](changes.md) | Each commit, what it changes and why |
| [testing.md](testing.md) | Tests added, test runs, scans, QA, coverage, failures during the work |
| [not-completed.md](not-completed.md) | What the agent could not complete, and the limits of this evidence |
| [timeline.md](timeline.md) | Timeline, human effort and time to a reviewable fix |
| [approval-record.md](approval-record.md) | Every decision put to the approver, and what was done under it |
| [raw-evidence.md](raw-evidence.md), `raw-evidence.sha256` | The raw evidence files and their checksums |
