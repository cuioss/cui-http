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
package de.cuioss.http.security.validation;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.PipelineFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for header-name / content-type allow-block list enforcement (F-08).
 */
@DisplayName("AllowBlockListStage (F-08)")
class AllowBlockListStageTest {

    @Test
    @DisplayName("Empty lists allow everything")
    void shouldAllowAllWhenEmpty() {
        var stage = new AllowBlockListStage(Set.of(), Set.of(), ValidationType.HEADER_NAME);
        assertEquals(Optional.of("X-Anything"), stage.validate("X-Anything"));
        assertEquals(Optional.empty(), stage.validate(null));
        assertEquals(Optional.of(""), stage.validate(""));
    }

    @Test
    @DisplayName("Block-list rejects case-insensitively")
    void shouldRejectBlocked() {
        var stage = new AllowBlockListStage(Set.of(), Set.of("X-Debug"), ValidationType.HEADER_NAME);
        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate("x-debug"));
        assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        assertTrue(exception.getDetail().orElse("").contains("block-listed"));
        // A non-blocked value passes.
        assertEquals(Optional.of("X-Allowed"), stage.validate("X-Allowed"));
    }

    @Test
    @DisplayName("Non-empty allow-list rejects values not in it")
    void shouldEnforceAllowList() {
        var stage = new AllowBlockListStage(Set.of("Accept", "Content-Type"), Set.of(), ValidationType.HEADER_NAME);
        assertEquals(Optional.of("accept"), stage.validate("accept")); // case-insensitive match
        var exception = assertThrows(UrlSecurityException.class, () -> stage.validate("X-Custom"));
        assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        assertTrue(exception.getDetail().orElse("").contains("allow-list"));
    }

    @Test
    @DisplayName("Block-list takes precedence over allow-list")
    void shouldPreferBlockOverAllow() {
        var stage = new AllowBlockListStage(Set.of("X-Debug"), Set.of("X-Debug"), ValidationType.HEADER_NAME);
        assertThrows(UrlSecurityException.class, () -> stage.validate("X-Debug"));
    }

    @Test
    @DisplayName("Header-name pipeline enforces the configured block-list")
    void shouldEnforceInHeaderNamePipeline() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .blockedHeaderNames(Set.of("X-Debug"))
                .build();
        SecurityEventCounter counter = new SecurityEventCounter();
        HttpSecurityValidator pipeline = PipelineFactory.createHeaderNamePipeline(config, counter);

        assertThrows(UrlSecurityException.class, () -> pipeline.validate("X-Debug"));
        assertTrue(counter.getCount(UrlSecurityFailureType.INVALID_INPUT) >= 1);
        assertTrue(pipeline.validate("Accept").isPresent());
    }

    @Test
    @DisplayName("Content-type pipeline enforces the configured allow-list")
    void shouldEnforceInContentTypePipeline() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .allowedContentTypes(Set.of("application/json"))
                .build();
        SecurityEventCounter counter = new SecurityEventCounter();
        HttpSecurityValidator pipeline = PipelineFactory.createContentTypePipeline(config, counter);

        assertTrue(pipeline.validate("application/json").isPresent());
        assertThrows(UrlSecurityException.class, () -> pipeline.validate("application/octet-stream"));
    }

    @Test
    @DisplayName("Content-type matching ignores parameters (charset, boundary)")
    void shouldMatchContentTypeMediaTypeIgnoringParameters() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .allowedContentTypes(Set.of("application/json"))
                .blockedContentTypes(Set.of("application/octet-stream"))
                .build();
        SecurityEventCounter counter = new SecurityEventCounter();
        HttpSecurityValidator pipeline = PipelineFactory.createContentTypePipeline(config, counter);

        // Allowed media type with parameters must still pass (was a false positive before the fix).
        assertTrue(pipeline.validate("application/json; charset=UTF-8").isPresent());
        assertTrue(pipeline.validate("application/json;charset=utf-8").isPresent());
        // Blocked media type with parameters must still be rejected.
        assertThrows(UrlSecurityException.class,
                () -> pipeline.validate("application/octet-stream; name=evil.bin"));
    }

    @Test
    @DisplayName("Null arguments are rejected")
    void shouldRejectNullArguments() {
        assertThrows(NullPointerException.class,
                () -> new AllowBlockListStage(null, Set.of(), ValidationType.HEADER_NAME));
        assertThrows(NullPointerException.class,
                () -> new AllowBlockListStage(Set.of(), null, ValidationType.HEADER_NAME));
        assertThrows(NullPointerException.class,
                () -> new AllowBlockListStage(Set.of(), Set.of(), null));
    }
}
