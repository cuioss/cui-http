# Landing Analysis: PLAN-15 — Documentation Inventory and Navigation

epic: quality-report-remediation
workstream: WS-05
pr: #161 — https://github.com/cuioss/cui-http/pull/161 (MERGED → `94004bc`)

> Closes WS-05. Every claim corroborated against the merged diff and the live tree at `94004bc`.

## Ground-Truth Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #161 merged → `94004bc` | **corroborated** | MERGED; `origin/main` HEAD. 13 files. |
| 6/6 deliverables | **corroborated** | All six verified in the diff. Matches the spec's six exactly. |
| Counts drifted further than the report knew | **corroborated by direct count** | `tests/` holds **27** classes (report: 26). Pipeline test classes: **7** (report: 4; this epic's own spec HYPOTHESIS said 6 — the drift outran both). `Legitimate*` matches **4** files of which one is the abstract base, so "4 legitimate-pattern databases" over-counts — 3 concrete. ⛔ This is why the spec's chosen route mattered: it retired the hand-maintained inventories rather than re-typing today's numbers, which would have been stale again by now. |
| DOC-7's RFC misattribution had a second, unlisted site | **corroborated** | `doc/http-security/README.adoc` now distinguishes RFC 7230 (header-name `token` grammar, enforced by `CharacterValidationStage`) from RFC 3986 (query-parameter names, `URLParameterNameValidationPipeline`). The source report named only one site. |
| Scope boundaries honoured | **corroborated** | `security/**` never opened (DOC-7's Javadoc half stays PLAN-04's), `doc/forwarded-header-resolution.adoc` untouched (WS-02's), no test class edited. **All three ownership boundaries this epic set held under pressure.** |
| Landing message completeness | **contradicted — `complete: false`** | All 8 required keys missing; no `landing-facts` block. **Third incomplete landing of four drained.** |

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Refresh or stop hand-maintaining the test inventories (DOC-8, TQ-9) | **shipped-as-specified, best route** | Inventories retired in favour of package-level pointers — the report's own preferred option ("better: stop hand-maintaining exhaustive counts in prose"), not the weaker re-type-the-numbers route. |
| 2. Bring `test-framework-structure.adoc` up to date (DOC-8, TQ-9) | **shipped-modified** | Converted to a package-level map rather than a corrected enumeration. Same rationale. |
| 3. Close the two navigation gaps (DOC-9) | **shipped-as-specified** | Plus corrections to the security-README claims. |
| 4. Add the missing implementation-status notes (DOC-10) | **shipped-as-specified** | HTTP-1, HTTP-2 and HTTP-14 notes added. |
| 5. Refresh `CLAUDE.md`; extend the pipeline decision matrix (DOC-11, DOC-12) | **shipped-as-specified** | `CLAUDE.md`'s architecture section now lists all five pipelines and eight stages, and the client/forwarded packages; the decision matrix gained one section per concrete pipeline. |
| 6. Clear the smaller accuracy and staleness items (DOC-7, DOC-14) | **shipped-as-specified, plus one unlisted site** | Note xref repaired; RFC misattribution fixed at two sites, one of which the report never named. |

## Metrics and Anomalies

- 3.03M tokens / 2h15m. 18/18 finalize steps. 9 review comments, all resolved. Sonar 0 new-code.
- **ADR-0004 and ADR-0005 added** (Proposed) — inventories point at package trees; cross-references
  use named anchors.
- ⛔ **The plan reproduced its own defect class, one level up — and this is the PLAN-14 hazard
  recurring exactly as the ledger predicted.** Both ADRs written to codify *removing documentation
  overclaims* themselves asserted that link validation already runs in this build. It does not:
  `pom.xml` configures no AsciiDoc processing and the Maven workflow skips doc-only changes.
  CodeRabbit caught it; corrected in `882cc7b`. The paired-claim hazard folded into this spec from
  PLAN-14's landing was aimed at prose/row pairs — it recurred instead as *claim vs. build reality*.
- **The quality gate again ran zero arms** ("no buildable footprint"), the third doc-plan landing
  where remote CI was the only gate.

## Routing and Merge Behavior

- Merged via queue (squash); branch and worktree removed; `archive-plan` ran.
- **Review-tooling caveat, self-reported:** pr-agent's clean verdict predates the final three
  commits — it covered a tree that no longer existed at merge. Promoted as a lesson.
- No surface collisions. `CLAUDE.md` was edited here and by nothing else.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` 161; `landing`; `plan_marshall_plan_id`
- [x] **WS-05 CLOSED** — both its plans shipped
- [x] 6 candidate-lessons promoted (`2026-08-27-07-001..006`)
- [x] 1 candidate-lesson FOLDED as a recurrence into `2026-08-26-19-002` rather than duplicated
- [x] Two residual items recorded as Open Defects (below)

## Follow-Ups

⛔ **WS-05 is closed, so both residual items below have NO owner in the current queue.** They are
outside the 117 report findings and are recorded rather than silently absorbed. Staging a spec for
either is an operator decision, not one the orchestrator takes unilaterally.

- **`compliance-traceability.adoc` still carries per-section test-CASE counts** ("200+ test cases",
  "500+ test cases", "64+ patterns") and line-number references. Same staleness class as DOC-8;
  deliberately left because deliverable 1 scoped to test-CLASS counts and widening the diff was not
  the plan's call. Correct restraint — and the item is real.
- **No link checker exists in this repository.** `pom.xml` configures no AsciiDoc processing and the
  Maven workflow skips doc-only changes, so a broken xref is decidable but undetected. ADR-0005 names
  adding one as the follow-on its decision enables. This is what let the ADR overclaim above survive
  authoring.
