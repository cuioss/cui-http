envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-diagnostics-and-doc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:25:36Z

component=plan-marshall:automatic-review
category=improvement
bundle=plan-marshall

# A review bot's completion signal can be an in-place EDIT of an existing comment, not a new object

## Observation

Review-participation detection that looks only for new review objects and new inline comments
misses CodeRabbit's actual "I reviewed this, nothing to say" signal, and separately can be
fooled by an automatic annotation on an old thread. Both directions occurred in this run.

Evidence from this plan (`plan-07-forwarded-diagnostics-and-doc-accuracy`), decision.log:

- **False ABSENT (missed a real review).** `2026-08-29T19:07:12Z`:
  "CodeRabbit's persistent walkthrough comment (issue_comment `IC_kwDOPwSbp88AAAABRZabJQ`,
  `updated_at=2026-08-29T19:00:20Z`) explicitly names the reviewed commit range as
  `565c2fca..7f9b9d4` (current HEAD) with 'No actionable comments were generated in the recent
  review' and its CodeRabbit completion check-run reports `completed=true`". The bot reported a
  completed review by EDITING one long-lived walkthrough comment in place. No new review object
  and no new inline comment was created — a nothing-to-say review produces no inline comments
  by definition. Detection restricted to reviews + inline comments reads a completed review as
  absent, and it stays absent no matter how long you wait.
- **Near-false PRESENT (nearly counted a non-review).** `2026-08-29T14:45:52Z`:
  "excluded coderabbit's stale inline evidence (comment updated `2026-08-29T13:24:52Z` is an
  auto-resolve annotation on a pre-existing thread, not a new review) from participated-bots."
  An auto-appended "Addressed in commit X" annotation bumps `updated_at` on an OLD inline
  thread. A currency test keyed on `updated_at` alone reads that as fresh participation.

Note the two failure modes are the SAME mechanism read in opposite directions: `updated_at`
moved on a pre-existing comment. In one case the edit carried a genuine new verdict; in the
other it carried a bookkeeping annotation. Timestamp recency alone cannot separate them.

## Why this is reusable

This is not a CodeRabbit quirk to special-case. "Maintain one persistent summary comment and
edit it" is a common bot publishing shape, and "auto-annotate resolved threads" is a common
bot bookkeeping shape. Any participation detector built on *object creation* will be wrong in
both directions against such bots, and the errors are expensive: the false-absent one costs
loop-back iterations that can never converge (this run burned two of three), and the
false-present one would silently pass a merge gate on a review that never happened.

## Proposed rule

Participation evidence must be **content-typed, not existence-typed**. For an edited-in-place
comment, the admissible evidence is the comment BODY asserting a reviewed commit range or
review verdict — cross-checked against the current HEAD — and/or the bot's own completion
check-run reporting terminal success. A bumped `updated_at` on a pre-existing comment is
neither necessary nor sufficient on its own:

- an edit whose body names the current HEAD's commit range IS participation, even with zero
  new objects;
- an edit whose body is a resolve/acknowledgement annotation is NOT participation, however
  recent.

The declared `participation_evidence` publish shapes per bot should therefore include the
"edited persistent summary comment" shape explicitly, with the commit-range assertion as its
currency test rather than the timestamp.
