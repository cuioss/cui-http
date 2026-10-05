envelope_version=1
sender_type=plan
sender_id=plan-12-javadoc-samples-and-api-prose
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-05T07:35:31Z

component=plan-marshall:phase-6-finalize
category=bug

# pre-push-quality-gate always reports module-tests DEGRADED on Maven projects

## Source

- Plan: plan-12-javadoc-samples-and-api-prose (PR #260)
- Signal: observed by the orchestrator during finalize (not in this plan's findings store;
  provenance is the orchestrator's run observation)

## What happened

The pre-push quality gate reported its module-tests arm as DEGRADED, but `verify -Ppre-commit`
had run the full cui-http test suite and passed. Two causes combine:

1. `derive_gate_bundles` derives the test scope from marketplace bundles. A Maven project has no
   bundles, so the derivation always returns zero.
2. The `plan-marshall:build-maven` skill has no `resolve-test-scope` verb that the gate could use
   to get a module-level scope from the Maven reactor.

## Why it matters

A permanent DEGRADED on a check that actually ran and passed teaches operators to ignore the
DEGRADED verdict. Then a real degradation goes unnoticed. The gate's evidence is also wrong: it says
module tests were not established, but they were.

## Suggested correction

Derive the gate scope from the active build system instead of assuming marketplace bundles. Either
add a `resolve-test-scope` verb to build-maven (and the other build-* skills) or map the changed
files to reactor modules through the architecture inventory (`architecture which-module`). Also,
when the configured pre-commit or verify command already ran the full suite, record module-tests as
covered by that run rather than DEGRADED.
