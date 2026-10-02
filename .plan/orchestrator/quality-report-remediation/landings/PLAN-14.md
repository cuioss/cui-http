# Landing Analysis: PLAN-14 — Build Artifacts and CI Safety

epic: quality-report-remediation
workstream: WS-07-build-ci-benchmarking
pr: #208 — https://github.com/cuioss/cui-http/pull/208

> Landing record for one shipped plan. Lives at `landings/PLAN-14.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

## Ground truth corroborated before anything was recorded

| Claim | Verdict | Evidence |
|---|---|---|
| PR #208 merged, squashed as `73afd22` | corroborated | `ci pr view --pr-number 208` → `state: merged`, `merge_commit_sha: 73afd22ec4df2cb7ae2c242a0d26532e8af6520c`; `git log` confirms it as HEAD |
| Six deliverables shipped | corroborated | `landing-facts` `deliverables_total=6 deliverables_done=6`; the diff touches every file each deliverable names |
| Landing carries every required fact | corroborated | `inbox landing-check --message build-artifacts-and-ci-safety-006.md` → `complete: true`, `missing_keys[0]` |
| Scope grew to CI-egress hardening | corroborated | `.github/workflows/benchmark.yml` is in the diff, and a new ADR records the principle |
| **"plus ADR-0016 recording the principle"** | ⛔ **CONTRADICTED** | ADR-**0016** was already taken by PLAN-01, which landed first at `7a0da52`. See § The ADR-0016 collision below |
| `.plan/marshal.json` dirty | corroborated | `git status --porcelain` → ` M .plan/marshal.json`. Operator's own knob edit |
| 16h10m wall / 2h39m worked / 4.5M tokens | corroborated | `landing-facts` `total_wall_seconds=58209` (16h10m), `total_tokens=4504876` |
| "7874-test suite ran whole-tree" | unverifiable at this tier | The orchestrator does not run builds. Consistent with CI green on a merged PR |

## ⛔ The ADR-0016 collision — the epic's first real concurrency failure

**Two ADRs now share the number 0016 on `main`:**

```text
doc/adr/0016-Absolute-path_dot-dot_walking_is_rejected_rather_than_clamped_at_root.adoc      (PLAN-01, 7a0da52)
doc/adr/0016-CI_harden-runner_egress_allowlists_enumerate_hosts_never_wildcard_never_runtime-derived.adoc  (PLAN-14, 73afd22)
```

**Neither plan noticed.** PLAN-14's landing message states "plus ADR-0016 recording the
principle" as though the number were free; PLAN-01's `adr-propose` had already allocated it in a
sibling worktree. Both plans were running concurrently, in separate worktrees, and each scanned
its own `doc/adr/` at a moment when 0016 was unclaimed.

**A second, compounding defect:** `doc/adr/README.md` was updated by PLAN-01 (`7a0da52`) and
**not** by PLAN-14 — it is absent from `73afd22`'s file list. A grep for `harden-runner` or
`egress` in the index returns nothing. So the index lists one ADR-0016 and is silent about the
other.

### Why the disjointness gate could not have caught this

This is the instructive part, and it inverts the epic's decompose-time assumption. The epic.md
queue annotation predicted an ADR collision — but named PLAN-01, PLAN-06, PLAN-08 and PLAN-09 as
the risk, because those four *declare* `doc/adr/` as a conditional `HYPOTHESIS` entry. **PLAN-14
declared no ADR surface at all**, and wrote one anyway.

The gate did exactly what it was built to do and still missed it, because
`adr-propose` is a **standard finalize step that runs on every plan** — so *any* plan can write
to `doc/adr/`, whether or not its spec says so. The declaration model treated ADR authorship as a
per-plan conditional; the finalize order makes it universal.

### The surface reconciliation makes the point twice over

| Declared entry | Realized? |
|---|---|
| `cui-http-benchmarking/scripts/benchmark-pages.py` | yes |
| `.github/workflows/benchmark.yml` | yes |
| `cui-http-core/pom.xml` | yes |
| `cui-http-benchmarking/pom.xml` | yes |
| `.../standard/ValidationStageBenchmark.java` | yes |
| `.../standard/PipelineThroughputBenchmark.java` | yes |
| `.../SecurityBenchmarkState.java` | yes |
| `cui-http-core/src/main/java/module-info.java` (HYPOTHESIS) | **no** — over-declaration |

**Realized but never declared:** `doc/adr/0016-CI_harden-runner_….adoc`.

So in one landing the gate **serialized PLAN-15 behind a file PLAN-14 never touched**
(`module-info.java` was PLAN-14's only overlap row in the whole corpus) **and missed the one file
it actually wrote**. Both residual classes the standard names, in the same plan, pulling in
opposite directions — the textbook "wrong in both directions is uninformative" case.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. `assemble` fails when a results directory is absent | shipped-as-specified, **without the required test** | `benchmark-pages.py` in the diff. The spec's Execution Constraints mandated TDD here; the operator directed dropping the Python test file and re-expressing verification as direct command invocation against a pre-change baseline |
| 2. Deploy gated on a non-empty tree + mandatory history fetch | shipped-as-specified | `benchmark.yml`. Closes **both** triggers the epic's verification pass identified — the guard and the `continue-on-error` fetch |
| 3. Token narrowed to `cuioss.github.io` only | shipped-as-specified | `benchmark.yml` |
| 4. Classifier-jar exclude coverage + own module name | shipped-as-specified | `cui-http-core/pom.xml` |
| 5. Unused Lombok removed | shipped-as-specified | `cui-http-benchmarking/pom.xml` |
| 6. Benchmarks exercise real decode/normalise; duplicate removed | shipped-as-specified | `SecurityBenchmarkState.java`, `ValidationStageBenchmark.java`, `PipelineThroughputBenchmark.java` |
| — | **added-unplanned** | CI-egress hardening of `benchmark.yml` (harden-runner `audit` → `block` with 20 enumerated Actions blob hosts), plus its ADR. Operator-directed in-plan after the security audit |

## Metrics and Anomalies

- Tokens: 4 504 876 — **1.7× PLAN-01's**, for a smaller change
- Duration: 58 209 s wall (16 h 10 m) against 2 h 39 m worked
- **The wall/worked gap is almost entirely two 91-minute CodeRabbit quota waits** — the *same*
  root cause the epic already filed as lesson `2026-09-06-07-001` from PLAN-01's landing. Second
  occurrence, second plan, ~3 h lost here on top of PLAN-01's ~6.5 h.
- Three review cycles were needed. Bot coverage of the final head was **positively verified by
  commit range each time, never inferred from silence** — the correct discipline, and the counter-
  example to the review-participation defect filed as `-003`.
- **Two named verification arms could not run**: neither `module-tests` nor `test-compile`
  resolves as a canonical command in this project, and both were recorded **UNGATED, not passed**.
  Substantive coverage existed via `verify -Ppre-commit`, but the manifest advertises arms the
  project cannot deliver.

## Routing and Merge Behavior

- **Review**: 12 comments; CodeRabbit verified fresh per head. It caught a genuine security
  regression in the plan's own hardening fix — the first version admitted **any** Azure Storage
  account via a `*.blob.core.windows.net` wildcard. The second version enumerates 20 hosts.
- **This is the second consecutive landing where the review caught a defect introduced by the
  plan's own fix**, not by the original code. PLAN-01: a fix reintroduced the asymmetry it existed
  to remove. PLAN-14: a hardening fix opened a wildcard. Both were found only because a further
  review round ran.
- **Two CodeRabbit findings rested on factual errors and were caught before acting** — it
  mislocated `original-jmh-result.json` (implementing it literally would have produced a gate that
  fails every legitimate run) and wrongly asserted the endpoint block was empty. Verified against
  live artifacts first. Filed as candidate-lesson `-001`.
- **Security audit**: 1 finding, operator-directed fix applied (the egress policy).
- **Sonar**: 0 new-code issues. **CI**: green across 3 heads. **Merge**: via queue as `73afd22`.
- **No rebase conflict with PLAN-01** despite both landing in the same window — their code
  surfaces were genuinely disjoint. The collision was in a surface neither declared.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-14 --status shipped`
- [x] row `pr` stamped `#208`
- [x] row `landing` stamped `landings/PLAN-14.md`
- [x] row `plan_marshall_plan_id` stamped `build-artifacts-and-ci-safety`
- [x] inbox drained — 6 messages, each with a recorded disposition, each archived
- [x] ADR-0016 collision folded into PLAN-13 with its surface already covering `doc/adr/`
- [x] ADR-allocation constraint added to every remaining spec
- [x] Open Defects opened; the quota-wait lesson reinforced with its second occurrence
- [x] epic.md queue reconciled from status.json; both generated blocks regenerated
- [x] resume_anchor updated

## Follow-Ups

1. **Renumber one ADR-0016 and repair the index.** → **PLAN-13**, which already owns
   `doc/adr/` and `F-documentation-12` (stale/orphaned index). Recorded as an Open Defect because
   it sits on `main` for the rest of the epic.
2. **Prevent recurrence.** Every remaining spec now carries a constraint: if `adr-propose` fires,
   allocate the number against `origin/main` at write time — never against the local worktree —
   and name the ADR in the plan's inbox message. This is cheaper and more accurate than declaring
   `doc/adr/` in all 12 remaining specs, which would serialize the entire epic on one directory.
3. **The egress allowlist has never executed.** The benchmark workflow fires only on merged PRs
   and tags. The first post-merge benchmark run is the validation point; a blocked call names its
   host in the harden-runner summary. → **Watch**, with the explicit instruction not to revert to
   `audit`.
4. **Two review findings accepted as out of scope**: no drift check on the enumerated endpoint
   list, and the required-artifact list duplicated between `benchmark-pages.py` and
   `benchmark.yml` (unifying it needs the out-of-repo `benchmarking-common` producer). → **Watch**.
5. **The deploy guard ships with no regression test**, by operator decision. → recorded, not
   re-litigated.
6. **`module-tests` / `test-compile` are advertised but unresolvable.** → candidate-lesson `-005`
   promoted; affects every plan in this epic.
