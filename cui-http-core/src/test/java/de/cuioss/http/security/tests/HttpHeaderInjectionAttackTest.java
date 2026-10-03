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
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.header.HttpHeaderInjectionAttackGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.HTTPHeaderValidationPipeline;
import de.cuioss.http.security.pipeline.URLParameterValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T15: Test HTTP header injection patterns
 *
 * <p>
 * A header injection payload ends the header the application meant to write with a line break and
 * continues with attacker-chosen header lines. The payload reaches the application as the content
 * of one HTTP component, and this class validates each spelling on the surface that receives it:
 * </p>
 * <ul>
 *   <li><strong>Raw line breaks in a header value</strong> are validated by
 *       {@link HTTPHeaderValidationPipeline} for {@link ValidationType#HEADER_VALUE}. That
 *       pipeline does not decode, and its character stage rejects a raw CR or LF as
 *       {@link UrlSecurityFailureType#INVALID_CHARACTER}.</li>
 *   <li><strong>Percent-encoded line breaks in a parameter value</strong> are validated by
 *       {@link URLParameterValidationPipeline}. A decoded CR or LF is legitimate form data under
 *       the default configuration, so the pipeline under test closes that carve-out with
 *       {@code allowLineBreaksInParameterValues(false)} - the setting of a deployment that
 *       reflects parameter values into response headers - and its decoding stage then rejects the
 *       decoded line break as {@link UrlSecurityFailureType#CONTROL_CHARACTERS}.</li>
 * </ul>
 *
 * <h3>Security Standards Compliance</h3>
 * <ul>
 *   <li>OWASP Top 10: A03:2021 – Injection</li>
 *   <li>CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers</li>
 *   <li>CWE-116: Improper Encoding or Escaping of Output</li>
 *   <li>RFC 7230: Hypertext Transfer Protocol (HTTP/1.1): Message Syntax</li>
 *   <li>NIST SP 800-53: SI-10 Information Input Validation</li>
 * </ul>
 *
 * @see HttpHeaderInjectionAttackGenerator
 * @see HTTPHeaderValidationPipeline
 * @see URLParameterValidationPipeline
 * @author Generated for HTTP Security Validation (T15)
 * @version 1.0.0
 */
@EnableGeneratorController
@DisplayName("T15: HTTP Header Injection Attack Validation Tests")
class HttpHeaderInjectionAttackTest {

    /** The verdict of the header-value pipeline for a raw CR or LF. */
    private static final UrlSecurityFailureType RAW_LINE_BREAK_IN_HEADER_VALUE =
            UrlSecurityFailureType.INVALID_CHARACTER;

    /** The verdict of the line-break-rejecting parameter pipeline for a decoded CR or LF. */
    private static final UrlSecurityFailureType DECODED_LINE_BREAK_IN_PARAMETER_VALUE =
            UrlSecurityFailureType.CONTROL_CHARACTERS;

    private HTTPHeaderValidationPipeline headerValuePipeline;
    private URLParameterValidationPipeline parameterValuePipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
        headerValuePipeline = new HTTPHeaderValidationPipeline(SecurityConfiguration.defaults(), eventCounter,
                ValidationType.HEADER_VALUE);
        parameterValuePipeline = new URLParameterValidationPipeline(
                SecurityConfiguration.builder().allowLineBreaksInParameterValues(false).build(), eventCounter);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpHeaderInjectionAttackGenerator.class, count = 150)
    @DisplayName("Every generated raw header injection is rejected by the header-value pipeline")
    void shouldRejectAllRawHeaderInjectionAttacks(String headerValue) {
        assertRejected(headerValuePipeline, headerValue, RAW_LINE_BREAK_IN_HEADER_VALUE);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = HttpHeaderInjectionAttackGenerator.ForParameterValue.class, count = 150)
    @DisplayName("Every generated encoded header injection is rejected by the parameter pipeline")
    void shouldRejectAllEncodedHeaderInjectionAttacks(String parameterValue) {
        assertRejected(parameterValuePipeline, parameterValue, DECODED_LINE_BREAK_IN_PARAMETER_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "normal\r\nX-Injected: malicious",
            "test\r\nLocation: http://evil.com",
            "data\nSet-Cookie: admin=true",
            "data\rX-Injected: header",
            "safe.com\r\nLocation: http://evil.com",
            "admin\r\nAuthorization: Bearer hijacked-token",
            "normal\r\nAccess-Control-Allow-Origin: *",
            "test\r\nX-XSS-Protection: 0",
            "normal\r\nCache-Control: public, max-age=31536000",
            "test\r\nSet-Cookie: JSESSIONID=hijacked"
    })
    @DisplayName("Known raw header injections are rejected by the header-value pipeline")
    void shouldRejectKnownRawHeaderInjectionAttacks(String headerValue) {
        assertRejected(headerValuePipeline, headerValue, RAW_LINE_BREAK_IN_HEADER_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "normal%0d%0aX-Injected:%20malicious",
            "normal%0D%0AX-Injected:%20malicious",
            "input%0AContent-Type:%20text/html",
            "data%0a%0dX-Reversed:%20crlf",
            "test%0d%20%0aX-Space:%20value",
            "test%0d%0a%20X-Header:%20injected",
            "input%0d%0a%09X-Tab:%20header",
            "user%0d%0aSet-Cookie:%20role=administrator",
            "safe.com%0d%0aLocation:%20http://evil.com",
            "admin%0d%0aAuthorization:%20Bearer%20hijacked-token"
    })
    @DisplayName("Known percent-encoded header injections are rejected by the parameter pipeline")
    void shouldRejectKnownEncodedHeaderInjectionAttacks(String parameterValue) {
        assertRejected(parameterValuePipeline, parameterValue, DECODED_LINE_BREAK_IN_PARAMETER_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test\r\n\r\n<html><body><h1>Hacked</h1></body></html>",
            "normal\r\n\r\n<script>document.location='http://evil.com'</script>",
            "value\r\nContent-Length: 0\r\n\r\nHTTP/1.1 200 OK",
            "input\n\nHTTP/1.1 302 Found\nLocation: javascript:alert('XSS')",
            "test\r\nSet-Cookie: session=hijacked\r\n\r\n<html>Controlled</html>",
            "normal\r\n\r\nHTTP/1.1 301 Moved\r\nLocation: http://evil.com"
    })
    @DisplayName("Raw response splitting is rejected by the header-value pipeline")
    void shouldRejectRawResponseSplitting(String headerValue) {
        assertRejected(headerValuePipeline, headerValue, RAW_LINE_BREAK_IN_HEADER_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test%0d%0a%0d%0a%3Chtml%3E%3Cbody%3E%3Ch1%3EHacked%3C/h1%3E%3C/body%3E%3C/html%3E",
            "value%0d%0aContent-Length:%200%0d%0a%0d%0aHTTP/1.1%20200%20OK",
            "test%0d%0aSet-Cookie:%20session=hijacked%0d%0a%0d%0a%3Chtml%3EControlled%3C/html%3E",
            "normal%0d%0a%0d%0aHTTP/1.1%20301%20Moved%0d%0aLocation:%20http://evil.com"
    })
    @DisplayName("Percent-encoded response splitting is rejected by the parameter pipeline")
    void shouldRejectEncodedResponseSplitting(String parameterValue) {
        assertRejected(parameterValuePipeline, parameterValue, DECODED_LINE_BREAK_IN_PARAMETER_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test\r\nX-First: value1\r\nX-Second: value2\r\nX-Third: value3",
            "normal\r\nLocation: http://evil.com\r\nSet-Cookie: admin=true\r\nX-Injected: success",
            "input\nContent-Type: text/html\nCache-Control: no-cache\nX-Custom: injected",
            "test\r\nAuthorization: Bearer token\r\nX-Role: admin",
            "multi\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Credentials: true"
    })
    @DisplayName("Raw multi-line header injections are rejected by the header-value pipeline")
    void shouldRejectRawMultiLineHeaderInjections(String headerValue) {
        assertRejected(headerValuePipeline, headerValue, RAW_LINE_BREAK_IN_HEADER_VALUE);
    }

    @Test
    @DisplayName("A double-encoded CRLF is rejected by the parameter pipeline as double encoding")
    void shouldRejectDoubleEncodedLineBreak() {
        assertRejected(parameterValuePipeline, "test%250d%250aX-Injected:%20value",
                UrlSecurityFailureType.DOUBLE_ENCODING);
    }

    @Test
    @DisplayName("An overlong UTF-8 line feed is rejected by the parameter pipeline as invalid encoding")
    void shouldRejectOverlongEncodedLineFeed() {
        assertRejected(parameterValuePipeline, "normal%c0%8aLocation:%20http://evil.com",
                UrlSecurityFailureType.INVALID_ENCODING);
    }

    @Test
    @DisplayName("A lone NEL byte is rejected by the parameter pipeline as invalid encoding")
    void shouldRejectLoneNextLineByte() {
        assertRejected(parameterValuePipeline, "data%85X-NEL:%20nextline", UrlSecurityFailureType.INVALID_ENCODING);
    }

    @Test
    @DisplayName("A UTF-8 encoded NEL is rejected by the parameter pipeline as a control character")
    void shouldRejectEncodedNextLineCharacter() {
        assertRejected(parameterValuePipeline, "data%c2%85X-NEL:%20nextline",
                UrlSecurityFailureType.CONTROL_CHARACTERS);
    }

    @Test
    @DisplayName("An encoded byte-order mark is rejected by the parameter pipeline as an invalid character")
    void shouldRejectEncodedByteOrderMark() {
        assertRejected(parameterValuePipeline, "input%ef%bb%bfX-BOM:%20header",
                UrlSecurityFailureType.INVALID_CHARACTER);
    }

    @Test
    @DisplayName("A UTF-16 spelled CRLF is rejected by the parameter pipeline for its encoded null byte")
    void shouldRejectUtf16SpelledLineBreak() {
        assertRejected(parameterValuePipeline, "encoded%0d%00%0a%00X-Wide:%20value",
                UrlSecurityFailureType.NULL_BYTE_INJECTION);
    }

    @Test
    @DisplayName("A literal backslash escape is not a line break: the header-value pipeline accepts it unchanged")
    void shouldAcceptLiteralBackslashEscapeInHeaderValue() {
        String headerValue = "data\\r\\nX-Injected: header";

        Optional<String> validated = assertDoesNotThrow(() -> headerValuePipeline.validate(headerValue));

        assertEquals(Optional.of(headerValue), validated,
                "Backslash-r backslash-n is four visible characters and no line break");
        assertEquals(0, eventCounter.getTotalCount(), "An accepted value records no security event");
    }

    @Test
    @DisplayName("A literal backslash escape is rejected by the parameter pipeline for the backslash itself")
    void shouldRejectLiteralBackslashEscapeInParameterValue() {
        assertRejected(parameterValuePipeline, "data\\r\\nX-Injected:header",
                UrlSecurityFailureType.INVALID_CHARACTER);
    }

    private void assertRejected(HttpSecurityValidator pipeline, String attack, UrlSecurityFailureType expected) {
        var exception = assertThrows(UrlSecurityException.class, () -> pipeline.validate(attack),
                () -> "Header injection attack should be rejected: " + attack);

        assertEquals(expected, exception.getFailureType(), () -> "Unexpected verdict for: " + attack);
        assertEquals(attack, exception.getOriginalInput(), "Original input should be preserved in exception");
        assertEquals(1, eventCounter.getCount(expected), () -> "Exactly one " + expected + " event should be recorded");
    }
}
