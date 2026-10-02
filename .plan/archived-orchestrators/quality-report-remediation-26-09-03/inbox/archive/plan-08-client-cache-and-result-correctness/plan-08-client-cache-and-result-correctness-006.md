envelope_version=1
sender_type=plan
sender_id=plan-08-client-cache-and-result-correctness
epic=quality-report-remediation
kind=landing
created=2026-08-27T20:17:19Z

plan_id=plan-08-client-cache-and-result-correctness
outcome=landed
pr=166
merge_commit=f1ba539
base_branch=main
deliverables_total=6
deliverables_landed=6
findings_covered=CL-1,CL-6,CL-13,CL-14,CL-19
tests_before=5471
tests_after=6453

# PLAN-08 landed: client cache and result correctness

Merged as **#166**, squash commit `f1ba539` on `main`. All six deliverables shipped.
(PR #157 was opened and closed unmerged first — same branch, no code difference; it was
replaced to obtain a review event after CodeRabbit's quota refused a re-review.)

## Findings covered

| ID | Severity | Status |
|----|----------|--------|
| CL-1 | HIGH | fixed — three-way method-gated 304 |
| CL-6 | MEDIUM | fixed — 204/205 exempted from the empty-content failure |
| CL-13 | LOW | fixed — status + ETag preserved on conversion failure |
| CL-14 | LOW | fixed — cache invalidated when a fresh 200 cannot replace the entry |
| CL-19 | INFO | fixed — cache-key allocation skipped for non-cacheable requests |

## Deferred, not dropped

**PATCH and OPTIONS test coverage for the 304 matrix.** `cui-test-mockwebserver-junit5` 1.5.0
declares no PATCH/OPTIONS `HttpMethodMapper` constants and no `handlePatch`/`handleOptions`
hooks, so `TestApiDispatcher` cannot serve those methods. Tracked as finding `36b80c`.

Production gating covers them correctly — the unsafe-method branch is a catch-all on
`!canReadCache(method)`, so PATCH and OPTIONS take the RFC 7232 failure path. Only their test
rows wait. DELETE/PUT/POST cover the unsafe-method equivalence class meanwhile.

Upstream fix is open: **cuioss/cui-test-mockwebserver-junit5 PR #104** (CI green, unmerged).
Consumption chain the operator chose: release 1.6.0 → bump `version.cui.test.mockwebserver` in
`cuioss-parent-pom` → release parent → bump `cui-java-parent` here → add the two rows.

## For PLAN-15 / DOC-9 (WS-05)

`doc/http-result-pattern.adoc` needs reconciling. A conversion-failure `HttpResult` now
resolves `getHttpStatus()` and `getETag()` where both were previously empty (CL-13). Not
edited here, per this plan's write-boundary.

## What the local gates did not catch

Worth carrying into sibling plans. The headline deliverable shipped **unreachable**: D6's
cache-key optimization narrowed the cache read to `GET||HEAD`, so `cachedEntry` was always
null for unsafe methods and the `304 && cachedEntry != null` gate could never fire for them.
Its test passed via the generic error path, which coincidentally returns `INVALID_CONTENT`.

Five local gates passed it — `verify -Ppre-commit`, `verify -Pcoverage`, pre-submission
self-review, simplify, and the phase-4 Q-Gate. Two independent review bots caught it. The
outline had flagged the D6/D2 interaction as a risk before execution began.

Four `kind: candidate-lesson` messages (001-004) carry the detail; 005 carries an owed
`architecture enrich insight --module cui-http` hint about classifying bot rate-limit notices
at ingestion rather than storing them as blocking findings.

## Verification

`verify -Ppre-commit` and `verify -Pcoverage` green at 6453 tests (5471 at plan start; the
rebase onto 10 upstream commits accounts for most of the growth). CI green on the merge HEAD:
Maven Build (push + pull_request), CodeQL, Dependency Review. Sonar new-code issues: 0,
confirmed. Review barrier `participation_complete: true` — coderabbit, pr-agent and sourcery
all participated, zero stale.
