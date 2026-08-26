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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Minimal RFC 7239 {@code Forwarded} header parser.
 *
 * <p>Grammar (RFC 7239 §4):</p>
 * <pre>
 * Forwarded         = 1#forwarded-element
 * forwarded-element = [ forwarded-pair ] *( ";" [ forwarded-pair ] )
 * forwarded-pair    = token "=" value
 * value             = token / quoted-string
 * </pre>
 *
 * <p>Extracts the last {@code proto} and {@code host} directives (case-insensitive names) and the
 * ordered list of {@code for} node identifiers across all comma-separated elements. Comma and
 * semicolon separators inside quoted strings are honored. Note: {@code Forwarded} has no
 * prefix/context-path directive, so none is extracted.</p>
 *
 * <p><strong>Last directive wins.</strong> Each proxy in the chain appends its own forwarded-element,
 * so the rightmost {@code proto}/{@code host} is the one contributed by the nearest (and therefore
 * most trustworthy) hop; any earlier occurrence is attacker-supplied when the client sent a
 * {@code Forwarded} header of its own. The {@code for} list is unaffected — it stays in appearance
 * order, because the chain walk consumes it right-to-left itself.</p>
 *
 * <p><strong>A malformed pair rejects the whole header.</strong> A {@code forwarded-pair} that is
 * non-blank yet carries no {@code =}, an empty directive name, or an empty (post-unquoting) value
 * violates the grammar. Such a pair is not discarded: it marks the result
 * {@linkplain Parsed#malformed() malformed}, parsing stops immediately, and no directive is reported.
 * The caller treats the entire {@code Forwarded} value as present-but-unresolvable, matching the
 * abort severity the {@code X-Forwarded-For} chain walk already applies to the same input class.
 * The grammar's optional {@code forwarded-pair} is honored: a pair that is blank after stripping
 * (e.g. a trailing {@code ;}) is legal and is skipped.</p>
 */
final class RfcForwardedParser {

    private RfcForwardedParser() {
    }

    /**
     * The relevant directives pulled from a {@code Forwarded} header value.
     *
     * <p>When {@code malformed} is {@code true} the header violated the grammar and no directive was
     * kept: {@code proto} and {@code host} are empty and {@code forValues} is empty. Callers must
     * treat the whole header as unresolvable rather than reading the (deliberately empty)
     * directives as "carried nothing".</p>
     *
     * @param proto     the last {@code proto} directive, if any
     * @param host      the last {@code host} directive, if any
     * @param forValues the ordered {@code for} node identifiers (unquoted), possibly empty
     * @param malformed whether a non-blank {@code forwarded-pair} violated the grammar
     */
    record Parsed(Optional<String> proto, Optional<String> host, List<String> forValues, boolean malformed) {

        /** The single outcome for a header carrying a grammar-violating pair. */
        static final Parsed MALFORMED = new Parsed(Optional.empty(), Optional.empty(), List.of(), true);
    }

    static Parsed parse(String headerValue) {
        Accumulator acc = new Accumulator();
        for (String element : splitTopLevel(headerValue, ',')) {
            for (String pair : splitTopLevel(element, ';')) {
                acc.apply(pair);
                if (acc.malformed) {
                    return Parsed.MALFORMED;
                }
            }
        }
        return new Parsed(Optional.ofNullable(acc.proto), Optional.ofNullable(acc.host), acc.forValues, false);
    }

    /**
     * Mutable accumulator that applies one {@code token=value} pair, keeping the last
     * {@code proto}/{@code host} and appending every {@code for} in appearance order. A pair that
     * violates the grammar raises {@link #malformed} instead of being discarded.
     */
    private static final class Accumulator {

        private String proto;
        private String host;
        private boolean malformed;
        private final List<String> forValues = new ArrayList<>();

        private void apply(String pair) {
            String stripped = pair.strip();
            if (stripped.isEmpty()) {
                // RFC 7239 §4: forwarded-pair is optional, so a blank pair (e.g. a trailing ';') is legal.
                return;
            }
            // eq == 0 is an empty directive name ("=value"), eq < 0 is a pair without '=' at all.
            int eq = stripped.indexOf('=');
            if (eq <= 0) {
                malformed = true;
                return;
            }
            String name = stripped.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String value = unquote(stripped.substring(eq + 1).strip());
            if (value.isEmpty()) {
                malformed = true;
                return;
            }
            switch (name) {
                case "proto" -> proto = value;
                case "host" -> host = value;
                case "for" -> forValues.add(value);
                default -> { /* ignore by, ext, and unknown directives */
                }
            }
        }

        /**
         * Strips surrounding double quotes and unescapes {@code \\x} sequences; returns non-quoted
         * input unchanged.
         */
        private static String unquote(String value) {
            if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
                return value;
            }
            StringBuilder out = new StringBuilder(value.length() - 2);
            boolean escaped = false;
            for (int i = 1; i < value.length() - 1; i++) {
                char c = value.charAt(i);
                if (escaped) {
                    out.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }
    }

    /**
     * Splits on {@code separator} at the top level only — separators inside a double-quoted string
     * are not split points (backslash escapes are honored inside quotes).
     */
    private static List<String> splitTopLevel(String input, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\' && inQuotes) {
                current.append(c);
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == separator && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }
}
