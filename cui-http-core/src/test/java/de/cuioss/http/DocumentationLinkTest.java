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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Link checker for the repository's AsciiDoc documentation: every relative cross-reference in
 * {@code README.adoc} and in the {@code *.adoc} files under {@code doc/} must point at a file or
 * directory that exists and, where it names an anchor in an AsciiDoc document, at an anchor that
 * document defines.
 *
 * <p>Checked reference forms:</p>
 * <ul>
 *   <li>{@code xref:TARGET[...]} and {@code link:TARGET[...]} whose target is not an absolute URL
 *   - the path is resolved against the directory of the containing document;</li>
 *   <li>a {@code #fragment} on a target that is an {@code .adoc} file - the fragment must be an
 *   anchor of that document;</li>
 *   <li>in-document references: {@code <<anchor>>}, {@code <<anchor,text>>},
 *   {@code xref:#anchor[...]} and {@code xref:anchor[...]}.</li>
 * </ul>
 *
 * <p>An anchor is an explicit id ({@code [#id]}, {@code [[id]]}, {@code [id=...]},
 * {@code anchor:id[]}) or the id Asciidoctor generates for a section title. The generated id
 * follows Asciidoctor's algorithm and honours a document's {@code :idprefix:} and
 * {@code :idseparator:} attributes.</p>
 *
 * <p>Not checked: absolute URLs (no network access in a unit test), fragments on targets that are
 * not AsciiDoc documents, and Javadoc {@code {@link}} references, which the Javadoc build
 * verifies. Content of listing, literal, passthrough and comment blocks and of inline literal and
 * passthrough spans ({@code `+text+`}, {@code +text+}, {@code ++text++}, {@code +++text+++},
 * {@code pass:[text]}) is skipped, so example syntax is neither checked nor read as an anchor.</p>
 *
 * <p>A plain backtick span ({@code `text`}) is <em>not</em> skipped: Asciidoctor renders it as
 * monospace and still runs its macros, so {@code `xref:missing.adoc[x]`} is a link on the published
 * page and is checked like any other. Example syntax that is meant literally is written as
 * {@code `+xref:missing.adoc[x]+`}.</p>
 */
@DisplayName("Documentation cross-references resolve")
class DocumentationLinkTest {

    private static final String DOC_DIRECTORY = "doc";
    private static final String ROOT_README = "README.adoc";
    private static final String ADOC_SUFFIX = ".adoc";

    /** {@code xref:TARGET[} / {@code link:TARGET[}; a preceding backslash escapes the macro. */
    private static final Pattern MACRO_REFERENCE = Pattern.compile("(?<![\\\\\\w])(xref|link):([^\\s\\[\\]]+)\\[");
    /** {@code <<target>>} / {@code <<target,text>>}. */
    private static final Pattern ANGLE_REFERENCE = Pattern.compile("<<([^,>\\s][^,>]*)(?:,[^>]*)?>>");
    /**
     * A URI scheme such as {@code https:} or {@code mailto:} - the target is not a repository path.
     * RFC 3986: {@code scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )}, so one letter suffices.
     */
    private static final Pattern ABSOLUTE_URL = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    /** Explicit anchor forms; group 1 is the id. */
    private static final List<Pattern> EXPLICIT_ANCHORS = List.of(
            Pattern.compile("\\[\\[([^\\],\\s]+)(?:,[^\\]]*)?]]"),
            Pattern.compile("\\[[\\w-]*#([^\\].,%#\\s]+)"),
            Pattern.compile("\\[(?:[^\\]]*,\\s*)?id=[\"']?([^\\]\"',\\s]+)"),
            Pattern.compile("anchor:([^\\[\\s]+)\\["));

    private static final Pattern SECTION_TITLE = Pattern.compile("^={2,6}\\s+(\\S.*)$");
    private static final Pattern BLOCK_ATTRIBUTE_LINE = Pattern.compile("^\\[.*]\\s*$");
    /** Delimiters of blocks whose content is not AsciiDoc markup: listing, literal, passthrough, comment. */
    private static final Pattern VERBATIM_DELIMITER = Pattern.compile("^(-{4,}|\\.{4,}|\\+{4,}|/{4,})\\s*$");
    /**
     * Inline spans in which Asciidoctor runs no macro: {@code +++text+++}, {@code ++text++}, the
     * constrained {@code +text+} - which is also what makes {@code `+text+`} literal - and
     * {@code pass:[text]}. A plain {@code `text`} span is monospace, not literal, and stays markup.
     * The constrained form follows Asciidoctor's rule: no word character directly outside the
     * plus signs, no whitespace directly inside them.
     */
    private static final Pattern INLINE_VERBATIM = Pattern.compile(
            "\\+\\+\\+.*?\\+\\+\\+|\\+\\+.*?\\+\\+"
                    + "|(?<![\\w;:\\\\+])\\+(?:\\S|\\S.*?\\S)\\+(?![\\w+])"
                    + "|pass:[a-z,-]*\\[(?:|.*?[^\\\\])]");
    private static final Pattern ID_ATTRIBUTE = Pattern.compile("^:(idprefix|idseparator):\\s*(.*?)\\s*$");

    /** A macro inside a section title contributes its link text to the generated id. */
    private static final Pattern TITLE_MACRO = Pattern.compile("(?:(?:xref|link):|https?://)[^\\s\\[\\]]*\\[([^\\]]*)]");
    /** What Asciidoctor removes from a title before building the id (its {@code InvalidSectionIdCharsRx}). */
    private static final Pattern INVALID_ID_CHARACTERS = Pattern.compile(
            "<[^>]+>|&(?:[a-z][a-z]+\\d{0,2}|#\\d\\d\\d{0,4}|#x[\\da-f][\\da-f][\\da-f]{0,3});"
                    + "|[^ \\p{L}\\p{M}\\p{N}\\p{Pc}\\-.]");

    @Test
    @DisplayName("Every relative xref:, link: and <<...>> reference points at an existing file and anchor")
    void everyRelativeReferenceShouldResolve() throws Exception {
        Path root = locateRepositoryRoot();
        List<Path> documents = documentsOf(root);
        assertTrue(documents.size() > 1, "Expected %s and documents under %s/, found: %s"
                .formatted(ROOT_README, DOC_DIRECTORY, documents));

        Result result = check(root, documents);

        assertTrue(result.checkedReferences() > 0,
                "No relative reference was found in %d documents - the scan is broken".formatted(documents.size()));
        assertTrue(result.broken().isEmpty(), () -> "%d broken documentation reference(s):%n%s"
                .formatted(result.broken().size(), String.join(System.lineSeparator(), result.broken())));
    }

    @Test
    @DisplayName("The checker reports every broken reference of a document, and none that resolves")
    void checkerShouldReportEveryBrokenReference(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("doc/sub"));
        Files.writeString(root.resolve("doc/sub/target.adoc"), """
                = Target
                :toc:

                [#explicit]
                == First Section

                == Second: Section (with `code`)

                Text with an inline anchor:inline[] and a [[bracketed]]second one.

                == Second: Section (with `code`)
                """);
        Path source = root.resolve("doc/source.adoc");
        Files.writeString(source, """
                = Source

                == Local Section

                xref:sub/target.adoc[file] xref:sub/target.adoc#explicit[explicit id]
                xref:sub/target.adoc#_second_section_with_code[generated id]
                xref:sub/target.adoc#_second_section_with_code_2[duplicate title]
                link:sub/target.adoc#inline[inline] link:sub/target.adoc#bracketed[bracketed] link:sub[directory]
                <<_local_section>> <<_local_section,text>> xref:#_local_section[local] xref:_local_section[local]
                link:https://example.org/missing[external] link:mailto:nobody@example.org[mail]
                `+xref:in-code.adoc[skipped]+` +xref:in-plus.adoc[skipped]+ \\xref:escaped.adoc[skipped]
                ++xref:in-double-plus.adoc[skipped]++ +++xref:in-triple-plus.adoc[skipped]+++ pass:[xref:in-pass.adoc[skipped\\]]
                `xref:sub/target.adoc[monospace, still a link]` `xref:in-backticks.adoc[monospace, still a link]`

                ----
                xref:in-listing.adoc[skipped]
                [#not-an-anchor]
                ----

                xref:sub/missing.adoc[broken file]
                xref:sub/target.adoc#_first_section[the explicit id replaces the generated one]
                xref:sub/target.adoc#not-an-anchor[anchor only inside a listing] <<_missing>>
                """);

        Result result = check(root, List.of(source));

        assertEquals(List.of(
                        "doc/source.adoc:13 -> in-backticks.adoc (file or directory does not exist: doc/in-backticks.adoc)",
                        "doc/source.adoc:20 -> sub/missing.adoc (file or directory does not exist: doc/sub/missing.adoc)",
                        "doc/source.adoc:21 -> sub/target.adoc#_first_section (no anchor '_first_section' in doc/sub/target.adoc)",
                        "doc/source.adoc:22 -> sub/target.adoc#not-an-anchor (no anchor 'not-an-anchor' in doc/sub/target.adoc)",
                        "doc/source.adoc:22 -> #_missing (no anchor '_missing' in doc/source.adoc)"),
                result.broken());
        assertEquals(17, result.checkedReferences(),
                "References checked: every one outside literal spans, passthrough spans and listing blocks");
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("A target with a URI scheme is not a repository path, whatever the scheme's length")
    @CsvSource(delimiter = '|', textBlock = """
            link:https://example.org/page[external]
            link:mailto:nobody@example.org[mail]
            link:x:opaque-part[one-character scheme]
            xref:x:opaque-part[one-character scheme]
            link:a+b-c.d:opaque-part[every scheme character]
            """)
    void targetWithUriSchemeShouldNotBeChecked(String markup) {
        assertEquals(List.of(), referencesIn(markup));
    }

    @Test
    @DisplayName("A target without a URI scheme is a repository path")
    void targetWithoutUriSchemeShouldBeChecked() {
        assertEquals(List.of("x/page.adoc", "1x:page.adoc"),
                referencesIn("link:x/page.adoc[relative] link:1x:page.adoc[a scheme starts with a letter]"));
    }

    @Test
    @DisplayName(":idprefix: and :idseparator: apply to the sections that follow them, and only when they are markup")
    void idAttributesShouldApplyFromTheirPositionAndOnlyAsMarkup(@TempDir Path root) throws Exception {
        Path document = root.resolve("attributes.adoc");
        Files.writeString(document, """
                = Attributes

                == Before Any Attribute

                ----
                :idprefix: listing-
                :idseparator: +
                ----

                ....
                :idprefix: literal-
                ....

                // :idprefix: comment-

                == After Verbatim Blocks

                :idprefix:
                :idseparator: -

                == After The Attributes

                == After The Attributes

                :idprefix: again_

                == Prefix Changed Again
                """);

        assertEquals(Set.of("_before_any_attribute", "_after_verbatim_blocks", "after-the-attributes",
                "after-the-attributes-2", "again_prefix-changed-again"), anchorsOf(document));
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @DisplayName("Section ids are generated the way Asciidoctor generates them")
    @CsvSource(delimiter = '|', textBlock = """
            Validation Stages                                     | _validation_stages
            A03:2021 – Injection                                  | _a032021_injection
            SEC-3: Traversal Pattern Detection                    | _sec_3_traversal_pattern_detection
            Composing `trustedProxies` — a range                  | _composing_trustedproxies_a_range
            Do's ✅                                               | _dos
            Version 1.2.x (Legacy)                                | _version_1_2_x_legacy
            xref:../src/HttpHandler.java[HttpHandler]             | _httphandler
            link:https://nvd.nist.gov/x[CVE-2021-44228] - Log4Shell | _cve_2021_44228_log4shell
            """)
    void sectionIdShouldFollowAsciidoctor(String title, String expectedId) {
        assertEquals(expectedId, sectionId(title, "_", "_"));
    }

    @Test
    @DisplayName("Section ids honour :idprefix: and :idseparator:")
    void sectionIdShouldHonourIdAttributes() {
        assertEquals("trust-model-and-scope", sectionId("Trust Model and Scope", "", "-"));
    }

    /** Outcome of a scan: the number of relative references checked and one line per broken one. */
    private record Result(int checkedReferences, List<String> broken) {
    }

    /**
     * A line of a document with verbatim content blanked out; {@code number} is one-based. A line
     * inside a listing, literal, passthrough or comment block, a block delimiter and a comment
     * line have an empty {@code markup}.
     */
    private record MarkupLine(int number, String raw, String markup) {
    }

    private static Result check(Path root, List<Path> documents) throws IOException {
        Map<Path, Set<String>> anchorsByDocument = new HashMap<>();
        List<String> broken = new ArrayList<>();
        int checked = 0;
        for (Path document : documents) {
            for (MarkupLine line : markupLines(document)) {
                for (String target : referencesIn(line.markup())) {
                    checked++;
                    String problem = problemOf(target, document, root, anchorsByDocument);
                    if (problem != null) {
                        broken.add("%s:%d -> %s (%s)".formatted(relative(root, document), line.number(), target, problem));
                    }
                }
            }
        }
        return new Result(checked, broken);
    }

    /** Collects the relative reference targets of one line, in the order they appear. */
    private static List<String> referencesIn(String markup) {
        List<String> targets = new ArrayList<>();
        Matcher macro = MACRO_REFERENCE.matcher(markup);
        while (macro.find()) {
            String target = macro.group(2);
            if (ABSOLUTE_URL.matcher(target).matches()) {
                continue;
            }
            // xref: without a file extension and without '#' names an anchor of the same document
            boolean anchorOnly = "xref".equals(macro.group(1)) && !target.contains("#") && !target.contains("/")
                    && !target.contains(".");
            targets.add(anchorOnly ? "#" + target : target);
        }
        Matcher angle = ANGLE_REFERENCE.matcher(markup);
        while (angle.find()) {
            String target = angle.group(1).trim();
            targets.add(target.contains(ADOC_SUFFIX) || target.startsWith("#") ? target : "#" + target);
        }
        return targets;
    }

    /**
     * @return the reason {@code target} is broken, or {@code null} when it resolves
     */
    private static String problemOf(String target, Path document, Path root,
            Map<Path, Set<String>> anchorsByDocument) throws IOException {
        int hash = target.indexOf('#');
        String pathPart = hash < 0 ? target : target.substring(0, hash);
        String fragment = hash < 0 ? "" : target.substring(hash + 1);

        Path resolved = pathPart.isEmpty() ? document : document.getParent().resolve(pathPart).normalize();
        if (!Files.exists(resolved)) {
            return "file or directory does not exist: " + relative(root, resolved);
        }
        if (fragment.isEmpty() || !resolved.getFileName().toString().endsWith(ADOC_SUFFIX)) {
            return null;
        }
        Set<String> anchors = anchorsByDocument.get(resolved);
        if (anchors == null) {
            anchors = anchorsOf(resolved);
            anchorsByDocument.put(resolved, anchors);
        }
        return anchors.contains(fragment) ? null
                : "no anchor '%s' in %s".formatted(fragment, relative(root, resolved));
    }

    /**
     * Collects the anchors of a document: every explicit id, and for every section title without
     * an explicit id the id Asciidoctor generates, with its {@code _2}, {@code _3}, ... suffix
     * when the same id was already taken. A section id is generated with the {@code :idprefix:}
     * and {@code :idseparator:} values in force at that section: the defaults until an attribute
     * entry sets one, and an entry inside a listing, literal, passthrough or comment block sets
     * nothing.
     */
    private static Set<String> anchorsOf(Path document) throws IOException {
        String prefix = "_";
        String separator = "_";
        Set<String> anchors = new HashSet<>();
        boolean idPending = false;
        for (MarkupLine line : markupLines(document)) {
            // An attribute entry counts only as markup and only for the sections after it
            Matcher attribute = ID_ATTRIBUTE.matcher(line.markup());
            if (attribute.matches()) {
                if ("idprefix".equals(attribute.group(1))) {
                    prefix = attribute.group(2);
                } else {
                    separator = attribute.group(2);
                }
                continue;
            }
            boolean declaresId = false;
            for (Pattern explicit : EXPLICIT_ANCHORS) {
                Matcher matcher = explicit.matcher(line.markup());
                while (matcher.find()) {
                    anchors.add(matcher.group(1));
                    declaresId = true;
                }
            }
            Matcher title = SECTION_TITLE.matcher(line.raw());
            if (title.matches() && !line.markup().isBlank()) {
                if (!idPending && !declaresId) {
                    addUnique(anchors, sectionId(title.group(1), prefix, separator), separator);
                }
                idPending = false;
            } else if (BLOCK_ATTRIBUTE_LINE.matcher(line.markup()).matches()) {
                idPending = idPending || declaresId;
            } else if (!line.markup().isBlank()) {
                idPending = false;
            }
        }
        return anchors;
    }

    private static void addUnique(Set<String> anchors, String id, String separator) {
        String candidate = id;
        for (int count = 2; !anchors.add(candidate); count++) {
            candidate = id + separator + count;
        }
    }

    /**
     * Generates the id Asciidoctor assigns to a section title (its {@code Section.generate_id}):
     * lower-case the title, drop every character that is not a word character, space, hyphen or
     * dot, replace each run of spaces, dots, hyphens and separators with one separator, drop a
     * trailing separator, and prepend the prefix.
     */
    private static String sectionId(String title, String prefix, String separator) {
        String text = TITLE_MACRO.matcher(title).replaceAll("$1").toLowerCase(Locale.ROOT);
        String id = INVALID_ID_CHARACTERS.matcher(text).replaceAll("");
        if (separator.isEmpty()) {
            return prefix + id.replace(" ", "");
        }
        String separatorCharacter = separator.substring(0, 1);
        id = id.replaceAll("[ .\\-" + Pattern.quote(separatorCharacter) + "]+",
                Matcher.quoteReplacement(separatorCharacter));
        if (id.endsWith(separatorCharacter)) {
            id = id.substring(0, id.length() - 1);
        }
        if (prefix.isEmpty() && id.startsWith(separatorCharacter)) {
            id = id.substring(1);
        }
        return prefix + id;
    }

    /**
     * Reads a document and blanks out everything that is not AsciiDoc markup: the content of
     * listing, literal, passthrough and comment blocks, comment lines and inline literal and
     * passthrough spans. A plain backtick span is monospace markup and is kept. A blanked line
     * keeps its position, so reported line numbers match the file.
     */
    private static List<MarkupLine> markupLines(Path document) throws IOException {
        List<String> rawLines = Files.readAllLines(document);
        List<MarkupLine> lines = new ArrayList<>(rawLines.size());
        String openDelimiter = null;
        for (int index = 0; index < rawLines.size(); index++) {
            String raw = rawLines.get(index);
            String markup;
            Matcher delimiter = VERBATIM_DELIMITER.matcher(raw);
            if (openDelimiter != null) {
                if (raw.stripTrailing().equals(openDelimiter)) {
                    openDelimiter = null;
                }
                markup = "";
            } else if (delimiter.matches()) {
                openDelimiter = delimiter.group(1);
                markup = "";
            } else if (raw.startsWith("//")) {
                markup = "";
            } else {
                markup = INLINE_VERBATIM.matcher(raw).replaceAll("");
            }
            lines.add(new MarkupLine(index + 1, raw, markup));
        }
        return lines;
    }

    private static List<Path> documentsOf(Path root) throws IOException {
        List<Path> documents = new ArrayList<>();
        documents.add(root.resolve(ROOT_README));
        try (Stream<Path> files = Files.walk(root.resolve(DOC_DIRECTORY))) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(ADOC_SUFFIX))
                    .sorted()
                    .forEach(documents::add);
        }
        return documents;
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve(DOC_DIRECTORY)) && Files.isRegularFile(current.resolve(ROOT_README))) {
                return current;
            }
            current = current.getParent();
        }
        return fail("Could not locate the directory holding %s and %s/ from %s"
                .formatted(ROOT_README, DOC_DIRECTORY, Path.of("").toAbsolutePath()));
    }
}
