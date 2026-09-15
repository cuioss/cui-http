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
package de.cuioss.http.forwarded;

import org.jspecify.annotations.Nullable;

/**
 * Pure (non-logging) normalization and injection guards for reverse-proxy context-path prefixes.
 *
 * <p>Lifted from the {@code ProxyContextPathResolver} prior art. The rules: exactly one leading
 * slash, no trailing slash, and an empty string when the value is absent, blank, or rejected by one
 * of the guards below.</p>
 *
 * <p>A value is rejected when it:</p>
 * <ul>
 *   <li>carries control characters (CR/LF or other);</li>
 *   <li>is protocol-relative ({@code //host}) or contains a backslash (which some browsers
 *       normalize to {@code /});</li>
 *   <li>contains a comma or any whitespace character (defence in depth — a well-formed context path
 *       has neither, so their presence signals a comma-separated header value that reached
 *       normalization without nearest-hop token selection);</li>
 *   <li>contains {@code ?}, {@code #} or {@code ;} anywhere — each ends the path and starts
 *       something the consumer reads differently (a query, a fragment, a path parameter);</li>
 *   <li>contains a percent sign anywhere — {@code %2f}, {@code %5c} and every other encoded
 *       separator are covered at once, without decoding anything this class is not permitted to
 *       decode;</li>
 *   <li>carries a dot-segment — a {@code .} or {@code ..} between slashes, which re-points the
 *       prefix at a different location than the one it spells.</li>
 * </ul>
 *
 * <p>Callers that need to log a rejection reason use {@link #containsControlCharacter(String)} /
 * {@link #isProtocolRelativeOrBackslash(String)}.</p>
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
        if (trimmed.isEmpty() || containsControlCharacter(trimmed) || isProtocolRelativeOrBackslash(trimmed)
                || containsCommaOrWhitespace(trimmed) || containsUnsafePathConstruct(trimmed)) {
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

    /**
     * Rejects an interior comma or any whitespace character as defence in depth. A well-formed
     * context path contains neither, so their presence means a comma-separated header value reached
     * this point without nearest-hop token selection having been applied — the caller's guard order
     * is wrong, and normalizing such a value would honor an attacker-supplied token. The check runs
     * against the already-stripped value, so surrounding whitespace is not affected.
     */
    static boolean containsCommaOrWhitespace(String trimmed) {
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == ',' || Character.isWhitespace(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rejects the characters and segments that change where a context path points, as opposed to
     * how it is spelled.
     *
     * <p>{@code ?}, {@code #} and {@code ;} each terminate the path and open something the consumer
     * reads as a different component, so a prefix carrying one states more than a prefix. A percent
     * sign is rejected wholesale rather than decoded: this class is never permitted to decode at
     * all, so there is no decoded form to inspect, and rejecting the sign covers {@code %2f},
     * {@code %5c} and every other encoded separator in one rule instead of chasing each
     * encoding.</p>
     *
     * <p>A dot-segment is rejected for the same reason in structural form: {@code /app/../admin}
     * spells one prefix and resolves to another, so a consumer that resolves it and an allow-list
     * that compares it as text disagree about which location was authorized.</p>
     *
     * @param trimmed the already-stripped value
     * @return {@code true} when the value must be rejected
     */
    static boolean containsUnsafePathConstruct(String trimmed) {
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '?' || c == '#' || c == ';' || c == '%') {
                return true;
            }
        }
        return containsDotSegment(trimmed);
    }

    /**
     * @return {@code true} when the slash-split of {@code trimmed} produces a {@code .} or
     *         {@code ..} segment
     */
    private static boolean containsDotSegment(String trimmed) {
        int segmentStart = 0;
        int slash = trimmed.indexOf('/');
        while (slash >= 0) {
            if (isDotSegment(trimmed, segmentStart, slash)) {
                return true;
            }
            segmentStart = slash + 1;
            slash = trimmed.indexOf('/', segmentStart);
        }
        return isDotSegment(trimmed, segmentStart, trimmed.length());
    }

    private static boolean isDotSegment(String value, int start, int end) {
        int length = end - start;
        if (length == 1) {
            return value.charAt(start) == '.';
        }
        return length == 2 && value.charAt(start) == '.' && value.charAt(start + 1) == '.';
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 1 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
