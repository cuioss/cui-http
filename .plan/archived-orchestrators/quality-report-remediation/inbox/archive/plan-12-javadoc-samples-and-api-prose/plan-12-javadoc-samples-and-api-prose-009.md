envelope_version=1
sender_type=plan
sender_id=plan-12-javadoc-samples-and-api-prose
epic=quality-report-remediation
kind=landing
created=2026-10-05T07:36:52Z

## What landed

plan-12-javadoc-samples-and-api-prose shipped as #260 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-12-javadoc-samples-and-api-prose
epic=quality-report-remediation
pr=#260
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=4180254
total_wall_seconds=10451.0
steps=finalize-step-sync-baseline:done,finalize-step-simplify:done,pre-submission-self-review:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.new_code_issue_count=0
```

## Residue

- Two loop-back iterations ran in finalize: one fix commit for a CodeRabbit inline finding (a request-body sample used the form-decoded pipeline return value) and one self-review fix (a checklist line contradicting the rewritten sample, plus a caveat that the parameter-value pipeline rejects raw values with spaces).
- Follow-ups for the epic (also in this plan's inbox candidate-lesson messages): 23 test files still carry the "HTTP verification specification" phrase; the adapter request-body prose "This prevents SQL injection, XSS scripts, path traversal…" may overstate what the pipeline catches.
- pre-push-quality-gate reported module-tests DEGRADED (build-maven has no resolve-test-scope verb); the whole-tree verify -Ppre-commit ran 12541 tests green each time.
