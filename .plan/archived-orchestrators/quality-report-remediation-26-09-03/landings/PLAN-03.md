# Landing Analysis: PLAN-03 — Security API Contract Hardening

epic: quality-report-remediation
workstream: WS-01
pr: #169 — https://github.com/cuioss/cui-http/pull/169 (merged, `e2c7ebd`)

> Landing record for one shipped plan. Written by the `analyze` verb after verifying claims
> against ground truth. A pasted claim is a lead, never a fact.

## Corroboration

Every material claim was checked against first-party ground truth before any ledger write.

| Claim (from paste + inbox `-011`) | Verdict | Evidence |
|---|---|---|
| PR #169 merged | corroborated | `ci pr view --pr-number 169` → `state: merged` |
| merge commit `e2c7ebdcbf358e69b23fc602dea3629078bf9868` | corroborated | `pr_view.merge_commit_sha` matches; `git log` HEAD is `e2c7ebd` |
| merged via merge queue | corroborated | `step.branch-cleanup.merge_mechanism=merge_queue` in the facts block, consistent with the queue merge commit |
| 21 files touched | corroborated | `git show --stat e2c7ebd` → 21 files, +507/−27 |
| 6/6 deliverables | corroborated | per-deliverable evidence below |
| repository clean, main current | corroborated | `git status --porcelain` empty; HEAD `e2c7ebd` |
| landing facts machine-readable and complete | corroborated | `inbox landing-check` → `complete: true`, `missing_keys[0]` |
| ⛔ "`gh auth status` takes 60.6s **on this host**" | **contradicted as stated** | measured 417 ms at analysis time, `rc=0`. See Open Defect below — the observation was real, the *attribution* was not. |

`steps` was parsed by LAST-colon split; all 16 elements are bare (no namespaced step ids in
this composition), so the split is confirmed applied but is not discriminating here.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. `AttributeParser` strips quotes, unescapes quoted-pairs (SV-8) | shipped-modified (widened) | `AttributeParser.java` gained a private `unquote()` with quoted-pair resolution AND an odd-backslash-parity guard at the closing delimiter; +79 lines. The parity guard is beyond spec — it closes a defect the fix itself introduced. |
| 2. `SecurityEventCounter` Javadoc reconciled (SV-9) | shipped-widened | `SecurityEventCounter.java` +41/−? ; the plan corrected **four** false claims where the spec named three. `getFailureTypeCount()`'s "at least one event recorded" was untrue after `reset()` and was not in the source report. |
| 3. Lenient preset double disablement documented (SV-11) | shipped-as-specified | `SecurityDefaults.java` +17, Javadoc-only |
| 4. BODY limit no longer capped at 2 GiB (SV-13) | shipped-as-specified | `LengthValidationStage.java` — `long inputLength`, `long limit`, `utf8ByteLength` accumulates in `long` with an explicit overflow note |
| 5. Pipeline `equals`/`hashCode` include configuration (SV-14) | shipped-modified (scope grew twice) | All five pipelines now carry `@EqualsAndHashCode(callSuper=false, of={"config"})` with the new field guarded by BOTH `@Getter(AccessLevel.NONE)` and `@ToString.Exclude`. Spec said four classes with an empty basis; outline found five. |
| 6. `Cookie` prefix factories document `@throws` (SV-20) | shipped-as-specified | `Cookie.java` +14, Javadoc + tests (`CookieTest.java` +37) |

Six deliverables at the split guard, proceeding unsplit — the recorded rationale held: no
ordering constraint materialised between them, and no deliverable blocked another.

## Metrics and Anomalies

- Tokens: 2,352,903 across 6 phases (`total_tokens` fact). The epic's most expensive plan to date.
- Duration: 6h07m wall (`total_wall_seconds=22020`), 1h38m worked — a 3.7× wall-to-worked ratio,
  driven by review-bot waits and the re-trigger below.
- Sync baseline: `action=noop`, `upstream_commit_count=0` — no rebase, no conflict.
- Anomalies:
  - CodeRabbit hit its hourly OSS review limit on the first pass and needed a re-trigger.
  - Sonar `fetch_findings` was invoked twice with a rejected flag set, then with a required flag
    missing — a guess-and-retry cycle, recovered (message `-009`).
  - The automated-review scripted path degraded to manual finding transcription (message `-010`).

## Routing and Merge Behavior

- Review: CodeRabbit and pr-agent (both required) reviewed the final tree. **Sourcery did not
  review** — its 7-day 250,000-diff-character budget was exhausted, resetting ~2026-08-30T12:00Z.
- Findings: 2 CodeRabbit findings fixed (`3313a97`), 1 Sonar new-code issue (`java:S127`) fixed,
  re-scan clean. All three were filed by hand, not by the scripted path.
- CI/merge: all checks green; merged through the merge queue as `e2c7ebd`. No rebase conflicts,
  no re-verify signals, no surface collision with any other plan.

**Three defects the review cycle caught that the spec did not** — all merged fixed. Each is an
instance of the exact defect class this epic exists to close, reintroduced by a fix for it:
the escaped-final-quote bug inside the SV-8 fix; the `@ToString` leak of the same `config` field
the operator's outline review had already corrected once for `@Getter`; and Sonar's S127.

## Reconciliation Actions

- [x] row `status` → `shipped` — `queue --transition PLAN-03 --status shipped`
- [x] row `pr` stamped `169`
- [x] row `landing` stamped `landings/PLAN-03.md`
- [x] row `plan_marshall_plan_id` stamped `security-api-contract-hardening`
- [x] epic.md reconciled from status.json; both generated blocks regenerated
- [x] 11 inbox messages drained and archived
- [x] Open Defects and Watches opened — see below
- [x] resume_anchor updated

## Follow-Ups

Dispositions of the 10 candidate-lesson messages are recorded in `epic.md` and the decision log.
Nothing here folds into a staged spec's deliverable set; the two that touch WS-01 practice are
recorded as Watches so PLAN-04 reads them at outline.

- **`-008` (Lombok generator enumeration)** → Watch on WS-01. PLAN-04 edits the same five pipeline
  classes' Javadoc; the durable rule is "enumerate every generating annotation on the type", not
  "remember `@Getter`". One field, two generators, caught at two separate stages.
- **`-007` (escaped-delimiter boundary parity)** → Watch. Applies to any delimiter-stripping routine
  in the security package; PLAN-04's surface includes `AttributeParser` Javadoc.
- **`-010` + the paste's `gh auth status` claim** → Open Defect, with the attribution corrected.
- **`-005` (`scope_estimate` band naming)** → explicitly deferred to the orchestrator by the plan
  itself; it takes no position. Recorded as a Watch, not escalated — it is a plan-marshall
  vocabulary question, not a cui-http one, and this epic cannot fix it.
- **D4's test couples to a private method name** (`getMaxLength()` via reflection). The plan asks
  for an epic-level view on whether that coupling is acceptable across WS-01. Recorded as an
  Open Defect for the operator — it is a standards call, not a code fix.
