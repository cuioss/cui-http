# PLAN-23: End-to-End Proof That `FORWARD_TO_ALLOWLISTED` Actually Forwards

epic: quality-report-remediation
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no brief.

## Objective

Restore the end-to-end proof that `CredentialForwarding.FORWARD_TO_ALLOWLISTED` **does** forward
credentials across an allowlisted cross-origin hop over HTTPS. PLAN-22's CWE-319 cleartext guard was
correct and necessary — and it **flipped the only E2E fixture that proved the positive direction**.
`forwardStrategyShouldKeepCredentialsCrossOrigin` was renamed to
`forwardStrategyShouldDropCredentialsToCleartextAllowlistedHost` and now asserts **DROP**, because its
target was cleartext.

⛔ **So the opt-in's whole purpose is currently unproven end-to-end.** A caller enables
`FORWARD_TO_ALLOWLISTED` precisely so credentials survive a cross-host hop; nothing now demonstrates
that they do. The rule holds at the `RedirectPolicy` unit seam and same-origin-over-TLS is covered
E2E, so **the gap is narrow but real** — and it is exactly the direction a regression would be silent
in: a bug that over-strips credentials breaks callers while every remaining test stays green.

⚠️ **This is the epic's own falsification rule biting.** A guard proven only in its DROP direction is
half-proven; the surviving assertions are all satisfied by an implementation that never forwards
anything.

## Deliverables

1. **Build a second-host TLS fixture.** The proof needs **two** TLS-terminated servers — an origin and
   an allowlisted cross-origin target — so a redirect can cross a host boundary while both hops stay
   `https`. ⛔ **Establish first whether `@EnableMockWebServer(useHttps = true)` can provide two
   servers in one test class**, and if it cannot, what the sanctioned alternative is. **That question
   is the plan's main risk and belongs at outline, not at execute.**
2. **Assert the positive direction end-to-end**: under `FORWARD_TO_ALLOWLISTED`, with the target host
   on the allowlist and **both hops over TLS**, `Authorization` and `Cookie` **arrive at the
   cross-origin target**. ⛔ **Assert on what the second server RECEIVED**, not on a policy verdict —
   a `RedirectPolicy` assertion is what already exists and is not what is missing.
3. **Keep the negative controls that PLAN-22 established**, and state the matrix the pair now covers:
   cleartext target → dropped (the flipped fixture); TLS allowlisted cross-origin → forwarded (new);
   non-allowlisted cross-origin → hop refused. ⛔ **Do not weaken or re-flip
   `forwardStrategyShouldDropCredentialsToCleartextAllowlistedHost`** — it guards the CWE-319 fix and
   its current assertion is correct.
4. **Falsify the new assertion.** Temporarily neutralise the forwarding path, confirm the new test
   **fails for its stated reason**, restore, confirm green, and record the pass/fail counts. ⛔ **A
   test written to close a proof gap and never observed failing has not closed it** — this plan exists
   because a control was flipped without one, and an unfalsified replacement repeats that.

Four deliverables — well under the split guard.

## Claim Labels

- OBSERVED: `HttpHandlerRedirectTest.java`:611 now carries
  `forwardStrategyShouldDropCredentialsToCleartextAllowlistedHost`, asserting DROP. The former
  `forwardStrategyShouldKeepCredentialsCrossOrigin` **no longer exists anywhere in the test tree** —
  grep at HEAD `384a22c` returns only the renamed method.
- OBSERVED: `RedirectPolicyTest.java`:336–338 asserts *"Should forward credentials on an allowlisted
  cross-host hop under FORWARD_TO_ALLOWLISTED"* — but at the **unit seam**, calling
  `forwardsCredentials(ORIGIN, ALLOWLISTED)` directly. ⛔ **That is the assertion this plan must NOT
  duplicate**: it proves the policy object's verdict, not that the wired client puts the header on the
  wire. The same seam-vs-wire distinction PLAN-20 closed for the TLS downgrade refusal.
- OBSERVED: `HttpHandlerRedirectTest.java`:644 covers *"FORWARD_TO_ALLOWLISTED should still refuse a
  non-allowlisted cross-host hop"*, and :624 parameterises both strategies over a shared case. Those
  are the negative controls to preserve.
- OBSERVED: `HttpHandlerHttpsIntegrationTest` (added by PLAN-20) is the project's **only**
  `@EnableMockWebServer(useHttps = true)` class, and it holds **one** server plus two dispatchers
  (`TestContentDispatcher`, `RedirectDispatcher`) — so a two-server TLS arrangement has no precedent
  in this repository.
- HYPOTHESIS: `@EnableMockWebServer(useHttps = true)` supports two servers in one class, or a second
  can be started directly — confirm/refute at outline (verify-at-outline). ⛔ **If it cannot, the
  deliverable is NOT abandoned**: report what the harness supports, and cover the positive direction
  at the closest seam that observes **the outgoing request**, stating plainly what remains unproven.
  Silently reverting to a `RedirectPolicy` assertion would recreate exactly the gap this plan closes.
- Verify-first clause: ⛔ **Re-derive the coverage matrix at HEAD before scoping.** This spec's reading
  of what is and is not covered was taken at `384a22c`; PLAN-22's own landing reports its finding
  `22aa41` as *accepted, not fixed*, so confirm the gap still exists rather than inheriting it. **If a
  later commit already closed it, say so and stop** — that is a complete outcome, not a failed plan.
- Verify-first clause: ⛔ **The cleartext guard is not in scope and must not be relaxed to make a test
  pass.** `RedirectPolicy.forwardsCredentials` returns `false` for any `http` target **before** the
  same-origin/allowlist decision (`RedirectPolicy.java`:226–231). A new positive test must therefore
  use an **https** target. A test that only passes after weakening that guard has re-opened CWE-319.

## Expected Surface

- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/handler/HttpHandlerRedirectTest.java` — the positive assertion and the preserved negative controls
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/handler/HttpHandlerHttpsIntegrationTest.java` — the existing TLS-enabled class, the likeliest host for a two-server arrangement
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/dispatcher/RedirectDispatcher.java` — a cross-host redirect route, if the existing routes cannot express one
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/client/handler/RedirectPolicyTest.java` — only if the unit-seam matrix needs a comment pointing at the new E2E proof (verify-at-outline)
- ⛔ **No, not this plan's surface:** `cui-http-core/src/main/java/de/cuioss/http/client/handler/RedirectPolicy.java` and `HttpHandler.java`. **This plan adds evidence, not behaviour.** ⛔ A production change here means either the guard is wrong (a finding to report and re-scope, never to absorb) or the test is asserting the wrong thing.
- ⛔ **No, not this plan's surface:** `doc/client-handlers-readme.adoc` — PLAN-22 documented the API and it is accurate. Report in the landing if the coverage matrix changes what the docs should say.

## Dependencies and Sequencing

- Depends on: PLAN-22 (shipped, PR #191) — this closes the gap its CWE-319 fix opened. PLAN-20
  (shipped, PR #189) — it made `@EnableMockWebServer(useHttps = true)` usable at all, which is the
  precondition for deliverable 1.
- Overlaps with: none. This is the epic's only live plan.
- ⚠️ **Adjacent — the `mockwebserver3`/okhttp history.** The TLS harness was unusable project-wide
  until PLAN-20, and three plans routed around it while a stale gap note claimed a cause that had
  already been fixed. ⛔ **If a two-server arrangement fails, diagnose it from the surefire
  `-output.txt` server-side exception** — the client-visible symptom is a connect timeout, and a run
  that reports a timeout and stops there has diagnosed nothing.
- ⛔ **Adjacent — five ADR-worthy decisions in this epic have no record**, because `adr-propose`
  carries `lane: off` under the standard posture. This plan settles no new architectural decision, so
  it should add none; if it does, report it rather than letting the debt grow to six silently.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-23-cross-origin-credential-forwarding-proof.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
