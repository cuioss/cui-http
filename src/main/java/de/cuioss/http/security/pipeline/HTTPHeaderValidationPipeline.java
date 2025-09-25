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
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.validation.CharacterValidationStage;
import de.cuioss.http.security.validation.LengthValidationStage;
import de.cuioss.http.security.validation.NormalizationStage;
import de.cuioss.http.security.validation.PatternMatchingStage;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Objects;

/**
 * Sequential validation pipeline specifically for HTTP header components.
 *
 * <h3>Validation Sequence</h3>
 * <ol>
 *   <li><strong>Length Validation</strong> - Enforces maximum header length limits</li>
 *   <li><strong>Character Validation</strong> - Validates RFC 7230 header characters</li>
 *   <li><strong>Normalization</strong> - Header normalization and security checks</li>
 *   <li><strong>Pattern Matching</strong> - Detects injection attacks and suspicious patterns</li>
 * </ol>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Sequential Execution</strong> - Each stage processes the output of the previous stage</li>
 *   <li><strong>Early Termination</strong> - Pipeline stops on first security violation</li>
 *   <li><strong>Security First</strong> - Validates before any transformation</li>
 *   <li><strong>Immutable</strong> - Thread-safe pipeline instance</li>
 * </ul>
 *
 * <h3>HTTP Header Security</h3>
 * <ul>
 *   <li><strong>Header Injection Prevention</strong> - Detects CRLF injection attempts</li>
 *   <li><strong>RFC 7230 Compliance</strong> - Enforces HTTP header character restrictions</li>
 *   <li><strong>Length Limits</strong> - Prevents header-based DoS attacks</li>
 *   <li><strong>Pattern Detection</strong> - Identifies malicious header values</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * SecurityConfiguration config = SecurityConfiguration.defaults();
 * SecurityEventCounter counter = new SecurityEventCounter();
 *
 * HTTPHeaderValidationPipeline pipeline = new HTTPHeaderValidationPipeline(config, counter);
 *
 * try {
 *     String safeHeader = pipeline.validate("Bearer eyJhbGciOiJIUzI1NiJ9...");
 *     // Use safeHeader for processing
 * } catch (UrlSecurityException e) {
 *     // Handle security violation
 *     log.warn("Header validation failed: {}", e.getMessage());
 * }
 * </pre>
 *
 * Implements: Task P3 from HTTP verification specification
 *
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = false, of = {"validationType"})
@ToString(callSuper = true)
@Getter
public final class HTTPHeaderValidationPipeline extends AbstractValidationPipeline {

    private final ValidationType validationType;

    /**
     * Creates a new HTTP header validation pipeline with the specified configuration.
     * The pipeline can be configured for either header names or header values.
     *
     * @param config The security configuration to use
     * @param eventCounter The counter for tracking security events
     * @param validationType The type of header component to validate (HEADER_NAME or HEADER_VALUE)
     * @throws NullPointerException if config, eventCounter, or validationType is null
     * @throws IllegalArgumentException if validationType is not a header type
     */
    public HTTPHeaderValidationPipeline(SecurityConfiguration config,
            SecurityEventCounter eventCounter,
            ValidationType validationType) {
        super(createStages(config, validationType), Objects.requireNonNull(eventCounter, "EventCounter must not be null"));
        Objects.requireNonNull(validationType, "ValidationType must not be null");

        if (!validationType.isHeader()) {
            throw new IllegalArgumentException("ValidationType must be a header type, got: " + validationType);
        }

        this.validationType = validationType;
    }

    private static List<HttpSecurityValidator> createStages(SecurityConfiguration config, ValidationType validationType) {
        Objects.requireNonNull(config, "Config must not be null");
        Objects.requireNonNull(validationType, "ValidationType must not be null");

        // Create validation stages in the correct order for HTTP headers
        // Note: Headers typically don't need URL decoding, so we skip DecodingStage
        return List.of(
                new LengthValidationStage(config, validationType),
                new CharacterValidationStage(config, validationType),
                new NormalizationStage(config, validationType),
                new PatternMatchingStage(config, validationType)
        );
    }

    @Override
    public ValidationType getValidationType() {
        return validationType;
    }

}