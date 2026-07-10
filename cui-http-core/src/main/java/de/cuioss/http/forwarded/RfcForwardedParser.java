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
 * <p>Extracts the first {@code proto} and {@code host} directives (case-insensitive names) and the
 * ordered list of {@code for} node identifiers across all comma-separated elements. Comma and
 * semicolon separators inside quoted strings are honored. Note: {@code Forwarded} has no
 * prefix/context-path directive, so none is extracted.</p>
 */
final class RfcForwardedParser {

    private RfcForwardedParser() {
    }

    /**
     * The relevant directives pulled from a {@code Forwarded} header value.
     *
     * @param proto     the first {@code proto} directive, if any
     * @param host      the first {@code host} directive, if any
     * @param forValues the ordered {@code for} node identifiers (unquoted), possibly empty
     */
    record Parsed(Optional<String> proto, Optional<String> host, List<String> forValues) {
    }

    static Parsed parse(String headerValue) {
        Accumulator acc = new Accumulator();
        for (String element : splitTopLevel(headerValue, ',')) {
            for (String pair : splitTopLevel(element, ';')) {
                acc.apply(pair);
            }
        }
        return new Parsed(Optional.ofNullable(acc.proto), Optional.ofNullable(acc.host), acc.forValues);
    }

    /**
     * Mutable accumulator that applies one {@code token=value} pair, keeping the first
     * {@code proto}/{@code host} and appending every {@code for} in appearance order.
     */
    private static final class Accumulator {

        private String proto;
        private String host;
        private final List<String> forValues = new ArrayList<>();

        private void apply(String pair) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                return;
            }
            String name = pair.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String value = unquote(pair.substring(eq + 1).strip());
            if (value.isEmpty()) {
                return;
            }
            switch (name) {
                case "proto" -> proto = proto == null ? value : proto;
                case "host" -> host = host == null ? value : host;
                case "for" -> forValues.add(value);
                default -> { /* ignore by, ext, and unknown directives */
                }
            }
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
