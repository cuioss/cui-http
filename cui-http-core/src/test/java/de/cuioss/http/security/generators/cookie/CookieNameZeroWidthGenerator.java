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
package de.cuioss.http.security.generators.cookie;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

/**
 * Generates cookie names with zero-width character injection attacks.
 *
 * <p>
 * This generator creates cookie names with zero-width Unicode characters that
 * are invisible but can alter string matching and bypass security checks.
 * Zero-width characters can hide malicious content within seemingly innocent
 * cookie names.
 * </p>
 *
 * <h3>Attack Patterns Generated</h3>
 * <ul>
 *   <li>Zero Width Space (U+200B)</li>
 *   <li>Zero Width Non-Joiner (U+200C)</li>
 *   <li>Zero Width Joiner (U+200D)</li>
 *   <li>Zero Width No-Break Space / BOM (U+FEFF)</li>
 *   <li>Injected at various positions in cookie names</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>RFC 6265bis - Cookie Prefixes</li>
 *   <li>CWE-176: Improper Handling of Unicode Encoding</li>
 * </ul>
 *
 * @see <a href="https://portswigger.net/research/cookie-chaos-how-to-bypass-host-and-secure-cookie-prefixes">
 *      Cookie Chaos Research</a>
 * @since 1.0
 */
@SuppressWarnings("UnnecessaryUnicodeEscape") // Unicode escapes intentional for security testing
public class CookieNameZeroWidthGenerator implements TypedGenerator<String> {

    private final TypedGenerator<Integer> zeroWidthTypeGen = Generators.integers(0, 3);
    private final TypedGenerator<Integer> namePatternGen = Generators.integers(0, 5);

    @Override
    public String next() {
        String zeroWidth = getZeroWidthCharacter();
        return injectZeroWidth(zeroWidth);
    }

    private String getZeroWidthCharacter() {
        return switch (zeroWidthTypeGen.next()) {
            case 0 -> "\u200B"; // ZERO WIDTH SPACE
            case 1 -> "\u200C"; // ZERO WIDTH NON-JOINER
            case 2 -> "\u200D"; // ZERO WIDTH JOINER
            case 3 -> "\uFEFF"; // ZERO WIDTH NO-BREAK SPACE (BOM)
            default -> "\u200B";
        };
    }

    private String injectZeroWidth(String zw) {
        return switch (namePatternGen.next()) {
            case 0 -> zw + "__Host-session";           // Before prefix
            case 1 -> "__Host" + zw + "-session";      // Inside name (after __Host)
            case 2 -> "__Host-" + zw + "session";      // After dash
            case 3 -> zw + "__Secure-token";           // Before __Secure-
            case 4 -> "__Secure-" + zw + "token";      // After __Secure-
            case 5 -> "session" + zw + "_id";          // In regular cookie name
            default -> zw + "__Host-session";
        };
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
