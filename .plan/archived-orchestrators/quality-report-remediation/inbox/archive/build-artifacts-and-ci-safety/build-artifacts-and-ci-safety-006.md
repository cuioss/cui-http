envelope_version=1
sender_type=plan
sender_id=build-artifacts-and-ci-safety
epic=quality-report-remediation
kind=landing
created=2026-09-06T09:10:16Z

## What landed

build-artifacts-and-ci-safety shipped as PR #208 (merged), squashed onto main as 73afd22.

```landing-facts
schema=landing-facts/1
plan_id=build-artifacts-and-ci-safety
epic=quality-report-remediation
pr=208
merge_state=merged
deliverables_total=6
deliverables_done=6
total_tokens=4504876
total_wall_seconds=58209
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
merge_commit_sha=73afd22ec4df2cb7ae2c242a0d26532e8af6520c
```

## Residue

Items the epic should carry that no step recorded as a fact.

**Scope grew beyond PLAN-14's six deliverables, by two accepted routes.** A security audit
found the benchmark workflow's harden-runner still in `egress-policy: audit` while the job
mints a deployment token and runs third-party Maven tooling; the operator directed the fix
in-plan. CodeRabbit then found the first version of that fix admitted any Azure Storage
account via a `*.blob.core.windows.net` wildcard, and a second round narrowed it to the 20
enumerated Actions blob hosts read from the GitHub Meta API. The plan therefore shipped a
CI-egress hardening that PLAN-14 never scoped, plus ADR-0016 recording the principle.

**Two review findings were accepted as out of scope and remain open work for the epic.**
(1) The enumerated endpoint list has no drift check against GitHub's published host set — if
GitHub adds a `productionresultssaNN` host, artifact upload or the Maven cache blocks and the
benchmark deploy fails. Declined here as a new automation surface, and because runtime
derivation is near-circular under the egress policy being configured. (2) The required-artifact
list is duplicated between `benchmark-pages.py` and `benchmark.yml`; unifying it needs the
out-of-repo `de.cuioss.sheriff.oauth:benchmarking-common` producer to emit a manifest, which
this repository cannot do.

**A deliberate test-coverage reduction, operator-directed.** PLAN-14's Execution Constraints
required TDD on deliverable 1 (a stdlib Python test of `assemble` against an absent results
directory). The operator directed dropping the test file; verification was re-expressed as
direct command invocation with a pre-change baseline. The deploy-guard fix therefore ships
with no automated regression test. Dropping the test did not clear the `unknown` file-type
classification that motivated the question — `benchmark-pages.py` is unclaimed by any build
extension either way.

**Two named verification arms cannot run in this project and were recorded UNGATED, not
passed.** Neither `module-tests` nor `test-compile` resolves as a canonical command at any
scope (available at `cui-http-parent`: clean, quality-gate, verify, install, compile, package),
yet `module-tests` sits in the phase-5 verification steps and the pre-push gate names both.
Substantively the coverage exists — `verify -Ppre-commit` compiles tests and ran the full
7874-test suite whole-tree — but the manifest advertises arms the project cannot deliver.

**The egress allowlist is unvalidated against a real run.** The benchmark workflow fires only
on merged PRs and tags, and no other workflow here runs block mode, so there was no known-good
allowlist to copy. The first post-merge benchmark run is the validation point. A blocked call
names its destination in the harden-runner run summary; the in-file comment directs a maintainer
to add that host rather than revert to `audit`.

**The three required-artifact filenames couple to an out-of-repo generator.** A
`benchmarking-common` version bump that renames an output fails the benchmark workflow loudly.
The comment blocks direct a maintainer to update the list rather than delete the gate.

**CodeRabbit's hourly quota shaped the run's wall-clock.** Three review cycles were needed
(fix commit, then the ADR commit); two 91-minute quota waits were spent, of an operator budget
of ten. Wall time 16h10m against 2h39m worked — the gap is almost entirely those waits.

**Two CodeRabbit findings rested on factual errors, caught before acting.** It placed
`original-jmh-result.json` at the results-directory root when it actually lives under `data/`
(implementing that literally would have produced a gate failing every legitimate run), and it
asserted the `allowed-endpoints` block contained none of the `productionresultssaNN` hosts when
the file carries 20 of them. Both were verified against the live artifacts before disposition.
Five candidate lessons are already filed in this epic's inbox covering that class.
