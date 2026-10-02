envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T15:50:24Z

# Candidate lesson: all-100% confidence scores are auto-suspicious, and the justification that clears them is re-verification not assertion

**Signal source**: `signal_qgate_pending_count` (Q-Gate finding `00cd1e`, phase `2-refine`, source `qgate`, resolution `taken_into_account`, non-blocking)

## What happened

All six confidence dimensions — correctness, completeness, consistency, non-duplication,
ambiguity, module_mapping — scored 100, which the `phase-2-refine` Q-Gate flags as suspicious
by default. The flag is a gaming detector: a uniform perfect score is the signature of a
scorer that rated its own work rather than measured it.

The justification accepted in this case was NOT an assertion of confidence. It was evidence
of independent re-verification:

- The request is lesson-derived from an already-verified epic plan carrying per-hypothesis
  verdicts corroborated at HEAD `f1ba539`.
- `phase-2-refine` independently re-verified 5 of those claims (AttributeParser quote
  handling, SecurityEventCounter behaviour/Javadoc mismatch, pipeline `@EqualsAndHashCode`
  presence) via architecture-inventory search and direct file reads. All 5 held.
- The sole proposed-fix probe (deliverable 1, quote-stripping) was executed and passed with
  no gap.
- Module mapping is an unambiguous direct match to `cui-http/cui-http-core`.

## Candidate rule

A perfect confidence score clears its suspicion gate only by naming *what was independently
re-checked and how*, never by restating the score's basis. Inheriting verdicts from an
upstream verified plan is not sufficient on its own — the inheriting phase must re-run a
sample of those verdicts against current HEAD, because the upstream corroboration was taken
against a possibly-older tree.

The generalisable half: **a confidence score is a claim about evidence, so its justification
must be a description of the evidence-gathering, not a paraphrase of the claim.** That is the
same distinction the retirement-evidence contract draws in `manage-lessons remove`
(`--coverage-verdict` asserts, `--covering-clause` + `--covering-input` evidence it).

## Why this is a candidate and not a filed lesson

This may already be adequately codified in the `phase-2-refine` Q-Gate's own justification
requirements — in which case it is a worked example rather than a lesson, and should be
discarded or folded into documentation. Only the orchestrator can see whether sibling plans
in this epic cleared the same gate on weaker justifications, which is what would turn this
into an actionable rule.

## Provenance

- Plan: `security-api-contract-hardening`
- Q-Gate finding hash: `00cd1e`
- Recorded 2026-08-29T10:07:11Z, resolved 2026-08-29T10:07:42Z
- Corroboration anchor: HEAD `f1ba539`
