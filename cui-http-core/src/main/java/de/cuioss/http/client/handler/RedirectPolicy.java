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
package de.cuioss.http.client.handler;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.*;

/**
 * Per-hop egress host policy deciding whether one redirect hop may be followed.
 * <p>
 * A {@code RedirectPolicy} answers a single question for every candidate hop: <em>may this client
 * send the next request to {@code to}, having just been redirected there from {@code from}?</em>
 * {@link #refuse(URI, URI)} returns an empty {@link Optional} when the hop is permitted, or the
 * {@link RedirectRefusal} naming why it is not.
 * <p>
 * Immutable and thread-safe: {@link #getAllowedHosts()} is an unmodifiable snapshot taken at build
 * time and every method is a pure function of the configured state and its arguments.
 *
 * <h3>Scope: this is an egress host policy, not a validation pipeline</h3>
 * <p>
 * This type is deliberately <strong>not</strong> a {@code de.cuioss.http.security} validation
 * pipeline and must not be mistaken for one. The {@code de.cuioss.http.security} pipelines sanitise
 * <em>inbound</em> HTTP components (paths, parameter names and values, headers, content types)
 * against attack-pattern databases at a server-side trust boundary. This class answers an
 * <em>outbound</em> question of a completely different shape — "is this host an acceptable egress
 * target for the next hop?" — with a small, fully enumerated set of ordered rules and no pattern
 * matching, no decoding, and no normalisation of the request itself. Routing a redirect target
 * through an inbound validation pipeline would neither express nor enforce the host policy this
 * type exists to enforce.
 *
 * <h3>Ordered refusal rules</h3>
 * <p>
 * {@link #refuse(URI, URI)} evaluates the following rules in order and returns on the first match:
 * <ol>
 *   <li>{@code to} carries a scheme that is neither {@code http} nor {@code https} (including a
 *       scheme-less URI) &rarr; {@link RedirectRefusal#UNSUPPORTED_SCHEME}.</li>
 *   <li>{@code from} is {@code https} and {@code to} is {@code http} &rarr;
 *       {@link RedirectRefusal#PROTOCOL_DOWNGRADE}. This is evaluated <em>before</em> the allowlist,
 *       so an allowlisted host never buys a downgrade off TLS.</li>
 *   <li>{@code from} and {@code to} are same-origin &rarr; permitted.</li>
 *   <li>the host of {@code to} is in {@link #getAllowedHosts()} &rarr; permitted.</li>
 *   <li>otherwise &rarr; {@link RedirectRefusal#CROSS_ORIGIN}.</li>
 * </ol>
 * <p>
 * Two URIs are same-origin when their schemes match case-insensitively, their hosts match
 * case-insensitively, and their <em>effective</em> ports match — an absent port ({@code -1})
 * normalises to {@code 443} for {@code https} and {@code 80} for {@code http}, so
 * {@code https://example.com/a} and {@code https://example.com:443/b} are the same origin.
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Same-origin only (the secure default): every cross-origin hop is refused
 * RedirectPolicy strict = RedirectPolicy.sameOrigin();
 * Optional&lt;RedirectPolicy.RedirectRefusal&gt; refusal = strict.refuse(
 *     URI.create("https://api.example.com/v1"),
 *     URI.create("https://cdn.example.net/v1"));
 * // refusal.isPresent() == true, value CROSS_ORIGIN
 *
 * // Allow a specific downstream host, and let credentials survive the hop to it
 * RedirectPolicy allowlisted = RedirectPolicy.builder()
 *     .maxHops(3)
 *     .allowedHosts(List.of("cdn.example.net"))
 *     .credentialForwarding(RedirectPolicy.CredentialForwarding.FORWARD_TO_ALLOWLISTED)
 *     .build();
 * </pre>
 *
 * @since 2.2
 * @see RedirectNotAllowedException
 */
@Getter
@EqualsAndHashCode
@ToString
public final class RedirectPolicy {

    /**
     * Default hop budget applied when a builder does not set one explicitly.
     */
    public static final int DEFAULT_MAX_HOPS = 10;

    private static final int HTTPS_DEFAULT_PORT = 443;
    private static final int HTTP_DEFAULT_PORT = 80;
    private static final String SCHEME_HTTPS = "https";
    private static final String SCHEME_HTTP = "http";

    /**
     * The maximum number of redirect hops a follower may take before giving up. Always positive.
     *
     * @return the configured hop budget
     */
    private final int maxHops;

    /**
     * The hosts a cross-origin hop may target, ASCII-lowercased. Empty by default, which makes the
     * policy same-origin only.
     * <p>
     * Stored as a private, independently-owned copy — see {@link #getAllowedHosts()} for how it is
     * exposed without leaking this internal representation.
     */
    @Getter(AccessLevel.NONE)
    private final Set<String> allowedHosts;

    /**
     * Whether {@code Authorization} and {@code Cookie} survive a non-same-origin hop.
     *
     * @return the configured credential-forwarding strategy
     */
    private final CredentialForwarding credentialForwarding;

    private RedirectPolicy(int maxHops, Set<String> allowedHosts, CredentialForwarding credentialForwarding) {
        this.maxHops = maxHops;
        // Defensive copy at the boundary: even though the builder already hands over an unmodifiable,
        // independently-owned set, copying here again decouples this field from whatever reference was
        // passed in, so this constructor is safe on its own and never aliases caller-owned state.
        this.allowedHosts = new LinkedHashSet<>(allowedHosts);
        this.credentialForwarding = credentialForwarding;
    }

    /**
     * The hosts a cross-origin hop may target, ASCII-lowercased.
     * <p>
     * Returns an unmodifiable view backed by a private, independently-owned copy: the returned
     * {@link Set} can never be mutated (attempting to do so throws
     * {@link UnsupportedOperationException}), and no reference to the internal field is ever handed
     * out, so mutating any collection the caller separately holds can never widen this policy's
     * egress allowlist after construction.
     *
     * @return the unmodifiable, ASCII-lowercased set of additionally permitted hosts
     */
    public Set<String> getAllowedHosts() {
        return Collections.unmodifiableSet(allowedHosts);
    }

    /**
     * Returns the secure default policy: same-origin hops only, no allowlisted hosts, credentials
     * stripped on every non-same-origin hop, and a budget of {@value #DEFAULT_MAX_HOPS} hops.
     *
     * @return a same-origin-only policy, never {@code null}
     */
    public static RedirectPolicy sameOrigin() {
        return builder().build();
    }

    /**
     * Returns a builder pre-loaded with the same defaults as {@link #sameOrigin()}.
     *
     * @return a new builder, never {@code null}
     */
    public static RedirectPolicyBuilder builder() {
        return new RedirectPolicyBuilder();
    }

    /**
     * Decides whether the hop from {@code from} to {@code to} may be followed.
     * <p>
     * See the ordered rules in the class documentation; the first matching rule wins.
     *
     * @param from the URI the redirect response was received from
     * @param to   the absolute redirect target
     * @return an empty {@link Optional} when the hop is permitted, otherwise the
     *         {@link RedirectRefusal} naming why it is not
     */
    public Optional<RedirectRefusal> refuse(URI from, URI to) {
        String toScheme = asciiLowerCase(to.getScheme());
        if (!SCHEME_HTTPS.equals(toScheme) && !SCHEME_HTTP.equals(toScheme)) {
            return Optional.of(RedirectRefusal.UNSUPPORTED_SCHEME);
        }
        String fromScheme = asciiLowerCase(from.getScheme());
        // Evaluated ahead of the allowlist: an allowlisted host must never buy a downgrade off TLS.
        if (SCHEME_HTTPS.equals(fromScheme) && SCHEME_HTTP.equals(toScheme)) {
            return Optional.of(RedirectRefusal.PROTOCOL_DOWNGRADE);
        }
        if (isSameOrigin(from, to)) {
            return Optional.empty();
        }
        String toHost = asciiLowerCase(to.getHost());
        if (toHost != null && allowedHosts.contains(toHost)) {
            return Optional.empty();
        }
        return Optional.of(RedirectRefusal.CROSS_ORIGIN);
    }

    /**
     * Decides whether {@code Authorization} and {@code Cookie} headers may be carried across the hop
     * from {@code from} to {@code to}.
     * <p>
     * A cleartext target ends the question: when the scheme of {@code to} is {@code http}, this
     * method returns {@code false} whatever the same-origin and strategy verdicts would otherwise
     * be, so no credential is written onto an unencrypted wire. That closes the two hops
     * {@link #refuse(URI, URI)} still permits to a cleartext target — an {@code http} &rarr;
     * {@code http} same-origin hop, and an {@code http} &rarr; {@code http} hop to an allowlisted
     * host under {@link CredentialForwarding#FORWARD_TO_ALLOWLISTED}. An {@code https} &rarr;
     * {@code http} hop never reaches this method at all: {@code refuse} already rejects it with
     * {@link RedirectRefusal#PROTOCOL_DOWNGRADE}.
     * <p>
     * For an {@code https} target the strategy decides: a same-origin hop forwards credentials under
     * both strategies, and a non-same-origin hop forwards them only under
     * {@link CredentialForwarding#FORWARD_TO_ALLOWLISTED}.
     * <p>
     * This method never changes a {@link #refuse(URI, URI)} verdict and is meaningful only for a hop
     * that {@code refuse} already permitted — a hop it refused is not taken at all. The cleartext
     * rule narrows only what is carried; it refuses no hop and redirects none.
     *
     * @param from the URI the redirect response was received from
     * @param to   the absolute redirect target
     * @return {@code true} when credentials may be forwarded across this hop
     */
    public boolean forwardsCredentials(URI from, URI to) {
        if (SCHEME_HTTP.equals(asciiLowerCase(to.getScheme()))) {
            return false;
        }
        return isSameOrigin(from, to) || credentialForwarding == CredentialForwarding.FORWARD_TO_ALLOWLISTED;
    }

    /**
     * Compares scheme (case-insensitive), host (case-insensitive) and effective port, where an
     * absent port normalises to the scheme's default.
     */
    private static boolean isSameOrigin(URI from, URI to) {
        String fromScheme = asciiLowerCase(from.getScheme());
        String toScheme = asciiLowerCase(to.getScheme());
        if (fromScheme == null || !fromScheme.equals(toScheme)) {
            return false;
        }
        String fromHost = asciiLowerCase(from.getHost());
        String toHost = asciiLowerCase(to.getHost());
        if (fromHost == null || !fromHost.equals(toHost)) {
            return false;
        }
        return effectivePort(from) == effectivePort(to);
    }

    /**
     * Resolves the port actually connected to: an explicit port when the URI carries one, otherwise
     * the scheme default ({@code 443} for https, {@code 80} for http). Any other scheme has no
     * default and reports {@code -1}; such a URI is refused by rule 1 before this matters.
     */
    private static int effectivePort(URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        String scheme = asciiLowerCase(uri.getScheme());
        if (SCHEME_HTTPS.equals(scheme)) {
            return HTTPS_DEFAULT_PORT;
        }
        if (SCHEME_HTTP.equals(scheme)) {
            return HTTP_DEFAULT_PORT;
        }
        return -1;
    }

    /**
     * Lowercases the ASCII letters {@code A-Z} and leaves every other code point untouched.
     * <p>
     * Deliberately not {@link String#toLowerCase()}: scheme and host comparison must not depend on
     * the default locale (the Turkish dotless-i mapping turns {@code I} into {@code ı}, which would
     * make {@code API.EXAMPLE.COM} stop matching {@code api.example.com}), and must not fold
     * non-ASCII code points that an IDNA-encoded host never contains anyway.
     */
    private static @Nullable String asciiLowerCase(@Nullable String value) {
        return value == null ? null : asciiLowerCaseOf(value);
    }

    /**
     * ASCII-lowercasing variant for a value already known to be non-{@code null}.
     *
     * @see #asciiLowerCase(String)
     */
    private static String asciiLowerCaseOf(String value) {
        char[] chars = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                if (chars == null) {
                    chars = value.toCharArray();
                }
                chars[i] = (char) (c + ('a' - 'A'));
            }
        }
        return chars == null ? value : new String(chars);
    }

    /**
     * The reasons a redirect hop is refused.
     *
     * @since 2.2
     */
    public enum RedirectRefusal {
        /**
         * The redirect target names a scheme other than {@code http} or {@code https}, or names no
         * scheme at all.
         */
        UNSUPPORTED_SCHEME,
        /**
         * The hop would move from {@code https} to {@code http}, dropping transport security.
         */
        PROTOCOL_DOWNGRADE,
        /**
         * The redirect target is neither same-origin nor an allowlisted host.
         */
        CROSS_ORIGIN,
        /**
         * The hop budget ({@link RedirectPolicy#getMaxHops()}) was exhausted. Raised by the
         * redirect-following loop rather than by {@link RedirectPolicy#refuse(URI, URI)}, which
         * evaluates a single hop and has no notion of how many preceded it; it is declared here so
         * every refusal reason has one home.
         */
        TOO_MANY_HOPS,
        /**
         * The {@code Location} header was present and non-blank but is not a syntactically valid URI
         * reference, so no target could be resolved to evaluate. Like {@link #TOO_MANY_HOPS} this is
         * raised by the redirect-following loop rather than by {@link RedirectPolicy#refuse(URI, URI)}:
         * the refusal happens <em>before</em> a target exists to hand to the policy. The
         * {@code Location} value is remote-controlled, so the loop treats an unparseable one as a
         * refusal of the hop rather than letting the parse failure escape untyped.
         */
        MALFORMED_LOCATION
    }

    /**
     * Whether {@code Authorization} and {@code Cookie} headers survive a non-same-origin hop.
     *
     * @since 2.2
     */
    public enum CredentialForwarding {
        /**
         * Secure default: drop {@code Authorization} and {@code Cookie} on every non-same-origin
         * hop. Used by {@link RedirectPolicy#sameOrigin()} and by any builder that does not name a
         * strategy explicitly.
         */
        STRIP_ON_CROSS_ORIGIN,
        /**
         * Explicit opt-in: let credentials survive a cross-origin hop, but only to a host
         * {@link RedirectPolicy#refuse(URI, URI)} already permitted and only when that host is
         * reached over {@code https} — a cleartext target is stripped under this strategy too, see
         * {@link RedirectPolicy#forwardsCredentials(URI, URI)}. It changes no refusal verdict —
         * a hop that is refused is never taken, so no credential is ever forwarded to it.
         */
        FORWARD_TO_ALLOWLISTED
    }

    /**
     * Builder for {@link RedirectPolicy}. Not thread-safe; the {@link RedirectPolicy} it builds is.
     *
     * @since 2.2
     */
    public static final class RedirectPolicyBuilder {

        private int maxHops = DEFAULT_MAX_HOPS;
        private Set<String> allowedHosts = Collections.emptySet();
        private CredentialForwarding credentialForwarding = CredentialForwarding.STRIP_ON_CROSS_ORIGIN;

        private RedirectPolicyBuilder() {
        }

        /**
         * Sets the hop budget. Default: {@value RedirectPolicy#DEFAULT_MAX_HOPS}.
         *
         * @param maxHops the maximum number of hops to follow, must be positive
         * @return this builder
         * @throws IllegalArgumentException if {@code maxHops} is not positive
         */
        public RedirectPolicyBuilder maxHops(int maxHops) {
            if (maxHops <= 0) {
                throw new IllegalArgumentException("maxHops must be positive, but was " + maxHops);
            }
            this.maxHops = maxHops;
            return this;
        }

        /**
         * Sets the hosts a cross-origin hop may target. Entries are ASCII-lowercased and de-duplicated;
         * the supplied collection is copied, so later mutation of it does not affect the built policy.
         * Default: empty (same-origin only).
         *
         * @param allowedHosts the additionally permitted hosts, must contain no {@code null} or blank entry
         * @return this builder
         * @throws IllegalArgumentException if any entry is {@code null} or blank
         */
        public RedirectPolicyBuilder allowedHosts(Collection<String> allowedHosts) {
            Set<String> normalized = new LinkedHashSet<>();
            for (String host : allowedHosts) {
                if (host == null || host.isBlank()) {
                    throw new IllegalArgumentException("allowedHosts must not contain a null or blank entry");
                }
                normalized.add(asciiLowerCaseOf(host.strip()));
            }
            this.allowedHosts = Collections.unmodifiableSet(normalized);
            return this;
        }

        /**
         * Sets the credential-forwarding strategy.
         * Default: {@link CredentialForwarding#STRIP_ON_CROSS_ORIGIN}.
         *
         * @param credentialForwarding the strategy to apply on non-same-origin hops
         * @return this builder
         */
        public RedirectPolicyBuilder credentialForwarding(CredentialForwarding credentialForwarding) {
            this.credentialForwarding = credentialForwarding;
            return this;
        }

        /**
         * Builds the immutable policy.
         *
         * @return a new {@link RedirectPolicy}, never {@code null}
         */
        public RedirectPolicy build() {
            return new RedirectPolicy(maxHops, allowedHosts, credentialForwarding);
        }
    }
}
