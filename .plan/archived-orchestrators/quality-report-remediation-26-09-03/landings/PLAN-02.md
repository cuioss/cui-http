# Landing Analysis: PLAN-02 — Post-Decode Character Enforcement

epic: quality-report-remediation
workstream: WS-01
pr: #159 — https://github.com/cuioss/cui-http/pull/159 (MERGED → `d042750`)

## Ground-Truth Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #159 merged → `d042750` | **corroborated** | MERGED; on `origin/main`. 14 files, +907/−31. |
| SV-4 strict preset fixed | **corroborated** | `SecurityDefaults`: `true, true` → `false, true // case-insensitive comparison (detects a superset)`. Exactly the spec's fix. |
| SV-5 widened to the whole C0/C1 class | **corroborated** | Decoded-character re-validation now rejects `U+0000`–`U+001F` and `U+007F`–`U+009F`; NUL deliberately left to `allowNullBytes`. |
| SV-3 resolved by NARROWING, not enforcing | **corroborated** | `/%3Cscript%3E` is deliberately ACCEPTED and pinned by a characterization test in `PostDecodeEnforcementRegressionTest`. ⛔ This inverts what the source review expected — see below. |
| SV-24 `+` preservation | **corroborated** | URL_PATH decoding switched to RFC 3986 semantics; `+` no longer rewritten to space. |
| 5537 tests green, 0 Sonar new-code | **corroborated as reported** | Quality gate ran fully this time (compile+lint+test), unlike the three doc plans. |
| PLAN-13 impact: none | **corroborated, and well-argued** | Because deliverable 1 resolved AWAY from full post-decode allow-list enforcement, previously-accepted legitimate percent-encoded paths still pass; the widened check covers only C0/C1, which no legitimate database entry contains. No file under `security/database/` or `security/generators/` was edited. **This closes the watch the spec opened.** |
| Landing message completeness | **contradicted — `complete: false`** | All 8 required keys missing. **Fourth incomplete landing of five drained.** |

## Deliverable Fidelity vs Spec

The spec's six deliverables were delivered as two outline deliverables. All six are accounted for:

| Spec deliverable | Verdict |
|---|---|
| 1. Re-validate decoded output, OR narrow the Javadoc (SV-3, SV-5) | **shipped — split resolution.** SV-5 enforced (C0/C1 widening); SV-3 **narrowed** by an explicit scope boundary. |
| 2. Fix the `strict()` preset (SV-4) | **shipped-as-specified** |
| 3. Make `failOnSuspiciousPatterns(false)` honest (SV-7) | **shipped** — ADR-0006 records that `PatternMatchingStage` does not observe non-failing matches; Javadoc corrected rather than logging added. |
| 4. Complete the encoded-dot case check (SV-10) | **shipped-modified** — see the unreachable-input note. |
| 5. Decide the `+`-to-space asymmetry (SV-24) | **shipped-as-specified** — RFC 3986 semantics. |
| 6. Regression tests | **shipped-as-specified** — `PostDecodeEnforcementRegressionTest`, one case per finding. |

## Metrics and Anomalies

- 3.9M tokens / 15h29m — by far the epic's most expensive plan. 19/19 finalize steps.
- ⛔ **The genuine fork the spec flagged was decided, and decided AGAINST the source review.** The
  spec required this be settled at outline against the code and the project's XSS-scope stance, with
  the choice recorded. It was: `/%3Cscript%3E` is accepted, on operator outline-gate confirmation
  plus the existing application-layer-XSS stance, and ADR-0004 records it. **The report's Top
  Priority #4 explicitly permitted this route** ("or narrow `CharacterValidationStage`'s own Javadoc
  to state that character rules apply to the encoded form only"), so this is compliant, not a
  shortfall — but it means SV-3 is closed by a narrowed guarantee, and the library's decoded output
  can still carry characters the encoded form would reject.
- **SV-10's specified test inputs were unreachable.** `/a%2E%2eb/x` cannot be rejected at pipeline
  level: `DecodingStage` runs before `NormalizationStage`, so it arrives as `/a..b/x`, a legitimate
  filename. The executor proved this empirically — *both positive controls also failed* — and
  substituted double-encoded inputs that decode once to the form the fixed check inspects. Correct
  handling of a spec defect: refuted, demonstrated, and re-scoped rather than forced.
- **Merge carried a recorded review gap.** CodeRabbit genuinely reviewed the final HEAD and signed
  off with no findings, but as a *comment* rather than a review submission, so the participation
  checker read `participated_stale`; pr-agent never saw the last two commits. Documented via a
  HEAD-bound pre-merge-consent authorization rather than hidden.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` 159; `landing`; `plan_marshall_plan_id`
- [x] PLAN-13 watch CLOSED — no legitimate-database expectation broke
- [x] Open Defect raised — duplicate ADR numbers (see Follow-Ups)
- [x] 7 candidate-lessons promoted (`2026-08-27-12-001..007`)

## Follow-Ups

- ⛔ **DUPLICATE ADR NUMBERS on main.** PLAN-02 and PLAN-15 each allocated ADR-0004 and ADR-0005
  concurrently, so `doc/adr/` now holds two 0004s and two 0005s:
  `0004-CharacterValidationStage_validates_the_wire_form…` vs
  `0004-Documentation_inventories_point_at_package-level_source_trees…`, and
  `0005-A_security_preset_must_never_set_caseSensitiveComparison_to_true…` vs
  `0005-Documentation_cross-references_use_named_anchors…`. Neither plan is individually at fault —
  there is no shared ADR-number allocator, so any two concurrent plans that propose ADRs will
  collide. **This will recur in every future wave** unless the allocation is serialised.
  No staged spec owns `doc/adr/`, and WS-05 is closed, so this has no owner.
- **SV-3's narrowed guarantee is now the library's position.** Any later plan tempted to "fix"
  decoded-character enforcement must read ADR-0004 first — reversing it would contradict a recorded
  decision and break the characterization test that pins it.
