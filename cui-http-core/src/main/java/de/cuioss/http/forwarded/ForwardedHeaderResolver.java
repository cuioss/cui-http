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

import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.PipelineFactory;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.util.*;
import java.util.function.Function;

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
 * <h3>Precedence</h3>
 * <ul>
 *   <li>scheme: {@code X-Forwarded-Proto} → {@code X-ProxyScheme} → RFC 7239 {@code proto}</li>
 *   <li>host: {@code X-Forwarded-Host} → {@code X-ProxyHost} → RFC 7239 {@code host}</li>
 *   <li>port: {@code X-Forwarded-Port} → {@code X-ProxyPort} → host {@code :port} fallback</li>
 *   <li>context-path: {@code X-ProxyContextPath} → {@code X-Forwarded-Prefix}
 *       ({@code Forwarded} has no prefix directive)</li>
 *   <li>client-IP: {@code X-Forwarded-For} chain → RFC 7239 {@code for} chain</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * ForwardedResolverConfig config = ForwardedResolverConfig.builder()
 *     .trustAll(true)
 *     .trustedProxies(Set.of("10.0.0.0/8"))
 *     .build();
 * ForwardedHeaderResolver resolver =
 *     new ForwardedHeaderResolver(config, new SecurityEventCounter());
 *
 * ResolvedForwarding forwarding = resolver.resolve(request::getHeader);
 * }</pre>
 *
 * <p>Instances are immutable and thread-safe (the underlying pipeline and event counter are
 * thread-safe).</p>
 *
 * @since 1.0
 */
// S4276: Function<String,String> is the intentional transport-agnostic header-accessor abstraction
// (e.g. request::getHeader) per issue #88 — not a UnaryOperator (which implies operating on a value).
@SuppressWarnings("java:S4276")
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
     * @param headerLookup maps a header name to its value (or {@code null} when absent); typically
     *                     {@code request::getHeader}
     * @return the sanitized, honored result (never {@code null}; {@link ResolvedForwarding#empty()}
     *         when nothing is present or honored)
     * @throws NullPointerException if {@code headerLookup} is {@code null}
     */
    public ResolvedForwarding resolve(Function<String, String> headerLookup) {
        Objects.requireNonNull(headerLookup, "headerLookup must not be null");
        RfcForwardedParser.Parsed forwarded = parseForwarded(headerLookup);

        Optional<String> scheme = resolveScheme(headerLookup, forwarded);
        HostPort hostPort = resolveHost(headerLookup, forwarded);
        OptionalInt port = resolvePort(headerLookup, hostPort.port());
        String contextPath = resolveContextPath(headerLookup);
        Optional<String> clientIp = resolveClientIp(headerLookup, forwarded);

        return new ResolvedForwarding(scheme, hostPort.host(), port, contextPath, clientIp);
    }

    // --- scheme ------------------------------------------------------------------------------

    private Optional<String> resolveScheme(Function<String, String> lookup, RfcForwardedParser.Parsed forwarded) {
        String raw = firstPresent(lookup, X_FORWARDED_PROTO, X_PROXY_SCHEME);
        if (raw == null) {
            raw = forwarded.proto().orElse(null);
        }
        if (raw == null || !config.trustAll()) {
            return Optional.empty();
        }
        return sanitize(X_FORWARDED_PROTO, raw)
                .map(ForwardedHeaderResolver::firstToken)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> "http".equals(value) || "https".equals(value));
    }

    // --- host --------------------------------------------------------------------------------

    private HostPort resolveHost(Function<String, String> lookup, RfcForwardedParser.Parsed forwarded) {
        String raw = firstPresent(lookup, X_FORWARDED_HOST, X_PROXY_HOST);
        if (raw == null) {
            raw = forwarded.host().orElse(null);
        }
        if (raw == null || !config.trustAll()) {
            return HostPort.EMPTY;
        }
        Optional<String> sanitized = sanitize(X_FORWARDED_HOST, raw).map(ForwardedHeaderResolver::firstToken);
        if (sanitized.isEmpty()) {
            return HostPort.EMPTY;
        }
        return parseHostPort(sanitized.get());
    }

    /**
     * Splits a {@code host[:port]} token (bracketed IPv6 aware) and validates the host contains no
     * path/backslash/whitespace. Returns {@link HostPort#EMPTY} for a malformed host.
     */
    private static HostPort parseHostPort(String value) {
        String host;
        OptionalInt port = OptionalInt.empty();
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 0) {
                return HostPort.EMPTY;
            }
            host = value.substring(0, close + 1);
            String rest = value.substring(close + 1);
            if (rest.startsWith(":")) {
                port = parsePort(rest.substring(1));
            }
        } else if (value.indexOf(':') == value.lastIndexOf(':') && value.indexOf(':') >= 0) {
            host = value.substring(0, value.indexOf(':'));
            port = parsePort(value.substring(value.indexOf(':') + 1));
        } else {
            host = value;
        }
        if (host.isEmpty() || containsHostSeparator(host)) {
            return HostPort.EMPTY;
        }
        return new HostPort(Optional.of(host), port);
    }

    private static boolean containsHostSeparator(String host) {
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '/' || c == '\\' || Character.isWhitespace(c)) {
                return true;
            }
        }
        return false;
    }

    // --- port --------------------------------------------------------------------------------

    private OptionalInt resolvePort(Function<String, String> lookup, OptionalInt hostPortFallback) {
        String raw = firstPresent(lookup, X_FORWARDED_PORT, X_PROXY_PORT);
        if (raw == null) {
            return hostPortFallback;
        }
        if (!config.trustAll()) {
            return OptionalInt.empty();
        }
        return sanitize(X_FORWARDED_PORT, raw)
                .map(ForwardedHeaderResolver::firstToken)
                .map(ForwardedHeaderResolver::parsePort)
                .orElse(OptionalInt.empty());
    }

    private static OptionalInt parsePort(String value) {
        try {
            int port = Integer.parseInt(value.strip());
            return port >= 1 && port <= MAX_PORT ? OptionalInt.of(port) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    // --- context path ------------------------------------------------------------------------

    private String resolveContextPath(Function<String, String> lookup) {
        String raw = firstPresent(lookup, X_PROXY_CONTEXT_PATH, X_FORWARDED_PREFIX);
        if (raw == null) {
            if (isPresent(lookup.apply(FORWARDED))) {
                LOGGER.debug("Forwarded header present but carries no context-path directive");
            }
            return "";
        }
        // Apply the injection guards to the RAW value first: the header-value pipeline collapses a
        // protocol-relative "//host" prefix to "/host", masking the attack, so the guard must run
        // before sanitization can rewrite it.
        String trimmed = raw.strip();
        if (ContextPaths.containsControlCharacter(trimmed)) {
            LOGGER.warn(ForwardedLogMessages.WARN.CONTEXT_PATH_CONTROL_CHARACTERS_REJECTED, sanitizeForLog(trimmed));
            return "";
        }
        if (ContextPaths.isProtocolRelativeOrBackslash(trimmed)) {
            LOGGER.warn(ForwardedLogMessages.WARN.CONTEXT_PATH_PROTOCOL_RELATIVE_REJECTED, sanitizeForLog(trimmed));
            return "";
        }
        Optional<String> sanitized = sanitize(X_FORWARDED_PREFIX, raw);
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

    private Optional<String> resolveClientIp(Function<String, String> lookup, RfcForwardedParser.Parsed forwarded) {
        // Secure-by-default: without trusted proxies the immediate peer cannot be trusted, so the
        // forwarded chain (including the nearest hop) is not honored at all.
        if (config.trustedProxies().isEmpty()) {
            return Optional.empty();
        }
        List<String> chain;
        String xff = lookup.apply(X_FORWARDED_FOR);
        if (isPresent(xff)) {
            Optional<String> sanitized = sanitize(X_FORWARDED_FOR, xff);
            if (sanitized.isEmpty()) {
                return Optional.empty();
            }
            chain = List.of(sanitized.get().split(","));
        } else if (!forwarded.forValues().isEmpty()) {
            chain = forwarded.forValues();
        } else {
            return Optional.empty();
        }
        return walkChain(chain);
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

    private RfcForwardedParser.Parsed parseForwarded(Function<String, String> lookup) {
        String raw = lookup.apply(FORWARDED);
        if (!isPresent(raw)) {
            return new RfcForwardedParser.Parsed(Optional.empty(), Optional.empty(), List.of());
        }
        // Sanitize the Forwarded header value before parsing its directives.
        return sanitize(FORWARDED, raw)
                .map(RfcForwardedParser::parse)
                .orElseGet(() -> new RfcForwardedParser.Parsed(Optional.empty(), Optional.empty(), List.of()));
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

    private static @Nullable String firstPresent(Function<String, String> lookup, String... headerNames) {
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

    private static String firstToken(String value) {
        int comma = value.indexOf(',');
        return (comma < 0 ? value : value.substring(0, comma)).strip();
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
     * Host token plus an optional port extracted from a {@code host:port} value.
     */
    private record HostPort(Optional<String> host, OptionalInt port) {
        private static final HostPort EMPTY = new HostPort(Optional.empty(), OptionalInt.empty());
    }
}
