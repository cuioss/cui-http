envelope_version=1
sender_type=plan
sender_id=attack-database-and-assertion-quality
epic=quality-report-remediation
kind=landing
created=2026-08-31T17:27:10Z

## What landed

attack-database-and-assertion-quality shipped as PR #178 (merged via merge queue as 30edfa3), covering all 7 deliverables of PLAN-13.

```landing-facts
schema=landing-facts/1
plan_id=attack-database-and-assertion-quality
epic=quality-report-remediation
pr=178
merge_state=merged
deliverables_total=6
deliverables_done=6
total_tokens=4176800
total_wall_seconds=33900
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:done
step.adr-propose.adrs_proposed=2
step.adr-propose.adr_numbers=0009,0010
step.lessons-capture.inbox_messages_written=4
step.branch-cleanup.merge_commit=30edfa3
```

## Residue

**WS-01 escalation — two production findings, confirmed empirically, NOT fixed by this plan.**
Deliverable 1's mandatory payload probe found two payloads the pipeline ACCEPTS under
`SecurityConfiguration.defaults()`:

1. `/file:///etc/passwd` is accepted. The cause is more precise than PLAN-13 predicted:
   `file:` and `/etc/` ARE present in `SecurityDefaults.SUSPICIOUS_PATH_PATTERNS`, but
   `SecurityConfigurationBuilder.failOnSuspiciousPatterns` defaults to `false`, so the entire
   gate is inert under the default preset. The pattern set exists and does nothing by default.
2. `/..;/..;/etc/passwd` is accepted — a semicolon-suffixed dot-segment traversal that
   `PatternMatchingStage` does not match. Found while probing (1); adjacent to but outside
   this plan's scope.

Per PLAN-13's own verify-first clause, these are production findings for WS-01, not a reason
to weaken the entry. `CRS_931100_PROTOCOL_HANDLER` was renamed rather than given a fabricated
payload, exactly as the spec directed.

**Deliverable-count reconciliation.** PLAN-13 declared 7 deliverables; the solution outline
resolved them into 6 (spec 3+4 folded into outline 3; spec 5 and 6 split across outline 4 and
5; spec 7 became outline 6). All 7 spec deliverables are covered — the count differs, the
scope does not.

**Test-class inventory drift (for PLAN-15 / TQ-9 / DOC-8).** This plan ADDED three test
classes, which PLAN-13's Dependencies section requires be reported so the inventory docs can
be updated:
- `cui-http-core/src/test/java/de/cuioss/http/security/tests/NfkcFoldClaimInvariantTest.java`
- `cui-http-core/src/test/java/de/cuioss/http/security/tests/AttackDatabaseEntries.java` (shared helper, not a test class)
- and REMOVED three test methods: `EdgeCaseValidURLsDatabaseTest.shouldProcessEdgeCasesEfficiently`,
  `URLPathValidationPipelineTest.shouldRejectPathTraversalAttacks`, and
  `PathTraversalGeneratorTest.shouldNfkcFoldUnicodeSignaturesToAsciiTraversal`.

**Carve-out honoured.** `HeaderAndContentTypeEnforcementRegressionTest.java` (PLAN-04's) was
never opened, as PLAN-13 directed.

**Self-inflicted defect class, worth the epic's attention.** This plan existed to eliminate
assertions that do not assert, and its own new test code reintroduced that class twice (an
NFC-vs-NFKC guard mismatch silently skipping 3 of 8 inputs; unguarded name-prefix branches
silently skipping several database entries). Both were caught by CodeRabbit, not by the plan's
own gates. Separately, a `.formatted()` operator-precedence bug in new test code produced 5
SonarCloud and 5 CodeQL findings from one root cause with no local signal at all. All are
fixed; four candidate-lesson messages carry the detail.

**Two ADRs proposed** (0009 attack-database structural verification, 0010 NFKC fold-claim
registry) — both `Proposed`, awaiting acceptance.
