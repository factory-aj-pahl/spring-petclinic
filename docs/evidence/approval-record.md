# Human approval record

Approver: the session user (GitHub account factory-aj-pahl), answering in the Factory session
Session: 5f856a0b-6321-404e-bcb7-7b7183376551

The agent asked for every decision below with its AskUser tool and waited for the answer before acting on it. Times are UTC on 2026-11-13 and come from the session transcript: when the question was shown and when the answer arrived. This record was written at 03:10:17 (decisions 1 to 4), 03:45:44 (decisions 5 and 6), 13:58:58 (the correction to decisions 2 and 5, and decisions 7 to 10) and 14:54:09 (decisions 11 and 12). At 16:16, decision 10 was rewritten to quote the answers as given, and the action for decision 7 was added. After the pull request was opened, the action for decision 8 was added, with the checks that GitHub ran on it.

| # | Asked → answered | Decision requested | Human answer |
|---|---|---|---|
| 1 | 03:06:30 → 03:08:53 | Approve the remediation plan (Spring Boot 4.1.0 -> 4.1.2 in Maven and Gradle, version guard test, failure-scenario tests, dependency doc and runbook, readiness fixes, QA and wiki skills, PR with evidence) | Approve as proposed |
| 2 | 03:06:30 → 03:08:53, corrected 13:07:57 → 13:11:20 | Where to push the branch and open the PR (the account has read-only access upstream) | New private repository `factory-aj-pahl/spring-petclinic` (corrected, see below) |
| 3 | 03:06:30 → 03:08:53 | Actuator exposes every endpoint without authentication, also in k8s | Record as a follow-up only (no change in this PR) |
| 4 | 03:06:30 → 03:08:53 | Optional sandbox extras | Install Docker to run MySQL/PostgreSQL tests; add DCO Signed-off-by line to commits in the user's name; install ImageMagick for QA screenshot diffs |
| 5 | 03:13:06 → 03:20:16 | Factory Droid GitHub App token cannot create repositories (HTTP 403 `createRepository`) | User creates the repository and grants access; agent pushes once access is confirmed |
| 6 | 03:34:28 → 03:42:09 | Tests found that page numbers from 429,496,731 up return HTTP 500 on `/owners` and `/vets.html` (offset exceeds `Integer.MAX_VALUE`); fixing it changes application code outside the approved plan | Fix it in a separate commit, with tests |
| 7 | 13:13:29 → 13:17:37 | The new repository is empty, so a pull request needs a `main` branch to target, but the project hook (and `AGENTS.md`) forbid agents to push to `main` | One push of the unchanged upstream commit `500158f` to `main` of `factory-aj-pahl/spring-petclinic`, as a one-time exception. The agent runs it once from a script file, because the hook matches command text, and records it here. No other push to `main`. |
| 8 | 13:13:29 → 13:17:37 | Pull request state | Open it ready for review, not as a draft. The agent does not merge, approve or deploy. |
| 9 | 13:07:57 → 13:11:20 and 13:35:12 → 13:36:16 | Wiki | Generate it locally and do not upload it. Add the wiki refresh workflow as its own commit in the same pull request; that workflow must not upload either (its prompt tells `/wiki` to skip the upload, so the rolling `factory/droid-wiki` pull request is the only copy). The generated `droid-wiki/` stays local: it goes into the evidence zip and the presentation and is not committed. |
| 10 | 04:25:23 → 12:47:45 | QA skill questionnaire: which of the 12 proposed flows to use, which databases QA covers, whether to add `.github/workflows/qa.yml` (it needs a `FACTORY_API_KEY` secret), and whether to keep the defaults for evidence and failure learning (text snapshots in the report, screenshots as workflow artifacts, new failure patterns suggested in the report) | "Use all 12 as listed" (W1 to W9 in the browser, A1 to A3 through curl); "H2 only"; "Yes, informational (does not block merge)"; "Keep the defaults". `.factory/skills/qa/config.yaml` records these answers; video evidence and Docker Compose are off, since the defaults include no video and H2 needs no database container. |
| 11 | 14:14:32 → 14:43:51 | Documenting the code found F15: `/vets.html` caches every requested page number with no size limit, so an unauthenticated client can grow the heap until the application runs out of memory (5,000 page numbers left 5,000 cached entries). Fixing it changes application code outside the approved plan | Fix in a separate commit with tests (check the page range before the cached query) |
| 12 | 14:14:32 → 14:43:51 | Documenting the code also found F16-F20 (owner paging links drop the search, MySQL duplicate pet name race, search case differs by database, translation gaps, owner page defects) | Record them as runbook follow-ups only |

Correction: decisions 2 and 5 first named the repository `factory-aj-pahl/spring-petclinic-eval`. The user corrected this to `factory-aj-pahl/spring-petclinic` before anything was pushed. The agent verified at 13:12:52 that the repository exists, is private and empty, and that the account has admin access.

Decisions the agent did not make on its own: plan scope, PR destination, runtime security behavior, legal DCO attestation, the one-time push to `main`, the PR state, where the wiki lives, whether to fix the findings made while documenting the code.

Not put to the approver: F21 and F22 came from a final review of the workflows after decision 12. They are recorded in `docs/RUNBOOK.md` as follow-ups that need an owner decision, and nothing was changed for them. The reviewer of the pull request decides them.

## Actions taken under these decisions

| Decision | Action | When | Evidence |
|---|---|---|---|
| 1 | Carried out the plan in commits `d74cc16` to `f9bbb32` | 03:32:40 to 04:22:51 | `git log 500158f..HEAD` |
| 4 | Installed Docker (server 29.1.3, Compose 2.40.3) and ImageMagick 6.9.12 in the sandbox. Every commit on the branch has `Signed-off-by: factory-aj-pahl` | 03:11:40 to 03:12:01 | `git log --format=%(trailers) 500158f..HEAD` |
| 6 | Fixed in `3a5a0bc`. Its tests failed before the change (7 failures) and pass after it | 03:57:17 | `docs/evidence/testing.md` |
| 3 | Recorded as follow-up F1 in `docs/RUNBOOK.md`. No configuration change | 04:16:55 (`0d8c589`) | `docs/RUNBOOK.md` |
| 2, 5 | The user created `factory-aj-pahl/spring-petclinic`. The agent checked it before pushing anything | 13:12:52 | This record, correction above |
| 10 | Added the QA skill and the informational `qa.yml` in `f643efa`. A local QA run passed 16 of 16 test cases | 13:45:35 | `docs/evidence/testing.md` |
| 9 | Added the wiki refresh workflow, which does not upload, in `7a27efc`. Generated the wiki locally in `droid-wiki/` (37 pages). It is excluded from Git and was not uploaded | 13:48:07 | `.git/info/exclude`, evidence zip |
| 11 | Fixed in `0e8563f`. Its tests failed before the change (2 failures) and pass after it | 14:48:30 | `docs/evidence/testing.md` |
| 12 | Recorded F16 to F20 in `docs/RUNBOOK.md` (`2aff817`). No code change | 14:54:21 | `docs/RUNBOOK.md` |
| 7 | Pushed the unchanged upstream commit `500158f` to `main` of `factory-aj-pahl/spring-petclinic` once, with `work/seed-main.sh` (in the evidence archive as `scripts/seed-main.sh`). The push created `main`; afterwards the remote had only `refs/heads/main` at `500158f`. No other push to `main` | 15:44:09 to 15:44:14 | `raw/seed-main/push.log`, `raw/seed-main/repo-settings.txt` |
| 8 | Pushed the branch `security/spring-boot-4.1.2-cve-remediation` at `c85ae9e`, which created it on GitHub, and opened [pull request #1](https://github.com/factory-aj-pahl/spring-petclinic/pull/1) ready for review, not as a draft. `main` stayed at `500158f`. GitHub's checks on the pull request passed: the Maven and Gradle builds, OSV-Scanner and QA (20 of 20 test cases). The agent did not merge, approve or deploy; the pull request is open | Push 16:31:54 to 16:31:57, pull request 16:32:12, checks done 16:41:38 | `raw/github/push-branch.log`, `raw/github/pr-opened.txt`, `raw/github/pr-checks-c85ae9e.txt` |
