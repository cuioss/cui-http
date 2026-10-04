<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug quality-report-remediation`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug quality-report-remediation`, and `git add` the result. -->

# Queue view: cui-http Quality Report Remediation

## START HERE

**Resume anchor**: N=2, R=0. PLAN-10 SHIPPED (#256, f81a554, drained 2026-10-04; WS-05 closed). PLAN-12 and PLAN-13 EMITTED 2026-10-04 together under a recorded operator override of the disjointness gate, awaiting operator-confirmed launch (auto_emit=false; rows still staged) - deliver both full briefs AT launch. PLAN-13 brief must add: re-read doc/http-security/specification/testing.adoc (PLAN-10 edited it, Open Defect) and document the removal of PathTraversalURLGenerator/DoubleEncodingAttackGenerator from the generators artifact. These are the epic's last two staged plans. Unowned Open Defects from the PLAN-10 landing: low-byte CRLF homograph accepted by URLParameterValidationPipeline (verify, likely a new WS-01 plan), no scheme rejection on URL path (decide by-design or not), test-side residue. Gate note: on plugin 0.1.1842 corpus cross-check counts terminal rows as candidates - every emit needs an operator override. On each landing: analyze (inbox drain), then next. 9 lessons promoted 2026-10-04-06-001..009 (route via /plan-orchestrator lessons; backlog 40+). ADR high-water 0023, index five rows behind (PLAN-13 4g). Standing: main has a REQUIRED merge queue; PR #224 still OPEN.
**Phase**: orchestrating
**Queue** (staged, in order):
1. PLAN-12 (WS-06-documentation-set)
2. PLAN-13 (WS-06-documentation-set)
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
- PLAN-14 (WS-07-build-ci-benchmarking) — plan=build-artifacts-and-ci-safety — PR #208 — landing=landings/PLAN-14.md — status: shipped
- PLAN-16 (WS-01-security-validation-core) — plan=parameter-value-linebreak-carve-out — PR #239 — landing=landings/PLAN-16.md — status: shipped

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) |
|---|------|------------|--------|--------------------|
| 1 | PLAN-12 | WS-06-documentation-set | staged | cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java; cui-http-core/src/main/java/de/cuioss/http/client/converter/StringContentConverter.java; cui-http-core/src/main/java/de/cuioss/http/client/handler/SecureSSLContextProvider.java; cui-http-core/src/main/java/de/cuioss/http/client/handler/package-info.java; cui-http-core/src/main/java/de/cuioss/http/client/package-info.java; cui-http-core/src/main/java/de/cuioss/http/client/result/package-info.java; cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfiguration.java; cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfigurationBuilder.java; cui-http-core/src/main/java/de/cuioss/http/security/config/package-info.java; cui-http-core/src/main/java/de/cuioss/http/security/core/HttpSecurityValidator.java; cui-http-core/src/main/java/de/cuioss/http/security/core/UrlSecurityFailureType.java; cui-http-core/src/main/java/de/cuioss/http/security/core/ValidationType.java; cui-http-core/src/main/java/de/cuioss/http/security/data/Cookie.java; cui-http-core/src/main/java/de/cuioss/http/security/data/HTTPBody.java; cui-http-core/src/main/java/de/cuioss/http/security/data/URLParameter.java; cui-http-core/src/main/java/de/cuioss/http/security/exceptions/UrlSecurityException.java; cui-http-core/src/main/java/de/cuioss/http/security/exceptions/package-info.java; cui-http-core/src/main/java/de/cuioss/http/security/monitoring/SecurityEventCounter.java; cui-http-core/src/main/java/de/cuioss/http/security/monitoring/package-info.java; cui-http-core/src/main/java/de/cuioss/http/security/pipeline/HTTPHeaderValidationPipeline.java; cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLParameterValidationPipeline.java; cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLPathValidationPipeline.java; cui-http-core/src/main/java/de/cuioss/http/security/pipeline/package-info.java; cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationConstants.java; cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java; cui-http-core/src/main/java/de/cuioss/http/security/validation/LengthValidationStage.java; cui-http-core/src/main/java/de/cuioss/http/security/validation/NormalizationStage.java; cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java; cui-http-core/src/main/java/de/cuioss/http/security/validation/package-info.java |
| 2 | PLAN-13 | WS-06-documentation-set | staged | .claude/skills/release/SKILL.md; .gitignore; CLAUDE.md; README.adoc; agents.md; cui-http-core/src/site/asciidoc/about.adoc; doc/; doc/adr/ |
