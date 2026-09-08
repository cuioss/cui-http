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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static de.cuioss.http.forwarded.ForwardedHeaderNames.*;

/**
 * Resolves the reverse-proxy / forwarded-header family into a single sanitized
 * {@link ResolvedForwarding}.
 *
 * <p>For each field the resolver: (1) selects the raw value by header precedence, (2) sanitizes it
 * through the existing {@link de.cuioss.http.security} header-value pipeline (rejecting over-length
 * input, NUL, other control characters including CR/LF, and — when so configured — extended ASCII),
 * (3) applies field-specific normalization and injection guards, and (4) honors it only when the
 * configured trust model permits. A value that fails sanitization is dropped and logged; a value
 * that is merely not trusted is also dropped, but only the context-path drop is logged (at
 * {@code DEBUG}).</p>
 *
 * <p><strong>{@code resolve} is fail-safe, not exception-free.</strong> No rejected or untrusted
 * header value produces an exception. It does, however, throw {@link NullPointerException} for a
 * {@code null} {@code headerLookup} (see {@link #resolve(Function)}), propagate any
 * {@link RuntimeException} thrown by the caller-supplied lookup, and propagate any exception other
 * than {@link UrlSecurityException} escaping the sanitization pipeline.</p>
 *
 * <h3 id="security-precondition">Security precondition — trusted network placement</h3>
 * <p><strong>{@link #resolve(Function)} trusts HTTP headers, not the socket.</strong> That overload
 * receives only a header accessor; the actual TCP peer (the socket remote address) is never passed
 * in and cannot be inspected. Consequently the {@code X-Forwarded-For} / {@code Forwarded} walk
 * cannot verify that the request actually arrived <em>through</em> a trusted proxy — it can only
 * match the addresses <em>inside the headers</em> against the configured {@code trustedProxies}.</p>
 * <p>A deployment using that overload therefore <strong>MUST</strong> guarantee that only trusted
 * proxies can connect to this server directly. If an attacker can reach the server without
 * traversing a trusted proxy, they can forge the chain (e.g. a single untrusted entry
 * {@code X-Forwarded-For: 6.6.6.6}) and have it returned verbatim as the client IP. Enforce this
 * with network controls — bind the listener to a private interface, restrict it with firewall /
 * security-group rules, or place it behind a service mesh — so that the socket peer is always a
 * trusted proxy.</p>
 * <p><strong>{@link #resolve(Function, InetAddress)} enforces that same property in code, and is
 * the stronger form.</strong> Given the socket peer it honors forwarded headers only when the peer
 * is itself a configured trusted proxy, and returns {@link ResolvedForwarding#empty()} otherwise —
 * so a request that did not arrive through the proxy tier attests nothing, whatever it claims in
 * its headers. Prefer it wherever the transport exposes the remote address; the network controls
 * above remain worth having as defence in depth, but are no longer the only thing standing between
 * a direct connection and a forged chain.</p>
 *
 * <h3>Precedence</h3>
 * <ul>
 *   <li>scheme: {@code X-Forwarded-Proto} reconciled with {@code X-ProxyScheme}, then reconciled
 *       against RFC 7239 {@code proto}</li>
 *   <li>host: {@code X-Forwarded-Host} reconciled with {@code X-ProxyHost}, then reconciled against
 *       RFC 7239 {@code host} — comparing the <em>host</em> alone, not the {@code host[:port]}
 *       token as a whole</li>
 *   <li>port: {@code X-Forwarded-Port} reconciled with {@code X-ProxyPort} and then, like every
 *       other field, against the port the RFC 7239 {@code host} directive carried; else the host
 *       {@code :port} fallback — itself the port each source's {@code host[:port]} token carried,
 *       reconciled <em>independently</em> of the host</li>
 *   <li>context-path: {@code X-ProxyContextPath} → {@code X-Forwarded-Prefix}
 *       ({@code Forwarded} has no prefix directive)</li>
 *   <li>client-IP: {@code X-Forwarded-For} chain, reconciled against the RFC 7239 {@code for}
 *       chain</li>
 * </ul>
 *
 * <p><strong>The de-facto families are reconciled against each other first.</strong> For scheme,
 * host, and port the {@code X-Forwarded-*} and {@code X-Proxy*} names are resolved
 * <em>independently</em> rather than by taking whichever name appears first in a fixed order. When
 * both are present and resolve to the same value, that value stands. When they disagree, the field
 * is <em>not</em> dropped: the family named by
 * {@link ForwardedResolverConfig#deFactoPrecedence()} wins and the disagreement is logged. That
 * knob is a tie-breaker of last resort and presumes the ingress strips the family it does not
 * itself write — see its Javadoc. The reconciled value, together with the header name it actually
 * came from, is what the RFC 7239 comparison below then sees; that comparison is unchanged and
 * still drops the field on disagreement.</p>
 *
 * <p><strong>That reconciliation is per field, not per header value.</strong> The host token the two
 * families carry states two fields, so host is compared against host and port against port here as
 * well, exactly as at the RFC 7239 stage below. A port only one family states is not in conflict
 * with the other family's silence and stands unopposed; only a port both state, differently, reaches
 * the precedence tie-break. A host, by contrast, is stated by the mere presence of the header — a
 * present-but-invalid one contests rather than falling through to its sibling family.</p>
 *
 * <p><strong>Nearest hop wins within a header.</strong> Each proxy in a chain <em>appends</em> its
 * own value, so for a comma-separated header value the resolver selects the <em>rightmost</em>
 * token — the one contributed by the closest, most trustworthy proxy. Leading tokens are
 * attacker-supplied whenever the original client sent the header itself. The same rule applies
 * across RFC 7239 elements: the <em>last</em> {@code proto} / {@code host} directive wins.</p>
 *
 * <p><strong>Conflicting sources = drop (fail closed).</strong> For scheme, host, port, and
 * client-IP the de-facto {@code X-Forwarded-*} / {@code X-Proxy*} family and the RFC 7239
 * {@code Forwarded} header are resolved <em>independently</em>. When only one source is present its result is honored. When
 * <em>both</em> are present they must agree: a proxy that populates both families does not
 * contradict itself, so a disagreement means at least one side is forged. The field is then dropped
 * and a warning logged, rather than letting the higher-precedence family silently win — preferring
 * one source is precisely what an attacker exploits by supplying the family the resolver ranks
 * higher.</p>
 *
 * <p><strong>Host and port are compared as separate fields.</strong> A source states both in one
 * {@code host[:port]} token, but that token is compared field by field: host against host, and the
 * port it carried against the port the other source carried. A host disagreement therefore drops
 * only the host, and a port disagreement only the port. Comparing the pair as one unit demanded
 * that both sources agree on host <em>and</em> port at once, so the legitimate split an ingress
 * writes as {@code X-Forwarded-Host: h} plus {@code X-Forwarded-Port: 8443} alongside
 * {@code Forwarded: host="h:8443"} was read as a conflict and dropped the host both sources
 * actually named.</p>
 *
 * <p><strong>Silence about the port is a non-statement on both sides.</strong> A source contests the
 * port only when its own {@code host[:port]} token carried one. That holds symmetrically: a
 * {@code Forwarded: host=h} directive naming a bare host contests no port an
 * {@code X-Forwarded-Host: h:8443} carried, and an {@code X-Forwarded-Host: h} naming a bare host
 * contests no port a {@code Forwarded: host="h:8443"} carried. Scoping the de-facto side to whether
 * the <em>family</em> was sent rather than to what its own token said broke that symmetry: a bare
 * {@code X-Forwarded-Host} dropped the RFC directive's port and logged a "sources disagree" warning
 * over a claim only one side ever made. The host keeps the stricter rule — the mere presence of the
 * header states it, so a present-but-invalid host still contests.</p>
 *
 * <p><strong>Silence is the absence of a port token, not the absence of a valid one — on both
 * sides.</strong> A {@code host[:port]} token that carried a port which fails to parse has
 * <em>stated</em> a port and contests exactly as a well-formed one does: the field is dropped
 * fail-closed with a warning. That holds symmetrically for {@code Forwarded: host="h:bogus"} and for
 * {@code X-Forwarded-Host: h:bogus} / {@code X-ProxyHost: h:bogus}, because which side an attacker
 * can reach is not something this resolver may assume. Reading "states a port" off the parsed
 * <em>value</em> instead made one unparseable character indistinguishable from genuine silence, so
 * the other source's port was accepted unopposed and unlogged on precisely the input a well-formed
 * conflict would have dropped. Applying the structural reading to only one of the two mirror sides
 * left that same hole open on the other.</p>
 *
 * <p><strong>Separate fields, but not separately believable when they share one value.</strong>
 * Per-field scoping decides which field a <em>disagreement</em> reaches; it never makes half of a
 * value credible after the other half was proven forged. So when a host disagreement drops the host,
 * the port a {@code host[:port]} token carried is withheld too — unless <em>both</em> sides stated a
 * port independently, in which case they corroborate each other and are compared like any other
 * field. A port only the losing side of the host comparison states has a rejected value as its sole
 * source, and honoring it lets an attacker smuggle an arbitrary port through whenever the other side
 * names a bare host. That rule is <em>symmetric</em>: it holds whether the forged {@code host[:port]}
 * token arrived in the de-facto family or in the RFC 7239 {@code host} directive, because which side
 * an attacker can reach is not something this resolver may assume. An explicit
 * {@code X-Forwarded-Port} / {@code X-ProxyPort} header never depends on this rule at all — it
 * supersedes the host-token port outright and is reconciled against RFC 7239 on its own.</p>
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
 * <p><strong>An unresolvable {@code Forwarded} header drops only the fields it spoke about.</strong>
 * When its raw value fails sanitization, <em>or</em> when it carries a malformed
 * {@code forwarded-pair} (a non-blank pair with no {@code =}, an empty directive name, or an empty
 * value — a grammatically legal blank pair is still accepted), the header contributes
 * <em>nothing</em>: it never supplies a value to the reconciliation, so any field it did carry a
 * directive for fails closed through the ordinary disagreement path. It is <em>not</em> treated as
 * present for every field it could theoretically have carried. Scope is per field, decided by the
 * directives the parser reached before it stopped: {@code Forwarded: proto=http;broken} drops the
 * scheme but leaves an {@code X-Forwarded-Host} value standing, because that header never spoke
 * about the host. Suppressing every field on any garbage input was a denial of service on the trust
 * model — an attacker appended one broken directive and erased everything a legitimate proxy
 * attested, which is the inverse of the fail-closed intent.</p>
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
        // The RFC 7239 host directive states BOTH the host and a port, and both the host stage and
        // the explicit-port stage must be reconciled against it, so it is resolved once here rather
        // than by each of them separately.
        RfcHost rfcHost = rfcHost(forwarded);
        boolean explicitPort = isPresent(lookup.apply(X_FORWARDED_PORT)) || isPresent(lookup.apply(X_PROXY_PORT));
        HostPort hostPort = resolveHost(lookup, rfcHost, explicitPort);
        OptionalInt port = resolvePort(lookup, rfcHost, explicitPort, hostPort.port());
        String contextPath = resolveContextPath(lookup, forwarded);
        Optional<String> clientIp = resolveClientIp(lookup, forwarded);

        return new ResolvedForwarding(scheme, hostPort.host(), port, contextPath, clientIp);
    }

    /**
     * Resolves the forwarded-header family, but only when the request actually arrived through a
     * configured trusted proxy.
     *
     * <p>This is the stronger of the two overloads (see the
     * <a href="#security-precondition">security precondition</a>). {@link #resolve(Function)} can
     * only match addresses <em>inside</em> the headers, so it relies on network placement to
     * guarantee that a forged chain never reaches it; this one is handed the socket peer and
     * enforces that guarantee itself. A peer outside {@code trustedProxies} attests nothing, so the
     * result is {@link ResolvedForwarding#empty()} regardless of what the headers claim.</p>
     *
     * <p>The gate is on the peer alone. Once it passes, resolution is the unchanged path — the
     * chain walk still skips trusted hops and still fails closed on an unparseable one, so a
     * trusted peer is permission to <em>read</em> the headers, never permission to believe them
     * uncritically.</p>
     *
     * @param headerLookup as {@link #resolve(Function)} — every instance of each header, in wire
     *                     order
     * @param peer         the socket remote address the request arrived from
     * @return the sanitized, honored result, or {@link ResolvedForwarding#empty()} when {@code peer}
     *         is not a configured trusted proxy (never {@code null})
     * @throws NullPointerException if {@code headerLookup} or {@code peer} is {@code null}
     */
    public ResolvedForwarding resolve(Function<String, List<String>> headerLookup, InetAddress peer) {
        Objects.requireNonNull(headerLookup, "headerLookup must not be null");
        Objects.requireNonNull(peer, "peer must not be null");
        if (!config.isTrustedProxy(peer)) {
            LOGGER.debug("Ignoring forwarded headers: peer is not a configured trusted proxy");
            return ResolvedForwarding.empty();
        }
        return resolve(headerLookup);
    }

    // --- scheme ------------------------------------------------------------------------------

    private Optional<String> resolveScheme(UnaryOperator<String> lookup, ForwardedResult forwarded) {
        if (!config.trustAll()) {
            return Optional.empty();
        }
        DeFactoResolution<String> deFacto =
                reconcileDeFacto(lookup, "scheme", X_FORWARDED_PROTO, X_PROXY_SCHEME, this::schemeOf);
        String rfc = forwarded.parsed().proto().orElse(null);
        return reconcileSources("scheme", deFacto.headerName(),
                deFacto.stated(), deFacto.value(),
                rfc != null, rfcResolution(forwarded, rfc, this::schemeOf));
    }

    private Optional<String> schemeOf(String headerName, String raw) {
        return sanitize(headerName, raw)
                .map(ForwardedHeaderResolver::lastToken)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> "http".equals(value) || "https".equals(value));
    }

    // --- host --------------------------------------------------------------------------------

    /**
     * Resolves the host and the port its {@code host[:port]} token carried as <em>two independent
     * fields</em>, at <em>both</em> reconciliation stages.
     *
     * <p>Each source is still parsed as one {@code host[:port]} token — that is what {@link HostPort}
     * is — but neither comparison ever runs over the whole record. The de-facto-versus-RFC-7239
     * comparison runs twice, host against host and port against port; so does the de-facto family
     * comparison that precedes it. Comparing a record as a unit made its equality demand that both
     * sides agree on host <em>and</em> port simultaneously, so the legitimate split an ingress writes
     * as {@code X-Forwarded-Host: h} plus {@code X-Forwarded-Port: 8443} alongside
     * {@code Forwarded: host="h:8443"} produced {@code (h, none)} against {@code (h, 8443)} and
     * dropped the host both sources actually agreed on. Reconciling field by field keeps each
     * outcome scoped to the field that actually disagreed.</p>
     *
     * <p><strong>An omitted port is a non-statement, not a conflicting one.</strong> The two de-facto
     * families are alternative spellings the same ingress writes, so
     * {@code X-Forwarded-Host: h} alongside {@code X-ProxyHost: h:9443} carries no port
     * <em>disagreement</em> at all — one spelling simply said nothing about the port. Whole-record
     * equality read that as a disagreement and handed the whole pair to
     * {@link ForwardedResolverConfig#deFactoPrecedence()}, which discarded a port no other source
     * contradicted. Only a port <em>both</em> families state, differently, is a genuine conflict and
     * reaches the precedence tie-break. The host field keeps the stricter rule — a present de-facto
     * header states the host even when it resolves to nothing valid, so a present-but-invalid host
     * still contests (and, losing or winning, never falls through to its sibling family).</p>
     *
     * <p><strong>The port statement is structural on this side too.</strong> "Carried a port" is
     * decided by {@link #reconcileStatedByPortToken} from the raw {@code host[:port]} token through
     * the same {@link #statesPort(String)} predicate {@link RfcHost#statesPort()} uses, so a token
     * whose port is present but unparseable ({@code X-Forwarded-Host: h:bogus}) states a port and
     * contests one the RFC 7239 directive carried — its resolution is simply empty, and the ordinary
     * disagreement path drops the field. A bare host still states nothing and still does not
     * contest.</p>
     */
    private HostPort resolveHost(UnaryOperator<String> lookup, RfcHost rfcHost, boolean explicitPortPresent) {
        if (!config.trustAll()) {
            return HostPort.EMPTY;
        }
        DeFactoSources<HostPort> deFacto =
                resolveDeFactoSources(lookup, X_FORWARDED_HOST, X_PROXY_HOST, this::hostPortOf);
        DeFactoResolution<String> deFactoHost = reconcileStatedByPresence(deFacto, "host", HostPort::host);
        DeFactoResolution<Integer> deFactoPort = reconcileStatedByPortToken(deFacto, "host port", HostPort::portValue);

        Optional<String> host = reconcileSources("host", deFactoHost.headerName(),
                deFactoHost.stated(), deFactoHost.value(),
                rfcHost.present(), rfcHost.host());
        // The host-token port is only ever a FALLBACK: an explicit port header supersedes it, and
        // resolvePort reconciles that header against RFC 7239 itself. Reconciling a fallback nobody
        // will consult is not merely wasted work — it logs a disagreement about a value that has no
        // bearing on the result, on exactly the split configuration this class documents as
        // legitimate (X-Forwarded-Host: h + X-Forwarded-Port: 8443 + Forwarded: host="h:8443").
        Optional<Integer> port = explicitPortPresent || hostTokenPortDiscredited(deFactoHost, deFactoPort, rfcHost)
                ? Optional.empty()
                : reconcileSources("host port", deFactoPort.headerName(),
                deFactoPort.stated(), deFactoPort.value(),
                rfcHost.statesPort(), rfcHost.port());

        return new HostPort(host, port.map(OptionalInt::of).orElseGet(OptionalInt::empty));
    }

    /**
     * Whether a {@code host[:port]} token's embedded port has been discredited along with the host
     * the same raw value carried — on <em>either</em> side of the comparison.
     *
     * <p>The host and the port of a {@code host[:port]} token are two fields, but they are two fields
     * read out of <em>one</em> raw value. When the de-facto and RFC 7239 hosts disagree, this class's
     * own rule — "a disagreement means at least one side is forged" — has been applied to those raw
     * values as wholes, not merely to the host characters inside them. Honoring a port that only one
     * of them carried lets an attacker smuggle an arbitrary port through, because the other side's
     * silence about the port leaves the surviving claim standing unopposed. Per-field scoping is
     * about which field a <em>disagreement</em> reaches, never about believing half of a value
     * already proven forged.</p>
     *
     * <p><strong>The rule is symmetric, and must be.</strong> Scoping the discredit to the de-facto
     * side alone covered only the direction where the forged token arrived as
     * {@code X-Forwarded-Host} / {@code X-ProxyHost}. The mirrored direction — a forged
     * {@code Forwarded: host="attacker.example:6666"} against a de-facto header naming a bare host —
     * fell through to {@link #reconcileSources}, which returns the RFC value unconditionally when the
     * de-facto side stated nothing, so the attacker-controlled port survived the very comparison that
     * had just rejected the host half of its own token. Which family an attacker can reach is not
     * something this resolver may assume, so the discredit is stated over both sides at once.</p>
     *
     * <p>Corroboration is what lifts the discredit, and it is deliberately narrow: <em>both</em>
     * sides must independently state a port, and the ordinary {@link #reconcileSources} comparison
     * then decides the outcome unchanged — agreeing ports stand on the two independent statements
     * rather than on a rejected value, and disagreeing ones are dropped as usual. A port stated by
     * one side only is not corroboration; it is the rejected value speaking for itself. The other
     * corroborating source, an explicit {@code X-Forwarded-Port} / {@code X-ProxyPort} header, never
     * reaches this fallback at all: it supersedes the host-token port outright, and
     * {@link #resolvePort} reconciles it against RFC 7239 on its own.</p>
     *
     * <p>Corroboration reads the de-facto side's <em>resolved</em> port, not merely its structural
     * statement, because a token whose port cannot be parsed carries no value to corroborate with.
     * That asymmetry with {@link #reconcileStatedByPortToken} costs nothing: whichever way an
     * unparseable de-facto port token is counted the field ends up empty, either discredited here or
     * dropped by the disagreement path in {@link #reconcileSources}. Reading the statement instead
     * would only move where the same fail-closed outcome is decided.</p>
     *
     * @return {@code true} when the host disagreed across the two sources <em>and</em> the two sides
     *         did not both state a port of their own to corroborate each other
     */
    private static boolean hostTokenPortDiscredited(DeFactoResolution<String> deFactoHost,
            DeFactoResolution<Integer> deFactoPort, RfcHost rfcHost) {
        boolean hostDisagreed = deFactoHost.stated() && rfcHost.present()
                && !deFactoHost.value().equals(rfcHost.host());
        boolean bothSidesStatePort = deFactoPort.value().isPresent() && rfcHost.statesPort();
        return hostDisagreed && !bothSidesStatePort;
    }

    /**
     * Resolves the RFC 7239 {@code host} directive once, for every stage that must be reconciled
     * against it.
     *
     * <p>The directive states two fields at once — the host and, when its token carries one, the
     * port — and both the host stage and the explicit-port stage compare against it. Resolving it in
     * one place keeps those stages reading the same value rather than re-deriving it, and keeps
     * <em>what the directive spoke about</em> separate from <em>what it may be believed to say</em>:
     * whether it carried a port is structural and survives the header being unbelievable, whereas
     * the port's value is withheld the moment the header is unresolvable.</p>
     */
    private RfcHost rfcHost(ForwardedResult forwarded) {
        String rfc = forwarded.parsed().host().orElse(null);
        if (rfc == null) {
            return RfcHost.ABSENT;
        }
        return new RfcHost(true, statesPort(lastToken(rfc)),
                rfcResolution(forwarded, rfc, this::hostPortOf));
    }

    /**
     * Whether a {@code host[:port]} token <em>carried a port token at all</em>, independently of
     * whether that token parses to a valid port.
     *
     * <p>Deriving this from {@link #parseHostPort(String)}'s resolved port instead collapsed two
     * structurally different inputs into one answer: {@code legit.example}, which stated no port,
     * and {@code legit.example:bogus}, which stated one that failed digit parsing. Both resolve to
     * an empty port, so the RFC side reported that it stated no port, {@link #reconcileSources}
     * short-circuited on a non-present RFC source, and a conflicting {@code X-Forwarded-Port} was
     * returned <em>unopposed</em> and unlogged — while a well-formed conflicting port was correctly
     * dropped. One unparseable character therefore switched the fail-closed disagreement check off,
     * which is the inverse of this class's intent and contradicts {@link RfcHost#statesPort()}'s own
     * rule that a directive which DID carry a port still contests it once it cannot be believed.</p>
     *
     * <p>It is the single answer <em>both</em> sides give: {@link #rfcHost} applies it to the RFC 7239
     * directive and {@link #reconcileStatedByPortToken} applies it to each de-facto family's raw
     * header, so the mirror sides cannot drift apart about what counts as stating a port.</p>
     *
     * <p>The predicate is deliberately <em>structural</em> and mirrors {@link #parseHostPort}'s
     * tokenization exactly, so the two never disagree about where the port token is: after a
     * bracketed literal only a {@code :} trailer is a port token (so {@code [::1]garbage} states
     * none), and an unbracketed value states one only when it carries exactly one colon — a
     * multi-colon unbracketed value is a bare IPv6 literal whose colons are not a port separator.</p>
     */
    private static boolean statesPort(String value) {
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            return close >= 0 && value.startsWith(":", close + 1);
        }
        int colon = value.indexOf(':');
        return colon >= 0 && colon == value.lastIndexOf(':');
    }

    /**
     * The RFC 7239 {@code host} directive's contribution, shared by the host and port stages.
     *
     * @param present    whether the directive was sent at all — distinct from it being sent but
     *                   unresolvable, which is a real disagreement rather than an absence
     * @param statesPort whether its token carried a port at all. A directive that named a bare host
     *                   says nothing about the port and must not contest one another source states;
     *                   a directive that DID carry a port still contests it after the header turns
     *                   out to be unresolvable, which is what keeps that case failing closed.
     *                   "Carried a port" is <em>structural</em> (see {@link #statesPort(String)}): a
     *                   token whose port token is present but unparseable ({@code host="h:bogus"})
     *                   carried one just as much as a well-formed one does, and therefore contests
     *                   just as much — its {@link #value} is simply empty, so the ordinary
     *                   disagreement path drops the field
     * @param value      its resolution ({@code empty} when absent or unresolvable)
     */
    private record RfcHost(boolean present, boolean statesPort, Optional<HostPort> value) {

        private static final RfcHost ABSENT = new RfcHost(false, false, Optional.empty());

        private Optional<String> host() {
            return value.flatMap(HostPort::host);
        }

        private Optional<Integer> port() {
            return value.flatMap(HostPort::portValue);
        }
    }

    private Optional<HostPort> hostPortOf(String headerName, String raw) {
        return sanitize(headerName, raw)
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
     * <p><strong>The bracket contents must themselves be an IP literal.</strong> {@code []} and
     * {@code [not-an-ip]} yield {@link HostPort#EMPTY} rather than becoming a host, because the
     * brackets promise a literal and an invalid one would be composed into an invalid authority.
     * The check accepts an IPv4 literal as well, so {@code [10.0.0.5]} is honored even though
     * RFC 3986 reserves the bracketed form for IPv6. That laxity is deliberate and documented
     * rather than intended: it keeps this path identical to
     * {@link IpAddresses#parseChainEntry(String)}, which has always accepted the same shape, and
     * a syntactically valid literal is still required either way. Tighten both together or
     * neither — a one-sided change reintroduces the host-vs-chain asymmetry.</p>
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
            // The brackets promise an IP literal, so validate what is INSIDE them too — checking
            // only the trailer would let "[]" and "[not-an-ip]" through as a host and hand a
            // downstream consumer an invalid URL authority. This mirrors
            // IpAddresses.parseChainEntry, which parses its bracketed literal for the same reason.
            if (IpAddresses.parse(value.substring(1, close)) == null) {
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

    /**
     * Resolves the port under the same per-field rule that governs scheme, host, and client-IP: an
     * unresolvable {@code Forwarded} header is <em>not</em> treated as speaking about the port
     * merely because it could have carried one via a {@code host=example.com:8443} directive.
     *
     * <p>The port is dropped only when the header actually carried a {@code host} directive bearing
     * one — and that drop needs no guard here, because it arrives through {@code hostPortFallback}:
     * a {@code host} directive the header did carry makes the RFC side present, whose empty
     * resolution then drops the host-derived port through {@link #resolveHost}'s own port
     * reconciliation. That reconciliation is independent of the host one, so the host may still
     * stand. A header that broke before any {@code host} directive leaves an
     * {@code X-Forwarded-Port} standing.</p>
     *
     * <p>{@code hostPortFallback} is therefore the port the two {@code host[:port]} sources agreed
     * on, consulted only when no explicit port header is present at all.</p>
     *
     * <p><strong>An explicit port header is reconciled against RFC 7239 too.</strong> A
     * {@code Forwarded: host="h:9000"} directive states a port just as much as
     * {@code X-Forwarded-Port: 8443} does, so the two are compared through the same fail-closed
     * {@link #reconcileSources} path scheme, host, and client-IP use: they agree and the port stands,
     * they disagree and it is dropped with a warning. Returning the de-facto port unconditionally
     * bypassed the disagreement check for this one field, letting a forged {@code X-Forwarded-Port}
     * win over the {@code Forwarded} header — which is exactly the "prefer one source" behaviour the
     * rest of the resolver refuses. The RFC side contests only when its {@code host} directive
     * actually carried a port: a directive naming a bare host said nothing about the port, so an
     * explicit port header stands unopposed against it. "Carried a port" is decided structurally by
     * {@link #statesPort(String)}, so a directive whose port token is present but unparseable
     * ({@code host="h:bogus"}) contests too — its resolution is empty, and the disagreement path
     * drops the field rather than letting the de-facto port through on a technicality.</p>
     */
    private OptionalInt resolvePort(UnaryOperator<String> lookup, RfcHost rfcHost, boolean explicitPortPresent,
            OptionalInt hostPortFallback) {
        if (!explicitPortPresent) {
            return hostPortFallback;
        }
        if (!config.trustAll()) {
            return OptionalInt.empty();
        }
        DeFactoResolution<Integer> deFacto =
                reconcileDeFacto(lookup, "port", X_FORWARDED_PORT, X_PROXY_PORT, this::portOf);
        return reconcileSources("port", deFacto.headerName(),
                deFacto.stated(), deFacto.value(),
                rfcHost.statesPort(), rfcHost.port())
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    /**
     * Resolves a port header to its validated value, collapsing "absent / rejected / not a valid
     * port" into a single empty result so the cross-family comparison sees one shape rather than a
     * nested optional.
     */
    private Optional<Integer> portOf(String headerName, String raw) {
        return sanitize(headerName, raw)
                .map(ForwardedHeaderResolver::lastToken)
                .map(ForwardedHeaderResolver::parsePort)
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt);
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
        SourcedValue raw = firstPresent(lookup, X_PROXY_CONTEXT_PATH, X_FORWARDED_PREFIX);
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
        String trimmed = lastToken(raw.value().strip());
        if (ContextPaths.containsControlCharacter(trimmed)) {
            LOGGER.warn(ForwardedLogMessages.WARN.CONTEXT_PATH_CONTROL_CHARACTERS_REJECTED, sanitizeForLog(trimmed));
            return "";
        }
        if (ContextPaths.isProtocolRelativeOrBackslash(trimmed)) {
            LOGGER.warn(ForwardedLogMessages.WARN.CONTEXT_PATH_PROTOCOL_RELATIVE_REJECTED, sanitizeForLog(trimmed));
            return "";
        }
        Optional<String> sanitized = sanitize(raw.headerName(), trimmed);
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
        boolean rfcPresent = !rfcChain.isEmpty();

        Optional<String> fromXff = xffPresent
                ? sanitize(X_FORWARDED_FOR, xff).map(value -> List.of(value.split(","))).flatMap(this::walkChain)
                : Optional.empty();
        // An unresolvable header contributes nothing, so a chain it DID carry meets an empty
        // resolution and is dropped by the disagreement path rather than being walked and believed.
        Optional<String> fromRfc = !rfcPresent || forwarded.unresolvable()
                ? Optional.empty()
                : walkChain(rfcChain);

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
        // NOT collapse into the absent case: the header WAS sent, so it stays present-but-unresolvable.
        // But it carries no parseable directives, so it suppresses no field and contests nothing —
        // reconcileSources sees rfcPresent=false everywhere and simply defers to the de-facto sibling.
        Optional<String> sanitized = sanitize(FORWARDED, raw);
        if (sanitized.isEmpty()) {
            // Nothing parsed, so the header spoke about no field and suppresses none.
            return ForwardedResult.SANITIZATION_REJECTED;
        }
        // A grammar-violating forwarded-pair makes the header unbelievable, but the directives the
        // parser reached before it stopped are RETAINED — they are what scopes the drop to the
        // fields this header actually spoke about instead of erasing all of them.
        RfcForwardedParser.Parsed parsed = RfcForwardedParser.parse(sanitized.get());
        if (parsed.malformed()) {
            LOGGER.warn(ForwardedLogMessages.WARN.FORWARDED_DIRECTIVE_MALFORMED, sanitizeForLog(raw));
            return new ForwardedResult(true, true, parsed);
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
     * The RFC 7239 side's contribution for one field: the directive's resolution, or nothing when
     * the header is unresolvable.
     *
     * <p>The two halves of the per-field rule meet here. Presence is decided by the directive alone
     * (the caller passes {@code rfc != null}), so a header that never spoke about this field leaves
     * the de-facto value to stand. Belief is decided by the header as a whole: an unresolvable one
     * supplies nothing, so a field it DID speak about meets an empty RFC resolution and is dropped
     * by the ordinary disagreement path in {@link #reconcileSources}. Keeping the two separate is
     * what makes the failure per-field instead of total.</p>
     */
    private <T> Optional<T> rfcResolution(ForwardedResult forwarded, @Nullable String rfc,
            BiFunction<String, String, Optional<T>> resolver) {
        if (rfc == null || forwarded.unresolvable()) {
            return Optional.empty();
        }
        return resolver.apply(FORWARDED, rfc);
    }

    /**
     * Reconciles the two de-facto header families against each other, before the field ever reaches
     * the RFC 7239 comparison.
     *
     * <p>Both names are resolved <em>independently</em> rather than by returning whichever is
     * present first: a fixed order silently lets an attacker-supplied {@code X-Forwarded-Proto}
     * override the {@code X-ProxyScheme} a deployment's own ingress writes, because that name heads
     * the list. When both are present and resolve equal, that value stands. When they disagree the
     * field is <em>not</em> dropped — unlike the de-facto-versus-RFC-7239 stage, where a
     * disagreement means one of two sources that a single proxy populates must be forged, either
     * de-facto family may legitimately be the one this deployment writes. The tie is therefore
     * broken by {@link ForwardedResolverConfig#deFactoPrecedence()} and logged.</p>
     *
     * <p>The winning header <em>name</em> travels with the value so the downstream disagreement
     * record still names the header the value actually came from.</p>
     *
     * @param field       the field name, for the disagreement log record
     * @param xForwardedName the {@code X-Forwarded-*} header name for this field
     * @param xProxyName     the {@code X-Proxy*} header name for this field
     * @param resolver    resolves a (header name, raw value) pair to the field's validated value
     * @return the reconciled resolution, carrying the header name it came from and whether either
     *         family was present at all
     */
    private <T> DeFactoResolution<T> reconcileDeFacto(UnaryOperator<String> lookup, String field,
            String xForwardedName, String xProxyName, BiFunction<String, String, Optional<T>> resolver) {
        return reconcileStatedByPresence(
                resolveDeFactoSources(lookup, xForwardedName, xProxyName, resolver), field, Optional::of);
    }

    /**
     * Reads both de-facto header names and resolves each independently, without yet comparing them.
     *
     * <p>Separating the read from the comparison is what lets a multi-field value such as
     * {@code host[:port]} be reconciled field by field: the two families are resolved once, and each
     * field they carry is then reconciled on its own terms (see {@link #reconcileStatedByPresence}
     * and {@link #reconcileStatedByValue}).</p>
     */
    private <T> DeFactoSources<T> resolveDeFactoSources(UnaryOperator<String> lookup,
            String xForwardedName, String xProxyName, BiFunction<String, String, Optional<T>> resolver) {
        String xForwardedRaw = lookup.apply(xForwardedName);
        String xProxyRaw = lookup.apply(xProxyName);
        boolean xForwardedPresent = isPresent(xForwardedRaw);
        boolean xProxyPresent = isPresent(xProxyRaw);
        return new DeFactoSources<>(
                xForwardedName, xForwardedPresent, xForwardedPresent ? xForwardedRaw : "",
                xForwardedPresent ? resolver.apply(xForwardedName, xForwardedRaw) : Optional.empty(),
                xProxyName, xProxyPresent, xProxyPresent ? xProxyRaw : "",
                xProxyPresent ? resolver.apply(xProxyName, xProxyRaw) : Optional.empty());
    }

    /**
     * Reconciles one field where <em>presence of the header</em> is the statement: a present de-facto
     * header speaks about the field even when it resolves to nothing valid, so a present-but-invalid
     * value still contests and never falls through to its sibling family.
     *
     * <p>This is the rule for a field that is the whole header value (scheme, port) and for the host
     * a {@code host[:port]} token always carries.</p>
     */
    private <T, F> DeFactoResolution<F> reconcileStatedByPresence(DeFactoSources<T> sources, String field,
            Function<T, Optional<F>> extractor) {
        return reconcileDeFactoField(field, sources,
                new FieldContribution<>(sources.xForwardedName(), sources.xForwardedPresent(),
                        sources.fromXForwarded().flatMap(extractor)),
                new FieldContribution<>(sources.xProxyName(), sources.xProxyPresent(),
                        sources.fromXProxy().flatMap(extractor)));
    }

    /**
     * Reconciles one field where <em>a port token in the raw value</em> is the statement: a family
     * whose {@code host[:port]} token carried no port token says nothing about the port and does not
     * contest, while one that carried a port token states a port even if that token fails to parse.
     *
     * <p>This is the rule for the optional port inside a {@code host[:port]} token, and it is the
     * de-facto <em>mirror</em> of {@link RfcHost#statesPort()}. Both sides answer it through the one
     * {@link #statesPort(String)} predicate, so they cannot drift apart about where a port token
     * is.</p>
     *
     * <p>Treating the absence of a port as a contested empty value made {@code X-Forwarded-Host: h}
     * versus {@code X-ProxyHost: h:9443} look like a conflict and let the precedence tie-break
     * discard a port no source contradicted; that half of the rule is unchanged, because a token
     * bearing no port token still states nothing. Deriving the statement from the <em>resolved</em>
     * port value instead collapsed the other half: {@code X-Forwarded-Host: h}, which stated no
     * port, and {@code X-Forwarded-Host: h:bogus}, which stated one that failed digit parsing, both
     * resolve to an empty port, so the de-facto side reported that it stated no port,
     * {@link #reconcileSources} deferred to the RFC directive, and a conflicting
     * {@code Forwarded: host="h:9999"} was honored <em>unopposed</em> and unlogged — on precisely
     * the input a well-formed conflict would have dropped. That was the same defect
     * {@link #statesPort(String)} had already removed from the RFC side, left standing on this
     * one.</p>
     *
     * <p>The predicate runs against the <em>raw</em> header value, exactly as {@link #rfcHost} runs
     * it against the raw directive: whether a token carried a port is structural and must survive
     * the value turning out to be unbelievable. Re-sanitizing here would also emit a second
     * rejection warning for a value already reported once.</p>
     */
    private <T, F> DeFactoResolution<F> reconcileStatedByPortToken(DeFactoSources<T> sources, String field,
            Function<T, Optional<F>> extractor) {
        return reconcileDeFactoField(field, sources,
                new FieldContribution<>(sources.xForwardedName(), statesPort(lastToken(sources.xForwardedRaw())),
                        sources.fromXForwarded().flatMap(extractor)),
                new FieldContribution<>(sources.xProxyName(), statesPort(lastToken(sources.xProxyRaw())),
                        sources.fromXProxy().flatMap(extractor)));
    }

    /**
     * The shared comparison behind both statement rules: agree and the value stands, genuinely
     * disagree and {@link ForwardedResolverConfig#deFactoPrecedence()} breaks the tie, and a field
     * only one family states is taken from that family unopposed.
     *
     * <p>The returned {@code stated} flag is this <em>field</em>'s statement, not the family's mere
     * presence, because that is what the RFC 7239 stage downstream compares against: it decides
     * whether that stage reconciles the two sides or simply defers to the RFC directive. Scoping it
     * to the family made a de-facto header that was sent but said nothing about this field contest an
     * RFC directive that did — dropping the field with a "sources disagree" warning over a claim
     * only one side ever made. The two statement rules above are what keep this honest per field: for
     * a field the mere presence of the header states (scheme, port, and the host a
     * {@code host[:port]} token always carries) a present family still states it, so a
     * present-but-invalid value still contests; only for the optional port inside a
     * {@code host[:port]} token do presence and statement come apart, and there the statement is
     * read structurally off the raw token ({@link #reconcileStatedByPortToken}) through the very
     * predicate {@link RfcHost#statesPort()} uses, so the two mirror sides answer it alike.</p>
     */
    private <T, F> DeFactoResolution<F> reconcileDeFactoField(String field, DeFactoSources<T> sources,
            FieldContribution<F> xForwarded, FieldContribution<F> xProxy) {
        boolean stated = xForwarded.stated() || xProxy.stated();
        if (!xForwarded.stated() && !xProxy.stated()) {
            return new DeFactoResolution<>(sources.attribution(), stated, Optional.empty());
        }
        if (!xProxy.stated()) {
            return new DeFactoResolution<>(xForwarded.headerName(), stated, xForwarded.value());
        }
        if (!xForwarded.stated()) {
            return new DeFactoResolution<>(xProxy.headerName(), stated, xProxy.value());
        }
        if (xForwarded.value().equals(xProxy.value())) {
            return new DeFactoResolution<>(xForwarded.headerName(), stated, xForwarded.value());
        }
        ForwardedResolverConfig.DeFactoFamily winner = config.deFactoPrecedence();
        LOGGER.warn(ForwardedLogMessages.WARN.DE_FACTO_FAMILIES_DISAGREE,
                field, xForwarded.headerName(), describeForLog(xForwarded.value()),
                xProxy.headerName(), describeForLog(xProxy.value()), winner);
        return winner == ForwardedResolverConfig.DeFactoFamily.X_FORWARDED
                ? new DeFactoResolution<>(xForwarded.headerName(), stated, xForwarded.value())
                : new DeFactoResolution<>(xProxy.headerName(), stated, xProxy.value());
    }

    /**
     * Both de-facto families read and resolved, before any field of them has been compared.
     *
     * @param xForwardedName    the {@code X-Forwarded-*} header name for this field family
     * @param xForwardedPresent whether that header was sent at all
     * @param xForwardedRaw     its raw value as sent, or {@code ""} when the header was absent or
     *                          blank. Retained because a <em>structural</em> statement rule
     *                          ({@link #reconcileStatedByPortToken}) must read what the token
     *                          literally carried, which the resolution below has already discarded;
     *                          the empty stand-in is what keeps that rule total without a nullable
     *                          component
     * @param fromXForwarded    its independent resolution ({@code empty} when absent or invalid)
     * @param xProxyName        the {@code X-Proxy*} header name for this field family
     * @param xProxyPresent     whether that header was sent at all
     * @param xProxyRaw         its raw value as sent, or {@code ""} when absent or blank
     * @param fromXProxy        its independent resolution ({@code empty} when absent or invalid)
     */
    private record DeFactoSources<T>(String xForwardedName, boolean xForwardedPresent, String xForwardedRaw,
    Optional<T> fromXForwarded,
    String xProxyName, boolean xProxyPresent, String xProxyRaw, Optional<T> fromXProxy) {

        /**
         * The header name a diagnostic should be attributed to when neither family stated the field:
         * the one that was actually sent, so a log line never names a header the request never
         * carried.
         */
        private String attribution() {
            return !xForwardedPresent && xProxyPresent ? xProxyName : xForwardedName;
        }
    }

    /**
     * One de-facto family's contribution to a single field.
     *
     * @param headerName the header name the contribution came from
     * @param stated     whether this family says anything about the field at all — the two statement
     *                   rules differ only in how they answer this
     * @param value      the stated value ({@code empty} when the family stated the field but resolved
     *                   to nothing valid)
     */
    private record FieldContribution<F>(String headerName, boolean stated, Optional<F> value) {
    }

    /**
     * A field resolved from the de-facto families, carrying the header name that produced the
     * winning value so the downstream RFC 7239 disagreement record stays honest about its source.
     *
     * @param headerName the de-facto header name the value came from
     * @param stated     whether either de-facto header said anything about <em>this field</em> —
     *                   distinct from stating it but resolving to nothing valid, which is a real
     *                   disagreement, and distinct from the family merely having been sent, which
     *                   says nothing about a field the header's own token never carried
     * @param value      the reconciled resolution
     */
    private record DeFactoResolution<T>(String headerName, boolean stated, Optional<T> value) {
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
     * @param field         the field name, for the disagreement log record
     * @param deFactoHeader the de-facto header name the value was actually read from (not the head
     *                      of the precedence list), for the disagreement log record
     * @param deFactoStated whether the de-facto source said anything about <em>this field</em>
     *                      (distinct from stating it but resolving to nothing valid, which is a real
     *                      disagreement, and from the family merely having been sent, which contests
     *                      nothing about a field it never spoke of)
     * @param fromDeFacto   the de-facto source's independent resolution
     * @param rfcPresent    whether the RFC 7239 source spoke about this field at all
     * @param fromRfc       the RFC 7239 source's independent resolution
     * @return the agreed resolution, or empty when the sources disagree
     */
    private <T> Optional<T> reconcileSources(String field, String deFactoHeader,
            boolean deFactoStated, Optional<T> fromDeFacto,
            boolean rfcPresent, Optional<T> fromRfc) {
        if (!deFactoStated) {
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

    /**
     * Selects the first present header value in precedence order, reporting <em>which</em> header
     * name produced it alongside the value.
     *
     * <p>Carrying the matched name is what keeps the diagnostics honest: a rejection of an
     * {@code X-Forwarded-Prefix} value must not be reported against {@code X-ProxyContextPath}
     * merely because that name heads the precedence list.</p>
     *
     * <p>Ordered precedence survives only for the context path, whose two names are not a de-facto
     * <em>family pair</em> the way {@code X-Forwarded-Proto} / {@code X-ProxyScheme} are: scheme,
     * host, and port resolve both families independently and reconcile them through
     * {@link #reconcileDeFacto}, so a first-present rule would reintroduce exactly the silent
     * override that reconciliation removes.</p>
     *
     * @return the matched header name and its value, or {@code null} when none of the names is
     *         present
     */
    private static @Nullable SourcedValue firstPresent(UnaryOperator<String> lookup, String... headerNames) {
        for (String name : headerNames) {
            String value = lookup.apply(name);
            if (isPresent(value)) {
                return new SourcedValue(name, value);
            }
        }
        return null;
    }

    /**
     * A header value together with the header name that actually produced it, so a downstream
     * diagnostic names the matched header rather than the head of the precedence list.
     *
     * @param headerName the header name the value was read from
     * @param value      the non-blank raw header value
     */
    private record SourcedValue(String headerName, String value) {
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
     *   <li>Unresolvable — the header WAS sent but its raw value failed sanitization (over-length,
     *       NUL, other control characters including CR/LF) or violated the RFC 7239 grammar with a
     *       malformed {@code forwarded-pair}. It supplies no value to any field
     *       ({@link #rfcResolution} returns empty), so each field it actually carried a directive
     *       for meets an empty RFC resolution and is dropped (fail closed) — and each field it did
     *       NOT carry a directive for is untouched. Scope is per field; the header's directives
     *       still say which fields those are, because the parser retains what it read before it
     *       stopped.</li>
     *   <li>Present and sanitized — the parsed directives decide per field. A well-formed value that
     *       simply carries no {@code proto} (say) leaves that one field's RFC source with nothing to
     *       contribute, which is an ordinary fallback rather than a disagreement.</li>
     * </ol>
     *
     * <p>Presence and belief are therefore separate axes, and only their combination is per-field:
     * {@code parsed} answers "did this header speak about the field", {@code unresolvable} answers
     * "may anything it said be believed". A sanitization failure yields no directives at all
     * ({@link #NO_DIRECTIVES}) and so drops nothing beyond what a de-facto source can stand on.</p>
     *
     * @param present      whether a non-blank {@code Forwarded} header was sent at all
     * @param unresolvable whether that header failed sanitization outright or parsed as malformed
     * @param parsed       the directives parsed before the parse stopped; empty when the raw value
     *                     never sanitized
     */
    private record ForwardedResult(boolean present, boolean unresolvable, RfcForwardedParser.Parsed parsed) {

        private static final RfcForwardedParser.Parsed NO_DIRECTIVES =
                new RfcForwardedParser.Parsed(Optional.empty(), Optional.empty(), List.of(), false);

        private static final ForwardedResult ABSENT = new ForwardedResult(false, false, NO_DIRECTIVES);

        /** A header whose raw value failed sanitization: present, unresolvable, and directive-less. */
        private static final ForwardedResult SANITIZATION_REJECTED = new ForwardedResult(true, true, NO_DIRECTIVES);
    }

    /**
     * Host token plus an optional port extracted from a {@code host:port} value.
     */
    private record HostPort(Optional<String> host, OptionalInt port) {
        private static final HostPort EMPTY = new HostPort(Optional.empty(), OptionalInt.empty());

        /**
         * The port as an {@link Optional}, so {@link #reconcileSources} can compare it on the same
         * shape it compares every other field on. {@link OptionalInt} is not an {@link Optional} and
         * carries none of its combinators, so without this the port could not be reconciled
         * independently of the host.
         */
        private Optional<Integer> portValue() {
            return port.isPresent() ? Optional.of(port.getAsInt()) : Optional.empty();
        }

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
