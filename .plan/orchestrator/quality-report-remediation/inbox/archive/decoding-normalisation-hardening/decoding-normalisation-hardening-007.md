envelope_version=1
sender_type=plan
sender_id=decoding-normalisation-hardening
epic=quality-report-remediation
kind=landing
created=2026-09-06T07:13:38Z

PLAN-01 landed: the decode → normalise chain now delivers the protections it advertised.

All six deliverables shipped in one squash merge. Both of the epic's High findings on this
surface (F-A-1 double-encoding, F-A-2 delimiter-before-normalisation) are closed, along with
F-A-4/L-2, L-1/F-A-8/V-1/L-13, and L-10/F-B-16.

Both verify-first clauses were discharged at outline time rather than deferred: a 584-file sweep
found no consumer of the U+FFFD replacement behaviour, and ADR-0014 was confirmed to pin the
clamp twice over (its Alternatives section explicitly rejected the option this plan adopts), so
the conditional ADR path fired — ADR-0016 supersedes it.

Five findings beyond the spec were found and fixed during finalize: two CRLF log-injection paths
(CWE-117/CWE-93) in the plan's own new code, found by the security audit; and three by the PR
review, two of which were genuine bypasses — `Character.digit` accepting fullwidth digits so
`%`+U+FF12 U+FF26 decoded to `/`, and the NFKC fold assembling `%2F` after the surviving-encoding
check ran. A third review round caught that the first fix for the latter read the returned value
rather than the normalised form, so `lenient()` reached a different verdict — the exact asymmetry
this plan existed to remove, reintroduced by its own fix.

```landing-facts
schema=landing-facts/1
plan_id=decoding-normalisation-hardening
epic=quality-report-remediation
pr=#210
merge_state=merged
deliverables_total=6
deliverables_done=6
total_tokens=2699830
total_wall_seconds=51180
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
step.branch-cleanup.merge_commit=7a0da52
step.create-pr.pr_number=210
step.finalize-step-security-audit.findings=2
step.automatic-review.findings=6
step.pre-submission-self-review.verdict=not_run
```

## Residue

Three things WS-06 / the epic should pick up, none of which this plan could close inside its
write boundary:

1. **`SecurityDefaults.java` carries two stale `LENIENT_CONFIGURATION` Javadoc claims.** Both
   double-encoding gates are now unconditional, so the preset no longer relaxes them. That file
   is outside this plan's boundary. Already filed as inbox message
   `decoding-normalisation-hardening-001`.
2. **`SecurityConfiguration.allowDoubleEncoding()` no longer gates any validation** but remains a
   public record component and builder setter, still read by `isStrict()` / `isLenient()`.
   Removing it is a breaking public-API change governed by ADR-0008 and was deliberately left
   out of scope; ADR-0017 records why the interim state is accepted rather than accidental.
   The two `doc/*.adoc` consumers were likewise not inspected.
3. **PLAN-10 / PLAN-11 (WS-05) will need re-baselining.** This plan changed the verdicts their
   tests assert. Deliverable 6's sweep fixed the mechanical breaks it found in-module; a
   premise-changing break would have been filed separately, and none was.

Two process observations recorded as separate candidate-lesson messages (`-002` … `-006`), of
which one is repository-wide rather than plan-local: **`references.json.plan_creation_sha` has no
writer anywhere in the installed bundle**, only a reader in phase-5-execute, so the scope-creep
guard resolves `no_baseline_sha` on every plan, not just this one. No scope-creep measurement was
taken for this plan; the committed file set was verified by hand against the declared
deliverables instead.
