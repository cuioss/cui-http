envelope_version=1
sender_type=plan
sender_id=plan-06-forwarded-trust-model
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-08T15:05:45Z

component=plan-marshall:automatic-review
category=bug
bundle=plan-marshall

# A currency check keyed on comment timestamp credited a review bot at a HEAD its own comment denied

The participation classifier credited `cuioss-review-bot` as having reviewed the
merge-candidate HEAD. The bot's own comment on that PR said otherwise — it carried
the line `Review updated until commit <earlier sha>`, naming a commit older than
HEAD.

The classifier rounded the stale review up because its currency test compared the
comment's `updated_at` timestamp against the commit time, rather than comparing the
commit the bot *said it reviewed* against the merge-candidate commit. A comment can
be touched (edited, re-rendered, threaded) after the review it reports, so
`updated_at` moves without the review moving. Timestamp ordering is a proxy for
coverage, and it is a proxy that fails in the permissive direction.

This is the classifier-side twin of the green-check-without-a-review defect: the same
wrong answer arrived through a second, independent path. That is the strongest
evidence that the underlying rule — *participation is a claim about a COMMIT, not
about a TIME* — is not encoded anywhere central.

## Solution

- Make the reviewed-commit id the single currency key everywhere participation is
  decided. Parse the sha the bot states (the `Review updated until commit <sha>`
  form, or the API's covered-commit field) and compare shas, never times.
- Where the bot states no sha at all, that is `unknown`, not `current`. An absent
  coverage claim must not be resolved to participation by falling back to a
  timestamp comparison.
- Encode the rule once, in the shared participation helper both the check-based and
  the comment-based path call, so a future third path cannot re-derive its own
  proxy.

## Impact

Affects the merge-authorization decision directly: a stale review counted as current
lets code merge that no bot has read. Any repository using comment-based review
bots with an "updated until commit" marker is exposed.
