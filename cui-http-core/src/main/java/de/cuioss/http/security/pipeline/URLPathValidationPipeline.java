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
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.validation.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Objects;

/**
 * Sequential validation pipeline specifically for URL path components.
 *
 * <h3>Validation Sequence</h3>
 * <p>Six stages run in this order; {@code PatternMatchingStage} appears twice:</p>
 * <ol>
 *   <li><strong>Length Validation</strong> - Enforces maximum path length limits</li>
 *   <li><strong>Character Validation</strong> - Validates RFC 3986 path characters</li>
 *   <li><strong>Pattern Matching (pre-decode)</strong> - Detects injection attacks and suspicious
 *       patterns in the raw input</li>
 *   <li><strong>Decoding</strong> - URL decodes with security checks</li>
 *   <li><strong>Normalization</strong> - Path normalization and traversal detection</li>
 *   <li><strong>Pattern Matching (post-normalization)</strong> - Re-runs the same detection on the
 *       decoded and normalized value</li>
 * </ol>
 *
 * <p>Pattern matching runs on both sides of decoding by design. The pre-decode pass catches raw
 * attack literals that decoding would otherwise consume or rewrite before they could be seen; the
 * post-normalization pass catches the patterns that only become visible once percent-decoding and
 * RFC 3986 dot-segment resolution have been applied. Neither pass subsumes the other, so both are
 * required for defence-in-depth against canonicalization attacks.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Sequential Execution</strong> - Each stage processes the output of the previous stage</li>
 *   <li><strong>Early Termination</strong> - Pipeline stops on first security violation</li>
 *   <li><strong>Security First</strong> - Validates before any transformation</li>
 *   <li><strong>Immutable</strong> - Thread-safe pipeline instance</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * SecurityConfiguration config = SecurityConfiguration.defaults();
 * SecurityEventCounter counter = new SecurityEventCounter();
 *
 * URLPathValidationPipeline pipeline = new URLPathValidationPipeline(config, counter);
 *
 * try {
 *     Optional&lt;String&gt; safePath = pipeline.validate("/api/users/123");
 *     safePath.ifPresent(path -&gt; {
 *         // Use the validated path for processing
 *     });
 * } catch (UrlSecurityException e) {
 *     // Handle security violation
 *     log.warn("Path validation failed: {}", e.getMessage());
 * }
 * </pre>
 *
 * <h3>Value Equality</h3>
 * <p>Two {@code URLPathValidationPipeline} instances are equal when their
 * {@link SecurityConfiguration} is equal. The configuration is retained solely to give
 * {@code equals}/{@code hashCode} a value basis and is deliberately <strong>not</strong> exposed
 * via an accessor, nor included in the generated {@code toString()}, so this class's public
 * API and rendered form are both unaffected by holding it.</p>
 *
 * <p>Two fields are deliberately excluded from the basis. The {@link SecurityEventCounter} is
 * mutable shared monitoring state, so including it would make {@code hashCode} change as events
 * are counted and break the {@code hashCode} contract for an instance already used as a hash key.
 * The {@code stages} list is derived deterministically from the configuration and most stages have
 * no value equality of their own, so including it would be redundant with the configuration and
 * would reintroduce identity semantics.</p>
 *
 * Implements: Task P1 from HTTP verification specification
 *
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = false, of = {"config"})
@ToString(callSuper = true)
@Getter
public final class URLPathValidationPipeline extends AbstractValidationPipeline {

    private static final ValidationType VALIDATION_TYPE = ValidationType.URL_PATH;

    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    private final SecurityConfiguration config;

    /**
     * Creates a new URL path validation pipeline with the specified configuration.
     *
     * @param config The security configuration to use
     * @param eventCounter The counter for tracking security events
     * @throws NullPointerException if config or eventCounter is null
     */
    public URLPathValidationPipeline(SecurityConfiguration config,
            SecurityEventCounter eventCounter) {
        super(createStages(config), Objects.requireNonNull(eventCounter, "EventCounter must not be null"));
        this.config = config;
    }

    private static List<HttpSecurityValidator> createStages(SecurityConfiguration config) {
        Objects.requireNonNull(config, "Config must not be null");
        // Create validation stages in the correct order.
        // CRITICAL: PatternMatchingStage runs TWICE - once before decoding/normalization to catch
        // raw traversal literals, and once after normalization to catch what decoding and
        // dot-segment resolution reveal. Neither pass subsumes the other.
        return List.of(
                new LengthValidationStage(config, ValidationType.URL_PATH),
                new CharacterValidationStage(config, ValidationType.URL_PATH),
                new PatternMatchingStage(config, ValidationType.URL_PATH), // Run before decoding/normalization to catch raw traversal patterns
                new DecodingStage(config, ValidationType.URL_PATH),
                new NormalizationStage(config, ValidationType.URL_PATH),
                new PatternMatchingStage(config, ValidationType.URL_PATH)  // Run again after normalization for defense-in-depth against canonicalization attacks
        );
    }

    @Override
    public ValidationType getValidationType() {
        return VALIDATION_TYPE;
    }

}