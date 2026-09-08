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
import de.cuioss.http.security.validation.AllowBlockListStage;
import de.cuioss.http.security.validation.CharacterValidationStage;
import de.cuioss.http.security.validation.LengthValidationStage;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Objects;

/**
 * Validation pipeline for HTTP {@code Content-Type} values, enforcing the header-value length and
 * character limits and the configured content-type allow/block lists.
 *
 * <h3>Validation Sequence</h3>
 * <ol>
 *   <li><strong>Length Validation</strong> - {@link LengthValidationStage} enforces the configured
 *       header-value length limit</li>
 *   <li><strong>Character Validation</strong> - {@link CharacterValidationStage} enforces the
 *       RFC 7230 header-value character restrictions</li>
 *   <li><strong>Allow/Block List</strong> - {@link AllowBlockListStage#forContentTypes(SecurityConfiguration)}
 *       enforces the configured {@code allowedContentTypes}/{@code blockedContentTypes} lists</li>
 * </ol>
 *
 * <p>This is the same length-then-character-then-list order the header pipelines use, so an
 * unbounded value is cut by the length stage before the character scan walks it. A caller does
 * <em>not</em> need to run the header-value pipeline
 * ({@link PipelineFactory#createHeaderValuePipeline(SecurityConfiguration, SecurityEventCounter)})
 * over a {@code Content-Type} value in addition to this one - this pipeline already applies those
 * two checks.</p>
 *
 * <p>The list check itself: a value present in {@code blockedContentTypes} is rejected, and - if
 * {@code allowedContentTypes} is non-empty - any value not in it is rejected (empty allow-list =
 * allow-all). Matching is on the media type only, so parameters such as {@code ; charset=UTF-8}
 * cannot defeat the lists. Security violations are recorded on the supplied
 * {@link SecurityEventCounter}, consistent with the other pipelines.</p>
 *
 * <h3>Why HEADER_VALUE is the reported type</h3>
 * <p>A content type travels as a header value and there is no dedicated {@link ValidationType}
 * constant for it, so {@link ValidationType#HEADER_VALUE} is the type reported in emitted
 * exceptions and on the event counter. The same constant configures the length and character
 * stages above, so the limits and the character set applied to a {@code Content-Type} value are
 * exactly the header-value ones. This pipeline still differs from the header-value pipeline in its
 * third stage: it enforces the content-type lists, which the header-value pipeline does not.</p>
 *
 * <h3>Value Equality</h3>
 * <p>Two {@code ContentTypeValidationPipeline} instances are equal when their
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
public final class ContentTypeValidationPipeline extends AbstractValidationPipeline {

    private static final ValidationType VALIDATION_TYPE = ValidationType.HEADER_VALUE;

    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    private final SecurityConfiguration config;

    /**
     * Creates a new content-type validation pipeline with the specified configuration.
     *
     * @param config The security configuration to use
     * @param eventCounter The counter for tracking security events
     * @throws NullPointerException if config or eventCounter is null
     */
    public ContentTypeValidationPipeline(SecurityConfiguration config,
            SecurityEventCounter eventCounter) {
        super(createStages(config), Objects.requireNonNull(eventCounter, "EventCounter must not be null"));
        this.config = config;
    }

    private static List<HttpSecurityValidator> createStages(SecurityConfiguration config) {
        Objects.requireNonNull(config, "Config must not be null");
        // Same length-then-character-then-list order the header pipelines use: an unbounded value is
        // cut by the length stage before the character scan walks it.
        return List.of(
                new LengthValidationStage(config, VALIDATION_TYPE),
                new CharacterValidationStage(config, VALIDATION_TYPE),
                AllowBlockListStage.forContentTypes(config));
    }

    @Override
    public ValidationType getValidationType() {
        return VALIDATION_TYPE;
    }
}
