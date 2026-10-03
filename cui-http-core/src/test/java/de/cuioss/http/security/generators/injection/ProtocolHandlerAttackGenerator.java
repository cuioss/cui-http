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
package de.cuioss.http.security.generators.injection;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

/**
 * Generator for protocol handler attack patterns.
 *
 * <p>
 * Every value starts with one of the protocol handler schemes the library enforces -
 * {@code javascript:}, {@code vbscript:}, {@code data:} or {@code file:}
 * ({@code SecurityDefaults.PROTOCOL_HANDLER_SCHEMES}) - spelled raw, in another letter case, or
 * percent-encoded, and continues with an otherwise legal path. The scheme is the only rejectable
 * property of a value: none carries a traversal sequence, a character outside the path character
 * set, a control character or a null byte.
 * </p>
 *
 * <p>
 * The scheme match rejects only when {@code failOnSuspiciousPatterns} is enabled (the
 * {@code strict()} and {@code paranoid()} presets); the verdict is then
 * {@code SUSPICIOUS_PATTERN_DETECTED}. Schemes outside the enforced set - {@code http:},
 * {@code ftp:}, custom schemes - are rejected by no preset and are therefore not generated.
 * </p>
 *
 * <h3>Attack Categories Generated</h3>
 * <ul>
 *   <li><strong>Javascript protocol</strong>: javascript: scheme attacks</li>
 *   <li><strong>VBScript protocol</strong>: vbscript: scheme attacks</li>
 *   <li><strong>Data URI exploitation</strong>: Malicious data: scheme usage</li>
 *   <li><strong>File protocol abuse</strong>: file: scheme for local access</li>
 *   <li><strong>Case manipulation</strong>: the scheme in upper or mixed case</li>
 *   <li><strong>Scheme encoding</strong>: the scheme fully or partially percent-encoded</li>
 *   <li><strong>Nested protocols</strong>: one enforced scheme carrying another</li>
 *   <li><strong>Scheme with authority</strong>: file: with a host and a path</li>
 * </ul>
 *
 * Implements: Task G-Protocol from HTTP verification test generators
 *
 * @author Claude Code Generator
 * @since 1.0
 */
public class ProtocolHandlerAttackGenerator implements TypedGenerator<String> {

    /** The number of attack categories {@link #next()} selects among. */
    public static final int ATTACK_CATEGORY_COUNT = 8;

    private final TypedGenerator<Integer> attackCategoryGen = Generators.integers(1, ATTACK_CATEGORY_COUNT);
    private final TypedGenerator<Integer> hostSelector = Generators.integers(1, 6);
    private final TypedGenerator<Integer> pathSelector = Generators.integers(1, 6);

    @Override
    public String next() {
        return switch (attackCategoryGen.next()) {
            case 1 -> generateJavaScriptProtocolAttack();
            case 2 -> generateVbScriptProtocolAttack();
            case 3 -> generateDataUriExploitation();
            case 4 -> generateFileProtocolAttack();
            case 5 -> generateProtocolCaseManipulation();
            case 6 -> generateProtocolEncodingAttacks();
            case 7 -> generateNestedProtocolAttacks();
            default -> generateFileProtocolWithAuthority();
        };
    }

    private String generateJavaScriptProtocolAttack() {
        return switch (Generators.integers(1, 6).next()) {
            case 1 -> "javascript:alert('XSS')" + generatePath();
            case 2 -> "javascript:eval(String.fromCharCode(97,108,101,114,116,40,39,88,83,83,39,41))/admin";
            case 3 -> "javascript:window.location='http://evil.com'";
            case 4 -> "javascript:document.location.href='malicious.com'" + generatePath();
            case 5 -> "javascript:fetch('/etc/passwd').then(console.log)";
            default -> "javascript:/*comment*/alert('XSS')" + generatePath();
        };
    }

    private String generateVbScriptProtocolAttack() {
        return switch (Generators.integers(1, 3).next()) {
            case 1 -> "vbscript:msgbox('XSS')" + generatePath();
            case 2 -> "vbscript:execute('msgbox(1)')";
            default -> "vbscript:window.location='http://evil.com'";
        };
    }

    private String generateDataUriExploitation() {
        return switch (Generators.integers(1, 6).next()) {
            case 1 -> "data:text/html,%3Cscript%3Ealert('XSS')%3C/script%3E";
            case 2 -> "data:application/javascript,alert('XSS')" + generatePath();
            case 3 -> "data:text/html;base64,PHNjcmlwdD5hbGVydCgnWFNTJyk8L3NjcmlwdD4=";
            case 4 -> "data:image/svg+xml,%3Csvg%3E%3Cscript%3Ealert('XSS')%3C/script%3E%3C/svg%3E";
            case 5 -> "data:text/plain,etc/passwd";
            default -> "data:,admin/config";
        };
    }

    private String generateFileProtocolAttack() {
        return switch (Generators.integers(1, 6).next()) {
            case 1 -> "file:///etc/passwd";
            case 2 -> "file:////etc/passwd";
            case 3 -> "file:///etc/hosts";
            case 4 -> "file:///c:/windows/win.ini";
            case 5 -> "file:///etc/shadow";
            default -> "file:" + generatePath();
        };
    }

    private String generateProtocolCaseManipulation() {
        return switch (Generators.integers(1, 6).next()) {
            case 1 -> "JAVASCRIPT:alert('XSS')" + generatePath();
            case 2 -> "JavaScript:alert('XSS')";
            case 3 -> "jAvAsCrIpT:alert(1)" + generatePath();
            case 4 -> "DATA:text/html,alert(1)";
            case 5 -> "FILE:///etc/passwd";
            default -> "VBScript:msgbox(1)";
        };
    }

    private String generateProtocolEncodingAttacks() {
        return switch (Generators.integers(1, 6).next()) {
            case 1 -> "%6a%61%76%61%73%63%72%69%70%74:alert('XSS')" + generatePath();
            case 2 -> "j%61vascript:alert(1)";
            case 3 -> "javascript%3Aalert(1)";
            case 4 -> "%64%61%74%61:text/html,alert(1)";
            case 5 -> "%66%69%6c%65:///etc/passwd";
            default -> "%76%62%73%63%72%69%70%74:msgbox(1)";
        };
    }

    private String generateNestedProtocolAttacks() {
        return switch (Generators.integers(1, 4).next()) {
            case 1 -> "javascript://javascript:alert('XSS')";
            case 2 -> "data:text/html,javascript:alert(1)";
            case 3 -> "javascript:location='data:text/html,alert(1)'";
            default -> "file://file:///etc/passwd";
        };
    }

    private String generateFileProtocolWithAuthority() {
        return switch (Generators.integers(1, 3).next()) {
            case 1 -> "file://" + generateHost() + generatePath();
            case 2 -> "file://localhost" + generatePath();
            default -> "file://admin:password@" + generateHost() + generatePath();
        };
    }

    private String generateHost() {
        return switch (hostSelector.next()) {
            case 1 -> "evil.com";
            case 2 -> "malicious.com";
            case 3 -> "attacker.com";
            case 4 -> "evil.site";
            case 5 -> "malicious.host";
            default -> "evil.domain";
        };
    }

    private String generatePath() {
        return switch (pathSelector.next()) {
            case 1 -> "/etc/passwd";
            case 2 -> "/admin/config";
            case 3 -> "/etc/hosts";
            case 4 -> "/sensitive";
            case 5 -> "/admin";
            default -> "/config";
        };
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
