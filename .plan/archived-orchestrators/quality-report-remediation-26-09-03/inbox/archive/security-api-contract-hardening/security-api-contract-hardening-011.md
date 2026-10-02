envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=landing
created=2026-08-29T15:56:18Z

## What landed

security-api-contract-hardening shipped as #169 (merged) — six independent contract divergences in `de.cuioss.http.security` corrected in place, plus three defects the review cycle surfaced.

```landing-facts
schema=landing-facts/1
plan_id=security-api-contract-hardening
epic=quality-report-remediation
pr=#169
merge_state=merged
deliverables_total=6
deliverables_done=6
total_tokens=2352903
total_wall_seconds=22020
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:done
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_commit=e2c7ebdcbf358e69b23fc602dea3629078bf9868
step.create-pr.pr_number=169
step.finalize-step-sync-baseline.action=noop
step.finalize-step-sync-baseline.upstream_commit_count=0
step.finalize-step-preference-emitter.hints_owed=0
step.lessons-capture.inbox_messages_written=10
```

## Residue

Items the epic should track that no step recorded as a fact:

- **SV-14 was broader than the spec stated, and the correction was still incomplete on the first pass.** The spec described pipeline `equals`/`hashCode` as ignoring configuration; outline discovery found four of five pipelines carry an *empty* `@EqualsAndHashCode` basis, so every instance of a class compared equal regardless of config. The operator's outline review then caught that the fix's new `config` field would grow the exported API via a class-level Lombok `@Getter`, and D5 was revised to suppress it with `@Getter(AccessLevel.NONE)` plus a reflection test. CodeRabbit subsequently found the *same* field still leaking through `@ToString` on the same classes. Two generators, one field, caught at two different stages — the durable rule is "enumerate every generating annotation on the type", not "remember `@Getter`". Filed as inbox message 008.

- **A fourth false Javadoc claim in `SecurityEventCounter` was found that the source report did not list**: `getFailureTypeCount()`'s "have had at least one event recorded" is also untrue after `reset()`. Corrected under SV-9 alongside the three the report named.

- **SV-8's fix introduced a new doc-vs-behaviour divergence, caught by CodeRabbit.** `unquote()` treated every trailing quote as a closing delimiter, so `name="abc\"` was stripped as balanced and returned `abc\` — contradicting the method's own Javadoc promise that unbalanced values are returned unchanged. This is the exact defect class the epic exists to close, reintroduced by a fix for it. Fixed in `3313a97` with regression tests in both directions.

- **D4's test deviates from both routes the outline proposed.** Both assumed a body exceeding `Integer.MAX_VALUE` bytes (~700M chars, ~1.5 GB heap), infeasible in a unit test. The executor asserts the resolved limit via reflection on the private `getMaxLength()` instead — discriminating old from new exactly, but coupling a test to a private method name. Worth an epic-level view on whether that coupling is acceptable across WS-01.

- **Sourcery (optional review bot) never reviewed this PR.** Its 7-day 250,000-diff-character budget was exhausted; it resets ~2026-08-30T12:00Z. CodeRabbit and pr-agent (both required) did review the final tree. If other WS-01 plans land in the same window, Sourcery coverage will be absent for them too.

- **Two tooling degradations this run, both worked around rather than fixed.** `gh auth status` takes ~60.6s on this host, just over `ci_base.run_cli`'s 60s subprocess timeout, so every `ci`/`github_ops` call reported "Not authenticated" despite valid credentials — the review FIND and RESPOND stages were done by hand via `gh api`, and the two `pr-comment` findings were filed manually. CodeRabbit also hit its hourly OSS review limit on first pass and had to be re-triggered. Filed as inbox messages 009 and 010; message 010 notes the masked failure mode — a run where the same degradation yields zero findings is currently indistinguishable from a clean review.
