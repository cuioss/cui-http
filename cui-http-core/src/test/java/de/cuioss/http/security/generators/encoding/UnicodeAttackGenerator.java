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
 * Generates Unicode-based attack patterns.
 *
 * <p>QI-6: Converted from fixedValues() to dynamic algorithmic generation.</p>
 *
 * <h3>Attack-signal invariant</h3>
 *
 * <p>Every emitted value carries attack signal on its own, independent of any parameter name or
 * surrounding context the consuming test supplies. The two traversal code point sequences are
 * complete payloads and may therefore be emitted bare; the invisible and control code points are
 * not, so they are always embedded in a traversal-shaped carrier ending in a sensitive path
 * target. A lone formatting character is not an attack and this generator never emits one.</p>
 *
 * Implements: Task G3 from HTTP verification specification
 */
public class UnicodeAttackGenerator implements TypedGenerator<String> {

    /** Dots and slash (U+002E U+002E U+002F) — a complete traversal payload. */
    private static final String DECODED_TRAVERSAL = Character.toString(0x002E)
            + Character.toString(0x002E) + Character.toString(0x002F);

    /**
     * One-dot leaders and division slash (U+2024 U+2024 U+2215) — a complete traversal payload
     * once NFKC folds the homoglyphs to their ASCII counterparts.
     */
    private static final String LOOKALIKE_TRAVERSAL = Character.toString(0x2024)
            + Character.toString(0x2024) + Character.toString(0x2215);

    /** RIGHT-TO-LEFT OVERRIDE (U+202E). */
    private static final String RIGHT_TO_LEFT_OVERRIDE = Character.toString(0x202E);

    /** ZERO WIDTH SPACE (U+200B). */
    private static final String ZERO_WIDTH_SPACE = Character.toString(0x200B);

    /** ZERO WIDTH NO-BREAK SPACE (U+FEFF). */
    private static final String ZERO_WIDTH_NO_BREAK_SPACE = Character.toString(0xFEFF);

    /** NULL (U+0000). */
    private static final String NULL_CHARACTER = Character.toString(0x0000);

    // QI-6: Dynamic generation components
    private final TypedGenerator<Integer> unicodeAttackTypeGen = Generators.integers(1, 6);
    private final TypedGenerator<Integer> pathTargetSelector = Generators.integers(1, 4);

    private final TypedGenerator<Boolean> combineGen = Generators.booleans();

    @Override
    public String next() {
        return switch (unicodeAttackTypeGen.next()) {
            case 2 -> withOptionalTraversalSuffix(LOOKALIKE_TRAVERSAL);
            case 3 -> embedInTraversalCarrier(RIGHT_TO_LEFT_OVERRIDE);
            case 4 -> embedInTraversalCarrier(ZERO_WIDTH_SPACE);
            case 5 -> embedInTraversalCarrier(ZERO_WIDTH_NO_BREAK_SPACE);
            case 6 -> embedInTraversalCarrier(NULL_CHARACTER);
            default -> withOptionalTraversalSuffix(DECODED_TRAVERSAL);
        };
    }

    /**
     * Emits a complete traversal payload either bare or extended by a further traversal to a
     * sensitive path, keeping both the minimal and the composite form reachable.
     */
    private String withOptionalTraversalSuffix(String traversalPayload) {
        return combineGen.next() ? traversalPayload + "../" + generatePathTarget() : traversalPayload;
    }

    /**
     * Embeds an invisible or control code point inside a traversal sequence that ends in a
     * sensitive path target, so the value is an attack in its own right rather than a lone
     * formatting character that only becomes hostile through context a caller supplies.
     */
    private String embedInTraversalCarrier(String invisibleCharacter) {
        String target = generatePathTarget();
        return combineGen.next()
                ? ".." + invisibleCharacter + "/" + target
                : "../" + invisibleCharacter + target;
    }

    private String generatePathTarget() {
        return switch (pathTargetSelector.next()) {
            case 1 -> "etc/passwd";
            case 2 -> "etc/shadow";
            case 3 -> "windows/win.ini";
            case 4 -> "boot.ini";
            default -> "etc/passwd";
        };
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
