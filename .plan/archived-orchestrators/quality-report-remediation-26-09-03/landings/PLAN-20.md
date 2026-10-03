# Landing Analysis: PLAN-20 — Test-Evidence Integrity

epic: quality-report-remediation
workstream: WS-04
pr: 189 (merged via queue as `821cd34`)

> Landing record for one shipped plan. Every claim below was corroborated against ground truth
> before it was recorded.

## Corroboration Summary

⛔ `inbox landing-check` on `test-evidence-integrity-009.md`: **`complete: false`**, entire required
set of 8 keys missing — **no `landing-facts` block at all**. Third prose-only landing against four
complete ones (PLAN-13, PLAN-04, PLAN-19, PLAN-18 complete; PLAN-16, PLAN-10, PLAN-20 prose-only).
The emitter remains **intermittent**; every fact below was recovered from prose plus corroboration.

| Claim | Verdict | Evidence |
|---|---|---|
| PR #189 merged as `821cd34` | **corroborated** | `git log origin/main` head |
| ⛔ **`production_code_changed: false`** | **corroborated** | `git diff --name-only 1c14ec1..821cd34 \| grep src/main/` returns **nothing** — all 14 files are tests. The spec's hardest constraint held |
| The project's first `useHttps = true` test exists | **corroborated** | `HttpHandlerHttpsIntegrationTest.java`:70 carries `@EnableMockWebServer(useHttps = true)`; it is the only such annotation in the tree |
| Deliverable 1's premise was false | **corroborated — and independently pre-verified** | The orchestrator reached the same bytecode conclusion on 2026-09-02 before the plan launched, and re-scoped the spec accordingly. The plan then **confirmed it empirically with a real TLS handshake and a surefire `-output.txt` read** — the step the orchestrator could not take |
| ⛔ **The `marshal.json` bot-name mismatch is still open** | **corroborated** | `.plan/marshal.json`:103 still reads `"required_bots": "coderabbit,pr-agent"`. Fixed plan-locally only, exactly as the landing states |
| Falsification ledger: 25/25 + 6 + 1 | accepted as reported | not independently re-run |
| 7,781 tests green | accepted as reported | |

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| 1. Confirm the misalignment is gone | ⛔ **DROPPED on a refuted premise — and the spec authorized it** | The re-scoped clause read: *"if the failure does not reproduce… re-scope to deliverables 2, 4 and 5 and record that 1 was unnecessary."* The plan did exactly that, with a positive account rather than a silent skip |
| 2. TLS coverage | shipped | `HttpHandlerHttpsIntegrationTest` (+152, new); downgrade refusal asserted end-to-end |
| 3. Sweep, triage, convert | shipped **+ operator-approved expansion** | sweep re-derived at HEAD |
| 4. Falsification | shipped, **with an honest shortfall** | see below |
| 5. Gap-note reconciliation | shipped | 5 test classes' notes corrected or retired |

**The re-scope worked exactly as designed.** The orchestrator's 2026-09-02 re-check inverted the
verify-first clause from *"reproduce the failure"* to *"run the test expecting a PASS"*; the plan ran
it, it passed, and deliverable 1 closed as verified-unnecessary. ⚠️ The plan also refuted a **second**
inherited claim the orchestrator had not checked: the gap notes said a downgrade test needed a second
TLS-terminated server. It does not — **the refusal is decided before the next hop is contacted**, so
one server suffices.

⛔ **The gap note was false when it was written.** `cui-test-mockwebserver-junit5` 1.6 arrived with
`cui-java-parent` 1.5.10 (`7807a00`, 2026-08-29); the note describing the failure was written at
`6ee0c7c` on **2026-08-30** — one day later — and was then carried forward untested through two
further plans. This is the epic's own *verify-first* rule failing at the plan tier: a claim written
once was inherited three times without re-derivation.

## ⛔ The plan reproduced its own target defect

The plan existed to remove guards whose predicate is a broad text filter. Its remediation added six
**admission counters** — and CodeRabbit rated it **Major** (`bde6fe`): a counter incremented by a
broad text filter proves only that *the filter matched*, not that the input belonged to the named
attack family. A CL.TE payload passed the TE.CL guard; a lone `Transfer-Encoding` header passed the
TE.TE guard.

⚠️ **The plan's own spec contained the sentence that would have caught it** — *"a matched flag makes
the skip visible, but it does not make a wrong guard right"* — and nothing was checking the plan
against its own rule. Fixed in `9f26273` (each guard now fingerprints header order, multiplicity, or
a family-unique header), falsified, confirmed on-thread.

**This is the third instance in this epic of a remediation reproducing its target class** (ADR
overclaim in PLAN-15, vanishing assertions in PLAN-13, this). It is no longer an anecdote.

## What the sweep found, versus what I seeded

⚠️ **My seed under-collected by more than 3×.** The orchestrator sweep that staged this plan found
**12 candidate blocks across 8 files**; the plan's re-derived sweep found **41 blocks across 15**.
Worse, an entire guard **form** was invisible to my heuristic — early-return filter guards
(`if (...) return;`), where no assertion sits inside the conditional. Six genuine sites in
`HttpRequestSmugglingAttackTest` were found only by a separate `return; // Skip` search, surfaced as
Q-Gate finding `adea8c` and expanded in-plan with operator approval.

**The spec's own framing saved this**: it said the 12 sites were *"a seed, not a verdict"* and *"the
line numbers are provenance, not the work list"*, and required the sweep be re-derived at HEAD. Had
it shipped the seed as the work list, six sites would have gone unremediated behind a green sweep.

⛔ **`UnicodeNormalizationAttackTest.shouldRejectNormalizationChangingForms` was 100% vacuous** —
both cases were NFC-invariant, so the loop body **never executed once**, and the in-code comment
claiming otherwise was wrong. I had flagged `:240` as a genuine instance; the reality was worse than
the diagnosis.

## Falsification — reported honestly, including its limit

**25 sites: 25/25 failed when perturbed, 25/25 passed when restored**, each failure quoting its own
stated reason; plus 6 for the expansion and 1 for the Sonar fix.

⚠️ **Five corroborative assertions were NOT independently falsifiable and are reported as such rather
than counted as proven.** They fell only *collectively* to one perturbation (`useHttps = false` →
`ParameterResolutionException`), which proves the class is genuinely TLS-bound but does not exercise
each assertion's own message. Isolating them would have required a production change, which the spec
forbade. **Declaring the shortfall instead of rounding it into the 25 is the correct handling.**

## Metrics and Anomalies

- 3h33m worked / 7h29m wall / 3.6M tokens. Final suite **7,781 tests** green.
- ⛔ **~2h and several hundred thousand tokens lost to the bot-name mismatch** — chasing a review that
  already existed.
- **Tightening the guards dropped admission to ~1/15 of generator output**, giving a reproduced
  **15–20% chance of zero admissions per run** at the existing sample counts. Counts raised to 200.
  A fail-closed zero-admission check over an unadjusted sample is a coin, not a detector.
- Two measurements **absent, not clean**: `scope_creep_check` never ran (no `plan_creation_sha` —
  fourth consecutive plan), and `pre-commit-verify-freshness` passed with
  `scope_cross_check: undetermined`.

## Routing and Merge Behavior

- **Merge authorization granted, HEAD-bound.** Both required bots reviewed the substantive diff at
  `9f26273` with no findings; neither re-confirmed the one-statement `8ede589` delta (CodeRabbit
  rate-limited). Sourcery **approved** at that HEAD and the queue re-tested against `main`. Recorded
  as `barrier-ask-override` / `review-barrier-gap`. Proportionate and properly recorded — and the
  **third consecutive plan** to merge with a disclosed review-coverage boundary.
- Sonar: 1 new-code issue (`java:S5778`) fixed at source and falsified.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` = `189`; `landing` = `landings/PLAN-20.md`
- [x] ⛔ **Lesson `2026-08-29-12-001` CLOSED and removed** — both halves now settled: the root cause was
      already fixed (orchestrator re-check), and the coverage gap is closed by this plan. Consolidated
      into `.plan/project-findings.md`.
- [x] **8 candidate lessons routed into the two findings reports**, not the store — 5 new findings,
      3 folded as recurrences.
- [x] **New Open Defect** — the `marshal.json` bot-name mismatch, unowned and recurring.
- [x] resume_anchor updated; both generated blocks regenerated

## Follow-Ups

1. ⛔ **`marshal.json` still carries `required_bots: pr-agent` while the reviewer posts as
   `cuioss-review-bot`.** A name mismatch resolves the bot to `absent` — a **blocking** state — while
   it has in fact reviewed. **Every future plan in this repo pays the same ~2h.** The repo-side fix is
   one config value; the tooling side needs a distinct `unresolvable_bot_identity` verdict. **Unowned.**
2. **`manage-lessons consult` surfaced 0 with all 23 paths `unmapped_paths`** — its `{bundle}:{skill}`
   derivation is marketplace-shaped and does not resolve Java source paths. ⛔ **That zero is a
   derivation miss, not evidence of no relevant lesson**, and it means the consult verb is inert for
   this project. The three lessons the spec named were consulted by hand.
3. **Adjacent debt untouched as instructed** — the three WS-05 documents, and the `.formatted()`
   lesson confirmed to need no work and correctly not re-opened.
