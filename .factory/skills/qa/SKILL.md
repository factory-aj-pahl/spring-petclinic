---
name: qa
description: >
  Run QA tests for Spring PetClinic. Analyzes the git diff to determine affected areas, starts
  the branch locally, runs the matching qa-web and qa-api flows as an anonymous visitor, and adds
  tests targeted at the diff. Uses droid-control for browser interactions.
  Use when testing pull requests, releases, or smoke testing a build.
---

# QA Orchestrator

**SCOPE: This skill performs manual/functional QA only -- verifying that the application actually works by interacting with it as a real user would (browser and HTTP calls). Do NOT run or report on CI checks, linting, formatting, unit tests, `./mvnw verify`, dependency scans, or any static analysis. Those are handled by separate workflows.**

**Never silently skip a flow. If a flow cannot complete, report it as BLOCKED with what was tried and how the user can fix it.**

## Step 1: Load Configuration

Read `.factory/skills/qa/config.yaml` for the environment, persona, app definitions and evidence settings.

Each app's `test_tool` in config.yaml names its driver. For every affected app whose tool is `agent-browser` (`qa-web`), invoke the `droid-control` skill before interacting with the app. Follow its routing tables:

- Load the target driver selected for the app: the web page route (browser-use, which drives `agent-browser`).
- Use the Capture and Verify stages on every run.
- Load Compose only when both `video_evidence: true` and `droid_control.compose: true`.
- When `droid_control.compose: false`, never invoke Compose. Keep raw captures and text snapshots as evidence.

Treat `droid_control.compose` as a runtime toggle. Read it on every run; do not cache the install-time answer.

Apps whose tool is `curl` (`qa-api`) do not need droid-control.

The only persona is `visitor`. Use its `test_focus` to choose tests targeted at the diff, and its `cannot_do` list for negative tests.

Create one run ID and evidence directory, and use that directory as droid-control's `RUN_DIR`:

```bash
RUN_ID="$(date +%s)"
mkdir -p "qa-results/$RUN_ID" && echo "RUN_ID=$RUN_ID RUN_DIR=$PWD/qa-results/$RUN_ID"
```

Shell variables do not carry over from one command to the next, because each command can run in a new shell. Note the printed values and write them literally into every later command. Below, `<RUN_ID>`, `<RUN_DIR>`, `<PORT>` and `<PID>` stand for those literal values.

## Step 2: Determine Target Environment

The only target is `local` in config.yaml: the application built from the checked-out branch and started on this machine. PetClinic has no hosted development, staging, preview or production environment, and the Kubernetes manifests deploy an unrelated image, so there is nothing else to test against. Never test a URL that is not a server this run started from the branch.

Respect the restrictions in config.yaml: use the default in-memory H2 database, and do not start the `mysql` or `postgres` profiles or the Docker Compose services.

## Step 3: Analyze Git Diff

Use the diff base the caller gives you (in CI, `origin/<base branch>`). Otherwise use `origin/main`, or `main` if `origin/main` does not exist. List the changed files, uncommitted changes included:

```bash
git diff --name-only "$(git merge-base <base> HEAD)"
git status --short   # also shows untracked files
```

Map changed files to apps using the `path_patterns` in config.yaml. One file can affect both apps. For example, `pom.xml` sets the versions of the server, the web framework and the JSON library, so it affects every page and endpoint.

Files that don't match ANY app's path_patterns (for example `.factory/**`, `.github/**`, `docs/**`, `README.md`, `k8s/**`, `docker-compose.yml`, `build.gradle`, `src/test/**`, and the MySQL and PostgreSQL files under `src/main/resources/`) are NOT associated with any app. Do NOT run app test flows for them.

For each affected app:

- Run ONLY that app's flows that relate to the change (Step 5)
- Generate ADDITIONAL targeted tests based on the specific changes in the diff

For apps NOT affected by the diff:

- Do NOT load or run their sub-skill, their flows, or their pre-flight checks. They are out of scope.

If NO app is affected by the diff (docs-only, CI-only, test-only or config-only changes), report INCONCLUSIVE: "No app code changed -- QA not applicable for this diff." Do NOT build or start the app.

## Step 4: Pre-flight Checks (affected apps only)

Both apps test the same server, so start it once and give its base URL, `http://localhost:<PORT>`, to every sub-skill:

1. Check for JDK 17 or later: `java -version`.
2. Build the branch once with the `build_command` of the affected apps in config.yaml (both apps use the same command). It skips the format and nohttp checks, which CI runs separately, so they cannot block QA. The build takes about a minute, longer when dependencies are downloaded for the first time.
3. Pick a free port: 8080, or another such as 18080. A port is free when `curl -s -o /dev/null http://localhost:<PORT>/` cannot connect. Never reuse a server this run did not start; it may run other code.
4. Start the server as a background job that keeps running after the command returns, and note its process ID. In Droid, run the command below through the Execute tool with `fireAndForget: true`, which returns the process ID; a job started with a plain `&` is stopped when its command ends.
   ```bash
   exec java -jar target/spring-petclinic-*.jar --server.port=<PORT> > <RUN_DIR>/app.log 2>&1
   ```
5. Poll `curl -fsS http://localhost:<PORT>/actuator/health` every 2 seconds, for up to 120 seconds, until the body contains `"status":"UP"`. Between polls, check that the process is still alive with `kill -0 <PID>`. If it exited, the port was taken or the start failed: read `<RUN_DIR>/app.log`.
6. Check that the server runs this checkout: `git.commit.id` in `http://localhost:<PORT>/actuator/info` must be the start of `git rev-parse HEAD`.
7. For `qa-web` only: check that the plugin named in `droid_control.plugin` is active (`droid plugin list --scope project`) and that `agent-browser --version` works. If `agent-browser` is missing, install it with `npm install -g agent-browser`, then run `agent-browser install` (add `--with-deps` on a Linux CI runner). When `droid_control.compose: true`, also check the Compose prerequisites listed by droid-control.

If a pre-flight check fails, report the affected app's flows as BLOCKED with the exact error and the fix (for a server that does not start, include the last 50 lines of `<RUN_DIR>/app.log`). Continue with any other affected app that can still run.

When every flow is done, clean up as `cleanup` in config.yaml describes: stop the server you started with `kill <PID>`.

## Step 5: Execute Diff-Relevant Flows Only

For each affected app, read the sub-skill named by its `skill` in config.yaml: `.factory/skills/<skill>/SKILL.md`.

Each sub-skill contains a MENU of flows, and each flow lists the code it covers. You must:

1. Read the diff carefully and identify which flows are relevant to the change.
2. Run those flows PLUS adjacent flows that verify the change integrates correctly. A dependency upgrade in `pom.xml` changes the embedded server, Spring MVC and JSON handling, so it is relevant to every flow: run the whole menu of both apps.
3. Do NOT run unrelated flows. For example, a change limited to `vet/VetController.java` needs the vets flows (W7 and A1), not the owner, pet or visit forms.
4. If no existing flow covers the change, write a NEW ad-hoc test that directly verifies the changed behavior.
5. Do NOT run unit tests, `./mvnw verify`, or any automated test suite. This is manual/functional QA: interact with the app as a real user would.
6. Run the flows in the order the sub-skill asks for. `qa-web` runs the flows that only read data before the flows that create records.

## Step 6: Evidence Capture

After each significant test step, capture evidence. Use **text snapshots as primary evidence**; they render inline in the PR comment with no image hosting issues.

For the web app (agent-browser):

- Use the `droid-control` web route; let its browser-use atom own browser mechanics and its Capture atom own the recording lifecycle.
- Capture the page's accessibility tree as text evidence, trimmed to the part that proves the result.
- Save screenshot files to `<RUN_DIR>/` for the artifact upload.
- When `video_evidence: false`, or when an upload fails: do NOT embed `![image](url)` markdown in the report. Artifact and repository URLs cannot be displayed inline in GitHub PR comments. Mention the filename and note that it is in the downloadable artifacts.
- When `imagemagick: true` and a flow has before and after screenshots of the same page, also save an animated diff: `convert -delay 120 -loop 0 before.png after.png <RUN_DIR>/<flow>-diff.gif` (`magick` instead of `convert` on ImageMagick 7).

For the API app (curl): record the exact command, the HTTP status and the relevant part of the response body in a fenced code block.

**Video evidence (when `video_evidence: true` in config.yaml):**

1. Follow the droid-control Capture atom for recording start, interactions, recording stop and raw-output verification. Record exactly one video per flow, never one video for the whole run.
2. If `droid_control.compose: false`, keep the browser driver's raw WebM. Upload it only after verifying it is a non-empty, playable video.
3. If `droid_control.compose: true`, hand the raw recording to the droid-control Compose atom and render `<RUN_DIR>/<flow-slug>.mp4`:
   - Use a `single` layout and pass the literal `"preset": "factory"` to Compose. Never use any other preset.
   - Trim dead time, add a concise title card, and use annotations only when they clarify the proof.
   - Keep the video at 60 seconds or less, and verify it with the Compose atom's finalization checks before upload.
4. Verify the evidence file exists and is non-empty. If capture or Compose fails, retry that stage once; if it still fails, fall back to text evidence and keep any raw capture in the artifacts.
5. Upload each video to GitHub's user-attachments endpoint. It is undocumented, so trust the live response:
   ```bash
   REPO_ID="${REPO_ID:-$(gh api "repos/$GITHUB_REPOSITORY" --jq .id)}"
   curl -sS --fail-with-body --request POST \
     --header "Authorization: Bearer $QA_EVIDENCE_TOKEN" \
     --header "Content-Type: application/octet-stream" \
     --header "Accept: application/json" \
     --header "X-GitHub-Api-Version: 2022-11-28" \
     "https://uploads.github.com/user-attachments/assets?name=<evidence-file>&content_type=<url-encoded-content-type>&repository_id=$REPO_ID" \
     --data-binary "@<RUN_DIR>/<evidence-file>" \
   | jq -e '.url'
   ```
   `QA_EVIDENCE_TOKEN` must be a classic PAT with `repo` scope. A 404 with a valid `repository_id` means an installation token such as `GITHUB_TOKEN` was used. A 403 `Resource not accessible by personal access token` means a fine-grained PAT. A 403 `Resource protected by organization SAML enforcement` means the classic PAT still needs SSO authorization.
6. Put each returned URL on its own line, directly under the flow's label in the Evidence section, and never wrap it in `![]()`. Screenshots uploaded the same way use `![name](url)`. Never embed a link that did not come back from the upload response; for a failed upload, use the text-first rules above.

Evidence quality rules:

- Focus on the RELEVANT content. Label each snapshot: what it shows and why it matters for the test.
- Each snapshot must show something different from the previous one.
- NEVER embed broken image links. The workflow uploads everything in `qa-results/` as an artifact; reference that for visual evidence.

## Step 7: Test Quality Gate

1. CHANGE-SPECIFIC FIRST. At least half of the tests must verify the behavioral change in the diff.
2. INTEGRATION TESTS ARE VALID. Tests that show the change works with the pages and endpoints around it are not smoke tests.
3. NO UNRELATED FLOWS. Do not test features the diff cannot affect.
4. NO AUTOMATED TEST SUITES. Do not run `./mvnw verify`, `./gradlew build` or any CI-style check.
5. NEGATIVE TESTS. Include at least one test of error handling or a boundary condition related to the change.
6. INTERACTIVE TESTING. Use the browser for pages and forms. Use curl for the JSON and Actuator endpoints, and for form requests that a browser cannot send (qa-web W5 step 5).
7. INCONCLUSIVE IF UNSURE. If you cannot say what the change does, mark the result INCONCLUSIVE rather than PASS.

## Step 8: Handle Failures

**Never silently skip a flow.** If a flow cannot complete, report it as BLOCKED with what was tried and how the user can fix it. Then continue with the next flow; never abort the whole run for one failure. A FAIL needs evidence of the wrong behavior. Known issues listed in a sub-skill's Known Failure Modes are not new failures unless the diff touches that code.

## Step 9: Generate Report

Write the report to `qa-results/report.md` using `.factory/skills/qa/REPORT-TEMPLATE.md`. Key rules:

- Start with the `## QA Report` heading followed by the results table.
- The Result column MUST use emojis: :white_check_mark: PASS, :x: FAIL, :no_entry: BLOCKED, :warning: FLAKY, :grey_question: INCONCLUSIVE.
- Keep it CONCISE: the table, a short "Action Required" section when needed, and the collapsed evidence.
- Do NOT include a "Behavioral Change Summary", "Blocked Flows" prose, an "Info" metadata table, or long explanations of the diff. The reviewer already knows that.
- Do NOT report setup steps (building, starting the server, opening the browser) as test rows. Only report rows that verify user-facing behavior or the change in the diff.
- Put ALL evidence in a single collapsed `<details>` block. Embed accessibility-tree snapshots and curl output as labeled fenced code blocks, and name screenshot files instead of embedding them (they are in the downloadable artifacts).
- With `video_evidence: true`, put each uploaded video URL on its own line (bare, never wrapped in `![]()`) and embed uploaded screenshots with `![name](asset-url)`. For any flow whose upload failed, use the text-first rules instead.

## Step 10: Suggest Skill Updates (Failure Learning)

After writing the report, check whether any BLOCKED or FAIL result revealed a **testing environment insight** that would help future runs: how the environment, the build, the browser driver or the app behaves. Do not suggest fixes for bad selectors or typos in these skills (fix those directly), and do not suggest anything already listed under Known Failure Modes or expected from the diff.

Read `failure_learning` from config.yaml on every run:

- `suggest_in_report`: add the table below to the report only. Do NOT write `qa-results/skill-updates.json`.
- `auto_commit` or `open_pr`: add the table AND write `qa-results/skill-updates.json`, a JSON array of edits with the fields `file` (path of the skill file), `section` (the Markdown heading to change, such as `Known Failure Modes`), `action` (`append` or `replace`) and `content` (the exact Markdown). `.github/workflows/qa.yml` has no step that applies these edits yet; say so under "Action Required" until one is added (re-run install-qa with the new answer to generate it).

If no new insight came up, leave the section out.

## Suggested Skill Updates (N issues found)

| #   | Severity        | File     | Issue               | Fix Prompt                                                                           |
| --- | --------------- | -------- | ------------------- | ------------------------------------------------------------------------------------ |
| 1   | <emoji> <level> | `<file>` | <short description> | <details><summary>Copy</summary><br>`<full droid prompt to fix the issue>`</details> |

Severity levels:

- `🔴 Breaking`: causes test failures on every run (wrong URL, wrong start command, missing step)
- `🟡 Degraded`: intermittent failures or slow runs (timing, port clashes, driver quirks)
- `🔵 Info`: new knowledge that improves future runs without causing failures

Each Fix Prompt must be a self-contained instruction that Droid can carry out when pasted, naming the target file and the section (usually "Known Failure Modes") to change.
