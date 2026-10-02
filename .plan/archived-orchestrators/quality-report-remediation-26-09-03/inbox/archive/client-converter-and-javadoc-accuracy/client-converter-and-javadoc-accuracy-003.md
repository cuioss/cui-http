envelope_version=1
sender_type=plan
sender_id=client-converter-and-javadoc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T09:30:54Z

# Candidate lesson: a success status proves SOME argument was applied, not the one you cared about — verify the mutation landed, by reading it back

## Signal provenance

Discovered when `pr-agent` flagged that the PR body diverged from the PR's actual contents.
By then the stale body had already propagated into a replacement PR.

## Observation

`ci pr edit` was called to update PR #182's title and body. It returned `status: success`.
The **title was applied; the body was not.**

The cause: `ci prepare-body` defaults to `--for create`, and the edit consumer requires
`--for edit`. The prepared body was therefore not in the shape the edit consumer reads, so
the body argument was silently dropped — and the call still reported success **on the
strength of the `--title` argument alone**. One argument landed, one did not, one status
covered both.

Nothing failed loudly: exit code 0, `status: success`, no warning naming the ignored body.
The stale body then survived into a replacement PR, where it was finally caught not by any
gate but by a review bot noticing the description did not match the diff.

## Reusable rule (candidate)

**A multi-argument mutation verb's aggregate `status: success` is not per-argument evidence.**
Two obligations follow:

1. **At the call site** — after a mutation whose payload matters (a PR body, a description, a
   comment), **read it back** and confirm the value you sent is the value now stored. A
   read-back is one cheap call; the alternative is the wrong content propagating silently
   into every downstream artifact.
2. **In the producer/consumer pair** — when one verb PREPARES a payload and another CONSUMES
   it, the preparer's default mode must not silently produce something the consumer discards.
   Either the consumer rejects a payload prepared `--for` the wrong mode (loudly), or the
   preparer has no default and forces the caller to state the mode. A default that is correct
   for the more common consumer and silently wrong for the other is a trap, not a
   convenience.

The generalization past this specific flag pair: **any verb that accepts several independent
mutations under one status field can report success for a subset.** Treat its status as
"at least one thing happened", never as "everything I asked for happened".

## Suggested routing (orchestrator to judge)

- Candidate component: `plan-marshall:tools-integration-ci` (the `prepare-body --for` /
  `pr edit` producer-consumer pair, and the aggregate status).
- Category: `bug` for the silent-drop half; the read-back obligation is `improvement`. The
  orchestrator should judge whether these split into two lessons.
- Note on dedup: the corpus already carries `2026-08-27-15-010` (three `ci` rejections about
  `--plan-id` position). This is **not** that lesson — those were loud argparse rejections;
  this one is a call that was accepted and partially ignored.

## Plan context

- Plan: `client-converter-and-javadoc-accuracy` (epic `quality-report-remediation`)
- PR #182; the stale body propagated into a replacement PR before `pr-agent` flagged the
  divergence.
