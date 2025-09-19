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
package de.cuioss.http.security.validation;

import de.cuioss.http.security.core.ValidationType;

import java.util.BitSet;

/**
 * RFC-compliant character set definitions for HTTP component validation.
 *
 * <p>This utility class provides pre-computed BitSet instances containing allowed characters
 * for different HTTP components according to RFC 3986 (URI) and RFC 7230 (HTTP) specifications.
 * All character sets are optimized for high-performance validation with O(1) character lookups.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>RFC Compliance</strong> - Strict adherence to HTTP and URI specifications</li>
 *   <li><strong>Performance Optimized</strong> - Pre-computed BitSets for O(1) character validation</li>
 *   <li><strong>Thread Safety</strong> - Immutable after initialization, safe for concurrent access</li>
 *   <li><strong>Memory Efficient</strong> - Shared instances reduce memory overhead</li>
 * </ul>
 *
 * <h3>Character Set Categories</h3>
 * <ul>
 *   <li><strong>RFC3986_UNRESERVED</strong> - Basic unreserved characters from RFC 3986</li>
 *   <li><strong>RFC3986_PATH_CHARS</strong> - Characters allowed in URL paths</li>
 *   <li><strong>RFC3986_QUERY_CHARS</strong> - Characters allowed in URL query parameters</li>
 *   <li><strong>RFC7230_HEADER_CHARS</strong> - Characters allowed in HTTP headers</li>
 *   <li><strong>HTTP_BODY_CHARS</strong> - Characters allowed in HTTP request/response bodies</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Get character set for URL path validation
 * BitSet pathChars = CharacterValidationConstants.getCharacterSet(ValidationType.URL_PATH);
 *
 * // Check if character is allowed in URL paths
 * char ch = '/';
 * boolean isAllowed = pathChars.get(ch); // Returns true
 *
 * // Validate string characters
 * String input = "/api/users";
 * for (int i = 0; i &lt; input.length(); i++) {
 *     char c = input.charAt(i);
 *     if (!pathChars.get(c)) {
 *         throw new IllegalArgumentException("Invalid character: " + c);
 *     }
 * }
 * </pre>
 *
 * <h3>Performance Characteristics</h3>
 * <ul>
 *   <li>O(1) character lookup time using BitSet.get()</li>
 *   <li>Minimal memory footprint - shared across all validators</li>
 *   <li>No runtime computation - all sets pre-computed during class loading</li>
 *   <li>Thread-safe concurrent access without synchronization</li>
 * </ul>
 *
 * <h3>RFC References</h3>
 * <ul>
 *   <li><strong>RFC 3986</strong> - Uniform Resource Identifier (URI) character definitions</li>
 *   <li><strong>RFC 7230</strong> - HTTP/1.1 Message Syntax and Routing header field definitions</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong> These character sets define <em>allowed</em> characters only.
 * Additional security validation (pattern matching, length limits, etc.) should be applied
 * by higher-level validation stages.</p>
 * <p>
 * Implements: Task V5 from HTTP verification specification
 *
 * @see ValidationType
 * @see de.cuioss.http.security.validation.CharacterValidationStage
 * @since 1.0
 */
public final class CharacterValidationConstants {

    private CharacterValidationConstants() {
        // Utility class
    }

    /**
     * RFC 3986 unreserved characters: ALPHA / DIGIT / "-" / "." / "_" / "~".
     * <p>These are the basic safe characters allowed in URIs without percent-encoding.</p>
     */
    public static final BitSet RFC3986_UNRESERVED;

    /**
     * RFC 3986 path characters including unreserved + path-specific characters.
     * <p>Includes all unreserved characters plus: / @ : ! $ &amp; ' ( ) * + , ; =</p>
     */
    public static final BitSet RFC3986_PATH_CHARS;

    /**
     * RFC 3986 query characters including unreserved + query-specific characters.
     * <p>Includes all unreserved characters plus: ? &amp; = ! $ ' ( ) * + , ;</p>
     */
    public static final BitSet RFC3986_QUERY_CHARS;

    /**
     * RFC 7230 header field characters (visible ASCII minus delimiters).
     * <p>Includes space through tilde (32-126) plus tab character.</p>
     */
    public static final BitSet RFC7230_HEADER_CHARS;

    /**
     * HTTP body content characters (permissive for JSON, XML, text, etc.).
     * <p>Includes printable ASCII (32-126), tab, LF, CR, and extended ASCII (128-255).</p>
     */
    public static final BitSet HTTP_BODY_CHARS;

    static {
        // Initialize RFC3986_UNRESERVED
        BitSet unreserved = new BitSet(256);
        // ALPHA
        for (int i = 'A'; i <= 'Z'; i++) unreserved.set(i);
        for (int i = 'a'; i <= 'z'; i++) unreserved.set(i);
        // DIGIT
        for (int i = '0'; i <= '9'; i++) unreserved.set(i);
        // "-" / "." / "_" / "~"
        unreserved.set('-');
        unreserved.set('.');
        unreserved.set('_');
        unreserved.set('~');
        RFC3986_UNRESERVED = unreserved;

        // Initialize RFC3986_PATH_CHARS
        BitSet pathChars = new BitSet(256);
        pathChars.or(unreserved);  // Include all unreserved chars
        pathChars.set('/');
        pathChars.set('@');
        pathChars.set(':');
        // sub-delims for path: "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
        "!$&'()*+,;=".chars().forEach(pathChars::set);
        RFC3986_PATH_CHARS = pathChars;

        // Initialize RFC3986_QUERY_CHARS
        BitSet queryChars = new BitSet(256);
        queryChars.or(unreserved);  // Include all unreserved chars
        queryChars.set('?');
        queryChars.set('&');
        queryChars.set('=');
        // sub-delims for query
        "!$'()*+,;".chars().forEach(queryChars::set);
        RFC3986_QUERY_CHARS = queryChars;

        // Initialize RFC7230_HEADER_CHARS
        BitSet headerChars = new BitSet(256);
        // RFC 7230: For header values, allow most visible ASCII plus space and tab
        // Only exclude control chars and characters that could break HTTP parsing
        for (int i = 32; i <= 126; i++) { // Include space (32) through tilde (126)
            headerChars.set(i);
        }
        headerChars.set('\t'); // Tab is allowed in headers
        // Only exclude characters that could break HTTP: CR, LF, NULL
        // Note: Other dangerous chars are handled at application level
        RFC7230_HEADER_CHARS = headerChars;

        // Initialize HTTP_BODY_CHARS (very permissive for body content)
        BitSet bodyChars = new BitSet(256);
        // Allow all printable ASCII and extended characters
        for (int i = 32; i <= 126; i++) { // ASCII printable characters
            bodyChars.set(i);
        }
        // Allow common whitespace characters
        bodyChars.set('\t');  // Tab (0x09)
        bodyChars.set('\n');  // Line feed (0x0A)
        bodyChars.set('\r');  // Carriage return (0x0D)
        // Allow extended ASCII and Unicode range (128-255)
        for (int i = 128; i <= 255; i++) {
            bodyChars.set(i);
        }
        // Note: Null bytes and other control chars (1-31) are excluded by default
        // They can be allowed via configuration if needed
        HTTP_BODY_CHARS = bodyChars;
    }

    /**
     * Returns the appropriate character set for the specified validation type.
     *
     * <p>This method provides a centralized mapping from validation types to their
     * corresponding RFC-compliant character sets. The returned BitSet is the actual
     * instance (not a copy) for performance reasons and must not be modified.</p>
     *
     * <h4>Validation Type Mappings:</h4>
     * <ul>
     *   <li>{@code URL_PATH} → {@link #RFC3986_PATH_CHARS}</li>
     *   <li>{@code PARAMETER_NAME, PARAMETER_VALUE} → {@link #RFC3986_QUERY_CHARS}</li>
     *   <li>{@code HEADER_NAME, HEADER_VALUE} → {@link #RFC7230_HEADER_CHARS}</li>
     *   <li>{@code BODY} → {@link #HTTP_BODY_CHARS}</li>
     *   <li>{@code COOKIE_NAME, COOKIE_VALUE} → {@link #RFC3986_UNRESERVED}</li>
     * </ul>
     *
     * @param type The validation type specifying which HTTP component is being validated
     * @return The corresponding BitSet containing allowed characters for the validation type
     * @throws NullPointerException if {@code type} is null
     * @see ValidationType
     * @see #RFC3986_PATH_CHARS
     * @see #RFC3986_QUERY_CHARS
     * @see #RFC7230_HEADER_CHARS
     * @see #HTTP_BODY_CHARS
     * @see #RFC3986_UNRESERVED
     */
    public static BitSet getCharacterSet(ValidationType type) {
        return switch (type) {
            case URL_PATH -> RFC3986_PATH_CHARS;
            case PARAMETER_NAME, PARAMETER_VALUE -> RFC3986_QUERY_CHARS;
            case HEADER_NAME, HEADER_VALUE -> RFC7230_HEADER_CHARS;
            case BODY -> HTTP_BODY_CHARS;
            case COOKIE_NAME, COOKIE_VALUE -> RFC3986_UNRESERVED;
        };
    }
}