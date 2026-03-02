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
 * Generates cookie names with ASCII whitespace injection attacks.
 *
 * <p>
 * This generator creates cookie names with leading, trailing, or embedded
 * ASCII whitespace characters (space, tab). Servers may normalize these
 * by trimming, causing validation to happen after normalization and
 * potentially bypassing security checks.
 * </p>
 *
 * <h3>Attack Patterns Generated</h3>
 * <ul>
 *   <li>Leading space and tab</li>
 *   <li>Trailing space and tab</li>
 *   <li>Both leading and trailing whitespace</li>
 *   <li>Multiple whitespace characters</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>RFC 6265bis - Cookie Prefixes</li>
 *   <li>CWE-20: Improper Input Validation</li>
 * </ul>
 *
 * @see <a href="https://portswigger.net/research/cookie-chaos-how-to-bypass-host-and-secure-cookie-prefixes">
 *      Cookie Chaos Research</a>
 * @since 1.0
 */
public class CookieNameAsciiWhitespaceGenerator implements TypedGenerator<String> {

    private final TypedGenerator<Integer> whitespaceTypeGen = Generators.integers(0, 3);
    private final TypedGenerator<Integer> nameTypeGen = Generators.integers(0, 2);
    private final TypedGenerator<Integer> positionGen = Generators.integers(0, 4);

    @Override
    public String next() {
        String whitespace = getWhitespace();
        String name = getCookieName();
        int position = positionGen.next();

        return switch (position) {
            case 0 -> whitespace + name;                    // Leading
            case 1 -> name + whitespace;                    // Trailing
            case 2 -> whitespace + name + whitespace;       // Both
            case 3 -> whitespace + whitespace + name;       // Double leading
            case 4 -> name + whitespace + whitespace;       // Double trailing
            default -> whitespace + name;
        };
    }

    private String getWhitespace() {
        return switch (whitespaceTypeGen.next()) {
            case 0 -> " ";      // Space
            case 1 -> "\t";     // Tab
            case 2 -> "  ";     // Double space
            case 3 -> "\t\t";   // Double tab
            default -> " ";
        };
    }

    private String getCookieName() {
        return switch (nameTypeGen.next()) {
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
