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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.test.context.aot.DisabledInAotMode;

/**
 * The application must refuse to start, rather than start half-working, when its database
 * cannot be reached. A failed start ends the process, so a supervisor such as Kubernetes
 * restarts it and the failure is visible. Uses the same environment variables as the
 * Kubernetes and Docker setups to point at a port where nothing listens.
 */
@DisabledInNativeImage
@DisabledInAotMode
class DatabaseUnavailableAtStartupTests {

	@ParameterizedTest(name = "{0} profile")
	@CsvSource({ "mysql, MYSQL_URL, jdbc:mysql://127.0.0.1:%d/petclinic",
			"postgres, POSTGRES_URL, jdbc:postgresql://127.0.0.1:%d/petclinic" })
	void startupFailsFastWhenTheDatabaseIsUnreachable(String profile, String urlVariable, String urlTemplate)
			throws IOException {
		SpringApplication application = new SpringApplication(PetClinicApplication.class);
		// MysqlTestApplication is on the test classpath and swaps in a Testcontainers
		// database whenever the mysql profile is active, which would hide the outage.
		application.addInitializers((context) -> context.getBeanFactory()
			.registerSingleton("excludeMysqlTestApplication", new ExcludeMysqlTestApplication()));
		String url = urlTemplate.formatted(unusedPort());
		long start = System.nanoTime();

		assertThatThrownBy(() -> application.run("--spring.profiles.active=" + profile, "--" + urlVariable + "=" + url,
				"--server.port=0", "--spring.docker.compose.enabled=false"))
			.hasRootCauseInstanceOf(ConnectException.class);

		// Well under HikariCP's 30 second connection timeout: a refused connection ends
		// startup at once instead of waiting for the database to appear.
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(20));
	}

	private static int unusedPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	static class ExcludeMysqlTestApplication extends TypeExcludeFilter {

		@Override
		public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
			return MysqlTestApplication.class.getName().equals(metadataReader.getClassMetadata().getClassName());
		}

	}

}
