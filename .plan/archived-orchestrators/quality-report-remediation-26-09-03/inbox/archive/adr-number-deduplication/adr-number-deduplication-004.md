envelope_version=1
sender_type=plan
sender_id=adr-number-deduplication
epic=quality-report-remediation
kind=landing
created=2026-09-01T13:49:30Z

## What landed

adr-number-deduplication shipped as PR #184 (merged, squash c631c77). All four duplicate ADR numbers are resolved; doc/adr/ now holds fourteen ADRs numbered 1-14 with no repeats.

```landing-facts
schema=landing-facts/1
plan_id=adr-number-deduplication
epic=quality-report-remediation
pr=184
merge_state=merged
deliverables_total=4
deliverables_done=4
total_tokens=3049695
total_wall_seconds=14062
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:done
step.create-pr.pr_number=184
step.sonar-roundtrip.new_code_issue_count=0
step.sonar-roundtrip.count_status=confirmed
step.finalize-step-sync-baseline.action=noop
```

## Residue

**Deliverable 3 was re-scoped mid-execution, and the reason generalizes.** The spec called for `doc/adr/README.adoc`. That is infeasible: `manage-adr list` and `scan` glob `ADR_DIR.glob('*.adoc')` with no numeric-prefix filter, and `parse_adr_file` assigns `number: 0` / `title: Unknown` to anything not matching `^(\d+)-`, so a `README.adoc` enumerates as a bogus fifteenth ADR and breaks the `count: 14` criterion. The executor stopped rather than substituting; the operator chose `doc/adr/README.md`, which sits outside the glob. Any future epic plan that wants a non-ADR document inside `doc/adr/` faces the same constraint.

**Deliverable 4 is a verified no-op, deliberately.** All fourteen ADRs remain `Proposed`. ADR-0012 (a security preset must never enable `caseSensitiveComparison`) and ADR-0013 (`when()`/`identity()` are fail-open) record previously-undocumented security positions; accepting them is a security decision the operator owns, and no ruling was given. This is unfinished business the epic still holds, not a closed item.

**The staging spec's cross-reference hypothesis was partly falsified.** It labelled "no file outside doc/adr/ references any of the eight" a HYPOTHESIS and asked for verification. The external absence held (564 inventoried files, plus a git-enumerated read of `.github/**`, `.claude/**` and the ignored set), but one internal reference existed and was repaired: `0009-Attack-database_entries…adoc:61` cited `ADR-0004`, whose target renumbered to 0011.

**The allocation constraint is recorded, not fixed — and the recording itself is unenforceable.** `doc/adr/README.md` states that ADR numbers are a shared sequential resource with no allocator, names the four observed collisions as evidence, and explains that the surface-disjointness gate is structurally blind because it compares paths while the contested resource is a number. CodeRabbit correctly flagged that the README asserted `manage-adr` scan behaviour this repository cannot enforce, and that its index table duplicates the authoritative `.adoc` files with no drift check. Commit `abd5722` scoped the first claim to a dated observation and made the hand-maintained nature of the index explicit. Neither underlying gap is closed: the allocator and the `default:adr-propose` `mutates_source` misdeclaration (lesson `2026-08-27-12-004`, now recurred twice) live in the plan-marshall marketplace and remain out of reach from this repository.

**The concurrency hazard did not fire, but was not prevented.** `semicolon-dot-segment-traversal` and `validated-redirect-following` were active throughout. Neither proposed an ADR, so nothing collided with the 0011-0014 range this plan claimed. That was luck plus one deliberate choice — the `standard` execution posture excludes `adr-propose`, so this plan's own finalize could not allocate from the sequence it was deduplicating. A future plan running the `full` posture concurrently with another would reproduce the original defect.

**Three candidate-lesson messages accompany this landing** (`adr-number-deduplication-001/002/003`), all concerning argparse-rejection recurrence at the orchestrator tier. Their proposed changes target `persona-plan-marshall-agent` and plugin-doctor, which live in the plan-marshall marketplace and need cross-repo routing.
