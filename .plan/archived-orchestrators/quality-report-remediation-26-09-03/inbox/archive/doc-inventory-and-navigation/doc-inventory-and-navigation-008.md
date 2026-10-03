envelope_version=1
sender_type=plan
sender_id=doc-inventory-and-navigation
epic=quality-report-remediation
kind=landing
created=2026-08-27T07:33:48Z

# Landing: PLAN-15 — Documentation Inventory and Navigation

plan_id: doc-inventory-and-navigation
epic: quality-report-remediation
workstream: WS-05
outcome: merged
pr: https://github.com/cuioss/cui-http/pull/161
merged_sha_range: 534d111..882cc7b (squash-merged via merge queue)

## Deliverables

All six landed.

1. **Test inventories retired.** `testing.adoc`'s per-class tree (with its stale `(26)`) replaced by a
   package-level map; `compliance-traceability.adoc`'s Test-Coverage-Summary counts replaced with
   count-free statements naming the packages. The report's preferred route — stop hand-maintaining —
   was taken rather than re-typing today's numbers.
2. **`test-framework-structure.adoc` remapped** to package-level navigation.
3. **Navigation gaps closed.** `doc/http-result-pattern.adoc` and `doc/LogMessages.adoc` now have
   inbound links; `configuration.adoc` and `specification/compliance-traceability.adoc` added to the
   security README's Document Index.
4. **Status notes added** for HTTP-1, HTTP-2 and HTTP-14.
5. **Pipeline decision matrix extended** to all five concrete pipelines; the misdirected
   "see the note below" replaced with an anchored xref.
6. **`CLAUDE.md` architecture refreshed** — 5 pipelines, 8 stages, and the previously-omitted
   `client.adapter` / `converter` / `result` / `forwarded` packages.

## Counts corrected against HEAD (not against the report)

- `tests/` holds **27** classes, not 26.
- Pipeline test classes: **7** at HEAD — the report said 6, which was already stale.
- `compliance-traceability.adoc`'s "14 Databases … plus 4 legitimate-pattern" over-counted: HEAD has
  **13 concrete** (10 attack + 3 legitimate-pattern). The fourth counted item is the
  `LegitimatePatternDatabase` *interface*.
- `module-info.java` has **3** non-static `requires`; HTTP-2's "only two runtime modules" was wrong
  (`java.net.http` was missing entirely).

## Defects found beyond the report's named sites

CodeRabbit's review surfaced three real defects the plan's own gates missed:

- **`functional-requirements.adoc:195` cited RFC 7230** for `PARAMETER_NAME` token rules. Verified
  against `CharacterValidationConstants:263`: `PARAMETER_NAME` maps to `RFC3986_QUERY_CHARS`;
  `RFC7230_TOKEN_CHARS` is `HEADER_NAME`-only. **This is the same misattribution class DOC-7 named,
  at a second site the source report never listed** — evidence that the report's named sites were not
  the whole population.
- HTTP-1's body bullet contradicted the "not provided" NOTE the plan had just added.
- `compliance-traceability.adoc:689` re-hardcoded the database inventory two paragraphs after the
  same file already cross-referenced it.

## Scope boundaries honoured

- `cui-http-core/src/main/java/de/cuioss/http/security/**` was never opened. DOC-7's tasks-document
  Javadoc references remain PLAN-04 (WS-01) territory; the exclusion is now recorded in the outline's
  Approach section so a later reader cannot mistake the gap for an oversight.
- `doc/forwarded-header-resolution.adoc` (WS-02) untouched.
- No test class edited.

## Residual work for the epic

- **`compliance-traceability.adoc` still carries per-section test-CASE counts** ("200+ test cases",
  "500+ test cases", "64+ patterns") and line-number references like "(line 209)". Same staleness
  class as DOC-8; task 1 scoped to test-CLASS counts, so these were deliberately left rather than
  widening the diff. A reasonable follow-up.
- **No link checker exists in this repository.** `pom.xml` configures no AsciiDoc processing and the
  Maven workflow skips documentation-only changes. ADR-0005 names adding one as the follow-on its
  decision enables. Until then, a broken xref is decidable but undetected.

## ADRs recorded

- **ADR-0004** — documentation inventories point at package-level source trees, not per-class
  enumerations (Proposed).
- **ADR-0005** — documentation cross-references use named anchors, not positional prose (Proposed).

Both were initially written asserting that link validation already runs; that claim was false and was
corrected in `882cc7b` after CodeRabbit caught it. Recorded as a candidate lesson — it is the
PLAN-14 overclaim hazard recurring inside the artifact written to prevent it.

## Verification

CI green · Sonar 0 new-code issues (confirmed, not unknown) · pre-submission self-review clean ·
simplify 0 net edits · security audit 0 findings, with both corrected claims independently
corroborated against the enforcement code · 9 review findings all resolved.

No build ran at any point: `build-decision` returned `not_necessary` for the whole footprint
(no `build_map` glob touched), and the freshness gate returned `fresh` on that same basis.

## Caveat

`pr-agent` finished `participated_stale` — its clean review covers `09284fd`, not the merged tree.
It does not auto-review on push and `re_review_on_loopback` is `false` for this plan. CodeRabbit did
review the final tree.
