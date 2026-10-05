# Landing Analysis: PLAN-01 — Decoding and Normalisation Hardening

epic: quality-report-remediation
workstream: WS-01-security-validation-core
pr: #210 — https://github.com/cuioss/cui-http/pull/210

> Landing record for one shipped plan. Lives at `landings/PLAN-01.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

## Ground truth corroborated before anything was recorded

| Claim (from paste and inbox message `-007`) | Verdict | Evidence |
|---|---|---|
| PR #210 merged | corroborated | `ci pr view --pr-number 210` → `state: merged`, `merge_commit_sha: 7a0da52225e33071b19a08504cc68eb73b7807fd` |
| `main` at `7a0da52` | corroborated | `git log --oneline -3` → `7a0da52 fix(security): decode-aware double-encoding, delimiter and traversal checks (#210)` |
| ADR-0016 supersedes ADR-0014; ADR-0017 created | corroborated | Both files present in `doc/adr/`; both appear in the `7a0da52` file list alongside an updated `doc/adr/README.md` |
| Six deliverables, all shipped | corroborated | `landing-facts` reports `deliverables_total=6 deliverables_done=6`; the commit body enumerates all six and the diff touches the stages each names |
| Landing carries every required machine-readable fact | corroborated | `inbox landing-check --message decoding-normalisation-hardening-007.md` → `complete: true`, `missing_keys[0]` |
| `.plan/marshal.json` dirty | corroborated | `git status --porcelain` → ` M .plan/marshal.json`. Operator-known knob; not a defect |
| "the landing plus six candidate-lessons" (paste) | **contradicted, immaterial** | `inbox list` reports 7 messages: 1 `finding` + 5 `candidate-lesson` + 1 `landing`. Message `-001` is a `finding`, not a candidate-lesson. Corrected here; changes no disposition |
| "7900 tests green" | unverifiable at this tier | The orchestrator does not run builds (prime directive). The claim is recorded as reported and is consistent with CI green on a merged PR |

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Decide double encoding on the decoded output, not a wire-form regex | shipped-as-specified | `DecodingStage.java` — new `SURVIVING_ENCODING_PATTERN` inspects the decoded result; commit body §1 |
| 2. Run the decoded-character checks on the normalised string | shipped-as-specified | `DecodingStage.java` — check re-runs against the normalised form; `;` added to `STRUCTURAL_CHARS`; commit body §2 |
| 3. Real UTF-8 decoding that reports malformed input | shipped-as-specified | `DecodingStage.java` — denylist + `URLDecoder` replaced by a hand-rolled percent-decode to `byte[]` plus a `CodingErrorAction.REPORT` decoder; commit body §3 |
| 4. Treat a segment starting with `..` as traversal intent; reject decoded `?`/`#` | shipped-as-specified, **with an ADR** | `NormalizationStage.java`; ADR-0016 supersedes ADR-0014. The spec's HYPOTHESIS predicted exactly this: ADR-0014's Alternatives section had explicitly rejected the option this plan adopts, so the conditional ADR path fired |
| 5. Close the raw-versus-encoded asymmetries | shipped-modified — **scope reduced, consequence filed** | `DecodingStage.java`: two gates became unconditional. `SecurityDefaults.java` was on the plan's do-not-touch list, so the two now-stale `LENIENT_CONFIGURATION` Javadoc claims were filed as inbox `-001` rather than fixed. Recorded below as a fold into PLAN-03 |
| 6. Sweep the tests that pinned the superseded verdicts | shipped-as-specified | `DecodingStageTest.java`, `NormalizationStageTest.java`, and a new `PostDecodeEnforcementRegressionTest.java` |

**Both verify-first clauses were discharged at outline time rather than deferred** — the spec's
clause on U+FFFD consumers was settled by a 584-file sweep finding none, and the HYPOTHESIS on
ADR-0014 was settled by reading the ADR. That is the contract working as written: neither was
carried into execution as an unchecked premise.

## Metrics and Anomalies

- Tokens: 2 699 830 (`total_tokens`)
- Duration: 51 180 s wall (≈14 h 13 m), the great majority of it spent blocked on a review bot
- Finalize steps: 18 of 18 recorded `done`
- Anomalies, in descending order of importance:
  1. **≈6.5 h of finalize wall-clock lost to a rate limit that had already lifted.** PR #209 was
     closed and reissued as #210 for no benefit; what unblocked it was re-requesting the review
     once the hourly window reset. Root cause filed as candidate-lesson `-002`.
  2. **The structural self-review never ran.** `pre-submission-self-review` recorded `done` with
     `verdict: not_run` — no `ext-self-review-{domain}` implementor resolves for Java. The
     `landing-facts` block carries this honestly as `step.pre-submission-self-review.verdict=not_run`,
     which is why it is visible here at all.
  3. **No scope-creep measurement was taken.** The guard resolved `no_baseline_sha` on every task
     because `references.json.plan_creation_sha` has a reader and no writer anywhere in the
     bundle. The committed file set was verified by hand instead.
  4. Two finalize steps paid a full re-dispatch (`finalize-step-simplify`, `adr-propose`) because
     their step bodies instruct actions a dispatched leaf cannot take.

## Routing and Merge Behavior

- **Review**: 2 rounds, 6 findings, confirmed clean on the third pass. Two were **genuine
  bypasses the plan's own tests missed**: `Character.digit` accepting fullwidth digits, so
  `%` + U+FF12 U+FF26 decoded to `/`; and the NFKC fold assembling `%2F` *after* the
  surviving-encoding check ran.
- **The review's most valuable catch was on the plan's own fix.** Round 1's patch read the value
  *selected for return*, whose selection is config-dependent — so under `lenient()` the gate read
  `decoded` and never saw the `%2F` living in `normalized`. That reintroduced the exact
  raw-versus-encoded asymmetry the plan existed to remove, and it was externally reachable on
  `URL_PATH` (CWE-177 class). Round 2 caught it; round 3 confirmed clean.
- **Security audit**: 2 CRLF log-injection findings (CWE-117 / CWE-93) in the plan's own new
  code, fixed before push.
- **Sonar**: 0 new-code issues.
- **CI/merge**: green; merged through the queue as `7a0da52`. No rebase conflicts, no re-verify
  signals, and no collision with the concurrently-running PLAN-14.

## Surface reconciliation — one under-declaration, no live collision

The realized file set was compared against the spec's declared `## Expected Surface`:

| Declared entry | Realized? |
|---|---|
| `.../validation/DecodingStage.java` | yes |
| `.../validation/NormalizationStage.java` | yes |
| `.../validation/PatternMatchingStage.java` | **no** — over-declaration |
| `.../config/SecurityDefaults.java` | **no** — over-declaration (do-not-touch list; see deliverable 5) |
| `.../test/.../validation/DecodingStageTest.java` | yes |
| `.../test/.../validation/NormalizationStageTest.java` | yes |
| `.../test/.../security/tests/` | **no** — over-declaration |
| `doc/adr/` | yes (0016, 0017, README.md) |

**Realized but never declared — one entry:**
`cui-http-core/src/test/java/de/cuioss/http/security/pipeline/PostDecodeEnforcementRegressionTest.java`

This is the standard's dominant residual class (under-declaration) in its mildest form: one new
file, in a directory **PLAN-04 already declares**. It caused **no live collision** — PLAN-14 was
the only concurrent plan and its surface is the benchmark module, the POMs and the workflows.
PLAN-04 inherits the file, which is correct, since that directory is its declared surface.

Per the standard, a collision the gate did not predict is evidence a declared surface was wrong.
Here the gate predicted no collision and none occurred, so nothing is corrected in PLAN-04.
PLAN-01's own spec is not amended — it is shipped, and amending a landed spec would rewrite
history rather than inform a future gate decision. The under-declaration is recorded here and as
a Watch, because the same authoring habit will recur in PLAN-02 and PLAN-03, which share this
plan's author-facing shape.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-01 --status shipped`
- [x] row `pr` stamped `#210`
- [x] row `landing` stamped `landings/PLAN-01.md`
- [x] row `plan_marshall_plan_id` stamped `decoding-normalisation-hardening`
- [x] inbox drained — 7 messages, each with a recorded disposition, each archived
- [x] PLAN-03 spec amended: inbox `-001` folded in, `## Expected Surface` widened in the same act
- [x] PLAN-02 / PLAN-03 / PLAN-04 / PLAN-05 given the both-presets regression constraint
- [x] two Open Defects opened (self-review gap, inert scope-creep guard)
- [x] two Watches opened (surface under-declaration habit; WS-05 re-baselining)
- [x] five candidate-lessons promoted to the global lessons corpus
- [x] epic.md queue reconciled from status.json
- [x] resume_anchor updated
- [x] START-HERE and Ordered Queue blocks regenerated

## Follow-Ups

1. **`allowDoubleEncoding` is now inert** — read by no production code after deliverable 5, but
   still a published record component and builder setter, and still read by `isStrict()` /
   `isLenient()`. Its fate (retain inert / deprecate / remove next major) is a public-API decision
   governed by ADR-0008. → **folded into PLAN-03**, whose `## Expected Surface` gained
   `SecurityConfiguration.java` in the same act.
2. **Two stale `LENIENT_CONFIGURATION` Javadoc claims in `SecurityDefaults.java`** — both gates
   are now unconditional, so the preset no longer relaxes them. → **folded into PLAN-03** (that
   spec already declares `SecurityDefaults.java`).
3. **`doc/http-security/configuration.adoc` and `doc/forwarded-header-resolution.adoc` reference
   `allowDoubleEncoding`** and were never inspected for the same staleness. → **PLAN-13**, whose
   `doc/` declaration already covers both; recorded in its spec.
4. **The asymmetry-reintroduction lesson is operational for the rest of WS-01 and WS-02.** Every
   remaining validation plan is fixing a defect *class*, which is exactly where a review fix
   silently restores it. → promoted to the lessons corpus AND written into PLAN-02, PLAN-03,
   PLAN-04 and PLAN-05 as a hard execution constraint: any gate touched during review must be
   pinned by a regression running the same input under **both** `defaults()` and `lenient()`,
   asserting the same failure type.
5. **PLAN-10 / PLAN-11 need re-baselining.** This plan changed verdicts their tests assert.
   Deliverable 6 fixed the mechanical breaks it found in-module; the message states no
   premise-changing break was found. → Watch, not a defect: WS-05 is already sequenced after
   WS-01 and WS-02 for exactly this reason, and its specs already carry re-read-at-outline
   clauses.
