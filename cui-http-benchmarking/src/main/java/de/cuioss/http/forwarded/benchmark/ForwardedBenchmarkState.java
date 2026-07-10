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
package de.cuioss.http.forwarded.benchmark;

import de.cuioss.http.forwarded.ForwardedHeaderResolver;
import de.cuioss.http.forwarded.ForwardedResolverConfig;
import de.cuioss.http.forwarded.ResolvedForwarding;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Shared JMH state for the forwarded-header resolver benchmarks. Builds the resolver once per trial
 * and cycles through representative header sets for each invocation.
 */
@State(Scope.Thread)
public class ForwardedBenchmarkState {

    /** Resolver honoring all fields, with a trusted-proxy range for client-IP resolution. */
    public ForwardedHeaderResolver resolver;

    /** A representative pre-resolved result, used to benchmark serialization. */
    public ResolvedForwarding resolved;

    // Clean, realistic X-Forwarded-* header sets.
    private static final List<Map<String, String>> CLEAN_XFORWARDED = List.of(
            Map.of("X-Forwarded-Proto", "https", "X-Forwarded-Host", "app.example.com:8443",
                    "X-Forwarded-Prefix", "/ui", "X-Forwarded-For", "203.0.113.7, 10.0.0.5"),
            Map.of("X-Forwarded-Proto", "https", "X-Forwarded-Host", "api.example.com",
                    "X-Forwarded-Port", "443", "X-Forwarded-For", "198.51.100.23, 10.1.2.3"),
            Map.of("X-Forwarded-Proto", "http", "X-Forwarded-Host", "gateway.internal:8080",
                    "X-Forwarded-Prefix", "/service/v1", "X-Forwarded-For", "192.0.2.44, 10.9.9.9"));

    // RFC 7239 Forwarded header sets (exercise the quoted-string parser).
    private static final List<Map<String, String>> FORWARDED_RFC = List.of(
            Map.of("Forwarded", "for=203.0.113.7;host=app.example.com;proto=https, for=\"10.0.0.5\""),
            Map.of("Forwarded", "proto=https;host=\"api.example.com:443\";for=\"[2001:db8::1]\", for=10.1.2.3"),
            Map.of("Forwarded", "for=192.0.2.44;proto=http;host=gateway.internal:8080, for=10.9.9.9"));

    // Injection / attack header sets (expected to be sanitized away).
    private static final List<Map<String, String>> ATTACK_XFORWARDED = List.of(
            Map.of("X-Forwarded-Host", "evil.com\r\nInjected: 1", "X-Forwarded-Prefix", "//attacker.com"),
            Map.of("X-Forwarded-Prefix", "/app\\..\\..", "X-Forwarded-For", "not-an-ip, 10.0.0.5"),
            Map.of("X-Forwarded-Proto", "javascript:alert(1)", "X-Forwarded-Host", "evil.com/../../etc"));

    private final AtomicInteger cleanIndex = new AtomicInteger(0);
    private final AtomicInteger forwardedIndex = new AtomicInteger(0);
    private final AtomicInteger attackIndex = new AtomicInteger(0);

    @Setup(Level.Trial)
    public void setup() {
        ForwardedResolverConfig config = ForwardedResolverConfig.builder()
                .trustAll(true)
                .trustedProxies(Set.of("10.0.0.0/8"))
                .build();
        resolver = new ForwardedHeaderResolver(config, new SecurityEventCounter());
        resolved = new ResolvedForwarding(Optional.of("https"), Optional.of("app.example.com"),
                OptionalInt.of(8443), "/ui", Optional.of("203.0.113.7"));
    }

    /** @return the next clean {@code X-Forwarded-*} header accessor, cycling. */
    public Function<String, String> nextCleanXForwarded() {
        return CLEAN_XFORWARDED.get(cleanIndex.getAndIncrement() % CLEAN_XFORWARDED.size())::get;
    }

    /** @return the next RFC 7239 {@code Forwarded} header accessor, cycling. */
    public Function<String, String> nextForwarded() {
        return FORWARDED_RFC.get(forwardedIndex.getAndIncrement() % FORWARDED_RFC.size())::get;
    }

    /** @return the next injection header accessor, cycling. */
    public Function<String, String> nextAttack() {
        return ATTACK_XFORWARDED.get(attackIndex.getAndIncrement() % ATTACK_XFORWARDED.size())::get;
    }
}
