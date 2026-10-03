envelope_version=1
sender_type=plan
sender_id=plan-05-forwarded-trust-boundary
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-26T20:09:34Z

# Candidate lesson: the PR-comment self-response filter does not recognise the orchestrator's own bot-trigger comments

## Signal

Source: `signal_automated_review_count` — pr-comment findings dismissed as noise during
phase-6 triage.

## Observation

During the run the orchestrator posted its own review-trigger comments on PR #155
(`@coderabbitai` mentions and a `/review` command). The pr-comment findings pipeline
ingested those comments as findings, and triage had to dismiss them one by one as noise.

The self-response filter — which exists precisely to stop the pipeline from feeding the
orchestrator its own output back as review findings — did not recognise them. It appears
to key on the comment author or on a reply relationship, and a bot-trigger comment the
orchestrator authored to *invoke* a reviewer does not match whatever shape the filter
looks for.

## Why it is worth recording

Low severity, but it is pure recurring tax: every orchestrated run that triggers a review
bot by comment will manufacture the same noise findings, and each one costs a triage
disposition. It is also the kind of miss that erodes trust in the findings count — a
pending-findings number inflated by the orchestrator's own comments is a worse signal
than a smaller accurate one.

## Candidate directive

Extend the pr-comment self-response filter so that comments the orchestrator itself
authored to trigger a review bot (`@coderabbitai …`, `/review`, and the equivalent
trigger forms for other configured bots) are excluded at ingestion rather than dismissed
at triage. The filter should treat "a comment this workflow wrote" as the criterion, not
"a comment replying to this workflow".

## Evidence

- PR #155 comment thread: orchestrator-authored `@coderabbitai` / `/review` trigger
  comments filed as `pr-comment` findings.
- Phase-6 triage dispositions dismissing them as noise.

## Scope note for the orchestrator

This is a tooling-level candidate against the plan-marshall review pipeline, not against
cui-http. Routing it to the right home is an orchestrator-side call.
