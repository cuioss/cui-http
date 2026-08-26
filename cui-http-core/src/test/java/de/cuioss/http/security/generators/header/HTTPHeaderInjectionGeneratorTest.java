/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.http.security.generators.header;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link HTTPHeaderInjectionGenerator}.
 *
 * <p>The defining property of this generator is that every emitted header-value fragment
 * smuggles a second directive past the header boundary — either by carrying a CRLF marker or,
 * in the null-byte family, by truncating the value with a null byte. The aggregate test asserts
 * that all eight documented injection families are reachable.</p>
 *
 * <p>No per-value pipeline round-trip is asserted here: this generator emits raw header-value
 * fragments rather than URLs, so it has no single pipeline that consumes every emitted value.</p>
 */
@EnableGeneratorController
@DisplayName("HTTPHeaderInjectionGenerator Contract Tests")
class HTTPHeaderInjectionGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    private static final Pattern CRLF_INJECTION =
            Pattern.compile("[\\r\\n](X-Injected|X-Forwarded-For|X-Real-IP|X-Admin|Authorization): ");
    private static final Pattern SMUGGLING =
            Pattern.compile("\\n\\n(GET|POST|PUT|DELETE) /(admin|api|config|users) HTTP/1\\.1");
    private static final Pattern SECURITY_HEADER_OVERRIDE =
            Pattern.compile("\\r\\n(X-Frame-Options|Content-Security-Policy|Access-Control-Allow-Origin|X-XSS-Protection): ");
    private static final Pattern CONTENT_TYPE_OVERRIDE =
            Pattern.compile("\\r\\n(Content-Type|Content-Length|Transfer-Encoding): ");
    private static final Pattern HOST_HEADER_INJECTION =
            Pattern.compile("\\r\\n(Host|Location): ");
    private static final String RESPONSE_SPLITTING = "\r\n\r\nHTTP/1.1 200 OK";
    private static final String NULL_BYTE_INJECTION = "\0admin";
    private static final String COOKIE_INJECTION = "\r\nSet-Cookie: ";

    @ParameterizedTest
    @TypeGeneratorSource(value = HTTPHeaderInjectionGenerator.class, count = 100)
    @DisplayName("Every generated value carries a CRLF or a null-byte marker")
    void shouldGenerateHeaderInjection(String generatedValue) {
        Set<String> injectionMarkers = new HashSet<>(CRLF_MARKERS);
        injectionMarkers.addAll(NULL_BYTE_MARKERS);
        assertContainsAny(generatedValue, injectionMarkers, "HTTP header injection value");
    }

    @Test
    @DisplayName("Should reach all eight documented injection families")
    void shouldReachAllInjectionFamilies() {
        HTTPHeaderInjectionGenerator generator = new HTTPHeaderInjectionGenerator();
        Set<String> families = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            if (CRLF_INJECTION.matcher(value).find()) {
                families.add("crlf");
            }
            if (value.contains(RESPONSE_SPLITTING)) {
                families.add("response-splitting");
            }
            if (value.contains(NULL_BYTE_INJECTION)) {
                families.add("null-byte");
            }
            if (value.contains(COOKIE_INJECTION)) {
                families.add("cookie");
            }
            if (SECURITY_HEADER_OVERRIDE.matcher(value).find()) {
                families.add("security-header");
            }
            if (CONTENT_TYPE_OVERRIDE.matcher(value).find()) {
                families.add("content-type");
            }
            if (SMUGGLING.matcher(value).find()) {
                families.add("smuggling");
            }
            if (HOST_HEADER_INJECTION.matcher(value).find()) {
                families.add("host-header");
            }
        }

        assertEquals(Set.of("crlf", "response-splitting", "null-byte", "cookie",
                        "security-header", "content-type", "smuggling", "host-header"), families,
                "Every documented injection family must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new HTTPHeaderInjectionGenerator().getType(),
                "Generator should return String.class");
    }
}
