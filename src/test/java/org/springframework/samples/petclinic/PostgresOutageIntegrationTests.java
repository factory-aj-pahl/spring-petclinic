/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

/**
 * Stops the PostgreSQL container under a running application, checks what users and the
 * Kubernetes probes see during the outage, then starts the database again and checks that
 * the application recovers without a restart.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "spring.datasource.hikari.connection-timeout=2000",
				"management.endpoint.health.probes.enabled=true",
				"management.endpoint.health.probes.add-additional-paths=true" })
@ActiveProfiles("postgres")
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
@DisabledInNativeImage
@DisabledInAotMode
class PostgresOutageIntegrationTests {

	// A restarted container gets a new random host port, which would no longer match the
	// application's JDBC URL, so the host port is fixed for this test.
	private static final int HOST_PORT = unusedPort();

	@Container
	static GenericContainer<?> postgres = new GenericContainer<>(DockerImageName.parse("postgres:18.4"))
		.withEnv("POSTGRES_USER", "petclinic")
		.withEnv("POSTGRES_PASSWORD", "petclinic")
		.withEnv("POSTGRES_DB", "petclinic")
		.withExposedPorts(5432)
		.withCreateContainerCmdModifier((cmd) -> cmd.getHostConfig()
			.withPortBindings(new PortBinding(Ports.Binding.bindPort(HOST_PORT), ExposedPort.tcp(5432))))
		.waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));

	@DynamicPropertySource
	static void postgresUrl(DynamicPropertyRegistry registry) {
		registry.add("POSTGRES_URL", () -> "jdbc:postgresql://localhost:" + HOST_PORT + "/petclinic");
	}

	@Autowired
	private TestRestTemplate rest;

	@Test
	void outageIsReportedWithoutInternalDetailsAndTheApplicationRecovers() {
		assertThat(getHtml("/owners/1").getBody()).contains("George Franklin");
		assertThat(status("/vets")).isEqualTo(HttpStatus.OK);
		assertThat(status("/actuator/health")).isEqualTo(HttpStatus.OK);

		DockerClient docker = DockerClientFactory.instance().client();
		docker.stopContainerCmd(postgres.getContainerId()).exec();

		ResponseEntity<String> ownerPage = getHtml("/owners/1");
		assertThat(ownerPage.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(ownerPage.getBody()).contains("Something happened...")
			.doesNotContain("jdbc:", "SQLException", "HikariPool", "Caused by", "\tat ");
		assertThat(status("/vets")).as("vets list served from the cache").isEqualTo(HttpStatus.OK);
		assertThat(status("/actuator/health")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(status("/livez")).isEqualTo(HttpStatus.OK);
		// The readiness group does not include the database, so Kubernetes keeps routing
		// traffic to the pod during an outage. docs/RUNBOOK.md tracks this gap.
		assertThat(status("/readyz")).isEqualTo(HttpStatus.OK);

		docker.startContainerCmd(postgres.getContainerId()).exec();
		Awaitility.await()
			.atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofMillis(500))
			.untilAsserted(() -> assertThat(status("/actuator/health")).isEqualTo(HttpStatus.OK));
		ResponseEntity<String> recovered = getHtml("/owners/1");
		assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(recovered.getBody()).contains("George Franklin");
	}

	private HttpStatusCode status(String path) {
		return this.rest.getForEntity(path, String.class).getStatusCode();
	}

	private ResponseEntity<String> getHtml(String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.TEXT_HTML));
		return this.rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private static int unusedPort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
