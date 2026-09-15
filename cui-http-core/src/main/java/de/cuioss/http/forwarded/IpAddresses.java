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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Numeric IP-literal parsing helpers. All parsing is literal-only (never DNS-resolving), so an
 * untrusted hostname can never be interpreted as an address.
 */
final class IpAddresses {

    /**
     * Dotted-quad with no leading zero in any octet and every octet in {@code 0..255}. A
     * leading-zero octet ({@code 010.0.0.5}) is read as octal by some resolvers, which makes it a
     * classic SSRF / allow-list bypass vector, so it is rejected outright rather than normalized.
     * <p>
     * The range bound is load-bearing, not cosmetic: {@link java.net.InetAddress#getByName} treats
     * a dotted-quad it cannot parse as an address ({@code 999.1.1.1}) as a <em>hostname</em> and
     * performs a real, blocking DNS lookup. A shape-only pattern would therefore let an attacker
     * turn any {@code X-Forwarded-For} entry into an outbound resolver round trip from this
     * header-parsing path, contradicting the class-level literal-only guarantee.
     */
    private static final String IPV4_OCTET = "(0|25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]\\d?)";
    private static final Pattern IPV4_LITERAL = Pattern.compile(IPV4_OCTET + "(\\." + IPV4_OCTET + "){3}");
    /**
     * A shape guard only — it admits the character set an IPv6 literal is written from, and says
     * nothing about the dotted-quad an IPv4-mapped form ({@code ::ffff:10.0.0.5}) carries after its
     * last colon. That tail is checked separately against {@link #IPV4_LITERAL} by
     * {@link #hasValidMappedIpv4Suffix(String)}, so a mapped literal cannot carry an octet the
     * plain IPv4 form would have refused.
     */
    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");

    /** A non-empty run of ASCII digits — the only thing a port suffix may be. */
    private static final String PORT_DIGIT_RUN = "\\d+";

    /** The only content permitted after a closing {@code ]}: a colon plus an all-ASCII-digit port. */
    private static final Pattern BRACKET_PORT_SUFFIX = Pattern.compile(":" + PORT_DIGIT_RUN);

    /** The colon-less sibling of {@link #BRACKET_PORT_SUFFIX}, for an already-split suffix. */
    private static final Pattern PORT_SUFFIX = Pattern.compile(PORT_DIGIT_RUN);

    private IpAddresses() {
    }

    /**
     * Parses an IPv4 or IPv6 literal without any DNS lookup.
     *
     * <p><strong>A zone/scope ID is not a usable literal here.</strong> An IPv6 value carrying one
     * ({@code fe80::1%eth0}) yields {@code null}. The rejection happens in the shape guard, not in
     * {@link InetAddress}: {@code %} is outside the {@link #IPV6_LITERAL} character class, so the
     * value never reaches {@link InetAddress#getByName}. That ordering is deliberate — letting a
     * {@code %}-bearing value through to {@code getByName} is exactly what would turn an
     * unrecognized form into a blocking DNS lookup, breaking the class-level literal-only
     * guarantee. A scope ID is meaningful only on the interface that defines it, so it identifies
     * no forwarded hop worth matching against a trusted-proxy range.</p>
     *
     * <p>The {@code null} is not silent at the call site: a chain entry rejected here surfaces
     * through the {@code HTTP-123}
     * {@link ForwardedLogMessages.WARN#CLIENT_IP_ENTRY_UNPARSEABLE} warning, and the resolver
     * drops the client IP fail-closed rather than honoring an unverifiable chain.</p>
     *
     * <p><strong>An IPv4-mapped literal is held to the IPv4 rules.</strong> A value such as
     * {@code ::ffff:010.0.0.5} or {@code ::ffff:999.1.1.1} carries a dotted-quad after its last
     * colon, and the IPv6 shape guard alone would admit it — leaving the mapped spelling as a way
     * around the leading-zero and octet-range rejections {@link #IPV4_LITERAL} enforces on the
     * plain form, which are exactly the SSRF / allow-list-bypass guards documented there. The tail
     * after the last colon is therefore matched against {@link #IPV4_LITERAL} whenever the literal
     * carries a dot, and a tail that fails yields {@code null}.</p>
     *
     * @param literal the candidate literal (already trimmed)
     * @return the parsed address, or {@code null} when {@code literal} is not a valid IP literal
     */
    static @Nullable InetAddress parse(String literal) {
        boolean looksV4 = IPV4_LITERAL.matcher(literal).matches();
        boolean looksV6 = literal.indexOf(':') >= 0 && IPV6_LITERAL.matcher(literal).matches()
                && hasValidMappedIpv4Suffix(literal);
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
     * Whether the dotted-quad an IPv4-mapped IPv6 literal carries after its last colon satisfies
     * the plain IPv4 rules.
     *
     * <p>A literal with no dot states no IPv4 tail, so there is nothing to hold to those rules and
     * the answer is {@code true}. Otherwise everything after the last colon is the mapped address,
     * and it is matched against {@link #IPV4_LITERAL} — the same pattern the plain form is held to,
     * so the mapped spelling cannot be the way an octal-looking or out-of-range octet gets in.</p>
     */
    private static boolean hasValidMappedIpv4Suffix(String literal) {
        if (literal.indexOf('.') < 0) {
            return true;
        }
        return IPV4_LITERAL.matcher(literal.substring(literal.lastIndexOf(':') + 1)).matches();
    }

    /**
     * Extracts the bare IP literal from a forwarded-chain entry, stripping an optional port and
     * unwrapping bracketed IPv6.
     *
     * <p>Handles {@code 192.0.2.7}, {@code 192.0.2.7:443}, {@code [2001:db8::1]},
     * {@code [2001:db8::1]:443}, and bare {@code 2001:db8::1}. RFC 7239 {@code unknown} and
     * obfuscated ({@code _hidden}) node identifiers yield {@code null}.</p>
     *
     * <p><strong>Trailing content after {@code ]} is rejected.</strong> The only thing permitted
     * after the closing bracket is a colon followed by one or more ASCII digits. Anything else —
     * {@code [::1]garbage}, {@code [::1]:notaport}, a bare trailing {@code [::1]:} — yields
     * {@code null} rather than silently resolving to the bracketed literal.</p>
     *
     * <p><strong>An unbracketed {@code address:port} entry is held to the same rule.</strong> When
     * the token carries exactly one colon, the suffix after it must be a non-empty run of ASCII
     * digits ({@link #isPortSuffix(String)}); {@code 192.0.2.7:notaport} and a bare trailing
     * {@code 192.0.2.7:} yield {@code null} rather than resolving to the address with the
     * unparseable remainder quietly discarded. The bracketed branch has always worked this way, and
     * an entry is malformed as a whole regardless of which spelling it arrived in.</p>
     *
     * <p>The {@code host:port} split here intentionally diverges from
     * {@code ForwardedHeaderResolver.parseHostPort}: this method <em>strips</em> the IPv6 brackets
     * to obtain a bare literal for {@link InetAddress} matching, whereas {@code parseHostPort}
     * <em>retains</em> them because it reconstructs a host string. Only the bracket <em>retention</em>
     * differs: the trailing-content rule above has a single implementation,
     * {@link #hasValidBracketTrailer(String)}, which both call sites share, so the two bracket
     * policies cannot drift apart.</p>
     *
     * <p>The literal itself is validated by {@link #parse(String)}, so its rules apply here
     * unchanged — in particular an IPv6 value carrying a zone/scope ID ({@code fe80::1%eth0}) is
     * not a usable literal and yields {@code null}. See that method for why the rejection is
     * deliberate and where it surfaces.</p>
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
            String rest = token.substring(close + 1);
            if (!hasValidBracketTrailer(rest)) {
                return null;
            }
            ipPart = token.substring(1, close);
        } else if (token.indexOf(':') == token.lastIndexOf(':') && token.indexOf(':') >= 0) {
            // exactly one colon -> IPv4:port. The suffix is held to the same digit-run rule the
            // bracketed branch applies through hasValidBracketTrailer: a chain entry whose port is
            // present but not a digit run is malformed as a whole, and reading the address out of
            // it while discarding the unparseable remainder would honor a hop nobody wrote.
            if (!isPortSuffix(token.substring(token.indexOf(':') + 1))) {
                return null;
            }
            ipPart = token.substring(0, token.indexOf(':'));
        } else {
            // no colon (IPv4) or multiple colons (bare IPv6)
            ipPart = token;
        }
        return parse(ipPart);
    }

    /**
     * The sole implementation of the "only {@code :digits} may follow a closing {@code ]}" rule,
     * shared by {@link #parseChainEntry(String)} and {@code ForwardedHeaderResolver.parseHostPort}.
     *
     * <p>Both call sites must reject trailing content after the closing bracket for the same reason:
     * a value such as {@code [::1]garbage}, {@code [::1]:notaport}, or a bare trailing {@code [::1]:}
     * must not silently resolve to the bracketed literal, because that is a host-confusion vector.
     * The rule therefore lives here once instead of being re-implemented per caller, where the two
     * copies could drift apart and reopen the vulnerability they jointly prevent.</p>
     *
     * @param rest the substring following the closing {@code ]} (possibly empty)
     * @return {@code true} when {@code rest} is empty, or is a colon followed by one or more ASCII
     * digits; {@code false} for any other trailing content
     */
    static boolean hasValidBracketTrailer(String rest) {
        return rest.isEmpty() || BRACKET_PORT_SUFFIX.matcher(rest).matches();
    }

    /**
     * The colon-less counterpart of {@link #hasValidBracketTrailer(String)}, for a suffix the
     * caller has already split off at the colon.
     *
     * <p>Both answer the same question — "is this a port?" — from the one
     * {@link #PORT_DIGIT_RUN} definition, so the bracketed and unbracketed branches cannot come to
     * differ about what a port suffix is. It is shared by {@link #parseChainEntry(String)} and
     * {@code ForwardedHeaderResolver.parseHostPort}, whose unbracketed branches face the same
     * malformed-suffix input.</p>
     *
     * @param suffix the substring following the colon
     * @return {@code true} when {@code suffix} is a non-empty run of ASCII digits
     */
    static boolean isPortSuffix(String suffix) {
        return PORT_SUFFIX.matcher(suffix).matches();
    }

    /**
     * @return the canonical textual form of {@code address} ({@link InetAddress#getHostAddress()})
     */
    static String canonical(InetAddress address) {
        return address.getHostAddress();
    }
}
