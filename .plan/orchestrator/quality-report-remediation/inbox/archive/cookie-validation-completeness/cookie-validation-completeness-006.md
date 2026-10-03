envelope_version=1
sender_type=plan
sender_id=cookie-validation-completeness
epic=quality-report-remediation
kind=landing
created=2026-09-09T15:15:14Z

PLAN-05 (WS-02) landed. `validateCookie` now validates the cookie: name and value both run
through `CharacterValidationStage`, the four security prefixes match case-insensitively, and
repeated attributes resolve last-wins with trimmed keys. Two of the four behaviour changes closed
genuinely exploitable fail-open holes, not cosmetic divergences.

```landing-facts
schema=landing-facts/1
plan_id=cookie-validation-completeness
epic=quality-report-remediation
pr=231
merge_state=merged
deliverables_total=5
deliverables_done=5
total_tokens=3682061
total_wall_seconds=14343
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:done
merge_commit=ca74911857ea43c22cd4f6d6aced309f7e74ca6d
base_at_merge=b09925ae838716f51be8b33dbd20a11ddd8c1b9b
```

## What shipped

Six commits, squash-merged as `ca74911`. Realized footprint is exactly the 10 declared files —
declared and realized agree, with no under-declaration.

- `b489b80` — prefix matching moved from two `String.startsWith` branches to one `SecurityPrefix`
  enum under an ASCII-only case fold, adding `__Http-` and `__HostHttp-`. `String.regionMatches(true, …)`
  was deliberately rejected: it folds over all of Unicode, so `__ſecure-` would read as prefixed
  although no user agent treats it so.
- `96ff4a1` — cookie values are now character-validated; name validation no longer rests on
  `String.trim`, which was blind to NBSP, ZWSP, U+3000 and NEL. Both prefix factories route
  through one shared guard, so `hostPrefix(null, "v")` throws instead of yielding `__Host-null`.
  Reported `Domain`/`Path` values go through `renderForDetail` (log-forging path).
- `cbac689` — `extractAttributeValue` resolves last-wins per RFC 6265 §5.3.
- `ceb369b` — one shared `splitAttributes`/`attributeKey` with the §5.2 trim rule.
- `2d2e1f2` — the cookie-content pipeline absence stated as a decision in `PipelineFactory` and
  `CookiePrefixValidationStage` Javadoc. No `COOKIE_NAME`/`COOKIE_VALUE` pipeline was implemented.
- `89d143a` — `assertComponentRejected` pinned to failure and validation type under both presets;
  `AttributeParser` Javadoc RFC attribution corrected.

Two operator decisions were taken at refine and are settled, not open: `__Http-`/`__HostHttp-`
are IN scope (deliverable 1's "record the decision" clause is discharged), and deliverable 5
resolves to its documentation half only. The verify-first clause was checked: neither
`functional-requirements.adoc` (HTTP-14) nor `specification.adoc` promises those pipelines.

## Exploitable before, closed now

- `__Host-a=1; Secure; Path=/; Path=/admin` PASSED `validateHostPrefix` because the gate read
  `Path=/` while the browser scopes the cookie to `/admin`.
- `__Host-x=1; Secure; Path=/; Domain =evil.com` PASSED because `getDomain()` returned empty on
  the whitespace-padded key while a browser applies the `Domain` — defeating exactly what
  `__Host-` encodes. The `AttributeParser` comment calling that skip "strict RFC compliance"
  misread §5.2, which mandates trimming.

## Handoffs

- **WS-05 PLAN-10 (`CookieChaosAttackTest.java`)** — three stale passages at lines **60-66,
  240-244 and 300-303** (the staged spec said 241-244 and 301-303; the Javadoc blocks start one
  line earlier). All three are prose; the assertions exercise only the `Cookie` record's data
  behaviour and reference no factory message, so nothing there broke. Not edited by this plan.
- **WS-06 PLAN-13 (`doc/`)** — falsified by this landing:
  `doc/http-security/specification/pipeline-architecture-standards.adoc` and any prose describing
  cookie-prefix case-sensitivity or first-match attribute resolution; and
  `doc/http-security/functional-requirements.adoc`, which still contains a "not yet implemented"
  hit for the cookie pipelines that now contradicts the production refusal message.
- **Owed architecture hint, not written** (post-merge writes are unpushable):
  `architecture enrich --module cui-http` — `java:S4144` on a dedicated RFC-clause regression test
  in the `de.cuioss.http.security.data` tests is suppressed at the call site, not collapsed.

**No ADR was allocated by this plan.** The `standard` posture composed `adr-propose` out, so the
0022 high-water is untouched by this landing. ADR-0017, ADR-0019 and ADR-0020 were consulted and
none is contradicted.

## Residue

- **A test was pinning the fail-open behaviour as correct.**
  `CookieTest.shouldHandleAttributesWithSpaces` asserted the deliverable-4 bypass with the comment
  *"This is actually correct per RFC 6265"*. That comment is why the defect survived earlier
  review. Rewritten, not deleted. This is the sibling-consistency class the epic already tracks,
  in its most durable form: a wrong claim written into a test as justification.
- **A finalize contract gap forced an orchestrator workaround.**
  `pre-submission-self-review` declares `mutates_source: false` but classified its own loop-back
  as inline-fixable (`loop_back_target: 6-finalize`) and then made source edits. Item 5f skips
  commit instrumentation on the declared fact, so the verified-green edits sat uncommitted and
  the push barrier's clean-tree assertion would have dropped them silently. The orchestrator
  committed them by hand as `89d143a`. Filed as candidate-lesson 001.
- **Two epic-brief predictions reproduced exactly.** `derive_gate_bundles` selected 0 bundles with
  all 10 footprint paths unresolved, and `build-maven` exposes no `resolve-test-scope` verb — so
  the per-bundle quality-gate arm and the module-tests divergence gate BOTH did not gate this
  push. The whole-tree arm did run the full suite green at the merged HEAD, which mitigates but
  does not substitute. Recorded as warnings against the plan rather than passed over.
  The brief's third prediction was WRONG in this repo: `pre-submission-self-review` DID resolve an
  implementor (`ext-self-review-plan-marshall`, full-surface, 10 files) and found a real defect.
- **The CI arm counts review bots.** The first `ci-complete` poll returned
  `ci_final_status: timeout` naming CodeRabbit as the sole failing check while all 22 build checks
  were already SUCCESS. Recording that as `ci-verify-timeout` would have been a false failure on a
  green build; a re-poll settled it. Filed as candidate-lesson 003.
- **Merge routing.** `main` carries a required merge queue, so the configured squash merge was
  refused with "an immediate merge would close the PR unmerged" and the PR was routed through
  `pr merge-queue` instead. Sibling plans in this epic should expect the same.
- **`scope_cross_check: undetermined`** (`required_coverage_unknown`) on the push freshness gate:
  it permitted on the worktree-sha match, not on a coverage verdict.
