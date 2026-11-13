# AGENTS.md

Instructions for coding agents working in this repository. People can use them too. The
[README](README.md) explains the application, [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) lists
every external dependency, and [docs/RUNBOOK.md](docs/RUNBOOK.md) covers operations.

## Project

- Spring Boot 4.1 web application built with Spring MVC, Thymeleaf and Spring Data JPA on Java 17.
- Maven (`pom.xml`) is the main build. Gradle (`build.gradle`) must stay in step with it: when you
  change a dependency or plugin version in one file, change it in the other.
- The default database is in-memory H2. The `mysql` and `postgres` Spring profiles use the services
  in `docker-compose.yml`. Schema and seed data live in `src/main/resources/db/<database>/`.
- Code is grouped by feature under `src/main/java/org/springframework/samples/petclinic/`:
  `owner`, `vet`, `system` and `model`.

## Commands

Use JDK 17. CI builds with Temurin 17.

| Task | Command |
| :--- | :--- |
| Build and run every test | `./mvnw verify` |
| Run one test class | `./mvnw test -Dtest=OwnerControllerTests` |
| Fix Java formatting | `./mvnw spring-javaformat:apply` |
| Build with Gradle | `./gradlew build` |
| Run the application on port 8080 | `./mvnw spring-boot:run` |
| Test the Droid hook | `python3 -B -m unittest discover -s .factory/hooks` |
| Scan dependencies for known vulnerabilities | See [the runbook](docs/RUNBOOK.md#check-dependencies-for-known-vulnerabilities) |

`./mvnw verify` takes about a minute. `MySqlIntegrationTests`, `PostgresIntegrationTests` and
`PostgresOutageIntegrationTests` need Docker and are skipped without it. When you report results,
say which tests were skipped instead of counting them as passed.

## Checks that fail the build

- Formatting: the `validate` phase runs `spring-javaformat:validate`. Run
  `./mvnw spring-javaformat:apply` before you commit Java changes.
- Plain HTTP links: the nohttp Checkstyle rule rejects `http://` URLs in the files it scans,
  Markdown included. Use `https://`.
- Translations: `I18nPropertiesSyncTest` fails when a template or controller contains hard-coded
  text instead of a message key, or when a key is missing from one of the `messages*.properties`
  files.
- Patched versions: `PatchedDependencyVersionsTests` fails when a library resolves below the first
  version that fixed a known advisory. Do not lower those minimums to get another change through.

## Making changes

- For a bug fix, first write a test that fails, then fix the code.
- When you add, remove or upgrade a dependency or an external service, update
  `docs/DEPENDENCIES.md`. When operations change, update `docs/RUNBOOK.md` too.
- Sign off every commit with `git commit -s`. The repository requires a Developer Certificate of
  Origin sign-off (`.github/dco.yml`).
- Keep each commit to one purpose and explain the reason in the message.

## What agents must not do

People merge, release and deploy. Agents push a feature branch and open a pull request for review.
Do not:

- merge or approve pull requests, push to `main`, force-push, or push tags;
- publish releases, container images or Maven artifacts;
- run commands that change a Kubernetes cluster or a Helm release. The `deploy-and-test-cluster`
  workflow applies `k8s/` to a temporary kind cluster in CI.

For Droid, `.factory/hooks.json` enforces these rules: `.factory/hooks/guard_release_actions.py`
blocks the commands before they run. The hook matches command text, so it is a guardrail and not a
security boundary. Branch protection on `main` is still needed.
