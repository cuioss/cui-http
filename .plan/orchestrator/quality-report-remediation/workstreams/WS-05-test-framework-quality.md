# WS-05: Security Test Framework Quality

epic: quality-report-remediation

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-05-test-framework-quality.md` and is tracked in the
> epic `status.json` `workstreams[]` field. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

This workstream owns the security test framework — the attack databases, the generators, and
the `tests/` integration layer. The review's verdict is that coverage is high (95.0 % line,
91.8 % branch) while several test classes do not test what their names say: URL-shaped header
injection, request smuggling and URL-length payloads are rejected on a literal `?` before the
mechanism under test ever runs; seven methods accept either outcome and so cannot fail; eleven
helpers assert "any of 6–10 of the 24 failure types"; and most attack generators wrap fixed
literal lists that are randomly *sampled* with a fresh per-run seed rather than iterated, so
entries are silently skipped on most runs. It closes when every security test has an expected
verdict it can fail against, and every fixed-list generator reaches every entry.

## Scope

- In scope: `cui-http-core/src/test/java/de/cuioss/http/security/database`,
  `.../security/generators`, `.../security/tests`, the shared test helpers those use, and the
  generator contract tests. Also the missing generator usage in the stage, client and forwarded
  test suites where the finding is "this suite uses no cui-test-generator at all".
- Out of scope: all production code (WS-01 through WS-04); `doc/test-framework-structure.adoc`
  and `doc/test-generators-readme.adoc` prose and the ADR statements about the framework, which
  are WS-06's — this workstream changes the test *code* and WS-06 reconciles the documents to
  it.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-10-attack-test-mechanism-correctness | staged | Point payloads at the surfaces that receive them; give every test an expected verdict; assert exact failure types |
| PLAN-11-generator-determinism-and-coverage | staged | Iterate fixed lists deterministically, widen the valid generators, retire overlaps, add the missing contract tests |

## Sequencing and Surface Notes

- **This whole workstream is sequenced after WS-01 and WS-02 land.** Its tests assert the exact
  behaviour those workstreams are about to change: a test given a real expected verdict today
  would have to be rewritten the moment `DecodingStage` stops accepting `%25%32%66`. Running it
  first would guarantee rework.
- PLAN-10 and PLAN-11 are surface-disjoint at directory granularity — PLAN-10 owns
  `security/tests`, PLAN-11 owns `security/generators` and `security/database` — and MAY run
  concurrently, subject to the epic's `parallelization_scope` of 2.
- PLAN-11 additionally touches the stage, client and forwarded test suites to introduce
  generator usage. Those files are owned by WS-01, WS-03 and WS-04 while their plans are in
  flight, so PLAN-11 must not run concurrently with any of them.
- Neither plan changes production code. If either discovers that a test cannot be given a real
  expected verdict because the production behaviour is itself wrong, it files an inbox message
  rather than fixing the production code — that is a WS-01..WS-04 deliverable.
