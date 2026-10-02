# Landing Analysis: PLAN-08 — Client Cache and Result Correctness

epic: quality-report-remediation
workstream: WS-03
pr: #166 — https://github.com/cuioss/cui-http/pull/166 (MERGED → `f1ba539`; replaced #157, closed unmerged)

## Ground-Truth Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #166 merged → `f1ba539` | **corroborated** | MERGED; on `origin/main`. |
| CL-1 closed with the three-way distinction | **corroborated in the landed source** | `handleNotModified` gates on `canReadCache(method)`: an unsafe method now returns an explicit failure naming the violation — *"HTTP 304 is not a valid response to a %s request (RFC 7232 requires 412)"* — with status 304 preserved. GET/HEAD read the cache; a null entry is reported as an unsolicited 304. **This is exactly the three-way fix the spec's verification NOTE prescribed.** |
| CL-6, CL-13, CL-14, CL-19 closed | **corroborated** | 204/205 exempted; status+ETag preserved on conversion failure; cache invalidated when a fresh 200 cannot replace the entry; cache-key allocation skipped for non-cacheable requests. |
| Tests 5471 → 6453 | **corroborated as reported** | Quality gate green at merge HEAD. |
| 3 bots participated, 0 stale | **corroborated as reported** | ⛔ The first landing in the epic with clean, non-degraded bot participation. |

## Deliverable Fidelity vs Spec

All six spec deliverables shipped. Deliverable 1 (the `TestApiDispatcher` fixture) was added by the
plan and is not in the spec — a necessary enabling change, not scope creep.

| Spec deliverable | Verdict |
|---|---|
| 1. Gate the 304 short-circuit by method, three ways (CL-1) | **shipped-as-specified** |
| 2. Exempt 204/205 only (CL-6) | **shipped-as-specified** — the spec's NOTE restricted this to protocol-defined no-body statuses; an empty 200 stays governed by `emptyContentIsValid()`. |
| 3. Preserve HTTP status on conversion failure (CL-13) | **shipped-as-specified** |
| 4. Fix cache invalidation on an ETag-less 200 (CL-14) | **shipped-as-specified** |
| 5. Reduce cache-key allocation and eviction cost (CL-19) | **shipped-as-specified** |
| 6. Regression tests | **shipped-as-specified** |

## Metrics and Anomalies

- 2,661,899 tokens / 30h49m. 6/6 phases.
- ⛔ **The headline fix nearly shipped UNREACHABLE, and the outline had predicted the exact
  interaction.** Deliverable 6's cache-key optimization made `cachedEntry` always null for unsafe
  methods, which made deliverable 2's method-gated 304 unreachable; the test passed via the generic
  error path **by coincidence**. **Five local gates cleared it. Two review bots caught it.** The
  outline had flagged the D6/D2 interaction as a risk *before* execution and the risk materialised
  anyway. Two conclusions, and they point in opposite directions:
  1. **This is the counter-example to the epic's "bot review is not a reliable gate" pattern.** Three
     earlier landings shipped with degraded review; here bot review caught a defect that five local
     gates and a pre-identified risk note did not. Both observations are now on the record; neither
     cancels the other.
  2. **A predicted risk is not a mitigated one.** The outline named the interaction and execution
     still walked into it — the prediction bought nothing without a test that could falsify it,
     which is precisely what candidate-lesson 004 says.
- **PR #157 was closed unmerged and replaced by #166** — worth knowing when reading PR history.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` 166; `landing`; `plan_marshall_plan_id`
- [x] **WS-03's head cleared** — PLAN-09 and PLAN-10 unblocked
- [x] 5 inbox messages promoted (`2026-08-27-20-001..005`), including the owed architecture hint
- [x] Open Defect — `doc/http-result-pattern.adoc` reconciliation is owed and UNOWNED
- [x] Watch — PATCH/OPTIONS coverage deferred on an external dependency

## Follow-Ups

- ⛔ **`doc/http-result-pattern.adoc` needs reconciling and has NO OWNER.** Conversion-failure results
  now carry status and ETag where both were previously empty. PLAN-08's spec correctly said "do not
  edit it here; report it in the landing so PLAN-15 reconciles the doc" — **but PLAN-15 shipped
  first**, on 2026-08-27, and WS-05 is closed. This is a sequencing miss in the epic's own
  decomposition: a doc plan was allowed to close before the code plan whose output it documents had
  landed. It joins the other unowned doc residue.
- **PATCH/OPTIONS test rows are deferred on an EXTERNAL dependency** — `cui-test-mockwebserver-junit5`
  PR #104 (open, CI green) and its release chain. Production gating already covers those methods via
  the catch-all branch, so **only test coverage is missing, not enforcement**. Finding `36b80c`.
  This is the epic's first dependency on work outside the repository.
