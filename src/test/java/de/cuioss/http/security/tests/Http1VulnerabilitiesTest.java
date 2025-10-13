/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.http.security.tests;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP/1.x Protocol Vulnerability Tests
 *
 * <p>
 * This test class validates defense against HTTP/1.x protocol vulnerabilities
 * documented in the PortSwigger research "HTTP/1 Must Die". These tests provide
 * regression prevention for application-layer HTTP component validation, ensuring
 * that HTTP headers, paths, and parameters cannot be manipulated to exploit
 * infrastructure-level request smuggling vulnerabilities.
 * </p>
 *
 * <h3>Test Coverage</h3>
 * <ul>
 *   <li>CL.0 smuggling patterns - Content-Length with no Transfer-Encoding</li>
 *   <li>0.CL smuggling patterns - Zero Content-Length with body</li>
 *   <li>Expect header desync patterns - Expect: 100-continue manipulation</li>
 *   <li>Parser differential attacks - Visible-Hidden (V-H) and Hidden-Visible (H-V)</li>
 *   <li>Upstream connection reuse attack patterns</li>
 *   <li>Header folding and whitespace manipulation</li>
 *   <li>Duplicate header patterns</li>
 *   <li>HTTP verb injection in component values</li>
 * </ul>
 *
 * <h3>Scope and Limitations</h3>
 * <p>
 * <strong>Library Scope (Tested):</strong> Application-layer HTTP component validation
 * (headers, paths, parameters) for injection patterns.
 * </p>
 * <p>
 * <strong>Infrastructure Scope (Not Tested):</strong> Full HTTP request/response parsing,
 * Content-Length vs Transfer-Encoding conflict resolution, connection reuse management,
 * protocol-level handling. These are servlet container, proxy, and load balancer responsibilities.
 * </p>
 *
 * <h3>Defense-in-Depth Strategy</h3>
 * <p>
 * Even when full request smuggling is impossible at the application layer, validating
 * HTTP components prevents:
 * </p>
 * <ul>
 *   <li>CRLF injection in headers and URLs</li>
 *   <li>Control character injection</li>
 *   <li>Embedded HTTP verbs in component values</li>
 *   <li>Header value manipulation</li>
 *   <li>Path traversal via HTTP smuggling patterns</li>
 * </ul>
 *
 * <h3>Security References</h3>
 * <ul>
 *   <li>PortSwigger Research: HTTP/1 Must Die</li>
 *   <li>CVE-2025-32094 - Akamai Infrastructure Vulnerability</li>
 *   <li>RFC 7230 - HTTP/1.1 Message Syntax and Routing</li>
 *   <li>RFC 9112 - HTTP/1.1 (Updated)</li>
 *   <li>CWE-444: Inconsistent Interpretation of HTTP Requests</li>
 * </ul>
 *
 * @see <a href="https://portswigger.net/research/http1-must-die">HTTP/1 Must Die</a>
 * @see <a href="../../../doc/http-security/analysis/http1-vulnerabilities-analysis.adoc">HTTP/1.x Vulnerabilities Analysis</a>
 * @since 1.0
 */
@DisplayName("HTTP/1.x Protocol Vulnerability Tests")
class Http1VulnerabilitiesTest {

    private URLPathValidationPipeline pathPipeline;
    private SecurityEventCounter eventCounter;
    private SecurityConfiguration config;

    @BeforeEach
    void setUp() {
        config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pathPipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Test CL.0 smuggling patterns where Content-Length is specified but body is ignored.
     * These patterns attempt to smuggle requests by embedding HTTP verbs in URL paths.
     *
     * <p>Attack Pattern: {@code /api?param=value%0d%0aContent-Length: 44%0d%0a%0d%0aGET /admin}</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api?data=test%0d%0aContent-Length: 44%0d%0a%0d%0aGET /admin HTTP/1.1",
            "/search?q=data%0d%0aContent-Length: 30%0d%0a%0d%0aPOST /admin/users",
            "/process?input=normal%0d%0aContent-Length: 25%0d%0a%0d%0aDELETE /users",
            "/api/endpoint%0d%0aContent-Length: 60%0d%0a%0d%0aGET /admin/secrets HTTP/1.1%0d%0aHost: internal",
            "/data?param=value%0d%0aContent-Length: 35%0d%0a%0d%0aPUT /admin/config"
    })
    @DisplayName("CL.0 smuggling patterns must be rejected")
    void shouldRejectClZeroSmugglingPatterns(String clZeroPattern) {
        // Given: A CL.0 smuggling pattern with embedded HTTP verb
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the pattern
        var exception = assertThrows(UrlSecurityException.class,
                () -> pathPipeline.validate(clZeroPattern),
                "CL.0 smuggling pattern should be rejected: " + clZeroPattern);

        // Then: Validation should fail with control character or invalid character detection
        assertNotNull(exception, "Exception should be thrown for CL.0 smuggling pattern");
        assertTrue(isControlCharacterOrInvalidCharacter(exception.getFailureType()),
                "Failure type should be CONTROL_CHARACTERS or INVALID_CHARACTER for CRLF injection: " + exception.getFailureType());

        // And: Original malicious input should be preserved
        assertEquals(clZeroPattern, exception.getOriginalInput(),
                "Original input should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for CL.0 pattern");
    }

    /**
     * Test 0.CL smuggling patterns where Content-Length is zero but body is present.
     * These patterns attempt to confuse parsers by declaring zero length with actual content.
     *
     * <p>Attack Pattern: {@code /api?param=value%0d%0aContent-Length: 0%0d%0a%0d%0aGET /admin}</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api?data=test%0d%0aContent-Length: 0%0d%0a%0d%0aGET /admin HTTP/1.1",
            "/search?q=data%0d%0aContent-Length: 0%0d%0a%0d%0aPOST /admin/users HTTP/1.1",
            "/process?input=normal%0d%0aContent-Length: 0%0d%0a%0d%0aDELETE /users/victim",
            "/api/endpoint%0d%0aContent-Length: 0%0d%0a%0d%0aGET /secrets HTTP/1.1%0d%0aHost: internal",
            "/data?param=value%0d%0aContent-Length: 0%0d%0a%0d%0aPUT /admin/elevate HTTP/1.1"
    })
    @DisplayName("0.CL smuggling patterns must be rejected")
    void shouldRejectZeroClSmugglingPatterns(String zeroClPattern) {
        // Given: A 0.CL smuggling pattern with zero Content-Length but body present
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the pattern
        var exception = assertThrows(UrlSecurityException.class,
                () -> pathPipeline.validate(zeroClPattern),
                "0.CL smuggling pattern should be rejected: " + zeroClPattern);

        // Then: Validation should fail with CRLF detection
        assertNotNull(exception, "Exception should be thrown for 0.CL smuggling pattern");
        assertTrue(isControlCharacterOrInvalidCharacter(exception.getFailureType()),
                "Failure type should be CONTROL_CHARACTERS or INVALID_CHARACTER: " + exception.getFailureType());

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for 0.CL pattern");
    }

    /**
     * Test Expect header desync patterns.
     * These patterns exploit differences in how servers handle Expect: 100-continue.
     *
     * <p>Attack Pattern: {@code /api?param=value%0d%0aExpect: 100-continue%0d%0a%0d%0aGET /admin}</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api?data=test%0d%0aExpect: 100-continue%0d%0aContent-Length: 44%0d%0a%0d%0aGET /admin",
            "/search?q=data%0d%0aExpect: 100-continue%0d%0a%0d%0aPOST /admin/users HTTP/1.1",
            "/process?input=normal%0d%0aExpect: 100-continue%0d%0aContent-Length: 30%0d%0a%0d%0aDELETE /users",
            "/api/endpoint%0d%0aExpect: 100-continue%0d%0aTransfer-Encoding: chunked%0d%0a%0d%0a0%0d%0a%0d%0aGET /secrets"
    })
    @DisplayName("Expect header desync patterns must be rejected")
    void shouldRejectExpectHeaderDesyncPatterns(String expectPattern) {
        // Given: An Expect header desync pattern
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the pattern
        var exception = assertThrows(UrlSecurityException.class,
                () -> pathPipeline.validate(expectPattern),
                "Expect header desync pattern should be rejected: " + expectPattern);

        // Then: Validation should fail with CRLF detection
        assertNotNull(exception, "Exception should be thrown for Expect header desync");
        assertTrue(isControlCharacterOrInvalidCharacter(exception.getFailureType()),
                "Failure type should be CONTROL_CHARACTERS or INVALID_CHARACTER: " + exception.getFailureType());

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for Expect desync");
    }

    /**
     * Test duplicate Content-Length header patterns (CL.CL) in URL paths.
     * These patterns exploit parsers that use the first vs. last Content-Length header.
     * Note: Testing in path context where URL decoding occurs.
     *
     * <p>Attack Pattern: {@code /api?param=value%0d%0aContent-Length: 10%0d%0aContent-Length: 20}</p>
     */
    @Test
    @DisplayName("Duplicate Content-Length header patterns in paths must be rejected")
    void shouldRejectDuplicateContentLengthHeadersInPaths() {
        // Given: Path values with duplicate Content-Length injection
        String[] duplicateClPaths = {
                "/api?data=test%0d%0aContent-Length: 10%0d%0aContent-Length: 20",
                "/search?q=data%0d%0aContent-Length: 0%0d%0aContent-Length: 44",
                "/process?input=normal%0d%0aContent-Length: 5%0d%0aContent-Length: 100",
                "/endpoint?param=value%0d%0aContent-Length: 30%0d%0aContent-Length: 60"
        };

        for (String pathValue : duplicateClPaths) {
            long initialEventCount = eventCounter.getTotalCount();

            // When: Attempting to validate the path
            var exception = assertThrows(UrlSecurityException.class,
                    () -> pathPipeline.validate(pathValue),
                    "Duplicate Content-Length pattern should be rejected: " + pathValue);

            // Then: Validation should fail
            assertNotNull(exception, "Exception should be thrown for duplicate Content-Length");
            assertTrue(eventCounter.getTotalCount() > initialEventCount,
                    "Security event should be recorded");
        }
    }

    /**
     * Test Transfer-Encoding obfuscation patterns in URL paths.
     * These patterns exploit variations in Transfer-Encoding header parsing.
     * Note: Testing in path context where URL decoding occurs.
     *
     * <p>Attack Pattern: {@code /api?param=chunked%0d%0aTransfer-Encoding: identity}</p>
     */
    @Test
    @DisplayName("Transfer-Encoding obfuscation patterns in paths must be rejected")
    void shouldRejectTransferEncodingObfuscationInPaths() {
        // Given: Path values with Transfer-Encoding injection
        String[] teObfuscationPaths = {
                "/api?data=chunked%0d%0aTransfer-Encoding: identity",
                "/search?q=chunked%0d%0aTransfer-encoding: chunked",
                "/process?input=test%0d%0aTransfer-Encoding: chunked",
                "/endpoint?param=value%0d%0aTransfer-Encoding: x"
        };

        for (String pathValue : teObfuscationPaths) {
            long initialEventCount = eventCounter.getTotalCount();

            // When: Attempting to validate the path
            var exception = assertThrows(UrlSecurityException.class,
                    () -> pathPipeline.validate(pathValue),
                    "Transfer-Encoding obfuscation should be rejected: " + pathValue);

            // Then: Validation should fail with CRLF detection
            assertNotNull(exception, "Exception should be thrown for TE obfuscation");
            assertTrue(eventCounter.getTotalCount() > initialEventCount,
                    "Security event should be recorded");
        }
    }

    /**
     * Test HTTP verb injection in paths.
     * These patterns embed HTTP verbs (GET, POST, DELETE, etc.) in URL paths.
     *
     * <p>Attack Pattern: {@code /api%0d%0aGET /admin HTTP/1.1}</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api%0d%0aGET /admin HTTP/1.1",
            "/search%0d%0aPOST /admin/users HTTP/1.1",
            "/process%0d%0aDELETE /users/victim HTTP/1.1",
            "/data%0d%0aPUT /admin/config HTTP/1.1",
            "/endpoint%0d%0aPATCH /admin/settings HTTP/1.1",
            "/api%0d%0aHEAD /admin/secrets HTTP/1.1",
            "/resource%0d%0aOPTIONS /admin HTTP/1.1"
    })
    @DisplayName("HTTP verb injection in paths must be rejected")
    void shouldRejectHttpVerbInjectionInPaths(String verbInjectionPath) {
        // Given: A path with embedded HTTP verb
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the path
        var exception = assertThrows(UrlSecurityException.class,
                () -> pathPipeline.validate(verbInjectionPath),
                "HTTP verb injection should be rejected: " + verbInjectionPath);

        // Then: Validation should fail with CRLF detection
        assertNotNull(exception, "Exception should be thrown for verb injection");
        assertTrue(isControlCharacterOrInvalidCharacter(exception.getFailureType()),
                "Failure type should be CONTROL_CHARACTERS or INVALID_CHARACTER: " + exception.getFailureType());

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for verb injection");
    }

    /**
     * Test header name injection with CRLF in URL paths.
     * These patterns attempt to inject additional headers via CRLF in path parameters.
     * Note: Header names themselves don't undergo URL decoding, so we test in path context.
     *
     * <p>Attack Pattern: {@code /api?header=X-Custom%0d%0aX-Injected: malicious}</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api?header=X-Custom%0d%0aX-Injected: malicious",
            "/search?user=Agent%0d%0aAuthorization: Bearer stolen",
            "/process?xff=value%0d%0aX-Admin: true",
            "/endpoint?type=json%0d%0aX-Override: admin",
            "/data?accept=all%0d%0aCookie: session=hijacked"
    })
    @DisplayName("Header injection via path parameters must be rejected")
    void shouldRejectHeaderInjectionViaPathParameters(String injectedPath) {
        // Given: A path with header injection attempt
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the path
        var exception = assertThrows(UrlSecurityException.class,
                () -> pathPipeline.validate(injectedPath),
                "Header injection via path should be rejected: " + injectedPath);

        // Then: Validation should fail with control character detection
        assertNotNull(exception, "Exception should be thrown for header injection");
        assertTrue(isControlCharacterOrInvalidCharacter(exception.getFailureType()),
                "Failure type should be CONTROL_CHARACTERS or INVALID_CHARACTER: " + exception.getFailureType());

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for header injection");
    }

    /**
     * Test whitespace and control character manipulation in URL paths.
     * These patterns exploit differences in whitespace handling between parsers.
     * Note: Testing in path context where URL decoding occurs.
     *
     * <p>Attack Pattern: {@code /api?param=chunked%0d%0a}</p>
     */
    @Test
    @DisplayName("Whitespace and control character manipulation in paths must be rejected")
    void shouldHandleWhitespaceManipulationInPaths() {
        // Given: Paths with whitespace and control character manipulation
        String[] whitespacePaths = {
                "/api?param=chunked%0d",
                "/search?q=value%0a",
                "/process?input=test%0d%0a",
                "/endpoint?data=value%09test",
                "/data?param=test%0d%0ainjected"
        };

        for (String pathValue : whitespacePaths) {
            long initialEventCount = eventCounter.getTotalCount();

            // When: Attempting to validate the path
            var exception = assertThrows(UrlSecurityException.class,
                    () -> pathPipeline.validate(pathValue),
                    "Whitespace/control character manipulation should be rejected: " + pathValue);

            // Then: Validation should fail with control character detection
            assertNotNull(exception, "Exception should be thrown for whitespace manipulation");
            assertTrue(eventCounter.getTotalCount() > initialEventCount,
                    "Security event should be recorded");
        }
    }

    /**
     * Test HTTP response injection patterns.
     * These patterns attempt to inject HTTP response headers or status lines.
     *
     * <p>Attack Pattern: {@code /api%0d%0aHTTP/1.1 200 OK%0d%0aContent-Type: text/html}</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api%0d%0aHTTP/1.1 200 OK%0d%0aContent-Type: text/html",
            "/search%0d%0aHTTP/1.1 302 Found%0d%0aLocation: http://evil.com",
            "/process%0d%0aHTTP/1.1 401 Unauthorized%0d%0aWWW-Authenticate: Basic",
            "/data%0d%0aHTTP/1.1 500 Internal Server Error",
            "/endpoint%0d%0aHTTP/1.1 403 Forbidden"
    })
    @DisplayName("HTTP response injection patterns must be rejected")
    void shouldRejectHttpResponseInjection(String responseInjection) {
        // Given: A path with embedded HTTP response
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the path
        var exception = assertThrows(UrlSecurityException.class,
                () -> pathPipeline.validate(responseInjection),
                "HTTP response injection should be rejected: " + responseInjection);

        // Then: Validation should fail with CRLF detection
        assertNotNull(exception, "Exception should be thrown for response injection");
        assertTrue(isControlCharacterOrInvalidCharacter(exception.getFailureType()),
                "Failure type should be CONTROL_CHARACTERS or INVALID_CHARACTER: " + exception.getFailureType());

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for response injection");
    }

    /**
     * Test upstream routing header injection.
     * These patterns inject headers used for routing (X-Forwarded-*, X-Original-URL, etc.).
     *
     * <p>Attack Pattern: {@code /api?param=value%0d%0aX-Original-URL: /admin}</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api?data=test%0d%0aX-Original-URL: /admin",
            "/search?q=data%0d%0aX-Rewrite-URL: /admin/secrets",
            "/process?input=normal%0d%0aX-Forwarded-Host: evil.com",
            "/data?param=value%0d%0aX-Forwarded-Proto: https",
            "/endpoint?test=true%0d%0aX-Forwarded-For: 127.0.0.1"
    })
    @DisplayName("Upstream routing header injection must be rejected")
    void shouldRejectUpstreamRoutingHeaderInjection(String routingInjection) {
        // Given: A path with routing header injection
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the path
        var exception = assertThrows(UrlSecurityException.class,
                () -> pathPipeline.validate(routingInjection),
                "Routing header injection should be rejected: " + routingInjection);

        // Then: Validation should fail with CRLF detection
        assertNotNull(exception, "Exception should be thrown for routing injection");
        assertTrue(isControlCharacterOrInvalidCharacter(exception.getFailureType()),
                "Failure type should be CONTROL_CHARACTERS or INVALID_CHARACTER: " + exception.getFailureType());

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for routing injection");
    }

    /**
     * Test Host header injection.
     * These patterns inject Host headers to cause request routing confusion.
     *
     * <p>Attack Pattern: {@code /api%0d%0aHost: evil.com%0d%0a%0d%0aGET /admin}</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api%0d%0aHost: evil.com%0d%0a%0d%0aGET /admin",
            "/search%0d%0aHost: attacker.com%0d%0a%0d%0aPOST /admin/users",
            "/process%0d%0aHost: malicious.net%0d%0a%0d%0aDELETE /users",
            "/data%0d%0aHost: phishing.org%0d%0a%0d%0aPUT /admin/config"
    })
    @DisplayName("Host header injection must be rejected")
    void shouldRejectHostHeaderInjection(String hostInjection) {
        // Given: A path with Host header injection
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the path
        var exception = assertThrows(UrlSecurityException.class,
                () -> pathPipeline.validate(hostInjection),
                "Host header injection should be rejected: " + hostInjection);

        // Then: Validation should fail with CRLF detection
        assertNotNull(exception, "Exception should be thrown for Host injection");
        assertTrue(isControlCharacterOrInvalidCharacter(exception.getFailureType()),
                "Failure type should be CONTROL_CHARACTERS or INVALID_CHARACTER: " + exception.getFailureType());

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for Host injection");
    }

    /**
     * Helper method to check if failure type is control character or invalid character related.
     */
    private boolean isControlCharacterOrInvalidCharacter(UrlSecurityFailureType failureType) {
        return failureType == UrlSecurityFailureType.CONTROL_CHARACTERS ||
                failureType == UrlSecurityFailureType.INVALID_CHARACTER ||
                failureType == UrlSecurityFailureType.PROTOCOL_VIOLATION ||
                failureType == UrlSecurityFailureType.RFC_VIOLATION;
    }
}
