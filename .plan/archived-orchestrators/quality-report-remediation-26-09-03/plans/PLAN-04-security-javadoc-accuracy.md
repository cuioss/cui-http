# PLAN-04: Security Javadoc Accuracy

epic: quality-report-remediation
workstream: WS-01

## Objective

Bring every Javadoc claim in `de.cuioss.http.security` into agreement with the code it documents.
The package currently ships usage examples that do not compile (assigning an `Optional<String>` to a
`String`, calling a two-argument `validate` overload that does not exist, chaining builder methods
and a `SecurityLevel` type that do not exist), a class Javadoc whose stage sequence is the exact
order its own code comment calls wrong, a data type directing users to a validation path that was
removed, and public API members with no Javadoc at all despite CLAUDE.md mandating it. This is the
epic's largest single documentation surface and it is sequenced LAST in WS-01 so it documents what
PLAN-01..03 actually landed.

## Deliverables

1. **Fix every non-compiling usage example** across the pipeline, monitoring, exceptions, and
   validation `package-info.java` files and the data types — the `Optional<String>`-to-`String`
   assignments and the stale two-argument `validate(x, ValidationType.Y)` form. This single
   deliverable also closes CL-16 and BB-5, which report the same defect from the client and build
   reviews. (SV-15, CL-16, BB-5)
2. **Correct the stage-sequence and inventory Javadoc** — `URLPathValidationPipeline`'s documented
   5-stage Decoding-before-Pattern order versus the code's 6 stages with Pattern before Decoding and
   again after Normalization; `PipelineFactory`'s "Supported Validation Types" and COOKIE error text
   omitting `PARAMETER_NAME`; and `pipeline/package-info.java` omitting two pipelines and
   overstating full-URL coverage. (SV-16, SV-17, SV-18)
3. **Remove the dead-end and non-compiling config documentation** — `HTTPBody`'s direction to a
   BODY validation path that `PipelineFactory` throws on; `config/package-info.java`'s example
   calling three nonexistent methods and a nonexistent `SecurityLevel` type plus its
   "all security features enabled by default" claim contradicting the real defaults; and
   `SecurityDefaults`' example using a private constructor. (SV-21, SV-23)
4. **Add the missing Javadoc** on the public API members CLAUDE.md mandates it for — `HTTPBody`'s
   ten factory and predicate methods, `URLParameter.withName`/`withValue`, and
   `Cookie.nameOrDefault`/`valueOrDefault`. (SV-22)
5. **Correct the misleading standalone-use documentation and add the threat-model notes** — the
   `NormalizationStage` example that documents a `DIRECTORY_ESCAPE_ATTEMPT` throw that does not
   happen (`..` at root is RFC-correctly clamped); an explicit note that the `when()`/`identity()`
   combinators are intentional fail-open building blocks whose misuse silently disables validation;
   an explicit threat-model note that decoded CR/LF is deliberately allowed for `PARAMETER_VALUE`
   and `BODY`, so response-splitting safety depends on the application not reflecting parameter
   values into headers; and a note that traversal detection is duplicated across three sites with
   divergent failure types. (SV-25, SV-26, SV-27, SV-28)
6. **Clear the small structural observations** — the redundant default-locale `toLowerCase` in
   `AttributeParser`, `Cookie.getAttributeNames` returning a malformed `=value` token whole and its
   undocumented first-match duplicate resolution, the `@ToString(callSuper=true)` noise on all
   pipelines, and `module-info.java`'s non-transitive `requires org.jspecify` despite JSpecify
   annotations appearing in exported API signatures. (SV-29)

7. **Clear the 6 Sonar new-code findings PLAN-16 surfaced** (7 originally; `java:S2589` moved to PLAN-10 on 2026-08-30 — it is PLAN-10's own declared file) (staged here 2026-08-29 by operator
   decision — see the ⛔ note below). All seven are pre-existing `main`-branch issues, NOT
   regressions from this epic: PLAN-16's scan reported `pull_request: none`. Two were independently
   corroborated at HEAD by the orchestrator; the other five are HYPOTHESIS and must be
   confirmed-or-refuted at outline before being actioned:

   | Finding | Location | Grounding | Native workstream |
   |---|---|---|---|
   | `java:S1845` **BLOCKER** | `ForwardedHeaderResolver.java:686` — the `UNRESOLVABLE` field and the `unresolvable()` record accessor differ only by case | ✅ corroborated at `382327a` | WS-02 — ⛔ **orphaned**, PLAN-07 closed without fixing it |
   | `java:S1905` | `RetryConfig.java:197` — `(double)` cast redundant inside `Math.min`. ⛔ **Line RE-DERIVED at `6ee0c7c` after PLAN-09 landed; the scan's original 163 is stale.** | ✅ corroborated at `6ee0c7c` | WS-03 |
   | `java:S127` | `LengthValidationStage.java:198` — loop-counter assignment | HYPOTHESIS (verify-at-outline) | WS-01 — this plan's own |
   | `java:S127` | `DecodingStage.java:406` — loop-counter assignment | HYPOTHESIS (verify-at-outline) | WS-01 — this plan's own |
   | `java:S5778` | `HeaderAndContentTypeEnforcementRegressionTest.java:228` | HYPOTHESIS (verify-at-outline) | WS-04 |
   | `java:S9142` | `ValidationTypeTest.java` | HYPOTHESIS (verify-at-outline) | WS-04 |

   ⛔ **Five of the seven are OUTSIDE this plan's workstream.** Treat every one of them as a
   MINIMAL, mechanical correction: fix the finding and nothing else in that file. ⛔ **Do NOT widen**
   — `ForwardedHeaderResolver` diagnostics are WS-02's, `RetryConfig` and `ETagAwareHttpAdapter`
   belong to WS-03, and the two test classes to WS-04. A non-mechanical change in any of them is a
   scope violation to surface, not to make. ⛔ **`java:S1845` is the only BLOCKER in the set and has
   no other owner** — WS-02 has no remaining staged plan.

⛔ **SEVEN deliverables — PAST the split guard, unsplit by explicit operator decision (2026-08-29).**
The original six carry this rationale: a homogeneous documentation-accuracy sweep over one package
tree with no behavioural risk and no ordering constraint between deliverables; splitting it would
multiply PR and review overhead across files that a single reader should see together, and the report
itself treats them as one class. The one non-documentation item is deliverable 6's `module-info` and
`AttributeParser` cleanup, which is trivial and co-located. **That rationale does NOT extend to
deliverable 7**, a cross-workstream mechanical sweep sharing no surface with 1–6. The operator
directed the findings into an upcoming plan rather than a running one; this is the next staged plan
in queue order. Recorded here rather than silently absorbed.

⛔ **History, so it is not re-litigated:** deliverable 7 was briefly written into PLAN-09's spec on
2026-08-29 and removed the same day. That fold could never take effect — PLAN-09's `request.md` was
written at 12:05 and the spec edit landed at 18:26, six hours after `phase-1-init` had ingested it —
and PLAN-09 was in `6-finalize` besides, where new deliverables can only mean a loop-back.
**Do not read PLAN-09's landing as having closed any Sonar finding.**

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| SV-15 | LOW | Non-compiling and stale Javadoc examples across pipelines and data types (reported by three reviewers) |
| SV-16 | LOW | `URLPathValidationPipeline` Javadoc stage sequence contradicts the code |
| SV-17 | LOW | `PipelineFactory` Javadoc and error messages omit supported types |
| SV-18 | LOW | `pipeline/package-info.java` omits two pipelines and overstates URL coverage |
| SV-21 | LOW | `HTTPBody` Javadoc directs users to a removed validation path |
| SV-22 | LOW | Missing Javadoc on public API members |
| SV-23 | INFO | `config/package-info.java` example calls nonexistent methods and overclaims defaults |
| SV-25 | INFO | Combinator utilities are intentional fail-open building blocks |
| SV-26 | INFO | Decoded CR/LF is intentionally allowed for `PARAMETER_VALUE` and `BODY` |
| SV-27 | INFO | Traversal detection is duplicated across three sites with divergent failure types |
| SV-28 | INFO | `NormalizationStage` standalone Javadoc example is inaccurate |
| SV-29 | INFO | Minor data-type and module observations |
| DOC-7 (part) | LOW | **Inherited from PLAN-15 (WS-05) by ownership decision.** The `"Implements: Task P5/C3 from HTTP verification specification"` Javadoc references in `PipelineFactory` and `SecurityDefaults` point at a tasks document that does not exist. Remove them. The rest of DOC-7 stays with PLAN-15. |
| CL-16 | LOW | Non-compiling example in `monitoring/package-info` — the same defect as SV-15, tracked here |
| BB-5 | LOW | Non-compiling examples in three `package-info` files — the same defect as SV-15, tracked here |

## Claim Labels

- OBSERVED: these findings are stated at `source/security-validation-review.adoc` § `SV-15`..`SV-29`,
  `source/http-client-review.adoc` § `CL-16`, and `source/build-and-benchmarking-review.adoc` § `BB-5`.
- OBSERVED: CL-16 and BB-5 both explicitly state they are tracked centrally under SV-15 — read at
  `source/http-client-review.adoc` § `CL-16` and `source/build-and-benchmarking-review.adoc` § `BB-5`.
  This is the report's own cross-reference, not an orchestrator inference.
- HYPOTHESIS: `HttpSecurityValidator.validate` returns `Optional<String>` and no two-argument
  overload exists — an asserted absence, verified as a presence — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/core/HttpSecurityValidator.java`
  § `validate` (verify-at-outline)
- HYPOTHESIS: no `SecurityLevel` type exists anywhere in the security package — an asserted
  absence — confirm/refute by grepping `cui-http-core/src/main/java/de/cuioss/http/security/`
  (verify-at-outline)
  - verdict: corroborated | checked_at: 6ee0c7c | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at 6ee0c7c after PLAN-03 shipped this package: HttpSecurityValidator.validate still returns Optional<String> at core/HttpSecurityValidator.java:106, so the non-compiling String-assignment examples SV-23 names remain non-compiling
- HYPOTHESIS: `PipelineFactory.createPipeline(BODY, …)` throws `IllegalArgumentException` naming the
  removed BODY pipeline — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/PipelineFactory.java`
  § `createPipeline` (verify-at-outline)
  - verdict: corroborated | checked_at: 6ee0c7c | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at 6ee0c7c: SecurityLevel still exists ONLY inside config/package-info.java:48's example (.securityLevel(SecurityLevel.STRICT)) and as no type anywhere in the security tree - the asserted absence still holds and IS the defect
- HYPOTHESIS: `URLPathValidationPipeline` builds six stages with `PatternMatchingStage` before
  `DecodingStage` and again after `NormalizationStage` — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLPathValidationPipeline.java`
  § `createStages` (verify-at-outline)
  - verdict: corroborated | checked_at: 6ee0c7c | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at 6ee0c7c: PipelineFactory.java:250-251 still throws naming the removed BODY validation pipeline
- HYPOTHESIS: the derived count "14 findings, spanning three source reports" is the orchestrator's
  own tally of what this plan owns, not a figure any report states — confirm/refute by re-counting
  the § Findings Covered table against the three named source documents (verify-at-outline)
  - verdict: contradicted | checked_at: 6ee0c7c | by: quality-report-remediation/cleanup | rescoped: yes | evidence: Re-checked at 6ee0c7c. Premise still refuted and the re-scope instruction STANDS: URLPathValidationPipeline.createStages is now Length->Character->Pattern->Decoding->Normalization->Pattern (lines 114-119), a six-stage sequence with PatternMatchingStage run TWICE. The Javadoc SV-16 targets must be read at 6ee0c7c, not at 7ae6499 and not at f1ba539
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: ⛔ this plan is sequenced LAST in WS-01 precisely because PLAN-01, PLAN-02 and
  PLAN-03 change the behaviour these Javadoc items describe. Before scoping, read what those three
  plans actually landed — the correct stage sequence for SV-16, the header-pipeline stage set for
  SV-18, and the `AttributeParser` state for SV-29 all depend on their outcomes. Documenting the
  pre-PLAN-01 behaviour would ship Javadoc that is stale on the day it lands.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/package-info.java`, `monitoring/package-info.java`, `exceptions/package-info.java`, `validation/package-info.java`, `config/package-info.java` — usage examples and inventories (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLPathValidationPipeline.java`, `URLParameterValidationPipeline.java`, `HTTPHeaderValidationPipeline.java`, `PipelineFactory.java` — class Javadoc, examples, error text (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/data/HTTPBody.java`, `URLParameter.java`, `Cookie.java`, `AttributeParser.java` — Javadoc, missing member docs, `getAttributeNames`, `toLowerCase` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/core/ValidationType.java`, `HttpSecurityValidator.java` — examples, combinator Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/NormalizationStage.java`, `DecodingStage.java`, `PatternMatchingStage.java` — examples, threat-model notes (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` — the example using a private constructor (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/module-info.java` — the `requires org.jspecify` directive (verify-at-outline)

### Deliverable 7's added surface (Sonar sweep, staged 2026-08-29)

⛔ **Each file is opened for ONE mechanical Sonar fix and nothing else.**

- CONFIRMED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java` — line 686 ONLY (`java:S1845`, BLOCKER). ⛔ WS-02 owns the rest of this file; PLAN-07 shipped it at `b71522c`.
- CONFIRMED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/RetryConfig.java` — line 197 ONLY (`java:S1905`), re-derived at `6ee0c7c`. ⛔ WS-03's file; PLAN-09 shipped it at `6ee0c7c`.
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/LengthValidationStage.java` — line 198 ONLY (`java:S127`) (verify-at-outline). Already this plan's package; ⛔ PLAN-03 shipped this file at `e2c7ebd`.
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java` — line 406 ONLY (`java:S127`) (verify-at-outline). Already this plan's declared surface for Javadoc.
- HYPOTHESIS: `cui-http-core/src/test/java/.../HeaderAndContentTypeEnforcementRegressionTest.java` — line 228 ONLY (`java:S5778`) (verify-at-outline). ⛔ WS-04's test tree.
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/core/ValidationTypeTest.java` — `java:S9142` ONLY (verify-at-outline). ⛔ WS-04's test tree.

✅ **SEQUENCING CLEARED 2026-08-30 — PLAN-09 landed (#172, `6ee0c7c`) and every file above is now
free.** The `java:S1905` line was re-derived against that HEAD: **163 → 197**. ⛔ `java:S2589` on
`ETagAwareHttpAdapter.java` was NOT re-derived to a line, deliberately — PLAN-09 rewrote that file
substantially, so **locate it by CONDITION** (an always-true expression), never by the scan's
original line 539 or by any number quoted here. ⛔ **`java:S5778` may already be resolved**: PLAN-09's
`sonar-roundtrip` reported "S5778 findings confirmed gone". **Verify before actioning it** — do not
"fix" a finding that no longer exists, and do not assume the rest of the set moved with it.

## Dependencies and Sequencing

- Depends on: PLAN-01, PLAN-02, PLAN-03 — all three, and not merely for sequencing: their landed
  behaviour is the input to deliverables 2, 5 and 6.
- Overlaps with: all of WS-01.
- Adjacent to: WS-03's `client` package and WS-06's build files. CL-16 and BB-5 point INTO this
  package from those reports and are closed here; PLAN-10 and PLAN-16 must not also edit these files.
- Adjacent to: `doc/http-security/specification/specification.adoc`, which the report verifies
  matches the pipeline stage orderings exactly. If deliverable 2 changes the documented order in
  Javadoc, that AsciiDoc must agree — flag it for PLAN-14/PLAN-15 rather than editing it here.


## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-04-security-javadoc-accuracy.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
