# What the agent inferred, and what remains unknown

`docs/DEPENDENCIES.md` gives every dependency one of three labels:

| Label | Meaning |
|-------|---------|
| Confirmed | Found in code or configuration and exercised by a test or a live run |
| Inferred | Follows from code or configuration, but nothing in this repository exercises it |
| Unknown | The repository cannot answer it; someone outside the code has to |

The tables with a status column hold 35 entries: 23 Confirmed, 11 Inferred and 1 Unknown. Three of the Confirmed entries, the GitHub Actions of the build workflows, the OSV-Scanner action and the QA check, were Inferred until GitHub ran them on the pull request. The 5 open questions below are Unknown too. Whether each advisory could be reached is a separate judgment, made by reading the code, and none was tested with an exploit: 8 not reachable, 1 not reachable by default, 1 unlikely and 10 unknown.

## Inferences that matter for the decision

| Inference | Basis | What would confirm it |
|-----------|-------|-----------------------|
| About half of the 20 advisories cannot be reached in the default configuration. | Each advisory read against the code and configuration: no DIGEST or FORM authentication, HTTP/2 and TLS off, no Server-Sent Events, no `XsltView`, no `@JsonView`, no default typing, logging through Logback | Exploit tests. The decision does not depend on it, because the upgrade fixes all 20. |
| The upgrade changes nothing but library versions. | All 102 changed resolved versions are patch-level, and 116 tests and 18 QA cases pass. | Running it in production |
| Kubernetes does not get the fix from this pull request. | `k8s/petclinic.yml` runs `dsyer/petclinic` without a tag, and no workflow builds that image (F4). | The owner of the image |
| Database credentials in Kubernetes depend on Spring Cloud Bindings in the image. | Nothing in the build reads `/bindings`; Paketo buildpacks add Spring Cloud Bindings (F6). | Inspecting the image |
| `PostgresOutageIntegrationTests` is skipped on a machine without Docker. | It uses the same `disabledWithoutDocker` setting as `MySqlIntegrationTests`. | A build without Docker. The agent's attempt to simulate one was invalid ([not-completed.md](not-completed.md)). |
| F17: on MySQL, two concurrent requests that add the same pet name give HTTP 500. | The MySQL unique constraint has no name, and `PetController` recognizes the violation by its name. | A concurrent test on MySQL |
| F11: the jar from the Gradle build has no `git` or `build` section in `/actuator/info`. | `build.gradle` lacks the plugins that write them. | Running the Gradle jar |
| The changed kind workflow works on GitHub. | It passes actionlint and GitHub's workflow schema (`raw/lint/workflow-lint.txt`). The Maven, Gradle, OSV-Scanner and QA workflows passed on the pull request ([testing.md](testing.md#github-checks-on-the-pull-request)). | A change under `k8s/`, or a manual run |
| The wiki workflow works in GitHub Actions. | The wiki skill ran locally, the workflow passes actionlint, and the `FACTORY_API_KEY` secret it needs is set, because the QA check ran. It has never run. | The next push to `main`, with `FACTORY_WIKI_PR_TOKEN` set |

## Open questions

The repository cannot answer these questions. Someone who runs or owns the service has to.

| Question | Why it matters |
|----------|----------------|
| Who owns the service and reviews changes to it? | No CODEOWNERS file names an owner, and nothing requires a review before a merge to `main` (F13). |
| Who builds the `dsyer/petclinic` image, and from which commit? | The fix reaches Kubernetes only through a rebuilt image (F4). |
| Where does the application run in production, and how are TLS, backups and secrets handled there? | The repository has no production configuration, and `k8s/` is a demo setup (F5). |
| Who calls `/vets` and the Actuator endpoints? | Restricting Actuator (F1) or changing `/vets` could break a caller that the repository does not know about. |
| What were `./conf.d` and `mysql-default-conf` meant for? | `docker-compose.yml` mounts a directory that does not exist (F7), and `PetClinicRuntimeHints` registers a resource that does not exist. |

## Corrections

The agent corrected these statements of its own after checking them again:

| Original statement | What was wrong | Corrected in |
|--------------------|----------------|--------------|
| `AGENTS.md` (`f9bbb32`): `I18nPropertiesSyncTest` fails when a template or controller contains hard-coded text. | The test checks only literal text between HTML tags in templates, and missing translation keys. It does not check Java code or fragment arguments (F19). | `2aff817` |
| `docs/RUNBOOK.md` (`0d8c589`): `/vets` keeps working from the cache during a database outage. | Only if it was requested before the outage | `2aff817` |
| `docs/DEPENDENCIES.md` (`2aff817`): without Docker, none of the three Docker-based test classes fails. | That was observed only for the two classes that existed before; `PostgresOutageIntegrationTests` never ran without Docker. | `44ddc86`, now marked as inferred |
| `docs/DEPENDENCIES.md` (`0d8c589`): the GitHub Actions workflows, OSV-Scanner, Dependabot and the DCO app are Confirmed. | None of them had run in this repository. | `44ddc86`, now Inferred |
| Commit message of `0e8563f`: the 5,000 cached pages took "about 3.4 MB of heap". | The heap grew by 2.5 MB in the fixed jar too, which caches 1 of those pages, so the difference does not measure the cache. | `c6b60ff` corrects the runbook. The commit message stays, because changing it would rewrite every later commit. |
