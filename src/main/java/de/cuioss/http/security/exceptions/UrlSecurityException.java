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
package de.cuioss.http.security.exceptions;

import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Main exception for HTTP security validation failures.
 * Extends RuntimeException to enable clean functional interface usage and fail-fast behavior.
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Fail Secure</strong> - Throws on any security violation for immediate handling</li>
 *   <li><strong>Rich Context</strong> - Provides detailed failure information for debugging and logging</li>
 *   <li><strong>Builder Pattern</strong> - Fluent API for exception construction</li>
 *   <li><strong>Immutable</strong> - All fields are final and thread-safe</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Simple security violation
 * throw UrlSecurityException.builder()
 *     .failureType(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED)
 *     .validationType(ValidationType.URL_PATH)
 *     .originalInput("../../../etc/passwd")
 *     .build();
 *
 * // Detailed violation with sanitized input
 * throw UrlSecurityException.builder()
 *     .failureType(UrlSecurityFailureType.INVALID_CHARACTER)
 *     .validationType(ValidationType.PARAMETER_VALUE)
 *     .originalInput("user&lt;script&gt;test(1)&lt;/script&gt;")
 *     .sanitizedInput("userscripttest1script")
 *     .detail("Removed script tags and special characters")
 *     .build();
 *
 * // Chained exception
 * throw UrlSecurityException.builder()
 *     .failureType(UrlSecurityFailureType.INVALID_ENCODING)
 *     .validationType(ValidationType.URL_PATH)
 *     .originalInput("%ZZ%invalid")
 *     .cause(originalException)
 *     .build();
 * </pre>
 *
 * Implements: Task B2 from HTTP verification specification
 *
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = true)
public class UrlSecurityException extends RuntimeException {

    /**
     * Pre-compiled pattern for removing control characters from log output.
     * Matches control characters (0x00-0x1F) and DEL character (0x7F).
     */
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");

    @Getter private final UrlSecurityFailureType failureType;
    @Getter private final ValidationType validationType;
    @Getter private final String originalInput;
    @Nullable private final String sanitizedInput;
    @Nullable private final String detail;

    /**
     * Creates a new UrlSecurityException with the specified parameters.
     * Use the {@link #builder()} method for easier construction.
     *
     * @param failureType The type of security failure that occurred
     * @param validationType The type of HTTP component being validated
     * @param originalInput The original input that caused the security violation
     * @param sanitizedInput Optional sanitized version of the input (may be null)
     * @param detail Optional additional detail about the failure (may be null)
     * @param cause Optional underlying cause exception (may be null)
     */
    @Builder
    private UrlSecurityException(UrlSecurityFailureType failureType,
            ValidationType validationType,
            String originalInput,
            @Nullable String sanitizedInput,
            @Nullable String detail,
            @Nullable Throwable cause) {
        super(buildMessage(failureType, validationType, originalInput, detail), cause);
        this.failureType = failureType;
        this.validationType = validationType;
        this.originalInput = originalInput;
        this.sanitizedInput = sanitizedInput;
        this.detail = detail;
    }

    /**
     * Gets the sanitized version of the input, if available.
     *
     * @return The sanitized input wrapped in Optional, or empty if not provided
     */
    public Optional<String> getSanitizedInput() {
        return Optional.ofNullable(sanitizedInput);
    }

    /**
     * Gets additional detail about the security failure.
     *
     * @return Additional detail wrapped in Optional, or empty if not provided
     */
    public Optional<String> getDetail() {
        return Optional.ofNullable(detail);
    }

    /**
     * Builds a comprehensive error message from the exception components.
     *
     * @param failureType The type of failure
     * @param validationType The type of validation
     * @param originalInput The input that caused the failure
     * @param detail Optional additional detail
     * @return A formatted error message
     */
    private static String buildMessage(UrlSecurityFailureType failureType,
            ValidationType validationType,
            String originalInput,
            @Nullable String detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("Security validation failed [").append(validationType).append("]: ");
        sb.append(failureType.getDescription());

        if (detail != null && !detail.trim().isEmpty()) {
            sb.append(" - ").append(detail);
        }

        // Safely truncate input for logging to prevent log injection
        String truncatedInput = truncateForLogging(originalInput);
        sb.append(" (input: '").append(truncatedInput).append("')");

        return sb.toString();
    }

    /**
     * Safely truncates input for logging to prevent log injection attacks.
     *
     * @param input The input to truncate
     * @return Safe truncated input
     */
    private static String truncateForLogging(@Nullable String input) {
        if (input == null) {
            return "null";
        }

        // Remove control characters and limit length
        String safe = CONTROL_CHARS_PATTERN.matcher(input).replaceAll("?");

        if (safe.length() > 200) {
            return safe.substring(0, 200) + "...";
        }

        return safe;
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "failureType=" + failureType +
                ", validationType=" + validationType +
                ", originalInput='" + truncateForLogging(originalInput) + '\'' +
                ", sanitizedInput='" + (sanitizedInput != null ? truncateForLogging(sanitizedInput) : null) + '\'' +
                ", detail='" + detail + '\'' +
                ", cause=" + getCause() +
                '}';
    }

}