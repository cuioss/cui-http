# Landing Analysis: PLAN-03 — Exception Sanitisation and Config Hygiene

epic: quality-report-remediation
workstream: WS-01-security-validation-core
pr: #222 (https://github.com/cuioss/cui-http/pull/222) — merged 2026-09-08T13:37:42Z as `7c7235b`

> Corroborated before writing: `gh pr view 222` (MERGED), `git show --stat 7c7235b` (4 files),
> the archived plan tree at
> `.plan/local/archived-plans/2026-09-08-exception-sanitisation-and-config-hygiene/`, and
> `gh pr view 224` (OPEN — the config PR this plan spun off).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. `detail` sanitised like `originalInput` | shipped-as-specified | `UrlSecurityException.buildMessage` routes `detail` through a new `escapeAndBound`; `AllowBlockListStage` renders through `renderForDetail` instead of splicing raw. Closes F-B-5, L-4 and F-A-5 together, exactly as the spec predicted |
| 2. Sanitiser covers every line-forging character | shipped-modified | `CONTROL_CHARS_PATTERN` widened to cover C1 (U+0085 NEL) and U+2028 / U+2029. **Modified:** shipped incomplete and was corrected on external review — see below |
| 3. Exception messages stop reproducing credential material | shipped-as-specified | Both rendering paths emit `(input: <redacted, length=N>)`, unconditional with no config knob; `getOriginalInput()` / `getSanitizedInput()` keep raw values as a documented opt-in |

Regressions were written first for every change and each was observed red for its stated reason.

## Three deliverables became eleven tasks — and the pattern in the overrun is the finding

Eight extra tasks, of which **five came from the finalize security audit finding the same defect
class one layer deeper on each round**, and one from an external review bot:

| Task | What it caught | Found by |
|---|---|---|
| TASK-7 | `sanitizedInput` still echoed in `toString()` after `originalInput` was redacted | audit |
| TASK-8 | `getMessage` rendered `detail` unbounded while `toString` capped it at 200 — two paths disagreeing about one field | audit |
| TASK-9 | `renderForDetail` built and **stored** the 6×-amplified string unbounded, so the exception retained it whether or not anything rendered it | audit |
| TASK-10 | `toString` rendered `getCause()` raw — the one field the class defended nothing on | audit |
| TASK-11 | The two sanitisers used **different predicates**: `Character.isISOControl` does not cover U+2028 / U+2029, **which was deliverable 2's own target** | CodeRabbit |

⛔ **TASK-11 is the one that matters.** Four internal audit rounds checked each sanitiser against
the attack class in isolation and never checked the two **against each other**, so the plan's own
deliverable 2 shipped incomplete and an external reviewer caught it. This is the second appearance
of that shape in two days — PLAN-02's C0 guard covered header types but not cookie types — and the
two have been consolidated into one lesson (`2026-09-08-06-002`), now generalised from *guards* to
*any sibling pair*: **siblings must be compared to each other, not only to the spec each claims to
satisfy.** A per-artifact check cannot see a divergence between artifacts.

The five audit rounds also produced their own lesson (`2026-09-08-15-001`): a symptom-scoped audit
re-finds the class one layer deeper each round, and only a closure question converges it.

## Surface delta — the best declaration in the epic so far

Declared **3** entries; the merge touched **4** files. One undeclared:

| File | Whose declared surface it belongs to |
|------|--------------------------------------|
| `…/test/…/security/validation/AllowBlockListStageTest.java` | PLAN-11 (`test/…/validation/`); also PLAN-02's, now shipped |

✅ **This reverses the epic's trend for the first time.** 1 file (PLAN-01) → a whole directory
(PLAN-14) → 7 files plus a violated exclusion (PLAN-02) → 3 files into a sibling's surface
(PLAN-06) → **1 file, and an obviously-adjacent test at that** (PLAN-03). Declaring three
tightly-scoped entries and landing four files is what an honest surface looks like. Nothing about
the epic's tooling changed to cause it, so it is evidence about spec authoring, not about the gate.

## The `landing-facts` block is CORRECT here — which sharpens the PLAN-06 defect

| Fact key | Block | `metrics.md` Total | Verdict |
|---|---|---|---|
| `total_tokens` | 13,434,874 | 13,434,874 | ✅ exact |
| `total_wall_seconds` | 45,240 (12h34m) | 12h34m | ✅ exact |

PLAN-06's block published its **6-finalize row** under the same two plan-scoped key names. ⛔ So the
emitter is **not** systematically wrong — it is wrong *sometimes*, which means `complete: true`
carries no assurance in either direction and every drained landing's totals must be checked against
`metrics.md`. That is a worse property than a uniform bug would be.

This block also carries two OPTIONAL keys the others did not — `step.branch-cleanup.merge_sha` and
`step.create-pr.pr_number` — which is exactly the machine-readable reconciliation the epic has been
doing by hand for four landings.

## Metrics and Anomalies

- **Tokens**: 13,434,874 total — **2.6× PLAN-06 and 3× PLAN-02**, and the epic's most expensive
  plan by a wide margin against its smallest diff (4 files, 690 insertions).
- **Distribution is unlike every other plan in the epic**: 6-finalize 11,591,300 (86%) and
  3-outline 1,601,279 (12%) — together 98%. 5-execute took only 202,402. And every phase but
  5-execute is marked **`(inline)`**, meaning it dispatched nothing and burned main-context.
- **Duration**: 12h34m wall, 6h46m idle (n=5/6). 6-finalize 5h47m, 5-execute 5h22m.
- **Tests**: 7,961 → 7,972.
- **Anomalies**:
  - ⛔ **A 690-line change cost 13.4M tokens.** The five-round audit spiral is the visible cause,
    and it is the same spiral lesson `2026-09-08-15-001` describes. Cost per landed line is the
    epic's worst by an order of magnitude.
  - `2-refine` (17s) and `4-plan` (38s) are effectively no-ops, while `3-outline` ran 1h18m inline
    at 1.6M tokens — an unusual shape worth watching if it repeats.
  - `adr-propose` did not run at all (`lane: off`, project config, operator-confirmed), so **no
    ADR was allocated** and the spec's ADR-numbering constraint had no firing site. ⛔ Note this is
    a *different* reason from PLAN-06's `adr-propose:skipped`, which still shipped an ADR written
    during execute. Neither step outcome is evidence about whether an ADR exists.

## Routing and Merge Behavior

- CI green; squash-merged via the platform merge queue.
- **One commit was deliberately not fully gated.** `f8d883b` (documentation-only) was covered by
  the whole-tree quality gate at its SHA, but the operator authorised skipping the re-fire of
  self-review / simplify / security-audit against it, since no executable line moves. Recorded here
  as a **deliberate exception, not a satisfied contract** — which is the right way to carry it.
- **Two plan-marshall tooling defects surfaced and were filed**: `re_review_on_branch_cleanup`
  cannot fire on a merge-queue repo (promoted as `2026-09-08-15-002`), and `derive_gate_bundles`
  has no Maven-module notion so the per-bundle gate selected **zero** bundles on a Java repo and
  the zero read as coverage (folded onto `2026-09-06-07-002` — same blind spot as the
  zero-surfacer self-review, different mechanism).

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#222`; `landing` `landings/PLAN-03.md`;
      `plan_marshall_plan_id` `exception-sanitisation-and-config-hygiene`
- [x] The split state is closed — the row was deliberately held at `running` between the 13:37
      merge and this 15:14 landing message, because code on `main` is not a landing analysis
- [x] epic.md reconciled; START-HERE and Ordered Queue regenerated
- [x] PLAN-03 `## Expected Surface` annotated with the one realized extra
- [x] **Open Defect sharpened** — the `landing-facts` emitter is intermittently wrong, not
      uniformly wrong
- [x] Five candidate-lessons dispositioned: three promoted, two folded
- [x] resume_anchor updated

## Follow-Ups

- **Five findings remain open by decision**, all `improvement` / `info`, and two of them name the
  same remedy: `049410` and `438902` — the `U+XXXX` escaping shape and the bound constants are
  duplicated across four classes in two packages, and **a non-exported internal package would
  retire both**. Also `8c7dff` (NEL / LINE SEPARATOR tests collapsible), `df8660`, and `f43a2c`
  (`UrlSecurityExceptionTest` is 500 lines against a 400-line budget, with a proposed split seam).
  → not owned by any staged spec; candidates for a PLAN-12 or PLAN-15 fold if either touches these
  files, otherwise they lapse. Recorded here so they are not silently dropped.
- ✅ **The `marshal.json` `file_globs` gap PLAN-02 reported is now PR #224 (OPEN).** Domain-narrowing
  had emptied `references.domains` on this plan too; the operator's hand-restore has been lifted
  into that PR, which is why the working tree is clean again. **It is not merged** — until it is,
  every plan in this epic keeps hitting the same zero-domain narrowing.
- **PLAN-11 inherits a changed file**: `AllowBlockListStageTest.java` was edited here undeclared,
  and PLAN-11 declares `test/…/validation/`. Re-read at outline.

---

## Addendum — 2026-09-08, from the operator's finalize report

Received after this record was written and after PLAN-03 was reconciled. No ship semantics
changed; the material below is new detail, not a new landing.

### Finalize shape: 17 steps, and the repetition counts are the story

`pre-push-quality-gate` fired **5 times**, `pre-submission-self-review` **5 times**, `simplify`
ran **5 rounds for 0 edits**, and `security-audit` ran **5 rounds finding 4 defects**. Read
together with the 86%-of-tokens-in-finalize figure above, the cost is not one expensive gate — it
is **five full passes of every gate**, driven by the audit spiral. `simplify` producing 0 edits
across all five is the clearest waste signal in the set: it was re-run four times with nothing to
do, because the loop re-fires the whole finalize bundle rather than the arm that failed.

### ⛔ A third failure mode for the realized-surface side of the gate

`realized_footprint` was left **null**: the worktree was removed before `capture-footprint` ran.
Two recovery attempts then returned **26** and **23** files — both wrong, because `main` kept
advancing underneath (PLAN-06 landed mid-finalize, so a diff against a moving `main` swept in a
sibling's work). It was finally **set by hand to the verified four**, with both wrong attempts
logged.

This is the third distinct way the realized side has failed in this epic, and they are different
faults, not one recurring one:

| Plan | Failure mode | Effect |
|---|---|---|
| PLAN-02 | `affected_files` recorded 14 of 19 | silent under-count |
| PLAN-06 | `affected_files` recorded 11 of 12 | silent under-count |
| PLAN-03 | `realized_footprint` **null**, then 26 and 23 before a hand-set 4 | capture failed outright, and the recovery path is unsafe while `main` moves |

✅ **The hand-set value is correct.** This record's "declared 3, realized 4" figure was derived
independently from `git show --stat 7c7235b` on the merge commit before the addendum arrived, and
it matches the hand-set `realized_footprint` exactly. The conclusion that PLAN-03 authored the
epic's best declaration therefore stands on measured evidence, not on the plan's own report.

⛔ But the general lesson is sharper than a bookkeeping note: **a realized-footprint recovery that
diffs against a moving `main` is not a recovery.** In a parallel epic, `main` advances during
finalize by construction — so the recovery must diff against the plan's own merge-base or its
merge commit, never against current `main`.

### Three self-reported process errors, all corrected in-run

Recorded because a self-reported error is evidence the reporting works, and because two of the
three are mechanical enough to be worth mechanising:

1. A self-review was dispatched **without its required `candidates` field**.
2. A **41-character SHA** was recorded — one character over the 40-character maximum, so a
   length check catches the whole class.
3. The `realized_footprint` sequence above.

### The sharpest observation in the report is about the reviewer's own framing

The plan noticed the two sanitisers' predicates differed **in a round-4 audit report**, and framed
it as *"nothing was weakened"* — answering the **regression** question when the live question was
**consistency**. The difference was seen, described, and dismissed by the framing applied to it.

⛔ This is a better statement of the defect than "the audit missed it", and it has been folded into
lesson `2026-09-08-06-002`: a sibling divergence can survive being *observed*, if the question asked
of it is "did this get worse?" rather than "do these two agree?". A checklist that asks only the
regression question cannot catch this class no matter how many rounds it runs — which is exactly
what five rounds demonstrated.
