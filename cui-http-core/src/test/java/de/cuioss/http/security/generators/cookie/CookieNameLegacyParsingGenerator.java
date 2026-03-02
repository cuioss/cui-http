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
 * Generates cookie names with legacy parsing trigger patterns.
 *
 * <p>
 * This generator creates cookie names with $Version=1 prefixes that trigger
 * legacy RFC 2109 parsing in Java servlet containers (Apache Tomcat, Jetty).
 * Legacy parsers may not enforce modern cookie prefix security requirements.
 * </p>
 *
 * <h3>Attack Patterns Generated</h3>
 * <ul>
 *   <li>$Version=1 with comma separator</li>
 *   <li>$Version=1 with semicolon separator</li>
 *   <li>$Version=2 patterns</li>
 *   <li>Combined with __Host- and __Secure- prefixes</li>
 * </ul>
 *
 * <h3>Affected Java Frameworks</h3>
 * <ul>
 *   <li>Apache Tomcat (all versions with RFC 2109 support)</li>
 *   <li>Eclipse Jetty (versions with RFC 2109 parser)</li>
 *   <li>Java Servlet Containers supporting legacy cookie specs</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>RFC 6265bis - Cookie Prefixes</li>
 *   <li>RFC 2109 - HTTP State Management (Legacy)</li>
 *   <li>CWE-565: Reliance on Cookies without Validation</li>
 * </ul>
 *
 * @see <a href="https://portswigger.net/research/cookie-chaos-how-to-bypass-host-and-secure-cookie-prefixes">
 *      Cookie Chaos Research</a>
 * @since 1.0
 */
public class CookieNameLegacyParsingGenerator implements TypedGenerator<String> {

    private final TypedGenerator<Integer> versionGen = Generators.integers(1, 2);
    private final TypedGenerator<Integer> separatorGen = Generators.integers(0, 2);
    private final TypedGenerator<Integer> prefixTypeGen = Generators.integers(0, 1);

    @Override
    public String next() {
        int version = versionGen.next();
        String separator = getSeparator();
        String prefix = getCookiePrefix();

        return "$Version=" + version + separator + prefix;
    }

    private String getSeparator() {
        return switch (separatorGen.next()) {
            case 0 -> ",";      // Comma (standard RFC 2109)
            case 1 -> ";";      // Semicolon
            case 2 -> ", ";     // Comma with space
            default -> ",";
        };
    }

    private String getCookiePrefix() {
        return switch (prefixTypeGen.next()) {
            case 0 -> "__Host-session";
            case 1 -> "__Secure-token";
            default -> "__Host-session";
        };
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
