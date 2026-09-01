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
package de.cuioss.http.client.handler;

import de.cuioss.http.client.handler.RedirectPolicy.RedirectRefusal;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.net.URI;

/**
 * Raised when a redirect hop is refused, naming the hop and the reason it was not followed.
 * <p>
 * Unchecked, because a refusal is a configuration verdict rather than a transport failure: the
 * remote endpoint redirected somewhere the caller's {@link RedirectPolicy} does not permit, and no
 * retry of the same request against the same policy can succeed.
 * {@code HttpErrorCategory.fromException} classifies it — like every non-{@code IOException} — as
 * the non-retryable {@code CONFIGURATION_ERROR}, so no change to that classifier is required.
 *
 * <h3>Usage Example</h3>
 * <pre>
 * try {
 *     HttpResponse&lt;String&gt; response = handler.sendFollowingRedirects(request, bodyHandler);
 * } catch (RedirectNotAllowedException e) {
 *     LOGGER.warn("Refused redirect %s -&gt; %s (%s)", e.getFrom(), e.getTo(), e.getReason());
 * }
 * </pre>
 *
 * @since 2.2
 * @see RedirectPolicy
 */
@Getter
public class RedirectNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The URI the refused redirect response was received from.
     *
     * @return the source URI of the refused hop, never {@code null}
     */
    private final URI from;

    /**
     * The redirect target that was refused, or {@code null} when the refusal was
     * {@link RedirectRefusal#TOO_MANY_HOPS} — the hop budget is exhausted before any particular
     * target is evaluated, so there is no single target to name.
     *
     * @return the refused target URI, or {@code null} for a hop-budget refusal
     */
    private final @Nullable URI to;

    /**
     * Why the hop was refused.
     *
     * @return the refusal reason, never {@code null}
     */
    private final RedirectRefusal reason;

    /**
     * Creates a refusal naming the hop and its reason.
     *
     * @param from   the URI the refused redirect response was received from
     * @param to     the refused redirect target, or {@code null} for
     *               {@link RedirectRefusal#TOO_MANY_HOPS}
     * @param reason why the hop was refused
     */
    public RedirectNotAllowedException(URI from, @Nullable URI to, RedirectRefusal reason) {
        super("Refusing to follow redirect from " + from + " to " + to + ": " + reason);
        this.from = from;
        this.to = to;
        this.reason = reason;
    }
}
