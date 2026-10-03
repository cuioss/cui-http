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
package de.cuioss.http.security.generators;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.PipelineFactory;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link SupportedValidationTypeGenerator}.
 *
 * <p>The generator's defining property is that it emits exactly the {@link ValidationType}
 * constants for which
 * {@link PipelineFactory#createPipeline(ValidationType, SecurityConfiguration, SecurityEventCounter)}
 * builds a pipeline - no supported type is left out, no rejected type is emitted, and no
 * supported type is starved. The expected set is derived by asking the factory about every
 * constant, so the test follows the factory instead of mirroring its switch in a literal.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("SupportedValidationTypeGenerator Contract Tests")
class SupportedValidationTypeGeneratorContractTest {

    /**
     * Far more draws than constants, so that under the pinned seed every type the generator can
     * emit is emitted.
     */
    private static final int DRAWS = 500;

    @Test
    @DisplayName("Should emit exactly the types the factory builds a pipeline for")
    void shouldEmitExactlyTheFactorySupportedTypes() {
        Set<ValidationType> supported = typesTheFactoryAccepts();
        SupportedValidationTypeGenerator generator = new SupportedValidationTypeGenerator();

        Set<ValidationType> emitted = EnumSet.noneOf(ValidationType.class);
        for (int i = 0; i < DRAWS; i++) {
            emitted.add(generator.next());
        }

        assertEquals(supported, emitted,
                "The generator must emit every type the factory supports and no type it rejects");
    }

    @Test
    @DisplayName("Should derive a supported set that separates accepted from rejected types")
    void shouldDeriveADiscriminatingSupportedSet() {
        Set<ValidationType> supported = typesTheFactoryAccepts();

        assertAll("Factory-derived supported set",
                () -> assertFalse(supported.isEmpty(),
                        "The factory must build a pipeline for at least one type"),
                () -> assertNotEquals(EnumSet.allOf(ValidationType.class), supported,
                        "The factory must reject at least one type, otherwise the derivation cannot"
                                + " tell a supported type from an unsupported one"));
    }

    @Test
    @DisplayName("Should emit every supported type with a share no smaller than half the even share")
    void shouldSpreadDrawsAcrossTheSupportedTypes() {
        Set<ValidationType> supported = typesTheFactoryAccepts();
        SupportedValidationTypeGenerator generator = new SupportedValidationTypeGenerator();

        Map<ValidationType, Integer> occurrences = new EnumMap<>(ValidationType.class);
        for (int i = 0; i < DRAWS; i++) {
            occurrences.merge(generator.next(), 1, Integer::sum);
        }

        int minimumShare = DRAWS / (2 * supported.size());
        for (ValidationType type : supported) {
            int count = occurrences.getOrDefault(type, 0);
            assertTrue(count >= minimumShare,
                    () -> type + " was drawn " + count + " times out of " + DRAWS
                            + ", expected at least " + minimumShare);
        }
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(ValidationType.class, new SupportedValidationTypeGenerator().getType(),
                "Generator should return ValidationType.class");
    }

    private static Set<ValidationType> typesTheFactoryAccepts() {
        SecurityConfiguration config = SecurityConfiguration.defaults();
        SecurityEventCounter eventCounter = new SecurityEventCounter();
        Set<ValidationType> supported = EnumSet.noneOf(ValidationType.class);
        for (ValidationType type : ValidationType.values()) {
            if (factoryAccepts(type, config, eventCounter)) {
                supported.add(type);
            }
        }
        return supported;
    }

    private static boolean factoryAccepts(ValidationType type, SecurityConfiguration config,
            SecurityEventCounter eventCounter) {
        try {
            return PipelineFactory.createPipeline(type, config, eventCounter) != null;
        } catch (IllegalArgumentException rejected) {
            return false;
        }
    }
}
