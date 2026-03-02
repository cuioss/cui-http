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
 * Generates cookie names with Unicode whitespace injection attacks.
 *
 * <p>
 * This generator creates cookie names prefixed with various Unicode whitespace
 * characters to test bypasses of cookie prefix validation (__Host-, __Secure-).
 * These attacks exploit the fact that browsers may accept Unicode whitespace
 * but servers may strip it during normalization, causing validation to occur
 * after the transformation.
 * </p>
 *
 * <h3>Attack Patterns Generated</h3>
 * <ul>
 *   <li>Multibyte Unicode whitespace (U+2000-U+200A)</li>
 *   <li>Single-byte Unicode whitespace (U+0085 NEL, U+00A0 NBSP)</li>
 *   <li>Prefixed on __Host- and __Secure- cookie names</li>
 *   <li>Trailing whitespace patterns</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>RFC 6265bis - Cookie Prefixes</li>
 *   <li>CWE-565: Reliance on Cookies without Validation</li>
 * </ul>
 *
 * @see <a href="https://portswigger.net/research/cookie-chaos-how-to-bypass-host-and-secure-cookie-prefixes">
 *      Cookie Chaos Research</a>
 * @since 1.0
 */
@SuppressWarnings("UnnecessaryUnicodeEscape") // Unicode escapes intentional for security testing
public class CookieNameUnicodeWhitespaceGenerator implements TypedGenerator<String> {

    private final TypedGenerator<Integer> whitespaceTypeGen = Generators.integers(0, 12);
    private final TypedGenerator<Integer> prefixTypeGen = Generators.integers(0, 2);
    private final TypedGenerator<Integer> positionGen = Generators.integers(0, 2);

    @Override
    public String next() {
        String whitespace = getUnicodeWhitespace();
        String prefix = getCookiePrefix();
        int position = positionGen.next();

        return switch (position) {
            case 0 -> whitespace + prefix; // Leading whitespace
            case 1 -> prefix + whitespace; // Trailing whitespace
            case 2 -> whitespace + prefix + whitespace; // Both
            default -> whitespace + prefix;
        };
    }

    private String getUnicodeWhitespace() {
        return switch (whitespaceTypeGen.next()) {
            case 0 -> "\u2000"; // EN QUAD
            case 1 -> "\u2001"; // EM QUAD
            case 2 -> "\u2002"; // EN SPACE
            case 3 -> "\u2003"; // EM SPACE
            case 4 -> "\u2004"; // THREE-PER-EM SPACE
            case 5 -> "\u2005"; // FOUR-PER-EM SPACE
            case 6 -> "\u2006"; // SIX-PER-EM SPACE
            case 7 -> "\u2007"; // FIGURE SPACE
            case 8 -> "\u2008"; // PUNCTUATION SPACE
            case 9 -> "\u2009"; // THIN SPACE
            case 10 -> "\u200A"; // HAIR SPACE
            case 11 -> "\u00A0"; // NO-BREAK SPACE (NBSP)
            case 12 -> "\u0085"; // NEXT LINE (NEL)
            default -> "\u2000";
        };
    }

    private String getCookiePrefix() {
        return switch (prefixTypeGen.next()) {
            case 0 -> "__Host-session";
            case 1 -> "__Secure-token";
            case 2 -> "session_id";
            default -> "__Host-session";
        };
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
