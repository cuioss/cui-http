# WS-04: Test Suite Integrity

epic: quality-report-remediation

## Charter

The stage- and pipeline-level tests are exemplary — specific failure-type assertions, exact detail
messages, at-limit and one-over boundaries, and dedicated false-positive databases that assert a
zero event count. The weakness is concentrated and self-reinforcing: 17 of 20 generator contract
tests assert nothing beyond non-null, *and* a separate pass over all 34 generator implementations
found that the rest emit values not matching the attack they advertise — two never produce their
advertised attack at all. The no-op tests are exactly why those bugs went uncaught. This workstream
makes the generator tests real, then fixes what they now catch, and closes when all 23 TQ findings
are resolved.

## Scope

- In scope: `cui-http-core/src/test/java/**` — generator implementations and their contract tests,
  the attack databases, pipeline/stage test classes, and the integration tests.
- Out of scope: all main source (WS-01/02/03) — a test change that requires a production fix is
  escalated, not absorbed; `doc/test-framework-structure.adoc` and `doc/http-security/specification/testing.adoc`,
  which are WS-05's (TQ-9 is the same finding as DOC-8 and is owned by PLAN-15).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-11-generator-contract-test-semantics | staged | The TQ HIGH: give the 17 no-op contract tests real semantic assertions |
| PLAN-12-generator-implementation-correctness | staged | Fix the generators that never emit their advertised attack, plus the mislabeled branches |
| PLAN-13-attack-database-and-assertion-quality | staged | Mislabeled CRS entries, the flaky timing assertion, weak assertions, coverage gaps |

## Sequencing and Surface Notes

- **PLAN-11 MUST land before PLAN-12.** PLAN-11 installs the assertions; PLAN-12 fixes the bugs
  those assertions are what catch. Reversing the order fixes the bugs with no regression guard in
  place — and the review's own "highest-value fixes" list makes the same ordering argument.
- PLAN-13 may run after PLAN-12; it touches the databases and pipeline tests, not the generators.
- **Whole-workstream surface note:** WS-04 touches only `src/test/`, so it is disjoint from
  WS-01/02/03/05/06 by construction. It is nonetheless *semantically* coupled: if PLAN-01 or PLAN-02
  changes what a pipeline rejects, the attack-database expectations in PLAN-13 change with it.
  Sequence WS-04 after the WS-01 code plans land, or re-verify expectations at outline.
- TQ-13 and TQ-14 record verified-correct observations with no action required. They are named in
  PLAN-13 as explicit no-action so their absence is not read as an oversight.
