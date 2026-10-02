envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T15:24:38Z

component=cui-http-test-generators
category=improvement
source_plan=generator-contract-test-semantics
source_signal=pr-comment
source_finding=000864
pr=164

# Truncation helper existed but four failure messages bypassed it

`GeneratorContractAssertions` defined a `preview(String)` helper
precisely to bound long generated payloads in diagnostics, yet four
assertion failure messages (`assertContainsAny`, `assertPipelineAccepts`
x2, `assertPipelineRejects`) still interpolated the raw `value`. A
failed length-attack contract therefore floods the test report with the
attack payload and buries the actual failure.

## Rule

When a class introduces a diagnostic-formatting helper, every
diagnostic in that class must route through it. A partially-adopted
helper is worse than none: it reads as adopted, so nobody re-checks the
sites that were missed. Treat "helper exists, N call sites bypass it"
as a defect, not a style nit.

## Impact

Generic, class-wide helpers need a mechanical sweep at introduction
time (and at review time), otherwise adoption drifts silently.
