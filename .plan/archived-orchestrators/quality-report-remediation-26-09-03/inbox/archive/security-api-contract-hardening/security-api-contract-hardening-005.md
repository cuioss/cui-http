envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T15:50:03Z

# Candidate lesson: scope_estimate=multi_module was driven by file-count breadth, not by module count

**Signal source**: `signal_qgate_pending_count` (Q-Gate finding `294601`, phase `2-refine`, source `qgate`, resolution `taken_into_account`, non-blocking)

## What happened

The Scope Realism check flagged that `scope_estimate=multi_module` was derived from an
11-file, 5-package spread (`data`, `monitoring`, `config`, `validation`, `pipeline`) that
resolves entirely to a SINGLE registered architecture module, `cui-http/cui-http-core`. No
second Maven module is touched anywhere in the plan.

The label was selected because the package-spanning breadth exceeded the `single_module`
10-file cap — that is, by the deterministic path-count band table — not because multiple
modules are involved. The band name and the thing the band measures disagree.

## Candidate rule (weak — for the orchestrator to judge)

The `scope_estimate` vocabulary (`surgical` / `single_module` / `multi_module`) names MODULE
counts, while the deterministic sensor that assigns it measures DISTINCT PATH COUNTS. For a
repository whose single module holds many packages, the two diverge routinely, and the
resulting `multi_module` label reads to a downstream consumer as "this plan crosses module
boundaries" when it does not.

Two possible dispositions, both above this plan's pay grade:

1. Accept the divergence as a known, documented property of a count-based sensor (the band
   is deliberately scale-truthful upward and the residuals push up, never down) — in which
   case nothing changes and this candidate should be discarded.
2. Treat it as a naming defect in the band vocabulary.

This plan states the observation and takes no position.

## Recurrence question for the orchestrator

This is the disposition that genuinely needs cross-plan context: whether sibling plans in
this epic — all of which target the same single-module repository — also land on
`multi_module` for the same reason. A one-off is noise; the same label on every plan in a
single-module repo is a signal about the sensor.

## Resolution in this plan

Documented as a deliberate scope-ladder labelling choice (package-spanning breadth within
one architecture module); no scope change made, carried forward to `phase-3-outline` for
confirmation.

## Provenance

- Plan: `security-api-contract-hardening`
- Q-Gate finding hash: `294601`
- Recorded 2026-08-29T10:07:03Z, resolved 2026-08-29T10:07:35Z
