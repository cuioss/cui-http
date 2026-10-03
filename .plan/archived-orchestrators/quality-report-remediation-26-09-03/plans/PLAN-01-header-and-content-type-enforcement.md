# PLAN-01: Header and Content-Type Enforcement

epic: quality-report-remediation
workstream: WS-01

## Objective

Close the two HIGH-severity contract-vs-implementation gaps in the header pipelines and the related
fail-open in content-type validation. The header-name pipeline promises RFC 7230 token validation
but maps `HEADER_NAME` to the header-*value* character set, so a "name" containing a space and a
colon passes the whole pipeline; `PatternMatchingStage` occupies a slot in that pipeline and is a
complete no-op for header types while the Javadoc advertises it as active detection; and
`ContentTypeValidationPipeline` validates nothing under the default configuration while an empty
string bypasses even a configured allow-list. Each is resolved either by making the enforcement real
or by narrowing the component's own Javadoc — never by leaving a broad claim over a narrow
implementation.

## Deliverables

1. **`HEADER_NAME` gets a real RFC 7230 `tchar` set.** Split `HEADER_NAME` off the shared
   `RFC7230_HEADER_CHARS` mapping and give it the RFC 7230 §3.2.6 token set, excluding space, `:`,
   `"`, `,`, `;`, `(`, `)`. (SV-1)
2. **Resolve the wired-no-op stages in the header pipelines** — either implement header-relevant
   pattern checks, or remove `PatternMatchingStage`/`NormalizationStage` from the header pipeline,
   or correct the pipeline Javadoc so it does not advertise detection the stage does not perform.
   The same correction covers `URLParameterNameValidationPipeline`'s pass-through claim. (SV-2, SV-19)
3. **Fix the `AllowBlockListStage` empty-string fail-open** so that when a non-empty allow-list is
   configured, `""` is rejected as "not in the allow-list" rather than short-circuit-accepted, and
   the block-list is no longer skipped for it. (SV-12, and SV-6(b) which reports the same root cause)
4. **Make `ContentTypeValidationPipeline`'s scope honest** — document the list-only behaviour
   prominently at the class level, and reconcile the fact that it reports `ValidationType.HEADER_VALUE`
   while performing none of the length/character validation its sibling HEADER_VALUE pipeline does. (SV-6(a))
5. **Regression tests** for each of the four above, asserting the specific `UrlSecurityFailureType`
   in the style the existing stage-level tests already use.

Five deliverables — under the split guard.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| SV-1 | HIGH | Header-name pipeline does not enforce RFC 7230 token characters |
| SV-2 | HIGH | `PatternMatchingStage` is a no-op for header types but advertised as active |
| SV-6 | MEDIUM | `ContentTypeValidationPipeline` validates nothing by default; empty string bypasses a configured allow-list |
| SV-12 | LOW | `AllowBlockListStage` empty-string early return bypasses the allow-list (root cause of SV-6(b)) |
| SV-19 | LOW | `URLParameterNameValidationPipeline` Javadoc describes a pass-through stage as active |

## Claim Labels

- OBSERVED: the six review reports under `source/` state these findings — read directly at
  `source/security-validation-review.adoc` § `SV-1`, `SV-2`, `SV-6`, `SV-12`, `SV-19`.
- OBSERVED: the report records that SV-2 was independently reported by two reviewers and that
  SV-6(b) was independently reported against `AllowBlockListStage` directly — read at
  `source/security-validation-review.adoc` § `SV-2`, `SV-6`.
- HYPOTHESIS: `CharacterValidationConstants` maps `HEADER_NAME` and `HEADER_VALUE` to the same
  `RFC7230_HEADER_CHARS` set spanning ASCII 32–126 plus tab — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationConstants.java`
  § the `HEADER_NAME, HEADER_VALUE ->` switch arm (verify-at-outline)
- HYPOTHESIS: every check in `PatternMatchingStage.validate` is gated on `URL_PATH`,
  `PARAMETER_VALUE`, or `PARAMETER_NAME`, so header types fall through to `return Optional.of(value)`
  — confirm/refute at `cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java`
  § `validate` (verify-at-outline)
- HYPOTHESIS: `AllowBlockListStage` short-circuits on `value.isEmpty()` *before* reaching the
  allow-list check — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/AllowBlockListStage.java`
  § the empty-value early return (verify-at-outline)
- HYPOTHESIS: `ContentTypeValidationPipeline.createStages` returns a single-element list containing
  only `AllowBlockListStage.forContentTypes(config)` — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/ContentTypeValidationPipeline.java`
  § `createStages` (verify-at-outline)
- HYPOTHESIS: the derived count "5 findings, of which 2 are HIGH" is the orchestrator's own tally of
  the SV findings assigned to this plan, not a figure stated in any report — confirm/refute by
  re-counting the § Findings Covered table against `source/security-validation-review.adoc`
  (verify-at-outline)
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: deliverable 2 offers three mutually exclusive resolutions (implement,
  remove, or narrow the Javadoc). This is a genuine design fork with materially different
  downstream consequences — decide it at outline against the code and record the choice, do not
  silently pick one.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationConstants.java` — the `HEADER_NAME` character-set mapping (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java` — `validate` type gating (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/AllowBlockListStage.java` — empty-value early return, `forContentTypes` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/NormalizationStage.java` — the non-path pass-through branch, Javadoc only (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/HTTPHeaderValidationPipeline.java` — class Javadoc, stage wiring (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/ContentTypeValidationPipeline.java` — `createStages`, class Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLParameterNameValidationPipeline.java` — class Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/PipelineFactory.java` — header-pipeline construction (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/` and `cui-http-core/src/test/java/de/cuioss/http/security/validation/` — the pipeline and stage test classes covering the four changes (verify-at-outline)
- ⛔ **NOT this plan's surface:** `cui-http-core/src/test/java/de/cuioss/http/security/generators/` and `.../database/`. WS-04 owns those. This bullet previously named the whole `security/` test tree, which made this plan falsely collide with PLAN-11 at the disjointness gate.

## Dependencies and Sequencing

- Depends on: none. This is the epic's queue head.
- Overlaps with: PLAN-02 (`CharacterValidationStage`, `CharacterValidationConstants`,
  `PipelineFactory`), PLAN-04 (Javadoc in the same pipeline files). Strictly sequential — never paired.
- Adjacent to: `de.cuioss.http.forwarded` (WS-02), which sanitizes through
  `createHeaderValuePipeline`. It stays untouched here, but a change to HEADER_VALUE behaviour
  invalidates the premise of FW-11 and DOC-4 — note the change in the landing so WS-02 and WS-05
  re-verify rather than re-derive.
- Adjacent to: `doc/http-security/configuration.adoc`, which the report verifies matches the code
  value for value. If a config knob's *effect* changes here, that doc's accuracy claim changes too —
  flag it for PLAN-14 rather than editing it.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-01-header-and-content-type-enforcement.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
