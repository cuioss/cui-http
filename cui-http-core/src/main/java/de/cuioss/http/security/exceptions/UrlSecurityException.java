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
package de.cuioss.http.security.exceptions;

import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import lombok.Builder;
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
// No equals/hashCode is generated: a previous @EqualsAndHashCode(callSuper = true) was
// misleading because Throwable does not override equals/hashCode, so callSuper resolved to
// reference identity and the field-based comparison never took effect. This exception
// therefore uses the inherited identity semantics (as instances are transient and compared
// by reference), which is the same behavior the annotation actually produced.
public class UrlSecurityException extends RuntimeException {

    /**
     * Pre-compiled pattern for neutralising line-forging code points in log output.
     *
     * <p>Matches every code point that can terminate a line in a log viewer or a JSON-lines
     * consumer: the C0 controls (U+0000-U+001F), DEL (U+007F), the C1 controls
     * (U+0080-U+009F, notably NEL U+0085), and the Unicode line and paragraph separators
     * U+2028 and U+2029.</p>
     */
    private static final Pattern CONTROL_CHARS_PATTERN =
            Pattern.compile("[\\x00-\\x1F\\x7F-\\u009F\\u2028\\u2029]");

    /**
     * Maximum number of characters of rendered detail either rendering path emits.
     *
     * <p>Both {@link #getMessage()} and {@link #toString()} bound the same field to this limit, so
     * an unbounded {@code detail} cannot inflate a log line through either path (CWE-400).</p>
     */
    private static final int MAX_RENDERED_DETAIL_LENGTH = 200;

    /** Marker appended by both rendering paths when the detail was cut at the limit. */
    private static final String TRUNCATION_MARKER = "...";

    @Getter
    private final UrlSecurityFailureType failureType;
    @Getter
    private final ValidationType validationType;
    @Getter
    private final String originalInput;
    @Nullable
    private final String sanitizedInput;
    @Nullable
    private final String detail;

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
     * Builds an error message from the exception components.
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
            sb.append(" - ").append(escapeAndBound(detail));
        }

        sb.append(" (input: ").append(describeRedactedInput(originalInput)).append(")");

        return sb.toString();
    }

    /**
     * Describes the offending input without reproducing it.
     *
     * <p>{@code HTTPHeaderValidationPipeline} validates {@code Authorization} header values, so a
     * rejected bearer token or session cookie would otherwise be written verbatim into every
     * {@code getMessage()} log statement. Only the length is reported: no content, no hash and no
     * fingerprint, and no configuration knob - the redaction is unconditional, so credential
     * material cannot reach a log by construction rather than by correct configuration.</p>
     *
     * <p>Both the original and the sanitized input are rendered through this helper, because the
     * sanitized form derives from the same rejected header value and therefore carries the same
     * credential material. Callers that genuinely need a value opt in through
     * {@link #getOriginalInput()} or {@link #getSanitizedInput()} at their own trust boundary.</p>
     *
     * @param input The input that caused the failure
     * @return {@code <redacted, null>} for a null input, otherwise {@code <redacted, length=N>}
     */
    private static String describeRedactedInput(@Nullable String input) {
        return input == null
                ? "<redacted, null>"
                : "<redacted, length=%d>".formatted(input.length());
    }

    /**
     * Escapes control characters in text rendered into the exception message and bounds the result.
     *
     * <p>The message is what callers habitually log, so a raw CR/LF reaching it would let an
     * attacker-supplied fragment forge a log line (CWE-117 / CWE-93). Control characters are
     * rendered in the {@code U+XXXX} shape used by
     * {@code CharacterValidationStage.handleInvalidCharacter}, which keeps the offending code
     * point readable instead of collapsing it into an opaque placeholder.</p>
     *
     * <p>Escaping is expansive - one control character renders as six - so escaping alone would
     * let an unbounded {@code detail} amplify the message sixfold (CWE-400). The escaped text is
     * therefore bounded by {@link #MAX_RENDERED_DETAIL_LENGTH} with the {@link #TRUNCATION_MARKER}
     * {@link #truncateForLogging(String)} already applies, so both rendering paths bound the same
     * neutralised operand at the same limit rather than merely producing similar output. The cut
     * is taken between rendered characters, so a {@code U+XXXX} sequence is never split.</p>
     *
     * @param text The text to render
     * @return The text with every control character replaced by its escaped form, bounded to
     *         {@link #MAX_RENDERED_DETAIL_LENGTH} characters plus the truncation marker
     */
    private static String escapeAndBound(String text) {
        StringBuilder rendered = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            String escaped = CONTROL_CHARS_PATTERN.matcher(String.valueOf(current)).matches()
                    ? "U+%04X".formatted((int) current)
                    : String.valueOf(current);
            if (rendered.length() + escaped.length() > MAX_RENDERED_DETAIL_LENGTH) {
                return rendered.append(TRUNCATION_MARKER).toString();
            }
            rendered.append(escaped);
        }
        return rendered.toString();
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

        if (safe.length() > MAX_RENDERED_DETAIL_LENGTH) {
            return safe.substring(0, MAX_RENDERED_DETAIL_LENGTH) + TRUNCATION_MARKER;
        }

        return safe;
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "failureType=" + failureType +
                ", validationType=" + validationType +
                ", originalInput=" + describeRedactedInput(originalInput) +
                ", sanitizedInput='" + describeRedactedInput(sanitizedInput) + '\'' +
                ", detail='" + (detail != null ? truncateForLogging(detail) : null) + '\'' +
                ", cause=" + getCause() +
                '}';
    }

}