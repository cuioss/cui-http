# WS-05: Documentation Truthfulness

epic: quality-report-remediation

## Charter

The documentation suite is materially more accurate than typical and unusually disciplined about
scoping its security claims — `LogMessages.adoc` and `configuration.adoc` match the code value for
value, and every cross-reference in the 22-file tree resolves. The residue is what this workstream
removes: a handful of overclaims that contradict the suite's own carefully-scoped statements
(a "✓ Complete" compliance matrix over a partial traceability matrix, a "configurable pattern
database" that does not exist), inventory listings that have drifted behind the code, and two
navigation gaps. It closes when all 14 DOC findings are resolved.

## Scope

- In scope: the `doc/` tree **except** `doc/forwarded-header-resolution.adoc`, plus `README.adoc`,
  `cui-http-core/src/site/asciidoc/about.adoc`, `CLAUDE.md`, and `agents.md`.
- Out of scope: `doc/forwarded-header-resolution.adoc` — owned exclusively by WS-02, because it is
  the resolver's public security guarantee and must move in lockstep with the resolver code.
  Javadoc inside main sources (WS-01/02/03 own their own Javadoc); test sources (WS-04).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-14-doc-overclaim-correction | staged | Compliance matrix, OWASP best-practices overclaims, inert knobs, phantom log levels, phantom CVE refs |
| PLAN-15-doc-inventory-and-navigation | staged | Stale test inventories, orphaned documents, index omissions, stale path prefixes, CLAUDE.md drift |

## Sequencing and Surface Notes

- PLAN-14 → PLAN-15. Both edit `doc/http-security/**`; sequenced, never paired.
- **Cross-workstream:** DOC-4 asserts three forwarded-sanitization config knobs are inert. That
  premise depends on what `HTTPHeaderValidationPipeline` does — which PLAN-01 may change. PLAN-14
  must re-verify DOC-4 against HEAD at outline, and if PLAN-01 has landed a `DecodingStage` into
  the header pipeline the finding is refuted and must be re-scoped rather than applied.
- **Cross-workstream:** DOC-11 (CLAUDE.md architecture is a stale subset) is a *repository
  instruction file*. Changing it changes the guidance every future agent reads, so PLAN-15 amends
  it factually and does not restructure it.
- TQ-9 and DOC-8 are the same finding (stale test inventories). Owned by PLAN-15; PLAN-13 names it
  as out of scope.
