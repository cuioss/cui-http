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
package de.cuioss.http.security.generators.cookie;

import java.util.List;

/**
 * The RFC 6265bis cookie name prefixes the cookie-name attack generators decorate.
 *
 * <p>The single definition of the prefix set for the generators in this package and for the tests
 * that assert a generated name still carries one of them, so the two cannot drift apart.</p>
 *
 * @since 1.0
 */
public final class CookieSecurityPrefixes {

    /** Host-locked cookie prefix. */
    public static final String HOST = "__Host-";

    /** Secure-only cookie prefix. */
    public static final String SECURE = "__Secure-";

    /** Every prefix the cookie-name attack generators emit. */
    public static final List<String> ALL = List.of(HOST, SECURE);

    private CookieSecurityPrefixes() {
        // constants only
    }

    /**
     * Reports whether {@code name} starts with one of {@link #ALL the generated prefixes}.
     *
     * @param name the cookie name to inspect
     * @return true if the name carries a generated prefix
     */
    public static boolean isPrefixed(String name) {
        return ALL.stream().anyMatch(name::startsWith);
    }
}
