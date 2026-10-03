envelope_version=1
sender_type=plan
sender_id=header-and-content-type-enforcement
epic=quality-report-remediation
kind=landing
created=2026-08-26T17:16:52Z

## What landed

header-and-content-type-enforcement shipped as #154 (merged) — RFC 7230 header-name token enforcement and content-type allow-list, squashed to main at 2a86a59.

```landing-facts
schema=landing-facts/1
plan_id=header-and-content-type-enforcement
epic=quality-report-remediation
pr=#154
merge_state=merged
deliverables_total=unknown
deliverables_done=unknown
total_tokens=unknown
total_wall_seconds=unknown
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:not_run,finalize-step-preference-emitter:not_run,record-metrics:not_run,finalize-step-print-phase-breakdown:not_run,emit-landing:done,archive-plan:not_run
```

## Residue

**INCIDENT — plan directory lost during branch-cleanup.** During the resumed finalize run, `default:workflow-integration-git:git-workflow worktree-remove` was invoked before `default:workflow-integration-git:integrate_into_main integrate` (the mandatory move-back). The doc specifies `worktree-remove` should refuse with `plan_dir_not_moved_back` in that ordering, but instead it removed the worktree (confirmed via `git worktree list` — the worktree and its `.git/worktrees/` admin entry are both gone) and only then surfaced an unrelated `internal_error: [Errno 2] No such file or directory`. Because `.plan/local/plans/header-and-content-type-enforcement/` is gitignored (untracked), the entire plan-state directory — `status.json`, `references.json` (including the just-captured `realized_footprint`), findings, tasks, and per-plan logs — was deleted along with the worktree and is NOT recoverable from git. The plan no longer appears in `manage-status list`. This is an operator-visible defect in `branch-cleanup`'s move-back-before-removal sequencing (or in this dispatch's execution of it) that the epic should track — worktree-remove's `plan_dir_not_moved_back` refusal did not fire when expected.

**What is NOT lost**: the actual code changes are safe — PR #154 was squash-merged to `main` at commit `2a86a59` before the incident, and post-merge CI on that commit was green. The local feature branch `feature/header-and-content-type-enforcement` still exists (untouched) with full history if anyone needs to inspect the pre-squash commits.

**Consequence for this run**: `lessons-capture`, `finalize-step-preference-emitter`, `record-metrics`, `finalize-step-print-phase-breakdown`, and `archive-plan` could NOT be executed against the plan's original artifacts because the plan directory no longer exists. This landing message was assembled manually (Write tool + `orchestrator inbox write`, which is main-anchored and does not require the plan's own `status.json`) from facts captured earlier in the session transcript, so the epic still receives a machine-readable record even though the formal `archive-plan` step never ran. The epic queue/status for this plan should be reconciled by hand (mark done/landed) since no `archive` call updated it.

**Six items the operator asked to be recorded for the epic (verbatim from the dispatch)**:

1. **HEADER_VALUE behaviour changed** (pipeline stages reduced) — WS-02 (`de.cuioss.http.forwarded`, FW-11) and WS-05 (DOC-4) must RE-VERIFY rather than re-derive their premises.
2. **Deliverable 6 amended a REQUIREMENTS document** (`functional-requirements.adoc`, HTTP-10, plus an HTTP-14 citation fix) — not only specification prose — so PLAN-14 does not redo it.
3. **`NormalizationStage` remains a no-op** in `URLParameterNameValidationPipeline` and its sibling value pipeline — deliberately out of scope for this plan, needs its own decision.
4. **AsciiDoc xref/link validation does not exist as a build capability** in this project (0 asciidoctor references across 512 files) — added to the residual epic hand-off list.
5. **Behavioral break**: empty content types no longer bypass a configured allow-list; header pipeline `getStages()` output changed (`HEADER_NAME` 5->3, `HEADER_VALUE` 4->2).
6. **Two config defects found in this repo, verified directly by the operator, tracked as separate epic work**:
   - `marshal.json` lists `pr-agent` in `required_bots`, but pr-agent has never acted on this repo — no comments/reviews across PRs 149/150/151/154, no check runs, no workflow file referencing pr-agent/qodo/codium. Its silence is a permanent latent merge blocker for every future plan until the app is installed or it is moved to `optional_bots`. This run only passed via the automatic-review escape hatch (structural refusal + `absent`-bot authorization recorded as `barrier-ask-override` over `review-barrier-gap`).
   - The finalize `sonar-roundtrip` step reported "Sonar not configured — Branch C" and skipped, but that is a FALSE NEGATIVE: `credentials_config.workflow-integration-sonar` IS present in `marshal.json`, `credentials verify --skill plan-marshall:workflow-integration-sonar` returns `verified: true` against `/api/system/status`, and CI runs a `build / sonar-build` check that passed. The CI scan ran, so nothing was missed on this PR, but the step's detection logic is misreporting.
