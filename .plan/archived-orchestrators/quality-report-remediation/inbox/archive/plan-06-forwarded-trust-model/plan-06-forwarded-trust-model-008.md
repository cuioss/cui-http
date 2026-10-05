envelope_version=1
sender_type=plan
sender_id=plan-06-forwarded-trust-model
epic=quality-report-remediation
kind=landing
created=2026-09-08T15:08:13Z

## What landed

plan-06-forwarded-trust-model shipped as cuioss/cui-http#214 (merged as `6e1a19c`).

```landing-facts
schema=landing-facts/1
plan_id=plan-06-forwarded-trust-model
epic=quality-report-remediation
pr=cuioss/cui-http#214
merge_state=merged
deliverables_total=7
deliverables_done=7
total_tokens=2562256
total_wall_seconds=68869
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:skipped,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:skipped,record-metrics:done,finalize-step-print-phase-breakdown:skipped,emit-landing:done
```

## Residue

**ADR allocated by this plan: ADR-0021 — "Unresolvable Forwarded header suppresses only the fields it
carried"**, at `doc/adr/0021-Unresolvable_Forwarded_header_suppresses_only_the_fields_it_carried.adoc`.
It supersedes ADR-0002, which is now `Status: Superseded`.

⚠ **The number is 0021, not the 0019 this plan's inbox message -001 originally announced.** ADR-0019 was
allocated against `origin/main` when 0018 was the highest number there. Upstream PR #217 then landed its
OWN ADR-0019 and an ADR-0020 while this plan was in review, so both branches defined a different 0019.
The rebase was clean and the tests were green with both files present — nothing but an explicit
pre-merge re-check catches this class. Message -001 has been corrected in place. This is the second
recurrence of the collision the epic already tracks (PLAN-01 and PLAN-14 both shipped an ADR-0016).

**Deliverable 4 resolved to the peer-address branch, not the rename branch.** The spec left it as a
genuine fork; the operator chose to thread an optional `InetAddress` through a new
`resolve(Function, InetAddress)` overload, so `trustedProxies` now gates whether forwarded headers are
believed at all rather than only which hops are skipped. The published API gained an overload; nothing
was renamed.

**Deliverable 1 resolved to configured precedence, not fail-closed.** The operator chose a
`deFactoPrecedence` configuration knob over resolving a cross-family disagreement to unresolvable. It
defaults to `X_FORWARDED`, which reproduces the previous behaviour exactly, so the change is not
integrator-visible by default. Note this makes the two reconciliation rules deliberately asymmetric: an
RFC-vs-de-facto disagreement still fails closed, while a de-facto-vs-de-facto disagreement resolves by
precedence.

**Five review rounds found eight defects, all fixed and verified.** Two from Sourcery, three from the
security audit, three from CodeRabbit. The most serious was a real trust-boundary leak: a de-facto host
token proven forged (it disagreed with the trusted RFC host) still had its embedded port honoured,
because the trusted side stated no port to contest it. It was found by re-firing the security audit
after HEAD advanced, and by compiling and running probes rather than reading the diff — the first audit
pass could not have seen it.

**`doc/forwarded-header-resolution.adoc` is still owed to WS-06 PLAN-13**, and the report in message
-001 stands. One executing envelope edited that file directly despite the spec's prohibition; the edit
was reverted and the file is untouched by this PR. Its five stale passages are unchanged and still need
PLAN-13 to reconcile them — with the addition that the document now also predates the
`deFactoPrecedence` knob, the peer-address overload, and the structural port-statement rule.

**The scope-creep guard never ran for this plan.** `scope_creep_check` returned
`could_not_look / no_baseline_sha` on every task because `references.json` carries no
`plan_creation_sha`. Scope was verified by hand instead (`git status` against the declared step targets
on each task). No scope-creep measurement exists for this run either way — an absent measurement, not a
clean one. Filed as candidate-lesson -006.
