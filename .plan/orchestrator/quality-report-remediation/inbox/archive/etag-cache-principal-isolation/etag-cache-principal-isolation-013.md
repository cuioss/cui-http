envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=landing
created=2026-09-16T15:38:03Z

## What landed

etag-cache-principal-isolation shipped as #240 (merged).

```landing-facts
schema=landing-facts/1
plan_id=etag-cache-principal-isolation
epic=quality-report-remediation
pr=#240
merge_state=merged
cleanup_owed=false
deliverables_total=13
deliverables_done=13
total_tokens=12972739
total_wall_seconds=27169.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,sonar-roundtrip:done,ci-verify:done,automatic-review:done,branch-cleanup:done,lessons-capture:done,adr-propose:skipped,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
```

## Residue

- **Process fault, disclosed and operator-resolved**: `adr-propose` was dispatched out of manifest order (after `branch-cleanup` instead of before) by the orchestrating session. A post-hoc Signal Gate re-check confirmed it would genuinely have fired (solution outline `compatibility: breaking`, non-empty decision log). Operator was informed and explicitly chose to skip it, since the plan's sole architectural decision (credential-material cache-key binding) was already documented in ADR-0023 as an explicit deliverable-13 task authored during execution, not deferred to this step.
- **Stale Sonar re-scan observed mid-run**: after fix commits landed for 3 Sonar new-code issues, a re-fire of `sonar-roundtrip` returned the identical 3 issue keys/lines byte-for-byte, despite the flagged code having demonstrably changed. Verified as false positives by direct code read and rejected with evidence rather than allocating redundant fix tasks. Suspected SonarCloud CE analysis lag behind the `sonar-build` GitHub check's report-submission step. Worth a look if this recurs on other plans.
- **In-run security finding**: the finalize security-audit step found and this run fixed a real defect — `ETagAwareHttpAdapter.principalBinding` concatenated raw credential header values with unescaped `&`/`=` before hashing, permitting a delimiter-forgery collision that could merge two different principals' cache identities. Fixed by digesting each header individually before joining (commit `8dac0da`).
