envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T15:48:52Z

# Candidate lesson: a Q-Gate finding asserted parser behaviour it had never executed

**Signal source**: `signal_qgate_pending_count` (Q-Gate finding `bef4b6`, phase `3-outline`, source `qgate`, resolution `rejected`)

## What happened

The outline Q-Gate raised a finding claiming that two read-only paths declared inside the
flat `**Affected files:**` block — distinguished only by an inline `(read)` marker — would
be unioned into `references.affected_files` as expected modifications, because
`manage-references sync-affected-files` "derives its set from the structured per-deliverable
declaration HEADINGS ... it does not parse an inline `(read)` suffix". The finding predicted
an unrecoverable declared-vs-realized divergence for two files the outline intends never to
touch, and prescribed restructuring the outline under `Files to survey:` headings.

The claim was false. Running the verb returned `declared_count 22 / mutation_count 21 /
read_intent_count 1 / unannotated_count 0`, filing `PipelineFactoryTest.java` into
`read_intent_files`. The inline `(read)` marker IS parsed and the mutation/read-intent
partition IS honoured from the flat heading. `CookieTest.java` correctly landed in
`affected_files` with `read_reclassified_count 1`, because a sibling deliverable
write-replaces it and mutation-wins is the intended precedence.

## Candidate rule

A finding that asserts what a script does MUST be grounded by executing that script, not by
reading its documentation or inferring from its flag names. Where the assertion is cheap to
falsify — a single read-only verb invocation — the reviewer is obliged to falsify it before
filing. Filing first and verifying later cost a full triage round and nearly drove a
restructuring of a correct outline.

Generalised: for the finding classes whose subject is a *tool's own observable behaviour*,
the evidence standard is a captured invocation, not a citation.

## Why this is a candidate and not a filed lesson

The recurrence question — whether outline Q-Gate reviewers systematically over-assert about
script behaviour across the epic's plans — is only answerable with the cross-plan context
the orchestrator holds. Classification deferred to orchestrator-side pickup.

## Provenance

- Plan: `security-api-contract-hardening`
- Q-Gate finding hash: `bef4b6`
- Recorded: 2026-08-29T11:31:53Z, rejected (refuted) 2026-08-29T11:33:18Z
