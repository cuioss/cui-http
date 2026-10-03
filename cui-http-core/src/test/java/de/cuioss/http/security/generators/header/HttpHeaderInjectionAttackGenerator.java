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

import de.cuioss.http.security.validation.CharacterValidationConstants;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * T15: HTTP Header Injection Attack Generator
 *
 * <p>
 * This generator creates HTTP header injection payloads: a benign leading token followed by a
 * line break and one or more injected header lines (or a whole injected response). Header
 * injection can lead to response splitting, cache poisoning, cross-site scripting and session
 * hijacking, because the line break ends the header the application meant to write.
 * </p>
 *
 * <h3>Component-shaped output, one surface per instance</h3>
 * <p>
 * Every value is the content of exactly <em>one</em> HTTP component - never an absolute URL with a
 * query string. The component is chosen by the {@link Surface} the instance was created for, and
 * {@link #getSurface()} reports it, so a consumer always knows which pipeline the value belongs
 * to:
 * </p>
 * <ul>
 *   <li>{@link Surface#HEADER_VALUE} (the no-argument constructor) - the line breaks are raw
 *       {@code CR} / {@code LF} characters, which is how the payload reaches a header value.
 *       Validate with {@code HTTPHeaderValidationPipeline} for {@code HEADER_VALUE}.</li>
 *   <li>{@link Surface#PARAMETER_VALUE} ({@link ForParameterValue}) - the same payload spelled as
 *       it travels in a query parameter value: the line breaks are percent-encoded
 *       ({@code %0d%0a}) and so is every other character that the RFC 3986 query character set
 *       does not admit, so the value contains nothing a parameter pipeline rejects before it
 *       decodes. Validate with {@code URLParameterValidationPipeline}.</li>
 * </ul>
 *
 * <h3>Attack Types Generated</h3>
 * <ul>
 *   <li>CRLF Injection - Carriage Return Line Feed character injection</li>
 *   <li>HTTP Response Splitting - Complete HTTP response manipulation</li>
 *   <li>Request-Header Injection - Forwarding and authorization header injection</li>
 *   <li>Cookie Injection Attacks - Malicious cookie header manipulation</li>
 *   <li>Location Header Injection - Redirect header manipulation</li>
 *   <li>Content-Type Header Injection - MIME type manipulation attacks</li>
 *   <li>Cache Poisoning Attacks - Cache-Control and related header manipulation</li>
 *   <li>Session Hijacking Headers - Session-related header injection</li>
 *   <li>XSS via Header Injection - Script injection through headers</li>
 *   <li>Authentication Header Bypass - Authorization header manipulation</li>
 *   <li>CORS Header Manipulation - Cross-origin header injection attacks</li>
 *   <li>Security Header Bypass - Security policy header manipulation</li>
 *   <li>Custom Header Injection - Application-specific header attacks</li>
 *   <li>Multi-line Header Injection - Complex multi-header attacks</li>
 *   <li>Line-Ending Variants - bare CR, bare LF, reversed and folded line breaks</li>
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
 * <h3>Relationship to {@link HTTPHeaderInjectionGenerator}</h3>
 * <p>
 * Both generators emit raw header-value fragments carrying literal line breaks.
 * {@link HTTPHeaderInjectionGenerator} (QI-6) assembles short fragments from small token pools
 * and additionally emits a null-byte arm; this class (T15) emits the fifteen documented attack
 * families and can spell each of them for the parameter-value surface as well.
 * </p>
 *
 * @author Generated for HTTP Security Validation (T15)
 * @version 1.0.0
 */
public class HttpHeaderInjectionAttackGenerator implements TypedGenerator<String> {

    /**
     * The HTTP component a generated payload is shaped for.
     */
    public enum Surface {

        /** A header field value: line breaks are raw {@code CR} / {@code LF} characters. */
        HEADER_VALUE,

        /**
         * A query parameter value: line breaks, and every other character outside the RFC 3986
         * query character set, are percent-encoded.
         */
        PARAMETER_VALUE
    }

    /**
     * The generator spelled for the parameter-value surface, usable wherever a no-argument
     * generator class is required (for instance {@code @TypeGeneratorSource}).
     */
    public static final class ForParameterValue extends HttpHeaderInjectionAttackGenerator {

        /** Creates a generator that emits parameter-value shaped payloads. */
        public ForParameterValue() {
            super(Surface.PARAMETER_VALUE);
        }
    }

    private static final String[] CRLF_INJECTION = {
            "normal\r\nX-Injected: malicious",
            "test\r\nLocation: http://evil.com",
            "value\nSet-Cookie: admin=true",
            "param\nContent-Type: text/html",
            "data\r\nX-Custom: injected",
            "input\r\nCache-Control: no-cache",
            "normal\r\n\r\n<html><script>alert('XSS')</script></html>"
    };

    private static final String[] RESPONSE_SPLITTING = {
            "test\r\n\r\n<html><body><h1>Hacked</h1></body></html>",
            "normal\r\n\r\n<script>document.location='http://evil.com'</script>",
            "value\r\nContent-Length: 0\r\n\r\nHTTP/1.1 200 OK\r\n",
            "param\n\nHTTP/1.1 302 Found\nLocation: javascript:alert('XSS')",
            "data\r\n\r\n<html><script>document.cookie='admin=true'</script>",
            "input\r\n\r\n<iframe src='http://attacker.com'></iframe>",
            "test\r\nSet-Cookie: session=hijacked\r\n\r\n<html>Controlled</html>"
    };

    private static final String[] REQUEST_HEADER_INJECTION = {
            "admin\r\nX-Admin: true",
            "user\r\nAuthorization: Bearer hijacked-token",
            "test\nX-Forwarded-For: 127.0.0.1",
            "value\nX-Real-IP: 192.168.1.100",
            "param\r\nX-Custom-Auth: bypassed",
            "data\r\nContent-Disposition: attachment; filename=evil.exe",
            "input\nX-Frame-Options: ALLOWALL"
    };

    private static final String[] COOKIE_INJECTION = {
            "normal\r\nSet-Cookie: admin=true; Path=/",
            "test\r\nSet-Cookie: session=ABC123; HttpOnly; Secure",
            "value\nSet-Cookie: role=administrator",
            "param\nSet-Cookie: auth=bypassed; Domain=.evil.com",
            "data\r\nSet-Cookie: csrf=disabled; SameSite=None",
            "input\r\nSet-Cookie: debug=enabled; Path=/admin",
            "user\r\nSet-Cookie: token=hijacked; expires=Thu, 31-Dec-2030 23:59:59 GMT"
    };

    private static final String[] LOCATION_INJECTION = {
            "safe.com\r\nLocation: http://evil.com",
            "redirect\r\nLocation: javascript:alert('XSS')",
            "normal\nLocation: data:text/html,<script>alert('XSS')</script>",
            "param\nLocation: vbscript:msgbox('XSS')",
            "test\r\nLocation: file:///etc/passwd",
            "value\r\nLocation: ftp://attacker.com/steal",
            "data\r\nLocation: //evil.com/phishing"
    };

    private static final String[] CONTENT_TYPE_INJECTION = {
            "text/html\r\nContent-Type: text/javascript",
            "application/json\r\nContent-Type: text/html",
            "text/plain\nContent-Type: application/octet-stream",
            "image/png\nContent-Type: text/html; charset=utf-7",
            "text/css\r\nContent-Type: application/x-shockwave-flash",
            "application/xml\r\nContent-Type: text/html",
            "text/javascript\r\nContent-Encoding: gzip"
    };

    private static final String[] CACHE_POISONING = {
            "normal\r\nCache-Control: public, max-age=31536000",
            "test\r\nPragma: no-cache\r\nExpires: Thu, 01 Jan 1970 00:00:00 GMT",
            "value\nCache-Control: no-store, must-revalidate",
            "param\nETag: \"hijacked-etag\"",
            "data\r\nVary: User-Agent, Accept-Language",
            "input\r\nLast-Modified: Wed, 21 Oct 2015 07:28:00 GMT",
            "cache\r\nAge: 0"
    };

    private static final String[] SESSION_HIJACKING = {
            "user\r\nSet-Cookie: JSESSIONID=hijacked",
            "test\r\nSet-Cookie: PHPSESSID=attacker-controlled",
            "normal\nSet-Cookie: session_id=stolen-session",
            "param\nSet-Cookie: auth_token=bypassed-token",
            "data\r\nSet-Cookie: user_session=admin-session",
            "value\r\nSet-Cookie: login_state=authenticated",
            "session\r\nSet-Cookie: csrf_token=disabled"
    };

    private static final String[] XSS_VIA_HEADER = {
            "test\r\nX-XSS-Protection: 0\r\nContent-Type: text/html\r\n\r\n<script>alert('XSS')</script>",
            "normal\r\nRefresh: 0; url=javascript:alert('XSS')",
            "value\nLink: <javascript:alert('XSS')>; rel=prefetch",
            "param\nContent-Disposition: inline; filename=\"<script>alert('XSS')</script>\"",
            "data\r\nX-Frame-Options: DENY\r\nContent-Type: text/html\r\n\r\n<script>alert('XSS')</script>",
            "input\r\nContent-Security-Policy: script-src 'unsafe-inline'",
            "xss\r\nX-Content-Type-Options: nosniff\r\n\r\n<img src=x onerror=alert('XSS')>"
    };

    private static final String[] AUTHENTICATION_BYPASS = {
            "user\r\nAuthorization: Basic YWRtaW46cGFzc3dvcmQ=",
            "test\r\nX-Forwarded-User: admin",
            "normal\nX-Remote-User: administrator",
            "param\nX-User-Role: admin",
            "data\r\nX-Auth-User: root",
            "value\r\nX-Forwarded-For: 127.0.0.1",
            "auth\r\nX-Real-IP: localhost"
    };

    private static final String[] CORS_MANIPULATION = {
            "normal\r\nAccess-Control-Allow-Origin: *",
            "test\r\nAccess-Control-Allow-Credentials: true",
            "value\nAccess-Control-Allow-Methods: GET, POST, PUT, DELETE",
            "param\nAccess-Control-Allow-Headers: *",
            "data\r\nAccess-Control-Max-Age: 86400",
            "input\r\nAccess-Control-Expose-Headers: *",
            "cors\r\nAccess-Control-Allow-Origin: http://evil.com"
    };

    private static final String[] SECURITY_HEADER_BYPASS = {
            "test\r\nStrict-Transport-Security: max-age=0",
            "normal\r\nX-Content-Type-Options: ",
            "value\nX-Frame-Options: ALLOWALL",
            "param\nContent-Security-Policy: default-src *",
            "data\r\nX-XSS-Protection: 0",
            "input\r\nReferrer-Policy: no-referrer-when-downgrade",
            "security\r\nFeature-Policy: geolocation *"
    };

    private static final String[] CUSTOM_HEADER_INJECTION = {
            "normal\r\nX-Custom-Admin: true",
            "test\r\nX-Debug-Mode: enabled",
            "value\nX-Internal-User: admin",
            "param\nX-Bypass-Auth: true",
            "data\r\nX-Override-Security: disabled",
            "input\r\nX-Special-Access: granted",
            "custom\r\nX-Application-Role: administrator"
    };

    private static final String[] MULTI_LINE_INJECTION = {
            "test\r\nX-First: value1\r\nX-Second: value2\r\nX-Third: value3",
            "normal\r\nLocation: http://evil.com\r\nSet-Cookie: admin=true\r\nX-Injected: success",
            "value\nContent-Type: text/html\nCache-Control: no-cache\nX-Custom: injected",
            "param\nSet-Cookie: session=hijacked\nLocation: javascript:alert('XSS')\nX-Admin: true",
            "data\r\nAuthorization: Bearer token\r\nX-Role: admin\r\nX-Debug: enabled",
            "input\r\nX-Frame-Options: DENY\r\nContent-Security-Policy: none\r\nX-XSS-Protection: 0",
            "multi\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Credentials: true"
    };

    private static final String[] LINE_ENDING_VARIANTS = {
            "test\rX-Injected: bare-carriage-return",
            "normal\nX-Injected: bare-line-feed",
            "value\n\rX-Injected: reversed-line-break",
            "param\r \nX-Injected: space-inside-line-break",
            "data\r\n X-Injected: folded-with-space",
            "input\r\n\tX-Injected: folded-with-tab",
            "ending\r\r\nX-Injected: doubled-carriage-return"
    };

    private static final String[][] FAMILIES = {
            CRLF_INJECTION, RESPONSE_SPLITTING, REQUEST_HEADER_INJECTION, COOKIE_INJECTION,
            LOCATION_INJECTION, CONTENT_TYPE_INJECTION, CACHE_POISONING, SESSION_HIJACKING,
            XSS_VIA_HEADER, AUTHENTICATION_BYPASS, CORS_MANIPULATION, SECURITY_HEADER_BYPASS,
            CUSTOM_HEADER_INJECTION, MULTI_LINE_INJECTION, LINE_ENDING_VARIANTS
    };

    /**
     * Every attack family {@link #next()} selects among, in the order the class Javadoc lists
     * them. Each family lists its payloads in the raw {@link Surface#HEADER_VALUE} spelling;
     * {@link #asParameterValue(String)} yields the {@link Surface#PARAMETER_VALUE} spelling.
     */
    public static final List<List<String>> ATTACK_FAMILIES = Stream.of(FAMILIES).map(List::of).toList();

    /** The number of attack families {@link #next()} selects among. */
    public static final int ATTACK_FAMILY_COUNT = FAMILIES.length;

    private final Surface surface;

    /** Creates a generator that emits header-value shaped payloads with raw line breaks. */
    public HttpHeaderInjectionAttackGenerator() {
        this(Surface.HEADER_VALUE);
    }

    /**
     * Creates a generator for the given surface.
     *
     * @param surface the component every generated payload is shaped for, must not be null
     */
    public HttpHeaderInjectionAttackGenerator(Surface surface) {
        this.surface = Objects.requireNonNull(surface, "surface");
    }

    /**
     * Reports the component every value of this instance is shaped for.
     *
     * @return the target surface, never null
     */
    public Surface getSurface() {
        return surface;
    }

    @Override
    public String next() {
        String[] family = FAMILIES[randomSelection(FAMILIES.length)];
        String headerValue = family[randomSelection(family.length)];
        return surface == Surface.HEADER_VALUE ? headerValue : asParameterValue(headerValue);
    }

    /**
     * Spells a raw header-injection payload as it travels in a query parameter value.
     *
     * <p>Every character the RFC 3986 query character set does not admit - the line breaks first
     * of all, but also space, angle brackets and double quotes - is percent-encoded with
     * lower-case hex digits; everything else is kept literally. The result therefore passes a
     * parameter pipeline's wire-form character validation untouched and reveals the line break
     * only once it is decoded.</p>
     *
     * @param headerValue the raw payload, ASCII only, must not be null
     * @return the percent-encoded parameter-value spelling of {@code headerValue}
     */
    public static String asParameterValue(String headerValue) {
        StringBuilder encoded = new StringBuilder(headerValue.length() + 32);
        for (int i = 0; i < headerValue.length(); i++) {
            char character = headerValue.charAt(i);
            if (CharacterValidationConstants.RFC3986_QUERY_CHARS.test(character)) {
                encoded.append(character);
            } else {
                encoded.append("%%%02x".formatted((int) character));
            }
        }
        return encoded.toString();
    }

    /**
     * Selects a random index in the range {@code [0, max)} using the
     * cui-test-generator infrastructure. This makes selection seed-reproducible
     * (governed by the framework seed) while allowing every {@link #next()}
     * invocation to cover any of the documented attack branches.
     *
     * @param max exclusive upper bound (number of choices), must be positive
     * @return a pseudo-random index in {@code [0, max)}
     */
    private int randomSelection(int max) {
        return Generators.integers(0, max - 1).next();
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
