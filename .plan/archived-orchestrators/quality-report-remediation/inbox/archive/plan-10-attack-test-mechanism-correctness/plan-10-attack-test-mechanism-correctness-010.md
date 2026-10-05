envelope_version=1
sender_type=plan
sender_id=plan-10-attack-test-mechanism-correctness
epic=quality-report-remediation
kind=landing
created=2026-10-03T21:24:53Z

## What landed

plan-10-attack-test-mechanism-correctness shipped as #256 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-10-attack-test-mechanism-correctness
epic=quality-report-remediation
pr=#256
merge_state=merged
cleanup_owed=false
deliverables_total=12
deliverables_done=12
total_tokens=8769480
total_wall_seconds=67986.0
steps=finalize-step-sync-baseline:done,finalize-step-simplify:done,finalize-step-security-audit:done,pre-submission-self-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:loop_back,automatic-review:done,sonar-roundtrip:done,adr-propose:skipped,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.new_code_issue_count=0
step.record-metrics.any_phase_missing_end_time=false
```

## Residue

- The `ci-verify` record still says `loop_back`. That is the first-round Sonar failure on raw bidi characters in a test string, fixed in 484282b. The later CI rounds and the post-merge CI (head dfdc7ab) were all green, but no `done` record was re-marked for the step.
- The pre-push-quality-gate module-tests arm reported DEGRADED: this Maven project has no module-tests canonical at default scope.
- The finalize loop ran 5 rounds; the operator raised the 3-iteration limit.
- These are production behaviours this plan observed but did not change, recorded as follow-ups:
  - URLParameterValidationPipeline accepts the low-byte CRLF homograph `%e5%98%8a%e5%98%8d` even with line breaks disallowed.
  - No preset rejects http/https/ftp/gopher/ldap or custom schemes on the URL path pipeline.
- Test-side follow-ups:
  - the set-membership assertion in URLPathValidationPipelineTest;
  - a raw NUL in the HTTPHeaderInjectionGenerator Javadoc;
  - raw characters in IDNAttackDatabase;
  - the PR body's Intent section was truncated.
- The breaking change to the published `generators` test artifact: PathTraversalURLGenerator and DoubleEncodingAttackGenerator are removed.
