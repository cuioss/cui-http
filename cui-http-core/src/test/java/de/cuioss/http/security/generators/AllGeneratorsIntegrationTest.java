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

import de.cuioss.http.client.result.HttpResult;
import de.cuioss.http.client.result.HttpResultFailureGenerator;
import de.cuioss.http.client.result.HttpResultSuccessGenerator;
import de.cuioss.http.forwarded.ForwardedHeaderResolver;
import de.cuioss.http.forwarded.ForwardedHostGenerator;
import de.cuioss.http.forwarded.ForwardedResolverConfig;
import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.data.Cookie;
import de.cuioss.http.security.data.URLParameter;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.cookie.*;
import de.cuioss.http.security.generators.encoding.*;
import de.cuioss.http.security.generators.header.*;
import de.cuioss.http.security.generators.injection.HttpRequestSmugglingAttackGenerator;
import de.cuioss.http.security.generators.injection.ProtocolHandlerAttackGenerator;
import de.cuioss.http.security.generators.url.*;
import de.cuioss.http.security.generators.url.URLLengthLimitAttackGenerator.Surface;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.HTTPHeaderValidationPipeline;
import de.cuioss.http.security.pipeline.PipelineFactory;
import de.cuioss.http.security.pipeline.URLParameterNameValidationPipeline;
import de.cuioss.http.security.pipeline.URLParameterValidationPipeline;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.http.security.validation.CookiePrefixValidationStage;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.containsControlCharacter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test over every generator class of this module.
 *
 * <p>The per-generator contract tests each look at one generator in depth. This test looks at all
 * of them at once, through one explicit {@link #registry() registry}, and asserts four things
 * of every registered generator:</p>
 * <ul>
 *   <li><strong>Reach</strong> - under the pinned seed, the draws hit every named arm of the
 *       registration and yield more than one distinct value, so a generator that degenerated into
 *       a constant fails here.</li>
 *   <li><strong>Round-trip</strong> - every value the registration makes a claim about is sent
 *       through the production route that consumes it and earns exactly the claimed verdict:
 *       attack values are rejected, valid values are accepted.</li>
 *   <li><strong>Type</strong> - a {@link TypedGenerator} reports the type its values have.</li>
 *   <li><strong>Concurrency</strong> - one instance shared between several workers hands out a
 *       value for every draw without throwing.</li>
 * </ul>
 *
 * <p>Most registrations claim one verdict for every value. Four generators deliberately mix
 * families that are not uniformly rejected - a merely long path is not an attack - and claim a
 * verdict only for the families that are unambiguous; the round-trip test requires every
 * registration to make at least one claim, so none of them is left unchecked.</p>
 *
 * <p>The registry is the place a new generator class has to be added to.
 * {@link #shouldRegisterEveryGeneratorClassOnce()} derives the set of generator classes from the
 * compiled test tree and requires the registry to hold exactly that set, so a generator nobody
 * registered fails the test by name. A generator class is every concrete, named class under
 * {@code de/cuioss/http} that implements {@link TypedGenerator} - nested member classes such as
 * {@link HttpRequestSmugglingAttackGenerator.HeaderShaped} included, because each is a generator
 * a test can instantiate on its own - or whose simple name ends in {@code Generator}. The name
 * rule finds generators that publish a fixed list instead of implementing {@link TypedGenerator},
 * such as {@link ForwardedHostGenerator}, without a hand-kept list a new one could be left out of.
 * Anonymous and local classes are not counted: they have no name a registry entry could refer
 * to.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("All Generators Integration Tests")
class AllGeneratorsIntegrationTest {

    /** The package tree, relative to the compiled test-classes root, that is scanned for generators. */
    private static final String SCANNED_PACKAGE_PATH = "de/cuioss/http";

    /** The name suffix that marks a generator class which publishes a fixed list instead of implementing TypedGenerator. */
    private static final String GENERATOR_NAME_SUFFIX = "Generator";

    private static final int DRAWS = 400;

    /** InvalidURLGenerator's rarest literal is drawn with probability 1/60. */
    private static final int INVALID_URL_DRAWS = 2000;

    private static final int CONCURRENT_WORKERS = 3;
    private static final int DRAWS_PER_WORKER = 100;

    private static final int NULL = 0x0000;
    private static final int PADDING_CHARACTER = 0x0080;
    private static final int NEXT_LINE = 0x0085;
    private static final int NO_BREAK_SPACE = 0x00A0;
    private static final int COMBINING_ACUTE_ACCENT = 0x0301;
    private static final int EN_QUAD = 0x2000;
    private static final int ZERO_WIDTH_SPACE = 0x200B;
    private static final int ONE_DOT_LEADER = 0x2024;
    private static final int RIGHT_TO_LEFT_OVERRIDE = 0x202E;
    private static final int ZERO_WIDTH_NO_BREAK_SPACE = 0xFEFF;
    private static final int FULLWIDTH_FULL_STOP = 0xFF0E;

    /** RFC 7230 {@code token}, which RFC 6265 uses for the cookie name. */
    private static final Pattern COOKIE_NAME_TOKEN = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

    /** RFC 6265 section 4.1.1 {@code cookie-octet}; an empty value is legal. */
    private static final Pattern COOKIE_OCTETS =
            Pattern.compile("[\\x21\\x23-\\x2B\\x2D-\\x3A\\x3C-\\x5B\\x5D-\\x7E]*");

    private static final List<String> INVALID_ESCAPES = List.of("%ZZ", "%GG", "%invalid", "%encoding");
    private static final Set<String> SYSTEM_PATHS = Set.of("/health", "/metrics", "/status", "/info");

    /** The one verdict a production route reaches for a value. */
    private enum Verdict {
        ACCEPTED, REJECTED
    }

    /**
     * One generator class together with what this test asserts of it.
     *
     * @param generatorClass the registered class
     * @param generator creates the generator, or {@code null} for a class that publishes a fixed
     *        list instead of implementing {@link TypedGenerator}
     * @param valueType the type a {@link TypedGenerator} must report
     * @param values draws the values under test
     * @param arms the named arms the draws must reach
     * @param minimumDistinct the least number of distinct values the draws must yield
     * @param claim the verdict claimed for a value, or empty where the registration makes none
     * @param roundTrip sends a value through the production route that consumes it
     * @param <T> the value type
     */
    private record Registration<T>(Class<?> generatorClass, Supplier<TypedGenerator<T>> generator,
    Class<?> valueType, Supplier<List<T>> values, Map<String, Predicate<T>> arms, int minimumDistinct,
    Function<T, Optional<Verdict>> claim, Function<T, Verdict> roundTrip) {

        @Override
        public String toString() {
            return generatorClass.getSimpleName();
        }
    }

    static List<Registration<?>> registry() {
        return List.of(
                generated(SupportedValidationTypeGenerator.class, SupportedValidationTypeGenerator::new,
                        ValidationType.class, DRAWS,
                        Map.of("url-path", type -> type == ValidationType.URL_PATH,
                                "parameter-name", type -> type == ValidationType.PARAMETER_NAME,
                                "parameter-value", type -> type == ValidationType.PARAMETER_VALUE,
                                "header-name", type -> type == ValidationType.HEADER_NAME,
                                "header-value", type -> type == ValidationType.HEADER_VALUE),
                        always(Verdict.ACCEPTED),
                        type -> verdictOf(PipelineFactory.createPipeline(type, SecurityConfiguration.defaults(),
                                new SecurityEventCounter()), "value")),
                generated(AttackCookieGenerator.class, AttackCookieGenerator::new, Cookie.class, DRAWS,
                        Map.of("control-character-name", cookie -> containsControlCharacter(cookie.name()),
                                "very-long-name", cookie -> cookie.name().startsWith("very_long_cookie_name_"),
                                "separator-name", cookie -> cookie.name().contains(";"),
                                "script-value", cookie -> cookie.value().contains("<script>"),
                                "null-byte-value", cookie -> cookie.value().indexOf(NULL) >= 0,
                                "traversal-value", cookie -> cookie.value().startsWith("../")),
                        AllGeneratorsIntegrationTest::cookieGrammarClaim,
                        AllGeneratorsIntegrationTest::cookieVerdict),
                strings(CookieNameAsciiWhitespaceGenerator.class, CookieNameAsciiWhitespaceGenerator::new, DRAWS,
                        Map.of("space", name -> name.contains(" "),
                                "tab", name -> name.contains("\t"),
                                "embedded", name -> name.equals(name.strip())),
                        always(Verdict.REJECTED), AllGeneratorsIntegrationTest::cookieNameVerdict),
                strings(CookieNameLegacyParsingGenerator.class, CookieNameLegacyParsingGenerator::new, DRAWS,
                        Map.of("version-1", name -> name.startsWith("$Version=1"),
                                "version-2", name -> name.startsWith("$Version=2")),
                        always(Verdict.REJECTED), AllGeneratorsIntegrationTest::cookieNameVerdict),
                strings(CookieNameUnicodeWhitespaceGenerator.class, CookieNameUnicodeWhitespaceGenerator::new, DRAWS,
                        Map.of("en-quad", name -> name.indexOf(EN_QUAD) >= 0,
                                "no-break-space", name -> name.indexOf(NO_BREAK_SPACE) >= 0,
                                "next-line", name -> name.indexOf(NEXT_LINE) >= 0),
                        always(Verdict.REJECTED), AllGeneratorsIntegrationTest::cookieNameVerdict),
                strings(CookieNameZeroWidthGenerator.class, CookieNameZeroWidthGenerator::new, DRAWS,
                        Map.of("zero-width-space", name -> name.indexOf(ZERO_WIDTH_SPACE) >= 0,
                                "zero-width-no-break-space", name -> name.indexOf(ZERO_WIDTH_NO_BREAK_SPACE) >= 0),
                        always(Verdict.REJECTED), AllGeneratorsIntegrationTest::cookieNameVerdict),
                generated(ValidCookieGenerator.class, ValidCookieGenerator::new, Cookie.class, DRAWS,
                        Map.of("with-attributes", cookie -> !cookie.attributes().isEmpty(),
                                "without-attributes", cookie -> cookie.attributes().isEmpty()),
                        always(Verdict.ACCEPTED), AllGeneratorsIntegrationTest::cookieVerdict),
                strings(BoundaryFuzzingGenerator.class, BoundaryFuzzingGenerator::new, DRAWS,
                        Map.of("long-path", value -> value.startsWith("/verylongpathsegment/"),
                                "deep-nesting", value -> value.startsWith("dir/"),
                                "null-byte", AllGeneratorsIntegrationTest::carriesNullByte,
                                "control-character", GeneratorContractAssertions::containsControlCharacter,
                                "shell-metacharacter", AllGeneratorsIntegrationTest::carriesNonPathShellCharacter),
                        value -> carriesNullByte(value) || containsControlCharacter(value)
                                || carriesNonPathShellCharacter(value) || value.startsWith("../")
                                ? Optional.of(Verdict.REJECTED)
                                : Optional.empty(),
                        through(AllGeneratorsIntegrationTest::pathPipeline)),
                strings(EncodingCombinationGenerator.class, EncodingCombinationGenerator::new, DRAWS,
                        Map.of("single-encoded", value -> !value.contains("%25"),
                                "double-encoded", value -> value.contains("%252") && !value.contains("%2525"),
                                "triple-encoded", value -> value.contains("%25252e")),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::pathPipeline)),
                strings(PathTraversalGenerator.class, PathTraversalGenerator::new, DRAWS,
                        Map.of("raw", value -> value.contains("../"),
                                "encoded", value -> value.contains("%2e%2e%2f"),
                                "double-encoded", value -> value.contains("%252e%252e"),
                                "null-byte", value -> value.contains("%00")),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::pathPipeline)),
                strings(UnicodeAttackGenerator.class, UnicodeAttackGenerator::new, DRAWS,
                        Map.of("right-to-left-override", value -> value.indexOf(RIGHT_TO_LEFT_OVERRIDE) >= 0,
                                "zero-width-space", value -> value.indexOf(ZERO_WIDTH_SPACE) >= 0,
                                "zero-width-no-break-space", value -> value.indexOf(ZERO_WIDTH_NO_BREAK_SPACE) >= 0,
                                "null-character", value -> value.indexOf(NULL) >= 0,
                                "lookalike-dots", value -> value.indexOf(ONE_DOT_LEADER) >= 0),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::pathPipeline)),
                strings(UnicodeControlCharacterAttackGenerator.class, UnicodeControlCharacterAttackGenerator::new,
                        DRAWS,
                        Map.of("url-context", value -> value.startsWith("http\0://"),
                                "bidirectional", value -> value.charAt(0) == RIGHT_TO_LEFT_OVERRIDE,
                                "c1-control", value -> value.charAt(1) == PADDING_CHARACTER,
                                "encoded-bypass", value -> value.contains("%0A")),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::pathPipeline)),
                strings(UnicodeNormalizationAttackGenerator.class, UnicodeNormalizationAttackGenerator::new, DRAWS,
                        Map.of("bidirectional", value -> value.charAt(0) == RIGHT_TO_LEFT_OVERRIDE,
                                "zero-width", value -> value.charAt(1) == ZERO_WIDTH_SPACE,
                                "fullwidth", value -> value.indexOf(FULLWIDTH_FULL_STOP) >= 0,
                                "combining", value -> value.indexOf(COMBINING_ACUTE_ACCENT) >= 0),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::pathPipeline)),
                strings(HTTPHeaderInjectionGenerator.class, HTTPHeaderInjectionGenerator::new, DRAWS,
                        Map.of("line-break", value -> value.contains("\r\n"),
                                "null-byte", value -> value.indexOf(NULL) >= 0),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::headerValuePipeline)),
                strings(HttpHeaderInjectionAttackGenerator.class, HttpHeaderInjectionAttackGenerator::new, DRAWS,
                        Map.of("crlf", value -> value.contains("\r\n"),
                                "bare-line-feed", value -> value.contains("\n") && !value.contains("\r"),
                                "bare-carriage-return", value -> value.contains("\r") && !value.contains("\n")),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::headerValuePipeline)),
                strings(HttpHeaderInjectionAttackGenerator.ForParameterValue.class,
                        HttpHeaderInjectionAttackGenerator.ForParameterValue::new, DRAWS,
                        Map.of("encoded-crlf", value -> value.contains("%0d%0a"),
                                "encoded-bare-line-feed", value -> value.contains("%0a") && !value.contains("%0d"),
                                "encoded-bare-carriage-return", value -> value.contains("%0d") && !value.contains("%0a")),
                        always(Verdict.REJECTED),
                        through(AllGeneratorsIntegrationTest::lineBreakRejectingParameterPipeline)),
                strings(InvalidHTTPHeaderNameGenerator.class, InvalidHTTPHeaderNameGenerator::new, DRAWS,
                        Map.of("crlf", value -> value.contains("\r\n"),
                                "bare-line-feed", value -> value.contains("\n") && !value.contains("\r"),
                                "bare-carriage-return", value -> value.contains("\r") && !value.contains("\n"),
                                "null-byte", value -> value.indexOf(NULL) >= 0),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::headerNamePipeline)),
                strings(ValidHTTPHeaderNameGenerator.class, ValidHTTPHeaderNameGenerator::new, DRAWS,
                        Map.of("custom", value -> value.startsWith("X-"),
                                "standard", value -> !value.startsWith("X-")),
                        always(Verdict.ACCEPTED), through(AllGeneratorsIntegrationTest::headerNamePipeline)),
                strings(ValidHTTPHeaderValueGenerator.class, ValidHTTPHeaderValueGenerator::new, DRAWS,
                        Map.of("bearer-token", value -> value.startsWith("Bearer "),
                                "origin", value -> value.startsWith("http"),
                                "connection", "close"::equals),
                        always(Verdict.ACCEPTED), through(AllGeneratorsIntegrationTest::headerValuePipeline)),
                strings(HttpRequestSmugglingAttackGenerator.class, HttpRequestSmugglingAttackGenerator::new, DRAWS,
                        Map.of("header-shaped", AllGeneratorsIntegrationTest::carriesRawLineBreak,
                                "query-shaped", value -> !carriesRawLineBreak(value) && value.contains("%0d%0a")),
                        always(Verdict.REJECTED),
                        value -> verdictOf(carriesRawLineBreak(value) ? headerValuePipeline()
                                : lineBreakRejectingParameterPipeline(), value)),
                strings(HttpRequestSmugglingAttackGenerator.HeaderShaped.class,
                        HttpRequestSmugglingAttackGenerator.HeaderShaped::new, DRAWS,
                        Map.of("header-shaped", AllGeneratorsIntegrationTest::carriesRawLineBreak),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::headerValuePipeline)),
                strings(HttpRequestSmugglingAttackGenerator.QueryShaped.class,
                        HttpRequestSmugglingAttackGenerator.QueryShaped::new, DRAWS,
                        Map.of("query-shaped", value -> !carriesRawLineBreak(value) && value.contains("%0d%0a")),
                        always(Verdict.REJECTED),
                        through(AllGeneratorsIntegrationTest::lineBreakRejectingParameterPipeline)),
                strings(ProtocolHandlerAttackGenerator.class, ProtocolHandlerAttackGenerator::new, DRAWS,
                        Map.of("javascript", value -> value.startsWith("javascript:"),
                                "vbscript", value -> value.startsWith("vbscript:"),
                                "data", value -> value.startsWith("data:"),
                                "file", value -> value.startsWith("file:"),
                                "percent-encoded", value -> value.startsWith("%")),
                        always(Verdict.REJECTED),
                        through(() -> new URLPathValidationPipeline(SecurityConfiguration.strict(),
                                new SecurityEventCounter()))),
                generated(AttackURLParameterGenerator.class, AttackURLParameterGenerator::new, URLParameter.class,
                        DRAWS,
                        Map.of("script-tag", parameter -> parameter.value().startsWith("<"),
                                "traversal", parameter -> parameter.value().startsWith("../"),
                                "null-byte", parameter -> carriesNullByte(parameter.value())),
                        parameter -> parameter.value().startsWith("<") || parameter.value().startsWith("../")
                                || carriesNullByte(parameter.value())
                                ? Optional.of(Verdict.REJECTED)
                                : Optional.empty(),
                        parameter -> verdictOf(parameterPipeline(), parameter.value())),
                strings(InvalidURLGenerator.class, InvalidURLGenerator::new, INVALID_URL_DRAWS,
                        Map.of("blank", String::isBlank,
                                "invalid-escape", AllGeneratorsIntegrationTest::carriesInvalidEscape,
                                "unencoded-space", value -> !value.isBlank() && value.contains(" ")),
                        value -> value.contains(" ") || carriesInvalidEscape(value)
                                ? Optional.of(Verdict.REJECTED)
                                : Optional.empty(),
                        through(AllGeneratorsIntegrationTest::pathPipeline)),
                strings(NullByteInjectionParameterGenerator.class, NullByteInjectionParameterGenerator::new, DRAWS,
                        Map.of("raw", value -> value.indexOf(NULL) >= 0,
                                "percent-encoded", value -> value.contains("%00")),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::parameterPipeline)),
                strings(NullByteURLGenerator.class, NullByteURLGenerator::new, DRAWS,
                        Map.of("raw", value -> value.indexOf(NULL) >= 0,
                                "percent-encoded", value -> value.contains("%00")),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::pathPipeline)),
                strings(PathTraversalParameterGenerator.class, PathTraversalParameterGenerator::new, DRAWS,
                        Map.of("overlong", value -> value.contains("%c0"),
                                "triple-encoded", value -> value.contains("%252e"),
                                "encoded-separator", value -> value.contains("..%2F")),
                        always(Verdict.REJECTED), through(AllGeneratorsIntegrationTest::parameterPipeline)),
                overlong(URLLengthLimitAttackGenerator.class, URLLengthLimitAttackGenerator::new, Surface.URL_PATH),
                overlong(URLLengthLimitAttackGenerator.ForParameterName.class,
                        URLLengthLimitAttackGenerator.ForParameterName::new, Surface.PARAMETER_NAME),
                overlong(URLLengthLimitAttackGenerator.ForParameterValue.class,
                        URLLengthLimitAttackGenerator.ForParameterValue::new, Surface.PARAMETER_VALUE),
                overlong(URLLengthLimitAttackGenerator.ForHeaderName.class,
                        URLLengthLimitAttackGenerator.ForHeaderName::new, Surface.HEADER_NAME),
                overlong(URLLengthLimitAttackGenerator.ForHeaderValue.class,
                        URLLengthLimitAttackGenerator.ForHeaderValue::new, Surface.HEADER_VALUE),
                strings(ValidURLGenerator.class, ValidURLGenerator::new, DRAWS, 20, Map.of(),
                        always(Verdict.ACCEPTED), through(AllGeneratorsIntegrationTest::pathPipeline)),
                generated(ValidURLParameterGenerator.class, ValidURLParameterGenerator::new, URLParameter.class,
                        DRAWS,
                        Map.of("numeric-value", parameter -> parameter.value().chars().allMatch(Character::isDigit),
                                "word-value", parameter -> parameter.value().chars().allMatch(Character::isLetter)),
                        always(Verdict.ACCEPTED),
                        parameter -> verdictOf(new URLParameterNameValidationPipeline(
                                SecurityConfiguration.defaults(), new SecurityEventCounter()), parameter.name())
                                == Verdict.ACCEPTED ? verdictOf(parameterPipeline(), parameter.value())
                                : Verdict.REJECTED),
                strings(ValidURLParameterStringGenerator.class, ValidURLParameterStringGenerator::new, DRAWS,
                        Map.of("numeric", value -> value.chars().allMatch(Character::isDigit),
                                "encoded-space", value -> value.contains("%20"),
                                "encoded-at-sign", value -> value.contains("%40"),
                                "phone", value -> value.contains("-")),
                        always(Verdict.ACCEPTED), through(AllGeneratorsIntegrationTest::parameterPipeline)),
                strings(ValidURLPathGenerator.class, ValidURLPathGenerator::new, DRAWS,
                        Map.of("system", SYSTEM_PATHS::contains,
                                "versioned-api", value -> value.startsWith("/api/v"),
                                "admin", value -> value.startsWith("/api/admin/")),
                        always(Verdict.ACCEPTED), through(AllGeneratorsIntegrationTest::pathPipeline)),
                listed(ForwardedHostGenerator.class, ForwardedHostGenerator::hostileHosts,
                        Map.of("quote", host -> host.contains("\""),
                                "angle-bracket", host -> host.contains("<"),
                                "semicolon", host -> host.contains(";"),
                                "encoded-separator", host -> host.contains("%2f"),
                                "closing-bracket", host -> host.contains("]"),
                                "homoglyph", host -> host.chars().anyMatch(character -> character > 0x7F)),
                        always(Verdict.REJECTED), AllGeneratorsIntegrationTest::forwardedHostVerdict),
                generated(HttpResultSuccessGenerator.class, HttpResultSuccessGenerator::new, HttpResult.class, DRAWS,
                        Map.of("not-modified", result -> result.getHttpStatus().orElseThrow() == 304,
                                "successful", result -> result.getHttpStatus().orElseThrow() < 300,
                                "with-etag", result -> result.getETag().isPresent(),
                                "without-etag", result -> result.getETag().isEmpty()),
                        always(Verdict.ACCEPTED), AllGeneratorsIntegrationTest::httpResultVerdict),
                generated(HttpResultFailureGenerator.class, HttpResultFailureGenerator::new, HttpResult.class, DRAWS,
                        Map.of("with-fallback", result -> result.getContent().isPresent(),
                                "without-fallback", result -> result.getContent().isEmpty()),
                        always(Verdict.REJECTED), AllGeneratorsIntegrationTest::httpResultVerdict));
    }

    static Stream<Registration<?>> typedRegistry() {
        return registry().stream().filter(registration -> registration.generator() != null);
    }

    @Test
    @DisplayName("Should register every generator class exactly once")
    void shouldRegisterEveryGeneratorClassOnce() throws Exception {
        List<Class<?>> registered = registry().stream().<Class<?>>map(Registration::generatorClass).toList();
        Set<String> expected = classNames(generatorClassesOfTheTestTree());
        Set<String> actual = classNames(registered);

        assertAll("Registry",
                () -> assertEquals(expected, actual,
                        () -> "The registry must hold exactly the generator classes of the test tree. Missing: "
                                + difference(expected, actual) + ", unexpected: " + difference(actual, expected)),
                () -> assertEquals(registered.size(), new HashSet<>(registered).size(),
                        "No generator class may be registered twice"));
    }

    /**
     * Walks the compiled test-classes root and returns every generator class found there. Classes
     * are loaded without being initialized.
     */
    private static Set<Class<?>> generatorClassesOfTheTestTree() throws IOException, URISyntaxException {
        Path root = Path.of(AllGeneratorsIntegrationTest.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
        ClassLoader loader = AllGeneratorsIntegrationTest.class.getClassLoader();
        Set<Class<?>> generators = new HashSet<>();
        try (Stream<Path> files = Files.walk(root.resolve(SCANNED_PACKAGE_PATH))) {
            files.map(root::relativize)
                    .map(Path::toString)
                    .filter(file -> file.endsWith(".class") && !file.endsWith("package-info.class"))
                    .map(file -> file.substring(0, file.length() - ".class".length())
                            .replace(File.separatorChar, '.'))
                    .map(name -> loadUninitialized(name, loader))
                    .filter(AllGeneratorsIntegrationTest::isGeneratorClass)
                    .forEach(generators::add);
        }
        return generators;
    }

    /**
     * A concrete, named {@link TypedGenerator} implementation or class named {@code *Generator};
     * anonymous and local classes have no registrable name.
     */
    private static boolean isGeneratorClass(Class<?> type) {
        return (TypedGenerator.class.isAssignableFrom(type)
                || type.getSimpleName().endsWith(GENERATOR_NAME_SUFFIX))
                && !type.isInterface()
                && !Modifier.isAbstract(type.getModifiers())
                && !type.isAnonymousClass()
                && !type.isLocalClass();
    }

    private static Class<?> loadUninitialized(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("A compiled test class could not be loaded: " + name, e);
        }
    }

    private static Set<String> classNames(Collection<Class<?>> classes) {
        return classes.stream().map(Class::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> difference(Set<String> minuend, Set<String> subtrahend) {
        Set<String> difference = new TreeSet<>(minuend);
        difference.removeAll(subtrahend);
        return difference;
    }

    @ParameterizedTest
    @MethodSource("registry")
    @DisplayName("Should reach every registered arm and more than one distinct value")
    void shouldReachEveryArm(Registration<?> registration) {
        assertReach(registration);
    }

    @ParameterizedTest
    @MethodSource("registry")
    @DisplayName("Should round-trip every claimed value to exactly its claimed verdict")
    void shouldRoundTripEveryClaimedValue(Registration<?> registration) {
        assertRoundTrip(registration);
    }

    @ParameterizedTest
    @MethodSource("typedRegistry")
    @DisplayName("Should report the type of the values it generates")
    void shouldReportItsValueType(Registration<?> registration) {
        assertEquals(registration.valueType(), registration.generator().get().getType(),
                () -> registration + " must report its value type");
    }

    @ParameterizedTest
    @MethodSource("typedRegistry")
    @DisplayName("Should hand out a value for every draw when one instance is shared by threads")
    void shouldServeConcurrentDraws(Registration<?> registration) throws Exception {
        assertConcurrentDraws(registration);
    }

    private static <T> void assertReach(Registration<T> registration) {
        List<T> values = registration.values().get();
        Set<String> reached = new TreeSet<>();
        for (T value : values) {
            registration.arms().forEach((name, arm) -> {
                if (arm.test(value)) {
                    reached.add(name);
                }
            });
        }

        assertAll(registration + " reach",
                () -> assertEquals(new TreeSet<>(registration.arms().keySet()), reached,
                        "Every registered arm must be reachable within " + values.size() + " draws"),
                () -> assertTrue(new HashSet<>(values).size() >= registration.minimumDistinct(),
                        () -> "Expected at least " + registration.minimumDistinct() + " distinct values within "
                                + values.size() + " draws, but got " + new HashSet<>(values).size()));
    }

    private static <T> void assertRoundTrip(Registration<T> registration) {
        int claims = 0;
        for (T value : registration.values().get()) {
            Optional<Verdict> claim = registration.claim().apply(value);
            if (claim.isPresent()) {
                claims++;
                assertEquals(claim.get(), registration.roundTrip().apply(value),
                        () -> registration + " value must earn its claimed verdict. Value: <" + describe(value) + ">");
            }
        }
        assertTrue(claims > 0, registration + " must claim a verdict for at least one of its values");
    }

    /**
     * Shares one generator instance between several workers. {@link Future#get()} re-throws
     * whatever a worker threw, so a data race that surfaces as an exception fails the test. The
     * non-null count detects a draw that yielded no value; it does not detect a duplicated value
     * caused by a lost state update.
     */
    private static <T> void assertConcurrentDraws(Registration<T> registration) throws Exception {
        TypedGenerator<T> generator = registration.generator().get();
        Callable<List<T>> worker = () -> {
            List<T> drawn = new ArrayList<>(DRAWS_PER_WORKER);
            for (int draw = 0; draw < DRAWS_PER_WORKER; draw++) {
                drawn.add(generator.next());
            }
            return drawn;
        };

        List<T> collected = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WORKERS)) {
            List<Future<List<T>>> futures = new ArrayList<>();
            for (int index = 0; index < CONCURRENT_WORKERS; index++) {
                futures.add(executor.submit(worker));
            }
            for (Future<List<T>> future : futures) {
                collected.addAll(future.get());
            }
        }

        assertEquals(CONCURRENT_WORKERS * DRAWS_PER_WORKER,
                collected.stream().filter(Objects::nonNull).count(),
                registration + " must hand out a value for every concurrent draw");
    }

    private static <T> Registration<T> generated(Class<? extends TypedGenerator<T>> generatorClass,
            Supplier<TypedGenerator<T>> generator, Class<?> valueType, int draws,
            Map<String, Predicate<T>> arms, Function<T, Optional<Verdict>> claim, Function<T, Verdict> roundTrip) {
        return new Registration<>(generatorClass, generator, valueType, () -> draw(generator.get(), draws), arms, 2,
                claim, roundTrip);
    }

    private static Registration<String> strings(Class<? extends TypedGenerator<String>> generatorClass,
            Supplier<TypedGenerator<String>> generator, int draws, Map<String, Predicate<String>> arms,
            Function<String, Optional<Verdict>> claim, Function<String, Verdict> roundTrip) {
        return strings(generatorClass, generator, draws, 2, arms, claim, roundTrip);
    }

    private static Registration<String> strings(Class<? extends TypedGenerator<String>> generatorClass,
            Supplier<TypedGenerator<String>> generator, int draws, int minimumDistinct,
            Map<String, Predicate<String>> arms, Function<String, Optional<Verdict>> claim,
            Function<String, Verdict> roundTrip) {
        return new Registration<>(generatorClass, generator, String.class, () -> draw(generator.get(), draws), arms,
                minimumDistinct, claim, roundTrip);
    }

    /**
     * A length-limit generator for one surface: the draws reach a value beyond each preset limit
     * of the surface, and every value is rejected by the surface's pipeline configured with the
     * strict limit of that surface alone.
     */
    private static Registration<String> overlong(Class<? extends TypedGenerator<String>> generatorClass,
            Supplier<TypedGenerator<String>> generator, Surface surface) {
        return strings(generatorClass, generator, DRAWS,
                Map.of("beyond-strict-only", value -> value.length() > surface.strictLimit()
                                && value.length() <= surface.defaultLimit(),
                        "beyond-default", value -> value.length() > surface.defaultLimit()
                                && value.length() <= surface.lenientLimit(),
                        "beyond-lenient", value -> value.length() > surface.lenientLimit()),
                always(Verdict.REJECTED),
                through(() -> PipelineFactory.createPipeline(surface.validationType(), strictLengthLimitOf(surface),
                        new SecurityEventCounter())));
    }

    private static SecurityConfiguration strictLengthLimitOf(Surface surface) {
        return switch (surface) {
            case URL_PATH -> SecurityConfiguration.builder().maxPathLength(surface.strictLimit()).build();
            case PARAMETER_NAME -> SecurityConfiguration.builder().maxParameterNameLength(surface.strictLimit()).build();
            case PARAMETER_VALUE -> SecurityConfiguration.builder().maxParameterValueLength(surface.strictLimit()).build();
            case HEADER_NAME -> SecurityConfiguration.builder().maxHeaderNameLength(surface.strictLimit()).build();
            case HEADER_VALUE -> SecurityConfiguration.builder().maxHeaderValueLength(surface.strictLimit()).build();
        };
    }

    private static <T> Registration<T> listed(Class<?> generatorClass, Supplier<List<T>> values,
            Map<String, Predicate<T>> arms, Function<T, Optional<Verdict>> claim, Function<T, Verdict> roundTrip) {
        return new Registration<>(generatorClass, null, null, values, arms, 2, claim, roundTrip);
    }

    private static <T> List<T> draw(TypedGenerator<T> generator, int draws) {
        List<T> values = new ArrayList<>(draws);
        for (int draw = 0; draw < draws; draw++) {
            values.add(generator.next());
        }
        return values;
    }

    private static <T> Function<T, Optional<Verdict>> always(Verdict verdict) {
        return value -> Optional.of(verdict);
    }

    private static Function<String, Verdict> through(Supplier<HttpSecurityValidator> route) {
        return value -> verdictOf(route.get(), value);
    }

    private static Verdict verdictOf(HttpSecurityValidator route, String value) {
        try {
            Optional<String> validated = route.validate(value);
            assertTrue(validated.isPresent(), "An accepted non-null value must be returned");
            return Verdict.ACCEPTED;
        } catch (UrlSecurityException rejection) {
            return Verdict.REJECTED;
        }
    }

    private static HttpSecurityValidator pathPipeline() {
        return new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter());
    }

    private static HttpSecurityValidator parameterPipeline() {
        return new URLParameterValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter());
    }

    /** The parameter pipeline of a deployment that reflects parameter values into response headers. */
    private static HttpSecurityValidator lineBreakRejectingParameterPipeline() {
        return new URLParameterValidationPipeline(
                SecurityConfiguration.builder().allowLineBreaksInParameterValues(false).build(),
                new SecurityEventCounter());
    }

    private static HttpSecurityValidator headerNamePipeline() {
        return new HTTPHeaderValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter(),
                ValidationType.HEADER_NAME);
    }

    private static HttpSecurityValidator headerValuePipeline() {
        return new HTTPHeaderValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter(),
                ValidationType.HEADER_VALUE);
    }

    /** The supported cookie route, given a cookie whose attributes satisfy every prefix rule. */
    private static Verdict cookieNameVerdict(String name) {
        return cookieVerdict(new Cookie(name, "value", "Secure; Path=/"));
    }

    private static Verdict cookieVerdict(Cookie cookie) {
        try {
            new CookiePrefixValidationStage().validateCookie(cookie);
            return Verdict.ACCEPTED;
        } catch (UrlSecurityException rejection) {
            return Verdict.REJECTED;
        }
    }

    /**
     * The verdict the RFC 6265 grammar assigns to an attack cookie: it is accepted exactly when
     * its name is a token and its value consists of cookie-octets. The attack names carry no
     * security prefix, so no prefix rule applies. The grammar is declared from the RFC rather than
     * read from the production character sets, so this claim does not follow production.
     */
    private static Optional<Verdict> cookieGrammarClaim(Cookie cookie) {
        boolean withinGrammar = COOKIE_NAME_TOKEN.matcher(cookie.name()).matches()
                && COOKIE_OCTETS.matcher(cookie.value()).matches();
        return Optional.of(withinGrammar ? Verdict.ACCEPTED : Verdict.REJECTED);
    }

    private static Verdict forwardedHostVerdict(String host) {
        ForwardedHeaderResolver resolver = new ForwardedHeaderResolver(
                ForwardedResolverConfig.builder().trustAll(true).build(), new SecurityEventCounter());
        return resolver.resolve(name -> "X-Forwarded-Host".equals(name) ? List.of(host) : null).host().isEmpty()
                ? Verdict.REJECTED
                : Verdict.ACCEPTED;
    }

    private static Verdict httpResultVerdict(HttpResult<String> result) {
        return result.isSuccess() ? Verdict.ACCEPTED : Verdict.REJECTED;
    }

    private static boolean carriesNullByte(String value) {
        return value.indexOf(NULL) >= 0 || value.contains("%00");
    }

    private static boolean carriesRawLineBreak(String value) {
        return value.contains("\r") || value.contains("\n");
    }

    /** The shell metacharacters of the boundary generator that the path character set does not admit. */
    private static boolean carriesNonPathShellCharacter(String value) {
        return value.contains("|") || value.contains("`") || value.contains(">");
    }

    private static boolean carriesInvalidEscape(String value) {
        return INVALID_ESCAPES.stream().anyMatch(value::contains);
    }

    private static String describe(Object value) {
        return GeneratorContractAssertions.preview(String.valueOf(value)).codePoints()
                .mapToObj(codePoint -> codePoint > 0x20 && codePoint < 0x7F
                        ? Character.toString(codePoint)
                        : "U+%04X".formatted(codePoint))
                .collect(Collectors.joining());
    }
}
