# Landing Analysis: PLAN-04 — Content-Type Pipeline Real Enforcement

epic: quality-report-remediation
workstream: WS-02-contenttype-cookie-collection
pr: #227 (https://github.com/cuioss/cui-http/pull/227) — merged 2026-09-09T07:38:33Z as `65dcb29`

> Corroborated: `gh pr view 227` (MERGED), `git show --stat 65dcb29` (22 files), ADR-0022 on disk,
> the four `doc/` corrections present in the diff.

## Deliverable Fidelity vs Spec

All 7 shipped, 13 tasks (12 planned + 1 review fix), 8,026 tests passing.

Three deliverables change **which requests are rejected** — this is a behaviour-changing landing,
not a documentation one:

1. `ContentTypeValidationPipeline.createStages` now composes `LengthValidationStage` →
   `CharacterValidationStage` → `AllowBlockListStage` (all `HEADER_VALUE`), so CR/LF after a `;`
   and an unbounded value are both rejected.
2. `AllowBlockListStage` runs configured entries through the same `canonicalise(value,
   mediaTypeOnly)` helper an incoming value goes through, so a block-list entry
   `text/html; charset=utf-8` actually blocks `text/html`. An entry canonicalising to an empty
   media type is now rejected at construction. Header-name lists keep whole-value matching.
3. ⛔ **Breaking**: `ContentTypeValidationPipeline.validate` overrides the base null short-circuit
   — a null Content-Type is rejected (`INVALID_INPUT` / `HEADER_VALUE`) and counted, **when and
   only when a non-empty allow-list is configured**. `AbstractValidationPipeline` is untouched.
   Recorded as **ADR-0022**.

**Verify-first clause resolved correctly.** Deliverable 4's clause was settled during refine:
`security-requirements.adoc` and `functional-requirements.adoc` were read and **neither mandates
XSS or "suspicious/malicious header content" detection** — SEC-4 explicitly assigns HTML-entity
encoding to the application layer. Retraction was therefore the right outcome, as the spec
anticipated. No re-scope to implementation. This is the verify-first contract working end to end.

## ✅ ADR-0022 allocated cleanly — the trap did NOT fire

The number was verified against `origin/main` **three times** — before writing, before commit, and
at the merge gate — and the high-water stayed `0021` throughout. **New high-water: 0022.**

This is the discipline the epic has been asking for since the 0016 collision, applied without
being prompted, and it is the first ADR in the epic to land with no incident. Worth stating
positively: the control that works is a re-check *at the merge gate*, not one at authoring time.

## ⛔ Surface delta — a declared exclusion overridden, with approval

Declared **9** `OBSERVED` entries; the merge touched **22** files.

**The spec asserted "⛔ This plan does not edit any file under `doc/`". That boundary was
overridden with explicit operator approval**, because deliverable 1 (composing length + character
stages into the content-type pipeline) **falsifies a single-stage claim in four documentation
files** that no deliverable declared and no sibling plan owned. `F-documentation-15` — the
`PipelineFactory` Javadoc retraction PLAN-13 was assigned — does not cover the stage-composition
change.

The four files were corrected **in this PR**:

- `doc/http-security/specification/pipeline-architecture-standards.adoc` (:117) — ⛔ **the
  pipeline-selection matrix `CLAUDE.md` directs agents to consult**
- `doc/http-security/specification/specification.adoc` (:199, :203-204)
- `doc/http-security/README.adoc` (:130-131)
- `doc/http-security/configuration.adoc` (:42-44)

⛔ **PLAN-13 must NOT re-derive or duplicate these four corrections.** Two `doc/http-security`
files remain excluded and untouched with intent `read`: `security-requirements.adoc` and
`functional-requirements.adoc`.

**This is the correct handling of a boundary that turns out to be wrong** — the alternative was to
ship a change that falsifies the document `CLAUDE.md` tells every agent to trust. The override was
surfaced, approved, scoped to four named files, and handed off. Contrast PLAN-02, which violated
the identical `doc/` exclusion **silently** by shipping two ADRs.

Other realized-beyond-declared surfaces, all folded at outline rather than deferred:

- `pipeline/package-info.java:32`, a `CharacterValidationStage` paragraph, and
  `AllowBlockListStage.renderForDetail`'s Javadoc — three describe-side surfaces claiming the
  content-type pipeline runs no length/character validation.
- `HeaderAndContentTypeEnforcementRegressionTest.ContentTypeScope` — pinned the exact behaviour
  deliverable 1 inverts.
- `HTTPHeaderValidationPipeline` is a **fifth** class carrying `@ToString(callSuper = true)`; the
  spec listed four. Rendering on `AbstractValidationPipeline` fixed all five at once.
- `URLParameterNameValidationPipelineBehaviorTest` had no `toString` assertion at all — added after
  a **Q-Gate finding caught the write-set being one short of its own criterion**.
- `AllowBlockListStageTest.java` (in `test/…/validation/`, PLAN-11's declared directory).

**Over-declared, never touched:** `URLPathValidationPipeline.java`.

⛔ **See `landings/PLAN-15.md` § Surface delta**: PLAN-15 ran concurrently with this plan and wrote
`DoubleEncodingPresetParityTest.java` into `test/…/security/pipeline/` — *this* plan's declared
directory. No textual conflict resulted, because the two touched different files.

## ⛔ Merge-gate gap — authorized, bounded, and recorded

`review_completeness` returned **`participation_complete: false`** at the merged HEAD `873a379`:

- `cuioss-review-bot` was **`participated_stale`** — it reviewed `40507ee` only, and
  `re_review_on_loopback` is `false`, so nothing re-triggered it.
- CodeRabbit was **quota-refused** for that last commit, having already reviewed `e798843`.

The operator was shown this explicitly and ruled *"merge if at least one CodeRabbit review is
present"*. A `barrier-ask-override` merge-authorization was minted **bound to `873a379`**
(`gap-class: review-barrier-gap`), recording exactly which bot was unproven and what it had
reviewed. The final commit was the doc-only ADR fix CodeRabbit itself requested.

**This is the right shape for a gate override** — bound to a specific commit, naming the specific
unproven bot, with the residual risk stated. It also lands directly on the epic's existing
`re_review_on_branch_cleanup` defect (lesson `2026-09-08-15-002`): that lesson said the re-review
path cannot fire on a merge-queue repo, and here the *loopback* variant is off for the same
practical reason. The two are one defect with two switches.

## Review outcome

- **CodeRabbit**: reviewed twice. 3 comments at `40507ee` (2 declined, 1 informational), 1
  actionable at `e798843` (fixed in `873a379`). ⛔ Its two declined comments argued `isPlainText`
  should do strict media-type matching — **contradicting the Q-Gate-confirmed deliverable-5
  decision** to keep substring matching for consistency with its five sibling predicates. Declining
  a bot comment that contradicts a settled, gate-confirmed decision is correct, and the reasoning
  is preserved here so it is not re-litigated.
- **Sonar**: 4 new-code `java:S5778` findings at `40507ee`, all in
  `HeaderAndContentTypeEnforcementRegressionTest`, all fixed by TASK-13. Re-scan at final HEAD
  confirmed **0** new-code issues.

**Both-preset regression rule (the PLAN-01 carry) honoured**: every security gate this plan touched
is pinned under both `defaults()` and `lenient()` asserting the same failure type — including the
null-Content-Type rejection (`nullInputUnderConfiguredAllowListAtLenientPreset`). The gates read
the canonical/normalised form, not the value selected for return.

## ⛔ The `landing-facts` block REGRESSED to absent

`inbox landing-check` reported **`complete: false` with all 8 required keys missing**. The message
is not short of data — it carries `plan_id`, PR, merge sha, deliverables, tokens and wall-seconds —
but as a **markdown bullet list rather than a fenced `landing-facts` block**, so the machine check
resolves nothing.

The emitter has now produced **three distinct states across six landings**:

| State | Plans |
|---|---|
| correct fenced block | PLAN-03, PLAN-15 |
| fenced block with **wrong values** (finalize figures as plan totals) | PLAN-06 |
| **no block** — data present but unfenced, or absent entirely | PLAN-02, PLAN-04 |

⛔ `complete: true` therefore establishes only that a block parsed — not that it is right — and
`complete: false` does not mean the facts are missing. Both verdicts need a hand check against
`metrics.md`, which is the opposite of what the check exists for.

## Metrics and Anomalies

- **Tokens**: 4,509,993. **Duration**: 15h30m (55,852s).
- **Tests**: 8,026 passing.
- **Anomalies**:
  - ⛔ **The verdict-currency classifier returned `invalidated` (`verdict_inputs_undeclared`) for
    every head-dependent settle step after a four-line test-only commit**, forcing ~400k tokens of
    re-runs that re-confirmed identical results. This is lesson `2026-09-06-14-004` recurring — a
    verdict-currency classifier must not read an undeclared `verdict_inputs` surface as invalidated.
    Second observation, and now with a token cost attached.
  - `build-maven` **exposes no `resolve-test-scope` verb**, so the module-tests divergence gate
    could not run at all in this project. → this is the mechanism behind the epic's standing
    "advertised `module-tests` and `test-compile` arms do not exist" defect.
  - `derive_gate_bundles` produced **0 bundles from a 29-file Maven footprint**, so the per-bundle
    quality-gate arm ran zero times and only the whole-tree arm gated the push. Third observation.
  - `marshal.json` provisioned at `0.1.1619` against installed `0.1.1627`.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#227`; `landing` `landings/PLAN-04.md`;
      `plan_marshall_plan_id` `contenttype-pipeline-enforcement`
- [x] `## Expected Surface` corrected: the approved `doc/` override, ADR-0022, and the five other
      realized-beyond-declared surfaces named; `URLPathValidationPipeline.java` marked over-declared
- [x] **Open Defect opened** — `landing-facts` has three inconsistent states across six landings
- [x] **Open Defect folded** — `build-maven` has no `resolve-test-scope` verb (the mechanism behind
      the standing module-tests defect)
- [x] **Open Defect folded** — verdict-currency `invalidated` recurrence, with its ~400k token cost
- [x] PLAN-13 and PLAN-05 hand-offs folded into their specs
- [x] epic.md reconciled; both generated blocks regenerated

## Follow-Ups

- ⛔ **PLAN-05 must re-read `AllowBlockListStage.java`** — this plan rewrote its entry
  canonicalisation, and PLAN-05 shares the exception-detail construction. The sequencing held
  (PLAN-04 ran first, as staged), but PLAN-05's staged premises now predate the rewrite.
- ⛔ **PLAN-13 must not re-derive PLAN-04's four `doc/` corrections**, and must reconcile
  `doc/http-security/configuration.adoc` for **both** plans: PLAN-04 corrected it at :42-44, and
  PLAN-15 left it stale at ~:275-276. One file, two different obligations, from two plans that ran
  concurrently.
- **PLAN-11**: `EncodingCombinationGenerator.java` was touched as its spec anticipated
  (`applyMixedCase` now randomises every `%XX` escape's hex digits from the seeded source; the
  previous two targeted regex replacements left every escape but `%2e`/`%2f` lowercase, notably
  `%25`). Also `AllowBlockListStageTest.java`. Both recorded so PLAN-11 does not re-derive them.

---

## Addendum — 2026-09-09, from the operator's finalize report

Arrived after this record was written and after PLAN-04 was reconciled. No ship semantics changed.
Three items are genuinely new; the rest corroborates what the inbox landing message already carried.

### The `x2` markers are the ~400k-token re-run, made visible

The finalize table marks **five separate steps `x2`** — `pre-push-quality-gate`,
`pre-submission-review`, `simplify`, `security-audit` and `ci-verify` all ran twice. That is the
verdict-currency `invalidated` cascade this record already documents, now with its blast radius
legible: **a four-line, test-only commit re-fired every head-dependent settle step**, and the second
pass of each returned an identical verdict (`simplify` 0 edits / 0 findings both times;
`security-audit` 0 edits / 0 findings both times).

⛔ The re-runs were not merely expensive — **every one of them was provably a no-op**, and that was
knowable in advance from the fact that a test-only commit cannot change a `simplify` or
`security-audit` verdict. Folded onto lesson `2026-09-06-14-004`, which now carries both the
mechanism and the cost.

### ⛔ "Recorded rather than reported green" — the sharpest statement of the zero-coverage class

The operator's framing of the two dead gate arms is better than the epic's own:

> *Both were recorded rather than reported green, but a run that doesn't read the warnings can't
> tell them from a pass.*

That is the whole defect in one sentence, and it names the part the epic had been describing
loosely. The arms are **honest** — they emit warnings and claim nothing. The failure is at the
**consumer**: a step board, a summary, or a reviewer that reads outcomes and not warnings sees a
run in which nothing failed, which is indistinguishable from a run in which everything passed. Fixing
the producer is not the remedy, because the producer is already correct.

Folded into lesson `2026-09-06-07-002` as the class's defining statement.

### `pre-submission-review`: "clean, 3 candidates, **no check matched**" — and it ran twice

⛔ This corroborates the epic's oldest standing defect from a **fourth** plan: no
`ext-self-review-{domain}` implementor resolves for Java, so the step surfaces candidates, matches
no check against any of them, and records `clean`. Running it twice produced *two* clean records
from *zero* checks. Every plan in this epic has shipped without a structural self-review, and each
one's step board reads green.

### Minor

- `preference-emitter`: **no patterns promoted — all unattributed.** Recorded as a first observation;
  not yet a pattern.
- Push chain `40507ee` → `7b52c48` → `873a379`, matching the merge-gate gap this record documents at
  `873a379`.
- The report shows **19/19 finalize steps done** and does not mention the `review_completeness`
  `participation_complete: false` gap at the merged HEAD. The inbox landing message did carry it in
  full, and § Merge-gate gap above is the authoritative account — a step recorded `done` is not
  evidence its barrier was satisfied, which is the same consumer-side blindness as the section above.
