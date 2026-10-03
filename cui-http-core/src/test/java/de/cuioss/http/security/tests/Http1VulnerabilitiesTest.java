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
package de.cuioss.http.security.tests;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP/1.x Protocol Vulnerability Tests
 *
 * <p>
 * This test class validates defense against HTTP/1.x protocol vulnerabilities
 * documented in the PortSwigger research "HTTP/1 Must Die". These tests provide
 * regression prevention for application-layer HTTP component validation, ensuring
 * that a URL path cannot carry a percent-encoded line break that an upstream parser
 * would read as the end of the request line.
 * </p>
 *
 * <h3>Payload shape and verdict</h3>
 * <p>
 * Every payload is a URL <em>path component</em> whose wire form consists of path characters
 * and well-formed percent-escapes only: the smuggled header block is appended to the path with
 * its line breaks spelled {@code %0d%0a} and its spaces spelled {@code %20}. Nothing in the wire
 * form is inadmissible, so the verdict is reached where the attack actually lives - the decoding
 * stage finds the decoded control character and reports
 * {@link UrlSecurityFailureType#CONTROL_CHARACTERS}.
 * </p>
 *
 * <h3>Test Coverage</h3>
 * <ul>
 *   <li>CL.0 smuggling patterns - Content-Length with no Transfer-Encoding</li>
 *   <li>0.CL smuggling patterns - Zero Content-Length with body</li>
 *   <li>Expect header desync patterns - Expect: 100-continue manipulation</li>
 *   <li>Duplicate header patterns</li>
 *   <li>Transfer-Encoding obfuscation</li>
 *   <li>HTTP verb injection in component values</li>
 *   <li>Header, routing-header and Host header injection</li>
 *   <li>Whitespace and control character manipulation</li>
 *   <li>HTTP response injection</li>
 * </ul>
 *
 * <h3>Scope and Limitations</h3>
 * <p>
 * <strong>Library Scope (Tested):</strong> Application-layer HTTP component validation
 * for injection patterns.
 * </p>
 * <p>
 * <strong>Infrastructure Scope (Not Tested):</strong> Full HTTP request/response parsing,
 * Content-Length vs Transfer-Encoding conflict resolution, connection reuse management,
 * protocol-level handling. These are servlet container, proxy, and load balancer responsibilities.
 * </p>
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

    private static final String CONTENT_LENGTH_NAME = "Content-Length:";
    private static final String CONTENT_LENGTH_HEADER = CONTENT_LENGTH_NAME + "%20";
    private static final String ZERO_CONTENT_LENGTH = CONTENT_LENGTH_HEADER + "0%0d";
    private static final String ZERO_CONTENT_LENGTH_THEN_BODY = ZERO_CONTENT_LENGTH + "%0a%0d%0a";

    /** A second request line behind the encoded line break. */
    private static final Pattern INJECTED_REQUEST_LINE =
            Pattern.compile("%0d%0a(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)%20/");

    /** A header name and its encoded colon-space behind the encoded line break. */
    private static final Pattern INJECTED_HEADER = Pattern.compile("%0d%0a[A-Za-z][A-Za-z0-9-]*:%20");

    /** An upstream routing header behind the encoded line break. */
    private static final Pattern INJECTED_ROUTING_HEADER =
            Pattern.compile("%0d%0aX-(Original-URL|Rewrite-URL|Forwarded-(Host|Proto|For)):%20");

    private URLPathValidationPipeline pathPipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
        pathPipeline = new URLPathValidationPipeline(SecurityConfiguration.defaults(), eventCounter);
    }

    /**
     * CL.0 smuggling: a Content-Length is declared but the body is ignored by one parser.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/test%0d%0aContent-Length:%2044%0d%0a%0d%0aGET%20/admin%20HTTP/1.1",
            "/search/data%0d%0aContent-Length:%2030%0d%0a%0d%0aPOST%20/admin/users",
            "/process/normal%0d%0aContent-Length:%2025%0d%0a%0d%0aDELETE%20/users",
            "/api/endpoint%0d%0aContent-Length:%2060%0d%0a%0d%0aGET%20/admin/secrets%20HTTP/1.1%0d%0aHost:%20internal",
            "/data/value%0d%0aContent-Length:%2035%0d%0a%0d%0aPUT%20/admin/config"
    })
    @DisplayName("CL.0 smuggling patterns are rejected as CONTROL_CHARACTERS")
    void shouldRejectClZeroSmugglingPatterns(String clZeroPattern) {
        assertTrue(clZeroPattern.contains(CONTENT_LENGTH_HEADER) && !clZeroPattern.contains(ZERO_CONTENT_LENGTH),
                () -> "CL.0 family: payload must declare a non-zero Content-Length - " + clZeroPattern);

        var exception = assertRejected(clZeroPattern);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + clZeroPattern);
    }

    /**
     * 0.CL smuggling: Content-Length is zero but a body is present.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/test%0d%0aContent-Length:%200%0d%0a%0d%0aGET%20/admin%20HTTP/1.1",
            "/search/data%0d%0aContent-Length:%200%0d%0a%0d%0aPOST%20/admin/users%20HTTP/1.1",
            "/process/normal%0d%0aContent-Length:%200%0d%0a%0d%0aDELETE%20/users/victim",
            "/api/endpoint%0d%0aContent-Length:%200%0d%0a%0d%0aGET%20/secrets%20HTTP/1.1%0d%0aHost:%20internal",
            "/data/value%0d%0aContent-Length:%200%0d%0a%0d%0aPUT%20/admin/elevate%20HTTP/1.1"
    })
    @DisplayName("0.CL smuggling patterns are rejected as CONTROL_CHARACTERS")
    void shouldRejectZeroClSmugglingPatterns(String zeroClPattern) {
        int headerEnd = zeroClPattern.indexOf(ZERO_CONTENT_LENGTH_THEN_BODY);
        assertTrue(headerEnd >= 0 && headerEnd + ZERO_CONTENT_LENGTH_THEN_BODY.length() < zeroClPattern.length(),
                () -> "0.CL family: payload must carry Content-Length 0, a blank line and a body - " + zeroClPattern);

        var exception = assertRejected(zeroClPattern);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + zeroClPattern);
    }

    /**
     * Expect header desync: differences in how servers handle {@code Expect: 100-continue}.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/test%0d%0aExpect:%20100-continue%0d%0aContent-Length:%2044%0d%0a%0d%0aGET%20/admin",
            "/search/data%0d%0aExpect:%20100-continue%0d%0a%0d%0aPOST%20/admin/users%20HTTP/1.1",
            "/process/normal%0d%0aExpect:%20100-continue%0d%0aContent-Length:%2030%0d%0a%0d%0aDELETE%20/users",
            "/api/endpoint%0d%0aExpect:%20100-continue%0d%0aTransfer-Encoding:%20chunked%0d%0a%0d%0a0%0d%0a%0d%0aGET%20/secrets"
    })
    @DisplayName("Expect header desync patterns are rejected as CONTROL_CHARACTERS")
    void shouldRejectExpectHeaderDesyncPatterns(String expectPattern) {
        assertTrue(expectPattern.contains("Expect:%20100-continue"),
                () -> "Expect desync family: payload must carry Expect: 100-continue - " + expectPattern);

        var exception = assertRejected(expectPattern);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + expectPattern);
    }

    /**
     * Duplicate Content-Length (CL.CL): parsers that use the first versus the last header.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/test%0d%0aContent-Length:%2010%0d%0aContent-Length:%2020",
            "/search/data%0d%0aContent-Length:%200%0d%0aContent-Length:%2044",
            "/process/normal%0d%0aContent-Length:%205%0d%0aContent-Length:%20100",
            "/endpoint/value%0d%0aContent-Length:%2030%0d%0aContent-Length:%2060"
    })
    @DisplayName("Duplicate Content-Length header patterns are rejected as CONTROL_CHARACTERS")
    void shouldRejectDuplicateContentLengthHeadersInPaths(String pathValue) {
        assertNotEquals(pathValue.indexOf(CONTENT_LENGTH_NAME), pathValue.lastIndexOf(CONTENT_LENGTH_NAME),
                () -> "Duplicate Content-Length family: Content-Length must occur at least twice - " + pathValue);

        var exception = assertRejected(pathValue);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + pathValue);
    }

    /**
     * Transfer-Encoding obfuscation: variations in Transfer-Encoding header parsing.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/chunked%0d%0aTransfer-Encoding:%20identity",
            "/search/chunked%0d%0aTransfer-encoding:%20chunked",
            "/process/test%0d%0aTransfer-Encoding:%20chunked",
            "/endpoint/value%0d%0aTransfer-Encoding:%20x"
    })
    @DisplayName("Transfer-Encoding obfuscation patterns are rejected as CONTROL_CHARACTERS")
    void shouldRejectTransferEncodingObfuscationInPaths(String pathValue) {
        assertTrue(pathValue.toLowerCase(Locale.ROOT).contains("transfer-encoding:%20"),
                () -> "Transfer-Encoding family: payload must carry a Transfer-Encoding header - " + pathValue);

        var exception = assertRejected(pathValue);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + pathValue);
    }

    /**
     * HTTP verb injection: a second request line behind an encoded line break.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api%0d%0aGET%20/admin%20HTTP/1.1",
            "/search%0d%0aPOST%20/admin/users%20HTTP/1.1",
            "/process%0d%0aDELETE%20/users/victim%20HTTP/1.1",
            "/data%0d%0aPUT%20/admin/config%20HTTP/1.1",
            "/endpoint%0d%0aPATCH%20/admin/settings%20HTTP/1.1",
            "/api%0d%0aHEAD%20/admin/secrets%20HTTP/1.1",
            "/resource%0d%0aOPTIONS%20/admin%20HTTP/1.1"
    })
    @DisplayName("HTTP verb injection in paths is rejected as CONTROL_CHARACTERS")
    void shouldRejectHttpVerbInjectionInPaths(String verbInjectionPath) {
        assertTrue(INJECTED_REQUEST_LINE.matcher(verbInjectionPath).find(),
                () -> "Verb injection family: payload must carry a request line behind the encoded break - "
                        + verbInjectionPath);

        var exception = assertRejected(verbInjectionPath);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + verbInjectionPath);
    }

    /**
     * Header injection: an additional header behind an encoded line break.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/X-Custom%0d%0aX-Injected:%20malicious",
            "/search/Agent%0d%0aAuthorization:%20Bearer%20stolen",
            "/process/value%0d%0aX-Admin:%20true",
            "/endpoint/json%0d%0aX-Override:%20admin",
            "/data/all%0d%0aCookie:%20session=hijacked"
    })
    @DisplayName("Header injection via the path is rejected as CONTROL_CHARACTERS")
    void shouldRejectHeaderInjectionViaPathParameters(String injectedPath) {
        assertTrue(INJECTED_HEADER.matcher(injectedPath).find(),
                () -> "Header injection family: payload must carry a header behind the encoded break - " + injectedPath);

        var exception = assertRejected(injectedPath);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + injectedPath);
    }

    /**
     * Whitespace and control character manipulation: a lone encoded CR, LF or TAB.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/chunked%0d",
            "/search/value%0a",
            "/process/test%0d%0a",
            "/endpoint/value%09test",
            "/data/test%0d%0ainjected"
    })
    @DisplayName("Encoded whitespace control characters in paths are rejected as CONTROL_CHARACTERS")
    void shouldHandleWhitespaceManipulationInPaths(String pathValue) {
        String lowerCased = pathValue.toLowerCase(Locale.ROOT);
        assertTrue((lowerCased.contains("%0d") || lowerCased.contains("%0a") || lowerCased.contains("%09"))
                && !pathValue.contains(":"),
                () -> "Whitespace/control family: payload must carry an encoded CR, LF or TAB and smuggle no header - "
                        + pathValue);

        var exception = assertRejected(pathValue);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + pathValue);
    }

    /**
     * HTTP response injection: an injected status line and response headers.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api%0d%0aHTTP/1.1%20200%20OK%0d%0aContent-Type:%20text/html",
            "/search%0d%0aHTTP/1.1%20302%20Found%0d%0aLocation:%20http://evil.com",
            "/process%0d%0aHTTP/1.1%20401%20Unauthorized%0d%0aWWW-Authenticate:%20Basic",
            "/data%0d%0aHTTP/1.1%20500%20Internal%20Server%20Error",
            "/endpoint%0d%0aHTTP/1.1%20403%20Forbidden"
    })
    @DisplayName("HTTP response injection patterns are rejected as CONTROL_CHARACTERS")
    void shouldRejectHttpResponseInjection(String responseInjection) {
        assertTrue(responseInjection.contains("%0d%0aHTTP/1.1%20"),
                () -> "Response injection family: payload must carry a status line behind the encoded break - "
                        + responseInjection);

        var exception = assertRejected(responseInjection);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + responseInjection);
    }

    /**
     * Upstream routing header injection: X-Forwarded-*, X-Original-URL and their siblings.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/test%0d%0aX-Original-URL:%20/admin",
            "/search/data%0d%0aX-Rewrite-URL:%20/admin/secrets",
            "/process/normal%0d%0aX-Forwarded-Host:%20evil.com",
            "/data/value%0d%0aX-Forwarded-Proto:%20https",
            "/endpoint/true%0d%0aX-Forwarded-For:%20127.0.0.1"
    })
    @DisplayName("Upstream routing header injection is rejected as CONTROL_CHARACTERS")
    void shouldRejectUpstreamRoutingHeaderInjection(String routingInjection) {
        assertTrue(INJECTED_ROUTING_HEADER.matcher(routingInjection).find(),
                () -> "Routing header family: payload must carry an upstream routing header - " + routingInjection);

        var exception = assertRejected(routingInjection);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + routingInjection);
    }

    /**
     * Host header injection: an injected Host header causing request routing confusion.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api%0d%0aHost:%20evil.com%0d%0a%0d%0aGET%20/admin",
            "/search%0d%0aHost:%20attacker.com%0d%0a%0d%0aPOST%20/admin/users",
            "/process%0d%0aHost:%20malicious.net%0d%0a%0d%0aDELETE%20/users",
            "/data%0d%0aHost:%20phishing.org%0d%0a%0d%0aPUT%20/admin/config"
    })
    @DisplayName("Host header injection is rejected as CONTROL_CHARACTERS")
    void shouldRejectHostHeaderInjection(String hostInjection) {
        assertTrue(hostInjection.contains("%0d%0aHost:%20"),
                () -> "Host header family: payload must carry an injected Host header - " + hostInjection);

        var exception = assertRejected(hostInjection);

        assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType(),
                () -> "Unexpected verdict for: " + hostInjection);
    }

    /**
     * Asserts the rejection itself, the preserved input and the single recorded event; the
     * failure type is asserted by the caller.
     */
    private UrlSecurityException assertRejected(String path) {
        assertEquals(PathWireForm.WIRE_CLEAN, PathWireForm.of(path),
                () -> "The payload must reach the decoding stage, so its wire form must be clean: " + path);
        var exception = assertThrows(UrlSecurityException.class, () -> pathPipeline.validate(path),
                () -> "HTTP/1.x smuggling path should be rejected: " + path);
        assertEquals(path, exception.getOriginalInput(), "Original input should be preserved in exception");
        assertEquals(1, eventCounter.getTotalCount(), "Exactly one security event should be recorded");
        return exception;
    }
}
