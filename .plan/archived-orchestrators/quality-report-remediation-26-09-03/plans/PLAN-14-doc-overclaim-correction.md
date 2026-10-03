# PLAN-14: Documentation Overclaim Correction

epic: quality-report-remediation
workstream: WS-05

## Objective

Remove the residual documentation overclaims that contradict the suite's own carefully-scoped
statements. `Requirements.adoc` marks OWASP Top 10, NIST 800-53 and PCI DSS "✓ Complete" while the
traceability matrix it links ONE LINE BELOW marks OWASP A09, NIST AU-2/AU-12 and PCI 10.2 partial —
and the traceability matrix is the one that matches the code, since `SecurityEventCounter` counts
only. `owasp-best-practices.adoc` claims a configurable pattern database that does not exist
(contradicting SEC-18, which correctly says runtime-configurable patterns are not implemented) and
claims JSON/XML and file-upload validation "implemented in" the URL pipelines before immediately
stating the body pipeline was eliminated. Three documented forwarded-sanitization knobs are inert.
The client-handlers readme claims log levels the code does not have. And SEC-3's four referenced
CVEs appear in no analysis document. Each is a false sense of assurance, which is exactly the theme
this epic exists to close.

## Deliverables

1. **Align the Requirements compliance matrix with the traceability matrix** — the traceability
   matrix matches the code and wins; the "✓ Complete" claims overstate and risk a false sense of
   compliance. This is the report's Top Priority #7. (DOC-1)
2. **Remove the two `owasp-best-practices.adoc` overclaims** — the nonexistent configurable pattern
   database, and the JSON/XML content and file-upload filename validation attributed to the URL
   pipelines (which perform neither, and `PipelineFactory.createPipeline(BODY, …)` throws). Also
   correct the attribution of parameter-NAME validation, which belongs to
   `URLParameterNameValidationPipeline`, not `URLParameterValidationPipeline`. (DOC-2, DOC-3)
3. **List only the config knobs that actually reach forwarded-header sanitization** —
   `normalizeUnicode`, the header allow/block lists, and `allowDoubleEncoding` have no effect,
   because the resolver sanitizes through the HEADER_VALUE pipeline which has no `DecodingStage` and
   gets `AllowBlockListStage` only for HEADER_NAME. The other listed knobs do apply. (DOC-4)
4. **Correct the claimed log levels** in `client-handlers-readme.adoc` — it says `HttpLogMessages`
   provides debug, info, warning and error levels, but the class contains only WARN and ERROR
   records. (DOC-5)
5. **Resolve the SEC-3 CVE reference** — its four CVEs appear in NO analysis document; the analysis
   doc it points at documents a DISJOINT set of eight. Point the reference at the test database that
   actually covers CVE-2021-41773, or add the CVEs to an analysis doc. (DOC-6)
6. **Add the missing SEC-13 status note** — "must use constant-time comparisons where appropriate"
   has no NOTE, no implementation reference, and no traceability row, unlike its diligently-annotated
   neighbours, so it reads as implemented when the library does not appear to do this anywhere. (DOC-13)

Six deliverables — at the split guard. Proceeding unsplit: all six are single-statement corrections
to prose, four of them within `doc/http-security/`, with no ordering constraint and no behavioural
risk. Splitting would double the review overhead for a reviewer who should see the compliance story
as one piece. Rationale recorded as an epic decision.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| DOC-1 | MEDIUM | The Requirements compliance matrix marks standards "✓ Complete" that the traceability matrix marks partial |
| DOC-2 | MEDIUM | `owasp-best-practices.adoc` claims a "configurable pattern database" that does not exist |
| DOC-3 | MEDIUM | `owasp-best-practices.adoc` overclaims body validation and contradicts itself |
| DOC-4 | MEDIUM | Two documented forwarded-sanitization config knobs are inert |
| DOC-5 | MEDIUM | `client-handlers-readme.adoc` claims log levels that do not exist |
| DOC-6 | MEDIUM | SEC-3's referenced CVEs appear in no analysis document |
| DOC-13 | LOW | SEC-13 reads as implemented but has no evidence |

## Claim Labels

- OBSERVED: these findings are stated at `source/documentation-review.adoc` § `DOC-1`..`DOC-6`, `DOC-13`.
- OBSERVED: the report states every drift claim was re-verified against the current code, and that
  the documentation suite is materially more accurate than typical — these are the residue, not a
  systemic problem. Read at `source/documentation-review.adoc` § the opening NOTE.
- OBSERVED: DOC-1 is named as Top Priority #7 in the epic index. Read at `source/README.adoc`
  § Top Priorities.
- OBSERVED: the report positively confirms that `doc/LogMessages.adoc` and
  `doc/http-security/configuration.adoc` match the code EXACTLY — every ID, template string,
  parameter description, and every preset value. ⛔ Those two files are verified-correct; do not
  "improve" them without a specific finding. Read at `source/documentation-review.adoc` § Verified
  correct / done well.
- OBSERVED: DOC-4's mechanism is that `normalizeUnicode` is consumed ONLY by `DecodingStage`, and the
  HEADER_VALUE pipeline has no `DecodingStage`. Read at `source/documentation-review.adoc` § `DOC-4`.
- HYPOTHESIS: `Requirements.adoc` shows "✓ Complete" for OWASP/NIST/PCI while
  `compliance-traceability.adoc` marks A09, AU-2/AU-12 and 10.2 partial — confirm/refute at
  `doc/http-security/Requirements.adoc` § the compliance matrix and
  `doc/http-security/specification/compliance-traceability.adoc` § the same standards
  (verify-at-outline)
- HYPOTHESIS: `SecurityDefaults`' pattern constants are `static final` and the builder exposes no
  pattern knob — an asserted absence underpinning DOC-2 — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` and
  `SecurityConfigurationBuilder.java` (verify-at-outline)
- HYPOTHESIS: `HttpLogMessages` contains only WARN and ERROR records — an asserted absence of DEBUG
  and INFO — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/client/HttpLogMessages.java` (verify-at-outline)
- HYPOTHESIS: none of SEC-3's four CVEs appears in any analysis document — an asserted absence the
  report established by grep — confirm/refute by grepping `doc/` for each of the four CVE ids
  (verify-at-outline)
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: ⛔ **DOC-4's premise depends on WS-01.** It asserts the HEADER_VALUE pipeline
  has no `DecodingStage` and gets `AllowBlockListStage` only for HEADER_NAME. PLAN-01 may have
  changed exactly that. If PLAN-01 has landed, re-read the header pipeline's stage set before
  applying deliverable 3 — if a `DecodingStage` is now present, the finding is REFUTED and must be
  re-scoped, not applied.
- Verify-first clause: DOC-3's premise depends on `PipelineFactory.createPipeline(BODY, …)` still
  throwing. Confirm before scoping.

## Expected Surface

- HYPOTHESIS: `doc/http-security/Requirements.adoc` — the compliance matrix (verify-at-outline)
- HYPOTHESIS: `doc/http-security/specification/compliance-traceability.adoc` — READ-ONLY reference for deliverable 1 (verify-at-outline)
- HYPOTHESIS: `doc/http-security/analysis/owasp-best-practices.adoc` — the pattern-database bullet, the body-validation section, the parameter-name attribution (verify-at-outline)
- ⛔ **`doc/forwarded-header-resolution.adoc` is NOT this plan's surface — RESOLVED.** DOC-4's edit lands in
  that file, which WS-02 owns exclusively. **PLAN-07 (WS-02) applies DOC-4**; this plan does not open the file.
  Deliverable 3 below is therefore DEFERRED to PLAN-07 and is retained here only as the finding's record.
  Ownership decided by the orchestrator on 2026-08-26 and logged; do not re-litigate it at outline.
- HYPOTHESIS: `doc/client-handlers-readme.adoc` — the log-levels claim (verify-at-outline)
- HYPOTHESIS: `doc/http-security/security-requirements.adoc` — SEC-3's reference, SEC-13's status note (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none within WS-05 — this is the workstream head. But see the DOC-4 verify-first clause:
  its premise is invalidated by PLAN-01.
- Overlaps with: PLAN-15 (both edit `doc/http-security/**`). Strictly sequential within WS-05.
- Adjacent to: `doc/forwarded-header-resolution.adoc` — ⛔ **never opened by this plan.** DOC-4 is applied
  by PLAN-07 (WS-02), which owns the file. This was the single WS-02/WS-05 collision and it is now resolved
  by ownership rather than by sequencing, which is what makes WS-05 disjoint from WS-02 at every position.
- Adjacent to: `doc/LogMessages.adoc` and `doc/http-security/configuration.adoc` — both
  verified-correct against the code. Leave them alone.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-14-doc-overclaim-correction.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
