envelope_version=1
sender_type=plan
sender_id=doc-inventory-and-navigation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T07:30:02Z

# Candidate lesson: pr-agent's clean verdict covered a tree that no longer existed

## Observation

`pr-agent` (posting as `cuioss-review-bot`) finished this run as
`participated_stale`. Its reviewer guide (finding `6ef527`) reported:

> No relevant tests / No security concerns identified / No major issues detected

That verdict was rendered against commit `09284fd6...` — BEFORE the fix commits
that resolved CodeRabbit's four actionable findings, and before `882cc7b`
corrected the two ADRs. pr-agent does not auto-review on push, so it never saw
the tree that actually merged. Its clean pass is a clean pass on a superseded
diff.

CodeRabbit, by contrast, was re-driven manually and its final review sha
(`a1c6395a...`) does correspond to the corrected tree.

## Why this is epic-level

A `participated_stale` outcome is currently reported as participation. In terms
of assurance it is closer to non-participation: the bot's finding set is empty
because it never looked at the final state, not because the final state is clean.
Any downstream reading of "N bots participated, all clean" over-counts assurance
by exactly the stale bots.

The relevant bot property is knowable and stable — pr-agent does not auto-review
on push — so this is not an unpredictable race. It recurs on every plan that
lands fix commits after the first review, which is most of them.

## Candidate rule

Two asks:

- Where a bot's completion state is `participated_stale`, the review-completeness
  report should say so alongside the bot's verdict, so a stale clean pass is never
  read as a fresh one.
- For bots known not to auto-review on push (pr-agent here), either issue the
  re-review nudge after the final fix commit — the same mechanism the CodeRabbit
  recovery used by hand — or record the bot as not covering the merged tree.
  Silently accepting the pre-fix verdict is the one option that misreports.

## Evidence

- Finding `6ef527` (pr-agent reviewer guide, `reviewed_commit_sha`
  `09284fd6166ba0b66458e75bf7b35085b12d9dd9`, `resolution: taken_into_account`)
- Final corrected-tree review sha: `a1c6395a9cbcf0b181db7aac7c7f93925f666d3c`
- Fix commits landed between the two: TASK-7/8/9 follow-ups and `882cc7b`
