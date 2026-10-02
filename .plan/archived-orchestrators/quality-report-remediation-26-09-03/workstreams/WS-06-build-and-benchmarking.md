# WS-06: Build and Benchmarking

epic: quality-report-remediation

## Charter

The build is consistent — JPMS requires match the pom, exported packages match the source tree, the
generators classifier artifact packages what it documents, and every log message ID is unique, in
range, and used. Two things are wrong: the forwarded attack-throughput benchmark measures
synchronized console I/O rather than resolver work on two of every three invocations, making its
numbers meaningless; and the CI workflows carry an over-privileged token, an ineffective guard, and
an unserialized cross-repo push. This workstream fixes the measurement and tightens the pipeline,
and closes when all 10 BB findings are resolved.

## Scope

- In scope: `pom.xml` (root), `cui-http-core/pom.xml`, `cui-http-benchmarking/pom.xml`,
  `.github/workflows/**`, `cui-http-benchmarking/src/**` (including `benchmark-logging.properties`
  and `scripts/benchmark-pages.py`), and `.claude/skills/release/SKILL.md`.
- Out of scope: all of `cui-http-core/src/` — BB-5 points at three `security/**` `package-info.java`
  files and is owned by PLAN-04 (WS-01), not here.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-16-benchmark-and-ci-hygiene | staged | The BB HIGH (benchmark measures logging) plus the pages-script guard, workflow privileges, and build hygiene |

## Sequencing and Surface Notes

- Single-plan workstream. The tier exists for grouping and charter, not mandatory fan-out.
- **Fully surface-disjoint from every other plan in this epic** — it is the only plan touching
  `.github/workflows/`, the pom trees, or the benchmarking module. If the operator ever raises
  `parallelization_scope` above 1, PLAN-16 is the safest plan to pair with anything.
- **Adjacency:** the benchmark under `cui-http-benchmarking/src/main/java/de/cuioss/http/forwarded/benchmark/`
  exercises WS-02's resolver. BB-1's fix is to the *logging configuration or the attack inputs*,
  never to the resolver — a resolver change is WS-02's, and PLAN-16 escalates rather than reaching
  across.
- **BB-8 is an operator decision, not a code fix.** Whether `cui-http-benchmarking` should publish
  to Maven Central is a product question the report flags for confirmation. PLAN-16 escalates it.
