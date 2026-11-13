# Runbook

How to build, run, check and recover Spring PetClinic. Every failure behavior below is covered by a test, named in the table. Dependencies and their status are in `docs/DEPENDENCIES.md`.

## Build and test

Requirements: JDK 17 or newer. Docker with Compose v2 for the MySQL and PostgreSQL tests.

| Task | Command |
|------|---------|
| Full Maven build: format check, nohttp check, all tests, coverage, jar | `./mvnw verify` |
| Full Gradle build | `./gradlew build` |
| Fix formatting | `./mvnw spring-javaformat:apply` |
| One test class | `./mvnw test -Dtest=OwnerControllerTests` |

`./mvnw verify` takes about one minute on 4 CPUs. Without Docker, `MySqlIntegrationTests` shows as skipped and `PostgresIntegrationTests` is not counted at all (observed before Docker was installed on the review machine). `PostgresOutageIntegrationTests` uses the same Testcontainers switch as the MySQL tests, so it should show as skipped too. The build still passes with fewer tests, so check the test count when it matters: 116 tests run with Docker.

The nohttp check scans every file in the repository, including untracked ones (build output and `qa-results/` excepted), and fails on `http://` links other than localhost. Use `https://` links.

## Run locally

| Database | Start the database | Start the application |
|----------|--------------------|-----------------------|
| H2 in memory (default) | Nothing to start | `./mvnw spring-boot:run` |
| MySQL 9.7 | `docker compose up mysql` | `./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql` |
| PostgreSQL 18.4 | `docker compose up postgres` | `./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres` |

The application listens on port 8080. To run the jar instead: `java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar --spring.profiles.active=postgres`. Connection settings come from `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASS` and `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASS`.

Kubernetes: `kubectl apply -f k8s/` deploys PostgreSQL and the prebuilt `dsyer/petclinic` image, not a build of this repository (see F4 below).

## Health checks

| Endpoint | Meaning |
|----------|---------|
| `/actuator/health` | `200 {"groups":["liveness","readiness"],"status":"UP"}` when the application and its database work. `503` with `DOWN` when the database is unreachable. |
| `/livez`, `/readyz` | Kubernetes liveness and readiness. Return 404 unless `management.endpoint.health.probes.add-additional-paths=true`, which `k8s/petclinic.yml` sets. Readiness does not include the database (F2). |
| `/actuator/info` | Git commit and build information of the running jar. Only the Maven build adds it (F11). |
| `/actuator/sbom/application` | CycloneDX SBOM of the running jar. Use it to check which library versions are deployed. |

Logs go to standard output. The error page never shows exception details; the stack trace is in the log.

## Failure behavior

| Situation | What happens | Verified by |
|-----------|--------------|-------------|
| Database unreachable at startup (`mysql` or `postgres` profile) | Startup fails within seconds with `Connection refused` and the process exits, so a supervisor restarts it. | `DatabaseUnavailableAtStartupTests` |
| Database stops while the application runs | Pages that need the database show the generic error page (HTTP 500) without internal details. `/vets` keeps working from the cache if it was requested before the outage. `/actuator/health` returns 503. `/livez` and `/readyz` stay 200. When the database is back, pages and health recover without a restart. | `PostgresOutageIntegrationTests` |
| Malformed id or page number, such as `abc` or `2147483648` | HTTP 400 error page. | `UntrustedInputIntegrationTests` |
| Unknown owner or pet id | HTTP 500 error page without internal details. It should be 404 (F3). | `UntrustedInputIntegrationTests` |
| Page number out of range, including values up to `2147483647` | Redirect to page 1. On `/vets.html` the range is checked before the cached page query, so these requests add nothing to the cache (F15). | `UntrustedInputIntegrationTests`, `OwnerControllerTests`, `VetControllerTests` |
| A library with a known advisory resolves below its fixed version | The build fails. | `PatchedDependencyVersionsTests` |

## Check dependencies for known vulnerabilities

Install [OSV-Scanner](https://google.github.io/osv-scanner/), then:

```bash
./mvnw -B -DskipTests package
osv-scanner scan source -L pom.xml
osv-scanner scan source -L target/classes/META-INF/sbom/application.cdx.json
```

The first scan resolves the dependency tree from `pom.xml`, the second checks what the jar actually contains. Both report `No issues found` today. The same scan runs on every pull request (`.github/workflows/osv-scanner.yml`), and Dependabot proposes updates weekly.

## QA check on pull requests

`.github/workflows/qa.yml` runs the Droid QA skill on every pull request to `main`, except pull requests that change only `droid-wiki/`. Droid builds the branch, starts it on the runner with the in-memory H2 database, uses the pages and endpoints that the diff can affect, and keeps one QA report comment on the pull request up to date. A pull request that changes only documentation, CI or tests gets an INCONCLUSIVE report, because no application code changed.

- The check is informational. Do not make it a required status check.
- It needs the `FACTORY_API_KEY` repository secret. Without the secret, the report comment says that QA did not run. Pull requests from forks get no secrets, so the job is skipped for them. Dependabot pull requests read Dependabot secrets instead. Adding the key there lets QA test dependency updates, but the build and the application then run the updated libraries with the key in their environment (F21).
- The full report, screenshots and the server log are in the `qa-results-<run id>-<attempt>` artifact of the workflow run for 14 days.
- To run QA locally, start Droid in the repository and ask it to run the `qa` skill. Results go to `qa-results/<run id>/`, which Git and the nohttp check ignore.
- The flows and their pass criteria are in `.factory/skills/qa-web/SKILL.md` (pages, through a real browser) and `.factory/skills/qa-api/SKILL.md` (JSON and Actuator endpoints, through curl). Settings, such as video evidence, are in `.factory/skills/qa/config.yaml`.

## Wiki refresh

`.github/workflows/droid-wiki-refresh.yml` runs the Droid `/wiki` skill after every push to `main`. It writes the pages to `droid-wiki/` and opens one pull request from the `factory/droid-wiki` branch, which later runs update in place. A person reviews and merges that pull request. The workflow does not upload the wiki to Factory or to the GitHub wiki, so the pages exist only in the repository.

- It needs two secrets: `FACTORY_API_KEY`, the same key as the QA check, and `FACTORY_WIKI_PR_TOKEN`, a fine-grained personal access token for this repository with read and write access to contents and pull requests. Without `FACTORY_WIKI_PR_TOKEN`, the workflow opens the pull request with the built-in `GITHUB_TOKEN`, and GitHub then starts no other workflows for it, so the build checks do not run on the wiki pull request.
- Each run starts from the pages on `factory/droid-wiki` and rewrites only the pages whose code changed. The first run writes the whole wiki.
- The QA check skips pull requests that change only `droid-wiki/`. The Maven and Gradle builds still run, and their nohttp check fails on `http://` links in the pages, localhost excepted.
- Screenshots and the video overview are not committed.

## Upgrade Spring Boot to fix an advisory

1. Find the first Spring Boot release whose BOM manages fixed versions. The relevant BOM properties are `tomcat.version`, `spring-framework.version`, `jackson-bom.version`, `postgresql.version` and `log4j2.version`:

   ```bash
   curl -s https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom \
     | grep -E '<(tomcat|spring-framework|jackson-bom|postgresql|log4j2)\.version>'
   ```

2. Change the parent version in `pom.xml` and the `org.springframework.boot` plugin version in `build.gradle`. Keep both builds on the same version.
3. With Docker running, run `./mvnw verify` and `./gradlew build`. Both must pass with the full test count.
4. Run both scans above.
5. If the upgrade fixes new advisories, raise the minimums in `PatchedDependencyVersionsTests` and update the advisory table in `docs/DEPENDENCIES.md`.
6. Commit with `git commit -s`, open a pull request and wait for CI and a human review.

Prefer upgrading Spring Boot over overriding a single library version. If an override cannot wait, set the same BOM property in `pom.xml` and in `build.gradle` (`ext['tomcat.version'] = '...'`), and remove it at the next Spring Boot upgrade.

## Roll back

- Code: revert the upgrade commit with `git revert`. `PatchedDependencyVersionsTests` then fails on purpose, because the vulnerable versions come back. Roll back only with a recorded risk acceptance, and lower the minimums in the same change.
- Data: the Spring Boot 4.1.2 upgrade changes no schema or data scripts, so the database needs no rollback.
- Kubernetes: the manifest uses the untagged `dsyer/petclinic` image, so there is no earlier tag to return to (F4).

## Follow-ups

Found during the 2026-11-13 review and left open on purpose, because each one needs an owner decision or is outside the dependency fix. F15 to F20 were found later the same day while documenting the code. The owner approved fixing F15 in this pull request and keeping F16 to F20 open; F15 stays listed so that the IDs do not change. F21 and F22 come from the final review of the workflows and need an owner decision.

| ID | Issue | Evidence | Suggested change |
|----|-------|----------|------------------|
| F1 | Actuator exposes every endpoint without authentication, including `env`, `beans`, `threaddump` and `sbom`. | `management.endpoints.web.exposure.include=*`; live run. | Expose only `health,info` outside development, or add authentication. |
| F2 | Readiness does not include the database, so Kubernetes keeps routing traffic to a pod whose database is down. | `PostgresOutageIntegrationTests` | Add `db` to the readiness group, accepting that every pod turns unready during a database outage. |
| F3 | An unknown owner or pet id returns 500 instead of 404. | `UntrustedInputIntegrationTests` | Upstream [spring-projects/spring-petclinic#2688](https://github.com/spring-projects/spring-petclinic/pull/2688) (open) throws `ResponseStatusException(NOT_FOUND)`. |
| F4 | Kubernetes runs `dsyer/petclinic` with no tag and unknown provenance. The kind workflow tests that image, not the pull request. | `k8s/petclinic.yml`, `.github/workflows/deploy-and-test-cluster.yml` | Build the image from this repository in CI and deploy it by tag or digest. |
| F5 | The Kubernetes database has plain-text credentials in the manifest and no persistent volume. | `k8s/db.yml` | Use a managed secret and a PersistentVolumeClaim, or a managed database. |
| F6 | Database credentials in Kubernetes rely on Spring Cloud Bindings inside the image. An image built another way ignores `/bindings` and falls back to `localhost`. | `k8s/petclinic.yml`, no Spring Cloud Bindings in the build files | Keep building with buildpacks, or pass `POSTGRES_URL`, `POSTGRES_USER` and `POSTGRES_PASS` explicitly. |
| F7 | `docker-compose.yml` mounts `./conf.d` into MySQL, but the directory is not in the repository. Docker creates an empty, root-owned `conf.d` on the first `docker compose up mysql`. | Reproduced with `docker compose up mysql` | Remove the mount, or commit `conf.d/` with the intended settings. |
| F8 | The MySQL Compose service allows an empty root password, and both Compose services publish their ports (3306 and 5432) on all interfaces. | `docker-compose.yml` | Fine for local development only. Bind the ports to `127.0.0.1` if that matters. |
| F9 | Docker-based tests are skipped without Docker, so a passing build can run fewer tests. | Test counts with and without Docker | Fail the CI build when these tests are skipped. |
| F10 | `MysqlTestApplication` replaces the configured MySQL connection with a Testcontainers database whenever the `mysql` profile is active in a test. | `DatabaseUnavailableAtStartupTests` had to exclude it | Move the container bean into a `@TestConfiguration` that tests import explicitly. |
| F11 | Maven and Gradle builds differ. Actuator is `runtimeOnly` in Gradle, which also has fewer test starters. Gradle has no JaCoCo, git-commit-id or build-info setup, so it writes no coverage report and, inferred from `build.gradle`, its jar has no `git` or `build` section in `/actuator/info`. | `pom.xml`, `build.gradle` | Align the Gradle build with Maven. |
| F12 | The JCache customizer in `CacheConfiguration` never runs, so the cache statistics it promises are not enabled. | `/actuator/caches` reports `CaffeineCacheManager` | Configure Caffeine statistics directly, or add a JCache provider. |
| F13 | The repository has no CODEOWNERS file, and branch protection on the main branch could not be checked. | No admin access during the review | Require a review and the CI checks before merging to the main branch. |
| F14 | Font Awesome 4.7.0 is unmaintained, and the `css` profile uses the deprecated LibSass. | `pom.xml` | Plan a front-end dependency refresh. |
| F15 | Fixed in this pull request. `/vets.html` ran its cached page query before the range check, and the `vets` cache has no size limit or expiry, so every distinct page number added an entry that stayed in memory. The range is now checked first. | Run against the jar before the fix: 5,000 page numbers left 5,000 cached pages (about 3.4 MB). `UntrustedInputIntegrationTests.outOfRangeVetPagesAreNotCached` | Also give the cache a size limit (`spring.cache.caffeine.spec`), so that a future cached query cannot grow without bound. |
| F16 | The paging links on the owner list drop `lastName`, so page 2 of a search result lists all owners. | Run against the jar: a search that found six owners linked to `/owners?page=2`, which showed owners 6 to 10 of all owners. | Add `lastName` to the links in `src/main/resources/templates/owners/ownersList.html`. |
| F17 | On MySQL, when two requests add the same pet name at the same time, the second gets HTTP 500 instead of a form error. The MySQL unique constraint has no name, and `PetController` recognizes the violation only by the name `unique_owner_pet_name`. | Inferred from `src/main/resources/db/mysql/schema.sql` and `PetController`; no test runs this race on MySQL. | Name the MySQL constraint `unique_owner_pet_name`; existing databases need it renamed. |
| F18 | Owner search by last name ignores case on H2 but not on PostgreSQL. On MySQL it depends on the column collation. | Run against the jar: `davis` finds both Davis owners on H2 and none on PostgreSQL, where `Davis` finds both. | Decide the intended behavior and make the query match it, for example with an `IgnoreCase` query method. |
| F19 | Translation gaps. English labels are passed as fragment arguments in `src/main/resources/templates/pets/createOrUpdatePetForm.html` and `src/main/resources/templates/pets/createOrUpdateVisitForm.html`. The error codes `size` (`PetValidator`) and `mismatch` (`OwnerController`) have no message key. Two German messages contain replacement characters (`src/main/resources/messages/messages_de.properties`, lines 7 and 8). Controller flash messages are English. `I18nPropertiesSyncTest` checks only text between HTML tags. | Reading the files | Move these texts to message keys, fix the German file, and extend the test to fragment arguments and Java code. |
| F20 | Owner page defects. `OwnerController` sets an `error` flash message on paths that render the form instead of redirecting, so the message never shows. The owner id mismatch branch in `processUpdateOwnerForm` cannot be reached from a browser. The script in `src/main/resources/templates/owners/ownerDetails.html` throws a TypeError when the success or the error message is absent. | Run against the jar for the flash message; the other two from reading the code. | Remove the dead code or show the messages, and check for missing elements in the script. |
| F21 | `.github/workflows/qa.yml` runs Droid with `FACTORY_API_KEY` in its environment, and the commands Droid starts, including the Maven build and the application, inherit it. If the key is also a Dependabot secret, a dependency update runs the new library versions with the key available. `qa.yml` and `.github/workflows/droid-wiki-refresh.yml` install the latest Droid CLI with `curl -fsSL https://app.factory.ai/cli \| sh`, so the CLI version is not pinned. | Reading the workflows. In a local Droid session, the commands that Droid runs see `FACTORY_API_KEY` (variable names checked, not values). Not observed in a workflow run. | Add the key to Dependabot secrets only if QA on dependency updates is worth that exposure, and rotate the key if it may have leaked. Pin the CLI version if the installer allows it. |
| F22 | `.github/workflows/maven-build.yml`, `.github/workflows/gradle-build.yml` and `.github/workflows/deploy-and-test-cluster.yml` have no `permissions:` block, so their `GITHUB_TOKEN` gets the repository's default permissions. | Reading the workflows; the repository default was not checked | Add `permissions: contents: read` to each workflow. |
