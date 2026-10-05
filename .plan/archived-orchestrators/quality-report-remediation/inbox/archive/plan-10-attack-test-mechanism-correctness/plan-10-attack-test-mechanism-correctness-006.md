envelope_version=1
sender_type=plan
sender_id=plan-10-attack-test-mechanism-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-03T21:23:00Z

# Candidate lesson: pre-push-quality-gate degrades in this Maven project (no default-scope module-tests canonical, no bundle derivation)

**Source signal**: work log WARNINGs 3c0ef7 and d80efa (pre-push-quality-gate, 2026-10-03T12:06-12:07Z).
**Component**: plan-marshall:phase-6-finalize (pre-push-quality-gate) plus cui-http project build configuration (marshal.json / architecture canonicals).

## What happened

1. "No module-tests canonical resolves at default scope in this project" - the dedicated module-tests divergence arm did not run, so the scoped-green / whole-tree-red divergence class (PLAN-08) was un-gated for this push (honest degradation). The whole-tree `verify -Ppre-commit` still ran 12512 tests green.
2. "Footprint paths matched a build_map glob but resolved to no bundle" - all 97 changed .java files under cui-http-core/src/test/java; bundle derivation targets marketplace bundles, which this Maven project does not have.

## Candidate rule

For cui-http: register a default-scope module-tests canonical (e.g. `test -pl cui-http-core -am`) so the divergence arm can run, or record explicitly that the whole-tree quality gate supersedes it. For the marketplace: the bundle-derivation footprint check should not warn on non-marketplace projects.

## Classification hint

Mixed: project config gap (epic-local) plus a marketplace noise warning.
