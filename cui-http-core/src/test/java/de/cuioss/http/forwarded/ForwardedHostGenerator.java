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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

import java.util.List;

/**
 * Supplies the hostile {@code X-Forwarded-Host} population the forwarded host guard must reject.
 *
 * <p>The population is owned here rather than inlined at each assertion site, so the host grammar's
 * adversarial input set has one home: a case added here reaches every consumer at once, instead of
 * being added to one test and silently missing from the next.</p>
 *
 * <p>Two consumption shapes are offered deliberately. {@link #hostileHosts()} exposes the population
 * as a list for an exhaustive parameterized run — every named case is then covered on every
 * execution, which a random draw cannot promise. {@link #next()} draws from the same list for
 * generator-driven use, so the two shapes cannot drift apart about what counts as hostile.</p>
 */
public class ForwardedHostGenerator implements TypedGenerator<String> {

    /**
     * Each entry states a distinct way a host value can carry meaning the composed URL authority
     * would act on: a quote that breaks out of a quoted directive, angle brackets and a semicolon
     * that terminate or extend a token, a percent-encoded path separator that a decoding consumer
     * turns back into {@code /}, a stray closing bracket that mimics the IPv6 literal form, and a
     * Cyrillic homoglyph whose rendered form is indistinguishable from the ASCII host it spoofs.
     */
    private static final List<String> HOSTILE_HOSTS = List.of(
            "app.example\"evil.com",
            "app<evil>.com",
            "app;evil.com",
            "app%2fevil.com",
            "app.example]",
            "аpp.example.com");

    private final TypedGenerator<Integer> indexSelector = Generators.integers(0, HOSTILE_HOSTS.size() - 1);

    /**
     * @return the full hostile population, for an exhaustive parameterized run (never empty)
     */
    public static List<String> hostileHosts() {
        return HOSTILE_HOSTS;
    }

    @Override
    public String next() {
        return HOSTILE_HOSTS.get(indexSelector.next());
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
