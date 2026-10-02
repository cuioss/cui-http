# Landing Analysis: PLAN-04 — Security Javadoc Accuracy

epic: quality-report-remediation
workstream: WS-01
pr: 180 (squash-merged via queue as `5aba533`)

> Landing record for one shipped plan. Every claim below was corroborated against ground truth
> before it was recorded. The operator paste and the plan's own inbox message are leads, not
> facts; third-party bot claims embedded in both were verified against source.

## Corroboration Summary

`inbox landing-check` on `security-javadoc-accuracy-005.md`: **`complete: true`, `missing_keys[0]`**
— the second consecutive complete landing, which further isolates PLAN-16's prose-only regression.

| Claim | Verdict | Evidence |
|---|---|---|
| PR #180 squash-merged as `5aba533` | **corroborated** | `git log origin/main` head is `5aba533 docs(security): correct Javadoc claims… (#180)` |
| All 5 remaining deliverable-7 Sonar sites were touched | **corroborated** | diff `30edfa3..5aba533` touches `ForwardedHeaderResolver.java` (S1845), `RetryConfig.java` (S1905), `DecodingStage.java` + `LengthValidationStage.java` (S127 ×2), `HeaderAndContentTypeEnforcementRegressionTest.java` (S5778) |
| `java:S5778` was still live despite PLAN-09's "confirmed gone" | **corroborated — and independently pre-verified by the orchestrator** | Flagged at the `next` round from `assertThrows(…, () -> pipeline().validate(…))` carrying two invocations; the diff now shows that file changed |
| `java:S9142` dropped from scope | **corroborated** | `ValidationTypeTest.java` absent from the diff; recorded as a settled operator decision, not a silent omission |
| Turkish-locale premise REFUTED | **corroborated — the plan is right** | Diff removes `attributeName.toLowerCase()` and passes `attributeName` straight to `equalsIgnoreCase`. `String.equalsIgnoreCase` compares per code point via `Character.toUpper/LowerCase` with no `Locale`, so the removal is redundancy-only, no behaviour change |
| `UrlSecurityException` never carried `@ToString(callSuper = true)` | **corroborated** | `grep '@ToString'` returns exactly five hits, all in `pipeline/`; the only `callSuper` token in `UrlSecurityException.java` sits in a comment describing a previously-removed `@EqualsAndHashCode` |
| `@ToString(callSuper = true)` retained on all five pipelines | **corroborated** | `URLPath`, `URLParameter`, `URLParameterName`, `ContentType`, `HTTPHeader` — all five still carry it |
| `module-info.java` keeps `requires transitive org.jspecify` | **corroborated** | line 20 |
| CLAUDE.md repeats the absolute-URL overclaim | **corroborated** | `CLAUDE.md:41` — "`URLPathValidationPipeline`: All URL validation (paths, **full URLs**, …)" |
| Sonar new-code count 0, `count_status=confirmed` | accepted as reported | Facts block; not independently re-queried |

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Non-compiling Javadoc examples (SV-15, CL-16, BB-5) | shipped-as-specified | 10 files; `HttpSecurityValidator.java` +47, `HTTPBody.java` +100 |
| 2. Stage-sequence and inventory Javadoc (SV-16/17/18) | shipped-**widened, correctly** | corrected **three** stale `PipelineFactory` inventories, not the two scoped. A widening that stays inside the deliverable's own claim |
| 3. Dead-end / non-compiling config docs (SV-21, SV-23) | shipped-as-specified | `config/package-info.java` +28, `SecurityDefaults.java` +10 |
| 4. Missing Javadoc on public API members (SV-22) | shipped-as-specified | 14 members; `Cookie.java` +45, `URLParameter.java` +18 |
| 5. Standalone-use docs + threat-model notes (SV-25…28) | shipped-as-specified | `NormalizationStage.java` +60, `PatternMatchingStage.java` +28 |
| 6. Structural observations (SV-29) | shipped-**partially, by recorded decision** | `AttributeParser`, `Cookie.getAttributeNames`, `module-info` done. The `@ToString(callSuper=true)` removal was **dropped** on a premise refutation, recorded rather than silently skipped |
| 7. Five staged Sonar findings | shipped-as-specified | all five sites in the diff; `java:S9142` dropped by recorded decision |

**Spec claim 6 was `contradicted / rescoped: yes` going in**, and the plan honoured the re-scope: the
stage sequence was re-read at HEAD rather than at `7ae6499`. The verify-first contract worked here.

## Surface: realized vs declared

⛔ **This is the confirmation of the defect recorded before PLAN-04 launched, and it is worse than
predicted.** The parser resolved 11 declared paths for PLAN-04; the realized footprint is **28 files**.

- **The 4 phantom declared paths were never touched** — `security/pipeline/{monitoring,exceptions,validation,config}/package-info.java` do not exist. The real files (`security/monitoring/package-info.java` etc.) **were** touched, undeclared.
- **~20 real paths were touched with no declaration**, including `core/HttpSecurityValidator.java`, `data/{AttributeParser,Cookie,URLParameter}.java`, `pipeline/{PipelineFactory,HTTPHeaderValidationPipeline,URLParameterValidationPipeline}.java`, `validation/{DecodingStage,LengthValidationStage,PatternMatchingStage}.java`, and both deliverable-7 cross-workstream files (`client/adapter/RetryConfig.java`, `forwarded/ForwardedHeaderResolver.java`).

**No collision resulted.** Verified against both concurrently-running plans:

- **vs PLAN-10 (still running)** — the only `client/` path PLAN-04 touched is `client/adapter/RetryConfig.java`, which PLAN-10 does not declare and does not own. The hand-derived partition made at the `next` round **held**.
- **vs PLAN-13 (shipped)** — PLAN-04 opened `HeaderAndContentTypeEnforcementRegressionTest.java`, and PLAN-13's landing independently confirms it never opened that file. The two-sided carve-out authored at launch **worked exactly as designed**.

So the gate's *prediction* was right while the gate's *input* was wrong: the disjointness held because
of hand analysis at emit time, not because the declaration described reality.

## Metrics and Anomalies

- **Tokens:** 4,618,330. **Duration:** 21h15m wall / 2h59m worked.
- **Tests:** 6,635 in the pre-push quality gate; all 19 finalize steps `done`.
- ⛔ **~10 hours of the 21h wall was dead time waiting on a rate-limited CodeRabbit** whose
  self-reported ETA ("24 minutes") was wrong by roughly 15×. The wait was not wasted only because
  an explicit `@coderabbitai review` re-trigger eventually produced 5 findings, **2 of them real
  Javadoc inaccuracies this plan had missed** — precisely its own subject matter.
- **Merged on operator consent, not on bot approval.** All bot reviews sat at `8b55b39`/`8c262cd`
  and Sourcery's approval was auto-dismissed by the ADR commit. Recorded as a HEAD-bound
  `pre-merge-consent` grant at `34db8ca`; the unreviewed delta was exactly the two ADR `.adoc`
  files (289 insertions, no code). Proportionate and properly recorded.

## Routing and Merge Behavior

- **Review:** CodeRabbit (after re-trigger) found 5, of which 2 real and fixed at `8c262cd`
  (`Cookie.getAttributeNames` overclaiming that all malformed tokens are returned whole;
  `pipeline/package-info` implying `scheme://` input skips traversal detection entirely).
- **CI/merge:** all green; squash-merged via merge queue. No rebase conflict with either
  concurrently-running plan.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` = `180`; `landing` = `landings/PLAN-04.md`
- [x] **PLAN-04 surface defect CLOSED as confirmed** — it can no longer be fixed (the plan shipped),
      so it converts from "fix at cleanup" to evidence in the gate's residual-error record.
- [x] **Duplicate-ADR defect: RECURRENCE recorded**, not a second entry — see below.
- [x] **New Open Defect** — the `adr-propose` `mutates_source` defect recurred despite an
      existing lesson.
- [x] **New Open Defect** — two out-of-scope doc residues PLAN-04 observed and correctly did not touch.
- [x] resume_anchor updated; both generated blocks regenerated

## Follow-Ups

1. ⛔ **DUPLICATE ADR NUMBERS RECURRED, exactly as the standing defect predicted.** `doc/adr/` now
   holds **two 0009s and two 0010s**: PLAN-13 landed `0009-Attack-database…` / `0010-NFKC-fold…` in
   PR #178, and PLAN-04 landed `0009-HttpSecurityValidators_when_and_identity…` /
   `0010-NormalizationStage_clamps…` in PR #180. Neither plan is at fault — there is still no shared
   ADR-number allocator, and both plans ran concurrently by design. The existing defect said "decide
   before the next wave is emitted, or accept that it recurs"; the wave was emitted and it recurred.
   **Four duplicate numbers now stand on main (0004, 0005, 0009, 0010).** Folded into the existing
   entry as a recurrence.
2. ⛔ **`default:adr-propose` still declares no `mutates_source`** and its output escaped commit
   instrumentation again. Both ADRs survived only because the plan committed them by hand. This is
   lesson `2026-08-27-12-004` recurring **five days after it was filed** — a filed lesson that
   produced no fix. Folded there as a recurrence.
3. **ADR-0009 and ADR-0010 (PLAN-04's) merged as `Proposed`** and want human acceptance — they record
   previously-undocumented *security positions*: `when()`/`identity()` are deliberately fail-open,
   and `NormalizationStage` RFC-correctly clamps root-consumed dot-segments. Combined with PLAN-13's
   two, **four ADRs now await acceptance**.
4. **Two out-of-scope doc residues, correctly not touched, now unowned:** `CLAUDE.md:41` repeats the
   same absolute-URL overclaim PLAN-04 just corrected in `pipeline/package-info.java`; and several
   `package-info` examples use SLF4J-style `{}` placeholders the CuiLogger standard forbids. WS-05
   is closed.
5. **Four candidate lessons** dispositioned individually — see the epic decision log.
