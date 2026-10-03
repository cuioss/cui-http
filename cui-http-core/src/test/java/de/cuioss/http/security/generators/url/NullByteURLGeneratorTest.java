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
package de.cuioss.http.security.generators.url;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link NullByteURLGenerator}.
 *
 * <p>The generator's values are a fixed list. Its defining properties are that every entry of
 * that list is an API path carrying a null byte - raw or percent-encoded - that the URL path
 * pipeline rejects, and that {@link NullByteURLGenerator#next()} walks the list in order, so a
 * consumer drawing from it covers every entry instead of a random subset.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("NullByteURLGenerator Contract Tests")
class NullByteURLGeneratorTest {

    private static final String RAW_NULL_BYTE = "\0";
    private static final String ENCODED_NULL_BYTE = "%00";
    private static final int THREADS = 8;
    private static final int PASSES_PER_THREAD = 50;

    static Stream<String> nullByteUrls() {
        return NullByteURLGenerator.NULL_BYTE_URLS.stream();
    }

    @ParameterizedTest
    @MethodSource("nullByteUrls")
    @DisplayName("Every listed URL carries a null byte and is rejected by the pipeline")
    void shouldListNullByteInjection(String listedValue) {
        assertTrue(listedValue.startsWith("/api"),
                () -> "Null byte URLs are rooted at '/api'. Value: <" + listedValue + ">");
        assertContainsAny(listedValue, NULL_BYTE_MARKERS, "Null byte injection URL");

        assertPipelineRejects(
                new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                listedValue);
    }

    @Test
    @DisplayName("Should emit the whole list in declaration order and then start over")
    void shouldWalkTheListInOrder() {
        NullByteURLGenerator generator = new NullByteURLGenerator();
        int size = NullByteURLGenerator.NULL_BYTE_URLS.size();

        List<String> firstPass = draw(generator, size);
        List<String> secondPass = draw(generator, size);

        assertAll("Consecutive passes",
                () -> assertEquals(NullByteURLGenerator.NULL_BYTE_URLS, firstPass,
                        "The first pass must emit every listed URL once, in declaration order"),
                () -> assertEquals(NullByteURLGenerator.NULL_BYTE_URLS, secondPass,
                        "After the last entry the generator must start over at the first"));
    }

    @Test
    @DisplayName("Should list both the raw and the percent-encoded null-byte form")
    void shouldListBothNullByteEncodings() {
        List<String> urls = NullByteURLGenerator.NULL_BYTE_URLS;

        assertAll("Null-byte encodings",
                () -> assertTrue(urls.stream().anyMatch(url -> url.contains(RAW_NULL_BYTE)),
                        "The list must contain a raw null byte"),
                () -> assertTrue(urls.stream().anyMatch(url -> url.contains(ENCODED_NULL_BYTE)),
                        "The list must contain a percent-encoded null byte"));
    }

    @Test
    @DisplayName("Should hand every list entry out equally often when one instance is shared by threads")
    void shouldWalkTheListThreadSafely() throws Exception {
        NullByteURLGenerator generator = new NullByteURLGenerator();
        int size = NullByteURLGenerator.NULL_BYTE_URLS.size();
        Callable<List<String>> drawer = () -> draw(generator, size * PASSES_PER_THREAD);

        List<String> drawn = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                futures.add(executor.submit(drawer));
            }
            for (Future<List<String>> future : futures) {
                drawn.addAll(future.get());
            }
        }

        Map<String, Long> occurrences = drawn.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<String, Long> expected = NullByteURLGenerator.NULL_BYTE_URLS.stream()
                .collect(Collectors.toMap(Function.identity(), url -> (long) THREADS * PASSES_PER_THREAD));
        assertEquals(expected, occurrences,
                "A shared counter that lost or repeated an update would skew the per-entry counts");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new NullByteURLGenerator().getType(),
                "Generator should return String.class");
    }

    private static List<String> draw(NullByteURLGenerator generator, int count) {
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(generator.next());
        }
        return values;
    }
}
