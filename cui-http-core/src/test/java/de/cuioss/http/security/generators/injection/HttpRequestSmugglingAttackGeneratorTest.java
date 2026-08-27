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
package de.cuioss.http.security.generators.injection;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineRejects;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link HttpRequestSmugglingAttackGenerator}.
 *
 * <p>The defining property of this generator is that every emitted value is an absolute HTTP URL
 * whose query smuggles a second request across an encoded CRLF, and is therefore rejected by the
 * URL path validation pipeline. The aggregate test asserts that all fifteen documented smuggling
 * families are reachable.</p>
 *
 * <p>Family attribution is first-match-wins over the ordered classifier below, because several
 * families legitimately share a header: the header-manipulation family, for instance, emits one
 * pattern carrying {@code X-HTTP-Method-Override}, which the method-override classifier claims
 * first. Every family still fills from the patterns that are unambiguously its own.</p>
 */
@EnableGeneratorController
@DisplayName("HttpRequestSmugglingAttackGenerator Contract Tests")
class HttpRequestSmugglingAttackGeneratorTest {

    private static final int AGGREGATE_DRAWS = 600;
    private static final String ENCODED_CRLF = "%0d%0a";

    private static final Pattern RESPONSE_STATUS_LINE = Pattern.compile("HTTP/1\\.1 \\d{3}");
    private static final Pattern DUPLICATE_TRANSFER_ENCODING =
            Pattern.compile("Transfer-[Ee]ncoding:.*?%0d%0aTransfer-[Ee]ncoding:");
    private static final Pattern DUPLICATE_CONTENT_LENGTH =
            Pattern.compile("Content-Length: \\d+%0d%0aContent-Length: \\d+");
    private static final Pattern CONTENT_LENGTH_THEN_TRANSFER_ENCODING =
            Pattern.compile("Content-Length: \\d+%0d%0aTransfer-Encoding: chunked");
    private static final Pattern TRANSFER_ENCODING_THEN_CONTENT_LENGTH =
            Pattern.compile("Transfer-Encoding: chunked%0d%0aContent-Length: \\d+");

    private static final List<String> HTTP2_MARKERS = List.of("HTTP2-Settings", "PRI * HTTP/2.0");
    private static final List<String> WEBSOCKET_MARKERS = List.of("Upgrade: websocket", "Sec-WebSocket");
    private static final List<String> CACHE_MARKERS = List.of("Cache-Control:", "Vary:", "Expires:");
    private static final List<String> HIJACKING_MARKERS = List.of(
            "/victim-request", "X=1POST /capture", "SMUGGLED_REQUEST", "HIJACK_PAYLOAD_REQUEST",
            "INTERCEPTED_USER_REQUEST", "POISONED_REQUEST_QUEUE", "QUEUE_POISONING_ATTACK");
    private static final List<String> URL_REWRITING_MARKERS = List.of(
            "X-Rewrite-URL", "X-Original-URI", "X-Forwarded-URI", "X-Forwarded-Path", "X-Proxy-URL",
            "X-Original-URL: /admin/users", "X-Original-URL: /admin/elevate");
    private static final List<String> METHOD_OVERRIDE_MARKERS = List.of(
            "X-HTTP-Method-Override", "X-HTTP-Method:", "X-Method-Override", "X-Method:", "_method:");
    private static final List<String> AUTH_BYPASS_MARKERS = List.of(
            "Authorization: Bearer hijacked", "X-Forwarded-User", "X-Remote-User",
            "Cookie: session=admin-session", "X-Forwarded-For: 127.0.0.1", "X-User-Role",
            "X-Original-URL: /admin%0d%0a");
    private static final List<String> HEADER_MANIPULATION_MARKERS = List.of(
            "X-Forwarded-Proto", "Host: evil.com", "X-Forwarded-Host", "X-Original-IP",
            "Referer: http://admin.internal", "User-Agent: AdminBot");

    private static final Set<String> DOCUMENTED_FAMILIES = Set.of(
            "cl.te", "te.cl", "te.te", "cl.cl", "http2-downgrade", "pipeline-poisoning",
            "cache-deception", "auth-bypass", "header-manipulation", "method-override",
            "url-rewriting", "request-hijacking", "response-queue-poisoning",
            "websocket-upgrade", "chunked-bypass");

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpRequestSmugglingAttackGenerator.class, count = 100)
    @DisplayName("Every generated value is an absolute URL smuggling a request across an encoded CRLF")
    void shouldGenerateSmugglingUrl(String generatedValue) {
        assertTrue(generatedValue.startsWith("http://") || generatedValue.startsWith("https://"),
                () -> "Smuggling payloads are absolute HTTP URLs. Value: <" + generatedValue + ">");
        assertTrue(generatedValue.contains(ENCODED_CRLF),
                () -> "Smuggling payloads carry an encoded CRLF. Value: <" + generatedValue + ">");

        assertPipelineRejects(
                new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue);
    }

    @Test
    @DisplayName("Should reach all fifteen documented smuggling families")
    void shouldReachAllSmugglingFamilies() {
        HttpRequestSmugglingAttackGenerator generator = new HttpRequestSmugglingAttackGenerator();
        Set<String> families = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            families.add(classify(generator.next()));
        }

        assertEquals(DOCUMENTED_FAMILIES, families,
                "Every documented smuggling family must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new HttpRequestSmugglingAttackGenerator().getType(),
                "Generator should return String.class");
    }

    private static String classify(String value) {
        if (containsAny(value, HTTP2_MARKERS)) {
            return "http2-downgrade";
        }
        if (containsAny(value, WEBSOCKET_MARKERS)) {
            return "websocket-upgrade";
        }
        if (containsAny(value, CACHE_MARKERS)) {
            return "cache-deception";
        }
        if (RESPONSE_STATUS_LINE.matcher(value).find()) {
            return "response-queue-poisoning";
        }
        if (containsAny(value, HIJACKING_MARKERS)) {
            return "request-hijacking";
        }
        if (containsAny(value, URL_REWRITING_MARKERS)) {
            return "url-rewriting";
        }
        if (containsAny(value, METHOD_OVERRIDE_MARKERS)) {
            return "method-override";
        }
        if (containsAny(value, AUTH_BYPASS_MARKERS)) {
            return "auth-bypass";
        }
        if (containsAny(value, HEADER_MANIPULATION_MARKERS)) {
            return "header-manipulation";
        }
        if (value.contains("Connection: keep-alive")) {
            return "pipeline-poisoning";
        }
        if (DUPLICATE_TRANSFER_ENCODING.matcher(value).find()) {
            return "te.te";
        }
        if (DUPLICATE_CONTENT_LENGTH.matcher(value).find()) {
            return "cl.cl";
        }
        if (CONTENT_LENGTH_THEN_TRANSFER_ENCODING.matcher(value).find()) {
            return "cl.te";
        }
        if (TRANSFER_ENCODING_THEN_CONTENT_LENGTH.matcher(value).find()) {
            return "te.cl";
        }
        if (value.contains("Transfer-Encoding: chunked")) {
            return "chunked-bypass";
        }
        return fail("Value belongs to no documented smuggling family. Value: <" + value + ">");
    }

    private static boolean containsAny(String value, List<String> markers) {
        return markers.stream().anyMatch(value::contains);
    }
}
