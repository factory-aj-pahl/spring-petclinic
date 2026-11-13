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

`./mvnw verify` takes about one minute on 4 CPUs. Without Docker, the MySQL tests show as skipped and the PostgreSQL tests are not counted at all, so the build still passes with fewer tests. Check the test count when it matters: 114 tests run with Docker.

The nohttp check scans every file in the repository, including untracked ones, and fails on `http://` links other than localhost. Use `https://` links.

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
| `/actuator/health` | `200 {"status":"UP"}` when the application and its database work. `503` with `DOWN` when the database is unreachable. |
| `/livez`, `/readyz` | Kubernetes liveness and readiness. Return 404 unless `management.endpoint.health.probes.add-additional-paths=true`, which `k8s/petclinic.yml` sets. Readiness does not include the database (F2). |
| `/actuator/info` | Git commit and build information of the running jar. |
| `/actuator/sbom/application` | CycloneDX SBOM of the running jar. Use it to check which library versions are deployed. |

Logs go to standard output. The error page never shows exception details; the stack trace is in the log.

## Failure behavior

| Situation | What happens | Verified by |
|-----------|--------------|-------------|
| Database unreachable at startup (`mysql` or `postgres` profile) | Startup fails within seconds with `Connection refused` and the process exits, so a supervisor restarts it. | `DatabaseUnavailableAtStartupTests` |
| Database stops while the application runs | Pages that need the database show the generic error page (HTTP 500) without internal details. `/vets` keeps working from the cache. `/actuator/health` returns 503. `/livez` and `/readyz` stay 200. When the database is back, pages and health recover without a restart. | `PostgresOutageIntegrationTests` |
| Malformed id or page number, such as `abc` or `2147483648` | HTTP 400 error page. | `UntrustedInputIntegrationTests` |
| Unknown owner or pet id | HTTP 500 error page without internal details. It should be 404 (F3). | `UntrustedInputIntegrationTests` |
| Page number out of range, including values up to `2147483647` | Redirect to page 1. | `UntrustedInputIntegrationTests`, `OwnerControllerTests`, `VetControllerTests` |
| A library with a known advisory resolves below its fixed version | The build fails. | `PatchedDependencyVersionsTests` |

## Check dependencies for known vulnerabilities

Install [OSV-Scanner](https://google.github.io/osv-scanner/), then:

```bash
./mvnw -B -DskipTests package
osv-scanner scan source -L pom.xml
osv-scanner scan source -L target/classes/META-INF/sbom/application.cdx.json
```

The first scan resolves the dependency tree from `pom.xml`, the second checks what the jar actually contains. Both report `No issues found` today. The same scan runs on every pull request (`.github/workflows/osv-scanner.yml`), and Dependabot proposes updates weekly.

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

Found during the 2026-11-13 review and left open on purpose, because each one needs an owner decision or is outside the dependency fix.

| ID | Issue | Evidence | Suggested change |
|----|-------|----------|------------------|
| F1 | Actuator exposes every endpoint without authentication, including `env`, `beans`, `threaddump` and `sbom`. | `management.endpoints.web.exposure.include=*`; live run. | Expose only `health,info` outside development, or add authentication. |
| F2 | Readiness does not include the database, so Kubernetes keeps routing traffic to a pod whose database is down. | `PostgresOutageIntegrationTests` | Add `db` to the readiness group, accepting that every pod turns unready during a database outage. |
| F3 | An unknown owner or pet id returns 500 instead of 404. | `UntrustedInputIntegrationTests` | Upstream [spring-projects/spring-petclinic#2688](https://github.com/spring-projects/spring-petclinic/pull/2688) (open) throws `ResponseStatusException(NOT_FOUND)`. |
| F4 | Kubernetes runs `dsyer/petclinic` with no tag and unknown provenance. The kind workflow tests that image, not the pull request. | `k8s/petclinic.yml`, `.github/workflows/deploy-and-test-cluster.yml` | Build the image from this repository in CI and deploy it by tag or digest. |
| F5 | The Kubernetes database has plain-text credentials (`user` / `pass`) and no persistent volume. | `k8s/db.yml` | Use a managed secret and a PersistentVolumeClaim, or a managed database. |
| F6 | Database credentials in Kubernetes rely on Spring Cloud Bindings inside the image. An image built another way ignores `/bindings` and falls back to `localhost`. | `k8s/petclinic.yml`, no Spring Cloud Bindings in the build files | Keep building with buildpacks, or pass `POSTGRES_URL`, `POSTGRES_USER` and `POSTGRES_PASS` explicitly. |
| F7 | `docker-compose.yml` mounts `./conf.d` into MySQL, but the directory is not in the repository. Docker creates an empty, root-owned `conf.d` on the first `docker compose up mysql`. | Reproduced with `docker compose up mysql` | Remove the mount, or commit `conf.d/` with the intended settings. |
| F8 | The MySQL Compose service allows an empty root password and publishes port 3306 on all interfaces. | `docker-compose.yml` | Fine for local development only. Bind to `127.0.0.1` if that matters. |
| F9 | Docker-based tests are skipped without Docker, so a passing build can run fewer tests. | Test counts with and without Docker | Fail the CI build when these tests are skipped. |
| F10 | `MysqlTestApplication` replaces the configured MySQL connection with a Testcontainers database whenever the `mysql` profile is active in a test. | `DatabaseUnavailableAtStartupTests` had to exclude it | Move the container bean into a `@TestConfiguration` that tests import explicitly. |
| F11 | Maven and Gradle declare different scopes: Actuator is `runtimeOnly` in Gradle, which also has fewer test starters. | `pom.xml`, `build.gradle` | Align the Gradle dependencies with Maven. |
| F12 | The JCache customizer in `CacheConfiguration` never runs, so the cache statistics it promises are not enabled. | `/actuator/caches` reports `CaffeineCacheManager` | Configure Caffeine statistics directly, or add a JCache provider. |
| F13 | The repository has no CODEOWNERS file, and branch protection on the main branch could not be checked. | No admin access during the review | Require a review and the CI checks before merging to the main branch. |
| F14 | Font Awesome 4.7.0 is unmaintained, and the `css` profile uses the deprecated LibSass. | `pom.xml` | Plan a front-end dependency refresh. |
