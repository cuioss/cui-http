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
/**
 * Reusable reverse-proxy / forwarded-header resolution with built-in sanitization.
 *
 * <p>Reverse proxies communicate the original request scheme, host, port, context-path
 * prefix, and client IP through a family of overlapping headers that sit at different
 * points on the standards spectrum:</p>
 * <table border="1">
 *   <caption>Forwarded header family</caption>
 *   <tr><th>Header(s)</th><th>Status</th></tr>
 *   <tr><td>{@code X-Forwarded-For / -Proto / -Host / -Port}</td><td>de-facto convention</td></tr>
 *   <tr><td>{@code X-Forwarded-Prefix}</td><td>de-facto (Spring Cloud Gateway, Traefik, …)</td></tr>
 *   <tr><td>{@code Forwarded} (RFC 7239)</td><td>IETF standard — but no prefix directive</td></tr>
 *   <tr><td>{@code X-ProxyScheme / -Host / -Port / -ContextPath}</td><td>Apache NiFi-proprietary</td></tr>
 * </table>
 *
 * <p>This package folds the three otherwise-duplicated concerns into one component:
 * <strong>precedence</strong> across the overlapping headers, <strong>normalization</strong>
 * (context-path slashes, default ports), and <strong>injection hardening</strong> (CR/LF,
 * protocol-relative {@code //host}, backslashes) — the last of which reuses the existing
 * {@link de.cuioss.http.security} validation pipelines rather than hand-rolling guards.</p>
 *
 * <h3>Components</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.forwarded.ForwardedHeaderResolver} - resolves the header family
 *       into a single sanitized result</li>
 *   <li>{@link de.cuioss.http.forwarded.ResolvedForwarding} - the immutable result, plus
 *       serialization back to proxy headers</li>
 *   <li>{@link de.cuioss.http.forwarded.ForwardedResolverConfig} - trust model and precedence
 *       configuration</li>
 * </ul>
 *
 * <h3>Secure by default</h3>
 * <p>The resolver is transport-agnostic (it consumes a {@link java.util.function.Function
 * Function&lt;String,String&gt;} header accessor, so no servlet/Jetty type leaks in) and
 * secure-by-default: with no allowlist and no explicit {@code trustAll} opt-in, client-supplied
 * forwarded values are ignored (never trusted), only logged. See
 * {@link de.cuioss.http.forwarded.ForwardedResolverConfig} for the trust model.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * ForwardedResolverConfig config = ForwardedResolverConfig.builder()
 *     .trustAll(true)                       // deployment sits fully behind a trusted proxy
 *     .trustedProxies(Set.of("10.0.0.0/8")) // for X-Forwarded-For client-IP resolution
 *     .build();
 * ForwardedHeaderResolver resolver =
 *     new ForwardedHeaderResolver(config, new SecurityEventCounter());
 *
 * ResolvedForwarding forwarding = resolver.resolve(request::getHeader);
 * String scheme = forwarding.scheme().orElse("http");
 * String prefix = forwarding.contextPath(); // "" when none / not honored
 * }</pre>
 *
 * <h3>Package Nullability</h3>
 * <p>This package follows strict nullability conventions using JSpecify annotations:
 * all parameters and return values are non-null by default; nullable ones are explicitly
 * annotated with {@code @Nullable}.</p>
 *
 * @since 1.0
 * @see de.cuioss.http.security.pipeline.PipelineFactory
 */
@NullMarked
package de.cuioss.http.forwarded;

import org.jspecify.annotations.NullMarked;
