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
package de.cuioss.http.client.result;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static de.cuioss.http.client.result.HttpResultState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttpResultState} API constants and behavior.
 */
class HttpResultStateTest {

    @Test
    void cacheStatesContainsCorrectStates() {
        assertEquals(CACHE_STATES, Set.of(CACHED, STALE), "CACHE_STATES should contain CACHED and STALE");
        assertFalse(CACHE_STATES.contains(FRESH), "CACHE_STATES should not contain FRESH");
        assertFalse(CACHE_STATES.contains(RECOVERED), "CACHE_STATES should not contain RECOVERED");
        assertFalse(CACHE_STATES.contains(ERROR), "CACHE_STATES should not contain ERROR");
    }

    @Test
    void successStatesContainsCorrectStates() {
        assertEquals(SUCCESS_STATES, Set.of(FRESH, CACHED, RECOVERED), "SUCCESS_STATES should contain FRESH, CACHED, and RECOVERED");
        assertFalse(SUCCESS_STATES.contains(STALE), "SUCCESS_STATES should not contain STALE");
        assertFalse(SUCCESS_STATES.contains(ERROR), "SUCCESS_STATES should not contain ERROR");
    }

    @Test
    void degradedStatesContainsCorrectStates() {
        assertEquals(DEGRADED_STATES, Set.of(STALE, RECOVERED), "DEGRADED_STATES should contain STALE and RECOVERED");
        assertFalse(DEGRADED_STATES.contains(FRESH), "DEGRADED_STATES should not contain FRESH");
        assertFalse(DEGRADED_STATES.contains(CACHED), "DEGRADED_STATES should not contain CACHED");
        assertFalse(DEGRADED_STATES.contains(ERROR), "DEGRADED_STATES should not contain ERROR");
    }

    @Test
    void mustBeHandledStatesContainsCorrectStates() {
        assertEquals(MUST_BE_HANDLED, Set.of(ERROR, STALE), "MUST_BE_HANDLED should contain ERROR and STALE");
        assertFalse(MUST_BE_HANDLED.contains(FRESH), "MUST_BE_HANDLED should not contain FRESH");
        assertFalse(MUST_BE_HANDLED.contains(CACHED), "MUST_BE_HANDLED should not contain CACHED");
        assertFalse(MUST_BE_HANDLED.contains(RECOVERED), "MUST_BE_HANDLED should not contain RECOVERED");
    }

    @Test
    void cacheStatesIsImmutable() {
        Set<HttpResultState> cacheStates = CACHE_STATES;
        assertInstanceOf(Set.class, cacheStates, "CACHE_STATES should be a Set instance");

        assertThrows(UnsupportedOperationException.class, () ->
                cacheStates.add(FRESH), "CACHE_STATES should be immutable");
    }

    @Test
    void successStatesIsImmutable() {
        Set<HttpResultState> successStates = SUCCESS_STATES;
        assertInstanceOf(Set.class, successStates, "SUCCESS_STATES should be a Set instance");

        assertThrows(UnsupportedOperationException.class, () ->
                successStates.add(ERROR), "SUCCESS_STATES should be immutable");
    }

    @Test
    void degradedStatesIsImmutable() {
        Set<HttpResultState> degradedStates = DEGRADED_STATES;
        assertInstanceOf(Set.class, degradedStates, "DEGRADED_STATES should be a Set instance");

        assertThrows(UnsupportedOperationException.class, () ->
                degradedStates.add(FRESH), "DEGRADED_STATES should be immutable");
    }

    @Test
    void mustBeHandledStatesIsImmutable() {
        Set<HttpResultState> mustBeHandledStates = MUST_BE_HANDLED;
        assertInstanceOf(Set.class, mustBeHandledStates, "MUST_BE_HANDLED should be a Set instance");

        assertThrows(UnsupportedOperationException.class, () ->
                mustBeHandledStates.add(CACHED), "MUST_BE_HANDLED should be immutable");
    }

    @Test
    void stateCollectionsHaveCorrectSemantics() {
        // FRESH should be successful but not cached or degraded
        assertTrue(SUCCESS_STATES.contains(FRESH), "FRESH should be in SUCCESS_STATES");
        assertFalse(CACHE_STATES.contains(FRESH), "FRESH should not be in CACHE_STATES");
        assertFalse(DEGRADED_STATES.contains(FRESH), "FRESH should not be in DEGRADED_STATES");
        assertFalse(MUST_BE_HANDLED.contains(FRESH), "FRESH should not be in MUST_BE_HANDLED");

        // CACHED should be successful and cached but not degraded
        assertTrue(SUCCESS_STATES.contains(CACHED), "CACHED should be in SUCCESS_STATES");
        assertTrue(CACHE_STATES.contains(CACHED), "CACHED should be in CACHE_STATES");
        assertFalse(DEGRADED_STATES.contains(CACHED), "CACHED should not be in DEGRADED_STATES");
        assertFalse(MUST_BE_HANDLED.contains(CACHED), "CACHED should not be in MUST_BE_HANDLED");

        // STALE should be cached and degraded and must be handled
        assertFalse(SUCCESS_STATES.contains(STALE), "STALE should not be in SUCCESS_STATES");
        assertTrue(CACHE_STATES.contains(STALE), "STALE should be in CACHE_STATES");
        assertTrue(DEGRADED_STATES.contains(STALE), "STALE should be in DEGRADED_STATES");
        assertTrue(MUST_BE_HANDLED.contains(STALE), "STALE should be in MUST_BE_HANDLED");

        // RECOVERED should be successful and degraded but not cached
        assertTrue(SUCCESS_STATES.contains(RECOVERED), "RECOVERED should be in SUCCESS_STATES");
        assertFalse(CACHE_STATES.contains(RECOVERED), "RECOVERED should not be in CACHE_STATES");
        assertTrue(DEGRADED_STATES.contains(RECOVERED), "RECOVERED should be in DEGRADED_STATES");
        assertFalse(MUST_BE_HANDLED.contains(RECOVERED), "RECOVERED should not be in MUST_BE_HANDLED");

        // ERROR should not be in any positive states but must be handled
        assertFalse(SUCCESS_STATES.contains(ERROR), "ERROR should not be in SUCCESS_STATES");
        assertFalse(CACHE_STATES.contains(ERROR), "ERROR should not be in CACHE_STATES");
        assertFalse(DEGRADED_STATES.contains(ERROR), "ERROR should not be in DEGRADED_STATES");
        assertTrue(MUST_BE_HANDLED.contains(ERROR), "ERROR should be in MUST_BE_HANDLED");
    }

    @Test
    void allStatesAreCategorized() {
        // Every enum value should appear in at least one category
        for (HttpResultState state : HttpResultState.values()) {
            boolean isInAtLeastOneCategory = SUCCESS_STATES.contains(state)
                    || CACHE_STATES.contains(state)
                    || DEGRADED_STATES.contains(state)
                    || MUST_BE_HANDLED.contains(state);

            assertTrue(isInAtLeastOneCategory,
                    "State " + state + " should be in at least one category");
        }
    }
}