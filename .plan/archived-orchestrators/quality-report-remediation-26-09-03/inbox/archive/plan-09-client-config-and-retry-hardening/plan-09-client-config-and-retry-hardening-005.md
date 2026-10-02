envelope_version=1
sender_type=plan
sender_id=plan-09-client-config-and-retry-hardening
epic=quality-report-remediation
kind=landing
created=2026-08-30T10:29:31Z

## What landed

plan-09-client-config-and-retry-hardening shipped as PR #172 (merged, squash 6ee0c7c). All six
deliverables landed: RetryConfig compact-constructor invariants, non-transient TLS reclassified to
CONFIGURATION_ERROR, resolveUri() made pure, host:port recognised as authority, HttpHandler
AutoCloseable plus interrupt-aware blocking and retry cancellation, and a cross-cutting regression
suite. Tests 6453 -> 6488.

```landing-facts
schema=landing-facts/1
plan_id=plan-09-client-config-and-retry-hardening
epic=quality-report-remediation
pr=172
merge_state=merged
deliverables_total=6
deliverables_done=6
total_tokens=4003916
total_wall_seconds=121320
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:skipped,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
```

## Residue

- CL-7 shipped with a hole and was fixed in-run. The compact constructor's ordered comparisons
  (`jitter < 0.0 || jitter > 1.0`, `multiplier < 1.0`) are all false for Double.NaN, so NaN passed
  every guard and reached calculateDelay() — reproducing the exact hot-retry loop the deliverable
  existed to prevent. Found by coderabbit, not by this plan's outline, q-gate or self-review. The
  hole was at THREE seams (compact constructor + both eager Builder setters), one more than the
  review comment named. Fixed in 482dd1d with !Double.isFinite guards and 4 tests.
- Behaviour change beyond the stated scope: the isFinite guard also newly rejects +Infinity for
  multiplier, which the old `multiplier < 1.0` check silently accepted. In scope under
  compatibility=breaking, but not something the spec called for.
- WS-06 (cui-http-benchmarking): NO cross-module break. Sweep found exactly one `new RetryConfig(`
  call site (Builder.build()) and zero in tests or the benchmark module, so the verify-first clause
  is satisfied with nothing to escalate.
- PLAN-14 (DOC-5, doc/client-handlers-readme.adoc): deliverable 5 created a documentation gap. The
  adoc has no close()/AutoCloseable/cancellation coverage and HttpHandler now implements
  AutoCloseable with an interrupt-aware blocking contract. Not edited here per the write boundary.
  Note doc/http-result-pattern.adoc already documented SSL under CONFIGURATION_ERROR, so CL-9
  closed a code-vs-doc divergence rather than creating one.
- Coverage gap shipped knowingly: HTTPS MockWebServer is broken project-wide (mockwebserver3 calls
  an okhttp Platform.configureTlsExtensions overload that no longer exists), so no end-to-end
  request over real TLS through the adapter stack is covered. TLS behaviour is asserted where
  deterministic. Recorded as lesson 2026-08-29-12-001 with a recommended dependency fix.
- One coderabbit finding was DECLINED (162339, resolveUri()): it contradicts this plan's
  Q-Gate-validated HOST_PORT_PATTERN decision that a letter-prefix name followed only by digits
  (`ftp:21`) is deliberately host:port shorthand, since real schemes are always followed by `//`.
  Reversing it needs a scheme blocklist, against the lean posture. Reason posted on the PR.
- Process: the finalize run lost ~3h and all three loop-back iterations to a review-bot stall that
  was NOT a quota problem. Two of three bots in this repo (coderabbit, pr-agent) do not react to
  push events and need explicit triggers; a stale never-updated refusal comment was read as a live
  refusal. Four candidate lessons were filed to this epic's inbox (001-004); three name
  plan-marshall-owned components the epic's host repo does not own and need --allow-foreign-store
  or carrying to the plan-marshall repo.
- The loop-back iteration counter was reset 3 -> 0 with operator approval, because all three spent
  iterations were zero-finding no-op re-polls at one unchanged HEAD and none was a remediation
  cycle. Justification is in decision.log.
