envelope_version=1
sender_type=plan
sender_id=contenttype-pipeline-enforcement
epic=quality-report-remediation
kind=landing
created=2026-09-09T10:36:21Z

# Landing: PLAN-04 — Content-Type Pipeline Real Enforcement

- **plan_marshall_plan_id**: `contenttype-pipeline-enforcement`
- **PR**: #227 — <https://github.com/cuioss/cui-http/pull/227>
- **merge_commit_sha**: `65dcb29fd48a3595a335427c9bcc393fb29bb370` (squash via merge queue, verified ancestor of `main`)
- **base at merge**: `main`
- **status**: shipped
- **deliverables**: 7/7
- **tasks**: 13/13 (12 planned + 1 review fix task)
- **tests**: 8026 passing
- **total_tokens**: 4,509,993
- **total_wall_seconds**: 55852 (15h30m)
- **any_phase_missing_end_time**: false

## Commits on the feature branch (squashed into 65dcb29)

| SHA | Subject |
|---|---|
| `0348d53` | fix(security): enforce length and characters on the content-type pipeline |
| `be9fca4` | fix(security): reject an absent Content-Type under a configured allow-list |
| `90a9d3e` | docs(security): retract PipelineFactory claims no stage implements |
| `375f4ed` | fix(security): match text/plain by substring like siblings |
| `710e190` | feat(security): render pipeline stages and type from toString |
| `b8a4266` | test(security): explore mixed case fully and pin stage-exception split |
| `7f14446` | style(security): adopt getFirst over get(0) in two pipeline tests |
| `7b52c48` | test(security): hoist pipeline construction out of assertThrows lambdas |
| `e798843` | docs(adr): record ADR-0022 on missing Content-Type rejection |
| `873a379` | docs(adr): name the executable guard in ADR-0022 risks |

## ⛔ ADR allocated — the epic's standing trap

**ADR-0022: "A missing Content-Type is rejected when a non-empty allow-list is configured"**
(`doc/adr/0022-A_missing_Content-Type_is_rejected_when_a_non-empty_allow-list_is_configured.adoc`,
status `Proposed`, affects `cui-http-core`).

The number was verified against `origin/main` three times — before writing, before commit, and at
the merge gate — and the high-water stayed `0021` throughout, so there was no collision this run.
**The new ADR high-water is 0022.**

## ⛔ Declared-surface widening — carry into WS-06 PLAN-13

This plan's spec declared "⛔ This plan does **not** edit any file under `doc/`". **That boundary was
overridden with explicit operator approval**, because deliverable 1 (composing length + character
stages into the content-type pipeline) falsifies a single-stage claim in four documentation files
that no deliverable declared and no sibling plan owned. `F-documentation-15` (the `PipelineFactory`
Javadoc retraction) is the only doc-side twin PLAN-13 was assigned, and it does not cover the
stage-composition change.

The four files were corrected **in this PR**:

- `doc/http-security/specification/pipeline-architecture-standards.adoc` (:117) — the pipeline-selection matrix `CLAUDE.md` directs agents to consult
- `doc/http-security/specification/specification.adoc` (:199 and :203-204 — both sites)
- `doc/http-security/README.adoc` (:130-131)
- `doc/http-security/configuration.adoc` (:42-44)

**PLAN-13 must NOT re-derive or duplicate these four corrections.** Two `doc/http-security` files
remain excluded and untouched here with intent `read`: `security-requirements.adoc` and
`functional-requirements.adoc`.

## Verify-first clause — resolved

The spec's verify-first clause for deliverable 4 was settled during refine: `security-requirements.adoc`
and `functional-requirements.adoc` were read, and **neither mandates XSS or "suspicious/malicious
header content" detection** (SEC-4 explicitly assigns HTML-entity encoding to the application layer).
Retraction was therefore the correct outcome, as the spec expected. No re-scope to implementation.

## Adjacent-surface touch — carry into WS-05 PLAN-11

This plan touched **one** file in the generator tree PLAN-11 owns:
`cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/EncodingCombinationGenerator.java`
(deliverable 6 — `applyMixedCase` now randomises the case of every `%XX` escape's hex digits from the
seeded source; the previous two targeted regex replacements left every escape but `%2e`/`%2f`
lowercase, notably `%25`). Recorded so PLAN-11 does not re-derive it.

## Behaviour changes (deliverables 1-3 change which requests are rejected)

1. `ContentTypeValidationPipeline.createStages` now composes `LengthValidationStage` →
   `CharacterValidationStage` → `AllowBlockListStage` (all `HEADER_VALUE`), so CR/LF after a `;` and
   an unbounded value are both rejected.
2. `AllowBlockListStage` runs configured entries through the same `canonicalise(value, mediaTypeOnly)`
   helper an incoming value goes through, so a block-list entry `text/html; charset=utf-8` actually
   blocks `text/html`. An entry canonicalising to an empty media type is rejected at construction.
   Header-name lists keep whole-value matching.
3. **Breaking**: `ContentTypeValidationPipeline.validate` overrides the base null short-circuit — a
   null Content-Type is rejected (`INVALID_INPUT` / `HEADER_VALUE`) and counted, when and only when a
   non-empty allow-list is configured. `AbstractValidationPipeline` is untouched. This is ADR-0022.

## Surfaces beyond the spec's Expected Surface

Discovered at outline and folded into deliverable 1 / 6 rather than deferred:

- `HeaderAndContentTypeEnforcementRegressionTest.ContentTypeScope` pinned the exact behaviour
  deliverable 1 inverts (`appliesNoLengthLimit`, `stageListOmitsLengthValidation`).
- Three describe-side surfaces claiming the content-type pipeline runs no length/character
  validation: `pipeline/package-info.java:32`, a `CharacterValidationStage` paragraph, and
  `AllowBlockListStage.renderForDetail`'s Javadoc.
- `HTTPHeaderValidationPipeline` is a **fifth** class carrying `@ToString(callSuper = true)`; the
  spec listed four. Rendering on `AbstractValidationPipeline` fixes all five at once.
- `URLParameterNameValidationPipelineBehaviorTest` had no `toString` assertion at all and was added
  to deliverable 6 after a Q-Gate finding caught the write-set being one short of its own criterion.

## Review outcome

- **CodeRabbit**: reviewed twice. 3 comments at `40507ee` (2 declined, 1 informational), 1 actionable
  comment at `e798843` (fixed in `873a379`). Its two declined comments argued `isPlainText` should do
  strict media-type matching, contradicting the Q-Gate-confirmed deliverable-5 decision to keep
  substring matching for consistency with its five sibling predicates.
- **Sonar**: 4 new-code `java:S5778` findings at `40507ee`, all in
  `HeaderAndContentTypeEnforcementRegressionTest`, all fixed by TASK-13. Re-scan at the final HEAD
  confirmed **0** new-code issues.
- ⛔ **Merge-gate gap, authorized and recorded.** `review_completeness` returned
  `participation_complete: false` at the merged HEAD `873a379`: `cuioss-review-bot` was
  `participated_stale` (it reviewed `40507ee` only, and `re_review_on_loopback` is `false` so nothing
  re-triggered it), and CodeRabbit was quota-refused for that last commit having already reviewed
  `e798843`. The operator was shown this explicitly and ruled "merge if at least one CodeRabbit review
  is present". A `barrier-ask-override` merge-authorization was minted bound to `873a379`
  (`gap-class: review-barrier-gap`) recording exactly which bot was unproven and what it had reviewed.
  The final commit was the doc-only ADR fix CodeRabbit itself requested.

## Both-preset regression rule (PLAN-01 carry) — honoured

Every security gate this plan touched is pinned under BOTH `defaults()` and `lenient()` asserting the
same failure type, including the null-Content-Type rejection
(`nullInputUnderConfiguredAllowListAtLenientPreset`). The gates read the canonical/normalised form,
not the value selected for return.

## Dependencies

- Depended on WS-01 PLAN-02 (#217) and PLAN-03 (#222) — both shipped before this plan started; their
  edits to `AllowBlockListStage.java` and `PatternMatchingStage.java` were re-read and the claimed
  defects still held.
- Overlaps PLAN-05 at `AllowBlockListStage` / `CookiePrefixValidationStage` exception-detail
  construction. **PLAN-04 ran first, as sequenced. PLAN-05 must re-read `AllowBlockListStage.java`** —
  this plan rewrote its entry canonicalisation.
- PLAN-15 was NOT a dependency and none of its four config classes were touched.

## Infrastructure gaps observed (5 candidate-lesson messages also filed)

- `derive_gate_bundles` is marketplace-shaped: 0 bundles from a 29-file Maven footprint, so the
  per-bundle quality-gate arm ran zero times. Only the whole-tree arm gated the push.
- `build-maven` exposes no `resolve-test-scope` verb, so the module-tests divergence gate could not
  run at all in this project.
- `domain-narrow` dropped all four domains at outline time (no `file_globs` in `marshal.json`, no task
  resolved yet); `references.domains` had to be restored by operator decision or phase-4-plan would
  have resolved no skills. **This recurs on every plan in this repo until `marshall-steward` seeds the
  globs.**
- The verdict-currency classifier returned `invalidated` (`verdict_inputs_undeclared`) for every
  head-dependent settle step after a four-line test-only commit, forcing ~400k tokens of re-runs that
  re-confirmed identical results.
- `marshal.json` is provisioned at `0.1.1619` against an installed `0.1.1627` — advisory-stale
  throughout this run.
