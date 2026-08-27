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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * A delegating {@link X509ExtendedTrustManager} that skips TLS
 * hostname/endpoint-identification checks and nothing else.
 *
 * <h2>What is relaxed - and what is not</h2>
 * <p>
 * The <strong>only</strong> check this wrapper suppresses is the match between the peer
 * certificate's identity (subject alternative names / common name) and the host the connection
 * was opened against. Every other part of the peer verification is performed unchanged by the
 * wrapped delegate:
 * <ul>
 *   <li>certificate-chain trust - the chain must still terminate in a trust anchor the delegate
 *       accepts; a certificate signed by an unknown CA is still rejected</li>
 *   <li>validity period - expired and not-yet-valid certificates are still rejected</li>
 *   <li>revocation posture - whatever revocation checking the delegate is configured for still
 *       applies</li>
 *   <li>algorithm constraints - the JDK's disabled-algorithm and key-size policies still apply</li>
 * </ul>
 *
 * <h2>Mechanism</h2>
 * <p>
 * The relaxation is not a re-implementation of certificate validation; it is the documented
 * {@link X509ExtendedTrustManager} contract. The endpoint-identification algorithm that triggers
 * hostname matching is carried by the {@link SSLEngine} / {@link Socket} handed to the extended
 * {@code checkServerTrusted} / {@code checkClientTrusted} overloads. Per that contract, when the
 * engine or socket argument is {@code null} the trust manager performs the full chain validation
 * but has no peer-identity context and therefore performs no identity matching. This class
 * consequently forwards the extended overloads to the delegate with an explicit {@code null}
 * engine/socket, and forwards the plain two-argument {@link javax.net.ssl.X509TrustManager} forms
 * and {@link #getAcceptedIssuers()} verbatim - those never perform identity checks to begin with.
 *
 * <h2>Usage</h2>
 * <p>
 * This class is deliberately package-private and strictly opt-in: it is only reachable through
 * {@link SecureSSLContextProvider#createHostnameRelaxedSSLContext()}, which in turn is only
 * selected by an explicit {@code HttpHandlerBuilder.verifyHostname(false)}. Relaxing hostname
 * verification removes the protection against an attacker presenting a valid certificate issued
 * for a <em>different</em> host, so it must never be enabled by default.
 *
 * @author Oliver Wolff
 * @since 1.3
 * @see SecureSSLContextProvider#createHostnameRelaxedSSLContext()
 */
final class HostnameVerificationRelaxingTrustManager extends X509ExtendedTrustManager {

    private final X509ExtendedTrustManager delegate;

    /**
     * Creates a wrapper around the given trust manager.
     *
     * @param delegate the trust manager performing the actual chain validation, must not be null
     * @throws NullPointerException if {@code delegate} is null
     */
    HostnameVerificationRelaxingTrustManager(X509ExtendedTrustManager delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * Wraps every {@link X509ExtendedTrustManager} element of the given array in a
     * {@link HostnameVerificationRelaxingTrustManager}, passing any other element through
     * unchanged.
     * <p>
     * Elements that are not {@link X509ExtendedTrustManager} instances cannot carry the
     * endpoint-identification context in the first place, so there is nothing to relax for them.
     *
     * @param delegates the trust managers to wrap, must not be null
     * @return a new array of the same length holding the wrapped and pass-through elements
     * @throws NullPointerException if {@code delegates} is null
     */
    static TrustManager[] relaxHostnameVerification(TrustManager[] delegates) {
        Objects.requireNonNull(delegates, "delegates must not be null");
        TrustManager[] relaxed = new TrustManager[delegates.length];
        for (int i = 0; i < delegates.length; i++) {
            TrustManager candidate = delegates[i];
            relaxed[i] = candidate instanceof X509ExtendedTrustManager extended
                    ? new HostnameVerificationRelaxingTrustManager(extended)
                    : candidate;
        }
        return relaxed;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to the delegate with a {@code null} {@link Socket}, which suppresses
     * hostname/endpoint-identification matching while keeping chain validation intact.
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        delegate.checkClientTrusted(chain, authType, (Socket) null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to the delegate with a {@code null} {@link SSLEngine}, which suppresses
     * hostname/endpoint-identification matching while keeping chain validation intact.
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        delegate.checkClientTrusted(chain, authType, (SSLEngine) null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to the delegate with a {@code null} {@link Socket}, which suppresses
     * hostname/endpoint-identification matching while keeping chain validation intact.
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        delegate.checkServerTrusted(chain, authType, (Socket) null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to the delegate with a {@code null} {@link SSLEngine}, which suppresses
     * hostname/endpoint-identification matching while keeping chain validation intact.
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        delegate.checkServerTrusted(chain, authType, (SSLEngine) null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegated verbatim - the two-argument form never performs identity matching.
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.checkClientTrusted(chain, authType);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegated verbatim - the two-argument form never performs identity matching.
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.checkServerTrusted(chain, authType);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegated verbatim - the accepted issuers are unaffected by hostname verification.
     */
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }
}
