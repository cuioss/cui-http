# Landing Analysis: PLAN-17 — Validated Redirect Following

epic: quality-report-remediation
workstream: WS-03
pr: 186 (merged via merge queue; `main` at `9446171`)

> Landing record for one shipped plan. Every claim below was corroborated against ground truth
> before it was recorded. This is the epic's final plan.

## Corroboration Summary

`inbox landing-check` on `validated-redirect-following-008.md`: **`complete: true`,
`missing_keys[0]`**. Fifth complete block in six landings.

| Claim | Verdict | Evidence |
|---|---|---|
| PR #186 merged as `9446171` | **corroborated** | `git log origin/main` head |
| ⛔ **The loop-placement question was settled the RIGHT way** | **corroborated** | the loop lives in `HttpHandler` (`send` at :554 with `while (true)` at :559; `sendAsync` at :592 delegating to `sendAsyncHop` at :606), **and `ETagAwareHttpAdapter`:688 now calls `httpHandler.sendAsync(...)` where it previously called `httpClient.sendAsync(...)`** — its own `httpClient` field was removed |
| Intermediate bodies are discarded | **corroborated** | `discardingIntermediateBodies(bodyHandler)` wraps on **both** routes (:557 sync, :593 async), documented as wrapping exactly once |
| The allowlist no longer escapes | **corroborated** | defensive copy at the constructor boundary (:130) plus `Collections.unmodifiableSet(allowedHosts)` at :149 |
| `CredentialForwarding` is a real public enum with a secure default | **corroborated** | `RedirectPolicy.java`:329; the check at :215 reads `isSameOrigin(from, to) \|\| credentialForwarding == FORWARD_TO_ALLOWLISTED`, and the Javadoc states it **never changes a refuse verdict** — consistent with the code |
| The raw-client path stays fail-secure | **corroborated** | `HttpHandler.java`:402 and :438 — no JDK redirect policy is configured, `Redirect.NEVER` deliberately left in place, follow loop bypassed |
| `HTTP-117` obliged a `doc/LogMessages.adoc` row | **corroborated** | `HttpLogMessages.java`:124 `.identifier(117)`; `doc/LogMessages.adoc`:16 range widened to `HTTP-110–117` and row at :77 |
| Two coverage boundaries open | accepted as reported, **self-disclosed** | see below |

## ⛔ The open design question was answered, not assumed

PLAN-17's spec carried this as its verify-first clause: *"a loop placed only in `HttpHandler`'s ping
methods would leave `ETagAwareHttpAdapter.java`:691 unprotected and would be a false fix."*

**The plan settled it against both send sites and took the harder, correct option**: rather than
duplicating a loop at the adapter, it removed the adapter's private `httpClient` field and routed it
through `httpHandler.sendAsync`. `ResilientHttpAdapter` inherits coverage by delegation. The single
uncovered path — a caller taking the raw client from `createHttpClient()` — keeps `Redirect.NEVER`
and therefore follows nothing, which is fail-secure, asserted in tests, and documented.

This is the clause working exactly as the verify-first contract intends: the spec named the trap, the
outline read both sites, and the shipped design is the one that does not fall into it.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Hop validator, same-origin + allowlist, preserving downgrade refusal and scheme policy | shipped-as-specified | `RedirectPolicy.java` (+415), `RedirectNotAllowedException.java` (+121) |
| 2. Bounded loop applying it to every hop | shipped-as-specified | `HttpHandler.java` (+530); `maxHops` bound; both send routes covered |
| 3. Loop-placement decision settled and recorded | shipped-as-specified | see above |
| 4. Builder opt-in, defaulting to empty | shipped-as-specified | `RedirectPolicy` builder; secure defaults |
| 5. Tests (same-origin / cross-host / cross-port / cross-scheme / allowlisted / downgrade / bound) | shipped-**one gap, disclosed** | `HttpHandlerRedirectTest` (+805), `RedirectPolicyTest` (+398), `RedirectDispatcher` (+278), `ETagAwareHttpAdapterIntegrationTest` (+189). ⛔ The HTTPS→HTTP downgrade is **not** asserted end-to-end |
| 6. Replace the forward-pointing Javadoc | shipped-as-specified | `HttpStatusFamily` (+28), `adapter/package-info` (+51) rewritten to shipped behaviour |

**Added beyond the six, by operator decision:** `CredentialForwarding` — credential stripping on
cross-origin hops became strategy-configurable rather than unconditional, with `STRIP_ON_CROSS_ORIGIN`
as the secure default. ⚠️ **The widening is bounded in the safe direction and the code backs the
claim**: the opt-in changes what the client *carries*, never *where it talks* — it touches no `refuse`
verdict, and same-origin hops forward headers unchanged under every strategy.

**Scope widened to `multi_module`, which the spec anticipated as possible.** The `HTTP-117` WARN record
obliged a `doc/LogMessages.adoc` row after refine had asserted the documentation module would not be
touched. Operator-accepted at the outline review gate — declared drift, not silent drift.

## Surface: realized vs declared

Realized 14 files against 8 declared. Both HYPOTHESIS entries resolved to real edits
(`adapter/HttpAdapter.java` was **not** touched — the adapter change went through
`ETagAwareHttpAdapter` instead; `handler/package-info.java` likewise untouched). Undeclared:
`client/HttpLogMessages.java`, `handler/RedirectPolicy.java` and `handler/RedirectNotAllowedException.java`
(both **new files** the spec called for without naming), `handler/HttpHandlerTest.java`, the new
`HttpHandlerRedirectTest`/`RedirectPolicyTest`, `adapter/ETagAwareHttpAdapterIntegrationTest.java`, and
`doc/LogMessages.adoc`.

**No collision** — PLAN-17 was the only plan in flight for the whole of its finalize. The
`doc/LogMessages.adoc` touch would have been the risk had PLAN-19 still been running; it had shipped.

## Metrics and Anomalies

- 4,865,380 tokens / 14h34m wall — the epic's second-most-expensive plan. 6,765 tests.
- ⛔ **Eight defects were found AFTER implementation was complete, every one by the review loop.**
  Two are genuinely serious: **undiscarded intermediate redirect bodies leaking connections under
  streaming handlers**, and **a mutable egress allowlist escaping through `getAllowedHosts()`** — an
  egress policy that callers could mutate is the policy failing open. The others: unsanitized
  remote-controlled `Location` reaching log sinks; incomplete RFC 9110 representation-metadata
  stripping; a test fixture duplicating the production followable-status set; an overclaiming test
  comment; a stale `LogMessages.adoc` WARN range; an unbounded target URI in refusal messages.
- ⛔ **The review loop hit its full 3/3 loop-back ceiling and stopped by operator choice, not by
  convergence** — every fix push drew a fresh round. That is a termination-by-budget, not a clean bill.

## Routing and Merge Behavior

- **Review:** CodeRabbit reviewed through `bbace1d`; Sonar 2 new-code issues fixed **at source**.
- ⛔ **Two coverage boundaries, both self-disclosed rather than papered over:**
  1. `pre-submission-self-review`, `finalize-step-simplify` and `finalize-step-security-audit` last
     ran at `35e5766`; four later commits were never re-swept, and their records keep the older SHA.
  2. `92b7e20` and `544a448` carry **no bot review at all**.
- ⚠️ **A suppression was landed and then withdrawn on operator challenge**, and the correction is the
  interesting part: `// NOSONAR java:S4449` on `sanitizeForMessage` was placed **above the
  declaration**, where Sonar honours nothing, and carried a rationale that was itself wrong. The
  finding was a **true positive** — `resolveTarget` declared `location` `@Nullable` and passed it to a
  non-null parameter. The shipped fix corrects the declaration and removes the suppression entirely.
- **No rate-limit incident.** Unlike PLAN-04, PLAN-10 and PLAN-18.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` = `186`; `landing` = `landings/PLAN-17.md`
- [x] ⛔ **Validated-redirect-following defect CLOSED** — the epic's largest self-created capability gap.
- [x] **New Open Defect** — the two unswept coverage boundaries.
- [x] **New Open Defect** — `lane: off` steps remaining in the composed list, and the owed ADR pass.
- [x] **Scope-creep guard gap now has a root cause** — promoted as a lesson rather than left as an
      epic observation recorded twice.
- [x] resume_anchor updated; both generated blocks regenerated

## Follow-Ups

1. ⛔ **An ADR pass is owed.** `adr-propose` carries `lane: off` yet stayed in the composed step list
   (as do `preference-emitter` and `print-phase-breakdown`), so it was recorded `skipped` on that
   marker. Its Signal Gate **would** have fired, and this plan settled genuinely ADR-worthy decisions:
   **the loop placement, same-origin-by-default egress policy, and the `CredentialForwarding`
   strategy**. ⚠️ **The lane-off-but-still-listed shape may itself be a compose bug** — a step that is
   off should not be composed in and then skipped on a marker.
2. ⛔ **`@EnableMockWebServer(useHttps = true)` has now blocked TLS coverage for the THIRD consecutive
   plan** (PLAN-10, PLAN-17 twice over). The HTTPS→HTTP downgrade refusal — a security guarantee this
   plan exists to provide — is still not asserted end-to-end. Covered at the `RedirectPolicy`
   validation seam and documented in `HttpHandlerRedirectTest`'s class Javadoc.
3. **Two unswept coverage boundaries** (above) mean the merged tree was not fully re-analysed after
   its last four commits. Nothing suggests a defect; the point is that the absence of findings there
   is unmeasured rather than clean.
4. **Seven candidate lessons** dispositioned individually — see the epic decision log.
