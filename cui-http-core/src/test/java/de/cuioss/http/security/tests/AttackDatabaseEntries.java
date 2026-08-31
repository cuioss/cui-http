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
package de.cuioss.http.security.tests;

import de.cuioss.http.security.database.AttackTestCase;
import org.junit.jupiter.params.provider.Arguments;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

/**
 * Shared reflection helper for attack-database claim tests: enumerates every declared
 * {@code public static AttackTestCase} constant on a database class as (constant name, attack
 * payload) argument pairs.
 *
 * <p>Used by {@code @MethodSource}-driven tests that assert a claim the constant's own NAME makes
 * about its payload - e.g. {@code HomographAttackDatabaseTest.shouldCarryTheScriptItsNameClaims}
 * and {@code IPv6AttackDatabaseTest.shouldCarryTheStructuralFeatureItsNameClaims}. Reflection is
 * used deliberately: the constant NAME is the claim under test, and {@link AttackTestCase} does
 * not carry it.</p>
 */
final class AttackDatabaseEntries {

    private AttackDatabaseEntries() {
    }

    static Stream<Arguments> declaredEntries(Class<?> databaseClass) {
        return Stream.of(databaseClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getType() == AttackTestCase.class)
                .map(AttackDatabaseEntries::toNameAndPayload);
    }

    private static Arguments toNameAndPayload(Field field) {
        try {
            return Arguments.of(field.getName(), ((AttackTestCase) field.get(null)).attackString());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read " + field.getName(), e);
        }
    }
}
