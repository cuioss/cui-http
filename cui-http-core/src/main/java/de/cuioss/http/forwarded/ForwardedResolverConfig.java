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

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.util.*;

/**
 * Immutable trust-model configuration for {@link ForwardedHeaderResolver}.
 *
 * <p>The resolver is <strong>secure-by-default</strong>: with the defaults (no allowlist, no
 * trusted proxies, {@code trustAll=false}), client-supplied forwarded values are ignored. To
 * honor forwarded values a deployment must opt in explicitly:</p>
 * <ul>
 *   <li>{@code trustAll} — honor the sanitized scheme / host / port and any sanitized
 *       context-path. Use only when the application sits fully behind a trusted proxy.</li>
 *   <li>{@code allowedContextPaths} — honor these specific normalized context paths even when
 *       {@code trustAll} is {@code false} (mirrors NiFi's {@code nifi.web.proxy.context.path}).</li>
 *   <li>{@code trustedProxies} — CIDR ranges / IP literals defining trusted proxy hops; required
 *       for {@code X-Forwarded-For} client-IP resolution. An empty set honors no client IP.
 *       <strong>A configured range must contain only proxies</strong> — see below.</li>
 * </ul>
 *
 * <h3 id="trusted-range-composition">Composition rule — a trusted range must contain only proxies</h3>
 * <p>The client-IP chain walk consumes the forwarded chain right-to-left and <em>skips</em> every
 * hop that falls inside a configured {@code trustedProxies} range; the first hop that does not is
 * returned as the client. Membership of that range is therefore a statement that the machine
 * <em>is a proxy whose appended chain entry can be believed</em> — not merely that it is on a
 * network you own. Both directions of getting the range wrong are real:</p>
 * <ul>
 *   <li><strong>Too broad → no client IP at all.</strong> A range wide enough to cover the whole
 *       network (or {@code 0.0.0.0/0}) makes <em>every</em> hop trusted, so the walk skips the
 *       entire chain, falls off the left end, and returns empty. This fails closed — no forged
 *       address is ever honored — but it is counter-intuitive to operators debugging a missing
 *       client IP, who typically widen the range further and make the symptom worse.</li>
 *   <li><strong>Too broad → client-IP spoofing.</strong> A non-proxy machine that happens to fall
 *       inside a configured range is skipped by the walk exactly as a real proxy would be. Anything
 *       that machine <em>prepends</em> to the chain is then treated as an earlier, untrusted hop and
 *       returned as the client IP. A single compromised or merely untrustworthy host inside the
 *       range is enough to spoof the client IP for every request it forwards.</li>
 * </ul>
 * <p>Scope each range to the proxy tier itself — the individual load-balancer / ingress addresses,
 * or the smallest subnet that holds nothing else — rather than to the enclosing VPC or office
 * network.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * ForwardedResolverConfig config = ForwardedResolverConfig.builder()
 *     .trustAll(true)
 *     .trustedProxies(Set.of("10.0.0.0/8", "2001:db8::/32"))
 *     .build();
 *
 * // Secure default — honors nothing:
 * ForwardedResolverConfig locked = ForwardedResolverConfig.secureDefault();
 * }</pre>
 *
 * <p>This class is immutable and thread-safe.</p>
 *
 * @since 1.0
 */
public final class ForwardedResolverConfig {

    private static final CuiLogger LOGGER = new CuiLogger(ForwardedResolverConfig.class);

    private final boolean trustAll;
    private final Set<String> allowedContextPaths;
    private final Set<String> trustedProxies;
    private final List<CidrRange> trustedProxyRanges;
    private final SecurityConfiguration securityConfig;

    private ForwardedResolverConfig(Builder builder) {
        this.trustAll = builder.trustAll;
        this.allowedContextPaths = Collections.unmodifiableSet(new LinkedHashSet<>(builder.allowedContextPaths));
        this.trustedProxies = Collections.unmodifiableSet(new LinkedHashSet<>(builder.trustedProxies));
        this.trustedProxyRanges = List.copyOf(builder.trustedProxyRanges);
        this.securityConfig = builder.securityConfig;
    }

    /**
     * @return whether sanitized scheme / host / port and any sanitized context-path are honored
     */
    public boolean trustAll() {
        return trustAll;
    }

    /**
     * @return the normalized context paths honored even when {@link #trustAll()} is {@code false},
     *         as the order-preserving unmodifiable view held by this configuration
     */
    public Set<String> allowedContextPaths() {
        return allowedContextPaths;
    }

    /**
     * @return the raw trusted-proxy CIDR / IP specs, in configuration order, as the
     *         order-preserving unmodifiable view held by this configuration
     */
    public Set<String> trustedProxies() {
        return trustedProxies;
    }

    /**
     * @return the security configuration driving the header-value sanitization pipeline
     */
    public SecurityConfiguration securityConfig() {
        return securityConfig;
    }

    /**
     * @param address the candidate address (an {@code X-Forwarded-For} hop)
     * @return {@code true} when {@code address} falls within any configured trusted-proxy range
     */
    boolean isTrustedProxy(InetAddress address) {
        for (CidrRange range : trustedProxyRanges) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the secure-by-default configuration: honors nothing, uses
     *         {@link SecurityConfiguration#defaults()}
     */
    public static ForwardedResolverConfig secureDefault() {
        return builder().build();
    }

    /**
     * @return a new builder initialized with secure defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parses a comma-separated allowlist of proxy context paths into a normalized,
     * deterministically-ordered, unmodifiable set (blank / slash-only / injection-rejected
     * entries dropped). Mirrors the prior-art {@code ProxyContextPathResolver.parseAllowlist}.
     *
     * @param commaSeparated the raw comma-separated allowlist (may be {@code null})
     * @return an unmodifiable set of normalized context paths in input order
     */
    public static Set<String> parseAllowlist(@Nullable String commaSeparated) {
        Set<String> allowed = new LinkedHashSet<>();
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return Collections.unmodifiableSet(allowed);
        }
        for (String entry : commaSeparated.split(",")) {
            String normalized = ContextPaths.normalize(entry);
            if (!normalized.isEmpty()) {
                allowed.add(normalized);
            }
        }
        return Collections.unmodifiableSet(allowed);
    }

    /**
     * Builder for {@link ForwardedResolverConfig}. All setters are validated for immediate
     * feedback; {@code trustedProxies} entries are parsed as IP literals / CIDR ranges and reject
     * malformed specs with {@link IllegalArgumentException}.
     */
    public static final class Builder {

        private boolean trustAll;
        private Set<String> allowedContextPaths = Set.of();
        private Set<String> trustedProxies = Set.of();
        private List<CidrRange> trustedProxyRanges = List.of();
        private SecurityConfiguration securityConfig = SecurityConfiguration.defaults();

        private Builder() {
        }

        /**
         * @param trustAll honor sanitized scheme / host / port and any sanitized context-path
         * @return this builder
         */
        public Builder trustAll(boolean trustAll) {
            this.trustAll = trustAll;
            return this;
        }

        /**
         * @param allowedContextPaths context paths to honor even when {@code trustAll=false}; each
         *                            entry is normalized (leading slash added, trailing slash
         *                            stripped) and empties are dropped
         * @return this builder
         * @throws NullPointerException if {@code allowedContextPaths} is {@code null}
         */
        public Builder allowedContextPaths(Set<String> allowedContextPaths) {
            Objects.requireNonNull(allowedContextPaths, "allowedContextPaths must not be null");
            Set<String> normalized = new LinkedHashSet<>();
            for (String entry : allowedContextPaths) {
                String value = ContextPaths.normalize(entry);
                if (!value.isEmpty()) {
                    normalized.add(value);
                }
            }
            this.allowedContextPaths = normalized;
            return this;
        }

        /**
         * Sets the trusted-proxy ranges used by the client-IP chain walk.
         *
         * <p>Each range must contain <em>only</em> proxies whose appended chain entry can be
         * believed — see the class-level
         * <a href="#trusted-range-composition">composition rule</a>. A range that is too broad
         * fails in both directions: covering the whole network makes every hop trusted, so the walk
         * exhausts the chain and yields no client IP at all; and any non-proxy machine inside a
         * configured range is skipped like a real proxy, so whatever it prepends to the chain is
         * returned as the client IP.</p>
         *
         * <p>A blank entry is skipped rather than parsed, but not silently: each one is reported at
         * {@code WARN} via {@code HTTP-126} so a configuration that produced an empty slot (a
         * trailing comma, an unsubstituted placeholder) is visible to the operator instead of
         * quietly shrinking the trusted set.</p>
         *
         * @param trustedProxies CIDR ranges / IP literals defining trusted proxy hops for
         *                       client-IP resolution; scope each to the proxy tier itself, never to
         *                       the enclosing VPC or office network
         * @return this builder
         * @throws NullPointerException if {@code trustedProxies} is {@code null}
         * @throws IllegalArgumentException if any entry is not a valid IP literal / CIDR
         */
        public Builder trustedProxies(Set<String> trustedProxies) {
            Objects.requireNonNull(trustedProxies, "trustedProxies must not be null");
            Set<String> raw = new LinkedHashSet<>();
            List<CidrRange> ranges = new ArrayList<>();
            for (String entry : trustedProxies) {
                if (entry.isBlank()) {
                    LOGGER.warn(ForwardedLogMessages.WARN.TRUSTED_PROXY_ENTRY_BLANK);
                    continue;
                }
                ranges.add(CidrRange.parse(entry));
                raw.add(entry.strip());
            }
            this.trustedProxies = raw;
            this.trustedProxyRanges = ranges;
            return this;
        }

        /**
         * @param securityConfig the security configuration for the sanitization pipeline
         * @return this builder
         * @throws NullPointerException if {@code securityConfig} is {@code null}
         */
        public Builder securityConfig(SecurityConfiguration securityConfig) {
            this.securityConfig = Objects.requireNonNull(securityConfig, "securityConfig must not be null");
            return this;
        }

        /**
         * @return a new immutable {@link ForwardedResolverConfig}
         */
        public ForwardedResolverConfig build() {
            return new ForwardedResolverConfig(this);
        }
    }
}
