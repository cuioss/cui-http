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
import de.cuioss.http.security.config.SecurityDefaults;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pipeline-level behavioural contract of the preset re-tiering.
 *
 * <p>The application-layer content judgement (Unix filesystem paths, sensitive filenames,
 * suspicious parameter names) no longer lives in {@link SecurityConfiguration#strict()}; it is
 * reachable only through {@link SecurityConfiguration#paranoid()} or an explicitly seeded
 * block-list. The structural protocol-handler scheme check stays in {@code strict()}, and both
 * surviving matchers are precise rather than bare substring containment.</p>
 *
 * <p>Four parts are pinned here, each driven through a {@link PipelineFactory}-built pipeline so
 * every assertion reads from a caller's angle:</p>
 * <ol>
 *   <li>{@code strict()} accepts the twelve realistic API paths the defect rejected;</li>
 *   <li>{@code strict()} still rejects a genuinely scheme-bearing path, and accepts one that
 *       merely carries a scheme literal mid-value;</li>
 *   <li>{@code paranoid()} reproduces the content detection, in either case spelling, without
 *       reintroducing the substring false positives;</li>
 *   <li>the configurable block-list is reachable independently of the preset.</li>
 * </ol>
 */
@DisplayName("Preset tiering behaviour")
class PresetTieringBehaviourTest {

    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
    }

    private HttpSecurityValidator pathPipeline(SecurityConfiguration config) {
        return PipelineFactory.createUrlPathPipeline(config, eventCounter);
    }

    private HttpSecurityValidator parameterNamePipeline(SecurityConfiguration config) {
        return PipelineFactory.createParameterNamePipeline(config, eventCounter);
    }

    @Nested
    @DisplayName("(a) strict() is usable against a realistic REST API vocabulary")
    class StrictAcceptsRealisticApiPaths {

        /**
         * The twelve paths measured against the pre-change {@code strict()}, every one of which
         * was rejected by the substring matcher over the application-layer literals.
         */
        @ParameterizedTest
        @DisplayName("strict() accepts the measured-evidence path")
        @ValueSource(strings = {
                "/api/v1/root/children",
                "/deployments/dev/status",
                "/tenants/acme/dev/config",
                "/config/etc/settings",
                "/hardware/sys/temperature",
                "/wiki/boot/procedure",
                "/api/profile:read",
                "/scopes/user.profile:write",
                "/v1/data:export",
                "/api/metadata:refresh",
                "/documents/document.envelope",
                "/reports/2024/revenue.env"
        })
        void strictAcceptsMeasuredEvidencePath(String path) {
            Optional<String> result = pathPipeline(SecurityConfiguration.strict()).validate(path);

            assertEquals(path, result.orElseThrow(),
                    "strict() must be usable as a production posture against a realistic API vocabulary");
        }
    }

    @Nested
    @DisplayName("(b) strict() keeps the structural protocol-handler detection")
    class StrictKeepsSchemeDetection {

        @ParameterizedTest
        @DisplayName("strict() rejects a scheme-bearing path")
        @ValueSource(strings = {"javascript:alert(1)", "data:text/html,x"})
        void strictRejectsSchemeBearingPath(String path) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pathPipeline(SecurityConfiguration.strict()).validate(path),
                    "a path component that IS a scheme-bearing URI is structurally invalid");

            assertEquals(UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED, exception.getFailureType());
        }

        @Test
        @DisplayName("strict() accepts a path merely carrying a scheme literal mid-value")
        void strictAcceptsSchemeLiteralMidValue() {
            Optional<String> result = pathPipeline(SecurityConfiguration.strict()).validate("/v1/data:export");

            assertEquals("/v1/data:export", result.orElseThrow(),
                    "the scheme check is anchored to the start of the whole value, not to each segment");
        }
    }

    @Nested
    @DisplayName("(c) paranoid() reproduces the content detection without the false positives")
    class ParanoidReproducesContentDetection {

        @ParameterizedTest
        @DisplayName("paranoid() rejects a sensitive path segment in either case")
        @ValueSource(strings = {
                "/config/etc/settings",
                "/CONFIG/ETC/settings",
                "/api/v1/root/children",
                "/API/V1/ROOT/children"
        })
        void paranoidRejectsSensitiveSegmentRegardlessOfCase(String path) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pathPipeline(SecurityConfiguration.paranoid()).validate(path),
                    "SENSITIVE_PATH_PATTERNS is all-lowercase, so the preset must compare case-insensitively");

            assertEquals(UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED, exception.getFailureType());
        }

        @Test
        @DisplayName("paranoid() rejects a blocked parameter name")
        void paranoidRejectsBlockedParameterName() {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> parameterNamePipeline(SecurityConfiguration.paranoid()).validate("file"));

            assertEquals(UrlSecurityFailureType.SUSPICIOUS_PARAMETER_NAME, exception.getFailureType());
        }

        @Test
        @DisplayName("paranoid() still accepts a segment that merely embeds a blocked literal")
        void paranoidAcceptsSegmentEmbeddingABlockedLiteral() {
            Optional<String> result = pathPipeline(SecurityConfiguration.paranoid())
                    .validate("/documents/document.envelope");

            assertEquals("/documents/document.envelope", result.orElseThrow(),
                    "block-list matching is whole-segment equality, never substring containment");
        }
    }

    @Nested
    @DisplayName("(d) the block-list is reachable independently of the preset")
    class BlockListIsPresetIndependent {

        @Test
        @DisplayName("a block-list seeded on defaults() rejects the sensitive path")
        void seededBlockListOnDefaultsRejectsSensitivePath() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .blockedPathPatterns(Set.of("/etc/"))
                    .build();

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pathPipeline(config).validate("/config/etc/settings"),
                    "the list is gated on its own non-emptiness, not on failOnSuspiciousPatterns");

            assertEquals(UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED, exception.getFailureType());
        }

        @Test
        @DisplayName("an empty block-list is a no-op even under strict()")
        void emptyBlockListIsANoOp() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .failOnSuspiciousPatterns(true)
                    .blockedPathPatterns(Set.of())
                    .build();

            Optional<String> result = pathPipeline(config).validate("/config/etc/settings");

            assertEquals("/config/etc/settings", result.orElseThrow());
        }

        @Test
        @DisplayName("seeding from SecurityDefaults reproduces the paranoid() path detection")
        void seedingFromDefaultsReproducesParanoidDetection() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .blockedPathPatterns(SecurityDefaults.SENSITIVE_PATH_PATTERNS)
                    .build();

            assertThrows(UrlSecurityException.class,
                    () -> pathPipeline(config).validate("/hardware/sys/temperature"));
        }
    }
}
