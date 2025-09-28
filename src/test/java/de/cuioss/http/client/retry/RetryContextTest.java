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
package de.cuioss.http.client.retry;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
class RetryContextTest implements ShouldHandleObjectContracts<RetryContext> {

    @Test
    @DisplayName("Should create initial context with correct values")
    void shouldCreateInitialContextWithCorrectValues() {
        RetryContext context = RetryContext.initial("test-operation");

        assertEquals("test-operation", context.operationName(), "Initial context should preserve operation name");
        assertEquals(1, context.attemptNumber(), "Initial context should start with attempt number 1");
        assertTrue(context.isFirstAttempt(), "Initial context should identify as first attempt");
    }

    @Test
    @DisplayName("Should create next attempt context with increment")
    void shouldCreateNextAttemptContextWithIncrementedAttemptNumber() {
        RetryContext initialContext = RetryContext.initial("test-operation");

        RetryContext nextContext = initialContext.nextAttempt();

        assertEquals("test-operation", nextContext.operationName(), "Next attempt context should preserve operation name");
        assertEquals(2, nextContext.attemptNumber(), "Next attempt context should increment attempt number to 2");
        assertFalse(nextContext.isFirstAttempt(), "Next attempt context should not identify as first attempt");
    }

    @Test
    @DisplayName("Should create multiple next attempt contexts correctly")
    void shouldCreateMultipleNextAttemptContextsCorrectly() {
        RetryContext context1 = RetryContext.initial("test-op");

        RetryContext context2 = context1.nextAttempt();
        RetryContext context3 = context2.nextAttempt();

        assertEquals(1, context1.attemptNumber(), "First context should have attempt number 1");
        assertTrue(context1.isFirstAttempt(), "First context should identify as first attempt");

        assertEquals(2, context2.attemptNumber(), "Second context should have attempt number 2");
        assertFalse(context2.isFirstAttempt(), "Second context should not identify as first attempt");

        assertEquals(3, context3.attemptNumber(), "Third context should have attempt number 3");
        assertFalse(context3.isFirstAttempt(), "Third context should not identify as first attempt");
    }

    @Test
    @DisplayName("Should create next attempt context correctly")
    void shouldCreateNextAttemptContextCorrectly() {
        RetryContext initialContext = RetryContext.initial("test-operation");

        RetryContext nextContext = initialContext.nextAttempt();

        assertEquals("test-operation", nextContext.operationName(), "Context should preserve operation name");
        assertEquals(2, nextContext.attemptNumber(), "Context should increment attempt number");
        assertFalse(nextContext.isFirstAttempt(), "Context should not identify as first attempt after increment");
    }

    @Test
    @DisplayName("Should preserve operation name across attempts")
    void shouldPreserveOperationNameAcrossAttempts() {
        String operationName = "complex-operation-name";
        RetryContext context1 = RetryContext.initial(operationName);
        RetryContext context2 = context1.nextAttempt();
        RetryContext context3 = context2.nextAttempt();

        assertEquals(operationName, context1.operationName(), "Initial context should preserve complex operation name");
        assertEquals(operationName, context2.operationName(), "Second context should preserve complex operation name");
        assertEquals(operationName, context3.operationName(), "Third context should preserve complex operation name");
    }

    @Test
    @DisplayName("Should implement record equality correctly")
    void shouldImplementRecordEqualityCorrectly() {
        RetryContext context1 = new RetryContext("op", 1);
        RetryContext context2 = new RetryContext("op", 1);
        RetryContext context3 = new RetryContext("op", 2);
        RetryContext context4 = new RetryContext("different", 1);

        assertEquals(context1, context2, "Contexts with identical fields should be equal");
        assertNotEquals(context1, context3, "Contexts with different attempt numbers should not be equal");
        assertNotEquals(context1, context4, "Contexts with different operation names should not be equal");
        assertEquals(context1.hashCode(), context2.hashCode(), "Equal contexts should have identical hash codes");
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToStringRepresentation() {
        RetryContext context = new RetryContext("test-op", 3);

        String toString = context.toString();

        assertTrue(toString.contains("test-op"), "toString should include operation name for debugging context");
        assertTrue(toString.contains("3"), "toString should include attempt number for debugging context");
    }

    @Override
    public RetryContext getUnderTest() {
        return new RetryContext("generated-operation", 2);
    }
}