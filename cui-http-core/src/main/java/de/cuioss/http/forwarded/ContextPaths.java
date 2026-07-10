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
package de.cuioss.http.forwarded;

import org.jspecify.annotations.Nullable;

/**
 * Pure (non-logging) normalization and injection guards for reverse-proxy context-path prefixes.
 *
 * <p>Lifted from the {@code ProxyContextPathResolver} prior art. The rules: exactly one leading
 * slash, no trailing slash, and an empty string when the value is absent, blank, carries control
 * characters (CR/LF or other), is protocol-relative ({@code //host}), or contains a backslash
 * (which some browsers normalize to {@code /}). Callers that need to log a rejection reason use
 * {@link #containsControlCharacter(String)} / {@link #isProtocolRelativeOrBackslash(String)}.</p>
 */
final class ContextPaths {

    private ContextPaths() {
    }

    /**
     * Normalizes a raw prefix to exactly one leading slash and no trailing slash, or returns the
     * empty string when the value is absent or rejected by the injection guard.
     *
     * @param raw the raw header value (may be {@code null})
     * @return the normalized prefix, or an empty string
     */
    static String normalize(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || containsControlCharacter(trimmed) || isProtocolRelativeOrBackslash(trimmed)) {
            return "";
        }
        String withLeadingSlash = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        String withoutTrailingSlash = stripTrailingSlashes(withLeadingSlash);
        // A value of only slashes (e.g. "/") collapses to empty.
        return "/".equals(withoutTrailingSlash) ? "" : withoutTrailingSlash;
    }

    static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rejects protocol-relative values ({@code //host}) and any backslash. Otherwise a value such
     * as {@code //attacker.com} would compose into {@code //attacker.com/...} in the browser — a
     * protocol-relative URL that exfiltrates the request to an attacker-controlled host.
     */
    static boolean isProtocolRelativeOrBackslash(String trimmed) {
        return trimmed.startsWith("//") || trimmed.indexOf('\\') >= 0;
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 1 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
