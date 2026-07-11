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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Numeric IP-literal parsing helpers. All parsing is literal-only (never DNS-resolving), so an
 * untrusted hostname can never be interpreted as an address.
 */
final class IpAddresses {

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");

    private IpAddresses() {
    }

    /**
     * Parses an IPv4 or IPv6 literal without any DNS lookup.
     *
     * @param literal the candidate literal (already trimmed)
     * @return the parsed address, or {@code null} when {@code literal} is not a valid IP literal
     */
    static @Nullable InetAddress parse(String literal) {
        boolean looksV4 = IPV4_LITERAL.matcher(literal).matches();
        boolean looksV6 = literal.indexOf(':') >= 0 && IPV6_LITERAL.matcher(literal).matches();
        if (!looksV4 && !looksV6) {
            return null;
        }
        try {
            // Guarded to a numeric literal above, so getByName performs no DNS lookup.
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Extracts the bare IP literal from a forwarded-chain entry, stripping an optional port and
     * unwrapping bracketed IPv6.
     *
     * <p>Handles {@code 192.0.2.7}, {@code 192.0.2.7:443}, {@code [2001:db8::1]},
     * {@code [2001:db8::1]:443}, and bare {@code 2001:db8::1}. RFC 7239 {@code unknown} and
     * obfuscated ({@code _hidden}) node identifiers yield {@code null}.</p>
     *
     * <p>The {@code host:port} split here intentionally diverges from
     * {@code ForwardedHeaderResolver.parseHostPort}: this method <em>strips</em> the IPv6 brackets
     * to obtain a bare literal for {@link InetAddress} matching, whereas {@code parseHostPort}
     * <em>retains</em> them because it reconstructs a host string. The divergence is deliberate —
     * keep both bracket policies in sync when either changes.</p>
     *
     * @param entry a single forwarded-chain entry (already trimmed, unquoted)
     * @return the parsed address, or {@code null} when the entry is not a usable IP literal
     */
    static @Nullable InetAddress parseChainEntry(String entry) {
        String token = entry.strip();
        if (token.isEmpty() || "unknown".equalsIgnoreCase(token) || token.charAt(0) == '_') {
            return null;
        }
        String ipPart;
        if (token.charAt(0) == '[') {
            int close = token.indexOf(']');
            if (close < 0) {
                return null;
            }
            ipPart = token.substring(1, close);
        } else if (token.indexOf(':') == token.lastIndexOf(':') && token.indexOf(':') >= 0) {
            // exactly one colon -> IPv4:port
            ipPart = token.substring(0, token.indexOf(':'));
        } else {
            // no colon (IPv4) or multiple colons (bare IPv6)
            ipPart = token;
        }
        return parse(ipPart);
    }

    /**
     * @return the canonical textual form of {@code address} ({@link InetAddress#getHostAddress()})
     */
    static String canonical(InetAddress address) {
        return address.getHostAddress();
    }
}
