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
package de.cuioss.http.security.benchmark;

import de.cuioss.benchmarking.common.config.BenchmarkConfiguration;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.runner.AbstractBenchmarkRunner;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.runner.RunnerException;

import java.io.IOException;

/**
 * Benchmark runner for CUI HTTP security validation pipelines.
 * Extends the common benchmarking infrastructure to run JMH micro-benchmarks
 * against the security validation pipelines.
 */
public class SecurityBenchmarkRunner extends AbstractBenchmarkRunner {

    private static final CuiLogger LOGGER = new CuiLogger(SecurityBenchmarkRunner.class);

    @Override
    protected BenchmarkConfiguration createConfiguration() {
        return BenchmarkConfiguration.builder()
                .withBenchmarkType(BenchmarkType.MICRO)
                .withThroughputBenchmarkName("measureThroughput")
                .withLatencyBenchmarkName("measureAverageTime")
                .withProjectName("CUI HTTP")
                .build();
    }

    @Override
    protected void prepareBenchmark(BenchmarkConfiguration config) throws IOException {
        LOGGER.debug("Starting CUI HTTP security pipeline benchmarks");
    }

    @Override
    protected void cleanup(BenchmarkConfiguration config) throws IOException {
        LOGGER.debug("Security pipeline benchmark cleanup completed");
    }

    public static void main(String[] args) throws IOException, RunnerException {
        new SecurityBenchmarkRunner().runBenchmark();
    }
}
