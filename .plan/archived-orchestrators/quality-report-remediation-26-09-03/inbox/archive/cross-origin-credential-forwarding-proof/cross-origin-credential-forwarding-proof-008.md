envelope_version=1
sender_type=plan
sender_id=cross-origin-credential-forwarding-proof
epic=quality-report-remediation
kind=landing
created=2026-09-03T09:32:48Z

## What landed

cross-origin-credential-forwarding-proof shipped as #194 (merged) — the end-to-end proof that `FORWARD_TO_ALLOWLISTED` actually forwards credentials across an allowlisted cross-origin HTTPS hop, falsified three-pass.

```landing-facts
schema=landing-facts/1
plan_id=cross-origin-credential-forwarding-proof
epic=quality-report-remediation
pr=194
merge_state=merged
deliverables_total=2
deliverables_done=2
total_tokens=2301113
total_wall_seconds=12765
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:skipped,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:done
step.finalize-step-sync-baseline.action=rebased
step.finalize-step-sync-baseline.upstream_commit_count=1
step.create-pr.pr_number=194
step.branch-cleanup.merge_commit_sha=ff12f345d1e95dca54a81b7da575364de4d0dae4
step.branch-cleanup.merge_strategy=merge_queue
step.sonar-roundtrip.new_code_issue_count=0
step.sonar-roundtrip.count_status=confirmed
```

## Residue

**The spec's open question resolved without the arrangement it anticipated.** PLAN-23 named "can
`@EnableMockWebServer(useHttps = true)` provide two servers in one test class" as its main risk and
deferred it to outline. The answer is that the extension exposes only `manualStart` / `useHttps` —
there is no multi-server API — but a second server proved unnecessary:
`RedirectDispatcher.PATH_ALLOWLISTED` already rewrites only the host (`localhost` → `127.0.0.1`),
preserving scheme and port, so a single TLS server yields a genuinely cross-origin `https` hop. The
real blocker was the certificate: the shared self-signed cert carries a single `localhost` DNS SAN,
so connecting to the IP literal failed hostname verification. Fixed additively via
`@TestProvidedCertificate` supplying a `127.0.0.1` `iPAddress` SAN. **No production code changed** —
the plan added evidence only, as its write-boundary required.

**Falsification result (deliverable 2).** Neutralizing the single policy-consumption site at
`HttpHandler.java:789` moved the suite from 7790/0 to 7788/2, and the two failures were exactly the
credential-forwarding pair reporting `expected: <present> but was: <absent>` for both `authorization`
and `cookie` — no timeout, no handshake error. Restored: 7790/0 with `git diff --exit-code` clean.
The `STRIP_ON_CROSS_ORIGIN` control kept passing throughout, which independently proves the hop is
genuinely cross-origin (a same-origin resolution would have made both strategies forward and failed
the STRIP test).

**Two review-bot registry defects found, neither fixable inside this plan's write boundary.**
(1) `coderabbit.md` declares only `review_body`/`inline` as `participation_evidence`, but CodeRabbit
posts its clean verdict as an `issue_comment` — so a genuinely-reviewed PR classifies as `absent`.
This produced a spurious loop-back that could not self-resolve; closed via the force-done escape
hatch, reproduced identically on two separate PRs. (2) Sourcery's hard-quota wording ("you've used
your own review **budget** of 250,000 diff characters") matches neither registered `refusal_pattern`
nor the structural recognizer (which keys on `exceeded|reached|hit`, not `used`), so a hard refusal
was mis-credited as `participated` — the unsafe error direction.

**A live config defect remains on `main`.** `.plan/marshal.json:103` reads
`required_bots: "coderabbit,cuioss-review-bot"`. `required_bots` takes a **bot_kind**, not an author
login; the installed registry declares `bot_kind: pr-agent` with `author_login: cuioss-review-bot`.
Commit `c7862d0` (#192) propagated the author rename into the bot_kind slot. This plan reverted only
its own plan-local snapshot — the tracked file at merged HEAD `ff12f34` still carries the defect and
will re-fire on the next plan. **Needs a separate plan.**

**Two coverage gaps recorded rather than absorbed.** `scope_creep_check` returned
`could_not_look` / `no_baseline_sha` in phase 5 because `references.json` carries no
`plan_creation_sha`, so scope containment was hand-checked, not guard-verified — notable because
D1's own success criterion was a scope-containment assertion. Separately,
`pre-commit-verify-freshness` passed on notation corroboration with `scope_cross_check: undetermined`
(`required_coverage_unknown`): the coverage dimension is unaudited, not shown adequate.

**The pre-submission self-review is domain-blind here.** The `ext-self-review-plan-marshall` surfacer
produced zero candidates over a Java diff (classified `other`), so its clean verdict rested on no
evidence — its own `delta_coverage.statement` says so. Recorded as a WARNING deviation rather than an
ordinary clean pass. Relatedly, the zero-candidate case has no legal representation in the dispatch
prompt contract: an empty surface presents as a malformed dispatch (`missing required prompt field:
candidates`).

**No ADRs were proposed**, per the spec's instruction that this plan settles no new architectural
decision. `adr-propose` was not in the composed step set under the `standard` posture.
