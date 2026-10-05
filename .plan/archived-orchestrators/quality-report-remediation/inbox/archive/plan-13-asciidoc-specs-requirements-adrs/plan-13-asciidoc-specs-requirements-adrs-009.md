envelope_version=1
sender_type=plan
sender_id=plan-13-asciidoc-specs-requirements-adrs
epic=quality-report-remediation
kind=landing
created=2026-10-05T15:35:41Z

## What landed

plan-13-asciidoc-specs-requirements-adrs shipped as #262 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-13-asciidoc-specs-requirements-adrs
epic=quality-report-remediation
pr=#262
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=5853910
total_wall_seconds=46294.0
steps=finalize-step-sync-baseline:done,finalize-step-simplify:done,pre-submission-self-review:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_commit_sha=4a9c30eb66bad4c8390bc36a8ce80bc558f09ab0
step.sonar-roundtrip.new_code_issue_count=0
```

## Residue

- **Merged under an operator override at the pre-merge review barrier**: `barrier-ask-override` granted at 0697017 because required bot CodeRabbit was `participated_stale` (last review 52c6e79; its 1/hour quota was exhausted). The final commit only fixed 3 Sonar smells in two test classes. cuioss-review-bot reviewed the final HEAD.
- **Spec boundary overridden by operator**: the spec's "no .java file" rule was overridden in review for TEST-scope Java only — new `LogMessagesDocumentationTest`, reworked `NfkcFoldClaimInvariantTest` (claims table + source scan), one corrected comment in `UnicodeNormalizationAttackTest`. No `src/main` Java was touched.
- **Loop-back ceiling exceeded with operator authorization**: 4 finalize loop-back rounds against `max_iterations=3`, plus a 5th direct fix round for 3 late CodeRabbit comments and 3 Sonar smells.
- **Deliverable 7 decisions**: the build-command fork resolved toward documenting the `./mvnw` fallback (executor not committed, `.gitignore` unchanged); F-documentation-23 REFUTED (site build generates `index.html`).
- **ADR set**: 18 ADRs advanced Proposed → Accepted; ADR-0005 and ADR-0018 deliberately left Proposed (`specification.adoc` "see below"; `benchmark.yml` wildcard egress). No ADR allocated by this plan. The ADR index consistency check is proposal-only in `doc/adr/README.md`.
- **PLAN-15 constants deletion**: recorded in the PR body only, per operator.
- **Follow-ups (production Java / PLAN-12 territory)**: `CharacterValidationStage` Javadoc and the >255-branch comment claim BODY is ASCII-only at the default (code admits 160-255); `ForwardedHeaderResolver` Javadoc "Present-but-invalid = drop" and "contributes nothing" comments in `ForwardedHeaderResolver.java` / `RfcForwardedParser.java`.
- **Metrics note**: one estimated 6-finalize accumulator sample was offset by a negative correction to the actual dispatch sums; the samples counter is inflated by 1.
