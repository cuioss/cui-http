# Landing Analysis: PLAN-09 — Client Config and Retry Hardening

epic: quality-report-remediation
workstream: WS-03
pr: #172 — merged as `6ee0c7c`

## Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #172 merged as `6ee0c7c` | corroborated | `git log`: `6ee0c7c fix(client): harden RetryConfig, SSL classification, adapter lifecycle (#172)` |
| ✅ landing carries a complete facts block | **corroborated** | `inbox landing-check` → `complete: true`, `missing_keys[0]`. **First complete landing since PLAN-03.** |
| NaN bug found and fixed | corroborated | `RetryConfig.java:132/135` now guard with `!Double.isFinite(...)`, mirrored in the builders at 272/310, with Javadoc at 116-118 explaining *why* ordered comparison fails on NaN |
| the fix also rejects `+Infinity` | corroborated | `Double.isFinite` excludes both infinities — a real behaviour widening beyond the spec's ask, correctly self-reported |
| 3/3 bots reviewed, Sourcery APPROVED | accepted as reported | first plan in the epic with Sourcery coverage — its budget reset as predicted (~2026-08-30T12:00Z) |
| `S5778` findings confirmed gone | accepted as reported | ⛔ **has a consequence for PLAN-04** — see Follow-Ups |
| 4,003,916 tokens / 121,320 s | from the facts block | the epic's most expensive plan |

## Deliverable Fidelity vs Spec

All 6 shipped: `RetryConfig` compact constructor enforcing 5 invariants (CL-7); non-transient TLS →
`CONFIGURATION_ERROR` with timeouts still retryable (CL-9); `resolveUri()` made pure so builder reuse
honours a new URI (CL-2); `host:port` recognised as authority (CL-3); `HttpHandler` `AutoCloseable`
with interrupt-aware blocking and cancellation (CL-11/12); cross-cutting regression suite.

⛔ **Deliverable 7 is correctly ABSENT.** The Sonar sweep briefly written into this spec never
reached the plan and was removed; all 7 findings live on PLAN-04. **This landing closed no Sonar
finding** — except incidentally, see below.

## Metrics and Anomalies

- 4,003,916 tokens / 121,320 s wall / 2h48m worked. Tests 6453 → 6488.
- ⛔ **The plan shipped a bug and caught it — in the guard the deliverable existed to add.** CL-7's
  compact constructor used ordered comparisons, and **every ordered comparison against NaN is
  false**, so NaN passed every guard and reached `calculateDelay()`, reproducing the exact hot-retry
  loop D1 was written to prevent. The hole was at **three** seams, not the two the comment named.
  CodeRabbit found it; the outline, Q-Gate, and self-review all missed it. Fixed in `482dd1d`.
- ⛔ **~3 hours and all three loop-back iterations were lost to a non-problem.** CodeRabbit's quota
  had reset hours earlier; its refusal comment was simply never updated, and nothing re-triggered the
  bot because HEAD had not moved. **Waiting could not have fixed it** — an explicit
  `@coderabbitai review` produced a review in 8 minutes.

## Routing and Merge Behavior

Queue-merged as `6ee0c7c`, worktree removed, tree clean. 3/3 bots reviewed with 0 new findings on the
final tree; Sourcery APPROVED.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `172`; `landing` `landings/PLAN-09.md`;
      `plan_marshall_plan_id` `plan-09-client-config-and-retry-hardening`
- [x] PLAN-04 deliverable 7 line numbers re-derived against `6ee0c7c` (see below)
- [x] PLAN-04's sequencing block cleared — every deliverable-7 file is now free
- [x] 5 inbox messages drained and archived

## Follow-Ups

- ✅ **PLAN-04's blocking constraint is CLEARED and its line numbers are re-derived.**
  `java:S1905` moved **163 → 197** — exactly the drift the constraint predicted. ⛔ `java:S2589` was
  deliberately NOT pinned to a line: PLAN-09 rewrote `ETagAwareHttpAdapter` substantially, so it must
  be located **by condition**. ⛔ **`java:S5778` may already be resolved** — PLAN-09 reports it
  "confirmed gone"; PLAN-04 must verify before actioning rather than "fix" a finding that no longer
  exists.
- ⛔ **`doc/client-handlers-readme.adoc` now has a real gap** — no `close()`/`AutoCloseable`/
  cancellation coverage, created by deliverable 5. ⛔ WS-05 is CLOSED and PLAN-14/PLAN-15 have
  shipped, so **this doc residue has no owner**. Joins the existing unowned-doc-residue set.
- **msg `-002`** — a zero-finding loop-back iteration at an unchanged HEAD should not consume the
  loop-back ceiling. This is the mechanism behind the 3-hour loss and is the actionable one.
- **msg `-004`** — a fifth `gh auth status` report. Folds into the resolved record; the operator has
  confirmed the underlying defect is fixed. No new action.
- Three of the four candidate lessons name plan-marshall-owned components this repo does not own;
  lifting them to the global corpus needs `--allow-foreign-store`.
