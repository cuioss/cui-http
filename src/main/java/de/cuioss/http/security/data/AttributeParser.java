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
package de.cuioss.http.security.data;

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
 *   <li><strong>Robust Parsing</strong> - Handles edge cases like missing values and whitespace</li>
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
     * @param attributeString The string containing attributes (e.g., "name=value; other=value2"), may be null
     * @param attributeName The name of the attribute to extract (case-insensitive)
     * @return An Optional containing the attribute value if found, or empty otherwise
     */
    static Optional<String> extractAttributeValue(@Nullable String attributeString, String attributeName) {
        if (attributeString == null || attributeString.isEmpty()) {
            return Optional.empty();
        }

        String lowerAttrString = attributeString.toLowerCase();
        String lowerAttrName = attributeName.toLowerCase();

        // Look for "attributeName=" pattern
        String searchPattern = lowerAttrName + "=";
        int startIndex = lowerAttrString.indexOf(searchPattern);

        if (startIndex == -1) {
            return Optional.empty();
        }

        // Find the start of the value
        int valueStart = startIndex + searchPattern.length();
        if (valueStart >= attributeString.length()) {
            return Optional.empty();
        }

        // Find the end of the value (semicolon or end of string)
        int valueEnd = attributeString.indexOf(';', valueStart);
        if (valueEnd == -1) {
            valueEnd = attributeString.length();
        }

        return Optional.of(attributeString.substring(valueStart, valueEnd).trim());
    }
}