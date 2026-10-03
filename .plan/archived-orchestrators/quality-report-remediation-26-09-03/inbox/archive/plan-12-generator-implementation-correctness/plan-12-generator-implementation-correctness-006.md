envelope_version=1
sender_type=plan
sender_id=plan-12-generator-implementation-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:37:04Z

# Candidate lesson: the outline's declared footprint is unauditable because this outline lane files no assessments

## What happened

`manage-findings assessment list --certainty CERTAIN_INCLUDE` returns `filtered_count: 0` for this
plan, while `solution_outline.md` declares 21 footprint entries across 8 deliverables (19 mutation
paths plus 2 read-intent paths; `bullets_parsed: 21`, `deliverables_scanned: 8`).

phase-3-outline Q-Gate Section 2.2 (Assessment Coverage) therefore had no population to check the
declared files against: every declared path was unbacked by a recorded assessment.

The Q-Gate re-verified the declared set independently by content sweep over a completely searched
inventory (`TRAVERSAL_MARKERS` 9 files, `AttackTypeSelector` 2 files, the doubled-backslash escape
family 6 files, the cookie-generator consumer set 5 files; 548 files scanned, `truncated: false`,
`unreadable` empty) and found the declarations accurate and complete. So this is a **provenance
gap, not a scope defect** — recorded at `warning` severity so it did not auto-loop the outline, and
accepted by the operator with no change required.

## The actual finding

Two zeros are being conflated, and only one of them is benign:

- "assessments were filed and none was `CERTAIN_INCLUDE`" — a real signal.
- "this outline lane does not produce assessments at all" — the steady state here.

Reading `filtered_count: 0` cannot distinguish them, so the gate raises a finding on every plan run
through this lane, and a future reader has no way to tell the expected zero from a genuinely
skipped outline step. This run resolved it by recording the fact on the plan; that record does not
generalise to the next plan.

## Also worth noting: the same finding was filed twice

`e5a783` and `18a091` are the same finding. The first stated 22 declared entries; the correct count
is 21 (19 mutation + 2 read-intent per `sync-affected-files`). It was suppressed and replaced by a
corrected restatement rather than amended in place, so the gate record would carry no wrong number.
That is the right disposition, but it means a corpus reader sees two findings where one event
occurred — worth checking whether the Q-Gate surface should support correcting a finding rather
than superseding it.

## Candidate rule

A gate that checks coverage against a store must first establish whether that store is POPULATED
for the lane under test, and report "not applicable to this lane" distinctly from "checked, found
nothing". Otherwise the gate's zero is an unfalsifiable warning that trains readers to ignore it.

## Provenance

- Plan: plan-12-generator-implementation-correctness
- Q-Gate findings: `18a091` (accepted) which supersedes `e5a783` (suppressed), both phase 3-outline
