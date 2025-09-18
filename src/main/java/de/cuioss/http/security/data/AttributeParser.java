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

import java.util.Optional;

/**
 * Utility class for parsing attribute values from strings like HTTP headers and cookies.
 *
 * <p>This class provides a common implementation for extracting attribute values
 * from strings that follow the pattern "attributeName=value" where attributes
 * are separated by semicolons.</p>
 *
 * @since 2.5
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
     * @param attributeString The string containing attributes (e.g., "name=value; other=value2")
     * @param attributeName The name of the attribute to extract (case-insensitive)
     * @return An Optional containing the attribute value if found, or empty otherwise
     */
    static Optional<String> extractAttributeValue(String attributeString, String attributeName) {
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