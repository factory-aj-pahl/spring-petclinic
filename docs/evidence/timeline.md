# Timeline and human effort

All times are UTC on 2026-11-13. They come from the session transcript (`raw/session/session-evidence.txt`), the commit times and the files in the evidence archive.

## Timeline

| Time | What happened | Evidence |
|------|---------------|----------|
| 02:55:43 | The user sends the request with the specification. | Session |
| 02:59:42 | First vulnerability scan of Spring Boot 4.1.0: 20 advisories in 6 libraries. | `raw/osv-scanner-sbom-baseline.json` |
| 03:02 | Baseline build: 79 tests, 2 skipped, because Docker is missing. | `raw/baseline-mvn-verify.log` |
| 03:06:30 | The agent presents its findings and the plan, with four questions, and waits. | Approval record, decisions 1 to 4 |
| 03:08:53 | The approver approves the plan. | Same |
| 03:11 to 03:12 | Docker and ImageMagick are installed in the sandbox (decision 4). | Approval record |
| 03:13:06 to 03:20:16 | The Droid App token cannot create the repository. The user will create it (decision 5). | Approval record |
| 03:22 and 03:29 | With Spring Boot 4.1.2, the Maven and Gradle builds pass the 81 existing tests. Scans at 03:26 and 03:27: 0 advisories. | `raw/remediated-*` |
| 03:32:40 | The fix is committed: `d74cc16`. | `git log` |
| 03:33 | Sending untrusted input to the running application finds HTTP 500 for huge page numbers. | `raw/overflow-discovery/` |
| 03:34:28 to 03:42:09 | Decision 6: fix it in a separate commit, with tests. | Approval record |
| 03:54:03 | The guard and failure-scenario tests are committed: `23d0c67`. | `git log` |
| 03:57:17 | The page number fix is committed: `3a5a0bc`. Its tests failed before it. | `raw/tdd-red-overflow.log`, `raw/tdd-green-overflow.log` |
| 04:15 to 04:22 | CI fixes, Dependabot and OSV-Scanner, the dependency documentation and runbook, `AGENTS.md` and the hook: `c565970` to `f9bbb32`. | `git log` |
| 04:21:48 | The hook stops a merge command in the live session. | `raw/hook-live-block-main-session.txt` |
| 04:25:23 to 12:47:45 | The QA questionnaire waits 502 minutes, overnight, for its answers (decision 10). | Approval record |
| 13:07:57 to 13:17:37 | The approver corrects the repository name and decides where the wiki goes, how `main` is seeded and how the pull request is opened (decisions 7 to 9). | Approval record |
| 13:12:52 | The agent checks the new repository: private, empty, admin access. | Approval record |
| 13:34 to 13:40 | QA run 1: 16 of 16 test cases pass. | `raw/qa-run-1794576862/` |
| 13:45:35 and 13:48:07 | The QA skill and the wiki workflow are committed: `f643efa`, `7a27efc`. | `git log` |
| 13:51 to about 14:14 | The agent generates the wiki, 37 pages, locally. | `droid-wiki/` in the presentation archive |
| 14:13 | A probe of the jar confirms F15, F16, F18 and F20. | `raw/findings-probe/` |
| 14:14:32 to 14:43:51 | Decisions 11 and 12: fix F15, record F16 to F20. | Approval record |
| 14:48:30 | F15 is fixed: `0e8563f`. Its tests failed before it. | `raw/tdd-red-f15.log`, `raw/tdd-green-f15.log` |
| 14:54 to 15:31 | Follow-ups F15 to F22, corrections, labels and open questions: `2aff817` to `407b5f3`. | `git log` |
| 15:24 | The guard test fails against Spring Boot 4.1.0, as intended. | `raw/guard-check-4.1.0/` |
| 15:31 to 15:35 | Final test run at `407b5f3`, then the baseline with Docker at `500158f`. | `raw/final-4/`, `raw/baseline-docker/` |
| 15:38 to 15:43 | QA run 2: 18 of 18 pass. Second probe: F15 fixed. | `raw/qa-run-1794584301/`, `raw/findings-probe-final/` |
| 15:44:09 to 15:44:14 | The one-time push of `500158f` to `main` (decision 7). | `raw/seed-main/push.log` |
| 15:45 to 15:49 | The upstream Maven and Gradle workflows pass on `main`. | `raw/github/runs-after-seed.txt` |
| 15:48:00 | `c6b60ff`, the last commit before this package. | `git log` |
| 16:30:25 | The evidence package is committed: `c85ae9e`. | `git log` |
| 16:31:54 to 16:31:57 | The agent pushes the feature branch. `main` stays at `500158f`. | `raw/github/push-branch.log` |
| 16:32:12 | Pull request #1 is opened, ready for review (decision 8). | `raw/github/pr-opened.txt` |
| 16:32:19 to 16:41:38 | GitHub's checks on the pull request pass: OSV-Scanner, the Maven and Gradle builds, and QA with 20 of 20 test cases. | `raw/github/pr-checks-c85ae9e.txt`, `raw/github/qa-ci-run-43463692800/` |

After the checks, two commits that change only Markdown files and the checksum list under `docs/` recorded the pull request, its checks and the new archive files, and labeled the CI entries that the checks ran as Confirmed. The pull request description was updated to match. The pull request page shows the checks for those commits.

## Human effort

| Measure | Value |
|---------|-------|
| Messages from the user | 1, the request |
| Question prompts answered | 8 prompts with 19 multiple-choice questions. One answer was typed as text: the correction of the repository name. |
| Work outside the session | 1 task: creating the repository `factory-aj-pahl/spring-petclinic` and giving the agent access |
| Code, tests or documentation written by a person | None |
| Time the agent spent waiting for answers | 557.5 minutes. 502.4 of them were the QA questionnaire, overnight. The other 7 prompts were answered within 1.1 to 29.3 minutes. |
| Time the agent was not waiting | 208.5 of the 766 minutes from the request to the last event in the transcript excerpt, 15:41 |

Still to do for people: review and merge the pull request, and the decisions listed in [README.md](README.md#decisions-for-the-reviewer).

## Time to a reviewable fix

| Milestone | Time | After the request |
|-----------|------|-------------------|
| Vulnerability identified | 02:59:42 | 4 minutes |
| Plan approved | 03:08:53 | 13 minutes |
| Fix verified with the existing tests and three scans | 03:29 | 34 minutes |
| Fix committed, `d74cc16` | 03:32:40 | 37 minutes, of which 9.6 were spent waiting for answers |
| Fix with its guard and failure-scenario tests, `23d0c67` | 03:54:03 | 58 minutes |
| Readiness fixes, documentation and hook, `f9bbb32` | 04:22:51 | 1 hour 27 minutes |
| The repository can receive the branch | 13:17:37 | 10 hours 22 minutes, mostly the overnight wait |
| Pull request opened, ready for review | 16:32:12 | 13 hours 36 minutes |
| GitHub's checks on the pull request pass | 16:41:38 | 13 hours 46 minutes |

The fix and its tests were reviewable 58 minutes after the request, but there was nowhere to push them: the upstream repository is read-only for this account, and the agent could not create a repository. Once the repository was ready, the agent finished the QA skill, the wiki and the evidence before opening the pull request, so that the reviewer would get one complete pull request. If the time to a reviewable fix matters most, the pull request can be opened as soon as the fix and its tests pass, with the rest added as later commits.
