# PLAN-11: Generator Contract Test Semantics

epic: quality-report-remediation
workstream: WS-04

## Objective

Give the generator contract tests assertions that can actually fail. Of the 20-class
generator-contract population, 17 assert nothing about the generated value — each is a single
`shouldGenerateValidOutput` checking only non-null, non-empty, or `length > N`, with zero semantic
assertions. A `PathTraversalGenerator` test that never asserts its output contains `../`, and a
`NullByteURLGenerator` test that never asserts a null byte is present, **cannot fail if the
generator silently stops producing attacks** — which is the exact regression a contract test exists
to catch. This is the epic's highest-leverage test change: the review's own highest-value-fixes list
puts it first precisely because it is also what would have caught the generator-implementation bugs
that PLAN-12 fixes.

⛔ **This plan MUST land before PLAN-12.** PLAN-11 installs the guard; PLAN-12 fixes the bugs the
guard catches. Reversing the order fixes the bugs with nothing in place to prevent their regression.

## Deliverables

1. **Add semantic assertions to the 9 URL and header generator contract tests** —
   `ValidURLPathGeneratorTest`, `NullByteURLGeneratorTest`, `PathTraversalURLGeneratorTest`,
   `PathTraversalParameterGeneratorTest`, `ValidURLParameterGeneratorTest`, `InvalidURLGeneratorTest`,
   `HTTPHeaderInjectionGeneratorTest`, `ValidHTTPHeaderNameGeneratorTest`,
   `InvalidHTTPHeaderNameGeneratorTest`. (TQ-1, part)
2. **Add semantic assertions to the 6 encoding, injection, and cookie generator contract tests** —
   `EncodingCombinationGeneratorTest`, `UnicodeAttackGeneratorTest`, `PathTraversalGeneratorTest`,
   `BoundaryFuzzingGeneratorTest`, `URLLengthLimitAttackGeneratorTest`,
   `HttpRequestSmugglingAttackGeneratorTest`. (TQ-1, part)
3. **Replace the two cookie generator tests' record-equality exercise with generator semantics** —
   `AttackCookieGeneratorTest` and `ValidCookieGeneratorTest` assert non-null and then exercise the
   `Cookie` record's auto-generated `equals`/`hashCode`, testing the record rather than the
   generator. (TQ-1, part)
4. **Apply the defining-property assertion pattern uniformly** — a traversal generator's output
   contains `../`; a null-byte generator's contains `\0` or `%00`; a "valid" generator's output
   PASSES the relevant pipeline; an "attack" generator's output causes the pipeline to THROW. Follow
   the model the review names: `SupportedValidationTypeGeneratorContractTest`, which asserts the
   exact produced set, the correct `getType()`, and a distribution sanity bound. (TQ-1)
5. **Make `AllGeneratorsIntegrationTest.shouldHaveAllGeneratorsImplemented` able to fail** — it
   calls `assertNotNull` on 10 `final` fields already initialized at construction. Assert something
   about each generator's output, or delete the redundant method. The rest of that file is strong
   and stays. (TQ-4)

Five deliverables — under the split guard.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| TQ-1 | HIGH | 17 of 20 generator contract tests assert nothing about the generated value |
| TQ-4 | MEDIUM | `AllGeneratorsIntegrationTest.shouldHaveAllGeneratorsImplemented` can never fail |
| TQ-15 | MEDIUM | **Absorbed from PLAN-12 on 2026-08-26.** `EncodingCombinationGenerator` never emits its mixed-case / single-encoded outputs |
| TQ-16 | MEDIUM | **Absorbed from PLAN-12 on 2026-08-26.** `URLLengthLimitAttackGenerator` emits sub-limit, non-attack values |
| TQ-17 | LOW | **Absorbed 2026-08-26, NOT DELIVERED, returned to PLAN-12 on 2026-08-27.** The `AttackTypeSelector` seed-invariant counter survives at `8a0aa4c`; the landing does not mention it. TQ-15 and TQ-16 both shipped. |

⛔ **Scope EXPANDED by operator decision at phase-2-refine.** Deliverable 4's defining-property
assertions FAIL against `main` for two generators, and this spec forbade both weakening the
assertion and fixing the generator, directing that the choice be escalated. The operator was offered
"mark the two assertions expected-failing" (preserving the PLAN-11-before-PLAN-12 split) or "land
assertion and fix as a coordinated pair", and chose the **coordinated pair**. So this plan now also
fixes `EncodingCombinationGenerator` and `URLLengthLimitAttackGenerator`, and owns
`URLLengthLimitAttackTest.isPathBasedLengthAttack` — the in-repo filter that hid TQ-16. Resulting
scope: 20 files (18 test files + 2 generator implementations). Recorded via inbox message
`generator-contract-test-semantics-001.md`; PLAN-12 amended in the same reconciliation.

## Claim Labels

- OBSERVED: these findings are stated at `source/test-quality-review.adoc` § `TQ-1`, `TQ-4`.
- OBSERVED: the report enumerates the affected classes by name and states the population precisely —
  20 classes total (19 `*GeneratorTest` plus `SupportedValidationTypeGeneratorContractTest`), of
  which 17 lack semantic assertions and only 3 are real
  (`DoubleEncodingAttackGeneratorTest`, `ValidURLGeneratorTest`, and the contract test). Read at
  `source/test-quality-review.adoc` § `TQ-1`.
- OBSERVED: the report states the 17 were established by a grep for
  `contains`/`matches`/`startsWith`/`validate`/`assertThrows`/`%2`/`../` returning nothing in each.
  Read at `source/test-quality-review.adoc` § `TQ-1`.
- OBSERVED: the report's own highest-value-fixes list ranks TQ-1 first and states explicitly that it
  is what would catch the generator-implementation bugs. This is the report's ordering argument, not
  the orchestrator's. Read at `source/test-quality-review.adoc` § Highest-value fixes.
- HYPOTHESIS: the 15 named test classes each contain a single `shouldGenerateValidOutput` with only
  non-null / non-empty / length assertions — confirm/refute at the enumerated paths under
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/` (verify-at-outline)
- HYPOTHESIS: `AllGeneratorsIntegrationTest.shouldHaveAllGeneratorsImplemented` asserts non-null on
  fields initialized at construction — confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/AllGeneratorsIntegrationTest.java`
  § `shouldHaveAllGeneratorsImplemented` (verify-at-outline)
- HYPOTHESIS: the derived split of TQ-1's 17 classes into this plan's deliverables 1 (9 classes),
  2 (6 classes) and 3 (2 classes) is the orchestrator's own grouping by package, summing to 17.
  Confirm/refute by re-counting against the report's enumeration (verify-at-outline)
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: ⛔ **Deliverable 4 will make some of these tests FAIL on landing** — that is
  the point. PLAN-12 documents two generators (`EncodingCombinationGenerator`,
  `URLLengthLimitAttackGenerator`) that never emit their advertised attack, so a correct assertion
  against them fails against current `main`. Do NOT weaken the assertion to make it pass, and do NOT
  fix the generator here — that is PLAN-12's scope. Mark the affected assertions as expected-failing
  in the way this project's conventions allow, or land the assertion and the generator fix as a
  coordinated pair only if the outline pass establishes that splitting them is unsafe. Escalate the
  choice rather than silently weakening a test.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/generators/url/` — `ValidURLPathGeneratorTest`, `NullByteURLGeneratorTest`, `PathTraversalURLGeneratorTest`, `PathTraversalParameterGeneratorTest`, `ValidURLParameterGeneratorTest`, `InvalidURLGeneratorTest`, `URLLengthLimitAttackGeneratorTest` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/generators/header/` — `HTTPHeaderInjectionGeneratorTest`, `ValidHTTPHeaderNameGeneratorTest`, `InvalidHTTPHeaderNameGeneratorTest` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/` — `EncodingCombinationGeneratorTest`, `UnicodeAttackGeneratorTest`, `PathTraversalGeneratorTest`, `BoundaryFuzzingGeneratorTest` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/generators/injection/` — `HttpRequestSmugglingAttackGeneratorTest` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/generators/cookie/` — `AttackCookieGeneratorTest`, `ValidCookieGeneratorTest` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/generators/AllGeneratorsIntegrationTest.java` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/generators/SupportedValidationTypeGeneratorContractTest.java` — READ-ONLY, the model to follow (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none within WS-04 — this is the workstream head.
- Overlaps with: PLAN-12 (the generator implementations these tests assert against). ⛔ **PLAN-11
  must land FIRST.**
- Adjacent to: all main source. A test that now fails may be revealing a PRODUCTION defect rather
  than a generator defect. If so, escalate — WS-01/02/03 own the production fix, not this plan.
- Adjacent to: PLAN-02's post-decode enforcement (WS-01). If PLAN-02 lands first and changes what
  the pipelines reject, the "valid generators pass the pipeline / attack generators throw"
  assertions in deliverable 4 change with it. Re-verify at outline.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-11-generator-contract-test-semantics.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
