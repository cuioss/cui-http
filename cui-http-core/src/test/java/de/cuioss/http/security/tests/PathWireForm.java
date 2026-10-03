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
package de.cuioss.http.security.tests;

import de.cuioss.http.security.config.SecurityDefaults;
import de.cuioss.http.security.validation.CharacterValidationConstants;

import java.util.Locale;

/**
 * Structural fingerprint of a URL-path attack payload: the first thing in its wire form that the
 * RFC 3986 path grammar does not admit, or {@link #WIRE_CLEAN} when there is none.
 *
 * <p>The attack generators mix payload families whose verdicts differ - a payload carrying a raw
 * null byte is decided by a different mechanism than one whose wire form is clean and whose
 * attack only appears after decoding. A test that asserts one exact failure type therefore
 * selects one family, and this fingerprint is what it selects by: it reads the payload itself,
 * left to right, and never consults a pipeline.</p>
 */
enum PathWireForm {

    /** A raw null byte, or its {@code %00} spelling. */
    NULL_BYTE,

    /** A raw C0 control character other than the null byte. */
    RAW_CONTROL_CHARACTER,

    /** A raw character outside the RFC 3986 path set that is not a C0 control. */
    RAW_NON_PATH_CHARACTER,

    /** A {@code %} that is not followed by two ASCII hex digits. */
    MALFORMED_ESCAPE,

    /** Path characters and well-formed escapes only. */
    WIRE_CLEAN;

    /**
     * Fingerprints a payload.
     *
     * @param payload the URL-path payload to read, must not be null
     * @return the form of the first inadmissible element, or {@link #WIRE_CLEAN}
     */
    static PathWireForm of(String payload) {
        int index = 0;
        while (index < payload.length()) {
            char character = payload.charAt(index);
            if (character == '\0') {
                return NULL_BYTE;
            }
            if (character == '%') {
                if (index + 2 >= payload.length() || isNotAsciiHex(payload.charAt(index + 1))
                        || isNotAsciiHex(payload.charAt(index + 2))) {
                    return MALFORMED_ESCAPE;
                }
                if (payload.charAt(index + 1) == '0' && payload.charAt(index + 2) == '0') {
                    return NULL_BYTE;
                }
                index += 3;
                continue;
            }
            int codePoint = payload.codePointAt(index);
            if (!CharacterValidationConstants.RFC3986_PATH_CHARS.test(codePoint)) {
                return codePoint <= 0x1F ? RAW_CONTROL_CHARACTER : RAW_NON_PATH_CHARACTER;
            }
            index += Character.charCount(codePoint);
        }
        return WIRE_CLEAN;
    }

    /**
     * @param payload the URL-path payload to read, must not be null
     * @return {@code true} when {@code payload} has this wire form
     */
    boolean isFormOf(String payload) {
        return of(payload) == this;
    }

    /**
     * Determines whether a payload spells a traversal sequence the way one of the
     * {@link SecurityDefaults#PATH_TRAVERSAL_PATTERNS} does, ignoring letter case - raw
     * ({@code ../}), single-encoded ({@code %2e%2e%2f}) or double-encoded
     * ({@code %252e%252e%252f}).
     *
     * @param payload the URL-path payload to read, must not be null
     * @return {@code true} when {@code payload} contains a listed traversal spelling
     */
    static boolean carriesListedTraversalSpelling(String payload) {
        String lowerCased = payload.toLowerCase(Locale.ROOT);
        return SecurityDefaults.PATH_TRAVERSAL_PATTERNS.stream()
                .map(pattern -> pattern.toLowerCase(Locale.ROOT))
                .anyMatch(lowerCased::contains);
    }

    private static boolean isNotAsciiHex(char character) {
        return !((character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F'));
    }
}
