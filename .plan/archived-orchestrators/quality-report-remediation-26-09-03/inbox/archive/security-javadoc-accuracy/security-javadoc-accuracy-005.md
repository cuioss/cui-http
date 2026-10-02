envelope_version=1
sender_type=plan
sender_id=security-javadoc-accuracy
epic=quality-report-remediation
kind=landing
created=2026-09-01T07:08:31Z

# Landing: PLAN-04 security-javadoc-accuracy

PLAN-04 landed as PR #180, squash-merged via the merge queue; `main` is at `5aba533`.
All 7 deliverables shipped. Every Javadoc claim SV-15..SV-29, CL-16, BB-5 and the
DOC-7 inheritance is closed, and deliverable 7's five staged Sonar findings are fixed.

```landing-facts
schema=landing-facts/1
plan_id=security-javadoc-accuracy
epic=quality-report-remediation
pr=#180
merge_state=merged
deliverables_total=7
deliverables_done=7
total_tokens=4618330
total_wall_seconds=76523
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:done
step.branch-cleanup.merge_state=merged
step.branch-cleanup.main_sha=5aba533
step.adr-propose.adrs_proposed=2
step.sonar-roundtrip.new_code_issue_count=0
step.sonar-roundtrip.count_status=confirmed
step.pre-push-quality-gate.tests_run=6635
step.lessons-capture.inbox_messages_written=4
```

## Deliverable outcomes

1. Non-compiling Javadoc examples — fixed across 10 files (SV-15, CL-16, BB-5).
2. Stage-sequence and pipeline-inventory Javadoc — corrected, including all THREE
   stale `PipelineFactory` inventories, not the two originally scoped (SV-16..18).
3. Dead-end / non-compiling config documentation — removed (SV-21, SV-23).
4. Missing Javadoc — added on 14 public members (SV-22).
5. Standalone-use documentation + threat-model notes (SV-25..28).
6. Structural observations — `AttributeParser`, `Cookie.getAttributeNames`,
   `module-info` (SV-29).
7. Five staged Sonar findings — `java:S1845` (BLOCKER), `java:S1905`, two
   `java:S127`, `java:S5778`. All minimal, single-fix, no widening.

## Operator decisions settled during this plan

- `java:S9142` DROPPED from scope; NOT re-derived at execution.
- `@ToString(callSuper = true)` removal DROPPED; annotation retained on all five
  pipeline classes and the six asserting tests untouched.
- Surface additions ACCEPTED: `core/package-info.java`,
  `ContentTypeValidationPipeline`, `URLParameterNameValidationPipeline`.
- `module-info.java` `requires transitive org.jspecify` KEPT in this plan.

## Corrections to the spec's own premises

- The spec asserted the `AttributeParser.toLowerCase` removal "closes a latent
  Turkish-locale defect". REFUTED empirically during execution:
  `String.equalsIgnoreCase` is locale-independent and
  `Character.toUpperCase('ı') == 'I'`, so the old code matched correctly under `tr`.
  The removal is pure redundancy removal, no behaviour change. Filed as
  candidate-lesson 001 because the false claim then RE-APPEARED in a later agent's
  report — a false-claim propagation path with no write-back channel for refutations.
- A prior revision claimed `UrlSecurityException` also carried
  `@ToString(callSuper = true)`. Verified ABSENT at HEAD — the five pipeline classes
  are the complete set.
- `java:S5778` was verified STILL PRESENT despite PLAN-09's `sonar-roundtrip`
  reporting "S5778 findings confirmed gone"; that report did not cover this file.

## Residue

- Two ADRs proposed and merged as `Proposed` status, awaiting human review:
  ADR-0009 (`when()`/`identity()` deliberately fail-open) and ADR-0010
  (`NormalizationStage` clamps root-consumed dot-segments; skips rewriting
  scheme-bearing input while still running LAYER 1/2 traversal checks).
- `default:adr-propose` wrote two tracked `.adoc` files while declaring no
  `mutates_source`, so the dispatcher's commit instrumentation did not fire. The
  orchestrator committed them by hand; without that they would have been destroyed
  with the worktree. Filed as candidate-lesson 004 against
  `plan-marshall:phase-6-finalize`.
- coderabbit refused twice on free-OSS quota and its self-reported "next review in
  24 minutes" was wrong by ~15x. Only an explicit `@coderabbitai review` re-trigger
  produced a review — which then found 2 REAL Javadoc inaccuracies this plan had
  missed (`Cookie.getAttributeNames` overclaim; `pipeline/package-info` scheme://
  short-circuit overstatement), both fixed at `8c262cd`. Filed as candidate-lessons
  002 and 003.
- Bot participation at the final merge HEAD was stale: all bot reviews sat at
  `8b55b39`/`8c262cd`, and sourcery's approval was auto-dismissed by the ADR commit.
  Merged on explicit operator consent recorded as a HEAD-bound
  `pre-merge-consent` grant at `34db8ca`; the unreviewed delta was exactly the two
  ADR `.adoc` files (289 insertions, no code).
- Out of scope, observed but NOT acted on: `CLAUDE.md` repeats the same absolute-URL
  overclaim this plan corrected in `pipeline/package-info.java`; several
  `package-info` examples use SLF4J-style `{}` placeholders that the CuiLogger
  standard forbids. Neither file is in this plan's declared footprint.
