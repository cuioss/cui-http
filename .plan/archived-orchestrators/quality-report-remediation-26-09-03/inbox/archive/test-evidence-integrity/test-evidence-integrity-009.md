envelope_version=1
sender_type=plan
sender_id=test-evidence-integrity
epic=quality-report-remediation
kind=landing
created=2026-09-02T14:09:04Z

# Landing: PLAN-20 test-evidence-integrity

outcome: merged
pr: https://github.com/cuioss/cui-http/pull/189
merge_commit: 821cd34c272f69fb6d351399d771eb6cebf52527
merged_at: 2026-09-02T13:54:05Z
mechanism: platform merge queue (squash)
base: main
production_code_changed: false

## Deliverables

The spec staged five. Four shipped as written; **deliverable 1 was dropped, and the
spec authorized the drop** ("if the failure does not reproduce, re-scope to
deliverables 2, 4 and 5 and record that 1 was unnecessary").

| # | Deliverable | Outcome |
|---|---|---|
| 1 | Fix the mockwebserver3 / okhttp misalignment | **DROPPED — premise refuted** |
| 2 | Add the TLS coverage the fix unlocks | shipped |
| 3 | Re-derive the guarded-assertion sweep, triage, convert | shipped (+ operator-approved scope expansion) |
| 4 | Prove every new and converted assertion by falsification | shipped |
| 5 | Reconcile the stale gap notes | shipped |

## Deliverable 1 — the refutation, in full

The three TLS-blocked plans' gap notes claim `mockwebserver3` calls
`Platform.configureTlsExtensions(SSLSocket, String, List)`, a 3-arg overload the
resolved okhttp no longer declares. **That is false at HEAD**, refuted at the
bytecode level and then confirmed empirically:

- `javap -p` on `okhttp-jvm-5.5.0.jar` declares `configureTlsExtensions(SSLSocket,
  String, List<Protocol>, ByteString)` — **4 args**.
- `javap -c` on `mockwebserver3-5.5.0.jar`'s `MockWebServer$SocketHandler` call site
  emits `invokevirtual …:(Ljavax/net/ssl/SSLSocket;Ljava/lang/String;Ljava/util/List;Lokio/ByteString;)V`
  — the **same 4-arg descriptor**. Caller and callee agree; the error cannot be raised.
- The recorded failure named the 3-arg overload under
  `cui-test-mockwebserver-junit5:1.5.0`. HEAD resolves **1.6**, which arrived with the
  `cui-java-parent` 1.5.9 → 1.5.10 bump (commit `7807a00`, 2026-08-29) — **one day
  before** the gap note describing the failure was written (`6ee0c7c`, 2026-08-30).
- Empirically confirmed: the new `HttpHandlerHttpsIntegrationTest` completes a real
  TLS handshake, and its surefire `-output.txt` was read after both an isolated and a
  full-suite run. No `NoSuchMethodError`, no `MockWebServer TaskRunner` trace.

**The note was carried forward untested through two subsequent plans.** No
`cui-http-core/pom.xml` change was needed or made; the okhttp-tls alignment comment
the spec asked us to distrust is in fact accurate.

## What shipped

- `HttpHandlerHttpsIntegrationTest` (NEW class — reported per the spec's
  test-class-inventory constraint): the project's first
  `@EnableMockWebServer(useHttps = true)` test, plus the HTTPS→HTTP downgrade refusal
  asserted end-to-end through `HttpHandler` → `RedirectPolicy` → a real TLS server.
- `RedirectDispatcher` gained a `PATH_DOWNGRADE_SCHEME` route. The gap note's stated
  blocker — that a second TLS-terminated server is required — is **also refuted**: the
  refusal is decided before the next hop is contacted, so one TLS server suffices.
- Guarded-assertion sweep re-derived at HEAD: **41 candidate blocks / 15 files**, not
  the seed's 12/8. Genuine instances converted to the fail-closed form; four seed files
  were already correct and left unchanged.
- `UnicodeNormalizationAttackTest.shouldRejectNormalizationChangingForms` was **100%
  vacuous** — both cases were NFC-invariant, so the loop body never executed once, and
  the in-code comment claiming otherwise was wrong. Fixed with genuinely-composing data.

## Falsification ledger

**25 sites: 25/25 failed when perturbed, 25/25 passed when restored**, each failure
quoting its own stated reason. Plus 6 more for the scope expansion, and 1 for the
Sonar fix.

**Five corroborative assertions were NOT independently falsifiable** and are reported
as such rather than counted as proven: D1's `https` scheme check, D2's
`assertNotNull(refusedTarget)`, `getFrom().getScheme()`, and `getRequestCount()==1`.
They fell only *collectively* to one perturbation (`useHttps = false` →
`ParameterResolutionException: No SSLContext available`), which proves the class is
genuinely TLS-bound but does not exercise each assertion's own message. Isolating them
would require a production change, which this plan forbids.

## Operator-approved scope expansion

`HttpRequestSmugglingAttackTest` — six early-return filter guards
(`if (!pattern.contains(...)) return;`) with no post-loop evidence the guard ever
admitted a sample. Surfaced as Q-Gate finding `adea8c` rather than silently absorbed;
operator approved converting them in-plan. **The sweep heuristic structurally cannot
see this form** — no assertion sits inside the conditional — which is why they were
missed originally and found only by a separate `return; // Skip` search.

## Review round-trip

CodeRabbit found a **Major** defect in that very expansion (`bde6fe`): the admission
counters proved a broad text filter matched, not that the input belonged to the named
family — a CL.TE payload passed the TE.CL guard, a lone `Transfer-Encoding` header
passed the TE.TE guard. **The plan had reproduced the defect class it existed to
remove**, exactly as its own spec warned ("a matched flag makes the skip visible, but
it does not make a wrong guard right"). Fixed in `9f26273`: each guard now fingerprints
header order, multiplicity, or a family-unique header. Replied on-thread and resolved;
CodeRabbit confirmed.

Tightening those predicates dropped each guard's admission rate to ~1/15 of generator
output, giving an observed **15–20% chance of zero admissions per run** at the existing
sample counts (reproduced twice). Counts raised to 200. Recorded because it generalises:
making a guard fail loudly on zero-admission changes its sample-size requirement.

Sonar `java:S5778` (`8ede589`): the `assertThrows` lambda wrapped both the request build
and the send, so the assertion could not say which threw — material in a test whose
point is proving the *refusal* threw. Narrowed and falsified.

## Caveats — read these

- **A merge authorization was granted.** Both required bots reviewed the substantive
  diff at `9f26273` and reported no findings, but neither re-confirmed the `8ede589`
  delta (CodeRabbit rate-limited; incremental-review model). Sourcery APPROVED at that
  HEAD and the merge queue re-tested against latest `main`. Recorded HEAD-bound as
  `barrier-ask-override` / `review-barrier-gap`.
- **OPEN, needs an owner outside this plan:** `marshal.json` sets
  `required_bots: pr-agent`, but that reviewer posts as GitHub login
  `cuioss-review-bot`. The producer matches no author and resolves the bot to
  `absent` — a BLOCKING state — while it has in fact reviewed. **A name mismatch is
  reported as a missing review.** Cost this run ~2h and several hundred thousand tokens
  chasing a review that already existed. Fixed plan-locally ONLY; every other plan in
  this repo will hit it again. Filed as candidate-lesson `-001`.
- **Two measurements are ABSENT, not clean:** `scope_creep_check` never ran on any task
  (`references.json` carries no `plan_creation_sha`), and `pre-commit-verify-freshness`
  passed on sha match with `scope_cross_check: undetermined`
  (`required_coverage_unknown`). Neither has been shown adequate or inadequate.
- **The lessons-consult verb surfaced 0** with all 23 paths `unmapped_paths` — its
  `{bundle}:{skill}` derivation is marketplace-shaped and does not resolve Java source
  paths. That zero is a derivation miss, not evidence of no relevant lesson; the three
  lessons the spec named were consulted by hand instead.

## Adjacent debt this plan did NOT touch

Per the spec: `doc/client-handlers-readme.adoc`, `doc/test-framework-structure.adoc`,
`doc/http-security/specification/testing.adoc` (WS-05 closed, unowned re-check debt).
The `.formatted()` precedence lesson was confirmed to need no work and was not re-opened.

## Metrics

3h33m worked / 7h29m wall / 3.6M tokens. 8 candidate-lessons filed to this epic's inbox.
Final suite: 7781 tests green.
