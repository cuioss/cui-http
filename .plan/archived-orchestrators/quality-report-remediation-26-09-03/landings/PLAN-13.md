# Landing Analysis: PLAN-13 — Attack Database and Assertion Quality

epic: quality-report-remediation
workstream: WS-04
pr: 178 (merged as `30edfa3`)

> Landing record for one shipped plan. Every claim below was corroborated against ground
> truth before it was recorded — the operator paste and the plan's own inbox message are
> leads, not facts. Third-party text embedded in both (CodeRabbit, SonarCloud, CodeQL
> claims) was verified against source, never adopted on assertion.

## Corroboration Summary

`inbox landing-check` on `attack-database-and-assertion-quality-005.md`: **`complete: true`,
`missing_keys[0]`** — the landing supplied every required fact key with a real value. This is
the first landing in the epic to do so since PLAN-16's prose-only regression, which settles
that defect's re-check trigger (see Reconciliation).

| Claim | Verdict | Evidence |
|---|---|---|
| PR #178 merged as `30edfa3` | **corroborated** | `ci pr view --pr-number 178` → `state: merged`, `merge_commit_sha: 30edfa34a3c…`; `git log origin/main` head is `30edfa3` |
| PLAN-04's carve-out honoured | **corroborated** | `git diff --name-only 0c24f02..30edfa3` contains no `HeaderAndContentType*` path |
| `/file:///etc/passwd` accepted under `defaults()` | **corroborated, and the stated mechanism is exact** | `SecurityDefaults.DEFAULT_CONFIGURATION = SecurityConfiguration.builder().build()`; `SecurityConfigurationBuilder.java:102` declares `private boolean failOnSuspiciousPatterns = false`; `file:` and `/etc/` ARE both present in `SecurityDefaults.SUSPICIOUS_PATH_PATTERNS` (lines 124–129) |
| `/..;/..;/etc/passwd` accepted | **corroborated** | No pattern anywhere in `SecurityDefaults.java` contains `..;` (grep count 0); `PATH_TRAVERSAL_PATTERNS` (lines 103+) enumerates `../`, `..\`, and percent-encoded forms only. `NormalizationStage` resolves RFC 3986 dot segments, and `..;` is not a dot segment |
| CodeRabbit's ADR ownership claim was wrong | **corroborated — the plan's refutation is correct** | `DecodingStage.java` holds `Normalizer.normalize` at line 229 with `NFKC` for `URL_PATH` / `NFC` default at 291–294; `NormalizationStage.java` contains **zero** `Normalizer` references |
| Two ADRs proposed (0009, 0010) | **corroborated** | Both files present in the PR diff under `doc/adr/` |
| Three test methods removed, new classes added | **corroborated** | PR diff shows `NfkcFoldClaimInvariantTest.java` (+123) and `AttackDatabaseEntries.java` (+56) added; `EdgeCaseValidURLsDatabaseTest` −25 net, `PathTraversalGeneratorTest` −19 net |
| Sourcery reviewed this PR | **corroborated** | `ci pr reviews --pr-number 178` returns both `coderabbitai` and `sourcery-ai` |

## Deliverable Fidelity vs Spec

The spec declared **7** deliverables; the solution outline resolved them into **6**. The plan
reported the reconciliation itself and it holds: spec 3+4 folded into outline 3, spec 5 and 6
split across outline 4 and 5, spec 7 became outline 6. All seven spec deliverables are covered.
The count differs; the scope does not.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Fix mislabeled/duplicated `ModSecurityCRSAttackDatabase` entries (TQ-1/2/3) | shipped-as-specified | `ModSecurityCRSAttackDatabase.java` +88/−… ; `CRS_931100_PROTOCOL_HANDLER` renamed rather than given a fabricated payload, exactly as the spec directed |
| 2. Remove flaky wall-clock assertion (TQ-4) | shipped-as-specified | `EdgeCaseValidURLsDatabaseTest.shouldProcessEdgeCasesEfficiently` removed; `ResilientHttpAdapterTest.java` +21 carries the monotonic-clock retry test |
| 3. Strengthen weak attack assertions (TQ-5) | shipped-as-specified | `URLParameterValidationPipelineTest` +144, `URLPathValidationPipelineTest` +88 |
| 4. Assert returned VALUE on valid input (TQ-6) | shipped-as-specified | folded into the same two pipeline test classes |
| 5. Close character-stage and database coverage gaps (TQ-7/8) | shipped-as-specified | `CharacterValidationStageTest` +81, `IPv6AttackDatabaseTest` +156, `HomographAttackDatabaseTest` +141 |
| 6. Document CVE short-circuit; drop cosmetic wrappers (TQ-11/12) | shipped-as-specified | `ApacheCVEAttackDatabase` +15, `NginxCVEAttackDatabase` +16; ADR-0009 records the structural-verification decision |
| 7. Correct false NFKC claim + add executable invariant | shipped-as-specified | `NfkcFoldClaimInvariantTest.java` (new, +123); `UnicodeNormalizationAttackTest` +32; ADR-0010 records the registry |

**Added-unplanned:** `AttackDatabaseEntries.java` (shared helper, not a test class) and the two
ADRs. Neither is a scope escape — both are direct enablers of declared deliverables.

## Surface: realized vs declared

⛔ **The realized footprint EXCEEDED the declared `## Expected Surface`, and the gate did not
see it.** Four touched paths were never declared:

| Touched path | Declared? |
|---|---|
| `test/…/security/generators/encoding/PathTraversalGeneratorTest.java` | **no** — belongs to PLAN-11/PLAN-12's declared tree |
| `test/…/security/generators/…/UnicodeNormalizationAttackGenerator.java` | **no** — same tree |
| `test/…/security/tests/HomographAttackDatabaseTest.java` | **no** — the spec named only `EdgeCaseValidURLsDatabaseTest` and `UnicodeNormalizationAttackTest` under `security/tests/`, not the directory |
| `test/…/security/tests/IPv6AttackDatabaseTest.java`, `NfkcFoldClaimInvariantTest.java`, `AttackDatabaseEntries.java` | **no** — same reason |

**No collision resulted**, and that is luck rather than design: PLAN-11 and PLAN-12 own the
generators tree and both shipped before this plan started, so the undeclared overlap had no
live counterparty. Against a live plan it would have been an unpredicted collision.

Verified disjoint from the two concurrently-running plans:

- **vs PLAN-04** — PR #178 touched no main-tree `security/**` file and never opened
  `HeaderAndContentTypeEnforcementRegressionTest.java`. Clean.
- **vs PLAN-10** — the only `client/` path touched is
  `client/adapter/ResilientHttpAdapterTest.java`, which PLAN-10 explicitly excludes and PLAN-13
  declares. The exclusion authored during the `next` round did its job. Clean.

## Metrics and Anomalies

- **Tokens:** 4,176,800 total. `6-finalize` alone consumed **2,642,172 — 63% of the whole plan**,
  against 569,786 for `5-execute`. Finalize cost 4.6× execution.
- **Duration:** 9h25m wall, 2h19m worked. `6-finalize` was 5h13m wall / 58m47s worked.
- **Steps:** all 19 finalize steps reported `done`; no `loop_back`, no skipped step.
- **Anomaly — the finalize/execute cost inversion is the signal here.** The three defects below
  were all found *after* execute, in review, so the remediation loop ran entirely inside
  finalize. This is the shape of a plan whose own gates did not catch its own defect class.

## Routing and Merge Behavior

- **Review:** CodeRabbit and Sourcery both reviewed. CodeRabbit caught **both** self-inflicted
  vanishing-assertion defects and made **one refuted suggestion** (the ADR ownership claim above),
  which the plan rejected with symbol-level evidence — correctly, as verified.
- **Static analysis:** 5 SonarCloud `java:S3457` + 5 CodeQL findings, all from **one** root cause
  (a `.formatted()` precedence bug). All remediated in-run.
- **CI/merge:** all green; merged via merge queue as `30edfa3`. No rebase conflicts, no re-verify
  signal, no collision with either concurrently-running plan.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-13 --status shipped`
- [x] row `pr` stamped `178`
- [x] row `landing` stamped `landings/PLAN-13.md`
- [x] row `plan_marshall_plan_id` stamped `attack-database-and-assertion-quality`
- [x] epic.md queue reconciled from status.json; START-HERE and Ordered Queue regenerated
- [x] **PLAN-16 landing-facts defect NARROWED** — its re-check trigger was "the next landing; if
      it also lacks the block, the emitter is broken, not the plan." This landing carries a
      complete block, so **the emitter is not broken** and PLAN-16 stands as an isolated
      regression. Its own figures remain unrecoverable.
- [x] **Sourcery defect RESOLVED** — its re-check trigger was "first plan landing after
      2026-08-30 noon." Sourcery reviewed PR #178. The 7-day budget has recovered.
- [x] **New Open Defect** — the two production security findings (below), unowned.
- [x] **New Open Defect** — PLAN-13's under-declared surface, as evidence for the gate's
      residual-error record.
- [x] **Watch retired** — test-class inventory drift is now a concrete, named list for PLAN-15.
- [x] resume_anchor updated

## Follow-Ups

1. ⛔ **Two production security findings, corroborated, unowned.** WS-01 has no staged plan and
   PLAN-04 is running, so neither could be folded. Recorded as an Open Defect; needs an operator
   decision on whether `failOnSuspiciousPatterns=false` is intended design (the field's own
   Javadoc says "suspicious (**non-attack**) patterns", which argues it is) before a spec is
   staged. The `..;` traversal gap has no such defence.
2. **Test-class inventory drift → PLAN-15 (TQ-9/DOC-8).** Added: `NfkcFoldClaimInvariantTest`,
   `AttackDatabaseEntries`. Removed methods: `EdgeCaseValidURLsDatabaseTest.shouldProcessEdgeCasesEfficiently`,
   `URLPathValidationPipelineTest.shouldRejectPathTraversalAttacks`,
   `PathTraversalGeneratorTest.shouldNfkcFoldUnicodeSignaturesToAsciiTraversal`. PLAN-15 has
   already shipped, so this needs a new spec or an operator decision to let the inventory drift.
3. **ADR-0009 and ADR-0010 are `Proposed`** — both await acceptance. Operator call.
4. **Four candidate lessons** dispositioned individually — see the epic decision log.
