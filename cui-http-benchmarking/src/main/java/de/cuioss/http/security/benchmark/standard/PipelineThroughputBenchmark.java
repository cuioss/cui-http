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
package de.cuioss.http.security.benchmark.standard;

import de.cuioss.http.security.benchmark.SecurityBenchmarkState;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Throughput benchmarks for the security validation pipelines.
 * Measures operations per second for different pipeline types and input patterns.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class PipelineThroughputBenchmark {

    /**
     * Primary throughput metric: mixed pipeline validation with clean URLs.
     * Used by the benchmark runner for reporting.
     */
    @Benchmark
    public Optional<String> measureThroughput(SecurityBenchmarkState state) throws UrlSecurityException {
        return state.pipelines.urlPathPipeline().validate(state.nextCleanUrl());
    }

    /** URL path pipeline throughput with clean input. */
    @Benchmark
    public Optional<String> urlPathCleanThroughput(SecurityBenchmarkState state) throws UrlSecurityException {
        return state.pipelines.urlPathPipeline().validate(state.nextCleanUrl());
    }

    /** URL path pipeline throughput with attack input (expected to throw). */
    @Benchmark
    public Optional<String> urlPathAttackThroughput(SecurityBenchmarkState state) {
        try {
            return state.pipelines.urlPathPipeline().validate(state.nextAttackUrl());
        } catch (UrlSecurityException e) {
            return Optional.empty();
        }
    }

    /** URL parameter pipeline throughput with clean input. */
    @Benchmark
    public Optional<String> urlParameterCleanThroughput(SecurityBenchmarkState state) throws UrlSecurityException {
        return state.pipelines.urlParameterPipeline().validate(state.nextCleanParam());
    }

    /** HTTP header value pipeline throughput with clean input. */
    @Benchmark
    public Optional<String> headerValueCleanThroughput(SecurityBenchmarkState state) throws UrlSecurityException {
        return state.pipelines.headerValuePipeline().validate(state.nextCleanHeader());
    }
}
