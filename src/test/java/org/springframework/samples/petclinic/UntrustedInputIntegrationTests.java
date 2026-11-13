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

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Sends malformed, unknown and out-of-range identifiers and page numbers through the
 * whole stack (Tomcat, Spring MVC, Spring Data JPA and the database) and checks that each
 * one gets a controlled response that does not expose internal details.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class UntrustedInputIntegrationTests {

	private static final String[] INTERNAL_DETAILS = { "Exception:", "Caused by", "\tat ", "java.lang.",
			"Failed to convert", "not found with id", "jdbc:" };

	@Autowired
	private TestRestTemplate rest;

	@ParameterizedTest(name = "{0}")
	@CsvSource({ "/owners?page=0, /owners?page=1", "/owners?page=-2147483648, /owners?page=1",
			"/owners?page=429496730, /owners?page=1", "/vets.html?page=-2147483648, /vets.html?page=1" })
	void outOfRangePageNumberRedirectsToTheFirstPage(String path, String expectedLocation) {
		ResponseEntity<String> response = this.rest.withRedirects(HttpRedirects.DONT_FOLLOW)
			.getForEntity(path, String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		URI location = response.getHeaders().getLocation();
		assertThat(location).isNotNull();
		assertThat(location.getRawPath() + "?" + location.getRawQuery()).isEqualTo(expectedLocation);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "/owners?page=2147483648", "/owners?page=abc", "/vets.html?page=2147483648", "/owners/abc",
			"/owners/2147483648", "/owners/1/pets/abc/edit" })
	void malformedNumberIsRejectedAsBadRequest(String path) {
		ResponseEntity<String> response = getHtml(path);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("Something happened...").doesNotContain(INTERNAL_DETAILS);
	}

	// Unknown ids surface as HTTP 500 instead of 404 (tracked in docs/RUNBOOK.md); this
	// checks that users still get the error page and no internal details.
	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "/owners/999999", "/owners/999999/edit", "/owners/999999/pets/new",
			"/owners/1/pets/999999/edit", "/owners/1/pets/999999/visits/new" })
	void unknownIdRendersTheErrorPageWithoutInternalDetails(String path) {
		ResponseEntity<String> response = getHtml(path);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).contains("Something happened...").doesNotContain(INTERNAL_DETAILS);
	}

	@Test
	void jsonErrorResponseOmitsMessageAndStackTrace() {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		ResponseEntity<Map<String, Object>> response = this.rest.exchange("/owners/999999", HttpMethod.GET,
				new HttpEntity<>(headers), new ParameterizedTypeReference<>() {
				});
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).containsEntry("status", 500).doesNotContainKeys("message", "trace", "exception");
	}

	private ResponseEntity<String> getHtml(String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.TEXT_HTML));
		return this.rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

}
