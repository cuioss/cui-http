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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consistency check of the hand-written ADR index {@code doc/adr/README.md} against the records
 * {@code doc/adr/NNNN-*.adoc} it mirrors.
 *
 * <p>Checked:</p>
 * <ul>
 *   <li>every record has exactly one index row carrying its number, and that row links to exactly
 *   the record's file name - so two records sharing a number fail too;</li>
 *   <li>every index row points at an existing record;</li>
 *   <li>a row's Status cell equals the record's status;</li>
 *   <li>the "highest allocated number" sentence names the highest record number.</li>
 * </ul>
 *
 * <p>A record states its status as the first line of its {@code == Status} section:
 * {@code Accepted}, {@code Proposed} or {@code Superseded}. A superseded record names its
 * successor in the following paragraph, as {@code Superseded by xref:NNNN-...[...]} or
 * {@code Superseded in part by xref:NNNN-...[...]}; the index condenses that to
 * {@code Superseded by N} or {@code Superseded in part by N}. The expected Status cell is derived
 * from the record in exactly that way and compared for equality.</p>
 *
 * <p>Not checked: the row's title against the record's title, and the number in a record's file
 * name against the number in its level-0 heading.</p>
 */
@DisplayName("The ADR index mirrors the ADR records")
class AdrIndexConsistencyTest {

    private static final String ADR_DIRECTORY = "doc/adr";
    private static final String INDEX_FILE = "README.md";

    private static final Pattern RECORD_FILE = Pattern.compile("^(\\d{4})-.*\\.adoc$");
    /** {@code | N | [title](file) | status |}; groups: number, file, status. */
    private static final Pattern INDEX_ROW = Pattern.compile(
            "^\\|\\s*(\\d{1,4})\\s*\\|\\s*\\[.*]\\(([^)]*)\\)\\s*\\|\\s*(.*?)\\s*\\|\\s*$");
    private static final Pattern HIGHEST_NUMBER = Pattern.compile("The highest allocated number is \\*\\*(\\d{1,4})\\*\\*");
    private static final Pattern SECTION = Pattern.compile("^==\\s+(\\S.*?)\\s*$");
    private static final Pattern SUCCESSOR = Pattern.compile("^Superseded( in part)? by xref:(\\d{4})-.*");
    private static final String SUPERSEDED = "Superseded";

    @Test
    @DisplayName("Every record has its index row, every row its record, with equal status and the highest number")
    void indexShouldMirrorTheRecords() throws Exception {
        Path directory = locateRepositoryRoot().resolve(ADR_DIRECTORY);

        List<String> mismatches = mismatchesOf(directory);

        assertTrue(mismatches.isEmpty(), () -> "%d mismatch(es) between %s/%s and the records:%n%s"
                .formatted(mismatches.size(), ADR_DIRECTORY, INDEX_FILE, String.join(System.lineSeparator(), mismatches)));
    }

    @Test
    @DisplayName("The check reports every mismatch of an index, and none for a consistent one")
    void checkShouldReportEveryMismatch(@TempDir Path directory) throws Exception {
        writeRecord(directory, "0001-First.adoc", "Accepted\n");
        writeRecord(directory, "0002-Second.adoc", "Superseded\n\nSuperseded by xref:0004-Fourth.adoc[ADR-0004].\n");
        writeRecord(directory, "0003-Third.adoc", "Superseded\n\nSuperseded in part by xref:0004-Fourth.adoc[ADR-0004].\n");
        writeRecord(directory, "0004-Fourth.adoc", "Proposed\n");
        writeRecord(directory, "0004-Collision.adoc", "Accepted\n");
        writeRecord(directory, "0005-Unlisted.adoc", "Accepted\n");
        writeRecord(directory, "0006-No_successor.adoc", "Superseded\n");
        Files.writeString(directory.resolve(INDEX_FILE), """
                # Index

                | # | Decision | Status |
                |---|----------|--------|
                | 1 | [First](0001-First.adoc) | Accepted |
                | 2 | [Second](0002-Second.adoc) | Superseded by 4 |
                | 3 | [Third](0003-Third.adoc) | Superseded by 4 |
                | 4 | [Fourth](0004-Fourth.adoc) | Accepted |
                | 6 | [No successor](0006-No_successor.adoc) | Superseded |
                | 7 | [Gone](0007-Gone.adoc) | Accepted |

                The highest allocated number is **7**.
                """);

        assertEquals(List.of(
                        "0003-Third.adoc: index status 'Superseded by 4', record status 'Superseded in part by 4'",
                        "0004-Collision.adoc: index row 4 links to '0004-Fourth.adoc', not to this record",
                        "0004-Fourth.adoc: index status 'Accepted', record status 'Proposed'",
                        "0005-Unlisted.adoc: 0 index rows with number 5, expected exactly 1",
                        "0006-No_successor.adoc: record status cannot be read (Superseded without a "
                                + "'Superseded [in part] by xref:NNNN-' line in its Status section)",
                        "index row 7 links to '0007-Gone.adoc', which is not a record in this directory",
                        "index states the highest allocated number is 7, the highest record number is 6"),
                mismatchesOf(directory));
    }

    @Test
    @DisplayName("A consistent index has no mismatch")
    void checkShouldAcceptConsistentIndex(@TempDir Path directory) throws Exception {
        writeRecord(directory, "0001-First.adoc", "Superseded\n\nSuperseded in part by xref:0002-Second.adoc[ADR-0002].\n");
        writeRecord(directory, "0002-Second.adoc", "Accepted\n\nSupersedes xref:0001-First.adoc[ADR-0001].\n");
        Files.writeString(directory.resolve(INDEX_FILE), """
                | 1 | [First](0001-First.adoc) | Superseded in part by 2 |
                | 2 | [Second](0002-Second.adoc) | Accepted |

                The highest allocated number is **2**.
                """);

        assertEquals(List.of(), mismatchesOf(directory));
    }

    @Test
    @DisplayName("An index without the highest-number sentence is a mismatch")
    void checkShouldReportMissingHighestNumberSentence(@TempDir Path directory) throws Exception {
        writeRecord(directory, "0001-First.adoc", "Accepted\n");
        Files.writeString(directory.resolve(INDEX_FILE), "| 1 | [First](0001-First.adoc) | Accepted |\n");

        assertEquals(List.of("index has no 'The highest allocated number is **N**' sentence"), mismatchesOf(directory));
    }

    /** One row of the index table. */
    private record IndexRow(int number, String file, String status) {
    }

    /**
     * @return one line per disagreement between the index and the records of {@code directory}:
     *         first the records in file-name order, then the rows without a record, then the
     *         highest-number sentence
     */
    private static List<String> mismatchesOf(Path directory) throws IOException {
        List<String> records = recordsOf(directory);
        String index = Files.readString(directory.resolve(INDEX_FILE));
        List<IndexRow> rows = rowsOf(index);
        List<String> mismatches = new ArrayList<>();

        int highest = 0;
        for (String file : records) {
            int number = numberOf(file);
            highest = Math.max(highest, number);
            List<IndexRow> numbered = rows.stream().filter(row -> row.number() == number).toList();
            if (numbered.size() != 1) {
                mismatches.add("%s: %d index rows with number %d, expected exactly 1".formatted(file, numbered.size(), number));
                continue;
            }
            IndexRow row = numbered.getFirst();
            if (!row.file().equals(file)) {
                mismatches.add("%s: index row %d links to '%s', not to this record".formatted(file, number, row.file()));
                continue;
            }
            String status = statusOf(directory.resolve(file));
            if (status == null) {
                mismatches.add(("%s: record status cannot be read (Superseded without a "
                        + "'Superseded [in part] by xref:NNNN-' line in its Status section)").formatted(file));
            } else if (!status.equals(row.status())) {
                mismatches.add("%s: index status '%s', record status '%s'".formatted(file, row.status(), status));
            }
        }
        for (IndexRow row : rows) {
            if (!records.contains(row.file())) {
                mismatches.add("index row %d links to '%s', which is not a record in this directory"
                        .formatted(row.number(), row.file()));
            }
        }
        Matcher sentence = HIGHEST_NUMBER.matcher(index);
        if (!sentence.find()) {
            mismatches.add("index has no 'The highest allocated number is **N**' sentence");
        } else if (recordNumber(sentence.group(1)) != highest) {
            mismatches.add("index states the highest allocated number is %s, the highest record number is %d"
                    .formatted(sentence.group(1), highest));
        }
        return mismatches;
    }

    /** @return the file names of the records, sorted */
    private static List<String> recordsOf(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .filter(name -> RECORD_FILE.matcher(name).matches())
                    .sorted()
                    .toList();
        }
    }

    private static int numberOf(String recordFile) {
        return recordNumber(recordFile.substring(0, 4));
    }

    private static List<IndexRow> rowsOf(String index) {
        List<IndexRow> rows = new ArrayList<>();
        for (String line : index.lines().toList()) {
            Matcher row = INDEX_ROW.matcher(line);
            if (row.matches()) {
                rows.add(new IndexRow(recordNumber(row.group(1)), row.group(2), row.group(3)));
            }
        }
        return rows;
    }

    /**
     * Reads a record's status in the wording of the index: the first line of the {@code == Status}
     * section, and for {@code Superseded} the successor the section names.
     *
     * @return the status, {@code ""} when the record has no Status section or the section is
     *         empty, or {@code null} when the record is superseded and names no successor
     */
    private static String statusOf(Path record) throws IOException {
        List<String> section = new ArrayList<>();
        boolean inStatus = false;
        for (String line : Files.readAllLines(record)) {
            Matcher heading = SECTION.matcher(line);
            if (heading.matches()) {
                if (inStatus) {
                    break;
                }
                inStatus = "Status".equals(heading.group(1));
            } else if (inStatus && !line.isBlank()) {
                section.add(line.strip());
            }
        }
        if (section.isEmpty()) {
            return "";
        }
        String status = section.getFirst();
        if (!SUPERSEDED.equals(status)) {
            return status;
        }
        for (String line : section) {
            Matcher successor = SUCCESSOR.matcher(line);
            if (successor.matches()) {
                return "%s%s by %d".formatted(SUPERSEDED, successor.group(1) == null ? "" : successor.group(1),
                        recordNumber(successor.group(2)));
            }
        }
        return null;
    }

    private static void writeRecord(Path directory, String file, String statusSection) throws IOException {
        Files.writeString(directory.resolve(file), "= ADR-%s: Title%n%n== Status%n%n%s%n== Context%n%nText.%n"
                .formatted(file.substring(0, 4), statusSection));
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(ADR_DIRECTORY).resolve(INDEX_FILE))) {
                return current;
            }
            current = current.getParent();
        }
        return fail("Could not locate the directory holding %s/%s from %s"
                .formatted(ADR_DIRECTORY, INDEX_FILE, Path.of("").toAbsolutePath()));
    }

    /**
     * Parses an ADR number. Every caller passes one to four digits matched by a pattern, so the
     * parse cannot fail; a number that still does not parse is a defect of this test.
     */
    private static int recordNumber(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Not an ADR number: " + digits, e);
        }
    }
}
