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
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior tests for {@link ContentTypeValidationPipeline}.
 *
 * <p>Covers the accept/reject behavior of the wrapped
 * {@link de.cuioss.http.security.validation.AllowBlockListStage} in content-type mode:
 * block-list rejection and precedence, allow-list restriction (with the empty allow-list =
 * allow-all rule), media-type-only matching that ignores parameters, case-insensitive
 * comparison, and correct exception metadata plus security-event recording.</p>
 */
@DisplayName("ContentTypeValidationPipeline behavior")
class ContentTypeValidationPipelineTest {

    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
    }

    private ContentTypeValidationPipeline pipeline(SecurityConfiguration config) {
        return new ContentTypeValidationPipeline(config, eventCounter);
    }

    @Test
    @DisplayName("reports HEADER_VALUE as its validation type")
    void reportsHeaderValueType() {
        assertEquals(ValidationType.HEADER_VALUE,
                pipeline(SecurityConfiguration.defaults()).getValidationType());
    }

    @Nested
    @DisplayName("block-list")
    class BlockList {

        @Test
        @DisplayName("rejects a block-listed content type with INVALID_INPUT and records an event")
        void rejectsBlockListed() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .blockedContentTypes(Set.of("application/x-msdownload"))
                    .build();
            ContentTypeValidationPipeline pipeline = pipeline(config);
            long before = eventCounter.getTotalCount();

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate("application/x-msdownload"));

            assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
            assertEquals(ValidationType.HEADER_VALUE, exception.getValidationType());
            assertEquals("application/x-msdownload", exception.getOriginalInput());
            assertTrue(eventCounter.getTotalCount() > before, "Security event should be recorded");
        }

        @Test
        @DisplayName("takes precedence over the allow-list")
        void blockWinsOverAllow() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowedContentTypes(Set.of("text/plain"))
                    .blockedContentTypes(Set.of("text/plain"))
                    .build();
            ContentTypeValidationPipeline pipeline = pipeline(config);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate("text/plain"));
            assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        }
    }

    @Nested
    @DisplayName("allow-list")
    class AllowList {

        @Test
        @DisplayName("rejects a value absent from a non-empty allow-list")
        void rejectsNotAllowed() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowedContentTypes(Set.of("application/json"))
                    .build();
            ContentTypeValidationPipeline pipeline = pipeline(config);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate("text/html"));
            assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
            assertEquals("text/html", exception.getOriginalInput());
        }

        @Test
        @DisplayName("accepts a value present in the allow-list and returns it unchanged")
        void acceptsAllowed() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowedContentTypes(Set.of("application/json"))
                    .build();
            ContentTypeValidationPipeline pipeline = pipeline(config);

            assertEquals(Optional.of("application/json"), pipeline.validate("application/json"));
        }

        @Test
        @DisplayName("an empty allow-list imposes no restriction (allow-all)")
        void emptyAllowListAllowsAll() {
            ContentTypeValidationPipeline pipeline = pipeline(SecurityConfiguration.defaults());

            assertEquals(Optional.of("anything/whatever"), pipeline.validate("anything/whatever"));
        }
    }

    @Nested
    @DisplayName("matching rules")
    class MatchingRules {

        @Test
        @DisplayName("matches on media type only, ignoring parameters like charset")
        void mediaTypeOnlyIgnoresParameters() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowedContentTypes(Set.of("application/json"))
                    .build();
            ContentTypeValidationPipeline pipeline = pipeline(config);

            // Same media type with a parameter must still be accepted.
            assertEquals(Optional.of("application/json; charset=UTF-8"),
                    pipeline.validate("application/json; charset=UTF-8"));
        }

        @Test
        @DisplayName("blocks on media type only, so parameters cannot defeat the block-list")
        void mediaTypeOnlyBlockCannotBeBypassedWithParameters() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .blockedContentTypes(Set.of("text/html"))
                    .build();
            ContentTypeValidationPipeline pipeline = pipeline(config);

            assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate("text/html; charset=UTF-8"));
        }

        @Test
        @DisplayName("comparison is case-insensitive")
        void caseInsensitive() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .blockedContentTypes(Set.of("application/json"))
                    .build();
            ContentTypeValidationPipeline pipeline = pipeline(config);

            assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate("APPLICATION/JSON"));
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("null input yields an empty Optional without throwing")
        void nullInput() {
            ContentTypeValidationPipeline pipeline = pipeline(SecurityConfiguration.defaults());
            assertEquals(Optional.empty(), pipeline.validate(null));
        }

        @Test
        @DisplayName("empty input is accepted and returned unchanged")
        void emptyInput() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowedContentTypes(Set.of("application/json"))
                    .build();
            ContentTypeValidationPipeline pipeline = pipeline(config);
            assertEquals(Optional.of(""), pipeline.validate(""));
        }
    }
}
