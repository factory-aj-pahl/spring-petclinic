# Dependencies

This file lists what Spring PetClinic depends on at runtime, at build time and in CI, and how sure we are about each entry. It also records the known advisories that the Spring Boot 4.1.2 upgrade fixed and how to keep the list current.

Last reviewed: 2026-11-13, on Spring Boot 4.1.2.

## Status labels

| Label | Meaning |
|-------|---------|
| Confirmed | Found in code or configuration and exercised by a test or a live run. |
| Inferred | Follows from code or configuration, but nothing in this repository exercises it. |
| Unknown | The repository cannot answer it. Someone outside the code has to decide or find out. |

## Runtime services

| Dependency | Used when | Defined in | Status | Evidence |
|------------|-----------|------------|--------|----------|
| H2 2.4.240, in-memory | Default profile | `application.properties` (`database=h2`), `db/h2/*.sql` | Confirmed | Every `@SpringBootTest` test runs on it. |
| MySQL 9.7 | `mysql` profile | `application-mysql.properties`, `docker-compose.yml` service `mysql`, `db/mysql/*.sql` | Confirmed | `MySqlIntegrationTests` (Testcontainers `mysql:9.7`). `docker compose up mysql` plus the `mysql` profile serves `/owners`. |
| PostgreSQL 18.4 | `postgres` profile and Kubernetes | `application-postgres.properties`, `docker-compose.yml` service `postgres`, `k8s/db.yml`, `db/postgres/*.sql` | Confirmed | `PostgresIntegrationTests` (Docker Compose), `PostgresOutageIntegrationTests` (Testcontainers `postgres:18.4`). |
| Caffeine 3.2.4, in-process cache `vets` | Always | `@Cacheable("vets")` in `VetRepository` | Confirmed | `/actuator/caches` reports a `CaffeineCacheManager`. During a database outage `/vets` is still served from this cache. |
| Bootstrap 5.3.8 and Font Awesome 4.7.0 webjars | Always, served from the jar | `pom.xml` properties | Confirmed | Templates load them from `/webjars/`. No CDN links in the templates. |
| Spring Boot Actuator, all endpoints exposed | Always | `management.endpoints.web.exposure.include=*` | Confirmed | A live run serves `env`, `beans`, `threaddump`, `sbom` and the other endpoints without authentication. See the follow-ups in `docs/RUNBOOK.md`. |

Database connection settings come from environment variables or properties with local defaults:

| Profile | URL | User | Password |
|---------|-----|------|----------|
| `mysql` | `MYSQL_URL` (default `jdbc:mysql://localhost/petclinic`) | `MYSQL_USER` (default `petclinic`) | `MYSQL_PASS` (default `petclinic`) |
| `postgres` | `POSTGRES_URL` (default `jdbc:postgresql://localhost/petclinic`) | `POSTGRES_USER` (default `petclinic`) | `POSTGRES_PASS` (default `petclinic`) |

Both profiles run the schema and data scripts at every start (`spring.sql.init.mode=always`). The scripts are written to be idempotent.

Outbound network calls: none (Confirmed). `src/main` has no HTTP client, mail, messaging or other remote calls. The only network peer at runtime is the database.

## Deployment

| Item | Defined in | Status | Notes |
|------|------------|--------|-------|
| Kubernetes Deployment `petclinic`, image `dsyer/petclinic` with no tag | `k8s/petclinic.yml` | Unknown | No workflow builds or pushes this image. Who builds it, from which commit and with which dependency versions is unknown, so the 4.1.2 fix does not reach Kubernetes until the image is rebuilt. |
| Database credentials through service binding (`SERVICE_BINDING_ROOT=/bindings`, Secret `demo-db` of type `servicebinding.io/postgresql`) | `k8s/petclinic.yml`, `k8s/db.yml` | Inferred | Spring Boot does not read `/bindings` by itself. This works only if the image contains Spring Cloud Bindings, which Paketo buildpacks add during `./mvnw spring-boot:build-image`. Neither build file declares it. |
| PostgreSQL 18.4 Deployment `demo-db` | `k8s/db.yml` | Confirmed | No volume, so data is lost when the pod restarts. Credentials `user` / `pass` are in plain text in the manifest. |
| Probes `/livez` and `/readyz` | `k8s/petclinic.yml` | Confirmed | These paths exist only when `management.endpoint.health.probes.add-additional-paths=true`, which the manifest sets. The readiness group does not include the database (`PostgresOutageIntegrationTests`). |
| Container image build | `./mvnw spring-boot:build-image` | Inferred | There is no Dockerfile. The buildpack builder chooses the base image and JRE. |

## Build and test toolchain

| Item | Version | Defined in | Status | Notes |
|------|---------|------------|--------|-------|
| JDK | 17 or newer | `java.version` in `pom.xml`, toolchain in `build.gradle` | Confirmed | Builds and tests pass on JDK 17. |
| Maven | 3.9.16 (wrapper 3.3.4) | `.mvn/wrapper/maven-wrapper.properties` | Confirmed | |
| Gradle | 9.5.1 | `gradle/wrapper/gradle-wrapper.properties` | Confirmed | |
| Spring Boot parent and Gradle plugin | 4.1.2 | `pom.xml`, `build.gradle` | Confirmed | Manages almost every library version below. |
| Docker with Compose v2 | Any recent | Needed by `MySqlIntegrationTests`, `PostgresIntegrationTests`, `PostgresOutageIntegrationTests` | Confirmed | Without Docker these tests are skipped, not failed. |
| Checkstyle 12.3.1 with nohttp 0.0.11 | | `pom.xml`, `build.gradle` | Confirmed | Scans every file in the repository and fails the build on `http://` links other than localhost. |
| spring-javaformat 0.0.47 | | `pom.xml`, `build.gradle` | Confirmed | `./mvnw spring-javaformat:apply` fixes formatting. |
| JaCoCo 0.8.15 | | `pom.xml` | Confirmed | Coverage report in `target/site/jacoco`. |
| CycloneDX plugin | Boot managed (Maven), 3.2.4 (Gradle) | `pom.xml`, `build.gradle` | Confirmed | Writes `META-INF/sbom/application.cdx.json`, served at `/actuator/sbom`. |
| git-commit-id plugin | Boot managed | `pom.xml` | Confirmed | Feeds `/actuator/info`. |
| GraalVM native build tools | Boot managed (Maven), 1.1.2 (Gradle) | `pom.xml`, `build.gradle` | Inferred | Native image build is not exercised here. |
| libsass-maven-plugin | 0.3.4 | `css` profile in `pom.xml` | Inferred | Only for recompiling CSS. LibSass itself is deprecated upstream. |
| JMeter test plan | | `src/test/jmeter/petclinic_test_plan.jmx` | Inferred | Targets `PETCLINIC_HOST:PETCLINIC_PORT` (default `localhost:8080`). Not run here. |

## CI and developer environments

| Item | Defined in | Status | Notes |
|------|------------|--------|-------|
| GitHub Actions `actions/checkout@v4`, `actions/setup-java@v4` (Temurin JDK 17), `gradle/actions/setup-gradle@v4`, `helm/kind-action@v1` | `.github/workflows/*.yml` | Confirmed | The kind workflow runs only when `k8s/**` changes, and it deploys the Docker Hub image, not the code under review. |
| OSV-Scanner action, Dependabot | `.github/workflows/osv-scanner.yml`, `.github/dependabot.yml` | Confirmed | Keep this file current, see below. |
| DCO sign-off | `.github/dco.yml` | Confirmed | Every commit needs a `Signed-off-by` trailer. |
| Dev container | `.devcontainer/devcontainer.json` | Inferred | Uses Java 21 (Oracle) while CI uses 17. Not exercised here. |
| Gitpod | `.gitpod.yml`, `.devcontainer/Dockerfile` | Inferred | Based on `mcr.microsoft.com/vscode/devcontainers/java:0-17-bullseye` with Java 17.0.7. Not exercised here. |

## Libraries

Direct dependencies and the versions Maven resolves with Spring Boot 4.1.2. The full resolved list (about 170 artifacts) comes from `./mvnw dependency:list`, and the runtime SBOM from `target/classes/META-INF/sbom/application.cdx.json`.

| Library | Version | Scope | Notes |
|---------|---------|-------|-------|
| spring-webmvc (via `spring-boot-starter-webmvc`) | 7.0.10 | compile | |
| tomcat-embed-core | 11.0.27 | compile | |
| hibernate-core (via `spring-boot-starter-data-jpa`) | 7.4.13.Final | compile | |
| spring-data-jpa | 4.1.2 | compile | |
| HikariCP | 7.0.2 | compile | |
| thymeleaf, thymeleaf-spring6 | 3.1.5.RELEASE | compile | |
| jackson-core, jackson-databind (`tools.jackson`) | 3.1.7 | compile | |
| logback-classic | 1.5.38 | compile | The logging backend. |
| log4j-api (via `log4j-to-slf4j`) | 2.25.5 | compile | Only bridges Log4j API calls to SLF4J. |
| micrometer-core | 1.17.2 | compile | |
| h2 | 2.4.240 | runtime | |
| mysql-connector-j | 9.7.0 | runtime | |
| postgresql | 42.7.14 | runtime | Brings in checker-qual 3.55.1. |
| caffeine | 3.2.4 | runtime | |
| javax.cache:cache-api | 1.1.1 | compile | No JCache provider is on the classpath, so Spring Boot uses Caffeine directly and the JCache customizer in `CacheConfiguration` never runs. Cache statistics are therefore not enabled. |
| jakarta.xml.bind-api | 4.0.5 | compile | |
| webjars-locator-lite, bootstrap, font-awesome | 1.1.3, 5.3.8, 4.7.0 | runtime | Font Awesome 4.7.0 dates from 2016 and is no longer maintained. |
| spring-boot-devtools | 4.1.2 | optional | Active under `./mvnw spring-boot:run`, not packaged in the jar. |

Maven and Gradle declare slightly different scopes (Confirmed by comparing the two files). Gradle puts Actuator in `runtimeOnly` and has fewer test starters. Both builds pass the same tests today, but the drift can surface when tests start using those classes directly.

## Advisories fixed by the Spring Boot 4.1.2 upgrade

Spring Boot 4.1.0 managed six libraries with 20 published advisories (7 critical, 7 high, 6 moderate). Spring Boot 4.1.1 still left 11 open (Tomcat 11.0.24, Jackson 3.1.5). 4.1.2 is the first release that fixes all of them. Three scans agree: OSV-Scanner on the SBOM, OSV-Scanner on `pom.xml`, and the OSV.dev API on the full resolved list. All went from 20 findings to 0.

The reachability column is Inferred from reading the code and configuration. Nothing here was exploit-tested. "Unknown" means the advisory text does not say enough to rule the application in or out.

| Package | Before → after | Advisory | CVE | Severity | First fixed | Reachability |
|---------|----------------|----------|-----|----------|-------------|--------------|
| tomcat-embed-core | 11.0.22 → 11.0.27 | [GHSA-9xv2-5v5q-p794](https://github.com/advisories/GHSA-9xv2-5v5q-p794) | CVE-2026-65905 | Critical 9.8 | 11.0.25 | Not reachable: DIGEST authentication is not configured. |
| tomcat-embed-core | | [GHSA-f525-44xv-f2qj](https://github.com/advisories/GHSA-f525-44xv-f2qj) | CVE-2026-65637 | Critical 9.8 | 11.0.25 | Not reachable by default: HTTP/2 and TLS are off. |
| tomcat-embed-core | | [GHSA-gcx9-497g-6cp6](https://github.com/advisories/GHSA-gcx9-497g-6cp6) | CVE-2026-65182 | Critical 9.1 | 11.0.25 | Unknown |
| tomcat-embed-core | | [GHSA-h3x4-894j-xpx5](https://github.com/advisories/GHSA-h3x4-894j-xpx5) | CVE-2026-68525 | Critical 9.1 | 11.0.25 | Not reachable: FORM authentication is not configured. |
| tomcat-embed-core | | [GHSA-mqg3-r7h5-24x4](https://github.com/advisories/GHSA-mqg3-r7h5-24x4) | CVE-2026-55276 | Critical 9.1 | 11.0.23 | Unknown |
| tomcat-embed-core | | [GHSA-h792-v28v-ppgr](https://github.com/advisories/GHSA-h792-v28v-ppgr) | CVE-2026-53404 | High 7.3 | 11.0.23 | Not reachable: no rewrite valve. |
| tomcat-embed-core | | [GHSA-5v3h-fjv2-54fg](https://github.com/advisories/GHSA-5v3h-fjv2-54fg) | CVE-2026-55956 | Moderate 6.5 | 11.0.23 | Unknown |
| spring-webmvc | 7.0.8 → 7.0.10 | [GHSA-j9f9-w8pj-32f8](https://github.com/advisories/GHSA-j9f9-w8pj-32f8) | CVE-2026-47890 | Critical 9.8 | 7.0.9 | Not reachable: no Server-Sent Events. |
| spring-webmvc | | [GHSA-pc63-qcmh-9cmg](https://github.com/advisories/GHSA-pc63-qcmh-9cmg) | CVE-2026-47884 | Critical 9.8 | 7.0.9 | Not reachable: no `XsltView`. |
| jackson-core | 3.1.4 → 3.1.7 | [GHSA-7hhh-6rmp-j9qf](https://github.com/advisories/GHSA-7hhh-6rmp-j9qf) | CVE-2026-89425 | High 7.5 | 3.1.7 | Unlikely: affects parsing from a `DataInput`, which the application does not do. |
| jackson-core | | [GHSA-p6pp-m3f8-5c89](https://github.com/advisories/GHSA-p6pp-m3f8-5c89) | CVE-2026-89407 | High 7.5 | 3.1.7 | Unknown. Actuator write endpoints accept JSON request bodies without authentication. |
| jackson-databind | 3.1.4 → 3.1.7 | [GHSA-cxp5-3px4-pw24](https://github.com/advisories/GHSA-cxp5-3px4-pw24) | CVE-2026-91777 | High 7.5 | 3.1.7 | Unknown, as above. |
| jackson-databind | | [GHSA-q4xh-88c3-wmh7](https://github.com/advisories/GHSA-q4xh-88c3-wmh7) | CVE-2026-68497 | High 7.5 | 3.1.6 | Unknown, as above. |
| jackson-databind | | [GHSA-wv8q-qhhj-9h54](https://github.com/advisories/GHSA-wv8q-qhhj-9h54) | CVE-2026-91776 | High 7.5 | 3.1.7 | Unknown, as above. |
| jackson-databind | | [GHSA-5gvw-p9qm-jgwh](https://github.com/advisories/GHSA-5gvw-p9qm-jgwh) | CVE-2026-59889 | Moderate 6.5 | 3.1.5 | Not reachable: no `@JsonView`. |
| jackson-databind | | [GHSA-gx83-3vf8-gh7j](https://github.com/advisories/GHSA-gx83-3vf8-gh7j) | CVE-2026-83557 | Moderate 5.6 | 3.1.6 | Not reachable: default typing is not enabled. |
| jackson-databind | | [GHSA-vvgp-rfg2-7rr6](https://github.com/advisories/GHSA-vvgp-rfg2-7rr6) | CVE-2026-77310 | Moderate 5.3 | 3.1.5 | Unknown |
| jackson-databind | | [GHSA-wjgm-6hv5-3cvf](https://github.com/advisories/GHSA-wjgm-6hv5-3cvf) | CVE-2026-19032 | Moderate 5.3 | 3.1.6 | Unknown |
| postgresql | 42.7.11 → 42.7.14 | [GHSA-j92g-9f8w-j867](https://github.com/advisories/GHSA-j92g-9f8w-j867) | CVE-2026-54291 | High 8.2 | 42.7.12 | Unknown. The driver uses TLS whenever the server offers it; the PostgreSQL containers in this repository do not. |
| log4j-api | 2.25.4 → 2.25.5 | [GHSA-qv9r-c865-cp47](https://github.com/advisories/GHSA-qv9r-c865-cp47) | CVE-2026-49844 | Moderate 6.3 | 2.25.5 | Not reachable: logging goes through Logback, and the code uses no `MapMessage`. |

About half of the advisories are likely unreachable in the default configuration. The other half cannot be ruled out, including two critical Tomcat ones, and the upgrade stays on the 4.1.x patch line without code changes. That is why all 20 were fixed instead of triaging each one.

## Keeping this current

- `PatchedDependencyVersionsTests` fails the build if any of the six libraries above resolves below its first fixed version. When a new advisory is fixed, add or raise the minimum there and add the advisory to the table above.
- Dependabot (`.github/dependabot.yml`) opens weekly pull requests for Maven, Gradle and GitHub Actions updates.
- The OSV-Scanner workflow scans every pull request. To scan locally, see "Check dependencies for known vulnerabilities" in `docs/RUNBOOK.md`.
- Prefer upgrading the Spring Boot parent over overriding single library versions. If an override is unavoidable, set the same Boot version property in both `pom.xml` (`<properties>`) and `build.gradle` (`ext['...']`), and remove the override after the next Boot upgrade.
