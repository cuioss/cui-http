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

import de.cuioss.http.security.config.SecurityConfiguration;
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
 *       for {@code X-Forwarded-For} client-IP resolution. An empty set honors no client IP.</li>
 * </ul>
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
     *         as an order-preserving unmodifiable copy
     */
    public Set<String> allowedContextPaths() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(allowedContextPaths));
    }

    /**
     * @return the raw trusted-proxy CIDR / IP specs, in configuration order, as an order-preserving
     *         unmodifiable copy
     */
    public Set<String> trustedProxies() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(trustedProxies));
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
         * @param trustedProxies CIDR ranges / IP literals defining trusted proxy hops for
         *                       client-IP resolution
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
