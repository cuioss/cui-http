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
package de.cuioss.http.security.data;

import de.cuioss.tools.string.Splitter;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Utility class for parsing attribute values from HTTP headers and cookie strings.
 *
 * <p>This utility provides a common implementation for extracting attribute values
 * from strings that follow the semicolon-separated pattern common in HTTP headers
 * and cookie attributes (e.g., "name=value; attr1=val1; attr2=val2").</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Case Insensitive</strong> - Attribute name matching is case-insensitive</li>
 *   <li><strong>Parsing</strong> - Handles edge cases like missing values and whitespace</li>
 *   <li><strong>Stateless</strong> - All methods are static and thread-safe</li>
 *   <li><strong>Utility Class</strong> - Cannot be instantiated</li>
 * </ul>
 *
 * <h3>Supported Formats</h3>
 * <ul>
 *   <li>Cookie attributes: "sessionId=ABC123; Domain=example.com; Secure; HttpOnly"</li>
 *   <li>Header parameters: "multipart/form-data; boundary=----WebKitFormBoundary"</li>
 *   <li>Content-Type: "text/html; charset=UTF-8"</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Extract domain from cookie attributes
 * String cookieAttrs = "Domain=example.com; Path=/; Secure; HttpOnly";
 * Optional&lt;String&gt; domain = AttributeParser.extractAttributeValue(cookieAttrs, "Domain");
 * // Returns: Optional.of("example.com")
 *
 * // Extract charset from content type
 * String contentType = "text/html; charset=UTF-8; boundary=xyz";
 * Optional&lt;String&gt; charset = AttributeParser.extractAttributeValue(contentType, "charset");
 * // Returns: Optional.of("UTF-8")
 *
 * // Extract a quoted charset - the surrounding quotes are stripped
 * String quotedContentType = "text/html; charset=\"UTF-8\"";
 * Optional&lt;String&gt; quotedCharset = AttributeParser.extractAttributeValue(quotedContentType, "charset");
 * // Returns: Optional.of("UTF-8")
 *
 * // An unbalanced quote is not a quoted-string and is preserved verbatim
 * Optional&lt;String&gt; unbalanced = AttributeParser.extractAttributeValue("name=\"abc", "name");
 * // Returns: Optional.of("\"abc")
 *
 * // Missing attribute
 * Optional&lt;String&gt; missing = AttributeParser.extractAttributeValue(cookieAttrs, "MaxAge");
 * // Returns: Optional.empty()
 * </pre>
 *
 * <p><strong>Package-private:</strong> This class is intended for internal use within the
 * data package for parsing HTTP-related attribute strings.</p>
 *
 * @since 1.0
 * @see Cookie
 * @see HTTPBody
 */
final class AttributeParser {

    private AttributeParser() {
        // Utility class
    }

    /**
     * Extracts the value of a named attribute from a string containing semicolon-separated attributes.
     *
     * <p>This method performs case-insensitive matching for the attribute name and handles
     * common edge cases like missing values, trailing/leading whitespace, and attributes
     * at the end of the string.</p>
     *
     * <p><strong>Quoted values:</strong> RFC 6265 and RFC 7231 both permit an attribute value
     * to be a {@code quoted-string}. After whitespace trimming, a value that is at least two
     * characters long and both starts and ends with a double quote has that surrounding pair
     * stripped, and the RFC 7230 {@code quoted-pair} escapes {@code \"} and {@code \\} within
     * the remainder are resolved to {@code "} and {@code \} respectively. So
     * {@code charset="UTF-8"} yields {@code UTF-8}, and {@code name="say \"hi\""} yields
     * {@code say "hi"}. A value with an unbalanced quote is not a quoted-string and is returned
     * unchanged: this covers a value carrying only a leading quote, only a trailing one, or one
     * whose trailing quote is itself escaped by an odd number of preceding backslashes (so
     * {@code name="abc\"} is returned verbatim rather than partially stripped).</p>
     *
     * <p><strong>Implementation Note:</strong> This method uses simple semicolon splitting
     * which is sufficient for the current use cases (cookie attributes per RFC 6265 and
     * charset extraction from Content-Type headers). It does NOT handle quoted values
     * containing semicolons (e.g., profile="url;version=1"). Current usage patterns don't
     * require this complexity as cookie values and charset values don't contain semicolons.
     * If such support is needed in the future, a stateful parser respecting quoted strings
     * would be required.</p>
     *
     * @param attributeString The string containing attributes (e.g., "name=value; other=value2"), may be null
     * @param attributeName The name of the attribute to extract (case-insensitive)
     * @return An Optional containing the attribute value if found, or empty otherwise
     */
    static Optional<String> extractAttributeValue(@Nullable String attributeString, String attributeName) {
        if (attributeString == null || attributeString.isEmpty()) {
            return Optional.empty();
        }

        // Split by semicolons to process each attribute individually
        for (String trimmedAttr : Splitter.on(';').trimResults().omitEmptyStrings().splitToList(attributeString)) {
            int equalsIndex = trimmedAttr.indexOf('=');

            if (equalsIndex > 0) {
                // Extract the key part before '=' (without trimming for strict RFC compliance)
                String key = trimmedAttr.substring(0, equalsIndex);

                // RFC 6265 requires strict formatting - no spaces around '='
                // Only trim the key if it doesn't have trailing spaces (strict parsing)
                String trimmedKey = key.trim();
                if (!key.equals(trimmedKey)) {
                    // Key has trailing spaces - this violates RFC 6265 strict formatting
                    continue;
                }

                // Check for exact match (case-insensitive)
                if (trimmedKey.equalsIgnoreCase(attributeName)) {
                    // Extract value after '=' and trim whitespace per RFC 6265
                    String value = trimmedAttr.substring(equalsIndex + 1);
                    // RFC 6265 allows trimming whitespace from attribute values
                    String trimmedValue = value.trim();

                    // Unwrap a well-formed quoted-string (which may be empty for "name=")
                    return Optional.of(unquote(trimmedValue));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Unwraps a well-formed RFC 7230 {@code quoted-string}, returning any other value unchanged.
     *
     * <p>A value qualifies as a quoted-string only when it is at least two characters long, both
     * starts and ends with a double quote, and its trailing quote is a real delimiter rather than
     * an escaped one. For such a value the surrounding quote pair is removed and every
     * {@code quoted-pair} escape within the remainder is resolved: {@code \"} becomes {@code "}
     * and {@code \\} becomes {@code \}. A backslash followed by any other character is not an
     * escape this parser recognises and is preserved verbatim.</p>
     *
     * <p>A value is unbalanced — and is therefore NOT a quoted-string, and is returned unchanged
     * rather than being silently corrupted by a partial strip — in any of three cases: it carries
     * only a leading quote, only a trailing quote, or a trailing quote preceded by an odd number
     * of backslashes. In the third case the final quote is itself escaped and so closes nothing,
     * which is why {@code "abc\"} is returned verbatim.</p>
     *
     * @param value The trimmed attribute value to unwrap, never null
     * @return The unquoted and unescaped value when {@code value} is a well-formed quoted-string,
     * otherwise {@code value} itself
     */
    private static String unquote(String value) {
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return value;
        }

        // A trailing quote preceded by an odd number of backslashes is itself escaped, so it is
        // not a closing delimiter and the value is unbalanced rather than a quoted-string.
        int precedingBackslashes = 0;
        for (int index = value.length() - 2; index >= 0 && value.charAt(index) == '\\'; index--) {
            precedingBackslashes++;
        }
        if (precedingBackslashes % 2 != 0) {
            return value;
        }

        String inner = value.substring(1, value.length() - 1);
        StringBuilder unescaped = new StringBuilder(inner.length());
        int index = 0;
        while (index < inner.length()) {
            char current = inner.charAt(index);
            if (current == '\\' && index + 1 < inner.length()) {
                char next = inner.charAt(index + 1);
                if (next == '"' || next == '\\') {
                    // Consume both the backslash and the character it escapes.
                    unescaped.append(next);
                    index += 2;
                    continue;
                }
            }
            unescaped.append(current);
            index++;
        }
        return unescaped.toString();
    }
}