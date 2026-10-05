envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:16:46Z

# Candidate lesson: script-failure cluster 2/3 — `manage-solution-outline` undeclared-flag rejections in two different phases

**Source**: `[ERROR] ... script_failure` work-log markers
**Plan**: plan-07-forwarded-parsing-and-validation
**Failing notation**: `plan-marshall:manage-solution-outline:manage-solution-outline`
**Events**: 2 (both `exit_code=2`, `failure_kind=argparse_rejection`)

## Observation

The same notation was rejected in two separate phases, on two different verbs:

1. 2026-09-15T14:20:43Z (4-plan): `Use a declared flag for ... get-deliverable: ['deliverable-number', 'plan-id']`
2. 2026-09-15T15:05:04Z (5-execute, envelope 2): `Use a declared flag for ... read: ['deliverable-number', 'plan-id', 'raw', 'section']`

Both are undeclared-flag rejections rather than verb-paraphrase: the verbs existed, the flags did
not. Note the two verbs have **overlapping but unequal** flag sets — `read` accepts `raw` and
`section`, `get-deliverable` does not — which is exactly the shape that invites carrying a flag from
one verb to its sibling.

## Why this is lesson-bearing

Two phases, two envelopes, two different agents, one notation: this is a **surface-level** recurrence,
not a one-off slip by a single caller. The distinguishing property of this surface is the
near-sibling verb pair with asymmetric flag sets — nothing at the call site signals that a flag valid
on `read` is invalid on `get-deliverable`.

Candidate correctives for the orchestrator to weigh:

- Have the consuming workflow bodies (phase-4-plan, phase-5-execute / execute-task) xref
  `manage-solution-outline`'s canonical-invocation block per verb rather than describing the read in
  prose.
- Or consider whether the two verbs should converge, since a caller reaching for `get-deliverable`
  with `read`'s flags is asking for a thing the tool could plausibly answer.

## Suggested disposition (orchestrator judges)

`plan-marshall:manage-solution-outline` (call-site canonical invocations, possibly verb surface
review). Cross-plan relevance is high — every plan in this epic reads its own outline in both phases.
