# PLAN-12: Generator Implementation Correctness

epic: quality-report-remediation
workstream: WS-04

## Objective

Make every generator emit the attack — or the legitimate value — it advertises. A dedicated review
pass read all 34 generator implementations in full: 21 are fully correct, and the rest emit values
that do not match what they claim. Two never produce their advertised attack at all.
`EncodingCombinationGenerator`'s `urlEncode` replaces `%` LAST and globally, so after a single pass
no literal `%2e`/`%2f` remains and `applyMixedCase` — which searches for exactly those — is a
permanent no-op: the mixed-case bypass technique the generator is NAMED for is never generated, and
neither is a single-encoded form. `URLLengthLimitAttackGenerator` returns many values well under the
smallest documented limit that are otherwise legitimate URLs, so its own Javadoc usage example
would fail. These went uncaught because the contract tests asserted nothing — which is why PLAN-11
lands first.

## Deliverables

⛔ **SCOPE REDUCED 2026-08-26 — deliverables 1 and 2 moved to PLAN-11.** By operator decision at
PLAN-11's phase-2-refine clarification round, PLAN-11 absorbed the `EncodingCombinationGenerator`
(TQ-15) and `URLLengthLimitAttackGenerator` (TQ-16, TQ-17) fixes, landing them as a coordinated
pair with the assertions that prove them. The four deliverables below are what remains. Recorded
via inbox message `generator-contract-test-semantics-001.md`.

1. ⛔ **RETURNED FROM PLAN-11, 2026-08-27 — TQ-17 was absorbed but never delivered.** Replace
   `URLLengthLimitAttackGenerator`'s seed-invariant attack-type selection. The private
   `AttackTypeSelector` class still holds `private int currentType = 0` and advances it with
   `currentType = (currentType + 1) % maxTypes` — a mutable counter independent of the framework
   seed and not thread-safe, violating the "reproducibility is a function of the seed, not internal
   state" standard that sibling generators explicitly advertise. **Verified surviving at `8a0aa4c`**
   despite PLAN-11 editing that file heavily (+118/−75) and closing TQ-16 in it. PLAN-11's landing
   does not mention TQ-17 at all. (TQ-17)
2. **Fix `PathTraversalGenerator.generateUnicodeTraversal`** — the Java source literal is
   double-backslashed, so the produced string contains the six characters `\ u 0 0 2 e` and NO real
   dots. A validator scanning for `..`, `%2e`, or an actual dot will not match it. Use the real
   escape if a real dot is intended, or rename the branch to reflect that it emits literal escape
   text. `generateAdvancedTraversal` case 4 has the same defect. (TQ-18)
3. **Strengthen the weak-attack branches** — `UnicodeAttackGenerator` can emit a lone invisible
   character (`U+202E`, `U+200B`, `U+FEFF`) with no other payload, which a traversal- or
   pattern-focused validator may not flag; and `AttackURLParameterGenerator`'s long-string branches
   emit plain `[A-Za-z0-9]` values that are not malicious by content, so the attack depends entirely
   on the paired parameter name or on length. (TQ-19, TQ-20)
4. **Fix the mislabeled and pass-through branches** — `UnicodeNormalizationAttackGenerator.createMixedScriptAttack`
   substitutes letters only, so a pure-traversal base pattern passes through unchanged and is
   returned as a "mixed-script normalization attack"; `createComposedNormalizationAttack` maps `.` to
   a lookalike that does not NFC-normalize back to `.`, so it is not a true normalization attack;
   and the two whitespace cookie-name generators include a non-prefixed `session_id` case that
   carries no `__Host-`/`__Secure-` prefix and so does not exercise the prefix-bypass scenario their
   Javadoc claims. (TQ-21, TQ-22)
5. **Clear the minor generator observations** — `HttpResultSuccessGenerator`/`HttpResultFailureGenerator`
   not overriding `getType()` and being used via direct `new … .next()` despite Javadoc claiming
   `@TypeGeneratorSource` usage; and `ValidCookieGenerator.generateExpires` emitting a
   comma-and-space date into an attribute string. `NullByteURLGenerator`/`NullByteInjectionParameterGenerator`
   being fixed lists and `BoundaryFuzzingGenerator` being range-based are documented and intentional —
   **no action**, named so their absence is not read as an oversight. (TQ-23)

Five deliverables — under the split guard. ⛔ The former "Six deliverables — at the
split guard" note and its proceed-unsplit rationale are RETIRED: they justified a six-deliverable
spec that no longer exists.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| TQ-18 | LOW | `PathTraversalGenerator.generateUnicodeTraversal` emits literal escape text, not a decodable traversal |
| TQ-19 | LOW | `UnicodeAttackGenerator` can emit a lone invisible character as an "attack" |
| TQ-20 | LOW | `AttackURLParameterGenerator` emits benign-by-content long values |
| TQ-21 | LOW | `UnicodeNormalizationAttackGenerator` has two mislabeled / pass-through branches |
| TQ-22 | LOW | Whitespace cookie generators include a non-prefixed cookie name |
| TQ-23 | INFO | Minor generator observations (partly no-action) |

| TQ-17 | LOW | ⛔ **RETURNED from PLAN-11 on 2026-08-27** — `URLLengthLimitAttackGenerator` uses a seed-invariant call-counter for attack-type selection |

**Moved OUT of this plan 2026-08-26, and only PARTLY delivered:** TQ-15 (`EncodingCombinationGenerator`)
and TQ-16 (`URLLengthLimitAttackGenerator` sub-limit values) went to PLAN-11 and **both shipped** in
PR #164. **TQ-17 went with them and did NOT ship** — the `AttackTypeSelector` seed-invariant counter
survives at `8a0aa4c`. It is returned to this plan as deliverable 1 above.
`URLLengthLimitAttackTest.isPathBasedLengthAttack` stays PLAN-11's; it was resolved there.

## Claim Labels

- OBSERVED: these findings are stated at `source/test-quality-review.adoc` § `TQ-15`..`TQ-23`,
  in the "Generator implementation correctness" section.
- OBSERVED: the report states all 34 generator implementation classes were read in full (cookie 6,
  encoding 7, header 5, injection 2, url 11, root 1, client/result 2), that 21 are fully correct,
  and it NAMES those 21. Read at `source/test-quality-review.adoc` § Generator implementation
  correctness. ⛔ Do not modify a generator on that list without first establishing a defect the
  report did not find.
- OBSERVED: the report records that TQ-15, TQ-16 and TQ-17 were re-verified directly against the
  source before publication — TQ-15 "verified by tracing the replace chain", TQ-16 "read the cited
  branches and confirmed the lengths", TQ-17 and TQ-18 "verified against the source". These carry
  higher confidence than the unverified LOWs. Read at `source/test-quality-review.adoc` § the same section.
- OBSERVED: TQ-23's `NullByteURLGenerator`, `NullByteInjectionParameterGenerator` and
  `BoundaryFuzzingGenerator` observations are explicitly recorded as documented and intentional with
  no claimed defect. Read at `source/test-quality-review.adoc` § `TQ-23`.
- HYPOTHESIS: `EncodingCombinationGenerator.urlEncode` chains
  `.replace(".", "%2e").replace("/", "%2f").replace("%", "%25")` and `encodingLevelGen` has a minimum
  of 1, making `applyMixedCase` unreachable — confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/EncodingCombinationGenerator.java`
  § `urlEncode`, `applyMixedCase` (verify-at-outline)
- HYPOTHESIS: `URLLengthLimitAttackGenerator`'s `createFragmentOverflow`, `createHostnameOverflow`
  and `createDeepPathNesting` return values under 1024 characters — confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/url/URLLengthLimitAttackGenerator.java`
  § those three methods (verify-at-outline)
- HYPOTHESIS: `URLLengthLimitAttackTest.isPathBasedLengthAttack` early-returns unless the extracted
  path component exceeds 1024, which is what hides TQ-16 — confirm/refute at the corresponding test
  class § `isPathBasedLengthAttack` (verify-at-outline)
- HYPOTHESIS: `PathTraversalGenerator`'s Unicode-traversal literal is double-backslashed in the Java
  source — confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/PathTraversalGenerator.java`
  § `generateUnicodeTraversal`, `generateAdvancedTraversal` (verify-at-outline)
- HYPOTHESIS: `UnicodeAttackGenerator` uses the REAL escape where `PathTraversalGenerator` uses the
  literal one — the report's contrast, and the evidence that the double-backslash is a bug rather
  than a convention — confirm/refute at the sibling class (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: PathTraversalGenerator's Unicode-traversal literal is still double-backslashed at f1ba539; PLAN-11 did not touch it
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: these generators are published in the `generators` classifier artifact and
  consumed OUTSIDE this repository. Changing what a generator emits changes what downstream
  consumers' tests receive. Check the artifact's documented contract before scoping, and prefer the
  documented-contract route over a silent output change where the report offers both.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/` — `PathTraversalGenerator`, `UnicodeAttackGenerator`, `UnicodeNormalizationAttackGenerator` (verify-at-outline)
- ⛔ **NOT this plan's surface:** `EncodingCombinationGenerator`, `URLLengthLimitAttackGenerator`, and `URLLengthLimitAttackTest` — all three moved to PLAN-11 on 2026-08-26.
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/generators/url/` — `AttackURLParameterGenerator` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/generators/cookie/` — `CookieNameUnicodeWhitespaceGenerator`, `CookieNameAsciiWhitespaceGenerator`, `ValidCookieGenerator` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/client/result/HttpResultSuccessGenerator.java` and `cui-http-core/src/test/java/de/cuioss/http/client/result/HttpResultFailureGenerator.java` — these TWO FILES ONLY (verify-at-outline)
- ⛔ **NOT this plan's surface:** `client/result/HttpErrorCategoryTest.java` and `client/result/HttpResultTest.java` — PLAN-09 owns the first for CL-9. Declaring the directory made this plan falsely collide with PLAN-09.
- HYPOTHESIS: the corresponding `*Test` classes for the four remaining deliverables (verify-at-outline)
- HYPOTHESIS: `cui-http-core/pom.xml` — the `generators` classifier packaging, READ-ONLY (WS-06's surface) (verify-at-outline)

## Dependencies and Sequencing

- Depends on: **PLAN-11, mandatorily** — but the RATIONALE has changed. It is no longer "PLAN-11
  installs the guard that deliverables 1-2 need", because those deliverables are now PLAN-11's own.
  The dependency stands because PLAN-11's defining-property assertions across the generator contract
  tests are what prove and guard the four fixes that REMAIN, and because PLAN-11's surface now
  includes 18 test files this plan must not edit concurrently.
- Overlaps with: PLAN-11 (the generator/test pairs), PLAN-13 (the same test tree).
- Adjacent to: `cui-http-core/pom.xml`'s `generators` classifier configuration, which packages
  `generators/**` and `database/**` from the test output. ⛔ Do NOT edit the pom — WS-06 owns it.
  If a generator moves package, that is a packaging change to surface, not to make.
- Adjacent to: the main validation pipelines. If a corrected generator now emits an attack the
  pipeline does NOT reject, that is a production finding — escalate to WS-01, do not weaken the
  generator back.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-12-generator-implementation-correctness.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
