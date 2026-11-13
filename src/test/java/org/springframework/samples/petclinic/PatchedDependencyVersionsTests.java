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

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Fails the build if a library with a published advisory resolves below the first version
 * that fixes it. Versions are read from the jar manifests on the test runtime classpath,
 * so the check sees what Maven or Gradle actually resolved, including versions managed by
 * the Spring Boot BOM. The advisories behind each minimum are listed in
 * {@code docs/DEPENDENCIES.md}.
 */
class PatchedDependencyVersionsTests {

	@ParameterizedTest(name = "{0} >= {2}")
	@CsvSource({ "tomcat-embed-core, org.apache.catalina.startup.Tomcat, 11.0.25",
			"spring-webmvc, org.springframework.web.servlet.DispatcherServlet, 7.0.9",
			"jackson-databind, tools.jackson.databind.ObjectMapper, 3.1.7",
			"jackson-core, tools.jackson.core.JsonParser, 3.1.7", "postgresql, org.postgresql.Driver, 42.7.12",
			"log4j-api, org.apache.logging.log4j.LogManager, 2.25.5" })
	void resolvedVersionIsAtLeastTheFirstPatchedVersion(String artifact, String className, String firstPatched)
			throws Exception {
		String resolved = implementationVersion(className);
		assertThat(compareVersions(resolved, firstPatched))
			.as("%s resolved to %s; %s is the first version without the known advisories", artifact, resolved,
					firstPatched)
			.isGreaterThanOrEqualTo(0);
	}

	@Test
	void versionsAreComparedNumerically() {
		assertThat(compareVersions("11.0.27", "11.0.25")).isPositive();
		assertThat(compareVersions("11.0.3", "11.0.25")).isNegative();
		assertThat(compareVersions("3.2.0", "3.1.7")).isPositive();
		assertThat(compareVersions("42.7.12", "42.7.12")).isZero();
		assertThat(compareVersions("2.25", "2.25.5")).isNegative();
	}

	private static String implementationVersion(String className) throws Exception {
		Class<?> type = Class.forName(className, false, PatchedDependencyVersionsTests.class.getClassLoader());
		URL location = type.getProtectionDomain().getCodeSource().getLocation();
		try (JarFile jar = new JarFile(new File(location.toURI()))) {
			String version = jar.getManifest().getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
			assertThat(version).as("Implementation-Version in %s", location).isNotBlank();
			return version;
		}
	}

	static int compareVersions(String left, String right) {
		int[] a = numericParts(left);
		int[] b = numericParts(right);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int difference = Integer.compare((i < a.length) ? a[i] : 0, (i < b.length) ? b[i] : 0);
			if (difference != 0) {
				return difference;
			}
		}
		return 0;
	}

	private static int[] numericParts(String version) {
		return Arrays.stream(version.split("\\D+"))
			.filter((part) -> !part.isEmpty())
			.mapToInt(Integer::parseInt)
			.toArray();
	}

}
