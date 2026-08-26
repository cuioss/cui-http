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
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link NullByteURLGenerator}.
 *
 * <p>The defining property of this generator is that every emitted value is an API path
 * carrying a null byte — raw or percent-encoded — and is therefore rejected by the URL path
 * validation pipeline. The aggregate test asserts that both null-byte encodings are
 * reachable.</p>
 */
@EnableGeneratorController
@DisplayName("NullByteURLGenerator Contract Tests")
class NullByteURLGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;
    private static final String RAW_NULL_BYTE = "\0";
    private static final String ENCODED_NULL_BYTE = "%00";

    @ParameterizedTest
    @TypeGeneratorSource(value = NullByteURLGenerator.class, count = 100)
    @DisplayName("Every generated URL carries a null byte and is rejected by the pipeline")
    void shouldGenerateNullByteInjection(String generatedValue) {
        assertTrue(generatedValue.startsWith("/api"),
                () -> "Null byte URLs are rooted at '/api'. Value: <" + generatedValue + ">");
        assertContainsAny(generatedValue, NULL_BYTE_MARKERS, "Null byte injection URL");

        assertPipelineRejects(
                new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue);
    }

    @Test
    @DisplayName("Should reach both the raw and the percent-encoded null-byte form")
    void shouldReachBothNullByteEncodings() {
        NullByteURLGenerator generator = new NullByteURLGenerator();
        Set<String> forms = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            if (value.contains(RAW_NULL_BYTE)) {
                forms.add("raw");
            }
            if (value.contains(ENCODED_NULL_BYTE)) {
                forms.add("encoded");
            }
        }

        assertEquals(Set.of("raw", "encoded"), forms,
                "Both null-byte encodings must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new NullByteURLGenerator().getType(),
                "Generator should return String.class");
    }
}
