---
name: qa-api
description: >
  QA tests for PetClinic's HTTP endpoints: the vets JSON and XML resource, Actuator health and
  info, and the runtime SBOM that shows which library versions the build really ships. Uses curl
  against the branch running on this machine.
---

# QA: PetClinic HTTP endpoints

## Testing Target

PetClinic has no preview deployments and no hosted environments. Test the branch's own code on a local server.

If the QA orchestrator passed the base URL of a server it started from this branch, use it and skip to "Authentication". Otherwise start one from the repository root. Shell variables do not carry over between commands, because each command can run in a new shell, so write the literal values into later commands: below, `<RUN_ID>`, `<PORT>`, `<PID>` and `<BASE_URL>` stand for them.

1. Create the evidence directory: `RUN_ID="$(date +%s)"; mkdir -p "qa-results/$RUN_ID" && echo "$RUN_ID"`.
2. Build: `./mvnw -B -q -DskipTests -Dspring-javaformat.skip=true -Dcheckstyle.skip=true package`. It skips the format and nohttp checks, which CI runs separately.
3. Pick a free port: 8080, or another such as 18080. A port is free when `curl -s -o /dev/null http://localhost:<PORT>/` cannot connect.
4. Start the server as a background job that keeps running after the command returns, and note its process ID. In Droid, run this through the Execute tool with `fireAndForget: true`, which returns the process ID; a job started with a plain `&` is stopped when its command ends.
   ```bash
   exec java -jar target/spring-petclinic-*.jar --server.port=<PORT> > qa-results/<RUN_ID>/app.log 2>&1
   ```
5. Poll `curl -fsS http://localhost:<PORT>/actuator/health` every 2 seconds, for up to 120 seconds, until it contains `"status":"UP"`. If the process exits meanwhile (`kill -0 <PID>` fails), the start failed: read `app.log`.
6. Check that `git.commit.id` in `http://localhost:<PORT>/actuator/info` is the start of `git rev-parse HEAD`, so you know the server runs this checkout.
7. `<BASE_URL>` is `http://localhost:<PORT>`.

NEVER fall back to any other URL: no other server runs this branch's code. If the server does not start, report every API flow as BLOCKED and include the last 50 lines of `app.log`. Stop the server you started when you finish: `kill <PID>`.

## Authentication

None. Every endpoint is public, so there are no tokens, secrets or environment variables to set, in CI or locally.

## How to test

Use `curl` and `jq` (or `python3 -m json.tool`) from the shell. For every check, record in the evidence the exact command, the HTTP status and the relevant part of the body, for example with `curl -sS -w '\nHTTP %{http_code} %{content_type}\n' ...`. Compare versions numerically, part by part (11.0.27 is newer than 11.0.3).

## Flow menu

The orchestrator picks the flows relevant to the diff. "Covers" lists the code each flow exercises. A change to `pom.xml` covers every flow.

### A1. Vets as JSON and XML

Covers: `vet/VetController.java` (`/vets`), `vet/Vets.java`, `vet/Vet.java`, `vet/Specialty.java`, `model/**`, and the JSON and XML libraries in `pom.xml`.

1. `curl -sS -H 'Accept: application/json' <BASE_URL>/vets`. **Pass:** HTTP 200, `Content-Type: application/json`, and a `vetList` array of 6 vets, each with `id`, `firstName`, `lastName` and `specialties`. James Carter has no specialties; Helen Leary has radiology.
2. `curl -sS -H 'Accept: application/xml' <BASE_URL>/vets`. **Pass:** HTTP 200, `Content-Type: application/xml`, a `<vets>` root element and 6 `<vetList>` elements.
3. Negative: `curl -sS -o /dev/null -w '%{http_code}' -H 'Accept: text/csv' <BASE_URL>/vets`. **Pass:** 406.

### A2. Health and build info

Covers: `application.properties` (Actuator settings), `system/**`, and the Spring Boot version in `pom.xml`.

1. `<BASE_URL>/actuator/health`. **Pass:** HTTP 200 and `"status":"UP"`, with the `liveness` and `readiness` groups listed.
2. `<BASE_URL>/actuator/health/liveness` and `<BASE_URL>/actuator/health/readiness`. **Pass:** HTTP 200 and `"status":"UP"` for both.
3. `<BASE_URL>/actuator/info`. **Pass:** `build.artifact` is `spring-petclinic`, `build.version` matches `<version>` in `pom.xml`, and `git.commit.id` is the start of `git rev-parse HEAD` for the checkout you built.
4. Negative: `curl -sS -o /dev/null -w '%{http_code}' -X POST <BASE_URL>/actuator/shutdown`. **Pass:** 404. Nobody can stop the application over HTTP.

### A3. Runtime SBOM and patched library versions

Covers: dependency versions in `pom.xml` (the Spring Boot parent manages most of them), and the CycloneDX plugin configuration.

1. `<BASE_URL>/actuator/sbom`. **Pass:** `{"ids":["application"]}`.
2. `<BASE_URL>/actuator/sbom/application`. **Pass:** HTTP 200, `Content-Type: application/vnd.cyclonedx+json`, `"bomFormat": "CycloneDX"`, and about a hundred components.
3. Read the versions of the libraries below and compare each with the first version that fixes its advisories (listed in `docs/DEPENDENCIES.md`). **Pass:** every one is at or above its minimum.

   The build enforces the same minimums in the `@CsvSource` of `src/test/java/org/springframework/samples/petclinic/PatchedDependencyVersionsTests.java`. Read that file first. If it lists other libraries or other minimums than this table, test against the file and suggest a skill update.

   | Component | Minimum |
   | --- | --- |
   | `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.25 |
   | `org.springframework:spring-webmvc` | 7.0.9 |
   | `tools.jackson.core:jackson-databind` | 3.1.7 |
   | `tools.jackson.core:jackson-core` | 3.1.7 |
   | `org.postgresql:postgresql` | 42.7.12 |
   | `org.apache.logging.log4j:log4j-api` | 2.25.5 |

   ```bash
   curl -sS <BASE_URL>/actuator/sbom/application \
     | jq -r '.components[] | select(.name | test("^(tomcat-embed-core|spring-webmvc|jackson-databind|jackson-core|postgresql|log4j-api)$")) | "\(.group):\(.name) \(.version)"'
   ```
4. When the diff changes a dependency version, also check that the SBOM shows the new version. This proves the running server ships what the build files say.
5. Negative: `<BASE_URL>/actuator/sbom/does-not-exist`. **Pass:** 404.

## Known Failure Modes

1. **Actuator exposes every endpoint without authentication.** This is known issue F1 in `docs/RUNBOOK.md`. Do not report it as a new failure unless the diff changes `management.endpoints.web.exposure.include` or adds security.
2. **Readiness ignores the database.** Readiness stays UP when the database is unreachable (known issue F2). Do not test a database outage here.
3. **Error bodies are JSON without an HTML `Accept` header.** curl gets `{"status":500,"error":"Internal Server Error",...}` where a browser gets the HTML error page. That is expected.
4. **`build.time` is old.** It is the fixed `project.build.outputTimestamp` from `pom.xml` (reproducible builds), not the time of the build.
5. **`/actuator/sbom/application` returns 404.** The SBOM is generated during the Maven build. A server started from an IDE or from stale classes may not have it. Rebuild with the command in Testing Target; if it is still missing, report A3 as BLOCKED.
6. **Port 8080 already in use.** Another server, possibly running different code, holds the port. Start this branch on another free port; never test the existing server.
7. **Server gone before the first flow.** A server started with a plain `&` is stopped when its command returns. Start it as described in Testing Target.
