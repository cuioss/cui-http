# Landing Analysis: PLAN-11 — Generator Contract Test Semantics

epic: quality-report-remediation
workstream: WS-04
pr: #164 — https://github.com/cuioss/cui-http/pull/164 (MERGED → `8a0aa4c`)

## Ground-Truth Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #164 merged → `8a0aa4c` | **corroborated** | MERGED; on `origin/main`. |
| 7/7 deliverables | **corroborated** | Covers the spec's 5 plus the 2 absorbed generator fixes. |
| TQ-1 closed — the 17 vacuous tests now assert | **corroborated** | Shared assertion helper plus per-value defining-property assertions across the 9 URL/header, 4 encoding/injection and 2 cookie contract tests. |
| TQ-4 closed | **corroborated** | `shouldHaveAllGeneratorsImplemented` is gone — grep returns 0. |
| TQ-15 fixed | **corroborated** | `EncodingCombinationGenerator.urlEncode` now replaces `%` **first**: `.replace("%","%25").replace(".","%2e").replace("/","%2f")`. Exactly the prescribed fix; `applyMixedCase` is reachable again. |
| TQ-16 fixed | **corroborated** | Every named sub-limit branch now exceeds STRICT=1024 — `letterStrings(1030,1050)`, `(1030,1080)`, `520+520`, `(1000,1030)`. The class comment states the limits it targets. |
| **TQ-17 fixed** | ⛔ **CONTRADICTED — absorbed but NOT delivered** | The seed-invariant `AttackTypeSelector` survives verbatim at `8a0aa4c`: `private int currentType = 0` advanced by `currentType = (currentType + 1) % maxTypes`. PLAN-11 edited that file heavily (+118/−75) and closed TQ-16 inside it, but the landing message does not mention TQ-17 anywhere. **Returned to PLAN-12 as its deliverable 1.** |
| "no assertion landed in a known-failing state" | **corroborated** | The coordinated-pair strategy worked: the two generator fixes landed with their assertions. |
| 6307 tests green | **corroborated as reported** | Up from 5583 — the epic's largest single test addition. |

## Deliverable Fidelity vs Spec

All five spec deliverables plus both absorbed fixes shipped; the absorbed TQ-17 did not.

| Spec deliverable | Verdict |
|---|---|
| 1–3. Semantic assertions for all 17 vacuous contract tests (TQ-1) | **shipped-as-specified** |
| 4. Apply the defining-property pattern uniformly (TQ-1) | **shipped-as-specified** — a shared helper, which is better than the spec asked for. |
| 5. Make `AllGeneratorsIntegrationTest` able to fail (TQ-4) | **shipped-modified** — the method was DELETED rather than strengthened. The spec permitted either ("assert something about each generator's output, or delete the redundant method"). |
| absorbed: TQ-15, TQ-16 | **shipped** |
| absorbed: TQ-17 | ⛔ **not shipped** — returned to PLAN-12. |

## Metrics and Anomalies

- 4,312,852 tokens / 46h30m — the epic's most expensive plan by a wide margin.
- ⛔ **The verify-first clause this spec carried was resolved exactly as designed.** The spec warned
  that deliverable 4's honest assertions would FAIL against `main`, forbade both weakening them and
  fixing the generators in-plan, and required escalation. The plan escalated (inbox message
  `generator-contract-test-semantics-001.md`), the operator chose the coordinated pair, PLAN-12 was
  amended, and the assertions landed green. **This is the clearest case in the epic of the
  escalate-don't-improvise contract working end to end.**
- **PR-Agent was made optional for this plan only**, at the merge barrier, with `marshal.json`
  untouched so it stays required elsewhere. The plan reports its re-review trigger is broken for
  merge-queue plans — **every merge-queue plan in this epic will hit the same block**.
- **`0c91b62` shipped without a bot re-review** — CodeRabbit's hourly quota was spent before the last
  two commits. CI and Sonar green, fixes small and locally verified, but no reviewer saw them. This
  is the third landing with a recorded review-coverage gap (#154, #159, #164).

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` 164; `landing`; `plan_marshall_plan_id`
- [x] **TQ-17 returned to PLAN-12** as deliverable 1; PLAN-12 renumbered to 5 deliverables
- [x] PLAN-11's findings table marks TQ-17 absorbed-but-not-delivered
- [x] Coverage re-verified: 117/117, TQ-17 now dual-listed with the transfer recorded on both sides
- [x] 10 candidate-lessons promoted (`2026-08-27-15-002..011`)

## Follow-Ups

- **PR-Agent's merge-queue re-review trigger is broken.** Made optional for this plan only. If
  PR-Agent is meant to gate merges here, every remaining merge-queue plan hits the same block —
  a standing decision the operator should make once rather than per-plan.
- **Three of eight landings shipped with degraded bot review** (#154 churn-disabled, #159
  comment-not-submission, #164 quota-exhausted). Individually each is defensible; the pattern is
  that bot review is not a reliable gate in this epic, and CI plus Sonar are carrying it.
