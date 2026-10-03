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
 * Generator for URL parameters containing null byte injection attacks.
 *
 * <p>The attack patterns are a fixed list, {@link #NULL_BYTE_PARAMETERS}. {@link #next()} walks
 * that list in declaration order and starts over after the last entry, so a generator instance
 * emits every pattern once per {@code NULL_BYTE_PARAMETERS.size()} calls, independent of any
 * seed. A test that must cover the whole list iterates {@link #NULL_BYTE_PARAMETERS} directly.</p>
 *
 * <p>Thread-safe: the position is an atomic counter, so concurrent callers of one instance each
 * receive a list entry and no entry is skipped.</p>
 */
public class NullByteInjectionParameterGenerator implements TypedGenerator<String> {

    /** Every null byte parameter pattern this generator emits, in emission order. */
    public static final List<String> NULL_BYTE_PARAMETERS = List.of(
            "param=value\0admin",
            "data=%00admin",
            "file=config\0",
            "user=test%00",
            "name=normal%00malicious",
            "path=safe.txt\0../../etc/passwd",
            "document=report.pdf%00.php",
            "upload=image.jpg\0shell.php",
            "config=settings.xml%00backup.sql",
            "log=access.log\0../../../sensitive.data",
            "backup=data.zip%00.exe",
            "script=normal.js\0malicious.php",
            "template=page.html%00admin.jsp",
            "resource=public.css\0private.cfg"
    );

    private final AtomicInteger position = new AtomicInteger();

    @Override
    public String next() {
        return NULL_BYTE_PARAMETERS.get(
                Math.floorMod(position.getAndIncrement(), NULL_BYTE_PARAMETERS.size()));
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}