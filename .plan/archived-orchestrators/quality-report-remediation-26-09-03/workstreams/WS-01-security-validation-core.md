# WS-01: Security Validation Core

epic: quality-report-remediation

## Charter

The security-validation pipelines are the library's reason for existing, and they carry the epic's
central theme in its sharpest form: several stages are wired into pipelines where they do nothing,
and several Javadoc contracts promise enforcement the code does not perform. This workstream closes
that gap across `de.cuioss.http.security` — either by making the enforcement real or by narrowing
the stated guarantee — and leaves no component advertising more validation than it performs.
It closes when all 29 SV findings are resolved.

## Scope

- In scope: `cui-http-core/src/main/java/de/cuioss/http/security/**` — the `validation`, `pipeline`,
  `config`, `data`, `core`, `monitoring`, and `exceptions` packages, including their
  `package-info.java` files and all Javadoc within them. `module-info.java` where SV-29 touches it.
- Out of scope: the `forwarded` package (WS-02) even though it consumes `createHeaderValuePipeline`;
  the `client` package (WS-03); everything under `src/test/` (WS-04); the `doc/` tree (WS-05).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-01-header-and-content-type-enforcement | staged | Both SV HIGH findings: header-name token set, no-op stages, content-type/allow-block-list fail-open |
| PLAN-02-post-decode-character-enforcement | staged | The percent-encoding bypass class (SV-3/SV-5), the strict-preset weakening (SV-4), pattern observability |
| PLAN-03-security-api-contract-hardening | staged | Behavioural minors: quoted charsets, counter accuracy, equals/hashCode, BODY limit cap |
| PLAN-04-security-javadoc-accuracy | staged | The Javadoc/example accuracy sweep — non-compiling examples, wrong stage orders, missing docs |

## Sequencing and Surface Notes

- PLAN-01 → PLAN-02 → PLAN-03 → PLAN-04, strictly. PLAN-01 and PLAN-02 both touch
  `CharacterValidationStage`, `CharacterValidationConstants`, and `PipelineFactory`; their surfaces
  overlap and they can never be paired concurrently.
- PLAN-04 is deliberately LAST. It documents behaviour that PLAN-01..03 change; running it earlier
  would produce Javadoc that is stale on the day it lands.
- **Cross-workstream overlap:** SV-15 also surfaces as CL-16 (WS-03) and BB-5 (WS-06). It is owned
  by PLAN-04 alone; PLAN-10 and PLAN-16 name it as out-of-scope-here.
- **Adjacency:** WS-02's forwarded resolver calls into `HTTPHeaderValidationPipeline`, which PLAN-01
  changes. DOC-4 (WS-05) documents which config knobs reach that pipeline. If PLAN-01 changes what
  HEADER_VALUE validation does, PLAN-05 and PLAN-14 must re-read that surface at outline.
