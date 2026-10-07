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
package de.cuioss.http.security.database;

import de.cuioss.http.security.core.UrlSecurityFailureType;

import java.util.List;

/**
 * Database of Spring CVE attack patterns.
 *
 * <p>Encodes <a href="https://nvd.nist.gov/vuln/detail/CVE-2020-5410">CVE-2020-5410</a>: Spring
 * Cloud Config Server (2.2.x before 2.2.3, 2.1.x before 2.1.9 and older unsupported versions)
 * served arbitrary files through a specially crafted URL. The request path
 * {@code /{name}/{profile}} carries the traversal in the {@code name} segment with every path
 * separator double-encoded ({@code ..%252F}), followed by an encoded fragment marker
 * ({@code %23foo}) that cuts off the suffix the server appends, and a profile segment
 * ({@code /development}).</p>
 *
 * <h3>Provenance of the payloads</h3>
 * <ul>
 *   <li>{@link #CVE_2020_5410_NUCLEI_TRAVERSAL_PASSWD} is the request path of the public
 *       ProjectDiscovery nuclei template {@code http/cves/2020/CVE-2020-5410.yaml}, verbatim.</li>
 *   <li>{@link #CVE_2020_5410_DEPTH_4_TRAVERSAL_PASSWD} is the same exploit with four parent
 *       references instead of eleven. It is a variant DERIVED from the template for this
 *       database, not a string quoted from a published source, and differs from the template in
 *       traversal depth only.</li>
 * </ul>
 *
 * <h3>What the declared failure type means</h3>
 * <p>Both entries declare {@code PATH_TRAVERSAL_DETECTED}, the pipeline's real first verdict: the
 * wire-form pass of {@code PatternMatchingStage}, which runs before {@code DecodingStage},
 * recognizes the double-encoded parent reference {@code ..%252F} as an encoded traversal. The
 * payloads are therefore rejected for the traversal the CVE describes, not merely for carrying a
 * double-encoding layer ({@code DecodingStage} would report {@code DOUBLE_ENCODING} for the same
 * {@code %25XX} sequences, but is never reached).</p>
 *
 * @since 1.0
 */
public class SpringCVEAttackDatabase implements AttackDatabase {

    private static final String DESCRIPTION_PREFIX = "CVE-2020-5410: Spring Cloud Config Server directory traversal through a specially crafted URL. ";
    private static final String RATIONALE = "PATH_TRAVERSAL_DETECTED is expected because the wire-form pass of PatternMatchingStage, which runs before decoding, matches the double-encoded parent reference (..%252F) as an encoded path traversal pattern; the payload is rejected there, before DecodingStage could report the same %25XX sequences as double encoding.";

    // CVE-2020-5410: request path of the nuclei template, eleven parent references
    public static final AttackTestCase CVE_2020_5410_NUCLEI_TRAVERSAL_PASSWD = new AttackTestCase(
            "/..%252F..%252F..%252F..%252F..%252F..%252F..%252F..%252F..%252F..%252F..%252Fetc%252Fpasswd%23foo/development",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            DESCRIPTION_PREFIX + "The application-name segment carries eleven double-encoded parent references down to /etc/passwd; the encoded fragment marker (%23foo) discards the file suffix the server appends, and /development is the profile segment. This is the request path of the public nuclei template for the CVE.",
            RATIONALE
    );

    // CVE-2020-5410: the same exploit with four parent references
    public static final AttackTestCase CVE_2020_5410_DEPTH_4_TRAVERSAL_PASSWD = new AttackTestCase(
            "/..%252F..%252F..%252F..%252Fetc%252Fpasswd%23foo/development",
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            DESCRIPTION_PREFIX + "Shallow variant with four double-encoded parent references, sufficient when the configuration repository sits four levels below the file-system root; fragment marker and profile segment as in the template form.",
            RATIONALE
    );

    private static final List<AttackTestCase> ALL_ATTACK_TEST_CASES = List.of(
            CVE_2020_5410_NUCLEI_TRAVERSAL_PASSWD,
            CVE_2020_5410_DEPTH_4_TRAVERSAL_PASSWD
    );

    @Override
    public Iterable<AttackTestCase> getAttackTestCases() {
        return ALL_ATTACK_TEST_CASES;
    }

    @Override
    public String getDatabaseName() {
        return "Spring CVE Attack Database";
    }

    @Override
    public String getDescription() {
        return "Spring Cloud Config Server CVE-2020-5410 directory traversal patterns using double-encoded path separators";
    }

    /**
     * JUnit 5 ArgumentsProvider for use with {@code @ArgumentsSource}.
     *
     * @since 1.0
     */
    public static class ArgumentsProvider extends AttackDatabase.ArgumentsProvider<SpringCVEAttackDatabase> {
        // Implementation inherited - uses reflection to create database instance
    }
}
