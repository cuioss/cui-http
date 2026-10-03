envelope_version=1
sender_type=plan
sender_id=cross-origin-credential-forwarding-proof
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-03T09:26:41Z

# Candidate lesson: CodeRabbit registry `participation_evidence` does not cover `issue_comment`

**Class**: producer/registry defect in `plan-marshall:automatic-review`. Reproduced on two PRs.

## Observation

`automatic-review/standards/coderabbit.md` (bundle 0.1.1581, line 41-43) declares:

```yaml
participation_evidence:
  - review_body
  - inline
```

CodeRabbit published its clean verdict for this run as an **`issue_comment`**, not as a
`review_body` or an `inline` comment. Verified on PR #194: author `coderabbitai`,
`kind=issue_comment`, `created_at=2026-09-03T08:46:26Z`, body carrying
`"No actionable comments were generated in the recent review"` and naming the exact
range reviewed (`c7862d0..84766d3`, which is the PR HEAD). `github_pr bot_completion`
independently reported `completed: true` for `completion_check_name: CodeRabbit`.

Because `issue_comment` is not in the taxonomy, the deterministic participation
classifier resolved a genuinely-reviewed PR to `absent`.

`issue_comment` IS a legal member of the vocabulary — `bot-participation-contract.md`
line 148 defines it, and `pr-agent.md` declares it as its unconditional evidence shape.
So this is a per-bot data gap in the CodeRabbit record, not a missing vocabulary member.

## Consequence observed

- Spurious `loop_back` on iteration 1 (decision.log `d68e73`, 2026-09-03T07:55:13Z).
- The loop could not terminate by re-running FIND: re-looping does not change the
  publish shape the bot chose, so the gate would have looped indefinitely.
- Closed only via the `force-done` escape hatch with an operator-confirmed override
  (decision.log `cfd27a`, 2026-09-03T09:01:51Z,
  `bot_states={coderabbit:absent,pr-agent:participated_but_empty,sourcery:participated}`).
- Reproduced identically on PR #193 and PR #194 (same branch, same HEAD), so it is not a
  stale-PR artifact.

## Candidate corrective action

Add `issue_comment` to `coderabbit.md`'s `participation_evidence`. Note the interaction
with that same record's `ignore_patterns`, which already lists
`"No actionable comments were generated"` as a whole-comment noise drop: the drop is
correct for FINDING extraction (the comment carries no finding) but the comment must
still COUNT as participation evidence. Whichever fix is chosen has to keep those two
answers separate — this is the same "the two lists answer different questions" boundary
the file already states for `refusal_patterns` vs `ignore_patterns`.

## Evidence

- `automatic-review/standards/coderabbit.md` lines 35-68 (registry data block)
- `automatic-review/standards/bot-participation-contract.md` line 148
- plan decision.log entries `d68e73`, `b0e929`, `cfd27a`
- plan `cross-origin-credential-forwarding-proof`, PRs cuioss/cui-http#193 and #194
