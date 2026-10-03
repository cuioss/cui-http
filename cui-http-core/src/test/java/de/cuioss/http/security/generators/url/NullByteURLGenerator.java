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
package de.cuioss.http.security.generators.url;

import de.cuioss.test.generator.TypedGenerator;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generator for URL paths containing null byte injection attacks.
 *
 * <p>The attack patterns are a fixed list, {@link #NULL_BYTE_URLS}: the position and the encoding
 * of the null byte (raw {@code \0} or percent-encoded {@code %00}) are what make each pattern an
 * attack, so they are curated literals rather than generated values.</p>
 *
 * <p>{@link #next()} walks that list in declaration order and starts over after the last entry.
 * A generator instance therefore emits every pattern once per {@code NULL_BYTE_URLS.size()}
 * calls, independent of any seed. A test that must cover the whole list iterates
 * {@link #NULL_BYTE_URLS} directly.</p>
 *
 * <h3>Null Byte Attack Database</h3>
 * <ul>
 *   <li><strong>Path truncation:</strong> {@code /api/users\0admin} - Null byte truncates path processing</li>
 *   <li><strong>URL encoded bypass:</strong> {@code /api/users%00admin} - Encoded null byte bypasses filters</li>
 *   <li><strong>File extension bypass:</strong> {@code /api/files\0.php} - Null byte truncates extension check</li>
 *   <li><strong>Directory traversal combination:</strong> {@code /api/download\0../../../etc/passwd} - Mixed attack vectors</li>
 *   <li><strong>MIME type confusion:</strong> {@code /api/upload/file.jpg\0.php} - Bypasses MIME validation</li>
 * </ul>
 *
 * <h3>Null Byte Attack Mechanics</h3>
 * <ul>
 *   <li><strong>String termination:</strong> C-style string processing terminates at null byte</li>
 *   <li><strong>Filter bypass:</strong> Security filters may not process beyond null byte</li>
 *   <li><strong>Path truncation:</strong> File system operations may truncate at null byte</li>
 *   <li><strong>Extension spoofing:</strong> File extension checks bypassed by null byte insertion</li>
 *   <li><strong>Encoding variations:</strong> Different null byte encodings bypass different filters</li>
 * </ul>
 *
 * <p>Thread-safe: the position is an atomic counter, so concurrent callers of one instance each
 * receive a list entry and no entry is skipped.</p>
 */
public class NullByteURLGenerator implements TypedGenerator<String> {

    /** Every null byte URL pattern this generator emits, in emission order. */
    public static final List<String> NULL_BYTE_URLS = List.of(
            "/api/users\0admin",
            "/api/users%00admin",
            "/api\0/users",
            "/api/users/123\0",
            "/api/files\0.php",
            "/api/download\0../../../etc/passwd",
            "/api/config%00.txt",
            "/api/upload/file.jpg\0.php",
            "/api/docs/readme\0.html",
            "/api/admin\0/secret",
            "/api/data%00/config",
            "/api/backup\0.tar.gz",
            "/api/logs/error.log\0",
            "/api/scripts%00.sh",
            "/api/images/photo.png\0.php"
    );

    private final AtomicInteger position = new AtomicInteger();

    @Override
    public String next() {
        return NULL_BYTE_URLS.get(Math.floorMod(position.getAndIncrement(), NULL_BYTE_URLS.size()));
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}