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

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import java.util.BitSet;

/**
 * Character validation stage that enforces RFC-compliant character sets for HTTP components.
 *
 * <p>This stage validates input characters against component-specific allowed character sets,
 * ensuring compliance with HTTP specifications and preventing character-based security attacks.
 * It performs comprehensive character validation including null byte detection, control character
 * filtering, and percent-encoding validation.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>RFC Compliance</strong> - Enforces RFC 3986 (URI) and RFC 7230 (HTTP) character rules</li>
 *   <li><strong>Security First</strong> - Rejects dangerous characters before any processing</li>
 *   <li><strong>Context Aware</strong> - Different character sets for different HTTP components</li>
 *   <li><strong>Performance Optimized</strong> - Uses BitSet for O(1) character lookups</li>
 *   <li><strong>Configurable</strong> - Allows fine-tuning of character validation rules</li>
 * </ul>
 *
 * <h3>Character Validation Rules</h3>
 * <ul>
 *   <li><strong>URL Paths</strong> - RFC 3986 unreserved + path-specific characters</li>
 *   <li><strong>Parameters</strong> - RFC 3986 query characters with percent-encoding support</li>
 *   <li><strong>Headers</strong> - RFC 7230 visible ASCII minus delimiters</li>
 *   <li><strong>Cookies</strong> - Restricted character set for cookie safety</li>
 *   <li><strong>Bodies</strong> - Content-type specific character validation</li>
 * </ul>
 *
 * <h3>Security Features</h3>
 * <ul>
 *   <li><strong>Null Byte Detection</strong> - Prevents null byte injection attacks</li>
 *   <li><strong>Control Character Filtering</strong> - Blocks dangerous control characters</li>
 *   <li><strong>Percent Encoding Validation</strong> - Validates hex digit sequences</li>
 *   <li><strong>High-Bit Character Control</strong> - Configurable handling of non-ASCII characters</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Create character validation stage
 * SecurityConfiguration config = SecurityConfiguration.defaults();
 * CharacterValidationStage validator = new CharacterValidationStage(config, ValidationType.URL_PATH);
 *
 * // Validate URL path characters
 * try {
 *     validator.validate("/api/users/123"); // Valid path characters
 *     validator.validate("/api/../etc/passwd"); // May contain invalid traversal patterns
 * } catch (UrlSecurityException e) {
 *     logger.warn("Invalid characters detected: {}", e.getFailureType());
 * }
 *
 * // Validate parameter with percent encoding
 * CharacterValidationStage paramValidator = new CharacterValidationStage(config, ValidationType.PARAMETER_VALUE);
 * try {
 *     paramValidator.validate("hello%20world"); // Valid percent-encoded space
 *     paramValidator.validate("hello%00world"); // Null byte - will be rejected
 * } catch (UrlSecurityException e) {
 *     logger.warn("Character validation failed: {}", e.getDetail());
 * }
 * </pre>
 *
 * <h3>Configuration Options</h3>
 * <ul>
 *   <li><strong>allowNullBytes</strong> - Whether to permit null bytes (default: false)</li>
 *   <li><strong>allowControlCharacters</strong> - Whether to permit control characters (default: false)</li>
 *   <li><strong>allowHighBitCharacters</strong> - Whether to permit non-ASCII characters (default: false)</li>
 * </ul>
 *
 * <h3>Performance Characteristics</h3>
 * <ul>
 *   <li>O(n) time complexity where n is input length</li>
 *   <li>O(1) character lookup using BitSet</li>
 *   <li>Early termination on first invalid character</li>
 *   <li>Minimal memory allocation during validation</li>
 * </ul>
 *
 * @see CharacterValidationConstants
 * @see SecurityConfiguration
 * @see ValidationType
 * @since 1.0
 */
@EqualsAndHashCode
@ToString
public final class CharacterValidationStage implements HttpSecurityValidator {

    private final BitSet allowedChars;
    private final ValidationType validationType;
    private final boolean allowPercentEncoding;
    private final boolean allowNullBytes;
    private final boolean allowControlCharacters;
    private final boolean allowHighBitCharacters;

    public CharacterValidationStage(SecurityConfiguration config, ValidationType type) {
        this.validationType = type;
        this.allowNullBytes = config.allowNullBytes();
        this.allowControlCharacters = config.allowControlCharacters();
        this.allowHighBitCharacters = config.allowHighBitCharacters();
        // Use the shared BitSet directly - it's read-only after initialization
        this.allowedChars = CharacterValidationConstants.getCharacterSet(type);

        // Determine if percent encoding is allowed based on type
        this.allowPercentEncoding = switch (type) {
            case URL_PATH, PARAMETER_NAME, PARAMETER_VALUE -> true;
            default -> false;  // HEADER_NAME, HEADER_VALUE and others don't allow percent encoding
        };
    }

    @Override
    @SuppressWarnings("squid:S3516")
    public String validate(@Nullable String value) throws UrlSecurityException {
        // Quick check for null/empty
        if (value == null || value.isEmpty()) {
            return value;
        }

        validateCharacters(value);
        return value;
    }

    /**
     * Validates all characters in the input string.
     * @param value The string to validate
     * @throws UrlSecurityException if any character validation fails
     */
    private void validateCharacters(String value) throws UrlSecurityException {
        int i = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);

            // Check for null byte FIRST (highest priority security check)
            if (ch == '\0') {
                handleNullByte(value, i);
            }

            // Handle percent encoding
            if (ch == '%' && allowPercentEncoding) {
                validatePercentEncoding(value, i);
                i += 3; // Skip the percent sign and two hex digits
                continue;
            }

            // Check if character is allowed based on configuration and character sets
            if (!isCharacterAllowed(ch)) {
                handleInvalidCharacter(value, ch, i);
            }
            i++;
        }
    }

    /**
     * Handles null byte detection.
     * @param value The original input string
     * @param position The position of the null byte
     * @throws UrlSecurityException if null bytes are not allowed
     */
    private void handleNullByte(String value, int position) throws UrlSecurityException {
        if (!allowNullBytes) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.NULL_BYTE_INJECTION)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Null byte detected at position " + position)
                    .build();
        }
    }

    /**
     * Handles invalid character detection.
     * @param value The original input string
     * @param ch The invalid character
     * @param position The position of the invalid character
     * @throws UrlSecurityException for the invalid character
     */
    private void handleInvalidCharacter(String value, char ch, int position) throws UrlSecurityException {
        UrlSecurityFailureType failureType = getFailureTypeForCharacter(ch);
        throw UrlSecurityException.builder()
                .failureType(failureType)
                .validationType(validationType)
                .originalInput(value)
                .detail("Invalid character '" + ch + "' (0x" + Integer.toHexString(ch).toUpperCase() + ") at position " + position)
                .build();
    }

    /**
     * Validates percent encoding at the given position.
     * @param value The string to validate
     * @param position The position of the percent sign
     * @throws UrlSecurityException if the percent encoding is invalid
     */
    private void validatePercentEncoding(String value, int position) throws UrlSecurityException {
        // Must be followed by two hex digits
        if (position + 2 >= value.length()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_ENCODING)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Incomplete percent encoding at position " + position)
                    .build();
        }

        char hex1 = value.charAt(position + 1);
        char hex2 = value.charAt(position + 2);
        if (isNotHexDigit(hex1) || isNotHexDigit(hex2)) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_ENCODING)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Invalid hex digits in percent encoding at position " + position)
                    .build();
        }

        // Check for encoded null byte %00
        if (hex1 == '0' && hex2 == '0' && !allowNullBytes) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.NULL_BYTE_INJECTION)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Encoded null byte (%00) detected at position " + position)
                    .build();
        }
    }

    private boolean isNotHexDigit(char ch) {
        return !((ch >= '0' && ch <= '9') ||
                (ch >= 'A' && ch <= 'F') ||
                (ch >= 'a' && ch <= 'f'));
    }

    /**
     * Checks if a character is allowed based on configuration flags and character sets.
     */
    private boolean isCharacterAllowed(char ch) {
        // Null byte (0) - should be allowed if configured (already checked earlier but may reach here)
        if (ch == 0) {
            return allowNullBytes;
        }

        // Control characters (1-31, excluding null which is handled above)
        if (ch <= 31) {
            // Always allow common whitespace characters that are in the base character set
            if (allowedChars.get(ch)) {
                return true;
            }
            // Other control characters depend on configuration
            return allowControlCharacters;
        }

        // Characters 32-127 (basic ASCII) - check against the base character set
        if (ch <= 127) {
            return allowedChars.get(ch);
        }

        // Extended ASCII characters (128-255) - check configuration and base character set
        if (ch <= 255) {
            return allowHighBitCharacters || allowedChars.get(ch);
        }

        // Unicode characters above 255 - allow only if high-bit characters are allowed
        // and the validation type supports it (e.g., BODY content is more permissive than URL paths)
        // Always reject combining characters (U+0300-U+036F) as they can cause normalization issues
        if (ch >= 0x0300 && ch <= 0x036F) {
            return false;
        }
        return allowHighBitCharacters && supportsUnicodeCharacters();
    }

    /**
     * Determines the appropriate failure type for a rejected character.
     */
    private UrlSecurityFailureType getFailureTypeForCharacter(char ch) {
        // Null byte (0)
        if (ch == 0) {
            return UrlSecurityFailureType.NULL_BYTE_INJECTION;
        }

        // Control characters (1-31)
        if (ch <= 31) {
            // For headers, control characters are just invalid characters per RFC
            // For other contexts, they're specifically flagged as control characters for security
            if (validationType == ValidationType.HEADER_NAME || validationType == ValidationType.HEADER_VALUE) {
                return UrlSecurityFailureType.INVALID_CHARACTER;
            }
            // If it's in the base character set, it's just an invalid character for this context
            if (allowedChars.get(ch)) {
                return UrlSecurityFailureType.INVALID_CHARACTER;
            }
            return UrlSecurityFailureType.CONTROL_CHARACTERS;
        }

        // All other invalid characters (including high-bit and Unicode)
        return UrlSecurityFailureType.INVALID_CHARACTER;
    }

    /**
     * Determines if the current validation type supports Unicode characters beyond 255.
     * URL paths and parameter validation are more restrictive per RFC 3986,
     * while body and header content can be more permissive.
     */
    private boolean supportsUnicodeCharacters() {
        return switch (validationType) {
            case BODY -> true;  // Body content can contain Unicode
            case HEADER_VALUE -> true;  // Header values can contain Unicode in some cases
            case URL_PATH, PARAMETER_NAME, PARAMETER_VALUE -> false;  // RFC 3986 is ASCII-based
            case HEADER_NAME -> false;  // Header names should be ASCII
            case COOKIE_NAME, COOKIE_VALUE -> false;  // Cookies should be ASCII-safe
        };
    }
}