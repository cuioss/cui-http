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
package de.cuioss.http.security.generators.encoding;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

/**
 * Generates various encoding combinations for bypass attempts.
 *
 * <p>QI-6: Converted from fixedValues() to dynamic algorithmic generation.</p>
 *
 * Implements: Task G2 from HTTP verification specification
 */
public class EncodingCombinationGenerator implements TypedGenerator<String> {

    // QI-6: Dynamic generation components
    private final TypedGenerator<Integer> basePatternTypeGen = Generators.integers(1, 5);
    private final TypedGenerator<Integer> depthGen = Generators.integers(1, 4);
    private final TypedGenerator<Boolean> useBackslashGen = Generators.booleans();

    private final TypedGenerator<Integer> encodingLevelGen = Generators.integers(1, 3);
    private final TypedGenerator<Boolean> mixedCaseGen = Generators.booleans();
    private final TypedGenerator<Boolean> hexDigitUppercaseGen = Generators.booleans();

    @Override
    public String next() {
        String basePattern = generateBasePattern();
        int level = encodingLevelGen.next();
        boolean mixedCase = mixedCaseGen.next();

        String encoded = basePattern;

        // Apply encoding levels
        for (int i = 0; i < level; i++) {
            encoded = urlEncode(encoded);
        }

        // Apply mixed case if selected
        if (mixedCase) {
            encoded = applyMixedCase(encoded);
        }

        return encoded;
    }

    private String generateBasePattern() {
        return switch (basePatternTypeGen.next()) {
            case 1 -> generateSimpleTraversal();
            case 2 -> generateWindowsTraversal();
            case 3 -> generateDeepTraversal();
            case 4 -> generateMixedSeparatorTraversal();
            case 5 -> generateCustomDepthTraversal();
            default -> generateSimpleTraversal();
        };
    }

    private String generateSimpleTraversal() {
        return useBackslashGen.next() ? "..\\" : "../";
    }

    private String generateWindowsTraversal() {
        return "..\\";
    }

    private String generateDeepTraversal() {
        int depth = depthGen.next();
        String separator = useBackslashGen.next() ? "\\" : "/";
        StringBuilder pattern = new StringBuilder();

        for (int i = 0; i < depth; i++) {
            pattern.append("..").append(separator);
        }

        return pattern.toString();
    }

    private String generateMixedSeparatorTraversal() {
        // Mix forward and backward slashes
        return "../..\\../";
    }

    private String generateCustomDepthTraversal() {
        int customDepth = Generators.integers(2, 6).next();
        String separator = useBackslashGen.next() ? "\\" : "/";
        StringBuilder pattern = new StringBuilder();

        for (int i = 0; i < customDepth; i++) {
            pattern.append("..").append(separator);
        }

        return pattern.toString();
    }

    private String urlEncode(String input) {
        // The literal percent character must be escaped FIRST. Escaping it last would rewrite
        // the percent characters that the dot and slash replacements just introduced, so a
        // single application would already yield %252e%252e%252f and the advertised level-1
        // output would be unreachable.
        return input.replace("%", "%25")
                .replace(".", "%2e")
                .replace("/", "%2f");
    }

    private String applyMixedCase(String input) {
        // Randomise the case of each hex digit of EVERY %XX escape. The previous two targeted
        // replacements uppercased only the literal %2e and %2f escapes, so every other escape the
        // generator emits - %25 above all, which every encoding level beyond the first produces -
        // stayed lowercase and the mixed-case dimension was never actually explored. Each case
        // decision is drawn from the seeded generator source, so a fixed seed still reproduces the
        // same output.
        StringBuilder result = new StringBuilder(input.length());
        int index = 0;
        while (index < input.length()) {
            char current = input.charAt(index);
            result.append(current);
            if (current == '%' && index + 2 < input.length()) {
                result.append(randomiseCase(input.charAt(index + 1)))
                        .append(randomiseCase(input.charAt(index + 2)));
                index += 3;
            } else {
                index++;
            }
        }
        return result.toString();
    }

    private char randomiseCase(char hexDigit) {
        return Boolean.TRUE.equals(hexDigitUppercaseGen.next())
                ? Character.toUpperCase(hexDigit)
                : Character.toLowerCase(hexDigit);
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}