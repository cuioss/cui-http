<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug quality-report-remediation`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug quality-report-remediation`, and `git add` the result. -->

# Queue view: cui-http Quality Report Remediation

## START HERE

**Resume anchor**: N=2, R=0. Release 3.1 CUT (PR #241, main now at 49242ab). PLAN-10 and PLAN-12 emitted, awaiting operator-confirmed launch (auto_emit=false) - both confirmed disjoint from each other and from PLAN-13. PLAN-13 remains staged, next in emission order. PLAN-09/PLAN-11 remain parked (merged away). Deliver PLAN-10's AND PLAN-12's full briefs AT launch. Known post-3.1 documentation gaps (not release blockers, flagged to operator): adapter/package-info.java's Javadoc sample still does not compile (PLAN-12's own fix target); compliance-traceability.adoc still marks OWASP A03/A04/A05 VERIFIED against unimplemented protections (PLAN-13's own fix target). Lesson 2026-09-15-19-003 (argparse router-vs-subcommand flag position) has recurred across 3 plans and 8 manage-*/router notations - needs a structural fix. ADR high-water is 0023; index is SIX divergences behind (PLAN-13 4g/4b owns it). Standing risks unchanged: main has a REQUIRED merge queue; PR #224 still OPEN; lessons backlog 40+ pending, blocking on /plan-orchestrator lessons.
**Phase**: orchestrating
**Parked**:
- PLAN-09 (WS-04-http-client)
- PLAN-11 (WS-05-test-framework-quality)
**Queue** (staged, in order):
1. PLAN-10 (WS-05-test-framework-quality)
2. PLAN-12 (WS-06-documentation-set)
3. PLAN-13 (WS-06-documentation-set)
- PLAN-01 (WS-01-security-validation-core) — plan=decoding-normalisation-hardening — PR #210 — landing=landings/PLAN-01.md — status: shipped
- PLAN-02 (WS-01-security-validation-core) — plan=character-set-and-control-characters — PR #217 — landing=landings/PLAN-02.md — status: shipped
- PLAN-03 (WS-01-security-validation-core) — plan=exception-sanitisation-and-config-hygiene — PR #222 — landing=landings/PLAN-03.md — status: shipped
- PLAN-15 (WS-01-security-validation-core) — plan=configuration-surface-hygiene — PR #229 — landing=landings/PLAN-15.md — status: shipped
- PLAN-04 (WS-02-contenttype-cookie-collection) — plan=contenttype-pipeline-enforcement — PR #227 — landing=landings/PLAN-04.md — status: shipped
- PLAN-05 (WS-02-contenttype-cookie-collection) — plan=cookie-validation-completeness — PR #231 — landing=landings/PLAN-05.md — status: shipped
- PLAN-06 (WS-03-forwarded-trust-model) — plan=plan-06-forwarded-trust-model — PR #214 — landing=landings/PLAN-06.md — status: shipped
- PLAN-07 (WS-03-forwarded-trust-model) — plan=plan-07-forwarded-parsing-and-validation — PR #237 — landing=landings/PLAN-07.md — status: shipped
- PLAN-08 (WS-04-http-client) — plan=etag-cache-principal-isolation — PR #240 — landing=landings/PLAN-08.md — status: shipped
- PLAN-14 (WS-07-build-ci-benchmarking) — plan=build-artifacts-and-ci-safety — PR #208 — landing=landings/PLAN-14.md — status: shipped
- PLAN-16 (WS-01-security-validation-core) — plan=parameter-value-linebreak-carve-out — PR #239 — landing=landings/PLAN-16.md — status: shipped

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) |
|---|------|------------|--------|--------------------|
| 1 | PLAN-09 | WS-04-http-client | parked | (no expected surface section) |
| 2 | PLAN-10 | WS-05-test-framework-quality | staged | cui-http-core/src/test/java/de/cuioss/http/client/; cui-http-core/src/test/java/de/cuioss/http/forwarded/; cui-http-core/src/test/java/de/cuioss/http/security/database/; cui-http-core/src/test/java/de/cuioss/http/security/generators/; cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/UnicodeAttackGeneratorTest.java; cui-http-core/src/test/java/de/cuioss/http/security/generators/header/HttpHeaderInjectionAttackGenerator.java; cui-http-core/src/test/java/de/cuioss/http/security/generators/injection/ProtocolHandlerAttackGenerator.java; cui-http-core/src/test/java/de/cuioss/http/security/tests/; cui-http-core/src/test/java/de/cuioss/http/security/validation/ |
| 3 | PLAN-11 | WS-05-test-framework-quality | parked | (no expected surface section) |
| 4 | PLAN-12 | WS-06-documentation-set | staged | cui-http-core/src/main; cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java; cui-http-core/src/main/java/de/cuioss/http/client/converter/StringContentConverter.java; cui-http-core/src/main/java/de/cuioss/http/client/handler/SecureSSLContextProvider.java; cui-http-core/src/main/java/de/cuioss/http/client/handler/package-info.java; cui-http-core/src/main/java/de/cuioss/http/client/package-info.java; cui-http-core/src/main/java/de/cuioss/http/client/result/package-info.java; cui-http-core/src/main/java/de/cuioss/http/security/config/package-info.java; cui-http-core/src/main/java/de/cuioss/http/security/exceptions/UrlSecurityException.java; cui-http-core/src/main/java/de/cuioss/http/security/exceptions/package-info.java; cui-http-core/src/main/java/de/cuioss/http/security/monitoring/package-info.java; cui-http-core/src/main/java/de/cuioss/http/security/pipeline/HTTPHeaderValidationPipeline.java; cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLParameterValidationPipeline.java; cui-http-core/src/main/java/de/cuioss/http/security/pipeline/package-info.java; cui-http-core/src/main/java/de/cuioss/http/security/validation/package-info.java |
| 5 | PLAN-13 | WS-06-documentation-set | staged | .claude/skills/release/SKILL.md; .gitignore; CLAUDE.md; README.adoc; agents.md; cui-http-core/src/site/asciidoc/about.adoc; doc/; doc/adr/ |
