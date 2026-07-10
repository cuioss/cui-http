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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Immutable, sanitized result of resolving the reverse-proxy / forwarded-header family.
 *
 * <p>Each field is already normalized and injection-guarded by
 * {@link ForwardedHeaderResolver}, and only carries a value when the configured trust model
 * honored it. Absent or un-honored fields resolve to {@link Optional#empty()} (or an empty
 * {@code contextPath}).</p>
 *
 * <h3>Serialization</h3>
 * <p>The {@link #toXForwardedHeaders()} and {@link #toForwardedHeader()} methods emit the
 * resolved state back as proxy headers, so a resolved request can be forwarded upstream and
 * round-trip-tested. <strong>Asymmetry:</strong> RFC 7239 {@code Forwarded} has no prefix
 * directive, so {@code contextPath} is expressible only through {@code X-Forwarded-Prefix}
 * (via {@link #toXForwardedHeaders()}), never through {@link #toForwardedHeader()}.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * ResolvedForwarding forwarding = resolver.resolve(request::getHeader);
 * String scheme = forwarding.scheme().orElse("http");
 * int port = forwarding.port().orElse(scheme.equals("https") ? 443 : 80);
 * String prefix = forwarding.contextPath(); // "" when none / not honored
 * forwarding.clientIp().ifPresent(ip -> log.info("client %s", ip));
 * }</pre>
 *
 * <p>This record is immutable and thread-safe.</p>
 *
 * @param scheme      the resolved request scheme ({@code "http"} or {@code "https"}), empty when
 *                    absent or not honored
 * @param host        the resolved host without a port, empty when absent or not honored
 * @param port        the resolved port in the range {@code 1..65535}, empty when absent or not honored
 * @param contextPath the normalized context-path prefix (exactly one leading slash, no trailing
 *                    slash), or the empty string when none is present or honored
 * @param clientIp    the resolved originating client IP in canonical form, empty when absent or
 *                    not honored
 *
 * @since 1.0
 */
public record ResolvedForwarding(
Optional<String> scheme,
Optional<String> host,
OptionalInt port,
String contextPath,
Optional<String> clientIp
) {

    private static final ResolvedForwarding EMPTY =
            new ResolvedForwarding(Optional.empty(), Optional.empty(), OptionalInt.empty(), "", Optional.empty());

    /**
     * Returns the empty result: no scheme, host, port, or client IP, and an empty context-path.
     * This is what a secure-by-default resolver returns for an un-proxied or fully un-honored request.
     *
     * @return the shared empty {@link ResolvedForwarding} instance
     */
    public static ResolvedForwarding empty() {
        return EMPTY;
    }

    /**
     * Serializes this result to the de-facto {@code X-Forwarded-*} header family.
     *
     * <p>Only present fields are emitted; absent fields are omitted from the map. A non-empty
     * {@code contextPath} is emitted as {@code X-Forwarded-Prefix}.</p>
     *
     * @return an insertion-ordered, modifiable map of header name to value (empty when this
     *         result carries no fields)
     */
    public Map<String, String> toXForwardedHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        scheme.ifPresent(value -> headers.put(ForwardedHeaderNames.X_FORWARDED_PROTO, value));
        host.ifPresent(value -> headers.put(ForwardedHeaderNames.X_FORWARDED_HOST, value));
        port.ifPresent(value -> headers.put(ForwardedHeaderNames.X_FORWARDED_PORT, Integer.toString(value)));
        if (!contextPath.isEmpty()) {
            headers.put(ForwardedHeaderNames.X_FORWARDED_PREFIX, contextPath);
        }
        clientIp.ifPresent(value -> headers.put(ForwardedHeaderNames.X_FORWARDED_FOR, value));
        return headers;
    }

    /**
     * Serializes this result to a single RFC 7239 {@code Forwarded} header value.
     *
     * <p>Emits the {@code for}, {@code host}, and {@code proto} directives for whichever fields
     * are present. When a {@code port} accompanies a {@code host}, it is folded into the
     * {@code host} directive ({@code host="name:port"}). IPv6 client addresses are bracketed and
     * the value quoted per RFC 7239; other values are quoted only when they are not a valid
     * RFC 7239 token.</p>
     *
     * <p><strong>Note:</strong> {@code contextPath} is intentionally absent — RFC 7239 defines no
     * prefix/context-path directive. Use {@link #toXForwardedHeaders()} to preserve it.</p>
     *
     * @return the {@code Forwarded} value, or {@link Optional#empty()} when no
     *         {@code Forwarded}-expressible field ({@code proto}/{@code host}/{@code for}) is present
     */
    public Optional<String> toForwardedHeader() {
        StringBuilder builder = new StringBuilder();
        clientIp.ifPresent(ip -> append(builder, "for", forwardedForValue(ip)));
        host.ifPresent(name -> append(builder, "host", port.isPresent() ? name + ":" + port.getAsInt() : name));
        scheme.ifPresent(value -> append(builder, "proto", value));
        return builder.isEmpty() ? Optional.empty() : Optional.of(builder.toString());
    }

    private static void append(StringBuilder builder, String directive, String value) {
        if (!builder.isEmpty()) {
            builder.append(';');
        }
        builder.append(directive).append('=').append(quoteIfNeeded(value));
    }

    /**
     * Wraps the IPv6 form of a client address in brackets ({@code [2001:db8::1]}); IPv4
     * addresses pass through unchanged. Bracketing (and the subsequent quoting) satisfies the
     * RFC 7239 {@code for} node-identifier syntax.
     */
    private static String forwardedForValue(String ip) {
        return ip.indexOf(':') >= 0 ? "[" + ip + "]" : ip;
    }

    /**
     * Returns the value unchanged when it is a valid RFC 7239 token, otherwise wraps it in a
     * double-quoted string (escaping backslashes and quotes).
     */
    private static String quoteIfNeeded(String value) {
        if (isToken(value)) {
            return value;
        }
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static boolean isToken(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isTokenChar(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * RFC 7230 {@code tchar}: {@code ALPHA / DIGIT} plus a fixed punctuation set. Values outside
     * this set (colon-bearing host:port, bracketed IPv6) fall back to a quoted-string.
     */
    private static boolean isTokenChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }
}
