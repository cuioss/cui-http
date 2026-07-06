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
 * <p>A content type travels as a header value, so this pipeline reports {@link ValidationType#HEADER_VALUE}.
 * It applies {@link AllowBlockListStage#forContentTypes(SecurityConfiguration)}: a value present in
 * {@code blockedContentTypes} is rejected, and - if {@code allowedContentTypes} is non-empty - any
 * value not in it is rejected (empty allow-list = allow-all). Security violations are recorded on the
 * supplied {@link SecurityEventCounter}, consistent with the other pipelines.</p>
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
