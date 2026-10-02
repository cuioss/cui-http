# PLAN-20: Test-Evidence Integrity — TLS Coverage and Fail-Closed Assertions

epic: quality-report-remediation
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no brief.
>
> **Merged 2026-09-01 from PLAN-20 (`https-mockwebserver-restoration`) and PLAN-21
> (`guarded-assertion-sweep`) at operator request.** PLAN-21's spec is retained as a superseded
> pointer; its row is `superseded`, not deleted. This is **not a weak merge** — see Charter.

## Objective

Make this project's test suite prove what it claims, on two surfaces where it currently does not.
First, **use** `@EnableMockWebServer(useHttps = true)` — the repository has **zero** tests exercising the
HTTPS path, so the HTTPS→HTTP downgrade refusal that `validated-redirect-following` shipped is asserted
only at the `RedirectPolicy` seam. ⚠️ **The dependency defect that made this impossible is already
fixed** (see deliverable 1); what remains is that nobody has written the tests. Second, sweep the test tree for assertions that
sit behind a guard with no fail-closed marker and convert every genuine instance, completing a sweep
PLAN-13 owed but never ran.

⛔ **In both cases the defect is that green does not mean verified.** A seam-level assertion proves the
*policy object* refuses a downgrade; it does not prove the *wired client* does. A guarded assertion that
never fires reports coverage over inputs it never checked.

## Charter: why these are one plan

Both halves are the same defect class: **a test suite that reports green over assertions which never
ran.** One half is a *missing* assertion (the TLS path cannot be exercised at all, so a shipped
security control's end-to-end evidence does not exist); the other is a *skipped* assertion (a guard
silently prevents the assert body from running). In both, green is indistinguishable from verified.

They also share one validating technique — **falsification** (deliverable 4) — which is what makes
this a genuine merge rather than two plans in a bag. An unfalsified fix in either half is
indistinguishable from no fix, because in both halves the defect *is* an assertion that does not fire.

## Deliverables

**Five deliverables, re-derived rather than summed.** The two source specs carried 9 between them;
the overlaps collapse: their two diagnose/align steps are one dependency fix, their two
coverage-addition steps land together, and their separate verification and record-reconciliation
steps are each one cross-cutting deliverable spanning both halves. Under the split guard at 5.

1. **Confirm at RUNTIME that the `mockwebserver3` / okhttp misalignment is already gone, and fix it
   only if it is not.** ⛔ **This deliverable is expected to be a NO-OP — do not budget it as a
   dependency fix.** An orchestrator re-check at HEAD on 2026-09-02 found the version mismatch already
   resolved: `cui-java-parent` 1.5.10 (`7807a00`, PR #173) moved `cui-test-mockwebserver-junit5` from
   **1.5.0 → 1.6**, and `mockwebserver3`, `okhttp-jvm` and `okhttp-tls` all now resolve at **5.5.0**.
   The bytecode agrees: `MockWebServer$SocketHandler` calls the **four**-parameter
   `Platform.configureTlsExtensions(SSLSocket, String, List, ByteString)`, which `okhttp-jvm 5.5.0`
   declares — the original `NoSuchMethodError` was against the three-parameter form that the
   1.5.0-era `mockwebserver3` called.
   ⚠️ **That re-check is bytecode-level, not runtime.** Matching descriptors prove *that* error cannot
   fire; they do **not** prove the server accepts a TLS connection. **Confirm it by running one
   `useHttps = true` test** (deliverable 2a serves as that confirmation). If it passes, record this
   deliverable as *verified unnecessary* with the evidence — a positive account, not a silent skip.
   ⛔ **If it does NOT pass, do not assume the old cause.** Re-derive from the actual failure: read the
   surefire `-output.txt` for the server-side thread's exception, because the client-visible symptom is
   only a connect timeout after the full retry budget (50–65 s). The `okhttp-tls` 5.5.0 pin in
   `cui-http-core/pom.xml`:45–52 carries a comment claiming alignment with the transitive okhttp —
   that claim now holds, but re-verify it rather than trusting the comment.
2. **Add the TLS coverage the fix unlocks**, in two parts that land together:
   (a) **one HTTPS smoke test** through `@EnableMockWebServer(useHttps = true)` — the guard that keeps
   the whole path from regressing unnoticed again, and the reason the fix is durable rather than
   one-off; and (b) **the HTTPS→HTTP downgrade refusal asserted end-to-end** through `HttpHandler` →
   `RedirectPolicy` → a real TLS server, replacing today's seam-only coverage. ⛔ For (b), assert
   **observable behaviour, not a reported flag**: the redirect must be refused at the wire, not merely
   reported as refusable.
3. **Triage the guarded-assertion candidates and convert every genuine instance** to the fail-closed
   form:
   ```java
   boolean matched = false;
   for (...) { if (applies) { matched = true; assertThat(...); } }
   assertTrue(matched, "no input exercised the assertion: " + describe(input));
   ```
   For a `startsWith`/`switch`-style dispatch over a declared entry set, add a terminal `else fail(...)`
   as well. ⛔ **The candidate list is a seed, not a verdict** — two of the twelve are already known to
   fall on opposite sides (see Claim Labels), so a plan that "fixes all 12" has not read them. Record
   the classification and its reason **per site**.
4. **Prove every new and converted assertion by falsification — both halves.** Restore the pre-fix
   form, confirm the new assertion **fails for the stated reason**, restore, confirm green, and record
   the pass/fail counts. ⛔ **This deliverable is why the two halves are one plan.** For half 3, force
   the guard never to match and confirm the `matched` assertion fires. For half 2, confirm the
   downgrade test fails against a policy that permits the downgrade — a TLS test that has never been
   observed to fail proves no more than the seam assertion it replaces. Lesson `2026-08-27-20-004`: *a
   regression test that has never been observed to fail has not been shown to test anything*, and this
   plan's entire subject is assertions that silently do not run.
5. **Reconcile the record so no stale claim of absence or coverage outlives the fix.** (a) Retire the
   gap notes the three TLS-blocked plans left in `ResilientHttpAdapterTest`,
   `ResilientHttpAdapterIntegrationTest`, `ETagAwareHttpAdapterIntegrationTest`,
   `HttpHandlerIntegrationTest` and `HttpHandlerRedirectTest` — ⛔ **but only those this plan actually
   closes; a note whose gap remains stays, corrected to say what is still missing.** Silently deleting
   it would restore exactly the false impression of coverage the notes exist to prevent. (b) Re-run the
   guarded-assertion sweep after the conversions and record the residual: how many sites remain and why
   each is a deliberate non-instance. A sweep not re-derived after the change has not been shown to
   have closed anything.

## Claim Labels

### The TLS half

- OBSERVED: The repository has **zero** tests using `@EnableMockWebServer(useHttps = true)`. Every
  MockWebServer test declares `useHttps = false` — verified at HEAD across `HttpHandlerRedirectTest`:74,
  `ResilientHttpAdapterIntegrationTest`:67, `ETagAwareHttpAdapterIntegrationTest`:67,
  `HttpHandlerIntegrationTest`:61. **The whole HTTPS test path is unexercised**, which is why the
  incompatibility stayed invisible.
- OBSERVED: `cui-http-core/pom.xml`:45–52 declares `com.squareup.okhttp3:okhttp-tls` at **5.5.0**, test
  scope, with a comment asserting version alignment. `cui-test-mockwebserver-junit5` is declared at
  :41–44 with **no version** (parent-managed).
- OBSERVED: `ResilientHttpAdapterTest`:564 carries an in-code statement that
  `@EnableMockWebServer(useHttps = true)` cannot accept a connection in this project.
- OBSERVED: **Three consecutive plans were blocked** — `plan-09-client-config-and-retry-hardening`,
  `client-converter-and-javadoc-accuracy` (PR #182), and `validated-redirect-following` (PR #186). Each
  handled it correctly by asserting at a seam and documenting the gap; none could close it. Lesson
  `2026-08-29-12-001`, two recorded recurrences.
- OBSERVED (re-checked 2026-09-02, supersedes two earlier HYPOTHESIS entries): **The version mismatch
  is already resolved.** `cui-java-parent` 1.5.10 (`7807a00`, PR #173) moved
  `cui-test-mockwebserver-junit5` 1.5.0 → **1.6**; `mockwebserver3`, `okhttp-jvm` and `okhttp-tls` all
  resolve at **5.5.0**. `MockWebServer$SocketHandler` calls the four-parameter
  `Platform.configureTlsExtensions(SSLSocket, String, List, ByteString)`, which `okhttp-jvm 5.5.0`
  declares; the recorded `NoSuchMethodError` was against the three-parameter form. The `okhttp-tls`
  5.5.0 pin's alignment comment now holds.
- HYPOTHESIS: HTTPS MockWebServer therefore **works** at HEAD and deliverable 1 is a no-op —
  confirm/refute by **running** one `useHttps = true` test (verify-at-outline). ⛔ **The OBSERVED entry
  above is bytecode-level evidence and does not establish this**: matching descriptors prove that one
  error cannot fire, not that the server accepts a connection.
- Verify-first clause: ⛔ **Run one `useHttps = true` test FIRST — the expected outcome is now that it
  PASSES.** The classpath re-check above says the dependency defect is gone, so the plan's likely shape
  is deliverables 2, 4 and 5 with 1 recorded as verified-unnecessary. ⛔ **If it fails instead, do not
  reach for the old cause.** The client-visible symptom is only a connect timeout after the full retry
  budget (50–65 s), and **the real cause appears only in the surefire `-output.txt`** — a run that
  reports a timeout and stops there has diagnosed nothing. Re-derive from the server-side thread's
  actual exception and re-scope deliverable 1 against what it names.

### The guarded-assertion half

- OBSERVED: The heuristic sweep at HEAD `9446171` returned **12 sites across 8 files** —
  `client/HttpMethodTest.java` (144, 156, 171), `security/core/ValidationTypeTest.java` (180),
  `security/generators/encoding/UnicodeAttackGeneratorTest.java` (121, 126),
  `security/generators/url/AttackURLParameterGeneratorTest.java` (91),
  `security/generators/url/InvalidURLGeneratorTest.java` (84),
  `security/generators/url/NullByteURLGeneratorTest.java` (74),
  `security/tests/UnicodeNormalizationAttackTest.java` (240, 430),
  `security/validation/NormalizationStageTest.java` (304).
- OBSERVED: **`UnicodeNormalizationAttackTest`:240 is a GENUINE instance.** A loop over
  `normalizationTests` guards `assertThrows` on `if (!test.equals(normalized))` with no `matched` flag —
  so if no case normalizes differently, the loop asserts nothing and the test passes green. ⚠️ It is in
  the file PLAN-13 edited for its NFKC work.
- OBSERVED: **`NullByteURLGeneratorTest`:74 is a FALSE POSITIVE.** Its guards accumulate into a `Set`
  and the assertion (`assertEquals(Set.of("raw","encoded"), forms, …)`) sits **outside** the loop over
  the full accumulated set — already the correct fail-closed shape.
- OBSERVED: PLAN-13 (PR #178) fixed this defect class **only at the two sites CodeRabbit flagged** and
  ran no corpus sweep. ⛔ This is the epic's own lesson `2026-08-27-07-002` — *the source report's named
  sites are the SEED of a sweep, never its population* — not applied to itself. This plan is the sweep
  that was owed.
- HYPOTHESIS: The remaining 10 sites split between the two classes above rather than introducing a
  third — confirm/refute by reading each (verify-at-outline). ⛔ **The heuristic over-collects by
  construction** (an `if`-guarded `assert` inside a loop with no `matched`/`fail`/`else` within 8
  lines), so a site whose assertion is outside the loop, or whose guard is exhaustive over a closed
  set, is not an instance.
- Verify-first clause: ⛔ **Re-run the sweep at HEAD before scoping** rather than working from the line
  numbers above. They were derived at `9446171`; any commit landing first shifts them, and a fix applied
  to a stale line number is worse than none. **The line numbers are provenance for the claim, not the
  work list.**
- Verify-first clause: ⛔ **A guard that mirrors a production predicate must name the same form the
  production code uses.** PLAN-13's original instance was an NFC-vs-NFKC mismatch: the test guarded on
  NFC while `DecodingStage` normalises URL paths with **NFKC**, so 3 of 8 inputs silently skipped. Where
  a converted site's guard paraphrases production behaviour, check it against the implementing source —
  a `matched` flag makes the skip **visible**, but it does not make a wrong guard right.

## Expected Surface

- OBSERVED: `cui-http-core/pom.xml` — the `cui-test-mockwebserver-junit5` and `okhttp-tls` declarations
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/handler/HttpHandlerRedirectTest.java` — the end-to-end downgrade assertion and its class-Javadoc gap note
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/adapter/ResilientHttpAdapterTest.java` — the gap note at :564
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/adapter/ResilientHttpAdapterIntegrationTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/adapter/ETagAwareHttpAdapterIntegrationTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/handler/HttpHandlerIntegrationTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/HttpMethodTest.java` — :144, :156, :171
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/tests/UnicodeNormalizationAttackTest.java` — the confirmed genuine instance at :240, plus :430
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/NormalizationStageTest.java` — :304
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/core/ValidationTypeTest.java` — :180
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/UnicodeAttackGeneratorTest.java` — :121, :126
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/generators/url/AttackURLParameterGeneratorTest.java` — :91
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/generators/url/InvalidURLGeneratorTest.java` — :84
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/generators/url/NullByteURLGeneratorTest.java` — :74, expected to resolve as a false positive and be left unchanged
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/client/dispatcher/` — the smoke test's dispatcher, if an existing one cannot serve it (verify-at-outline)
- ⛔ **No, not this plan's surface:** `cui-http-core/src/main/java/` — this plan changes **no production
  code**. It adds *evidence*, not behaviour. `RedirectPolicy.java` and `HttpHandler.java` were shipped by
  PLAN-17 and are correct. A test that cannot be made fail-closed, or a TLS assertion that cannot pass,
  without a production change has found a **second defect**: report it in the landing and re-scope
  explicitly rather than absorbing it.

## Dependencies and Sequencing

- Depends on: PLAN-17 (shipped, PR #186) — deliverable 2(b) asserts the control PLAN-17 shipped.
  PLAN-11, PLAN-12 and PLAN-13 (the test-tree plans) have all shipped.
- Overlaps with: none staged — this is the epic's only live plan.
- ⛔ **Adjacent — a parent-managed version bump has a documented cost in this repository.** Lesson
  `2026-08-26-20-001`: three bot parent bumps skipped the quality gate, and the resulting license-header
  churn produced a **229-file diff** that exceeded CodeRabbit's 100-file limit and Sourcery's
  diff-character cap — **both bots declined to review a HIGH-severity security PR**. If deliverable 1
  moves a parent-managed version, run the full quality gate and **check the resulting diff size before
  pushing**; a large generated-content diff will silently cost this plan its review coverage.
- Adjacent to: `doc/client-handlers-readme.adoc`, `doc/test-framework-structure.adoc` and
  `doc/http-security/specification/testing.adoc` — ⛔ do NOT edit; WS-05 is closed and already carries
  unowned re-check debt. ⚠️ **If this plan adds or renames a test class, report it in the landing**:
  PLAN-13's landing already left an unowned test-class inventory drift, and a second undocumented
  addition compounds it.
- Adjacent to: the `.formatted()` precedence lesson (`2026-08-31-17-001`). ⛔ **It needs no work** — an
  orchestrator sweep at HEAD found **zero** open instances; the two `+ "%.1f".formatted(...)` sites carry
  no placeholder in the leading literal and are correct. Do not re-open it.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-20-test-evidence-integrity.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
