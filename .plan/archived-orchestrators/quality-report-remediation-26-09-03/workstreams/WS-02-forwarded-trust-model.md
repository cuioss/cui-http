# WS-02: Forwarded-Header Trust Model

epic: quality-report-remediation

## Charter

The `X-Forwarded-For` chain-walk algorithm is correct and fails closed — the review confirms this
positively. The defect is in the *integration surface* around it: how a caller wires headers into
the resolver, which header family wins, and which end of a comma-separated list is trusted for
non-IP fields. Three of the epic's eight HIGH findings sit here, and each one re-opens a spoofing
path the algorithm itself closes. This workstream makes the resolver's real deployment
preconditions either enforced or explicitly stated, and closes when all 21 FW findings are resolved.

## Scope

- In scope: `cui-http-core/src/main/java/de/cuioss/http/forwarded/**` (all classes) **and**
  `doc/forwarded-header-resolution.adoc`. That AsciiDoc file is owned by this workstream alone —
  it is the resolver's public security guarantee, so it must be amended in lockstep with the code.
- Out of scope: the `security` package the resolver sanitizes through (WS-01); the benchmarks under
  `cui-http-benchmarking/src/main/java/de/cuioss/http/forwarded/benchmark/` (WS-06 owns BB-1);
  every other file under `doc/` (WS-05).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-05-forwarded-trust-boundary | staged | The three HIGH trust-model gaps: multi-header invisibility, XFF-shadows-Forwarded, leftmost-token selection |
| PLAN-06-forwarded-parser-strictness | staged | Parser-differential and validation gaps: silent element drops, unvalidated record, IP-literal laxity |
| PLAN-07-forwarded-diagnostics-and-doc-accuracy | staged | Operator-facing diagnostics, allocation, and the residual doc/Javadoc drift |

## Sequencing and Surface Notes

- PLAN-05 → PLAN-06 → PLAN-07. All three touch `ForwardedHeaderResolver.java`; strictly sequential
  within the workstream regardless of the epic's scope knob.
- PLAN-05's resolution may legitimately be a **narrowed guarantee** rather than a code change — the
  report's Top Priority #3 names both routes as acceptable. Whichever route is taken, the resolver's
  own Javadoc **and** `doc/forwarded-header-resolution.adoc` must both state it; a note in a review
  report is explicitly *not* a sufficient outcome.
- **Adjacency:** the resolver sanitizes via `createHeaderValuePipeline`, owned by WS-01/PLAN-01.
  If PLAN-01 lands a real HEADER_NAME token set or removes the no-op stages, PLAN-05..07 must
  re-verify FW-11 and DOC-4's premise at outline.
- **Do not touch** `ForwardedResolverBenchmark.java` or `benchmark-logging.properties` — BB-1 owns
  the benchmark's logging problem and lives in WS-06.
