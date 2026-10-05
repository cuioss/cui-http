envelope_version=1
sender_type=plan
sender_id=contenttype-pipeline-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T10:32:36Z

# Candidate lesson: build-maven exposes no resolve-test-scope verb, so the module-tests divergence gate could not run at all

## Observation

The module-tests divergence gate needs a build-system verb that resolves which test scope a
given change footprint maps to. `plan-marshall:build-maven` exposes no `resolve-test-scope`
verb, so the gate had no way to obtain that mapping and did not run in this project.

## Why it matters

The failure is at the CAPABILITY layer, not the verdict layer. The gate did not evaluate and
return "no divergence" — it was never able to evaluate. From the run's outcome those two are
again indistinguishable, and the second one is the one that happened.

This is the same shape as the `derive_gate_bundles` zero reported separately: a check that
cannot run in this project contributes nothing to the gate, permanently, and nothing on the
run says so. Two independent instances in one plan suggests the pattern is worth a rule
rather than two point fixes.

## Proposed direction (for the orchestrator to judge and place)

Two separable pieces:

1. **The capability gap** — implement `resolve-test-scope` on `build-maven` (mapping a
   changed-path set to the owning Maven modules and their test scope, e.g. via `-pl … -am`),
   so the divergence gate has the input it needs in Maven projects.
2. **The reporting gap** — a gate whose required build-system verb is unavailable should
   report `not_available` naming the missing verb and the build system, and be excluded from
   the gate's verdict, rather than silently contributing nothing. (The orchestrator's own
   `cleanup restart-check` already models this: its `registry_parity` row reports
   `not_available`, names the spec that owns the surface, and is excluded from the floor —
   so an unowned surface cannot masquerade as a passing one.)

The generalisable claim: **a gate that depends on a build-system verb the project's build
system does not implement must declare itself unavailable; silently not running is
indistinguishable from running clean.**

## Provenance

- Plan: `contenttype-pipeline-enforcement` (Maven project `cui-http`)
- Component: `plan-marshall:build-maven` (missing verb) + the module-tests divergence gate
  (missing unavailability report).
