envelope_version=1
sender_type=plan
sender_id=semicolon-dot-segment-traversal
epic=quality-report-remediation
kind=landing
created=2026-09-01T16:07:25Z

## What landed

semicolon-dot-segment-traversal shipped as #185 (merged): `..;` dot-segment path-parameter traversal is now rejected with `PATH_TRAVERSAL_DETECTED` in `NormalizationStage` LAYER 1.

```landing-facts
schema=landing-facts/1
plan_id=semicolon-dot-segment-traversal
epic=quality-report-remediation
pr=#185
merge_state=merged
deliverables_total=2
deliverables_done=2
total_tokens=2337642
total_wall_seconds=22380
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:skipped,record-metrics:done,finalize-step-print-phase-breakdown:skipped,emit-landing:done,archive-plan:done
step.branch-cleanup.merge_state=merged
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_commit=18c0cc71d9493ea0922bf6603d8082be162f6804
step.create-pr.pr_number=185
step.sonar-roundtrip.new_code_issue_count=0
step.sonar-roundtrip.count_status=confirmed
step.finalize-step-sync-baseline.action=noop
step.pre-push-quality-gate.tests_run=6693
```

## Residue

**Bypass re-verified at HEAD before scoping, not inherited.** The spec's verify-first clause was honoured: the outline ran `URLPathValidationPipeline` under `SecurityConfiguration.defaults()` via jshell and observed six family members ACCEPTED (`/..;/..;/etc/passwd`, `/api/..;/..;/secret`, `/..;foo=bar/..;baz=1/etc/passwd`, `/..%3B/..%3B/etc/passwd`, `/%2e%2e;/%2e%2e;/etc/passwd`, `/..;/`) with `/../../etc/passwd` correctly REJECTED as control.

**Detection point settled at outline (spec deferred it).** `NormalizationStage` LAYER 1, a fifth segment-anchored intent pattern `(?:^|[/\\])\.\.;` in `containsDirectoryTraversalIntent`. It sits after `DecodingStage`, so every encoded spelling folds to one `..;` shape before the check — scoped by mechanism, not by literal enumeration. Detection-only, so ADR-0010's (now ADR-0014's) rewrite clamp is untouched. `PatternMatchingStage` was rejected (substring matching would also reject legitimate `b..;`); `DecodingStage` was rejected per ADR-0004's character-safety scope. Reuses `PATH_TRAVERSAL_DETECTED` — no new `UrlSecurityFailureType`, no `SecurityDefaults` change.

**ADR renumbering collision with PLAN-19's territory.** Upstream PR #184 (`docs(adr): deduplicate colliding ADR numbers`) landed mid-finalize and renamed `doc/adr/0010-NormalizationStage_clamps...` to `0014-...`. This plan edits that ADR's pattern-count claim. Git rename detection carried the correction into `0014-` correctly and no stale `0010` duplicate was resurrected — verified. Epic should note that ADR ordinals are now allocated by #184's constraint.

**Out-of-scope items left untouched as the spec required.** `SecurityConfigurationBuilder.failOnSuspiciousPatterns` and its inert `/etc/` + `file:` gate were NOT changed. `doc/http-security/specification/specification.adoc` was NOT edited and there is nothing to flag — the fix changes no documented stage order.

**Adjacent observation for the epic (not acted on).** `cui-http-benchmarking/.../SecurityBenchmarkState.java:73` already carried `/api/..;/admin/config` in its `ATTACK_URLS` array — the repo had independently classified this family as an attack while the pipeline accepted it. Harmless (both benchmark call sites catch `UrlSecurityException`), but it corroborates the finding's premise.

**Review coverage is thinner than the step outcomes suggest — read this before trusting `automatic-review:done`.** The plan shipped with ONE required-bot review, not two:
- CodeRabbit reviewed `8f2cf93` cleanly (all 5 files, run `96a683fa`, "No actionable comments"), but was quota-refused at the merged HEAD `fd7035a`. The delta between those two SHAs is ZERO Java changes (`git diff --name-only -- '*.java'` = 0 files); the whole delta is #184's already-merged ADR renumbering. Operator approved the merge on that basis; a `barrier-ask-override` merge authorization was granted at `fd7035a` with `gap-class=review-barrier-gap`.
- Sourcery was budget-exhausted for the entire run (250,000 diff-char / 7-day limit) and never reviewed. Its two refusal notices were filed as `pr-comment` findings and resolved `accepted` (`03f92b`, `a4dfbc`).
- pr-agent (`cuioss-review-bot`) reviewed clean: "PR contains tests / No security concerns identified / No major issues detected".

**PR #183 was closed unmerged and replaced by #185.** `automatic-review` on #183 force-done'd past a correct `absent` verdict for CodeRabbit, on the wrong rationale that a registry classification gap was hiding a real review. CodeRabbit had in fact never reviewed #183: 0 reviews, 0 inline comments, 0 check-runs — its green `CodeRabbit / Review completed` commit status is a non-blocking placeholder it sets while rate-limited. Recorded as candidate-lesson 001.

**Orchestrator-side incident during this run.** A retrigger loop posted `@coderabbitai review` every 2 minutes for ~55 minutes against a quota-blocked bot: ~27 spam comments, and it additionally exhausted the CodeRabbit chat-message hourly quota, which is why no re-review of `fd7035a` was obtainable. Comments were deleted except four on the now-closed #183. Recorded as candidate-lesson 002.

**Tooling gap surfaced.** `git-workflow baseline-reconcile` classified the #184 rename as `no_overlap` / `conflict_count=0` while GitHub reported #185 `CONFLICTING` — the probe compares by path, so a rename/edit conflict is invisible to it. Recorded as candidate-lesson 003.

**Q-Gate findings resolved in-run.** `057d2f` (refine confidence-justification, `accepted`), `82d88d` (outline declared `single_module` while deliverable 1 also touches a `documentation`-module ADR — `fixed` by documenting the deliberate cross-module touch), `e93893` (Sonar `java:S4144` duplicate-body negative-control test — `suppressed` via `@SuppressWarnings` matching the file's existing precedent at line 348).
