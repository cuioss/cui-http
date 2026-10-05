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
package de.cuioss.http;

import de.cuioss.http.client.HttpLogMessages;
import de.cuioss.http.forwarded.ForwardedLogMessages;
import de.cuioss.tools.logging.LogRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the hand-written log-message documentation against the {@code LogMessages} classes it
 * describes, so the documentation cannot drift from the code-defined set of log records.
 *
 * <p>The expected identifiers, levels and message templates are derived reflectively from the
 * {@link LogRecord} constants of {@link HttpLogMessages} and {@link ForwardedLogMessages}; there is
 * no hand-maintained list in this test. The checks are:</p>
 * <ol>
 *   <li>{@code doc/LogMessages.adoc} has exactly one table row per declared record, under the
 *   section of its class and the table of its level, with the record's template as the message
 *   cell - and names no {@code HTTP-NNN} identifier the code does not declare;</li>
 *   <li>the identifier-range summary of each class in the overview of {@code doc/LogMessages.adoc}
 *   matches the declared identifiers;</li>
 *   <li>the {@code ForwardedLogMessages} catalogue in {@code doc/forwarded-header-resolution.adoc}
 *   names every declared WARN identifier, states the declared range, and the document names no
 *   identifier the code does not declare.</li>
 * </ol>
 */
@DisplayName("Log-message documentation matches the LogMessages classes")
class LogMessagesDocumentationTest {

    private static final String LOG_MESSAGES_DOC = "LogMessages.adoc";
    private static final String FORWARDED_DOC = "forwarded-header-resolution.adoc";
    private static final String EXPECTED_PREFIX = "HTTP";

    private static final Pattern IDENTIFIER = Pattern.compile("HTTP-(\\d+)");
    private static final Pattern TABLE_ID_ROW = Pattern.compile("^\\|HTTP-(\\d+)\\s*$");
    private static final String EN_DASH = "–";

    private static final List<Class<?>> LOG_MESSAGES_CLASSES =
            List.of(HttpLogMessages.class, ForwardedLogMessages.class);

    /** class simple name -> level (nested class name) -> identifier -> template */
    private static Map<String, Map<String, SortedMap<Integer, String>>> declared;
    private static SortedSet<Integer> allDeclaredIds;
    private static List<String> logMessagesDoc;
    private static List<String> forwardedDoc;

    @BeforeAll
    static void loadCodeAndDocs() throws IOException, IllegalAccessException {
        declared = new LinkedHashMap<>();
        allDeclaredIds = new TreeSet<>();
        for (Class<?> logMessagesClass : LOG_MESSAGES_CLASSES) {
            declared.put(logMessagesClass.getSimpleName(), collectRecords(logMessagesClass));
        }
        declared.values().forEach(levels -> levels.values().forEach(ids -> allDeclaredIds.addAll(ids.keySet())));
        assertFalse(allDeclaredIds.isEmpty(), "Reflection found no LogRecord constants - the derivation is broken");

        Path docDir = locateDocDirectory();
        logMessagesDoc = Files.readAllLines(docDir.resolve(LOG_MESSAGES_DOC));
        forwardedDoc = Files.readAllLines(docDir.resolve(FORWARDED_DOC));
    }

    @Test
    @DisplayName("LogMessages.adoc tables list exactly the declared records, per class and level, with their templates")
    void logMessagesTablesShouldMatchDeclaredRecords() {
        Map<String, Map<String, SortedMap<Integer, String>>> documented = parseLogMessagesTables();

        for (var classEntry : declared.entrySet()) {
            String className = classEntry.getKey();
            Map<String, SortedMap<Integer, String>> documentedLevels = documented.getOrDefault(className, Map.of());
            assertEquals(classEntry.getValue().keySet(), documentedLevels.keySet(),
                    "Level tables documented for %s in %s differ from its declared levels"
                            .formatted(className, LOG_MESSAGES_DOC));
            for (var levelEntry : classEntry.getValue().entrySet()) {
                String level = levelEntry.getKey();
                SortedMap<Integer, String> documentedRows = documentedLevels.get(level);
                assertEquals(levelEntry.getValue().keySet(), documentedRows.keySet(),
                        "Identifiers in the %s %s table of %s differ from the declared %s.%s records"
                                .formatted(className, level, LOG_MESSAGES_DOC, className, level));
                for (var record : levelEntry.getValue().entrySet()) {
                    assertEquals(record.getValue(), documentedRows.get(record.getKey()),
                            "Message cell of HTTP-%d in %s differs from the declared template"
                                    .formatted(record.getKey(), LOG_MESSAGES_DOC));
                }
            }
        }
        assertEquals(declared.keySet(), documented.keySet(),
                "%s documents a LogMessages section the code does not declare".formatted(LOG_MESSAGES_DOC));
    }

    @Test
    @DisplayName("LogMessages.adoc names no HTTP-NNN identifier the code does not declare")
    void logMessagesDocShouldNameOnlyDeclaredIdentifiers() {
        assertEquals(new TreeSet<>(), undeclared(identifiersIn(logMessagesDoc)),
                "%s names identifiers that no LogMessages class declares".formatted(LOG_MESSAGES_DOC));
    }

    @Test
    @DisplayName("LogMessages.adoc overview states each class's declared identifier ranges")
    void logMessagesRangeSummaryShouldMatchDeclaredIdentifiers() {
        for (Class<?> logMessagesClass : LOG_MESSAGES_CLASSES) {
            String expected = "(" + formatRanges(idsOf(logMessagesClass.getSimpleName())) + ")";
            String bullet = "- `" + logMessagesClass.getName() + "`";
            List<String> summaryLines = logMessagesDoc.stream().filter(line -> line.startsWith(bullet)).toList();

            assertEquals(1, summaryLines.size(),
                    "Expected exactly one overview bullet for %s in %s".formatted(logMessagesClass.getName(),
                            LOG_MESSAGES_DOC));
            assertTrue(summaryLines.getFirst().contains(expected),
                    "Overview bullet for %s must state the declared range %s, but reads: %s"
                            .formatted(logMessagesClass.getSimpleName(), expected, summaryLines.getFirst()));
        }
    }

    @Test
    @DisplayName("forwarded-header-resolution.adoc catalogue names every ForwardedLogMessages WARN record and its range")
    void forwardedCatalogueShouldMatchDeclaredWarnRecords() {
        SortedSet<Integer> declaredWarn = new TreeSet<>(
                declared.get(ForwardedLogMessages.class.getSimpleName()).get("WARN").keySet());
        List<String> catalogue = forwardedCatalogueSection();

        assertEquals(declaredWarn, identifiersIn(catalogue),
                "The ForwardedLogMessages catalogue in %s must name exactly the declared WARN identifiers"
                        .formatted(FORWARDED_DOC));
        assertEquals(declaredWarn.getLast() - declaredWarn.getFirst() + 1, declaredWarn.size(),
                "The catalogue states a contiguous range, but the declared WARN identifiers are not contiguous: "
                        + declaredWarn);
        String expectedRange = "`HTTP-%d`..`HTTP-%d`".formatted(declaredWarn.getFirst(), declaredWarn.getLast());
        assertTrue(catalogue.stream().anyMatch(line -> line.contains(expectedRange)),
                "The catalogue in %s must state the declared range %s".formatted(FORWARDED_DOC, expectedRange));
        assertEquals(new TreeSet<>(), undeclared(identifiersIn(forwardedDoc)),
                "%s names identifiers that no LogMessages class declares".formatted(FORWARDED_DOC));
    }

    private static SortedMap<String, SortedMap<Integer, String>> collectRecords(Class<?> logMessagesClass)
            throws IllegalAccessException {
        SortedMap<String, SortedMap<Integer, String>> levels = new TreeMap<>();
        for (Class<?> levelClass : logMessagesClass.getDeclaredClasses()) {
            SortedMap<Integer, String> records = new TreeMap<>();
            for (Field field : levelClass.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)
                        && LogRecord.class.isAssignableFrom(field.getType())) {
                    LogRecord logRecord = (LogRecord) field.get(null);
                    assertEquals(EXPECTED_PREFIX, logRecord.getPrefix(),
                            "Unexpected prefix on " + levelClass.getName() + "." + field.getName());
                    String previousTemplate = records.put(logRecord.getIdentifier(), logRecord.getTemplate());
                    assertNull(previousTemplate, "Duplicate identifier %d in %s"
                            .formatted(logRecord.getIdentifier(), levelClass.getName()));
                }
            }
            if (!records.isEmpty()) {
                levels.put(levelClass.getSimpleName(), records);
            }
        }
        return levels;
    }

    private static Map<String, Map<String, SortedMap<Integer, String>>> parseLogMessagesTables() {
        Map<String, Map<String, SortedMap<Integer, String>>> documented = new LinkedHashMap<>();
        String currentClass = null;
        String currentLevel = null;
        for (int i = 0; i < logMessagesDoc.size(); i++) {
            String line = logMessagesDoc.get(i);
            if (line.startsWith("== ")) {
                currentClass = line.substring(3).trim();
                currentLevel = null;
            } else if (line.startsWith("=== ")) {
                currentLevel = line.substring(4).trim().split("\\s+")[0];
            } else {
                Matcher row = TABLE_ID_ROW.matcher(line);
                if (row.matches()) {
                    assertNotNull(currentClass, "Table row outside a class section at line " + (i + 1));
                    assertNotNull(currentLevel, "Table row outside a level section at line " + (i + 1));
                    assertTrue(i + 2 < logMessagesDoc.size(), "Truncated table row at line " + (i + 1));
                    String messageCell = logMessagesDoc.get(i + 2);
                    assertTrue(messageCell.startsWith("|"), "Malformed message cell at line " + (i + 3));
                    String previous = documented
                            .computeIfAbsent(currentClass, key -> new LinkedHashMap<>())
                            .computeIfAbsent(currentLevel, key -> new TreeMap<>())
                            .put(Integer.parseInt(row.group(1)), messageCell.substring(1).trim());
                    assertNull(previous, "HTTP-%s is documented twice in %s".formatted(row.group(1), LOG_MESSAGES_DOC));
                }
            }
        }
        return documented;
    }

    private static List<String> forwardedCatalogueSection() {
        List<String> section = new ArrayList<>();
        boolean inSection = false;
        for (String line : forwardedDoc) {
            if (line.startsWith("=")) {
                if (inSection) {
                    break;
                }
                inSection = line.startsWith("=== ") && line.endsWith("[ForwardedLogMessages]");
            } else if (inSection) {
                section.add(line);
            }
        }
        assertFalse(section.isEmpty(),
                "No ForwardedLogMessages catalogue section found in " + FORWARDED_DOC);
        return section;
    }

    private static SortedSet<Integer> idsOf(String className) {
        SortedSet<Integer> ids = new TreeSet<>();
        declared.get(className).values().forEach(records -> ids.addAll(records.keySet()));
        return ids;
    }

    private static SortedSet<Integer> identifiersIn(List<String> lines) {
        SortedSet<Integer> ids = new TreeSet<>();
        for (String line : lines) {
            Matcher matcher = IDENTIFIER.matcher(line);
            while (matcher.find()) {
                ids.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return ids;
    }

    private static SortedSet<Integer> undeclared(SortedSet<Integer> ids) {
        SortedSet<Integer> result = new TreeSet<>(ids);
        result.removeAll(allDeclaredIds);
        return result;
    }

    /** Formats sorted identifiers as contiguous runs, e.g. {@code HTTP-106–108, HTTP-110–117}. */
    private static String formatRanges(SortedSet<Integer> ids) {
        List<String> runs = new ArrayList<>();
        Integer start = null;
        Integer end = null;
        for (int id : ids) {
            if (start != null && id == end + 1) {
                end = id;
                continue;
            }
            if (start != null) {
                runs.add(formatRun(start, end));
            }
            start = id;
            end = id;
        }
        if (start != null) {
            runs.add(formatRun(start, end));
        }
        return String.join(", ", runs);
    }

    private static String formatRun(int start, int end) {
        return start == end ? "HTTP-" + start : "HTTP-" + start + EN_DASH + end;
    }

    private static Path locateDocDirectory() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("doc");
            if (Files.isRegularFile(candidate.resolve(LOG_MESSAGES_DOC))) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("Could not locate doc/" + LOG_MESSAGES_DOC + " from " + Path.of("").toAbsolutePath());
    }
}
