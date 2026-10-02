# Landing Analysis: PLAN-01 — Header and Content-Type Enforcement

epic: quality-report-remediation
workstream: WS-01
pr: #154 — https://github.com/cuioss/cui-http/pull/154 (MERGED 2026-08-26T17:11:18Z → `2a86a59`)

> Landing record for one shipped plan. Every claim below was corroborated against ground truth —
> the merged diff, the landed code, and the CI check state — before it was recorded. Claims that
> could not be settled are marked `unverifiable` and carry the reason, not a guess.

## Ground-Truth Corroboration

The operator's landing narrative was treated as a lead, not a fact. Verdicts:

| Claim (as pasted) | Verdict | Evidence |
|---|---|---|
| PR #154 → `2a86a59` on main | **corroborated** | `gh pr view 154`: state MERGED, mergeCommit `2a86a59`. `git branch -r --contains 2a86a59` → `origin/main`. |
| All five SV findings closed | **corroborated** | Per-finding evidence in the fidelity table below; each read from the landed source at `2a86a59`, not from the PR narrative. |
| 6/6 deliverables | **corroborated** | ⛔ *Initially recorded as contradicted; that verdict was WRONG and is retracted.* `.plan/temp/header-and-content-type-enforcement/deliverable-hashes.toon` records **six** tracked deliverables plus `__whole_outline__`. The three figures are consistent, not contradictory: the staged spec declared **5**, the plan's outline expanded them to **6**, and PR #154's body summarises **5** numbered items. 6/6 is correct at the plan's own tracking level. |
| 5471 tests green | **corroborated** | PR body verification line (`verify -Ppre-commit -pl cui-http-core -am`, 5471 tests, exit 0). This is the plan's self-report; it is *independently* supported by CI — `build (21)`, `build (25)`, `sonar-build`, `analyze (java)` and the JMH run all pass on the merge commit. No build was run from this session (that is plan work, outside the orchestrator's carve-out). |
| HEADER_VALUE stage count dropped 4 → 2 | **corroborated** | The diff removes both `NormalizationStage` and `PatternMatchingStage` from `HTTPHeaderValidationPipeline.createStages`, leaving `LengthValidationStage` → `CharacterValidationStage`. |
| The feature branch does not survive | **corroborated** | No ref matches `*header-and-content-type*` locally or on the remote. |
| `d010a20` is dangling, reachable only until gc | **corroborated — and harmless** | `d010a20` is a real commit object, and `git merge-base --is-ancestor d010a20 origin/main` → false. ⛔ **But no content is at risk:** the PR was squash-merged, so `d010a20`'s changes are *in* `2a86a59`. `git diff d010a20 origin/main -- <its files>` is empty. Losing the commit object to gc loses nothing but the unsquashed history. |
| `archive-plan` never ran | **corroborated** | A repo-wide search finds no `archived-plans` directory in the main checkout or in any worktree — no plan has ever been archived. This is the one cleanup finding that survives verification. |

## Deliverable Fidelity vs Spec

Five specified, five shipped. Every verdict below was read from the landed source at `2a86a59`.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. `HEADER_NAME` gets a real RFC 7230 `tchar` set (SV-1) | **shipped-as-specified** | `CharacterValidationConstants` now defines `RFC7230_TOKEN_CHARS` (RFC 7230 §3.2.6) and the switch reads `case HEADER_NAME -> RFC7230_TOKEN_CHARS`, no longer sharing `HEADER_VALUE`'s set. |
| 2. Resolve the wired-no-op stages (SV-2, SV-19) | **shipped-modified — stronger than specified** | The spec offered three routes (implement / remove / correct the Javadoc). The plan chose **remove**: both stages are deleted from the header pipeline, their imports dropped, and the Javadoc rewritten to describe control-character rejection instead of the detection it used to over-promise. `URLParameterNameValidationPipeline`'s pass-through claim corrected. This is the strongest of the three routes and closes the finding rather than documenting it. |
| 3. Fix the `AllowBlockListStage` empty-string fail-open (SV-12 / SV-6b) | **shipped-as-specified** | `AllowBlockListStage` no longer exempts the empty value from allow/block-list evaluation. |
| 4. Make `ContentTypeValidationPipeline`'s scope honest (SV-6a) | **shipped-as-specified** | Its single-stage allow/block-list-only scope is now documented at the class level. |
| 5. Regression tests asserting specific failure types | **shipped-as-specified** | New `HeaderAndContentTypeEnforcementRegressionTest` (256 lines), plus additions to `ContentTypeValidationPipelineTest`, `HTTPHeaderValidationPipelineTest`, `AllowBlockListStageTest`, `CharacterValidationConstantsTest`. |
| — | **added-unplanned** | Four `doc/http-security/**` files updated. Outside the spec's declared Expected Surface — see Routing and Merge Behavior. |

## Metrics and Anomalies

- **Diff size is misleading: 240 files changed, but only 23 substantively.** 217 of them are a
  single automated copyright-header rewrite (`Copyright © 2025` → `2025-present`) applied
  repo-wide by the license plugin. Reviewing this PR by file count would badly misjudge it.
- **Anomaly — PLAN-01's plan-store directory is gone.** `.plan/local/plans/header-and-content-type-enforcement/`
  no longer exists in any checkout or worktree, so the plan's metrics and execution manifest are
  unrecoverable. Its `deliverable-hashes.toon` survives under `.plan/temp/`, which is what settled
  the 6/6 question above. Expected for a landed plan whose worktree was removed — but `archive-plan`
  never ran, so nothing was preserved deliberately.
- ⛔ **RETRACTED — "three other plan directories are gone" was FALSE.** The original analysis ran
  `ls .plan/local/plans/` in the MAIN checkout only and read absence there as deletion. **The plans
  store is not main-anchored: each worktree carries its own `.plan/local/plans/{plan}`.** Ground
  truth: `plan-05-forwarded-trust-boundary` and `generator-contract-test-semantics` are alive in
  their worktrees, and `plan-14-doc-overclaim-correction` is alive in the main checkout (its
  worktree was removed after its PR was raised, which is the normal post-push cleanup). Nothing was
  lost. The retraction is kept rather than deleted so the mistaken method is not repeated.
- ⛔ **RETRACTED — "PLAN-11 has neither a PR nor a plan directory" was FALSE.** PLAN-11 has an
  **open PR #156**, a live worktree at `3fee3b9` on `feature/generator-contract-test-semantics`
  with a clean status and two commits. It is running normally. The earlier `gh pr list` predated
  #156's creation and the directory check used the wrong path.
- Tokens / duration: **unavailable** — carried in the deleted plan-store directory.

## Routing and Merge Behavior

- Review: CodeRabbit passed (rate-limited, so no substantive review was performed). CodeQL
  `analyze (java)` passed.
- CI/merge: all checks green on the merge commit — `build (21)`, `build (25)`, `check-changes`,
  `config`, `gate`, `sonar-build`, and an 8m15s JMH benchmark run. Merged through the merge queue.
- **Surface collision observed — PLAN-01 wrote outside its declared surface.** The spec's Expected
  Surface declared no `doc/` file, yet the landing edited four:
  `doc/http-security/configuration.adoc`, `.../specification/specification.adoc`,
  `.../functional-requirements.adoc`, and `.../analysis/http1-vulnerabilities-analysis.adoc`.
  All four edits are *consequential and correct* — they document the stage-set change the plan
  made, and leaving them would have manufactured fresh documentation drift. But `doc/http-security/**`
  is WS-05's declared tree, so the disjointness check did not predict this overlap. Recorded
  against the next pairing decision.
- ⛔ **The collision did NOT materialise as a conflict.** PR #153 (PLAN-14, still open) touches
  `client-handlers-readme.adoc`, `Requirements.adoc`, `owasp-best-practices.adoc` and
  `security-requirements.adoc` — **file-disjoint** from PLAN-01's four. PR #153 reports
  `MERGEABLE / CLEAN` against the new main. No rebase is owed.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-01 --status shipped`
- [x] row `pr` stamped — `154`
- [x] row `landing` stamped — `landings/PLAN-01.md`
- [x] row `plan_marshall_plan_id` stamped — `header-and-content-type-enforcement` (stamped before the directory was deleted; the id is now a dangling reference and is retained as the historical join)
- [x] epic.md narrative reconciled from status.json
- [x] Open Defects added (plan-directory loss; PLAN-11 unknown state; archive-plan never ran)
- [x] Watches updated — the DOC-4 / FW-11 re-verification watch sharpened with the observed stage-count change
- [x] resume_anchor updated
- [x] START-HERE and Ordered Queue blocks regenerated

## Follow-Ups

- **FW-11 (PLAN-07) must RE-VERIFY, not re-derive.** The header pipeline HEADER_VALUE stage set is
  now `Length → Character` (was `Length → Character → Normalization → Pattern`). PLAN-07's
  premise about what the resolver's `sanitize` path runs through has changed underneath it.
- **DOC-4 (now owned by PLAN-07) must RE-VERIFY, not re-derive.** Its core premise —
  `normalizeUnicode`, the header allow/block lists and `allowDoubleEncoding` are inert for
  forwarded-header sanitization — **still holds**, because no `DecodingStage` was added and
  `AllowBlockListStage` remains HEADER_NAME-only. The finding is NOT refuted. But the stage list
  it describes has changed, so the wording must be re-read against `2a86a59` before it is applied.
- **PLAN-14 must NOT redo the doc work PLAN-01 already landed.** `configuration.adoc` now carries
  the `PatternMatchingStage` scope note and the empty-value-vs-empty-list clarification;
  `specification.adoc` carries the header-pipeline composition rationale;
  `http1-vulnerabilities-analysis.adoc` carries the header-smuggling note. None of these is
  PLAN-14's to write again. Its four files are disjoint from these, so the practical instruction
  is: do not widen scope into them.
- **`functional-requirements.adoc` is a REQUIREMENTS document, and PLAN-01 changed a requirement's
  text** (the header-characters bullet, splitting it into header *names* = RFC 7230 tchar and
  header *values* = visible ASCII + tab). PLAN-15 owns DOC-10 and DOC-7 in this same file. Those
  are different requirement rows, so there is no double-work — but PLAN-15 must re-read the file
  at HEAD rather than working from the report's quotation of it.
