# PLAN-15: Documentation Inventory and Navigation

epic: quality-report-remediation
workstream: WS-05

## Objective

Fix the documentation that has drifted behind the code rather than overclaimed about it. Four
documents hand-maintain exhaustive test inventories and counts that no longer match the tree — a
"(26)" that is 27, "Pipeline Tests: 4" that is 6, "Validation Stage Tests: 7" that is 9, "4
legitimate-pattern databases" of which only 3 are concrete. Two documents have zero inbound
references and cannot be discovered by a reader navigating from the README, while the security
README's own index omits two documents despite instructing that it be updated when documents are
added. `CLAUDE.md`'s architecture section is a stale subset of the API — and because `agents.md`
defers to it as the single source of truth for architecture, the gap propagates to every agent that
reads it. Alongside these: unimplemented features missing their status notes, stale tree-root path
prefixes, and an overdue review date.

## Deliverables

1. **Refresh or stop hand-maintaining the test inventories** — `testing.adoc`'s "(26)" and its
   omission of `ProtocolHandlerAttackDatabaseTest` (the directory has 27), the same stale "26" in
   `pipeline-architecture-standards.adoc`, `testing.adoc`'s 4-of-6 pipeline test classes, and
   `compliance-traceability.adoc`'s Test-Coverage-Summary counts. The report recommends generating
   these from the tree, or better, ceasing to hand-maintain exhaustive counts in prose — take that
   recommendation seriously rather than re-typing today's numbers. (DOC-8, and TQ-9 which reports
   the same drift from the test-review side)
2. **Bring `doc/test-framework-structure.adoc` up to date** — the report calls it the most stale
   document in the tree; its database, pipeline, validation-stage, attack and cookie-generator
   listings each omit classes that exist. (DOC-8, TQ-9)
3. **Close the two navigation gaps** — link `doc/http-result-pattern.adoc` and `doc/LogMessages.adoc`
   from the relevant readmes (both currently have ZERO inbound references), and add
   `configuration.adoc` and `specification/compliance-traceability.adoc` to the security README's
   Document Index, which its own instructions require. (DOC-9)
4. **Add the missing implementation-status notes** — `functional-requirements.adoc` HTTP-1 requires
   body-parameter validation with no status NOTE while `PipelineFactory` throws for BODY and the
   pipeline standards state the body pipeline was eliminated; and HTTP-14 lists `COOKIE_NAME`,
   `COOKIE_VALUE` and `BODY` per-type configurations without noting the cookie pipelines throw "not
   yet implemented". The doc set is otherwise diligent about these notes, which is what makes their
   absence read as implemented. (DOC-10)
5. **Refresh `CLAUDE.md`'s architecture section** — it lists 3 pipelines (omitting
   `URLParameterNameValidationPipeline` and `ContentTypeValidationPipeline`) and 3 client classes
   (omitting the entire `de.cuioss.http.forwarded` package and the `adapter`/`converter`/`result`
   packages, all exported in `module-info.java`), and `agents.md` defers to it. Either refresh it or
   point it at `README.adoc`, which does cover them. Also extend
   `pipeline-architecture-standards.adoc`'s decision matrix, which is billed as authoritative for
   pipeline selection but treats only `URLPathValidationPipeline` in detail. (DOC-11, DOC-12)
6. **Clear the smaller accuracy and staleness items** — the HTTP-2 claim of "only two runtime
   modules" against three non-static requires; `specification.adoc`'s "see the note below" pointing
   at a note above; `http-security/README.adoc`'s RFC 8941 claim with no structured-field code, its
   RFC 7230 citation for query-parameter-name rules (7230 defines header tokens), and its
   Input→Decode→Normalize→Validate schematic matching no actual pipeline order; the stale
   `src/test/java/...` tree-root prefixes that post-restructure are `cui-http-core/src/test/java/...`;
   the overdue "Next Review" date; and the Javadoc references to a nonexistent tasks document in
   `PipelineFactory`/`SecurityDefaults`. (DOC-7, DOC-14)

Six deliverables — at the split guard. Proceeding unsplit: this is one inventory-and-navigation
refresh over a single doc tree, and deliverables 1, 2 and 6 all touch the same four specification
documents. A split would leave two plans editing `testing.adoc` and `compliance-traceability.adoc`
in sequence for no benefit. Rationale recorded as an epic decision.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| DOC-7 | LOW | Assorted smaller accuracy gaps |
| DOC-8 | MEDIUM | Stale test inventories |
| DOC-9 | MEDIUM | Orphaned documents and index omissions |
| DOC-10 | MEDIUM | Unimplemented body validation lacks a status note |
| DOC-11 | LOW | `CLAUDE.md` architecture is a stale subset of the API |
| DOC-12 | LOW | Pipeline-selection standard omits two pipelines |
| DOC-14 | LOW | Stale tree-root path prefixes and an overdue review date |
| TQ-9 | LOW | Documentation drift in the test-framework inventory — the same finding as DOC-8, owned here |

## Claim Labels

- OBSERVED: these findings are stated at `source/documentation-review.adoc` § `DOC-7`..`DOC-12`,
  `DOC-14`, and `source/test-quality-review.adoc` § `TQ-9`.
- OBSERVED: DOC-8 and TQ-9 explicitly cross-reference each other as the same drift. This is the
  reports' own identification, not an orchestrator inference. Read at
  `source/documentation-review.adoc` § `DOC-8` and `source/test-quality-review.adoc` § `TQ-9`.
- OBSERVED: DOC-8's recommended fix is "regenerate these inventories from the tree, or (better) stop
  hand-maintaining exhaustive counts in prose" — the report's own preference ordering. Read at
  `source/documentation-review.adoc` § `DOC-8`.
- OBSERVED: DOC-14 records that the actual `link:` targets in `testing.adoc` and
  `test-framework-structure.adoc` were ALREADY updated and resolve — only the prose tree-root
  prefixes are stale. Read at `source/documentation-review.adoc` § `DOC-14`.
- OBSERVED: the report positively confirms that ALL cross-references across the 22 files resolve
  (zero missing targets, all `#anchor` fragments resolve) and that requirement-ID anchor arithmetic
  is correct. ⛔ Deliverable 3 adds links; it must not break this verified property. Read at
  `source/documentation-review.adoc` § Verified correct / done well.
- HYPOTHESIS: the `tests/` directory contains 27 classes, not the documented 26 — a derived COUNT
  the orchestrator carries from the report without verifying — confirm/refute by listing
  `cui-http-core/src/test/java/de/cuioss/http/security/tests/` (verify-at-outline)
- HYPOTHESIS: there are 6 pipeline test classes and 9 validation-stage test classes, against the
  documented 4 and 7 — likewise derived counts — confirm/refute by listing the corresponding test
  directories (verify-at-outline)
- HYPOTHESIS: `doc/http-result-pattern.adoc` and `doc/LogMessages.adoc` have ZERO inbound references
  — an asserted absence — confirm/refute by grepping the doc tree for both filenames
  (verify-at-outline)
- HYPOTHESIS: `module-info.java` has three non-static `requires` — confirm/refute at
  `cui-http-core/src/main/java/module-info.java` (verify-at-outline)
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: ⛔ **every count in this plan is a moving target.** PLAN-11, PLAN-12 and
  PLAN-13 all add, rename or remove test classes. Deliverables 1 and 2 MUST be scoped against HEAD
  at the moment the plan runs, not against the report's numbers — and if PLAN-13 has landed, read
  its landing record for the classes it added or renamed. This is the strongest argument for
  deliverable 1's "stop hand-maintaining counts" route.
- Verify-first clause: deliverable 5 edits `CLAUDE.md`, a repository instruction file that every
  future agent session reads. Amend it factually; do not restructure it, and do not change any
  instruction it carries beyond the stale architecture inventory.
- ⛔ **Verify-first clause — the paired-claim hazard, folded in from PLAN-14's landing
  (2026-08-26, inbox `plan-14-doc-overclaim-correction-001.md`).** PLAN-14 reproduced the exact
  defect class it existed to remove, TWICE, and both instances were caught only by review:
  **a claim stored as a prose/row pair, edited on one side only.** Correcting SEC-3's prose left its
  traceability ROW still citing `cve-analysis.adoc`; the same shape recurred at an
  `owasp-best-practices.adoc` section header. This plan is denser in such pairs than PLAN-14 was —
  DOC-8's inventories are counts in prose backed by traceability rows, and DOC-11's `CLAUDE.md`
  architecture list is mirrored by `agents.md`'s delegation to it. **For every claim edited, locate
  and update its sibling side before moving on**, and treat a prose count with a matching table row
  as ONE edit in two places, never two edits.

## Expected Surface

- HYPOTHESIS: `doc/http-security/specification/testing.adoc`, `pipeline-architecture-standards.adoc`, `compliance-traceability.adoc`, `specification.adoc` — inventories, counts, the note cross-reference, the review date (verify-at-outline)
- HYPOTHESIS: `doc/test-framework-structure.adoc` — the full listing set and the tree-root prefix (verify-at-outline)
- HYPOTHESIS: `doc/http-security/README.adoc` — the Document Index, the RFC 8941 and RFC 7230 claims, the pipeline schematic (verify-at-outline)
- HYPOTHESIS: `doc/http-security/functional-requirements.adoc` — HTTP-1, HTTP-2, HTTP-14 status notes (verify-at-outline)
- HYPOTHESIS: `README.adoc`, `doc/security-readme.adoc`, `doc/client-handlers-readme.adoc` — inbound links for the two orphans (verify-at-outline)
- HYPOTHESIS: `CLAUDE.md` — the architecture section (verify-at-outline)
- ⛔ **`cui-http-core/src/main/java/de/cuioss/http/security/**` is NOT this plan's surface — RESOLVED.**
  DOC-7's "Implements: Task P5/C3" Javadoc references sit in `PipelineFactory` and `SecurityDefaults`,
  inside WS-01's tree. **PLAN-04 (WS-01) removes them** as part of its Javadoc-accuracy sweep; this plan
  does not open either file. Ownership decided by the orchestrator on 2026-08-26 and logged.

## Dependencies and Sequencing

- Depends on: PLAN-14 (WS-05 is strictly sequential; both edit `doc/http-security/**`).
- Overlaps with: PLAN-14.
- Adjacent to: WS-04's test tree. This plan DOCUMENTS what is there; PLAN-11/12/13 CHANGE what is
  there. Sequence this plan after WS-04 lands, or accept that deliverables 1 and 2 will need a
  second pass. ⛔ Do not edit any test class from here.
- Adjacent to: `cui-http-core/src/main/java/de/cuioss/http/security/` — ⛔ **never opened by this plan.**
  DOC-7's tasks-document Javadoc reference is applied by PLAN-04 (WS-01), which owns that tree. This was
  the single WS-01/WS-05 collision and it is now resolved by ownership rather than by sequencing.
- Adjacent to: `doc/forwarded-header-resolution.adoc` — ⛔ owned by WS-02. Never edited here.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-15-doc-inventory-and-navigation.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
