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

import de.cuioss.http.forwarded.ResolvedForwarding;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Throughput benchmarks for {@link de.cuioss.http.forwarded.ForwardedHeaderResolver}.
 * Measures resolution and serialization operations per second across clean, RFC 7239, and
 * injection input patterns.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ForwardedResolverBenchmark {

    /** Primary throughput metric: full resolution of a clean proxied header set. */
    @Benchmark
    public ResolvedForwarding resolveCleanThroughput(ForwardedBenchmarkState state) {
        return state.resolver.resolve(state.nextCleanXForwarded());
    }

    /** Resolution throughput exercising the RFC 7239 {@code Forwarded} parse path. */
    @Benchmark
    public ResolvedForwarding resolveForwardedThroughput(ForwardedBenchmarkState state) {
        return state.resolver.resolve(state.nextForwarded());
    }

    /** Resolution throughput on injection inputs (values are sanitized away). */
    @Benchmark
    public ResolvedForwarding resolveAttackThroughput(ForwardedBenchmarkState state) {
        return state.resolver.resolve(state.nextAttack());
    }

    /** Serialization throughput to the {@code X-Forwarded-*} header family. */
    @Benchmark
    public Map<String, String> serializeXForwardedThroughput(ForwardedBenchmarkState state) {
        return state.resolved.toXForwardedHeaders();
    }

    /** Serialization throughput to a single RFC 7239 {@code Forwarded} value. */
    @Benchmark
    public Optional<String> serializeForwardedThroughput(ForwardedBenchmarkState state) {
        return state.resolved.toForwardedHeader();
    }
}
