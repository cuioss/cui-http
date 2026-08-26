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

import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@link ForwardedHeaderResolver#resolve(Function)} accessor contract as executable
 * behaviour: the accessor MUST return every instance of a repeated header, in wire order.
 *
 * <p>Each case is paired with a matched negative control — the same header set read through an
 * under-supplying accessor that returns only the first instance. The control is what makes the
 * caller obligation visible and non-optional: without it, an under-supplying accessor still
 * "works" and simply resolves the attacker's value, which is indistinguishable from correct
 * behaviour unless the two are asserted side by side.</p>
 */
@EnableTestLogger
@DisplayName("ForwardedHeaderResolver accessor contract")
class ForwardedAccessorContractTest {

    /** A compliant accessor: returns every instance of the named header, in wire order. */
    private static Function<String, List<String>> everyInstance(Map<String, List<String>> values) {
        return new HashMap<>(values)::get;
    }

    /**
     * An under-supplying accessor: exposes only the first instance, as a single-valued
     * {@code request::getHeader} would. This is the shape the contract forbids.
     */
    private static Function<String, List<String>> firstInstanceOnly(Map<String, List<String>> values) {
        Map<String, List<String>> copy = new HashMap<>(values);
        return name -> {
            List<String> instances = copy.get(name);
            return instances == null || instances.isEmpty() ? null : List.of(instances.getFirst());
        };
    }

    private static ForwardedHeaderResolver trustingResolver() {
        ForwardedResolverConfig config = ForwardedResolverConfig.builder()
                .trustAll(true)
                .trustedProxies(Set.of("10.0.0.0/8"))
                .build();
        return new ForwardedHeaderResolver(config, new SecurityEventCounter());
    }

    @Nested
    @DisplayName("Repeated X-Forwarded-For")
    class RepeatedClientIpChain {

        /**
         * Append-style shape: the client forged the first instance before the request reached the
         * proxy, and the proxy appended the second recording the real peer.
         */
        private static final Map<String, List<String>> APPEND_STYLE = Map.of(
                "X-Forwarded-For", List.of("6.6.6.6", "203.0.113.7, 10.0.0.5"));

        @Test
        @DisplayName("returning every instance in wire order is the required caller contract: the proxy-attested client IP wins")
        void everyInstanceResolvesProxyAttestedClient() {
            var result = trustingResolver().resolve(everyInstance(APPEND_STYLE));

            assertEquals("203.0.113.7", result.clientIp().orElseThrow(),
                    "the nearest hop's appended instance must be visible to the resolver");
        }

        @Test
        @DisplayName("negative control: an accessor returning only the first instance resolves the attacker's client IP")
        void firstInstanceOnlyResolvesAttackerClient() {
            var result = trustingResolver().resolve(firstInstanceOnly(APPEND_STYLE));

            assertEquals("6.6.6.6", result.clientIp().orElseThrow(),
                    "hiding the appended instance leaves only the forged one — the caller obligation is real");
        }
    }

    @Nested
    @DisplayName("Repeated X-Forwarded-Proto")
    class RepeatedScheme {

        private static final Map<String, List<String>> APPEND_STYLE = Map.of(
                "X-Forwarded-Proto", List.of("https", "http"));

        @Test
        @DisplayName("returning every instance in wire order is the required caller contract: the nearest hop's scheme wins")
        void everyInstanceResolvesNearestHopScheme() {
            var result = trustingResolver().resolve(everyInstance(APPEND_STYLE));

            assertEquals("http", result.scheme().orElseThrow(),
                    "the appended instance is the nearest hop's scheme");
        }

        @Test
        @DisplayName("negative control: an accessor returning only the first instance resolves the attacker's scheme")
        void firstInstanceOnlyResolvesAttackerScheme() {
            var result = trustingResolver().resolve(firstInstanceOnly(APPEND_STYLE));

            assertEquals("https", result.scheme().orElseThrow(),
                    "hiding the appended instance leaves only the forged one — the caller obligation is real");
        }
    }
}
