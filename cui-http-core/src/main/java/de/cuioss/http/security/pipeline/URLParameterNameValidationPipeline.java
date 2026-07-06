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
import de.cuioss.http.security.validation.*;
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
 *   <li><strong>Normalization</strong> - Normalization and security checks</li>
 *   <li><strong>Pattern Matching</strong> - Detects suspicious parameter names and injection attacks</li>
 * </ol>
 *
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = false, of = {})
@ToString(callSuper = true)
@Getter
public final class URLParameterNameValidationPipeline extends AbstractValidationPipeline {

    private static final ValidationType VALIDATION_TYPE = ValidationType.PARAMETER_NAME;

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
    }

    private static List<HttpSecurityValidator> createStages(SecurityConfiguration config) {
        Objects.requireNonNull(config, "Config must not be null");
        // Stages are typed PARAMETER_NAME so that name-only rules (stricter length limit,
        // decoded delimiter/CR-LF rejection, suspicious-name detection) are actually applied.
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
