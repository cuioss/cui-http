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

import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for validation pipelines that provides common validation logic.
 *
 * <p>This class implements the standard pipeline validation pattern where multiple
 * validation stages are executed sequentially with early termination on security violations.</p>
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li><strong>Sequential Processing</strong> - Each stage processes the output of the previous stage</li>
 *   <li><strong>Early Termination</strong> - Pipeline stops on first security violation</li>
 *   <li><strong>Event Tracking</strong> - Security violations are tracked via SecurityEventCounter</li>
 *   <li><strong>Exception Enhancement</strong> - Exceptions are re-thrown with correct validation type</li>
 * </ul>
 *
 * <h3>Stage Contract (fail-secure)</h3>
 * <p>The pipeline treats an empty {@link java.util.Optional} returned by a stage as a
 * short-circuit <em>acceptance</em> of the input: iteration stops and the pipeline returns
 * {@code Optional.empty()} without running the remaining stages. This is only safe because
 * every stage returns empty <strong>exclusively for a {@code null} input</strong> (a
 * {@code null} value cannot carry an attack). To preserve the fail-secure guarantee, any
 * custom stage <strong>must never return an empty {@code Optional} for a non-null input</strong>:
 * doing so would silently pass validation for that value rather than rejecting it. A stage
 * that wishes to accept a non-null value must return {@code Optional.of(value)}; a stage that
 * wishes to reject it must throw {@link UrlSecurityException}.</p>
 *
 * @since 1.0
 */
@RequiredArgsConstructor
@Getter
public abstract class AbstractValidationPipeline implements HttpSecurityValidator {

    /**
     * The ordered list of validation stages to execute.
     */
    protected final List<HttpSecurityValidator> stages;

    /**
     * Counter for tracking security events.
     */
    protected final SecurityEventCounter eventCounter;

    /**
     * Returns the validation type handled by this pipeline.
     *
     * @return The validation type for this pipeline
     */
    public abstract ValidationType getValidationType();

    @Override
    public Optional<String> validate(@Nullable String value) throws UrlSecurityException {
        if (value == null) {
            return Optional.empty();
        }

        String result = value;

        // Sequential execution with early termination
        for (HttpSecurityValidator stage : stages) {
            try {
                Optional<String> stageResult = stage.validate(result);
                if (stageResult.isEmpty()) {
                    return Optional.empty();
                }
                result = stageResult.get();
            } catch (UrlSecurityException e) {
                // Track security event
                eventCounter.increment(e.getFailureType());

                // Re-throw with correct validation type, preserving the original
                // stage exception as cause to keep the full exception chain
                throw UrlSecurityException.builder()
                        .failureType(e.getFailureType())
                        .validationType(getValidationType())
                        .originalInput(value) // Use original input, not current result
                        .sanitizedInput(e.getSanitizedInput().orElse(null))
                        .detail(e.getDetail().orElse("Validation failed"))
                        .cause(e)
                        .build();
            }
        }

        return Optional.of(result);
    }
}