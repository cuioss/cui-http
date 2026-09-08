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

import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the stage-exception handling {@link AbstractValidationPipeline} documents.
 *
 * <p>The stage loop catches {@link UrlSecurityException} and nothing else. That is a deliberate
 * partition rather than an oversight, so both halves of it are pinned here as a matched pair: a
 * stage failing with {@code UrlSecurityException} is a security verdict - it is caught, counted on
 * the {@link SecurityEventCounter} and re-thrown carrying the <em>pipeline's</em> validation type -
 * while a stage failing with any other exception is a defect in the stage itself, which propagates
 * unwrapped and is not counted as a security event. Counting a stage defect would inflate the
 * security metrics with bugs; swallowing it would hide them entirely.</p>
 *
 * <p>Both cases are asserted against exact values - the exact exception class and the exact counter
 * total before and after - because an assertion that merely tolerates "some exception" or "a
 * non-zero count" cannot tell the two halves of the partition apart.</p>
 */
@DisplayName("AbstractValidationPipeline stage-exception handling")
class AbstractValidationPipelineTest {

    /**
     * The type the pipeline under test reports. Deliberately different from
     * {@link #STAGE_VALIDATION_TYPE} so the re-throw can be shown to carry the pipeline's own type
     * rather than passing the stage's through.
     */
    private static final ValidationType PIPELINE_VALIDATION_TYPE = ValidationType.URL_PATH;

    private static final ValidationType STAGE_VALIDATION_TYPE = ValidationType.PARAMETER_VALUE;

    private static final String INPUT = "/api/users";

    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
    }

    @Test
    @DisplayName("a stage failing with UrlSecurityException is caught, counted and re-thrown with the pipeline's type")
    void shouldCatchCountAndRethrowSecurityException() {
        UrlSecurityException stageFailure = UrlSecurityException.builder()
                .failureType(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED)
                .validationType(STAGE_VALIDATION_TYPE)
                .originalInput(INPUT)
                .detail("stage rejected the input")
                .build();
        TestPipeline pipeline = pipelineWith(value -> {
            throw stageFailure;
        });
        assertEquals(0L, eventCounter.getTotalCount(), "The counter must start empty");

        UrlSecurityException thrown = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(INPUT));

        assertAll("A security verdict is counted and re-typed to the pipeline",
                () -> assertEquals(1L, eventCounter.getTotalCount(),
                        "A caught security violation must be counted exactly once"),
                () -> assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED, thrown.getFailureType(),
                        "The stage's failure type must be preserved"),
                () -> assertEquals(PIPELINE_VALIDATION_TYPE, thrown.getValidationType(),
                        "The re-thrown exception must carry the pipeline's validation type, not the stage's"),
                () -> assertSame(stageFailure, thrown.getCause(),
                        "The originating stage exception must be preserved as cause"));
    }

    @Test
    @DisplayName("a stage failing with any other exception propagates unwrapped and is not counted")
    void shouldPropagateNonSecurityStageExceptionUnwrapped() {
        TestPipeline pipeline = pipelineWith(value -> {
            throw new IllegalStateException("stage is misconfigured");
        });
        assertEquals(0L, eventCounter.getTotalCount(), "The counter must start empty");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> pipeline.validate(INPUT));

        assertAll("A stage defect is neither wrapped nor counted",
                () -> assertSame(IllegalStateException.class, thrown.getClass(),
                        "The stage's exception must propagate as its exact type, not wrapped in a UrlSecurityException"),
                () -> assertEquals("stage is misconfigured", thrown.getMessage(),
                        "The stage's exception must propagate unaltered"),
                () -> assertEquals(0L, eventCounter.getTotalCount(),
                        "A stage defect is not a security violation and must not be counted as one"));
    }

    private TestPipeline pipelineWith(HttpSecurityValidator stage) {
        return new TestPipeline(List.of(stage), eventCounter);
    }

    /**
     * Minimal concrete pipeline: it contributes only the validation type, so every behaviour
     * asserted above is the base class's own.
     */
    private static final class TestPipeline extends AbstractValidationPipeline {

        private TestPipeline(List<HttpSecurityValidator> stages, SecurityEventCounter eventCounter) {
            super(stages, eventCounter);
        }

        @Override
        public ValidationType getValidationType() {
            return PIPELINE_VALIDATION_TYPE;
        }
    }
}
