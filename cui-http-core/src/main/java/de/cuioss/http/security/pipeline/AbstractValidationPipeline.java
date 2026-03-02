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

                // Re-throw with correct validation type
                throw UrlSecurityException.builder()
                        .failureType(e.getFailureType())
                        .validationType(getValidationType())
                        .originalInput(value) // Use original input, not current result
                        .sanitizedInput(e.getSanitizedInput().orElse(null))
                        .detail(e.getDetail().orElse("Validation failed"))
                        .cause(e.getCause())
                        .build();
            }
        }

        return Optional.of(result);
    }
}