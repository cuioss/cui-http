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
 * <p><strong>A malformed pair stops the parse and is reported, not swallowed.</strong> A
 * {@code forwarded-pair} that is non-blank yet carries no {@code =}, an empty directive name, or an
 * empty (post-unquoting) value violates the grammar. Such a pair is not discarded: it marks the
 * result {@linkplain Parsed#malformed() malformed} and parsing stops immediately. The directives
 * accumulated <em>before</em> the offending pair are <strong>retained</strong>, so the caller can
 * still tell which fields the header actually spoke about — {@code proto=https;broken} reports
 * {@code proto} as {@code https} with {@code malformed=true}, while {@code broken;proto=https}
 * reports no {@code proto} at all, because the parse never reached it.</p>
 *
 * <p>Retaining them is a <em>presence</em> signal, not a licence to honor them: a malformed header
 * is unresolvable, and the caller supplies nothing from it to the reconciliation. What the retained
 * directives buy is per-field scope — a header that carried a {@code proto} and then broke drops
 * only the scheme, instead of erasing every field a legitimate proxy attested. The grammar's
 * optional {@code forwarded-pair} is honored: a pair that is blank after stripping (e.g. a trailing
 * {@code ;}) is legal and is skipped.</p>
 */
final class RfcForwardedParser {

    private RfcForwardedParser() {
    }

    /**
     * The relevant directives pulled from a {@code Forwarded} header value.
     *
     * <p>When {@code malformed} is {@code true} the header violated the grammar and the parse
     * stopped at the offending pair — the directives reported are the ones accumulated
     * <em>before</em> it. Read them as "which fields did this header speak about", never as values
     * to honor: the header is unresolvable, so the caller contributes nothing from it and the
     * fields it did speak about fail closed through the ordinary disagreement path.</p>
     *
     * @param proto     the last {@code proto} directive parsed before the stop, if any
     * @param host      the last {@code host} directive parsed before the stop, if any
     * @param forValues the ordered {@code for} node identifiers (unquoted) parsed before the stop,
     *                  possibly empty
     * @param malformed whether a non-blank {@code forwarded-pair} violated the grammar
     */
    record Parsed(Optional<String> proto, Optional<String> host, List<String> forValues, boolean malformed) {
    }

    static Parsed parse(String headerValue) {
        Accumulator acc = new Accumulator();
        for (String element : splitTopLevel(headerValue, ',')) {
            for (String pair : splitTopLevel(element, ';')) {
                acc.apply(pair);
                if (acc.malformed) {
                    return acc.toParsed();
                }
            }
        }
        return acc.toParsed();
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

        /**
         * Snapshots what has been accumulated so far. Called both on a clean finish and at the stop
         * a malformed pair forces, so the retained-directives rule has a single implementation and
         * the two exits cannot drift apart.
         */
        private Parsed toParsed() {
            return new Parsed(Optional.ofNullable(proto), Optional.ofNullable(host),
                    List.copyOf(forValues), malformed);
        }

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
