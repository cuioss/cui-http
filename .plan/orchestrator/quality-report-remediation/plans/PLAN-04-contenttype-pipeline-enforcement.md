# PLAN-04: Content-Type Pipeline — Real Enforcement

epic: quality-report-remediation
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-04-contenttype-pipeline-enforcement.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

## Objective

Turn the content-type pipeline from a list-membership check into a validating pipeline, and stop
the factory Javadoc promising detections no stage performs. `ContentTypeValidationPipeline`
composes exactly one stage, so `application/json; x\r\nX: y` passes an allow-list of
`application/json` and an arbitrarily long value passes too; the list *entries* are only
lowercased while incoming *values* are additionally stripped at the first `;` and trimmed, so a
block-list entry copied verbatim as `text/html; charset=utf-8` can never match anything and
silently blocks nothing; and a request with no Content-Type at all returns `Optional.empty()`
and bypasses a configured allow-list entirely, while the identical body with a non-matching type
is rejected. Meanwhile `PipelineFactory`'s Javadoc advertises XSS detection, "suspicious header
names" and "malicious header content" that no stage implements. Every change is driven by a
regression test written and seen to fail first.

## Deliverables

1. **The content-type pipeline gets length and character stages (L-3).** `createStages` returns
   `List.of(AllowBlockListStage.forContentTypes(config))` and nothing else. Compose
   `LengthValidationStage` and `CharacterValidationStage` so CR/LF after a `;` and an unbounded
   value are both rejected.
2. **List entries are canonicalised the way values are (F-B-1).** `toLowercaseSet` only
   lowercases each configured entry; `comparisonKey` strips from the first `;` and trims, but
   only for the incoming value. Run entries through the same canonicalisation at construction, so
   a parameterised entry is either normalised to match or rejected as malformed at build time
   rather than silently ineffective at request time.
3. **`null` Content-Type semantics are defined and enforced (F-B-2).** `HTTPBody.contentType()`
   is a `@Nullable` record component and `AbstractValidationPipeline.validate` returns
   `Optional.empty()` immediately for a null value with no exception and no event counted, so the
   documented usage route lets a request with no Content-Type bypass an allow-list. Decide and
   implement the semantics — an allow-list with no matching type must reject a missing type — and
   state them in the pipeline Javadoc.
4. **The factory stops promising detections it does not perform (F-B-3).**
   `PipelineFactory.createUrlParameterPipeline`'s Javadoc lists "XSS attack patterns", and the
   header factory methods list "suspicious header names" and "malicious header content", while
   `PatternMatchingStage` explicitly states XSS pattern checking was removed as an
   application-layer responsibility and the header pipeline composes no pattern stage at all.
   Retract the claims, or implement them — retraction is the expected outcome and is the safer
   default given the library's stated layering.
5. **`HTTPBody` predicate consistency and pipeline `toString` (F-B-10, F-B-13).**
   `isPlainText()` uses whole-string equality while every sibling predicate uses substring
   matching, so `text/plain; charset=utf-8` is not "plain text". Make it consistent. Separately,
   all four pipeline classes carry `@ToString(callSuper = true)` while their abstract parent has
   no `@ToString`, so the rendered value is `Class(super=…@identityHash)` — no diagnostic content
   at all, and the tests only check the class name. Render something useful or drop the
   annotation.
6. **Pipeline-area test-quality gaps (F-B-14).** `EncodingCombinationGenerator.applyMixedCase`
   only uppercases the literal `%2e` / `%2f` escapes via two targeted regex replacements rather
   than randomising case across hex digits; `AbstractValidationPipeline`'s stage loop catches only
   `UrlSecurityException`, so any other `RuntimeException` from a custom stage propagates without
   incrementing the event counter — a contract its own Javadoc states and no test exercises. Add
   the missing coverage.
7. **Regression tests, written first.** One failing test per deliverable, asserting exact values.
   Include: `application/json; x\r\nX: y` against an allow-list of `application/json` must be
   rejected; a block-list entry of `text/html; charset=utf-8` must actually block a `text/html`
   request; a null Content-Type against a configured allow-list must be rejected; a custom stage
   throwing `IllegalStateException` must still increment the counter or must be documented as not
   doing so with a test that pins it.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass that corroborated 44 of 44 findings in `01-security-validation.adoc` with 0
contradicted and 0 unverifiable.

- OBSERVED: `createStages` returns `List.of(AllowBlockListStage.forContentTypes(config))` — no
  `LengthValidationStage`, no `CharacterValidationStage` — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/ContentTypeValidationPipeline.java`
  (lines 96-99).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: createStages 96-99 returns the single AllowBlockListStage verbatim
- OBSERVED: `toLowercaseSet` (lines 99-105, invoked from the constructor at 95-96) only lowercases
  each configured entry, while `comparisonKey` (lines 167-177) strips from the first `;` and trims
  the incoming value — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/AllowBlockListStage.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: toLowercaseSet 99-105, ctor 95-96, comparisonKey 167-177 exact
- OBSERVED: `HTTPBody`'s class Javadoc documents the usage route
  `contentTypeValidator.validate(body.contentType())` at lines 71-76; `contentType()` is
  `@Nullable`; `AbstractValidationPipeline.validate` returns `Optional.empty()` for a null value
  with no exception and no counter increment at lines 79-81 — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/data/HTTPBody.java` and
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/AbstractValidationPipeline.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: HTTPBody Javadoc 71-76 and AbstractValidationPipeline null short-circuit 79-81 verbatim
- OBSERVED: `PipelineFactory`'s Javadoc carries the bullet "XSS attack patterns" at line 110 and
  "suspicious header names" / "malicious header content" on the header factory methods, while
  `PatternMatchingStage` states at lines 231-232 that XSS pattern checking was removed as an
  application-layer responsibility, and `HTTPHeaderValidationPipeline` composes only a length and
  a character stage (plus `AllowBlockListStage` for `HEADER_NAME`) — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/PipelineFactory.java`,
  `.../validation/PatternMatchingStage.java` and `.../pipeline/HTTPHeaderValidationPipeline.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: PipelineFactory 110 and 163/185 exact; PatternMatchingStage XSS-removed comment 231-232 exact
- OBSERVED: `isPlainText` uses `"text/plain".equalsIgnoreCase(contentType)` at lines 259-261 while
  `isJson` (lines 229-232), `isFormData` (lines 268-273) and every other sibling predicate use
  `contentType.toLowerCase().contains(...)` — read at `HTTPBody.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: isPlainText 259-261 equalsIgnoreCase vs isJson 229-232 / isFormData 268-273 contains
- OBSERVED: `@ToString(callSuper = true)` is present on `URLPathValidationPipeline` (line 98),
  `URLParameterValidationPipeline` (line 90), `URLParameterNameValidationPipeline` (line 73) and
  `ContentTypeValidationPipeline` (line 73), while `AbstractValidationPipeline` declares no
  `@ToString` of its own — read at the four pipeline classes.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: @ToString(callSuper=true) at 98/90/73/73 exact
- OBSERVED: `EncodingCombinationGenerator.applyMixedCase` performs two targeted regex replacements
  on the literal `%2e` / `%2f` escapes only (lines 123-127); `AbstractValidationPipeline`'s stage
  loop catches only `UrlSecurityException` at line 93, and its own "Stage Contract" Javadoc
  (lines 43-52) documents that behaviour while no test exercises it — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/EncodingCombinationGenerator.java`
  and `AbstractValidationPipeline.java` (lines 86-107).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: applyMixedCase 123-127 and catch(UrlSecurityException) 93 exact
- HYPOTHESIS: deliverable 3's `null` semantics change may break callers that pass an optional
  Content-Type and rely on the current pass-through. Confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/ContentTypeValidationPipelineTest.java`
  and at every `ContentTypeValidationPipeline` consumer under `cui-http-core/src/main/java`
  (verify-at-outline).
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: forward HYPOTHESIS on null-semantics breaking callers
- Verify-first clause: before scoping deliverable 4, settle whether the promised XSS and
  header-content detections are required by any requirement document that is not itself scheduled
  for correction. Read `doc/http-security/security-requirements.adoc` § SEC-1 and
  `doc/http-security/functional-requirements.adoc`. Note that WS-06 PLAN-13 already owns
  correcting those documents (`F-documentation-15`), so the expected outcome is retraction — but a
  refutation (a requirement genuinely mandates the detection) loops back and re-scopes
  deliverable 4 to implementation.
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: procedural verify-first directive on requirement docs

## Expected Surface

⛔ **CORRECTED 2026-09-09 at landing (PR #227, `65dcb29`): 9 declared entries, 22 realized files.**
Shipped spec; correction serves the audit record.

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/ContentTypeValidationPipeline.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/AbstractValidationPipeline.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/PipelineFactory.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/AllowBlockListStage.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/data/HTTPBody.java` — declared
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/` — declared; `ContentTypeValidationPipelineTest`, `AbstractValidationPipelineTest`, `HTTPHeaderValidationPipelineTest`, `URLPathValidationPipelineTest`, `URLParameterValidationPipelineTest`, `URLParameterNameValidationPipelineBehaviorTest`, `HeaderAndContentTypeEnforcementRegressionTest`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/data/HTTPBodyTest.java` — declared
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/EncodingCombinationGenerator.java` — declared; PLAN-11's tree, and the spec anticipated the touch
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/package-info.java` — UNDECLARED describe-side surface
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationStage.java` — UNDECLARED describe-side surface
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/AllowBlockListStageTest.java` — UNDECLARED; PLAN-11's declared directory
- OBSERVED: `doc/adr/` — UNDECLARED; `0022-A_missing_Content-Type_is_rejected_when_a_non-empty_allow-list_is_configured.adoc`
- OBSERVED: `doc/http-security/specification/pipeline-architecture-standards.adoc`, `doc/http-security/specification/specification.adoc`, `doc/http-security/README.adoc`, `doc/http-security/configuration.adoc` — ⛔ **UNDECLARED and behind an explicit exclusion, overridden with operator approval**
- NOT TOUCHED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLPathValidationPipeline.java` — over-declared

✅ **The `doc/` override was handled correctly**, and the contrast is instructive: PLAN-02 violated
the identical exclusion **silently** by shipping two ADRs; this plan surfaced the conflict, obtained
explicit approval, scoped the override to four named files, and handed off to PLAN-13. ⛔ PLAN-13
must not re-derive those four corrections.

⛔ PLAN-15 wrote `DoubleEncodingPresetParityTest.java` into this plan's declared pipeline test
package while both were running — see `landings/PLAN-15.md`.

## Dependencies and Sequencing

- Depends on: WS-01 PLAN-02 and PLAN-03. PLAN-02 settles what the `CharacterValidationStage` this
  plan composes actually rejects; PLAN-03 stops the raw value reaching the `AllowBlockListStage`
  exception detail this plan also edits. **PLAN-15 is NOT a dependency** — the configuration half
  of the original PLAN-03 was split out into it at the PLAN-01 landing, and this plan touches none
  of the four config classes PLAN-15 owns.
- Overlaps with: PLAN-05 at `AllowBlockListStage` / `CookiePrefixValidationStage` exception-detail
  construction — strictly sequential, PLAN-04 first.
- Adjacent to: `EncodingCombinationGenerator` sits in the generator tree that WS-05 PLAN-11 owns.
  This plan touches only that one generator, for deliverable 6, and PLAN-11 is sequenced after it.
  Record the touch in the inbox message so PLAN-11 does not re-derive it.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-04-contenttype-pipeline-enforcement.md"
```

## Execution Constraints

- **TDD is mandatory for every code change in this plan.** For each deliverable: write the
  regression test, run it, see it fail for the stated reason, then make the production edit, then
  see it pass.
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree
  (carried from the PLAN-14 landing).** `adr-propose` is a standard finalize step that runs on
  EVERY plan, so any plan can write to `doc/adr/` whether or not its spec declares that surface —
  which is exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016 while running concurrently in
  separate worktrees, each scanning a tree in which 0016 was free. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then **name the ADR (number and title) in this plan's inbox message**, so the orchestrator can
  see the allocation even when the surface was never declared. The disjointness gate cannot catch
  this class — it reads declarations, and this write is undeclared by construction.
- ⛔ **Both-preset regression rule (carried from the PLAN-01 landing, `7a0da52`).** Any security
  gate this plan touches — including any gate touched by a fix applied during PR review — must be
  pinned by a regression that runs the SAME input under BOTH `defaults()` and `lenient()` and
  asserts the SAME failure type. **Check the operand, not the outcome**: a gate must read the
  canonical/normalised form, never the value selected for RETURN, because the return selection is a
  presentation choice a configuration flag is allowed to change. PLAN-01's round-1 review fix read
  the returned value and thereby reintroduced the exact raw-versus-encoded asymmetry that plan
  existed to remove — externally reachable on `URL_PATH` under `lenient()`, CWE-177 class — and
  only a second review round caught it. A single-preset regression cannot observe an asymmetry.
  See **ADR-0017**.
- ⛔ **Re-check every review fix against the class invariant before committing it.** A fix
  written under review pressure against one reported symptom is exactly where a defect class
  silently returns. Where this plan has an ADR, re-read the ADR's own claim against the patched
  code before committing — both of PLAN-01's round-1 defects were detectable as direct
  contradictions of ADR text already present in the same PR.
- Seven deliverables exceeds the epic's split guard of ~6. The split-or-proceed rationale is
  recorded as an epic decision: deliverables 1-4 are one mechanism (the content-type pipeline
  and the factory that builds it) and cannot ship apart, and deliverables 5-6 are small
  consistency fixes in the same two packages. A split would produce two plans that both edit
  `AllowBlockListStage.java` and both edit the pipeline test package.
- Deliverables 1-3 change which requests are rejected. Record each as a documented behaviour
  change in the PR body; deliverable 3 in particular converts a silent pass into a rejection.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.
