envelope_version=1
sender_type=plan
sender_id=plan-16-benchmark-and-ci-hygiene
epic=quality-report-remediation
kind=landing
created=2026-08-29T16:09:18Z

# Landing: PLAN-16 — Benchmark and CI Hygiene

epic: quality-report-remediation
workstream: WS-06
plan_id: plan-16-benchmark-and-ci-hygiene
outcome: merged
pr: https://github.com/cuioss/cui-http/pull/170
merge_commit: 9f26433ae91334af7fe3d8d2e2c6ab01a1eb3c96
base: main
files_changed: 13
tasks_completed: 8
deliverables: 6

## Findings disposition

| ID | Disposition |
|----|-------------|
| BB-1 | FIXED — `de.cuioss.http.forwarded.level = SEVERE` in `benchmark-logging.properties` |
| BB-2 | FIXED — existence guard hoisted before any mkdir side effect in `benchmark-pages.py` |
| BB-3 | FIXED — benchmark job token downgraded to `contents: read` |
| BB-4 | FIXED — bounded 5-attempt fetch-rebase-retry around the cross-repo pages push |
| BB-6 | FIXED — explicit `<scope>test</scope>` on `junit-jupiter-api` |
| BB-7 | FIXED — `quick` profile RENAMED to `smoke` (timings unchanged) |
| BB-8 | FIXED — operator confirmed unintended; publish/deploy skip added |
| BB-9 | ACCEPTED — cosmetic version-line tension, no code change |
| BB-10 | FIXED — template comments removed from 4 workflows |
| BB-5 | NOT IN SCOPE — same defect as SV-15, owned by PLAN-04 (WS-01) |

## Load-bearing results for downstream plans

**BB-1 is confirmed by measurement.** `resolveAttackThroughput` moved from 43.3K ops/s
(captured baseline at `534d111`) to 539.0K ops/s — ~12.4x — with zero
`de.cuioss.http.forwarded` warning records. The sibling `resolveForwardedThroughput`
measured 569.0K in the same baseline run, so the attack path now sits alongside it.

**Any future benchmark comparison must baseline against `534d111`, never `7ae6499`, and
never against a pre-merge point of this PR.** The pre-fix gh-pages trend points are
actively purged by `benchmark-pages.py`, so the published series is discontinuous at this
landing by design. The captured baseline artifact is preserved at the plan's
`work/baseline-534d1119.json`.

**BB-8 correction worth propagating:** the task prescribed `maven.deploy.skip`, which is
INERT against this project's real publication path. Release publishes via
`org.sonatype.central:central-publishing-maven-plugin`, and the parent already sets
`maven-deploy-plugin`'s `<skip>true</skip>` globally. The load-bearing switch is
`skipPublishing`. Any sibling plan touching publication config should verify against the
effective POM, not the pom source.

**Two spec assumptions were REFUTED during outline and must not be re-asserted:**
1. The `quick` profile was NOT referenced in `.github/workflows/benchmark.yml` (the spec
   expected it there); it WAS referenced at `doc/http-security/specification/testing.adoc`
   (the spec did not anticipate that).
2. BB-10 covers FOUR workflow files, not the three the spec implied — `maven.yml`,
   `release.yml`, `dependency-review.yml`, `scorecards.yml`. `pr-agent.yml` carries a
   genuine descriptive header and is correctly excluded.

## Out-of-footprint findings surfaced, NOT owned here

The Sonar fetch returned 7 new-code findings, ALL in `cui-http-core/src/**` Java sources.
This plan edited no Java source, and the fetch reported `pull_request: none`, so these are
pre-existing main-branch issues surfaced by a branch-level scan. They were resolved
`taken_into_account` and deliberately NOT dismissed server-side — they are real and belong
to whichever plan owns those files:

- `java:S1845` BLOCKER — `ForwardedHeaderResolver.java:646` (field `UNRESOLVABLE` vs `unresolvable`) — relevant to WS-02
- `java:S2589` — `ETagAwareHttpAdapter.java:539` (expression always true) — relevant to the client workstream
- `java:S1905` — `RetryConfig.java:163` (unnecessary cast)
- `java:S127` ×2 — `LengthValidationStage.java:198`, `DecodingStage.java:406` (loop-counter assignment)
- `java:S5778` — `HeaderAndContentTypeEnforcementRegressionTest.java:228`
- `java:S9142` — `ValidationTypeTest.java`

## Surface touched

`.github/workflows/{benchmark,maven,release,dependency-review,scorecards}.yml`,
`CLAUDE.md`, `doc/http-security/specification/testing.adoc`,
`cui-http-benchmarking/{pom.xml,scripts/benchmark-pages.py,src/main/resources/benchmark-logging.properties}`,
`cui-http-core/pom.xml`, `.plan/marshal.json`.

No other plan in this epic touches `.github/workflows/`, the pom trees, or the
benchmarking module, so the declared surface-disjointness held.
