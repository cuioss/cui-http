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
package de.cuioss.http.security.pipeline;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.SupportedValidationTypeGenerator;
import de.cuioss.http.security.generators.header.ValidHTTPHeaderNameGenerator;
import de.cuioss.http.security.generators.header.ValidHTTPHeaderValueGenerator;
import de.cuioss.http.security.generators.url.PathTraversalURLGenerator;
import de.cuioss.http.security.generators.url.ValidURLParameterStringGenerator;
import de.cuioss.http.security.generators.url.ValidURLPathGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnableGeneratorController
class PipelineFactoryTest {

    private SecurityConfiguration config;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
    }

    @Nested
    class PipelineCreation {

        @ParameterizedTest
        @TypeGeneratorSource(value = ValidURLPathGenerator.class, count = 5)
        void shouldCreateUrlPathValidationPipeline(String validPath) {
            HttpSecurityValidator pipeline = PipelineFactory.createUrlPathPipeline(config, eventCounter);
            assertInstanceOf(URLPathValidationPipeline.class, pipeline);

            Optional<String> result = pipeline.validate(validPath);
            assertTrue(result.isPresent());
            assertEquals(validPath, result.get());
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = ValidURLParameterStringGenerator.class, count = 5)
        void shouldCreateUrlParameterValidationPipeline(String validParam) {
            HttpSecurityValidator pipeline = PipelineFactory.createUrlParameterPipeline(config, eventCounter);
            assertInstanceOf(URLParameterValidationPipeline.class, pipeline);

            Optional<String> result = pipeline.validate(validParam);
            assertTrue(result.isPresent());
            assertNotNull(result.get(), "URL parameter pipeline should process valid input without throwing exception");
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = ValidHTTPHeaderNameGenerator.class, count = 5)
        void shouldCreateHeaderNameValidationPipeline(String validHeaderName) {
            HttpSecurityValidator pipeline = PipelineFactory.createHeaderNamePipeline(config, eventCounter);
            assertInstanceOf(HTTPHeaderValidationPipeline.class, pipeline);

            Optional<String> result = pipeline.validate(validHeaderName);
            assertTrue(result.isPresent());
            assertEquals(validHeaderName, result.get());
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = ValidHTTPHeaderValueGenerator.class, count = 5)
        void shouldCreateHeaderValueValidationPipeline(String validHeaderValue) {
            HttpSecurityValidator pipeline = PipelineFactory.createHeaderValuePipeline(config, eventCounter);
            assertInstanceOf(HTTPHeaderValidationPipeline.class, pipeline);

            Optional<String> result = pipeline.validate(validHeaderValue);
            assertTrue(result.isPresent());
            assertEquals(validHeaderValue, result.get());
        }

    }

    @Nested
    class ValidationAndErrorHandling {

        @Test
        void shouldRejectNullConfiguration() {
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createUrlPathPipeline(null, eventCounter));
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createUrlParameterPipeline(null, eventCounter));
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createHeaderNamePipeline(null, eventCounter));
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createHeaderValuePipeline(null, eventCounter));
        }

        @Test
        void shouldRejectNullEventCounter() {
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createUrlPathPipeline(config, null));
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createUrlParameterPipeline(config, null));
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createHeaderNamePipeline(config, null));
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createHeaderValuePipeline(config, null));
        }

        @Test
        void shouldRejectUnsupportedValidationTypes() {
            IllegalArgumentException paramNameException = assertThrows(IllegalArgumentException.class,
                    () -> PipelineFactory.createPipeline(ValidationType.PARAMETER_NAME, config, eventCounter));
            assertTrue(paramNameException.getMessage().contains("PARAMETER_NAME validation is not supported"));
            assertTrue(paramNameException.getMessage().contains("Use PARAMETER_VALUE"));

            IllegalArgumentException cookieNameException = assertThrows(IllegalArgumentException.class,
                    () -> PipelineFactory.createPipeline(ValidationType.COOKIE_NAME, config, eventCounter));
            assertTrue(cookieNameException.getMessage().contains("Cookie validation pipelines are not yet implemented"));

            IllegalArgumentException cookieValueException = assertThrows(IllegalArgumentException.class,
                    () -> PipelineFactory.createPipeline(ValidationType.COOKIE_VALUE, config, eventCounter));
            assertTrue(cookieValueException.getMessage().contains("Cookie validation pipelines are not yet implemented"));

            IllegalArgumentException bodyException = assertThrows(IllegalArgumentException.class,
                    () -> PipelineFactory.createPipeline(ValidationType.BODY, config, eventCounter));
            assertTrue(bodyException.getMessage().contains("BODY validation pipeline has been removed"));
            assertTrue(bodyException.getMessage().contains("application layer"));
        }

        @Test
        void shouldRejectNullParametersInGenericFactory() {
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createPipeline(null, config, eventCounter));
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createPipeline(ValidationType.URL_PATH, null, eventCounter));
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createPipeline(ValidationType.URL_PATH, config, null));
        }
    }

    @Nested
    class GenericFactoryMethods {

        @Test
        void shouldCreatePipelinesUsingGenericFactory() {
            HttpSecurityValidator pathPipeline = PipelineFactory.createPipeline(ValidationType.URL_PATH, config, eventCounter);
            HttpSecurityValidator paramPipeline = PipelineFactory.createPipeline(ValidationType.PARAMETER_VALUE, config, eventCounter);
            HttpSecurityValidator headerNamePipeline = PipelineFactory.createPipeline(ValidationType.HEADER_NAME, config, eventCounter);
            HttpSecurityValidator headerValuePipeline = PipelineFactory.createPipeline(ValidationType.HEADER_VALUE, config, eventCounter);
            assertInstanceOf(URLPathValidationPipeline.class, pathPipeline);
            assertInstanceOf(URLParameterValidationPipeline.class, paramPipeline);
            assertInstanceOf(HTTPHeaderValidationPipeline.class, headerNamePipeline);
            assertInstanceOf(HTTPHeaderValidationPipeline.class, headerValuePipeline);
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = SupportedValidationTypeGenerator.class, count = 5)
        void shouldHandleSupportedValidationTypes(ValidationType validationType) {
            HttpSecurityValidator pipeline = PipelineFactory.createPipeline(validationType, config, eventCounter);
            assertNotNull(pipeline);
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = ValidURLPathGenerator.class, count = 3)
        void shouldCreateEquivalentPipelinesViaDifferentFactoryMethods(String validPath) {
            HttpSecurityValidator directPathPipeline = PipelineFactory.createUrlPathPipeline(config, eventCounter);
            HttpSecurityValidator genericPathPipeline = PipelineFactory.createPipeline(ValidationType.URL_PATH, config, eventCounter);

            assertEquals(directPathPipeline.getClass(), genericPathPipeline.getClass());
            assertEquals(directPathPipeline.validate(validPath), genericPathPipeline.validate(validPath));
        }

    }

    @Nested
    class PipelineSetOperations {

        @Test
        void shouldCreatePipelineSetWithAllCommonPipelines() {
            PipelineFactory.PipelineSet pipelineSet = PipelineFactory.createCommonPipelines(config, eventCounter);

            assertInstanceOf(URLPathValidationPipeline.class, pipelineSet.urlPathPipeline());
            assertInstanceOf(URLParameterValidationPipeline.class, pipelineSet.urlParameterPipeline());
            assertInstanceOf(HTTPHeaderValidationPipeline.class, pipelineSet.headerNamePipeline());
            assertInstanceOf(HTTPHeaderValidationPipeline.class, pipelineSet.headerValuePipeline());
        }

        @Test
        void shouldRejectNullParametersInPipelineSetCreation() {
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createCommonPipelines(null, eventCounter));
            assertThrows(NullPointerException.class,
                    () -> PipelineFactory.createCommonPipelines(config, null));
        }

        @Test
        void shouldUseSharedConfigurationAndEventCounter() {
            SecurityEventCounter sharedCounter = new SecurityEventCounter();
            PipelineFactory.PipelineSet pipelineSet = PipelineFactory.createCommonPipelines(config, sharedCounter);

            assertFalse(sharedCounter.hasAnyEvents());

            String nullByteContent = "test content\u0000with null byte";
            HttpSecurityValidator validator = pipelineSet.urlPathPipeline();
            assertThrows(UrlSecurityException.class, () ->
                    validator.validate(nullByteContent));
            assertTrue(sharedCounter.hasAnyEvents());
        }

        @Test
        void shouldValidatePipelineSetRecordConstraints() {
            HttpSecurityValidator validPipeline = PipelineFactory.createUrlPathPipeline(config, eventCounter);

            assertThrows(NullPointerException.class, () ->
                    new PipelineFactory.PipelineSet(null, validPipeline, validPipeline, validPipeline));
            assertThrows(NullPointerException.class, () ->
                    new PipelineFactory.PipelineSet(validPipeline, null, validPipeline, validPipeline));
            assertThrows(NullPointerException.class, () ->
                    new PipelineFactory.PipelineSet(validPipeline, validPipeline, null, validPipeline));
            assertThrows(NullPointerException.class, () ->
                    new PipelineFactory.PipelineSet(validPipeline, validPipeline, validPipeline, null));
        }

        @Test
        void shouldSupportPipelineSetEqualityAndHashcode() {
            PipelineFactory.PipelineSet set1 = PipelineFactory.createCommonPipelines(config, eventCounter);
            PipelineFactory.PipelineSet set2 = PipelineFactory.createCommonPipelines(config, eventCounter);

            assertEquals(set1, set2);
            assertEquals(set1.hashCode(), set2.hashCode());

            assertEquals(set1, set1);
            assertEquals(set1.hashCode(), set1.hashCode());
        }

        @Test
        void shouldProvideMeaningfulPipelineSetToString() {
            PipelineFactory.PipelineSet pipelineSet = PipelineFactory.createCommonPipelines(config, eventCounter);
            String toString = pipelineSet.toString();

            assertNotNull(toString);
            assertFalse(toString.isEmpty());
        }
    }

    @Nested
    class FactoryBehavior {

        @Test
        void shouldCreateDifferentInstancesOnEachCall() {
            HttpSecurityValidator pipeline1 = PipelineFactory.createUrlPathPipeline(config, eventCounter);
            HttpSecurityValidator pipeline2 = PipelineFactory.createUrlPathPipeline(config, eventCounter);

            assertNotSame(pipeline1, pipeline2);
            assertEquals(pipeline1.getClass(), pipeline2.getClass());
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = ValidURLPathGenerator.class, count = 5)
        void shouldWorkWithValidInputs(String validInput) {
            HttpSecurityValidator pipeline = PipelineFactory.createUrlPathPipeline(config, eventCounter);
            Optional<String> result = pipeline.validate(validInput);
            assertTrue(result.isPresent());
            assertEquals(validInput, result.get());
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = PathTraversalURLGenerator.class, count = 5)
        void shouldRejectMaliciousInputs(String maliciousInput) {
            HttpSecurityValidator pipeline = PipelineFactory.createUrlPathPipeline(config, eventCounter);
            assertThrows(UrlSecurityException.class, () -> pipeline.validate(maliciousInput));
        }


        @Test
        @SuppressWarnings("java:S1612")
        void shouldHandleConcurrentFactoryCallsSafely() {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            HttpSecurityValidator[] results = new HttpSecurityValidator[threadCount];
            boolean[] success = new boolean[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    results[index] = PipelineFactory.createUrlPathPipeline(config, eventCounter);
                    success[index] = results[index] != null;
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                assertDoesNotThrow(() -> thread.join());
            }

            for (int i = 0; i < threadCount; i++) {
                assertTrue(success[i]);
                assertNotNull(results[i]);
            }

            for (int i = 0; i < threadCount; i++) {
                for (int j = i + 1; j < threadCount; j++) {
                    assertNotSame(results[i], results[j]);
                }
            }
        }
    }
}