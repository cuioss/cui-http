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
import de.cuioss.http.security.validation.AllowBlockListStage;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Objects;

/**
 * Validation pipeline for HTTP {@code Content-Type} values, enforcing the configured
 * content-type allow/block lists.
 *
 * <h3>Scope</h3>
 * <p><strong>This pipeline performs allow/block-list enforcement ONLY.</strong> It consists of a
 * single stage: {@link AllowBlockListStage#forContentTypes(SecurityConfiguration)}. It applies
 * <em>no</em> length limit and <em>no</em> character validation. A caller that also needs those
 * checks on a {@code Content-Type} value must apply the header-value pipeline
 * ({@link PipelineFactory#createHeaderValuePipeline(SecurityConfiguration, SecurityEventCounter)})
 * to that value separately - this pipeline does not do it for them.</p>
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
 * exceptions and on the event counter. <strong>This reporting choice does not imply the
 * header-value pipeline's stage set</strong> - as stated under Scope, this pipeline runs the
 * allow/block-list stage alone.</p>
 *
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = false, of = {})
@ToString(callSuper = true)
@Getter
public final class ContentTypeValidationPipeline extends AbstractValidationPipeline {

    private static final ValidationType VALIDATION_TYPE = ValidationType.HEADER_VALUE;

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
    }

    private static List<HttpSecurityValidator> createStages(SecurityConfiguration config) {
        Objects.requireNonNull(config, "Config must not be null");
        return List.of(AllowBlockListStage.forContentTypes(config));
    }

    @Override
    public ValidationType getValidationType() {
        return VALIDATION_TYPE;
    }
}
