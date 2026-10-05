envelope_version=1
sender_type=plan
sender_id=cookie-validation-completeness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T15:10:42Z

## Candidate lesson: two finalize gates are structurally inert on Maven repos — confirmed by observation, not prediction

**Components:** `plan-marshall:phase-6-finalize`, `plan-marshall:build-maven`
**Category:** improvement / known-inert tooling
**Observed:** plan `cookie-validation-completeness`, this run. Both were *predicted* in the epic
brief and both *reproduced exactly*, so this is now confirmed behaviour rather than a hypothesis.

### Gate 1 — per-bundle quality gate never runs: `derive_gate_bundles` returns 0 bundles

`derive_gate_bundles` returned **0 bundles with all 10 footprint paths unresolved**. The
per-bundle quality-gate arm therefore never executed. The derivation maps a plan's realized
footprint onto marketplace *bundle* identities; a Maven repository's footprint is
`cui-http-core/src/main/java/...`, which resolves to no bundle, so every path is unresolved and
the derived set is empty.

The failure mode is the dangerous one: the gate reports a **clean zero** (no bundles to gate)
that is indistinguishable from "gated and found nothing". The arm silently contributes no
coverage on every non-marketplace repository.

### Gate 2 — module-tests divergence gate cannot run at all: no `resolve-test-scope` verb

`plan-marshall:build-maven` exposes **no `resolve-test-scope` verb**. The scoped-vs-whole-tree
module-tests divergence gate depends on that verb to compute the scoped test set, so on a Maven
project the gate is not merely empty — it is unreachable.

### Why this is worth recording

Both arms are advertised as part of the finalize quality posture. On a Maven repository the
posture is quietly narrower than advertised, and nothing in the run's output says so. The
run's green finalize therefore overstates the coverage actually achieved.

### Suggested directions (for orchestrator judgement)

- **Make the zero speak.** `derive_gate_bundles` should distinguish "no bundles because the
  footprint is not bundle-shaped" from "no bundles because nothing was touched", and the
  finalize step should surface the former as an explicit not-applicable rather than a pass.
  This is the same *which-zero-is-this* discipline already applied across the inbox/findings
  surfaces.
- **Either implement `resolve-test-scope` on `build-maven`, or have the divergence gate declare
  itself unavailable** for build systems that do not provide the verb — again, an explicit
  not-applicable rather than a silent skip.

### Scope note

Only Maven was observed here. The same question is open for `build-gradle`, `build-npm`, and
`build-pyproject`; worth checking whether the divergence gate has a `resolve-test-scope`
implementor on any build system at all.
