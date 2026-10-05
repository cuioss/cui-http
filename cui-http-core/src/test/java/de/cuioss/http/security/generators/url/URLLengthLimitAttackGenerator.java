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
package de.cuioss.http.security.generators.url;

import de.cuioss.http.security.config.SecurityDefaults;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

import java.util.Objects;

/**
 * Generator for length limit attack patterns.
 *
 * <p>
 * This generator creates length limit attack vectors that attempt to exploit length limitations
 * to cause denial of service, buffer overflows, or bypass security controls. A length limit is a
 * property of one HTTP component, so every value is the content of exactly one component - the
 * {@link Surface} the instance was created for - and never a URL assembled from several of them.
 * </p>
 *
 * <h3>Surfaces</h3>
 * <ul>
 *   <li>{@link Surface#URL_PATH} (the no-argument constructor) - long segments, deep nesting and
 *       many short segments</li>
 *   <li>{@link Surface#PARAMETER_NAME} ({@link ForParameterName}) - long parameter names</li>
 *   <li>{@link Surface#PARAMETER_VALUE} ({@link ForParameterValue}) - long parameter values</li>
 *   <li>{@link Surface#HEADER_NAME} ({@link ForHeaderName}) - long header names</li>
 *   <li>{@link Surface#HEADER_VALUE} ({@link ForHeaderValue}) - long header values</li>
 * </ul>
 *
 * <h3>Length Invariant</h3>
 *
 * <p>Every value this generator emits is longer than the strict limit of its surface
 * ({@link Surface#strictLimit()}). That is the defining property of the generator: a value that
 * does not exceed the limit it targets is not a length-limit attack at all. Each surface also
 * reaches a value beyond its default and beyond its lenient limit.</p>
 *
 * <h3>Character Invariant</h3>
 *
 * <p>Every value consists exclusively of characters its surface admits and carries no
 * percent-escape, traversal sequence or other attack marker. Length is therefore the only reason
 * a pipeline can have to reject it - a path value in particular carries no {@code ?}, {@code #}
 * or space. Branches that need a repeated token build it through
 * {@code repeat(token, minCount, maxCount)} rather than
 * {@code Generators.strings(token, min, max)} — the latter treats its first argument as an
 * <em>alphabet</em> and would emit a short scramble of the token's characters, leaving the value
 * far under the limit.</p>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>RFC 3986 - Uniform Resource Identifier (URI): Generic Syntax</li>
 *   <li>RFC 7230 - HTTP/1.1 Message Syntax and Routing</li>
 *   <li>OWASP - Application Denial of Service</li>
 *   <li>CWE-400 - Uncontrolled Resource Consumption</li>
 *   <li>CWE-770 - Allocation of Resources Without Limits or Throttling</li>
 *   <li>CWE-120 - Buffer Copy without Checking Size of Input</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * &#64;ParameterizedTest
 * &#64;TypeGeneratorSource(value = URLLengthLimitAttackGenerator.class, count = 100)
 * void shouldRejectOverlongPath(String overlongPath) {
 *     var exception = assertThrows(UrlSecurityException.class,
 *         () -> strictPathPipeline.validate(overlongPath));
 *     assertEquals(UrlSecurityFailureType.PATH_TOO_LONG, exception.getFailureType());
 * }
 * </pre>
 *
 *
 * @author Claude Code Generator
 * @since 1.0
 */
public class URLLengthLimitAttackGenerator implements TypedGenerator<String> {

    /**
     * The HTTP component a generated value is shaped for, together with the three preset limits
     * of that component.
     */
    public enum Surface {

        /** A URL path component. */
        URL_PATH(ValidationType.URL_PATH, SecurityDefaults.MAX_PATH_LENGTH_STRICT,
            SecurityDefaults.MAX_PATH_LENGTH_DEFAULT, SecurityDefaults.MAX_PATH_LENGTH_LENIENT),

        /** A query parameter name. */
        PARAMETER_NAME(ValidationType.PARAMETER_NAME, SecurityDefaults.MAX_PARAMETER_NAME_LENGTH_STRICT,
                SecurityDefaults.MAX_PARAMETER_NAME_LENGTH_DEFAULT, SecurityDefaults.MAX_PARAMETER_NAME_LENGTH_LENIENT),

        /** A query parameter value. */
        PARAMETER_VALUE(ValidationType.PARAMETER_VALUE, SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_STRICT,
                SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_DEFAULT, SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_LENIENT),

        /** A header field name. */
        HEADER_NAME(ValidationType.HEADER_NAME, SecurityDefaults.MAX_HEADER_NAME_LENGTH_STRICT,
                SecurityDefaults.MAX_HEADER_NAME_LENGTH_DEFAULT, SecurityDefaults.MAX_HEADER_NAME_LENGTH_LENIENT),

        /** A header field value. */
        HEADER_VALUE(ValidationType.HEADER_VALUE, SecurityDefaults.MAX_HEADER_VALUE_LENGTH_STRICT,
                SecurityDefaults.MAX_HEADER_VALUE_LENGTH_DEFAULT, SecurityDefaults.MAX_HEADER_VALUE_LENGTH_LENIENT);

        private final ValidationType validationType;
        private final int strictLimit;
        private final int defaultLimit;
        private final int lenientLimit;

        Surface(ValidationType validationType, int strictLimit, int defaultLimit, int lenientLimit) {
            this.validationType = validationType;
            this.strictLimit = strictLimit;
            this.defaultLimit = defaultLimit;
            this.lenientLimit = lenientLimit;
        }

        /**
         * @return the validation type of the pipeline that owns this surface's length limit
         */
        public ValidationType validationType() {
            return validationType;
        }

        /**
         * @return the strict preset limit of this surface, which every generated value exceeds
         */
        public int strictLimit() {
            return strictLimit;
        }

        /**
         * @return the default preset limit of this surface
         */
        public int defaultLimit() {
            return defaultLimit;
        }

        /**
         * @return the lenient preset limit of this surface
         */
        public int lenientLimit() {
            return lenientLimit;
        }
    }

    /** The generator for {@link Surface#PARAMETER_NAME}, usable as a no-argument generator class. */
    public static final class ForParameterName extends URLLengthLimitAttackGenerator {

        /** Creates a generator that emits overlong parameter names. */
        public ForParameterName() {
            super(Surface.PARAMETER_NAME);
        }
    }

    /** The generator for {@link Surface#PARAMETER_VALUE}, usable as a no-argument generator class. */
    public static final class ForParameterValue extends URLLengthLimitAttackGenerator {

        /** Creates a generator that emits overlong parameter values. */
        public ForParameterValue() {
            super(Surface.PARAMETER_VALUE);
        }
    }

    /** The generator for {@link Surface#HEADER_NAME}, usable as a no-argument generator class. */
    public static final class ForHeaderName extends URLLengthLimitAttackGenerator {

        /** Creates a generator that emits overlong header names. */
        public ForHeaderName() {
            super(Surface.HEADER_NAME);
        }
    }

    /** The generator for {@link Surface#HEADER_VALUE}, usable as a no-argument generator class. */
    public static final class ForHeaderValue extends URLLengthLimitAttackGenerator {

        /** Creates a generator that emits overlong header values. */
        public ForHeaderValue() {
            super(Surface.HEADER_VALUE);
        }
    }

    /**
     * The number of arms every surface selects among. Three of them are the tier arms - just
     * over the strict, the default and the lenient limit - so each tier is reachable on every
     * surface.
     */
    public static final int ARM_COUNT = 8;

    private final Surface surface;

    /** Creates a generator that emits overlong URL paths. */
    public URLLengthLimitAttackGenerator() {
        this(Surface.URL_PATH);
    }

    /**
     * Creates a generator for the given surface.
     *
     * @param surface the component every generated value is shaped for, must not be null
     */
    public URLLengthLimitAttackGenerator(Surface surface) {
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
        int arm = selection(ARM_COUNT);
        return switch (surface) {
            case URL_PATH -> createOverlongPath(arm);
            case PARAMETER_NAME -> createOverlongParameterName(arm);
            case PARAMETER_VALUE -> createOverlongParameterValue(arm);
            case HEADER_NAME -> createOverlongHeaderName(arm);
            case HEADER_VALUE -> createOverlongHeaderValue(arm);
        };
    }

    /**
     * Creates an overlong URL path: letters, digits, {@code /} and {@code _} only.
     */
    private String createOverlongPath(int arm) {
        return switch (arm) {
            case 0 -> "/api/" + letters(1025, 1060); // single segment just over STRICT
            case 1 -> "/data/" + letters(4097, 4150); // single segment just over DEFAULT
            case 2 -> "/resource/" + letters(8193, 8250); // single segment just over LENIENT
            case 3 -> "/" + repeat("dir/", 260, 320) + "file"; // deep nesting
            case 4 -> "/" + repeat("a/", 520, 640) + "target"; // many single-character segments
            case 5 -> "/service/dir_" + letters(520, 560) + "/file_" + letters(520, 560); // several long segments
            case 6 -> "/" + letters(1030, 1080) + "/endpoint/file"; // long leading segment
            default -> "/" + repeat("folder/subfolder/", 62, 90) + "destination"; // alternating segments
        };
    }

    /**
     * Creates an overlong parameter name: letters, digits and {@code _} only.
     */
    private String createOverlongParameterName(int arm) {
        return switch (arm) {
            case 0 -> letters(65, 100); // just over STRICT
            case 1 -> letters(129, 200); // just over DEFAULT
            case 2 -> letters(257, 300); // just over LENIENT
            case 3 -> "param_" + letters(60, 110); // prefixed name
            case 4 -> "field_name_" + letters(55, 100); // descriptive prefix
            case 5 -> repeat("parameter_", 7, 12) + "name"; // repeated token
            case 6 -> "query_string_parameter_name_" + letters(40, 80); // very descriptive name
            default -> letters(1025, 1100); // name as long as an overlong value
        };
    }

    /**
     * Creates an overlong parameter value: letters, digits, {@code _} and {@code ,} only.
     */
    private String createOverlongParameterValue(int arm) {
        return switch (arm) {
            case 0 -> letters(1025, 1060); // just over STRICT
            case 1 -> letters(2049, 2100); // just over DEFAULT
            case 2 -> letters(8193, 8250); // just over LENIENT
            case 3 -> "value_" + letters(1020, 1060); // prefixed value
            case 4 -> repeat("term,", 210, 260); // repeated list entries
            case 5 -> letters(3000, 3200); // between DEFAULT and LENIENT
            case 6 -> repeat("chunk_" + letters(50, 100) + ",", 20, 40); // structured data
            default -> letters(12000, 15000); // memory exhaustion
        };
    }

    /**
     * Creates an overlong header name: letters, digits and {@code -} only.
     */
    private String createOverlongHeaderName(int arm) {
        return switch (arm) {
            case 0 -> "X-" + letters(63, 100); // just over STRICT
            case 1 -> "X-" + letters(127, 200); // just over DEFAULT
            case 2 -> "X-" + letters(255, 300); // just over LENIENT
            case 3 -> "X-Custom-" + letters(60, 110); // prefixed name
            case 4 -> "X-" + repeat("Forwarded-", 7, 12) + "For"; // repeated token
            case 5 -> letters(65, 128); // no vendor prefix
            case 6 -> "X-Very-Long-Application-Header-Name-" + letters(40, 80); // very descriptive name
            default -> "X-" + letters(1025, 1100); // name as long as an overlong value
        };
    }

    /**
     * Creates an overlong header value: letters, digits, space, {@code ,}, {@code ;} and
     * {@code =} only.
     */
    private String createOverlongHeaderValue(int arm) {
        return switch (arm) {
            case 0 -> "Bearer " + letters(1020, 1060); // just over STRICT
            case 1 -> "Bearer " + letters(2045, 2100); // just over DEFAULT
            case 2 -> "Bearer " + letters(8190, 8250); // just over LENIENT
            case 3 -> repeat("token, ", 150, 200); // repeated list entries
            case 4 -> "session=" + letters(1020, 1060) + "; Path=x"; // cookie-like value
            case 5 -> letters(3000, 3200); // between DEFAULT and LENIENT
            case 6 -> repeat("key=" + letters(20, 30) + "; ", 40, 60); // structured data
            default -> letters(12000, 15000); // memory exhaustion
        };
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }

    /**
     * Selects a random index in {@code [0, bound)} using the cui-test-generator
     * infrastructure, making selection seed-reproducible (governed by the framework
     * seed).
     *
     * @param bound exclusive upper bound (number of choices), must be positive
     * @return a pseudo-random index in {@code [0, bound)}
     */
    private int selection(int bound) {
        return Generators.integers(0, bound - 1).next();
    }

    private String letters(int minLength, int maxLength) {
        return Generators.letterStrings(minLength, maxLength).next();
    }

    /**
     * Repeats a multi-character token a seed-reproducible number of times.
     *
     * <p>This is deliberately <em>not</em> {@code Generators.strings(token, min, max)}: that
     * factory treats its first argument as an alphabet and draws {@code min..max}
     * <em>characters</em> from it, so a multi-character token yields a short scramble of the
     * token's characters rather than the intended repetition. Every attack branch that
     * needs a repeated token uses this helper, so the produced component length is
     * {@code token.length() * count} and can be reasoned about against the length limits the
     * generator targets.</p>
     *
     * @param token the token to repeat, must not be empty
     * @param minCount minimum number of repetitions, inclusive
     * @param maxCount maximum number of repetitions, inclusive
     * @return the token repeated between {@code minCount} and {@code maxCount} times
     */
    private String repeat(String token, int minCount, int maxCount) {
        return token.repeat(Generators.integers(minCount, maxCount).next());
    }
}
