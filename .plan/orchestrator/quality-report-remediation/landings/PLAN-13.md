# Landing Analysis: PLAN-13 — AsciiDoc specs, requirements and ADRs

epic: quality-report-remediation
workstream: WS-06
pr: #262 (`4a9c30e`, merged via merge queue under an operator barrier override)

> Drained from inbox message `plan-13-asciidoc-specs-requirements-adrs-009.md` on 2026-10-05.
> `inbox landing-check`: `complete: true`, no missing keys. Corroborated: `ci pr view 262` reports
> `state: merged`, merge commit `4a9c30e`; `git diff 4a9c30e^ 4a9c30e` lists 43 files.

## Deliverable Fidelity vs Spec

The landing reports 8/8 deliverables done. Ground-truth checks:

| Check | Verdict | Evidence |
|-------|---------|----------|
| No `src/main` Java touched | held | the only `.java` files in the diff are 3 test files |
| Spec rule "edits no `.java` file" | **overridden by operator** (test scope only) | `LogMessagesDocumentationTest.java` (new), `NfkcFoldClaimInvariantTest.java`, `UnicodeNormalizationAttackTest.java` |
| ADR index re-derived (4g) | held | `doc/adr/README.md` on main: rows 19–23 present, "highest allocated number is **23**"; 23 `.adoc` records |
| `testing.adoc` re-read after PLAN-10's edit | touched | `doc/http-security/specification/testing.adoc` in the diff |
| Generator readme vs the generators PLAN-10 removed | held | `doc/test-generators-readme.adoc` changed; no remaining reference to `PathTraversalURLGenerator` / `DoubleEncodingAttackGenerator` |
| F-documentation-23 (site `index.html`) | **refuted** | the plan's site build generates `index.html` (reported) |
| Build-command fork (deliverable 7) | resolved: document the `./mvnw` fallback | `CLAUDE.md` now maps each executor command to `./mvnw`; executor not committed |

## Surface delta

Declared 8, realized 43: `expansion_detected`, `added_count: 3` (the three test files above),
`missing_count: 1` (`.gitignore`, a HYPOTHESIS entry the build-command fork resolved against —
expected). Fourteenth under-declaration occurrence, all of it the operator-authorised test scope.
No collision with PLAN-12 (disjoint by extension, as planned).

## Metrics and Anomalies

- Tokens: 5,853,910 total
- Duration: 46,294 s wall (~12.9 h)
- Anomalies: 4 finalize loop-back rounds against a ceiling of 3 (operator-authorised) plus a 5th
  direct fix round; ~7 self-review rounds that self-seeded on reworded doc claims;
  `scope_creep_check` failed on every run (`scope_creep_warning` not a known finding type);
  domain-narrow emptied `references.domains`; one 6-finalize metrics sample inflated by 1;
  `archive-plan: pending`.

## Routing and Merge Behavior

- Review: CodeRabbit `participated_stale` (1/hour quota exhausted) — merged under
  `barrier-ask-override`; cuioss-review-bot reviewed the final HEAD; CodeQL findings on the new
  tests fixed; Sonar new-code issues at merge: 0.
- CI/merge: merge queue; no rebase conflict.

## Outcome notes

- 18 ADRs moved Proposed → Accepted; ADR-0005 and ADR-0018 deliberately left Proposed.
- PLAN-15's constants deletion is recorded in the PR body only (operator choice).

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr`, `landing`, `plan_marshall_plan_id` stamped
- [x] Open Defects resolved: PLAN-10 `testing.adoc` boundary crossing; generator removal
  (doc half — the release-notes half stays open)
- [x] Open Defect added: production Javadoc drift (`CharacterValidationStage`,
  `ForwardedHeaderResolver`, `RfcForwardedParser`)
- [x] 7 plan-marshall lessons routed to plan-marshall `truthful-signals`
  (`cui-http-quality-report-remediation-014..020`)
- [x] resume anchor updated — every plan in the epic is now terminal
