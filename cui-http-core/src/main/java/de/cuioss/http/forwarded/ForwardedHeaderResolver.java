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

import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.PipelineFactory;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static de.cuioss.http.forwarded.ForwardedHeaderNames.*;

/**
 * Resolves the reverse-proxy / forwarded-header family into a single sanitized
 * {@link ResolvedForwarding}.
 *
 * <p>For each field the resolver: (1) selects the raw value by header precedence, (2) sanitizes it
 * through the existing {@link de.cuioss.http.security} header-value pipeline (rejecting CR/LF, NUL,
 * control characters, over-length, and suspicious patterns), (3) applies field-specific
 * normalization and injection guards, and (4) honors it only when the configured trust model
 * permits. Values that fail sanitization or are not trusted are dropped (and logged) rather than
 * honored — {@code resolve} never throws.</p>
 *
 * <h3 id="security-precondition">Security precondition — trusted network placement (MANDATORY)</h3>
 * <p><strong>{@code resolve(...)} trusts HTTP headers, not the socket.</strong> The resolver
 * receives only a header accessor; the actual TCP peer (the socket remote address) is never passed
 * in and cannot be inspected. Consequently the {@code X-Forwarded-For} / {@code Forwarded} walk
 * cannot verify that the request actually arrived <em>through</em> a trusted proxy — it can only
 * match the addresses <em>inside the headers</em> against the configured {@code trustedProxies}.</p>
 * <p>The deployment therefore <strong>MUST</strong> guarantee that only trusted proxies can connect
 * to this server directly. If an attacker can reach the server without traversing a trusted proxy,
 * they can forge the chain (e.g. a single untrusted entry {@code X-Forwarded-For: 6.6.6.6}) and have
 * it returned verbatim as the client IP. Enforce this with network controls — bind the listener to a
 * private interface, restrict it with firewall / security-group rules, or place it behind a service
 * mesh — so that the socket peer is always a trusted proxy. The resolver cannot make this guarantee
 * for you, and (by design) does not accept the peer address as a parameter.</p>
 *
 * <h3>Precedence</h3>
 * <ul>
 *   <li>scheme: {@code X-Forwarded-Proto} → {@code X-ProxyScheme}, reconciled against RFC 7239
 *       {@code proto}</li>
 *   <li>host: {@code X-Forwarded-Host} → {@code X-ProxyHost}, reconciled against RFC 7239
 *       {@code host}</li>
 *   <li>port: {@code X-Forwarded-Port} → {@code X-ProxyPort} → host {@code :port} fallback</li>
 *   <li>context-path: {@code X-ProxyContextPath} → {@code X-Forwarded-Prefix}
 *       ({@code Forwarded} has no prefix directive)</li>
 *   <li>client-IP: {@code X-Forwarded-For} chain, reconciled against the RFC 7239 {@code for}
 *       chain</li>
 * </ul>
 *
 * <p><strong>Nearest hop wins within a header.</strong> Each proxy in a chain <em>appends</em> its
 * own value, so for a comma-separated header value the resolver selects the <em>rightmost</em>
 * token — the one contributed by the closest, most trustworthy proxy. Leading tokens are
 * attacker-supplied whenever the original client sent the header itself. The same rule applies
 * across RFC 7239 elements: the <em>last</em> {@code proto} / {@code host} directive wins.</p>
 *
 * <p><strong>Conflicting sources = drop (fail closed).</strong> For scheme, host, and client-IP the
 * de-facto {@code X-Forwarded-*} / {@code X-Proxy*} family and the RFC 7239 {@code Forwarded} header
 * are resolved <em>independently</em>. When only one source is present its result is honored. When
 * <em>both</em> are present they must agree: a proxy that populates both families does not
 * contradict itself, so a disagreement means at least one side is forged. The field is then dropped
 * and a warning logged, rather than letting the higher-precedence family silently win — preferring
 * one source is precisely what an attacker exploits by supplying the family the resolver ranks
 * higher.</p>
 *
 * <p><strong>Present-but-invalid = drop (no fall-through).</strong> A present, non-blank source is
 * validated; if it fails its field guard it is <em>dropped</em> — lower-precedence sources are
 * <em>not</em> consulted as a fallback. In particular a present-but-invalid
 * {@code X-Forwarded-Port} / {@code X-ProxyPort} (not digit-only, or outside {@code 1..65535})
 * yields no port; the host {@code :port} fallback is used only when no explicit port header is
 * present at all. Likewise an IPv6 host value must be supplied <em>bracketed</em>
 * ({@code [2001:db8::1]}) to be honored — an unbracketed multi-colon value yields no host.
 * A source that is present but resolves to nothing valid also <em>disagrees</em> with a sibling
 * source that resolved successfully, so the conflicting-source rule above drops the field.</p>
 *
 * <p>This applies to the RFC 7239 {@code Forwarded} header as a whole: when its raw value fails
 * sanitization, <em>or</em> when it carries a malformed {@code forwarded-pair} (a non-blank pair with
 * no {@code =}, an empty directive name, or an empty value — a grammatically legal blank pair is
 * still accepted), the header is treated as present-but-unresolvable for <em>every</em> field it could
 * have carried (scheme, host, client-IP), not as absent. A garbage {@code Forwarded} header therefore
 * disagrees with — and drops — an otherwise clean {@code X-Forwarded-*} value, instead of letting the
 * de-facto family win by default. Only a header that is genuinely absent, or one that sanitizes and
 * parses cleanly while simply carrying no directive for a given field, leaves that field's de-facto
 * value to stand on its own.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * ForwardedResolverConfig config = ForwardedResolverConfig.builder()
 *     .trustAll(true)
 *     // The ingress proxies themselves, never the enclosing network: every address in the range is
 *     // skipped by the client-IP walk, so a non-proxy host inside it could spoof the client IP.
 *     .trustedProxies(Set.of("10.0.7.10/32", "10.0.7.11/32"))
 *     .build();
 * ForwardedHeaderResolver resolver =
 *     new ForwardedHeaderResolver(config, new SecurityEventCounter());
 *
 * // The accessor MUST expose every instance of a repeated header, not just the first:
 * ResolvedForwarding forwarding =
 *     resolver.resolve(name -> Collections.list(request.getHeaders(name)));
 * }</pre>
 *
 * <p>Instances are immutable and thread-safe (the underlying pipeline and event counter are
 * thread-safe).</p>
 *
 * @since 1.0
 */
public final class ForwardedHeaderResolver {

    private static final CuiLogger LOGGER = new CuiLogger(ForwardedHeaderResolver.class);
    private static final int MAX_PORT = 65535;

    private final ForwardedResolverConfig config;
    private final HttpSecurityValidator headerValueValidator;

    /**
     * Creates a resolver.
     *
     * @param config the trust-model and precedence configuration
     * @param counter the security event counter used by the sanitization pipeline
     * @throws NullPointerException if {@code config} or {@code counter} is {@code null}
     */
    public ForwardedHeaderResolver(ForwardedResolverConfig config, SecurityEventCounter counter) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(counter, "counter must not be null");
        this.headerValueValidator = PipelineFactory.createHeaderValuePipeline(config.securityConfig(), counter);
    }

    /**
     * Resolves the forwarded-header family from the supplied header accessor.
     *
     * @param headerLookup maps a header name to <em>every</em> instance of that header present on
     *                     the request, in wire order; {@code null} or an empty list means the header
     *                     is absent. A single-valued accessor such as {@code request::getHeader} is
     *                     <strong>insufficient</strong> — it exposes only the first instance, hiding
     *                     the remaining ones, and a proxy appends by adding a repeated header just
     *                     as legitimately as by extending a comma-separated value. Use
     *                     {@code name -> Collections.list(request.getHeaders(name))} instead.
     * @return the sanitized, honored result (never {@code null}; {@link ResolvedForwarding#empty()}
     *         when nothing is present or honored)
     * @throws NullPointerException if {@code headerLookup} is {@code null}
     */
    public ResolvedForwarding resolve(Function<String, List<String>> headerLookup) {
        Objects.requireNonNull(headerLookup, "headerLookup must not be null");
        UnaryOperator<String> lookup = joining(headerLookup);
        ForwardedResult forwarded = parseForwarded(lookup);

        Optional<String> scheme = resolveScheme(lookup, forwarded);
        HostPort hostPort = resolveHost(lookup, forwarded);
        OptionalInt port = resolvePort(lookup, hostPort.port());
        String contextPath = resolveContextPath(lookup, forwarded);
        Optional<String> clientIp = resolveClientIp(lookup, forwarded);

        return new ResolvedForwarding(scheme, hostPort.host(), port, contextPath, clientIp);
    }

    // --- scheme ------------------------------------------------------------------------------

    private Optional<String> resolveScheme(UnaryOperator<String> lookup, ForwardedResult forwarded) {
        if (!config.trustAll()) {
            return Optional.empty();
        }
        String deFacto = firstPresent(lookup, X_FORWARDED_PROTO, X_PROXY_SCHEME);
        String rfc = forwarded.parsed().proto().orElse(null);
        return reconcileSources("scheme", X_FORWARDED_PROTO,
                deFacto != null, deFacto == null ? Optional.empty() : schemeOf(deFacto),
                forwarded.contributes(rfc != null), rfc == null ? Optional.empty() : schemeOf(rfc));
    }

    private Optional<String> schemeOf(String raw) {
        return sanitize(X_FORWARDED_PROTO, raw)
                .map(ForwardedHeaderResolver::lastToken)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> "http".equals(value) || "https".equals(value));
    }

    // --- host --------------------------------------------------------------------------------

    private HostPort resolveHost(UnaryOperator<String> lookup, ForwardedResult forwarded) {
        if (!config.trustAll()) {
            return HostPort.EMPTY;
        }
        String deFacto = firstPresent(lookup, X_FORWARDED_HOST, X_PROXY_HOST);
        String rfc = forwarded.parsed().host().orElse(null);
        return reconcileSources("host", X_FORWARDED_HOST,
                deFacto != null, deFacto == null ? Optional.empty() : hostPortOf(deFacto),
                forwarded.contributes(rfc != null), rfc == null ? Optional.empty() : hostPortOf(rfc))
                .orElse(HostPort.EMPTY);
    }

    private Optional<HostPort> hostPortOf(String raw) {
        return sanitize(X_FORWARDED_HOST, raw)
                .map(ForwardedHeaderResolver::lastToken)
                .map(ForwardedHeaderResolver::parseHostPort)
                .filter(hostPort -> hostPort.host().isPresent());
    }

    /**
     * Splits a {@code host[:port]} token (bracketed IPv6 aware) and validates the host contains no
     * path/backslash/whitespace/URL-authority-delimiter characters. Returns {@link HostPort#EMPTY}
     * for a malformed host.
     *
     * <p><strong>An IPv6 host must be bracketed.</strong> An unbracketed value carrying more than
     * one colon (a bare IPv6 literal such as {@code 2001:db8::1}) is rejected: the host is later
     * composed back into a URL authority, where an unbracketed IPv6 literal produces a malformed or
     * attacker-steerable authority. Supply it as {@code [2001:db8::1]} to have it honored.</p>
     *
     * <p><strong>Trailing content after {@code ]} is rejected.</strong> The only thing permitted
     * after the closing bracket is a colon followed by one or more ASCII digits, so
     * {@code [::1]garbage} and {@code [::1]x:8443} yield {@link HostPort#EMPTY} instead of resolving
     * to host {@code [::1]}. That rule is not restated here: it is applied through the shared
     * {@link IpAddresses#hasValidBracketTrailer(String)} helper.</p>
     *
     * <p>The {@code host:port} split here intentionally diverges from
     * {@link IpAddresses#parseChainEntry(String)}: this method reconstructs the <em>host string</em>
     * and therefore <em>retains</em> the IPv6 brackets (a host is later composed back into a URL),
     * whereas {@code parseChainEntry} strips them to obtain a bare literal for {@code InetAddress}
     * matching. Only the bracket <em>retention</em> differs: the trailing-content rule has a single
     * implementation shared by both call sites, so the two bracket policies cannot drift apart.</p>
     */
    private static HostPort parseHostPort(String value) {
        String host;
        OptionalInt port = OptionalInt.empty();
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 0) {
                return HostPort.EMPTY;
            }
            String rest = value.substring(close + 1);
            if (!IpAddresses.hasValidBracketTrailer(rest)) {
                return HostPort.EMPTY;
            }
            if (!rest.isEmpty()) {
                port = parsePort(rest.substring(1));
            }
            host = value.substring(0, close + 1);
        } else if (value.indexOf(':') == value.lastIndexOf(':') && value.indexOf(':') >= 0) {
            host = value.substring(0, value.indexOf(':'));
            port = parsePort(value.substring(value.indexOf(':') + 1));
        } else if (value.indexOf(':') >= 0) {
            // Neither bracketed nor host:port, yet colon-bearing: a bare IPv6 literal.
            return HostPort.EMPTY;
        } else {
            host = value;
        }
        if (host.isEmpty() || containsHostSeparator(host)) {
            return HostPort.EMPTY;
        }
        return new HostPort(Optional.of(host), port);
    }

    /**
     * Rejects a path separator, backslash, whitespace, or any of the URL-authority delimiter
     * characters ({@code @ # ?}). The consumer composes {@link ResolvedForwarding#host()} back
     * into an absolute URL (see the package's serialization/usage examples); an embedded
     * {@code @} would let a forged host smuggle a userinfo component
     * ({@code https://real-host@attacker.example/}), which most URL parsers resolve to the
     * <em>attacker's</em> host rather than the trusted one — the same host-confusion class the
     * path/backslash checks already guard against, just via a different delimiter.
     */
    private static boolean containsHostSeparator(String host) {
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '/' || c == '\\' || c == '@' || c == '#' || c == '?' || Character.isWhitespace(c)) {
                return true;
            }
        }
        return false;
    }

    // --- port --------------------------------------------------------------------------------

    private OptionalInt resolvePort(UnaryOperator<String> lookup, OptionalInt hostPortFallback) {
        String raw = firstPresent(lookup, X_FORWARDED_PORT, X_PROXY_PORT);
        if (raw == null) {
            return hostPortFallback;
        }
        if (!config.trustAll()) {
            return OptionalInt.empty();
        }
        return sanitize(X_FORWARDED_PORT, raw)
                .map(ForwardedHeaderResolver::lastToken)
                .map(ForwardedHeaderResolver::parsePort)
                .orElse(OptionalInt.empty());
    }

    /**
     * Parses a digit-only port in {@code 1..65535}. The digit-only precondition is what keeps
     * {@code Integer.parseInt} from honoring its own lenient forms — a leading {@code +} would
     * otherwise make {@code +443} resolve to {@code 443}, and an interior space would slip through
     * a purely exception-based guard.
     */
    private static OptionalInt parsePort(String value) {
        String trimmed = value.strip();
        if (!isAllAsciiDigits(trimmed)) {
            return OptionalInt.empty();
        }
        try {
            int port = Integer.parseInt(trimmed);
            return port >= 1 && port <= MAX_PORT ? OptionalInt.of(port) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            // A digit run longer than int can hold.
            return OptionalInt.empty();
        }
    }

    /**
     * @return {@code true} when {@code value} is non-empty and every character is an ASCII digit
     */
    private static boolean isAllAsciiDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    // --- context path ------------------------------------------------------------------------

    private String resolveContextPath(UnaryOperator<String> lookup, ForwardedResult forwarded) {
        String raw = firstPresent(lookup, X_PROXY_CONTEXT_PATH, X_FORWARDED_PREFIX);
        if (raw == null) {
            if (forwarded.present()) {
                LOGGER.debug("Forwarded header present but carries no context-path directive");
            }
            return "";
        }
        // Apply the injection guards to the RAW value first: the header-value pipeline collapses a
        // protocol-relative "//host" prefix to "/host", masking the attack, so the guard must run
        // before sanitization can rewrite it.
        //
        // Token selection must in turn precede the guards, because a guard applied to the whole raw
        // string inspects only its leading characters and so misses an attack carried in a later
        // token: "/app, //attacker.com" does not itself start with "//", so isProtocolRelativeOrBackslash
        // would pass the whole string through, and the nearest-hop token "//attacker.com" would then
        // be honored unguarded. Selecting the last token first means every guard below runs against
        // the exact value that will be returned.
        String trimmed = lastToken(raw.strip());
        if (ContextPaths.containsControlCharacter(trimmed)) {
            LOGGER.warn(ForwardedLogMessages.WARN.CONTEXT_PATH_CONTROL_CHARACTERS_REJECTED, sanitizeForLog(trimmed));
            return "";
        }
        if (ContextPaths.isProtocolRelativeOrBackslash(trimmed)) {
            LOGGER.warn(ForwardedLogMessages.WARN.CONTEXT_PATH_PROTOCOL_RELATIVE_REJECTED, sanitizeForLog(trimmed));
            return "";
        }
        Optional<String> sanitized = sanitize(X_FORWARDED_PREFIX, trimmed);
        if (sanitized.isEmpty()) {
            return "";
        }
        String normalized = ContextPaths.normalize(sanitized.get());
        if (normalized.isEmpty()) {
            return "";
        }
        if (config.trustAll() || config.allowedContextPaths().contains(normalized)) {
            return normalized;
        }
        LOGGER.debug("Ignoring context path %s: not trusted and not in the allowlist", normalized);
        return "";
    }

    // --- client IP ---------------------------------------------------------------------------

    private Optional<String> resolveClientIp(UnaryOperator<String> lookup, ForwardedResult forwarded) {
        // Secure-by-default: without trusted proxies the immediate peer cannot be trusted, so the
        // forwarded chain (including the nearest hop) is not honored at all.
        if (config.trustedProxies().isEmpty()) {
            return Optional.empty();
        }
        String xff = lookup.apply(X_FORWARDED_FOR);
        boolean xffPresent = isPresent(xff);
        List<String> rfcChain = forwarded.parsed().forValues();
        boolean rfcPresent = forwarded.contributes(!rfcChain.isEmpty());

        Optional<String> fromXff = xffPresent
                ? sanitize(X_FORWARDED_FOR, xff).map(value -> List.of(value.split(","))).flatMap(this::walkChain)
                : Optional.empty();
        Optional<String> fromRfc = rfcChain.isEmpty() ? Optional.empty() : walkChain(rfcChain);

        return reconcileSources("client IP", X_FORWARDED_FOR, xffPresent, fromXff, rfcPresent, fromRfc);
    }

    /**
     * Walks the forwarded chain right-to-left, skipping trusted-proxy hops; the first untrusted,
     * well-formed address is the client. Any unparseable hop encountered aborts resolution
     * (secure default: an unverifiable chain yields no client IP).
     */
    private Optional<String> walkChain(List<String> chain) {
        for (int i = chain.size() - 1; i >= 0; i--) {
            String entry = chain.get(i).strip();
            if (entry.isEmpty()) {
                continue;
            }
            InetAddress address = IpAddresses.parseChainEntry(entry);
            if (address == null) {
                LOGGER.warn(ForwardedLogMessages.WARN.CLIENT_IP_ENTRY_UNPARSEABLE, sanitizeForLog(entry));
                return Optional.empty();
            }
            if (!config.isTrustedProxy(address)) {
                return Optional.of(IpAddresses.canonical(address));
            }
        }
        return Optional.empty();
    }

    // --- shared helpers ----------------------------------------------------------------------

    /**
     * Adapts a multi-instance header accessor to the single-value view every field resolver below
     * consumes, by joining the instances with {@code ", "}.
     *
     * <p>RFC 7230 §3.2.2 makes this equivalence explicit: a recipient MAY combine multiple instances
     * of a comma-separated-list header into one value by concatenating them in wire order, separated
     * by commas, without changing the message semantics. Joining here therefore makes a repeated
     * header indistinguishable from the equivalent single comma-separated header — which is exactly
     * what the nearest-hop token selection downstream needs in order to see every appended hop.</p>
     *
     * @return an accessor yielding the joined value, or {@code null} when the header is absent
     *         (null list, empty list, or a list holding only {@code null}s)
     */
    private static UnaryOperator<String> joining(Function<String, List<String>> headerLookup) {
        return name -> {
            List<String> instances = headerLookup.apply(name);
            if (instances == null || instances.isEmpty()) {
                return null;
            }
            String joined = instances.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
            return joined.isEmpty() ? null : joined;
        };
    }

    private ForwardedResult parseForwarded(UnaryOperator<String> lookup) {
        String raw = lookup.apply(FORWARDED);
        if (!isPresent(raw)) {
            return ForwardedResult.ABSENT;
        }
        // Sanitize the Forwarded header value before parsing its directives. A rejected value must
        // NOT collapse into the absent case: the header WAS sent, so it stays present-but-unresolvable
        // and disagrees with any de-facto sibling that resolves (fail closed).
        Optional<String> sanitized = sanitize(FORWARDED, raw);
        if (sanitized.isEmpty()) {
            return ForwardedResult.UNRESOLVABLE;
        }
        // A grammar-violating forwarded-pair rejects the whole header with the same severity as a
        // sanitization failure — the directives it could have carried are all unresolvable.
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse(sanitized.get());
        if (parsed.malformed()) {
            LOGGER.warn(ForwardedLogMessages.WARN.FORWARDED_DIRECTIVE_MALFORMED, sanitizeForLog(raw));
            return ForwardedResult.UNRESOLVABLE;
        }
        return new ForwardedResult(true, false, parsed);
    }

    /**
     * Runs a raw header value through the security header-value pipeline. Returns the sanitized
     * value, or empty when the value is absent/blank or fails sanitization (logged).
     */
    private Optional<String> sanitize(String headerName, @Nullable String raw) {
        if (!isPresent(raw)) {
            return Optional.empty();
        }
        try {
            return headerValueValidator.validate(raw);
        } catch (UrlSecurityException e) {
            LOGGER.warn(ForwardedLogMessages.WARN.FORWARDED_VALUE_SANITIZATION_REJECTED,
                    headerName, sanitizeForLog(raw));
            return Optional.empty();
        }
    }

    /**
     * Reconciles a field resolved independently from the de-facto {@code X-Forwarded-*} /
     * {@code X-Proxy*} family and from the RFC 7239 {@code Forwarded} header.
     *
     * <p>When only one source is present, its resolution is returned unchanged. When BOTH are
     * present the two resolutions must agree: a trustworthy proxy that populates both families does
     * not contradict itself, so a disagreement means at least one side is forged. The field is then
     * dropped (fail closed) rather than letting the de-facto header win on precedence — silently
     * preferring one source is exactly the behaviour an attacker exploits by supplying the family
     * the resolver happens to rank higher.</p>
     *
     * @param field          the field name, for the disagreement log record
     * @param deFactoHeader  the de-facto header name, for the disagreement log record
     * @param deFactoPresent whether the de-facto source was present at all (distinct from it being
     *                       present but unresolvable, which is a real disagreement)
     * @param fromDeFacto    the de-facto source's independent resolution
     * @param rfcPresent     whether the RFC 7239 directive was present at all
     * @param fromRfc        the RFC 7239 source's independent resolution
     * @return the agreed resolution, or empty when the sources disagree
     */
    private <T> Optional<T> reconcileSources(String field, String deFactoHeader,
            boolean deFactoPresent, Optional<T> fromDeFacto,
            boolean rfcPresent, Optional<T> fromRfc) {
        if (!deFactoPresent) {
            return fromRfc;
        }
        if (!rfcPresent) {
            return fromDeFacto;
        }
        if (fromDeFacto.equals(fromRfc)) {
            return fromDeFacto;
        }
        LOGGER.warn(ForwardedLogMessages.WARN.FORWARDED_SOURCES_DISAGREE,
                field, deFactoHeader, describeForLog(fromDeFacto), describeForLog(fromRfc));
        return Optional.empty();
    }

    /**
     * Renders a resolution for the disagreement log record, routing the value through
     * {@link #sanitizeForLog(String)} so no untrusted content reaches the log unfiltered.
     */
    private static String describeForLog(Optional<?> resolution) {
        return resolution.map(value -> sanitizeForLog(String.valueOf(value))).orElse("(nothing valid)");
    }

    private static @Nullable String firstPresent(UnaryOperator<String> lookup, String... headerNames) {
        for (String name : headerNames) {
            String value = lookup.apply(name);
            if (isPresent(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isPresent(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Selects the nearest-hop token of a comma-separated header value: the substring after the last
     * comma. Each proxy in the chain <em>appends</em> its own value, so the rightmost token is the
     * one contributed by the closest (and therefore most trustworthy) proxy, while any leading
     * tokens are attacker-supplied when the client sent the header itself.
     */
    private static String lastToken(String value) {
        int comma = value.lastIndexOf(',');
        return (comma < 0 ? value : value.substring(comma + 1)).strip();
    }

    /**
     * Strips control characters and truncates before interpolating an untrusted value into a log
     * message, so a malicious header cannot forge or inject log lines.
     */
    private static String sanitizeForLog(String value) {
        StringBuilder builder = new StringBuilder(Math.min(value.length(), 200));
        for (int i = 0; i < value.length() && i < 200; i++) {
            char c = value.charAt(i);
            builder.append(Character.isISOControl(c) ? '?' : c);
        }
        return builder.toString();
    }

    /**
     * Outcome of reading the RFC 7239 {@code Forwarded} header, keeping the header's own presence and
     * its sanitization verdict distinct from the presence of any individual directive.
     *
     * <p>Collapsing the three states into "are the parsed directives empty?" is what lets a
     * present-but-rejected header masquerade as an absent one, so the reconciliation in
     * {@link #reconcileSources} would silently honor the de-facto sibling instead of dropping the
     * field. The three states are:</p>
     * <ol>
     *   <li>{@link #ABSENT} — no {@code Forwarded} header was sent; the RFC source contributes
     *       nothing and the de-facto family is honored on its own.</li>
     *   <li>{@link #UNRESOLVABLE} — the header WAS sent but its raw value failed sanitization
     *       (CR/LF, control characters, over-length, suspicious pattern) or violated the RFC 7239
     *       grammar with a malformed {@code forwarded-pair}. The source counts as present for
     *       <em>every</em> field, so it disagrees with any de-facto sibling that resolves and the
     *       field is dropped (fail closed).</li>
     *   <li>Present and sanitized — the parsed directives decide per field. A well-formed value that
     *       simply carries no {@code proto} (say) leaves that one field's RFC source with nothing to
     *       contribute, which is an ordinary fallback rather than a disagreement.</li>
     * </ol>
     *
     * @param present      whether a non-blank {@code Forwarded} header was sent at all
     * @param unresolvable whether that header failed sanitization outright or parsed as malformed
     * @param parsed       the directives parsed from the sanitized value; empty unless present and
     *                     sanitized
     */
    private record ForwardedResult(boolean present, boolean unresolvable, RfcForwardedParser.Parsed parsed) {

        private static final RfcForwardedParser.Parsed NO_DIRECTIVES =
                new RfcForwardedParser.Parsed(Optional.empty(), Optional.empty(), List.of(), false);

        private static final ForwardedResult ABSENT = new ForwardedResult(false, false, NO_DIRECTIVES);

        private static final ForwardedResult UNRESOLVABLE = new ForwardedResult(true, true, NO_DIRECTIVES);

        /**
         * Whether the RFC 7239 source counts as present for a field whose directive presence is
         * {@code directivePresent}. An unresolvable header counts as present for every field — that
         * is precisely what forces the disagreement path instead of a silent fallback.
         */
        private boolean contributes(boolean directivePresent) {
            return unresolvable || directivePresent;
        }
    }

    /**
     * Host token plus an optional port extracted from a {@code host:port} value.
     */
    private record HostPort(Optional<String> host, OptionalInt port) {
        private static final HostPort EMPTY = new HostPort(Optional.empty(), OptionalInt.empty());

        /**
         * Renders as {@code host[:port]} so a disagreement log line reads as the header value it
         * came from rather than as the record's default field dump.
         */
        @Override
        public String toString() {
            return host.orElse("(none)") + (port.isPresent() ? ":" + port.getAsInt() : "");
        }
    }
}
