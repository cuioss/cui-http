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
package de.cuioss.http.security.pipeline;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal behavior tests for {@link URLParameterNameValidationPipeline}.
 *
 * <p>Existing coverage only exercises wiring via the pipeline factory. These tests assert the
 * name-strict behavior that distinguishes this pipeline from the parameter-value pipeline: a
 * plain query key is accepted, while a name carrying an encoded structural delimiter (CR/LF) is
 * rejected, closing the encoded-delimiter injection gap the class documents.</p>
 */
@DisplayName("URLParameterNameValidationPipeline behavior")
class URLParameterNameValidationPipelineBehaviorTest {

    private SecurityEventCounter eventCounter;
    private URLParameterNameValidationPipeline pipeline;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
        pipeline = new URLParameterNameValidationPipeline(SecurityConfiguration.defaults(), eventCounter);
    }

    @Test
    @DisplayName("reports PARAMETER_NAME as its validation type")
    void reportsParameterNameType() {
        assertEquals(ValidationType.PARAMETER_NAME, pipeline.getValidationType());
    }

    @Test
    @DisplayName("accepts a plain query key and returns it unchanged")
    void acceptsPlainName() {
        assertEquals(Optional.of("page"), pipeline.validate("page"));
    }

    @Test
    @DisplayName("rejects a parameter name carrying an encoded CRLF delimiter")
    void rejectsEncodedCrlfInName() {
        long before = eventCounter.getTotalCount();

        assertThrows(UrlSecurityException.class,
                () -> pipeline.validate("name%0d%0aInjected"));

        assertTrue(eventCounter.getTotalCount() > before,
                "Security event should be recorded for encoded-delimiter injection");
    }

    @Test
    @DisplayName("null input yields an empty Optional without throwing")
    void nullInput() {
        assertEquals(Optional.empty(), pipeline.validate(null));
    }

    @Test
    @DisplayName("instances built from the same configuration are equal")
    void shouldHaveCorrectEqualsAndHashCode() {
        SecurityConfiguration config = SecurityConfiguration.defaults();
        URLParameterNameValidationPipeline pipeline1 =
                new URLParameterNameValidationPipeline(config, eventCounter);
        URLParameterNameValidationPipeline pipeline2 =
                new URLParameterNameValidationPipeline(config, eventCounter);

        assertEquals(pipeline1, pipeline2, "Pipelines with same configuration should be equal");
        assertEquals(pipeline1.hashCode(), pipeline2.hashCode(),
                "Equal pipelines should have same hash code");
    }

    @Test
    @DisplayName("instances built from different configurations are not equal")
    void shouldNotBeEqualWhenConfigurationDiffers() {
        URLParameterNameValidationPipeline strict = new URLParameterNameValidationPipeline(
                SecurityConfiguration.strict(), eventCounter);
        URLParameterNameValidationPipeline lenient = new URLParameterNameValidationPipeline(
                SecurityConfiguration.lenient(), eventCounter);

        assertNotEquals(strict, lenient,
                "Pipelines with different security configurations must not compare equal");
    }

    @Test
    @DisplayName("the retained configuration is not exposed as a public accessor")
    void shouldNotExposeConfigAccessor() {
        assertThrows(NoSuchMethodException.class,
                () -> URLParameterNameValidationPipeline.class.getMethod("getConfig"),
                "The retained config must not become part of the exported public API");
    }
}
