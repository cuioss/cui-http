envelope_version=1
sender_type=plan
sender_id=plan-13-asciidoc-specs-requirements-adrs
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-05T15:33:41Z

# Candidate lesson: invented build-maven verb resolve-test-scope (argparse rejection)

- Suggested component: plan-marshall:build-maven (caller side, pre-push-quality-gate)
- Suggested category: anti-pattern
- Signal source: script-failure cluster (1 occurrence)
- Evidence: work log cab0da at 10:57:16Z, `script_failure notation=plan-marshall:build-maven:maven exit_code=2 failure_kind=argparse_rejection detail='resolve-test-scope' is not a registered verb ... ['check-warnings', 'coverage-report', 'discover', 'parse', 'rewrite-log', 'run', 'run-config-key']`. Work log 366cb3 right after it: "No module-tests canonical resolves in this project ... divergence class (PLAN-08) is UN-GATED at finalize".

## What happened

During pre-push-quality-gate, the agent called `build-maven:maven resolve-test-scope`, which is not a verb that build-maven provides. The gate then degraded honestly and noted that no module-tests canonical resolves for this project, so the scoped-green versus whole-tree-red divergence check did not run.

## Why it matters

The invented verb shows a gap between pre-push-quality-gate's documented flow and what the Maven provider exposes. Either the workflow refers to a capability the Maven provider lacks, or the agent extrapolated a verb.

## Suggested fix

Check which verb pre-push-quality-gate documents for resolving the test scope on Maven. If none exists, document the Maven degradation path explicitly so agents do not guess a verb. Consider giving Maven a module-tests canonical so the divergence gate can run.
