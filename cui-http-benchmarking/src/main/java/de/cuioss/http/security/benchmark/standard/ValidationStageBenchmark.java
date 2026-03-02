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
import org.openjdk.jmh.annotations.Threads;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Isolation benchmarks for individual validation stages.
 * Measures the cost of each stage independently to identify bottlenecks.
 * Single-threaded to get accurate per-stage latency.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(1)
public class ValidationStageBenchmark {

    /** Pattern matching stage: regex-based attack pattern detection. */
    @Benchmark
    public Optional<String> patternMatchingStage(SecurityBenchmarkState state) throws UrlSecurityException {
        return state.patternMatchingStage.validate(state.nextCleanUrl());
    }

    /** Decoding stage: URL percent-encoding and UTF-8 overlong detection. */
    @Benchmark
    public Optional<String> decodingStage(SecurityBenchmarkState state) throws UrlSecurityException {
        return state.decodingStage.validate(state.nextCleanUrl());
    }

    /** Character validation stage: allowed character set enforcement. */
    @Benchmark
    public Optional<String> characterValidationStage(SecurityBenchmarkState state) throws UrlSecurityException {
        return state.characterValidationStage.validate(state.nextCleanUrl());
    }

    /** Normalization stage: RFC 3986 path normalization with traversal detection. */
    @Benchmark
    public Optional<String> normalizationStage(SecurityBenchmarkState state) throws UrlSecurityException {
        return state.normalizationStage.validate(state.nextCleanUrl());
    }

    /** Length validation stage: input length limit enforcement. */
    @Benchmark
    public Optional<String> lengthValidationStage(SecurityBenchmarkState state) throws UrlSecurityException {
        return state.lengthValidationStage.validate(state.nextCleanUrl());
    }
}
