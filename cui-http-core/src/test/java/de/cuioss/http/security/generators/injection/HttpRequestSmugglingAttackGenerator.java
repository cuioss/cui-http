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
package de.cuioss.http.security.generators.injection;

import de.cuioss.http.security.generators.header.HttpHeaderInjectionAttackGenerator;
import de.cuioss.http.security.generators.header.HttpHeaderInjectionAttackGenerator.Surface;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

import java.util.Arrays;

/**
 * T16: HTTP Request Smuggling Attack Generator
 *
 * <p>
 * This generator creates comprehensive HTTP request smuggling attack patterns that exploit
 * discrepancies in how front-end and back-end servers parse HTTP requests. HTTP request
 * smuggling is a critical vulnerability that can lead to request hijacking, cache poisoning,
 * authentication bypass, and access to other users' requests by sending ambiguous HTTP
 * requests that are interpreted differently by different servers in the chain.
 * </p>
 *
 * <h3>Component-shaped output</h3>
 * <p>
 * Every value is the content of exactly one HTTP component - never an absolute URL. Each family
 * is shaped for the surface that receives it, and {@link #surfaceOf(String)} reports which one:
 * </p>
 * <ul>
 *   <li><strong>Header-shaped</strong> families inject a framing or connection header block
 *       behind a header value, so their line breaks are raw {@code CR LF}. Validate with
 *       {@code HTTPHeaderValidationPipeline} for {@code HEADER_VALUE}. {@link HeaderShaped} emits
 *       these families only.</li>
 *   <li><strong>Query-shaped</strong> families smuggle the second request through a query
 *       parameter value, so their line breaks - and every other character outside the RFC 3986
 *       query character set - are percent-encoded. Validate with
 *       {@code URLParameterValidationPipeline}. {@link QueryShaped} emits these families
 *       only.</li>
 * </ul>
 *
 * <h3>Attack Types Generated</h3>
 * <ul>
 *   <li>CL.TE Smuggling - Content-Length vs Transfer-Encoding conflicts</li>
 *   <li>TE.CL Smuggling - Transfer-Encoding vs Content-Length conflicts</li>
 *   <li>TE.TE Smuggling - Dual Transfer-Encoding header confusion</li>
 *   <li>CL.CL Smuggling - Duplicate Content-Length header attacks</li>
 *   <li>HTTP/2 Downgrade Smuggling - Protocol version downgrade attacks</li>
 *   <li>Pipeline Poisoning - Request pipeline contamination</li>
 *   <li>Cache Deception - Cache poisoning via smuggled requests</li>
 *   <li>Authentication Bypass - Session hijacking through smuggling</li>
 *   <li>Header Manipulation - Request header modification attacks</li>
 *   <li>Method Override Smuggling - HTTP method manipulation</li>
 *   <li>URL Rewriting Attacks - Request URL modification via smuggling</li>
 *   <li>Request Hijacking - Capturing other users' requests</li>
 *   <li>Response Queue Poisoning - Response desynchronization attacks</li>
 *   <li>WebSocket Upgrade Smuggling - Protocol upgrade manipulation</li>
 *   <li>Chunked Encoding Bypass - Transfer-encoding chunk manipulation</li>
 * </ul>
 *
 * <h3>Security Standards Compliance</h3>
 * <ul>
 *   <li>OWASP Top 10: A03:2021 – Injection</li>
 *   <li>CWE-444: Inconsistent Interpretation of HTTP Requests ('HTTP Request Smuggling')</li>
 *   <li>CWE-436: Interpretation Conflict</li>
 *   <li>RFC 7230: HTTP/1.1 Message Syntax and Routing</li>
 *   <li>RFC 9112: HTTP/1.1 Specification</li>
 * </ul>
 *
 * @author Generated for HTTP Security Validation (T16)
 * @version 1.0.0
 */
public class HttpRequestSmugglingAttackGenerator implements TypedGenerator<String> {

    /**
     * The generator restricted to the nine header-shaped families: framing-header conflicts
     * (CL.TE, TE.CL, TE.TE, CL.CL), HTTP/2 downgrade, pipeline poisoning, cache deception,
     * WebSocket upgrade and chunked-encoding bypass. Every value is a header value carrying raw
     * line breaks.
     */
    public static final class HeaderShaped extends HttpRequestSmugglingAttackGenerator {

        /** Creates a generator that emits header-value shaped smuggling payloads only. */
        public HeaderShaped() {
            super(HEADER_SHAPED_FAMILIES);
        }
    }

    /**
     * The generator restricted to the six query-shaped families: authentication bypass, header
     * manipulation, method override, URL rewriting, request hijacking and response queue
     * poisoning. Every value is a parameter value whose line breaks are percent-encoded.
     */
    public static final class QueryShaped extends HttpRequestSmugglingAttackGenerator {

        /** Creates a generator that emits parameter-value shaped smuggling payloads only. */
        public QueryShaped() {
            super(QUERY_SHAPED_FAMILIES);
        }
    }

    private static final int FAMILY_COUNT = 15;
    private static final int[] ALL_FAMILIES = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    private static final int[] HEADER_SHAPED_FAMILIES = {0, 1, 2, 3, 4, 5, 6, 13, 14};
    private static final int[] QUERY_SHAPED_FAMILIES = {7, 8, 9, 10, 11, 12};

    private final int[] families;

    /** Creates a generator that emits all fifteen families, each shaped for its own surface. */
    public HttpRequestSmugglingAttackGenerator() {
        this(ALL_FAMILIES);
    }

    private HttpRequestSmugglingAttackGenerator(int[] families) {
        this.families = families;
    }

    /**
     * Reports the surface a generated payload is shaped for. A header-shaped payload carries its
     * line breaks raw; a query-shaped payload carries them percent-encoded and therefore contains
     * no raw line break at all.
     *
     * @param payload a value produced by this generator, must not be null
     * @return {@link Surface#HEADER_VALUE} when {@code payload} carries a raw line break,
     *         {@link Surface#PARAMETER_VALUE} otherwise
     */
    public static Surface surfaceOf(String payload) {
        return payload.indexOf('\r') >= 0 || payload.indexOf('\n') >= 0
                ? Surface.HEADER_VALUE
                : Surface.PARAMETER_VALUE;
    }

    @Override
    public String next() {
        int family = families[randomSelection(families.length)];
        String headerBlock = switch (family) {
            case 0 -> createClTeSmuggling();
            case 1 -> createTeClSmuggling();
            case 2 -> createTeTeSmuggling();
            case 3 -> createClClSmuggling();
            case 4 -> createHttp2DowngradeSmuggling();
            case 5 -> createPipelinePoisoning();
            case 6 -> createCacheDeception();
            case 7 -> createAuthenticationBypass();
            case 8 -> createHeaderManipulation();
            case 9 -> createMethodOverrideSmuggling();
            case 10 -> createUrlRewritingAttack();
            case 11 -> createRequestHijacking();
            case 12 -> createResponseQueuePoisoning();
            case 13 -> createWebSocketUpgradeSmuggling();
            case 14 -> createChunkedEncodingBypass();
            default -> throw new IllegalStateException("No smuggling family " + family + " of " + FAMILY_COUNT);
        };
        return isQueryShaped(family) ? HttpHeaderInjectionAttackGenerator.asParameterValue(headerBlock) : headerBlock;
    }

    private static boolean isQueryShaped(int family) {
        return Arrays.stream(QUERY_SHAPED_FAMILIES).anyMatch(queryShaped -> queryShaped == family);
    }

    private String createClTeSmuggling() {
        String[] clTeAttacks = {
                "test\r\nContent-Length: 6\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\nG",
                "value\r\nContent-Length: 4\r\nTransfer-Encoding: chunked\r\n\r\n5c\r\nGET /admin HTTP/1.1",
                "data\r\nContent-Length: 13\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\nGET /secret",
                "normal\r\nContent-Length: 15\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\nPOST /backdoor",
                "cl.te\r\nContent-Length: 8\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\nSMUGGLE",
                "smuggle\r\nContent-Length: 44\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\nGET /admin HTTP/1.1\r\nHost: vulnerable-website.com",
                "get\r\nContent-Length: 30\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\nDELETE /admin/users HTTP/1.1"
        };
        return clTeAttacks[randomSelection(clTeAttacks.length)];
    }

    private String createTeClSmuggling() {
        String[] teClAttacks = {
                "test\r\nTransfer-Encoding: chunked\r\nContent-Length: 4\r\n\r\n5c\r\nGET /admin HTTP/1.1\r\n0\r\n\r\n",
                "value\r\nTransfer-Encoding: chunked\r\nContent-Length: 6\r\n\r\n0\r\n\r\nG",
                "smuggle\r\nTransfer-Encoding: chunked\r\nContent-Length: 13\r\n\r\n56\r\nGET /secret HTTP/1.1\r\nHost: internal\r\n\r\n0\r\n\r\n",
                "normal\r\nTransfer-Encoding: chunked\r\nContent-Length: 15\r\n\r\n2a\r\nPOST /backdoor HTTP/1.1\r\nContent-Length: 15\r\n\r\n0\r\n\r\n",
                "te.cl\r\nTransfer-Encoding: chunked\r\nContent-Length: 8\r\n\r\n23\r\nGET /admin/delete?user=victim\r\n0\r\n\r\n",
                "request\r\nTransfer-Encoding: chunked\r\nContent-Length: 44\r\n\r\n71\r\nPOST /admin/users HTTP/1.1\r\nHost: vulnerable\r\nContent-Length: 15\r\n\r\nx=1\r\n0\r\n\r\n",
                "post\r\nTransfer-Encoding: chunked\r\nContent-Length: 30\r\n\r\n3c\r\nDELETE /admin/users/victim HTTP/1.1\r\nHost: internal-admin\r\n\r\n0\r\n\r\n"
        };
        return teClAttacks[randomSelection(teClAttacks.length)];
    }

    private String createTeTeSmuggling() {
        String[] teTeAttacks = {
                "test\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding: identity\r\n\r\n5c\r\nGET /admin HTTP/1.1",
                "value\r\nTransfer-Encoding: chunked\r\nTransfer-encoding: chunked\r\n\r\n0\r\n\r\nSMUGGLE",
                "smuggle\r\nTransfer-Encoding: xchunked\r\nTransfer-Encoding: chunked\r\n\r\n23\r\nGET /secret HTTP/1.1\r\n0\r\n\r\n",
                "normal\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding: x\r\n\r\n2a\r\nPOST /backdoor HTTP/1.1\r\n0\r\n\r\n",
                "te.te\r\nTransfer-Encoding: chunked, identity\r\nTransfer-Encoding: identity\r\n\r\n5c\r\nGET /admin/delete",
                "double\r\nTransfer-Encoding: identity\r\nTransfer-Encoding: chunked\r\n\r\n71\r\nPOST /admin/users HTTP/1.1\r\n0\r\n\r\n",
                "multiple\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding:\tchunked\r\n\r\n3c\r\nDELETE /users HTTP/1.1\r\n0\r\n\r\n"
        };
        return teTeAttacks[randomSelection(teTeAttacks.length)];
    }

    private String createClClSmuggling() {
        String[] clClAttacks = {
                "test\r\nContent-Length: 6\r\nContent-Length: 0\r\n\r\nGET /admin HTTP/1.1",
                "value\r\nContent-Length: 13\r\nContent-Length: 7\r\n\r\nSMUGGLE REQUEST",
                "smuggle\r\nContent-Length: 0\r\nContent-Length: 44\r\n\r\nGET /secret HTTP/1.1\r\nHost: vulnerable-website.com",
                "normal\r\nContent-Length: 15\r\nContent-Length: 25\r\n\r\nPOST /backdoor HTTP/1.1\r\nContent-Length: 15",
                "cl.cl\r\nContent-Length: 8\r\nContent-Length: 30\r\n\r\nGET /admin/delete?user=victim",
                "duplicate\r\nContent-Length: 44\r\nContent-Length: 6\r\n\r\nPOST /admin/users HTTP/1.1\r\nx=1",
                "conflict\r\nContent-Length: 30\r\nContent-Length: 60\r\n\r\nDELETE /admin/users/victim HTTP/1.1\r\nHost: internal"
        };
        return clClAttacks[randomSelection(clClAttacks.length)];
    }

    private String createHttp2DowngradeSmuggling() {
        String[] http2Attacks = {
                "test\r\nHTTP2-Settings: AAMAAABkAAQAAgAAAAA\r\nUpgrade: h2c\r\nConnection: Upgrade, HTTP2-Settings\r\nContent-Length: 0",
                "h2\r\nConnection: Upgrade\r\nUpgrade: h2c\r\nHTTP2-Settings: smuggle\r\nContent-Length: 35\r\n\r\nGET /admin HTTP/1.1",
                "downgrade\r\nPRI * HTTP/2.0\r\n\r\nSM\r\n\r\nGET /secret HTTP/1.1\r\nHost: internal",
                "protocol\r\nHTTP2-Settings: smuggled\r\nUpgrade: h2c\r\nConnection: HTTP2-Settings\r\nContent-Length: 25",
                "h2smuggle\r\nConnection: close, Upgrade\r\nUpgrade: h2c\r\nHTTP2-Settings: AAMAAABkAAQAAgAAAAA\r\nContent-Length: 44",
                "version\r\nPRI * HTTP/2.0\r\n\r\nSM\r\n\r\nPOST /admin/users HTTP/1.1\r\nHost: vulnerable",
                "upgrade\r\nUpgrade: h2c\r\nConnection: Upgrade\r\nHTTP2-Settings: exploit\r\nContent-Length: 30"
        };
        return http2Attacks[randomSelection(http2Attacks.length)];
    }

    private String createPipelinePoisoning() {
        String[] pipelineAttacks = {
                "test\r\nConnection: keep-alive\r\nContent-Length: 44\r\n\r\nGET /admin HTTP/1.1\r\nHost: vulnerable-website.com\r\n\r\n",
                "pipeline\r\nConnection: keep-alive\r\nContent-Length: 0\r\n\r\nPOST /admin/users HTTP/1.1\r\nContent-Length: 15",
                "poison\r\nConnection: keep-alive\r\nContent-Length: 56\r\n\r\nGET /secret HTTP/1.1\r\nHost: internal\r\nAuthorization: Bearer token",
                "keep\r\nConnection: keep-alive\r\nContent-Length: 25\r\n\r\nDELETE /backdoor HTTP/1.1\r\nHost: admin",
                "persist\r\nConnection: keep-alive\r\nContent-Length: 71\r\n\r\nPOST /admin/delete HTTP/1.1\r\nHost: vulnerable\r\nContent-Length: 15\r\n\r\nx=1",
                "queue\r\nConnection: keep-alive\r\nContent-Length: 35\r\n\r\nGET /admin/users/victim HTTP/1.1\r\nHost: internal-admin",
                "persistent\r\nConnection: keep-alive\r\nContent-Length: 60\r\n\r\nPUT /admin/settings HTTP/1.1\r\nHost: vulnerable\r\nContent-Length: 20"
        };
        return pipelineAttacks[randomSelection(pipelineAttacks.length)];
    }

    private String createCacheDeception() {
        String[] cacheAttacks = {
                "test\r\nCache-Control: max-age=3600\r\nContent-Length: 44\r\n\r\nGET /admin/sensitive HTTP/1.1\r\nHost: cache-target",
                "cache\r\nVary: User-Agent\r\nContent-Length: 0\r\n\r\nPOST /admin/users HTTP/1.1\r\nAuthorization: Bearer stolen",
                "deception\r\nCache-Control: public\r\nContent-Length: 56\r\n\r\nGET /secret.json HTTP/1.1\r\nHost: api\r\nX-API-Key: secret",
                "poison\r\nExpires: Wed, 21 Oct 2025 07:28:00 GMT\r\nContent-Length: 25\r\n\r\nDELETE /cache HTTP/1.1\r\nHost: admin",
                "store\r\nCache-Control: max-age=31536000\r\nContent-Length: 71\r\n\r\nPOST /admin/config HTTP/1.1\r\nHost: vulnerable\r\nContent-Length: 15",
                "cdn\r\nVary: Authorization\r\nContent-Length: 35\r\n\r\nGET /admin/secrets HTTP/1.1\r\nAuthorization: Basic admin:pass",
                "edge\r\nCache-Control: public, max-age=86400\r\nContent-Length: 60\r\n\r\nPUT /admin/cache HTTP/1.1\r\nHost: edge-cache"
        };
        return cacheAttacks[randomSelection(cacheAttacks.length)];
    }

    private String createAuthenticationBypass() {
        String[] authBypassAttacks = {
                "test\r\nAuthorization: Bearer hijacked\r\nContent-Length: 44\r\n\r\nGET /admin/users HTTP/1.1\r\nHost: admin-panel",
                "auth\r\nX-Forwarded-User: admin\r\nContent-Length: 0\r\n\r\nPOST /protected HTTP/1.1\r\nAuthorization: Bearer victim-token",
                "bypass\r\nX-Remote-User: root\r\nContent-Length: 56\r\n\r\nGET /admin/secrets HTTP/1.1\r\nHost: internal\r\nSession-Id: stolen",
                "session\r\nCookie: session=admin-session\r\nContent-Length: 25\r\n\r\nDELETE /users/victim HTTP/1.1\r\nHost: app",
                "hijack\r\nX-Forwarded-For: 127.0.0.1\r\nContent-Length: 71\r\n\r\nPOST /admin/elevate HTTP/1.1\r\nHost: vulnerable\r\nContent-Length: 15",
                "identity\r\nX-User-Role: administrator\r\nContent-Length: 35\r\n\r\nGET /admin/config HTTP/1.1\r\nX-Internal-User: admin",
                "spoof\r\nX-Original-URL: /admin\r\nContent-Length: 60\r\n\r\nPUT /admin/users HTTP/1.1\r\nHost: spoofed\r\nAuthorization: spoofed"
        };
        return authBypassAttacks[randomSelection(authBypassAttacks.length)];
    }

    private String createHeaderManipulation() {
        String[] headerAttacks = {
                "test\r\nX-Forwarded-Proto: https\r\nContent-Length: 44\r\n\r\nGET /admin HTTP/1.1\r\nX-Forwarded-Proto: http",
                "header\r\nHost: evil.com\r\nContent-Length: 0\r\n\r\nPOST /webhook HTTP/1.1\r\nHost: legitimate.com",
                "inject\r\nX-Forwarded-Host: attacker.com\r\nContent-Length: 56\r\n\r\nGET /password-reset HTTP/1.1\r\nHost: victim.com",
                "modify\r\nX-Original-IP: 192.168.1.1\r\nContent-Length: 25\r\n\r\nDELETE /admin HTTP/1.1\r\nX-Real-IP: attacker",
                "override\r\nX-HTTP-Method-Override: DELETE\r\nContent-Length: 71\r\n\r\nPOST /users HTTP/1.1\r\nHost: vulnerable",
                "replace\r\nReferer: http://admin.internal\r\nContent-Length: 35\r\n\r\nGET /internal/api HTTP/1.1\r\nReferer: http://evil.com",
                "swap\r\nUser-Agent: AdminBot/1.0\r\nContent-Length: 60\r\n\r\nPUT /config HTTP/1.1\r\nUser-Agent: AttackerBot/2.0"
        };
        return headerAttacks[randomSelection(headerAttacks.length)];
    }

    private String createMethodOverrideSmuggling() {
        String[] methodOverrideAttacks = {
                "test\r\nX-HTTP-Method-Override: DELETE\r\nContent-Length: 44\r\n\r\nPOST /admin/users HTTP/1.1\r\nHost: vulnerable",
                "override\r\nX-HTTP-Method: PUT\r\nContent-Length: 0\r\n\r\nGET /admin/config HTTP/1.1\r\nX-Method-Override: PATCH",
                "method\r\nX-Method-Override: DELETE\r\nContent-Length: 56\r\n\r\nPOST /users/victim HTTP/1.1\r\nHost: app\r\nContent-Length: 0",
                "verb\r\n_method: PUT\r\nContent-Length: 25\r\n\r\nGET /admin/settings HTTP/1.1\r\n_method: DELETE",
                "tunnel\r\nX-HTTP-Method-Override: PATCH\r\nContent-Length: 71\r\n\r\nPOST /admin/users HTTP/1.1\r\nHost: vulnerable\r\n_method: DELETE",
                "disguise\r\nX-Method: DELETE\r\nContent-Length: 35\r\n\r\nGET /users HTTP/1.1\r\nX-HTTP-Method-Override: DELETE",
                "hidden\r\n_method: PATCH\r\nContent-Length: 60\r\n\r\nPOST /admin/config HTTP/1.1\r\nX-Method-Override: PUT"
        };
        return methodOverrideAttacks[randomSelection(methodOverrideAttacks.length)];
    }

    private String createUrlRewritingAttack() {
        String[] urlRewriteAttacks = {
                "test\r\nX-Original-URL: /admin/users\r\nContent-Length: 44\r\n\r\nGET /public HTTP/1.1\r\nHost: vulnerable",
                "rewrite\r\nX-Rewrite-URL: /admin/secrets\r\nContent-Length: 0\r\n\r\nPOST /allowed HTTP/1.1\r\nX-Original-URL: /forbidden",
                "url\r\nX-Original-URI: /admin/config\r\nContent-Length: 56\r\n\r\nGET /public/info HTTP/1.1\r\nHost: app\r\nX-Rewrite-URL: /admin",
                "path\r\nX-Forwarded-URI: /admin/delete\r\nContent-Length: 25\r\n\r\nDELETE /safe HTTP/1.1\r\nX-Original-URI: /admin",
                "redirect\r\nX-Original-URL: /admin/elevate\r\nContent-Length: 71\r\n\r\nPOST /public HTTP/1.1\r\nHost: vulnerable\r\nX-Rewrite-URL: /admin",
                "route\r\nX-Forwarded-Path: /admin/users\r\nContent-Length: 35\r\n\r\nGET /normal HTTP/1.1\r\nX-Original-URL: /admin/delete",
                "proxy\r\nX-Proxy-URL: /admin/config\r\nContent-Length: 60\r\n\r\nPUT /public/data HTTP/1.1\r\nX-Forwarded-URI: /admin"
        };
        return urlRewriteAttacks[randomSelection(urlRewriteAttacks.length)];
    }

    private String createRequestHijacking() {
        String[] hijackingAttacks = {
                "test\r\nContent-Length: 0\r\n\r\nGET /victim-request HTTP/1.1\r\nHost: hijack-target\r\nAuthorization: Bearer victim-token",
                "hijack\r\nContent-Length: 5\r\n\r\nX=1POST /capture HTTP/1.1\r\nHost: attacker.com\r\nContent-Length: 100",
                "capture\r\nContent-Length: 15\r\n\r\nSMUGGLED_REQUESTGET /sensitive HTTP/1.1\r\nHost: internal\r\nSession-Id: victim",
                "steal\r\nContent-Length: 30\r\n\r\nHIJACK_PAYLOAD_REQUESTPOST /admin HTTP/1.1\r\nHost: target\r\nAuthorization: Bearer stolen",
                "intercept\r\nContent-Length: 44\r\n\r\nINTERCEPTED_USER_REQUESTGET /profile HTTP/1.1\r\nHost: app\r\nCookie: session=victim",
                "poison\r\nContent-Length: 60\r\n\r\nPOISONED_REQUEST_QUEUEGET /admin/users HTTP/1.1\r\nHost: admin\r\nX-API-Key: secret",
                "queue\r\nContent-Length: 35\r\n\r\nQUEUE_POISONING_ATTACKDELETE /users HTTP/1.1\r\nHost: api\r\nAuthorization: admin"
        };
        return hijackingAttacks[randomSelection(hijackingAttacks.length)];
    }

    private String createResponseQueuePoisoning() {
        String[] queuePoisonAttacks = {
                "test\r\nContent-Length: 0\r\n\r\nHTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 25\r\n\r\n<h1>Poisoned Response</h1>",
                "poison\r\nContent-Length: 5\r\n\r\nX=1HTTP/1.1 302 Found\r\nLocation: http://evil.com\r\nContent-Length: 0",
                "queue\r\nContent-Length: 15\r\n\r\nSMUGGLED_RESPONSEHTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"admin\"",
                "desync\r\nContent-Length: 30\r\n\r\nFAKE_RESPONSE_HEADERHTTP/1.1 500 Internal Server Error\r\nContent-Type: text/plain",
                "corrupt\r\nContent-Length: 44\r\n\r\nRESPONSE_QUEUE_CORRUPTIONHTTP/1.1 403 Forbidden\r\nContent-Length: 15\r\n\r\nAccess Denied",
                "desynchronize\r\nContent-Length: 60\r\n\r\nDESYNC_ATTACK_RESPONSEHTTP/1.1 200 OK\r\nSet-Cookie: admin=true\r\nContent-Length: 10",
                "mismatch\r\nContent-Length: 35\r\n\r\nRESPONSE_MISMATCH_ATTACKHTTP/1.1 301 Moved\r\nLocation: javascript:alert(1)"
        };
        return queuePoisonAttacks[randomSelection(queuePoisonAttacks.length)];
    }

    private String createWebSocketUpgradeSmuggling() {
        String[] websocketAttacks = {
                "test\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nContent-Length: 44\r\n\r\nGET /admin HTTP/1.1\r\nHost: websocket-target",
                "ws\r\nSec-WebSocket-Key: smuggled\r\nUpgrade: websocket\r\nContent-Length: 0\r\n\r\nPOST /admin/users HTTP/1.1",
                "websocket\r\nConnection: keep-alive, Upgrade\r\nUpgrade: websocket\r\nContent-Length: 56\r\n\r\nGET /sensitive HTTP/1.1\r\nHost: internal",
                "protocol\r\nSec-WebSocket-Protocol: smuggle\r\nUpgrade: websocket\r\nContent-Length: 25\r\n\r\nDELETE /admin HTTP/1.1",
                "upgrade\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nContent-Length: 71\r\n\r\nPOST /admin/config HTTP/1.1\r\nHost: vulnerable",
                "handshake\r\nSec-WebSocket-Extensions: smuggle\r\nUpgrade: websocket\r\nContent-Length: 35\r\n\r\nGET /admin/secrets HTTP/1.1",
                "switch\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nContent-Length: 60\r\n\r\nPUT /admin/websocket HTTP/1.1\r\nHost: target"
        };
        return websocketAttacks[randomSelection(websocketAttacks.length)];
    }

    private String createChunkedEncodingBypass() {
        String[] chunkedBypassAttacks = {
                "test\r\nTransfer-Encoding: chunked\r\n\r\n1e\r\nGET /admin HTTP/1.1\r\nHost: bypass\r\n0\r\n\r\n",
                "chunk\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\nPOST /admin/users HTTP/1.1\r\nContent-Length: 15\r\n\r\nx=1",
                "bypass\r\nTransfer-Encoding: chunked\r\n\r\n56\r\nGET /sensitive HTTP/1.1\r\nHost: internal\r\nAuthorization: Bearer token\r\n0\r\n\r\n",
                "encoding\r\nTransfer-Encoding: chunked\r\n\r\n2a\r\nDELETE /admin/users HTTP/1.1\r\nHost: vulnerable\r\n0\r\n\r\n",
                "chunk\r\nTransfer-Encoding: chunked\r\n\r\n71\r\nPOST /admin/elevate HTTP/1.1\r\nHost: app\r\nContent-Length: 15\r\n\r\nadmin=true\r\n0\r\n\r\n",
                "split\r\nTransfer-Encoding: chunked\r\n\r\n3c\r\nGET /admin/config HTTP/1.1\r\nHost: target\r\nX-Admin: true\r\n0\r\n\r\n",
                "fragment\r\nTransfer-Encoding: chunked\r\n\r\n4a\r\nPUT /admin/settings HTTP/1.1\r\nHost: vulnerable\r\nContent-Length: 20\r\n0\r\n\r\n"
        };
        return chunkedBypassAttacks[randomSelection(chunkedBypassAttacks.length)];
    }

    /**
     * Selects a random index in the range {@code [0, max)} using the
     * cui-test-generator infrastructure. This makes selection seed-reproducible
     * (governed by the framework seed) while allowing every {@link #next()}
     * invocation to cover any of the documented attack branches.
     *
     * @param max exclusive upper bound (number of choices), must be positive
     * @return a pseudo-random index in {@code [0, max)}
     */
    private int randomSelection(int max) {
        return Generators.integers(0, max - 1).next();
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}