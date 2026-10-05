<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug quality-report-remediation`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug quality-report-remediation`, and `git add` the result. -->

# Queue view: cui-http Quality Report Remediation

## START HERE

**Resume anchor**: ALL PLANS TERMINAL as of 2026-10-05: 14 shipped (last: PLAN-13, #262, 4a9c30e), 2 superseded; all 7 workstreams closed; inbox empty. NEXT ACTION: operator decision - stage follow-up plans for the unowned Open Defects, or close the epic (/plan-orchestrator close). Unowned Open Defects in epic.md: (1) low-byte CRLF homograph accepted by URLParameterValidationPipeline - unverified security lead, likely a WS-01 plan; (2) no URL-scheme rejection on the path pipeline - by design?; (3) test-side residue incl. 23 test files citing 'HTTP verification specification'; (4) Javadoc drift - adapter request-body prose overstatement, CharacterValidationStage BODY ASCII-only claim, ForwardedHeaderResolver/RfcForwardedParser comments; (5) release notes must call out the removed generators (PathTraversalURLGenerator, DoubleEncodingAttackGenerator). Plan-marshall findings route to plan-marshall epic truthful-signals (020 messages sent as cui-http-quality-report-remediation). Gate note: plugin 0.1.1842 corpus cross-check counts terminal rows - any further emit needs an operator override. Standing: main has a REQUIRED merge queue; PR #224 still OPEN; lessons backlog 40+ (/plan-orchestrator lessons).
**Phase**: orchestrating
**Queue** (staged, in order):
- (empty)
- PLAN-01 (WS-01-security-validation-core) — plan=decoding-normalisation-hardening — PR #210 — landing=landings/PLAN-01.md — status: shipped
- PLAN-02 (WS-01-security-validation-core) — plan=character-set-and-control-characters — PR #217 — landing=landings/PLAN-02.md — status: shipped
- PLAN-03 (WS-01-security-validation-core) — plan=exception-sanitisation-and-config-hygiene — PR #222 — landing=landings/PLAN-03.md — status: shipped
- PLAN-15 (WS-01-security-validation-core) — plan=configuration-surface-hygiene — PR #229 — landing=landings/PLAN-15.md — status: shipped
- PLAN-04 (WS-02-contenttype-cookie-collection) — plan=contenttype-pipeline-enforcement — PR #227 — landing=landings/PLAN-04.md — status: shipped
- PLAN-05 (WS-02-contenttype-cookie-collection) — plan=cookie-validation-completeness — PR #231 — landing=landings/PLAN-05.md — status: shipped
- PLAN-06 (WS-03-forwarded-trust-model) — plan=plan-06-forwarded-trust-model — PR #214 — landing=landings/PLAN-06.md — status: shipped
- PLAN-07 (WS-03-forwarded-trust-model) — plan=plan-07-forwarded-parsing-and-validation — PR #237 — landing=landings/PLAN-07.md — status: shipped
- PLAN-08 (WS-04-http-client) — plan=etag-cache-principal-isolation — PR #240 — landing=landings/PLAN-08.md — status: shipped
- PLAN-09 (WS-04-http-client) — status: superseded
- PLAN-10 (WS-05-test-framework-quality) — plan=plan-10-attack-test-mechanism-correctness — PR #256 — landing=landings/PLAN-10.md — status: shipped
- PLAN-11 (WS-05-test-framework-quality) — status: superseded
- PLAN-12 (WS-06-documentation-set) — plan=plan-12-javadoc-samples-and-api-prose — PR #260 — landing=landings/PLAN-12.md — status: shipped
- PLAN-13 (WS-06-documentation-set) — plan=plan-13-asciidoc-specs-requirements-adrs — PR #262 — landing=landings/PLAN-13.md — status: shipped
- PLAN-14 (WS-07-build-ci-benchmarking) — plan=build-artifacts-and-ci-safety — PR #208 — landing=landings/PLAN-14.md — status: shipped
- PLAN-16 (WS-01-security-validation-core) — plan=parameter-value-linebreak-carve-out — PR #239 — landing=landings/PLAN-16.md — status: shipped

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) |
|---|------|------------|--------|--------------------|
| — | (empty) | — | — | — |
