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

/**
 * Canonical forwarded/proxy header names, shared between the resolver (parsing) and
 * {@link ResolvedForwarding} (serialization) so the two sides never drift.
 */
final class ForwardedHeaderNames {

    // de-facto X-Forwarded-* family
    static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    static final String X_FORWARDED_PORT = "X-Forwarded-Port";
    static final String X_FORWARDED_PREFIX = "X-Forwarded-Prefix";
    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    // Apache NiFi-proprietary family
    static final String X_PROXY_SCHEME = "X-ProxyScheme";
    static final String X_PROXY_HOST = "X-ProxyHost";
    static final String X_PROXY_PORT = "X-ProxyPort";
    static final String X_PROXY_CONTEXT_PATH = "X-ProxyContextPath";

    // RFC 7239
    static final String FORWARDED = "Forwarded";

    private ForwardedHeaderNames() {
    }
}
