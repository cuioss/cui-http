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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

import java.util.Arrays;
import java.util.List;

/**
 * Generator for URL length limit attack patterns.
 *
 * <p>
 * This generator creates comprehensive URL length limit attack vectors that
 * attempt to exploit URL length limitations to cause denial of service,
 * buffer overflows, or bypass security controls. The generator covers various
 * URL length attack techniques used by attackers to exploit web applications
 * through excessive URL sizes.
 * </p>
 *
 * <h3>Attack Types Generated</h3>
 * <ul>
 *   <li><strong>Basic Length Overflow</strong> - URLs exceeding standard length limits</li>
 *   <li><strong>Path Component Overflow</strong> - Extremely long path segments</li>
 *   <li><strong>Query Parameter Overflow</strong> - Long query strings and parameters</li>
 *   <li><strong>Fragment Overflow</strong> - Long URL fragments</li>
 *   <li><strong>Hostname Overflow</strong> - Long hostname components</li>
 *   <li><strong>Repeated Parameter Attack</strong> - Many identical parameters</li>
 *   <li><strong>Deep Path Nesting</strong> - Many nested directory levels</li>
 *   <li><strong>Long Parameter Names</strong> - Extremely long parameter names</li>
 *   <li><strong>Long Parameter Values</strong> - Extremely long parameter values</li>
 *   <li><strong>Mixed Length Attacks</strong> - Combination of long components</li>
 *   <li><strong>Buffer Overflow Patterns</strong> - Patterns designed to cause overflows</li>
 *   <li><strong>Memory Exhaustion</strong> - URLs designed to consume memory</li>
 *   <li><strong>Parser Confusion</strong> - Long URLs with parsing challenges</li>
 *   <li><strong>Encoding Length Attacks</strong> - Length amplification via encoding</li>
 *   <li><strong>Algorithmic Complexity</strong> - URLs causing processing slowdown</li>
 * </ul>
 *
 * <h3>Length Invariant</h3>
 *
 * <p>Every value this generator emits is longer than
 * {@link de.cuioss.http.security.config.SecurityDefaults#MAX_PATH_LENGTH_STRICT} characters and
 * begins either with {@code /} or with an {@code http://} / {@code https://} scheme. That is the
 * defining property of the generator: a value that does not exceed the limit it targets is not a
 * length-limit attack at all. Branches that need a repeated token build it through
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
 * void shouldRejectURLLengthLimitAttacks(String lengthAttack) {
 *     assertThrows(UrlSecurityException.class,
 *         () -> pipeline.validate(lengthAttack));
 * }
 * </pre>
 *
 * Implements: Task T19 from HTTP verification specification
 *
 * @author Claude Code Generator
 * @since 1.0
 */
public class URLLengthLimitAttackGenerator implements TypedGenerator<String> {

    private static final List<String> BASE_PATTERNS = Arrays.asList(
            "/api",
            "/search",
            "/data",
            "/resource",
            "/service",
            "/endpoint",
            "/handler",
            "/process",
            "/action",
            "/request"
    );

    private final AttackTypeSelector attackTypeSelector = new AttackTypeSelector(13); // Removed encoding attacks (13,14) - they test encoding not length

    @Override
    public String next() {
        String basePattern = BASE_PATTERNS.get(hashBasedSelection(BASE_PATTERNS.size()));

        return switch (attackTypeSelector.nextAttackType()) {
            case 0 -> createBasicLengthOverflow(basePattern);
            case 1 -> createPathComponentOverflow(basePattern);
            case 2 -> createQueryParameterOverflow(basePattern);
            case 3 -> createFragmentOverflow(basePattern);
            case 4 -> createHostnameOverflow(basePattern);
            case 5 -> createRepeatedParameterAttack(basePattern);
            case 6 -> createDeepPathNesting(basePattern);
            case 7 -> createLongParameterNames(basePattern);
            case 8 -> createLongParameterValues(basePattern);
            case 9 -> createMixedLengthAttacks(basePattern);
            case 10 -> createBufferOverflowPatterns(basePattern);
            case 11 -> createMemoryExhaustionAttack(basePattern);
            case 12 -> createAlgorithmicComplexity(basePattern);
            // Removed case 13 (encoding attacks) and case 14 - they test encoding validation not length validation
            default -> createBasicLengthOverflow(basePattern);
        };
    }

    /**
     * Creates basic URL length overflow attacks exceeding standard limits.
     */
    private String createBasicLengthOverflow(String pattern) {
        // Test realistic length limits: STRICT=1024, DEFAULT=4096, LENIENT=8192
        // Generate URLs just over these limits to test actual validation logic
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> pattern + "?" + Generators.letterStrings(1030, 1050).next(); // Just over STRICT limit
            case 1 -> pattern + "/" + Generators.letterStrings(1025, 1040).next(); // Barely over STRICT
            case 2 -> pattern + "?" + "param=" + Generators.letterStrings(4100, 4150).next(); // Just over DEFAULT limit
            case 3 -> pattern + "/" + Generators.letterStrings(4097, 4120).next(); // Barely over DEFAULT
            case 4 -> pattern + "?" + Generators.letterStrings(8200, 8250).next(); // Just over LENIENT limit
            case 5 -> pattern + "/" + Generators.letterStrings(8193, 8210).next(); // Barely over LENIENT
            case 6 -> pattern + "/" + Generators.letterStrings(512, 512).next() + "/" + Generators.letterStrings(512, 512).next() + "?" + Generators.letterStrings(512, 512).next(); // Distributed length
            case 7 -> pattern + "?" + "field=" + Generators.letterStrings(2050, 2100).next(); // Medium overflow
            default -> pattern + "?" + Generators.letterStrings(1030, 1050).next(); // Default just over STRICT
        };
    }

    /**
     * Creates path component overflow attacks with extremely long path segments.
     */
    private String createPathComponentOverflow(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> pattern + "/" + repeat("segment/", 150, 180) + "file"; // Repeated segments to reach limits
            case 1 -> pattern + "/" + Generators.letterStrings(1030, 1050).next() + "/normal"; // Single long segment over STRICT
            case 2 -> pattern + "/" + "path_" + Generators.letterStrings(1030, 1050).next() + "/data"; // Long segment with prefix
            case 3 -> "/" + Generators.letterStrings(1030, 1080).next() + pattern + "/file"; // Long prefix path
            case 4 -> pattern + "/" + "dir_" + Generators.letterStrings(520, 560).next() + "/file_" + Generators.letterStrings(520, 560).next(); // Multiple segments
            case 5 -> pattern + "/" + "very_long_directory_name_" + Generators.letterStrings(1000, 1030).next(); // Descriptive long segment
            case 6 -> pattern + "/" + "component" + Generators.letterStrings(505, 530).next() + "/subdir" + Generators.letterStrings(505, 530).next() + "/file"; // Nested paths
            case 7 -> pattern + "/" + Generators.letterStrings(4100, 4150).next() + "/end"; // Just over DEFAULT limit
            default -> pattern + "/" + Generators.letterStrings(1030, 1050).next(); // Default just over STRICT
        };
    }

    /**
     * Creates query parameter overflow attacks with long query strings.
     */
    private String createQueryParameterOverflow(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> pattern + "?" + "param=" + Generators.letterStrings(1020, 1060).next(); // Parameter value over STRICT limit
            case 1 -> pattern + "?" + "data=" + Generators.letterStrings(520, 560).next() + "&info=" + Generators.letterStrings(520, 560).next(); // Multiple parameters
            case 2 -> pattern + "?" + "query=" + Generators.letterStrings(3800, 3900).next(); // Parameter near DEFAULT limit
            case 3 -> pattern + "?" + "search=" + repeat("term ", 210, 260); // Repeated terms to reach limit
            case 4 -> pattern + "?" + "content=" + Generators.letterStrings(1010, 1060).next() + "&type=json"; // Long parameter with normal
            case 5 -> pattern + "?" + repeat("input=value123&", 70, 100); // Many small parameters
            case 6 -> pattern + "?" + "buffer=" + Generators.letterStrings(7800, 7900).next(); // Near LENIENT limit
            case 7 -> pattern + "?" + "payload=" + Generators.letterStrings(1030, 1080).next() + "&extra=data"; // Just over STRICT with extra
            default -> pattern + "?" + "param=" + Generators.letterStrings(1030, 1050).next(); // Default just over STRICT
        };
    }

    /**
     * Creates fragment overflow attacks with long URL fragments.
     */
    private String createFragmentOverflow(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> pattern + "#" + Generators.letterStrings(1030, 1080).next(); // Fragment just over STRICT
            case 1 -> pattern + "?param=value#" + Generators.letterStrings(1030, 1080).next(); // Long fragment with query
            case 2 -> pattern + "/path#" + "section" + Generators.letterStrings(1030, 1080).next(); // Named fragment
            case 3 -> pattern + "#" + "anchor_" + Generators.letterStrings(1030, 1080).next(); // Fragment with prefix
            case 4 -> pattern + "?data=test#" + Generators.letterStrings(1030, 1080).next(); // Long fragment over STRICT
            case 5 -> pattern + "#" + repeat("part_", 210, 260); // Repeated fragment parts
            case 6 -> pattern + "#" + Generators.letterStrings(3800, 3900).next(); // Fragment near DEFAULT limit
            case 7 -> pattern + "/resource?id=123#" + "content_" + Generators.letterStrings(1030, 1080).next(); // Mixed with fragment
            default -> pattern + "#" + Generators.letterStrings(1030, 1080).next(); // Default fragment
        };
    }

    /**
     * Creates hostname overflow attacks with long hostname components.
     */
    private String createHostnameOverflow(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> "https://" + Generators.letterStrings(1030, 1080).next() + ".com" + pattern; // Single label far past DNS max (253)
            case 1 -> "https://" + Generators.letterStrings(520, 560).next() + "." + Generators.letterStrings(520, 560).next() + ".com" + pattern; // Two labels each far past DNS max (63)
            case 2 -> "https://" + repeat("subdomain.", 105, 130) + "example.com" + pattern; // Many subdomains
            case 3 -> "https://" + Generators.letterStrings(1030, 1080).next() + ".evil.com" + pattern; // Long subdomain
            case 4 -> "https://" + repeat("sub", 350, 420) + ".domain.com" + pattern; // Repeated subdomain parts
            case 5 -> "https://" + Generators.letterStrings(1030, 1080).next() + ".attacker.org" + pattern; // Long subdomain
            case 6 -> "https://" + Generators.letterStrings(1050, 1200).next() + ".malicious.net" + pattern; // Very long hostname part
            case 7 -> "https://" + repeat("long", 265, 320) + ".test.com" + pattern; // Multiple long parts
            default -> "https://" + Generators.letterStrings(1030, 1080).next() + ".com" + pattern; // Default long hostname
        };
    }

    /**
     * Creates repeated parameter attacks with many identical parameters.
     */
    private String createRepeatedParameterAttack(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> pattern + "?" + repeat("param=value&", 90, 130); // Many small parameters to reach limit
            case 1 -> pattern + "?" + repeat("data=test&", 105, 150); // Repeated parameters
            case 2 -> pattern + "?" + repeat("field=info&", 95, 140); // Parameter repetition
            case 3 -> pattern + "?" + repeat("item=" + Generators.letterStrings(20, 30).next() + "&", 40, 60); // Parameters with medium values
            case 4 -> pattern + "?" + repeat("query=search&", 80, 120); // Many search parameters
            case 5 -> pattern + "?" + repeat("param" + Generators.letterStrings(10, 15).next() + "=value&", 47, 70); // Varied parameter names
            case 6 -> pattern + "?" + repeat("test=data&", 105, 150); // Many test parameters
            case 7 -> pattern + "?" + repeat("key=value" + Generators.letterStrings(5, 10).next() + "&", 70, 100); // Mixed parameters
            default -> pattern + "?" + repeat("param=value&", 90, 130); // Default repeated parameters
        };
    }

    /**
     * Creates deep path nesting attacks with many directory levels.
     */
    private String createDeepPathNesting(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> "/" + repeat("dir/", 260, 320) + pattern.substring(1); // Many directory levels to reach limits
            case 1 -> "/" + repeat("level/", 175, 220) + "file"; // Deep levels
            case 2 -> pattern + "/" + repeat("sub/", 260, 320) + "resource"; // Nested levels
            case 3 -> "/" + repeat("path/", 210, 260) + "endpoint"; // Many path segments
            case 4 -> "/" + repeat("deep/", 110, 140) + repeat("very/", 110, 140) + "nested/" + pattern.substring(1); // Mixed depths
            case 5 -> "/" + repeat("dir" + hashBasedSelection(100) + "/", 210, 280) + "target"; // Varied directory names
            case 6 -> "/" + repeat("A/", 520, 640) + "final"; // Single-char directories
            case 7 -> "/" + repeat("folder/subfolder/", 62, 90) + "destination"; // Alternating paths
            default -> "/" + repeat("dir/", 260, 320) + pattern.substring(1); // Default deep nesting
        };
    }

    /**
     * Creates attacks with extremely long parameter names.
     */
    private String createLongParameterNames(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> pattern + "?" + Generators.letterStrings(1020, 1060).next() + "=value"; // Long parameter name over STRICT
            case 1 -> pattern + "?" + "param_" + Generators.letterStrings(1015, 1060).next() + "=data"; // Long name with prefix
            case 2 -> pattern + "?" + Generators.letterStrings(1010, 1060).next() + "=test&normal=ok"; // Long name with normal parameter
            case 3 -> pattern + "?" + "field_name_" + Generators.letterStrings(1005, 1050).next() + "=content"; // Descriptive long name
            case 4 -> pattern + "?" + Generators.letterStrings(3800, 3900).next() + "=info"; // Parameter name near DEFAULT limit
            case 5 -> pattern + "?" + repeat("parameter_" + Generators.letterStrings(20, 30).next() + "=value&", 30, 45); // Multiple medium names
            case 6 -> pattern + "?" + Generators.letterStrings(1030, 1080).next() + "=result"; // Just over STRICT limit
            case 7 -> pattern + "?" + "query_string_parameter_name_" + Generators.letterStrings(990, 1040).next() + "=search"; // Very descriptive name
            default -> pattern + "?" + Generators.letterStrings(1020, 1060).next() + "=value"; // Default long name
        };
    }

    /**
     * Creates attacks with extremely long parameter values.
     */
    private String createLongParameterValues(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> pattern + "?data=" + Generators.letterStrings(1030, 1080).next(); // Just over STRICT limit
            case 1 -> pattern + "?content=" + Generators.letterStrings(4100, 4150).next(); // Just over DEFAULT limit
            case 2 -> pattern + "?payload=" + Generators.letterStrings(8200, 8250).next(); // Just over LENIENT limit
            case 3 -> pattern + "?info=" + Generators.letterStrings(2000, 2100).next(); // Medium length value
            case 4 -> pattern + "?search=" + repeat("query ", 175, 220); // Repeated search terms
            case 5 -> pattern + "?input=" + Generators.letterStrings(3000, 3200).next(); // Large but reasonable value
            case 6 -> pattern + "?field=" + "value_" + Generators.letterStrings(1015, 1060).next(); // Long value with prefix
            case 7 -> pattern + "?buffer=" + Generators.letterStrings(7800, 7900).next(); // Near LENIENT limit
            default -> pattern + "?data=" + Generators.letterStrings(1030, 1080).next(); // Default just over STRICT
        };
    }

    /**
     * Creates mixed length attacks combining multiple long components.
     */
    private String createMixedLengthAttacks(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> "/" + Generators.letterStrings(350, 400).next() + pattern + "?" + "param=" + Generators.letterStrings(350, 400).next() + "#" + Generators.letterStrings(350, 400).next(); // Distributed length components
            case 1 -> pattern + "/" + Generators.letterStrings(560, 610).next() + "?" + Generators.letterStrings(460, 510).next() + "=value"; // Long path segment and parameter name
            case 2 -> "/" + repeat("path/", 15, 25) + pattern.substring(1) + "?" + "data=" + Generators.letterStrings(960, 1010).next(); // Deep path with long parameter
            case 3 -> pattern + "/" + Generators.letterStrings(400, 450).next() + "/" + Generators.letterStrings(400, 450).next() + "?" + "query=" + Generators.letterStrings(600, 650).next(); // Multiple medium components
            case 4 -> "/" + Generators.letterStrings(800, 850).next() + "?" + repeat("param" + Generators.letterStrings(15, 20).next() + "=" + Generators.letterStrings(15, 20).next() + "&", 8, 12); // Long path with parameters
            case 5 -> pattern + "/" + "segment_" + Generators.letterStrings(400, 450).next() + "?" + "field_" + Generators.letterStrings(200, 250).next() + "=" + Generators.letterStrings(400, 450).next() + "#anchor_" + Generators.letterStrings(200, 250).next(); // All components reasonable
            case 6 -> "/" + repeat("dir/", 30, 50) + "resource" + "?" + "buffer=" + Generators.letterStrings(910, 960).next(); // Deep nesting with parameter
            case 7 -> "https://" + Generators.letterStrings(80, 100).next() + ".example.com" + pattern + "/" + Generators.letterStrings(450, 500).next() + "?" + "data=" + Generators.letterStrings(560, 610).next(); // Long hostname, path, and parameter
            default -> pattern + "/" + Generators.letterStrings(510, 560).next() + "?" + "param=" + Generators.letterStrings(510, 560).next(); // Default mixed length
        };
    }

    /**
     * Creates buffer overflow patterns designed to trigger memory issues.
     */
    private String createBufferOverflowPatterns(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> pattern + "?" + Generators.letterStrings(8200, 8300).next(); // Just over LENIENT limit (testing buffer boundaries)
            case 1 -> pattern + "/" + Generators.letterStrings(4100, 4200).next() + "?" + "data=" + Generators.letterStrings(4100, 4200).next(); // DEFAULT limit overflow in both components
            case 2 -> pattern + "?" + "buffer=" + Generators.letterStrings(9000, 9500).next(); // Moderate buffer test
            case 3 -> "/" + Generators.letterStrings(2000, 2100).next() + pattern + "?" + "payload=" + Generators.letterStrings(6000, 6500).next(); // Distributed length test
            case 4 -> pattern + "?" + repeat("overflow" + Generators.letterStrings(20, 30).next() + "=data&", 35, 55); // Many parameters with patterns
            case 5 -> pattern + "/" + Generators.letterStrings(8300, 8400).next(); // Path component just over LENIENT
            case 6 -> pattern + "?" + "input=" + Generators.letterStrings(10000, 12000).next(); // Large but not extreme parameter
            case 7 -> pattern + "#" + Generators.letterStrings(7000, 7500).next(); // Large fragment
            default -> pattern + "?" + Generators.letterStrings(8200, 8300).next(); // Default buffer test
        };
    }

    /**
     * Creates memory exhaustion attacks designed to consume server memory.
     */
    private String createMemoryExhaustionAttack(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> pattern + "?" + "memory=" + Generators.letterStrings(12000, 15000).next(); // Large but reasonable parameter
            case 1 -> pattern + "/" + Generators.letterStrings(4000, 4500).next() + "?" + "data=" + Generators.letterStrings(4000, 4500).next(); // Distributed large components
            case 2 -> pattern + "?" + repeat("param" + hashBasedSelection(100) + "=" + Generators.letterStrings(20, 30).next() + "&", 100, 200); // Many parameters with varied data
            case 3 -> pattern + "?" + "exhaustion=" + Generators.letterStrings(20000, 25000).next(); // Large parameter test
            case 4 -> "/" + Generators.letterStrings(6000, 8000).next() + pattern; // Large path prefix
            case 5 -> pattern + "?" + "large_data=" + repeat("chunk" + Generators.letterStrings(50, 100).next(), 50, 100); // Structured data within limits
            case 6 -> pattern + "#" + Generators.letterStrings(10000, 15000).next(); // Large fragment
            case 7 -> pattern + "?" + "payload=" + Generators.letterStrings(30000, 35000).next(); // Large payload test
            default -> pattern + "?" + "memory=" + Generators.letterStrings(12000, 15000).next(); // Default memory test
        };
    }

    /**
     * Creates algorithmic complexity attacks causing processing slowdown.
     */
    private String createAlgorithmicComplexity(String pattern) {
        int attackType = hashBasedSelection(8);
        return switch (attackType) {
            case 0 -> pattern + "?" + repeat("a=b&", 260, 400); // Many small parameters within reason
            case 1 -> pattern + "/" + repeat("x/", 520, 700) + "target"; // Many small path segments
            case 2 -> "/" + repeat("../", 350, 450) + pattern; // Path traversal attempts within limits
            case 3 -> pattern + "?" + repeat("param" + hashBasedSelection(100) + "=value" + hashBasedSelection(100) + "&", 90, 150); // Varied parameter names
            case 4 -> pattern + "/" + repeat("segment" + hashBasedSelection(50), 130, 200); // Varied path segments
            case 5 -> pattern + "?" + "regex=" + repeat("(a+)+", 210, 260); // Regex complexity pattern
            case 6 -> pattern + "/" + repeat("a" + repeat("/b", 20, 40), 26, 45); // Nested pattern complexity
            case 7 -> pattern + "?" + repeat("key=value&", 105, 300); // Numerous but reasonable parameters
            default -> pattern + "?" + repeat("a=b&", 260, 400); // Default complexity attack
        };
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }

    /**
     * Selects a random index in {@code [0, bound)} using the cui-test-generator
     * infrastructure, making selection seed-reproducible (governed by the framework
     * seed) instead of deriving randomness from {@code System.nanoTime()}.
     *
     * @param bound exclusive upper bound (number of choices), must be positive
     * @return a pseudo-random index in {@code [0, bound)}
     */
    private int hashBasedSelection(int bound) {
        return Generators.integers(0, bound - 1).next();
    }

    /**
     * Repeats a multi-character token a seed-reproducible number of times.
     *
     * <p>This is deliberately <em>not</em> {@code Generators.strings(token, min, max)}: that
     * factory treats its first argument as an alphabet and draws {@code min..max}
     * <em>characters</em> from it, so a multi-character token yields a short scramble of the
     * token's characters rather than the intended repetition. Every attack branch below that
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

    /**
     * Helper class to cycle through attack types systematically.
     */
    private static class AttackTypeSelector {
        private final int maxTypes;
        private int currentType = 0;

        AttackTypeSelector(int maxTypes) {
            this.maxTypes = maxTypes;
        }

        int nextAttackType() {
            int type = currentType;
            currentType = (currentType + 1) % maxTypes;
            return type;
        }
    }
}