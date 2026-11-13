# Raw evidence

The evidence archive holds the raw output behind this package: build and test logs, JUnit reports, coverage reports, scan results, probe output, QA reports and screenshots, and the scripts that produced them. It has 567 files and about 21 MB of generated output, so it is not in the repository. It is delivered as the `evidence/` folder of `spring-petclinic-evaluation.zip`, next to a copy of the Markdown files in this directory.

To check that an archive matches this package, run this in its `evidence/` folder:

```bash
sha256sum -c raw-evidence.sha256
```

`raw-evidence.sha256` lists every file under `raw/` and `scripts/` with its SHA-256 checksum. Because it is committed, a changed or missing file shows up as a failed check.

Where the tables below say "Session", the agent ran the command directly in the Droid session, and the session transcript records it. The transcript is not in the archive ([not-completed.md](not-completed.md#limits-of-this-evidence)). The scripts use the paths of the agent's sandbox, such as `/home/factory-user/spring-petclinic` and `$HOME/.local/tools/jdk-17`; change them to run elsewhere.

## Before the fix

| Path | Content | Made by |
|------|---------|---------|
| `raw/baseline-mvn-verify.log`, `raw/baseline-surefire/`, `raw/baseline-jacoco.csv` | `./mvnw verify` on `500158f` at 03:02, before Docker was installed | Session |
| `raw/baseline-docker/` | `./mvnw verify` on `500158f` with Docker, in a fresh clone, at 15:34: `env.txt`, `mvn-verify.log`, `surefire-reports/` and `jacoco.csv`. `clone.txt` is empty because the quiet `git clone` printed nothing. | `scripts/baseline-docker.sh` |
| `raw/deps-all-baseline.txt`, `raw/deptree-baseline.txt` | The 171 resolved artifacts on Spring Boot 4.1.0, from Maven's `dependency:list` and `dependency:tree` | Session |
| `raw/sbom-baseline.cdx.json` | The CycloneDX SBOM that the build writes into the jar | Session, copied from `target/classes/META-INF/sbom/` |
| `raw/osv-scanner-sbom-baseline.json`, `raw/osv-scanner-pom-baseline.json` | OSV-Scanner 2.7.1 on the SBOM and on `pom.xml`: 20 advisories in 6 packages | Session |
| `raw/osv-api-baseline.json` | The OSV.dev API's answers for the 171 artifacts: 20 findings | Session, a query run at 02:59 |
| `raw/bom/boot-bom-versions.txt` | The library versions that the published Spring Boot 4.1.0, 4.1.1 and 4.1.2 BOMs manage, read at 15:54 | Session |

## The fix

| Path | Content | Made by |
|------|---------|---------|
| `raw/remediated-mvn-verify.log`, `raw/remediated-surefire/`, `raw/remediated-jacoco.csv` | `./mvnw verify` with Spring Boot 4.1.2 and no other change, at 03:22: 81 tests | Session |
| `raw/remediated-gradle-build.log`, `raw/remediated-gradle-test-results/` | `./gradlew build` with Spring Boot 4.1.2, at 03:29: 81 tests | Session |
| `raw/deps-all-remediated.txt`, `raw/deptree-remediated.txt`, `raw/sbom-remediated.cdx.json` | As before the fix, on Spring Boot 4.1.2: 172 artifacts | Session |
| `raw/osv-scanner-sbom-remediated.json`, `raw/osv-scanner-pom-remediated.json` | OSV-Scanner on Spring Boot 4.1.2: no results | Session |
| `raw/osv-api-remediated.json` | The OSV.dev API's answers for the 172 artifacts: no findings | `scripts/osv_api_check.py` |

## Tests that failed before each fix

| Path | Content | Made by |
|------|---------|---------|
| `raw/overflow-discovery/` | The probe at 03:33 that found HTTP 500 for huge page numbers. `probe-transcript.txt` holds the agent's commands and their output; `app-0333.log` and `app-0334.log` are the application logs of the two runs. | `probe-transcript.txt`: `scripts/extract-overflow-probe.py`, from the session transcript |
| `raw/tdd-before-overflow-fix.log` | The first run of the new tests, at 03:47: 53 tests, 8 failures | Session |
| `raw/tdd-red-overflow.log`, `raw/tdd-green-overflow.log` | The page number tests before and after the fix: 7 failures, then 0 | Session |
| `raw/tdd-red-f15.log`, `raw/tdd-green-f15.log` | The F15 tests before and after the fix: 2 failures, then 0 | Session |
| `raw/commit-checks/` | The runs before commits `23d0c67` and `3a5a0bc`: the 26 new tests, then the full build with 107 and with 114 tests | Session |
| `raw/guard-check-4.1.0/` | The guard test against Spring Boot 4.1.0, in a worktree of `500158f`: `env.txt`, `worktree.txt` and `mvn-test.log` | `scripts/guard-check-4.1.0.sh` |
| `raw/no-docker-simulation-invalid.log` | The invalid attempt to run the build without Docker ([testing.md](testing.md#failures-during-the-work)) | Session |

## Final runs

| Path | Content | Made by |
|------|---------|---------|
| `raw/final/`, `raw/final-2/`, `raw/final-3/`, `raw/final-4/` | Full runs at `7a27efc`, `2aff817`, `b851d19` and `407b5f3`. Each has `env.txt` (the commit, the number of uncommitted changes to tracked files, the tool versions, the exit codes and the times), `mvn-verify.log`, `surefire-reports/`, `jacoco.csv`, `sbom.cdx.json`, `osv-pom.txt`, `osv-sbom.txt`, `hook-unittest.log`, `gradle-build.log` and `gradle-test-results/`. | `scripts/final-tests.sh` to `scripts/final-tests-4.sh`, one per run |

## The running application

| Path | Content | Made by |
|------|---------|---------|
| `raw/findings-probe/`, `raw/findings-probe-final/` | Probes of the jar built from `7a27efc`, at 14:13, and from `407b5f3`, at 15:38, on H2 and on PostgreSQL: `h2-probe.json` and `pg-probe.json` (the `.txt` files are the same output with the exit status), the application logs, the jar's checksum and Git commit, the Java version, the PostgreSQL container id, the start and end times, and `secret-check.txt` | `scripts/probe-findings.sh`, `scripts/probe-findings-final.sh` and `scripts/probe_findings.py` |
| `raw/qa-run-1794576862/` | QA run 1, 13:34 to 13:40, on `f9bbb32` with the QA skill before it was committed | The QA skill in `droid exec`, with the prompt from `.github/workflows/qa.yml` |
| `raw/qa-run-1794584301/` | QA run 2, 15:38 to 15:43, on `407b5f3` | Same |

Each QA directory has `report.md`, screenshots (`W*.png`), before-and-after comparisons (`W*-diff.gif`), text snapshots of the pages, the curl output of the API test cases, `app.log`, `started.txt`, `finished.txt` and `droid-exec-qa.jsonl`, the event stream of the `droid exec` session with the prompt and every tool call. `build.log` is empty in both, because the quiet Maven build printed nothing.

## Policy, approvals and GitHub

| Path | Content | Made by |
|------|---------|---------|
| `raw/session/session-evidence.txt` | The user's messages, every question prompt with its answers and times, the hook blocks and the time spent waiting for answers | `scripts/session-evidence.py`, from the session transcript |
| `raw/hook-e2e-droid-exec.jsonl` | The event stream of the `droid exec` session in which the hook let `echo hook-check-allowed` run and stopped `gh pr merge` | Session |
| `raw/hook-live-block-main-session.txt` | The hook's block of a merge command in the main session at 04:21:48, with the hook's message | Session, written by the agent from the tool result |
| `raw/seed-main/` | `push.log`: the one-time push of `500158f` to `main` (decision 7). `repo-settings.txt`: the repository settings that the GitHub API returned after it. | `scripts/seed-main.sh`; session |
| `raw/github/` | `upstream-settings.txt`: branch protection, rulesets and CODEOWNERS of `spring-projects/spring-petclinic`. `runs-after-seed.txt`: the workflow runs on `main` after the seed push. | Session |
| `raw/lint/workflow-lint.txt` | actionlint, and GitHub's JSON schemas for the workflows, Dependabot and the issue forms, at `c6b60ff` | Session |
| `raw/credential-scan.txt` | The scan of the archive for credentials and personal data | `scripts/credential-scan.py` |

## Scripts

| Script | What it does |
|--------|--------------|
| `baseline-docker.sh` | Clones the repository, checks out `500158f` and runs `./mvnw clean verify` with Docker. Writes `raw/baseline-docker/`. |
| `final-tests.sh` to `final-tests-4.sh` | The same script with a different output directory. Records the commit and the number of uncommitted changes to tracked files, then runs `./mvnw clean verify`, both OSV-Scanner scans, the hook tests and `./gradlew clean build`, one after the other. |
| `summarize-final.py` | Prints the results of one `raw/final*/` directory |
| `guard-check-4.1.0.sh` | Adds a worktree at `500158f`, copies the guard test into it and runs only that test |
| `probe-findings.sh`, `probe-findings-final.sh`, `probe_findings.py` | Start the jar on H2, and on PostgreSQL in Docker with a generated password, and check F15, F16, F18 and F20 |
| `seed-main.sh` | The one-time push of `500158f` to `main`. It refuses to run if `origin` is another repository or already has branches, and removes any credential from the URLs in its log. |
| `session-evidence.py`, `extract-overflow-probe.py` | Extract the approval evidence and the probe of 03:33 from the session transcript, with tokens redacted |
| `osv_api_check.py` | Queries the OSV.dev API for every artifact in a `dependency:list` file, independently of OSV-Scanner |
| `qa_post_step_test.py`, `qa_post_step_test.out` | Runs the report comment steps of `.github/workflows/qa.yml` locally in 25 cases, and its output |
| `redact-session-ids.py` | Replaced the HTTP session ids in `raw/findings-probe-final/h2-probe.json` and `h2-probe.txt` |
| `credential-scan.py` | Scans the archive for tokens, keys, session ids, passwords and email addresses |
| `collect-tests.py`, `collect-tdd.py`, `test-names.py`, `count-labels.py` | Print the numbers used in this package: tests per class before and after, the failures of the red runs, the test case names, and the labels in `docs/DEPENDENCIES.md` |

## Redactions and personal data

- The HTTP session ids in `raw/findings-probe/` and `raw/findings-probe-final/` are replaced with `<redacted>`.
- The scripts that extract from the session transcript replace any token with `[REDACTED]`. None of the extracted files needed it.
- Each probe checked that its generated database password appears in none of its output files (`secret-check.txt`).
- `raw/credential-scan.txt` reports no tokens, keys, credentials in URLs, authorization headers or session ids.
- The archive contains the approver's name and work email address, because the build records the Git user in its logs and JUnit reports. Every commit's author line and sign-off contain the same. The baseline logs also contain the name and email address of the upstream author of `500158f`, as its public commit does.
