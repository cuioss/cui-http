<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug quality-report-remediation`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug quality-report-remediation`, and `git add` the result. -->

# Queue view: cui-http Quality Report Remediation

## START HERE

**Resume anchor**: N=2, R=0. PLAN-17-post-landing-residue STAGED and EMITTED 2026-10-05 (operator-requested; emit under the standing gate override), awaiting launch - either /plan-marshall or a plain Claude Code session (spec carries both commands). It absorbs every remaining unowned Open Defect except the release-notes item (removed generators PathTraversalURLGenerator/DoubleEncodingAttackGenerator must be named in the next release notes). A plain session files no inbox message: on its PR merge, analyze from the PR (paste or on-disk mode). After PLAN-17 ships: close the epic (/plan-orchestrator close). 14 shipped, 2 superseded, 1 staged. Plan-marshall findings route to plan-marshall truthful-signals (020 sent). Standing: main has a REQUIRED merge queue; PR #224 still OPEN; lessons backlog 40+.
**Phase**: orchestrating
**Queue** (staged, in order):
1. PLAN-17 (WS-01-security-validation-core)
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
| 1 | PLAN-17 | WS-01-security-validation-core | staged | cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java; cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java; cui-http-core/src/main/java/de/cuioss/http/forwarded/RfcForwardedParser.java; cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLParameterNameValidationPipeline.java; cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLParameterValidationPipeline.java; cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLPathValidationPipeline.java; cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationStage.java; cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java; cui-http-core/src/test/java/de/cuioss/http/security/database/IDNAttackDatabase.java; cui-http-core/src/test/java/de/cuioss/http/security/generators/; cui-http-core/src/test/java/de/cuioss/http/security/pipeline/; cui-http-core/src/test/java/de/cuioss/http/security/tests/; doc/adr/ |
