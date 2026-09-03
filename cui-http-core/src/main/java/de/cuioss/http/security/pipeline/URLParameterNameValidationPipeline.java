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
 * Sequential validation pipeline specifically for URL parameter <em>names</em> (query keys).
 *
 * <p>Unlike parameter <em>values</em>, parameter names are structural: they must not carry
 * delimiters or line breaks. This pipeline is typed {@link ValidationType#PARAMETER_NAME}, so
 * name-only rules apply - in particular {@code DecodingStage} forbids decoded CR/LF and other
 * structural characters ({@code = &amp; ; space}) for names, closing the encoded-delimiter
 * injection gap that routing names through the value pipeline left open.</p>
 *
 * <h3>Validation Sequence</h3>
 * <ol>
 *   <li><strong>Length Validation</strong> - Enforces the parameter <em>name</em> length limit</li>
 *   <li><strong>Character Validation</strong> - Validates RFC 3986 query characters</li>
 *   <li><strong>Decoding</strong> - URL decodes with security checks (name-strict CR/LF and
 *       delimiter rejection on the decoded output)</li>
 *   <li><strong>Normalization</strong> - Pass-through for {@link ValidationType#PARAMETER_NAME}:
 *       RFC 3986 dot-segment resolution applies to {@link ValidationType#URL_PATH} only, so this
 *       stage does not rewrite parameter names. It is retained here solely for stage-order
 *       symmetry with the parameter-value pipeline; removing it is out of this plan's scope.</li>
 *   <li><strong>Pattern Matching</strong> - Path-traversal detection is unconditional.
 *       Blocked-parameter-name detection fires only when {@code blockedParameterNames} is
 *       non-empty, which it is <em>not</em> by default;
 *       {@link SecurityConfiguration#paranoid()} is the preset that seeds it.</li>
 * </ol>
 *
 * <h3>Value Equality</h3>
 * <p>Two {@code URLParameterNameValidationPipeline} instances are equal when their
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
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = false, of = {"config"})
@ToString(callSuper = true)
@Getter
public final class URLParameterNameValidationPipeline extends AbstractValidationPipeline {

    private static final ValidationType VALIDATION_TYPE = ValidationType.PARAMETER_NAME;

    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    private final SecurityConfiguration config;

    /**
     * Creates a new URL parameter name validation pipeline with the specified configuration.
     *
     * @param config The security configuration to use
     * @param eventCounter The counter for tracking security events
     * @throws NullPointerException if config or eventCounter is null
     */
    public URLParameterNameValidationPipeline(SecurityConfiguration config,
            SecurityEventCounter eventCounter) {
        super(createStages(config), Objects.requireNonNull(eventCounter, "EventCounter must not be null"));
        this.config = config;
    }

    private static List<HttpSecurityValidator> createStages(SecurityConfiguration config) {
        Objects.requireNonNull(config, "Config must not be null");
        // Stages are typed PARAMETER_NAME so that name-only rules (stricter length limit,
        // decoded delimiter/CR-LF rejection, blocked-name detection) are actually applied.
        return List.of(
                new LengthValidationStage(config, ValidationType.PARAMETER_NAME),
                new CharacterValidationStage(config, ValidationType.PARAMETER_NAME),
                new DecodingStage(config, ValidationType.PARAMETER_NAME),
                new NormalizationStage(config, ValidationType.PARAMETER_NAME),
                new PatternMatchingStage(config, ValidationType.PARAMETER_NAME)
        );
    }

    @Override
    public ValidationType getValidationType() {
        return VALIDATION_TYPE;
    }
}
