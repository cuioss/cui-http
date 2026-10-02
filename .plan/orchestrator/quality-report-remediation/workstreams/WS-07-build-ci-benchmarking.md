# WS-07: Build, CI and Benchmarking

epic: quality-report-remediation

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-07-build-ci-benchmarking.md` and is tracked in the epic
> `status.json` `workstreams[]` field. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

This workstream owns the build and delivery infrastructure: the three POMs, the eight GitHub
workflows, the JMH benchmark module and its publishing script. Its five findings are small in
number but include the epic's only *destructive* one — the benchmark `assemble` step tolerates
a missing results directory and the deploy step then publishes that empty tree over the live
benchmark site — plus a published artifact defect: the `generators` classifier jar leaks a
nested test class and carries a duplicate module name, which reaches every downstream consumer
of the artifact. It closes when no workflow can publish an empty result over a good one, the
published classifier jar contains exactly what it claims, and the benchmark workflow requests
only the token scope it uses.

## Scope

- In scope: `pom.xml`, `cui-http-core/pom.xml`, `cui-http-benchmarking/pom.xml`,
  `.github/workflows/**`, `cui-http-benchmarking/scripts/benchmark-pages.py`, and the
  benchmark module's Java sources where a naming or data issue affects measurement validity.
- Out of scope: all library production code and tests (WS-01..WS-05); all documentation
  (WS-06), except a build-related statement inside a workflow or POM comment.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-14-build-artifacts-and-ci-safety | staged | Benchmark publish safety, classifier-jar contents and module name, unused Lombok, token scope, benchmark data naming |

## Sequencing and Surface Notes

- A single-plan workstream is legitimate; the tier exists here for grouping and charter, and
  five findings over three POMs and one workflow do not warrant a split.
- This workstream is **surface-disjoint from every other workstream in the epic** — no other
  plan touches a POM, a workflow, or the benchmark module. It is therefore the epic's safest
  concurrency partner and can be scheduled against whatever code plan is running.
- Because it is disjoint and self-contained, PLAN-14 is a good candidate to emit **early**
  despite its low severity: it is the one plan whose landing cannot be invalidated by any other
  plan's changes.
- `F-D-14` (the deploy step wiping the published site) is the epic's only finding with an
  ongoing destructive risk on every scheduled benchmark run. That argues for scheduling this
  plan first in wall-clock terms, even though the report ranks it Low.
