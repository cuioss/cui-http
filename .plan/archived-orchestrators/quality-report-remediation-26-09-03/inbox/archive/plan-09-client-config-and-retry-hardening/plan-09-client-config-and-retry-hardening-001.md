envelope_version=1
sender_type=plan
sender_id=plan-09-client-config-and-retry-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-30T10:26:17Z

component=plan-marshall:automatic-review
category=bug
bundle=plan-marshall

# A review bot's stale rate-limit comment is not a live refusal, and waiting cannot fix a missing trigger

## Observation

`automatic-review` looped three times against an unchanged HEAD, each iteration
returning `comments_found=0`, and burned roughly three hours before the cause was
identified. The step read a coderabbit rate-limit comment posted at 14:42Z as a
current refusal. Two facts made that reading wrong:

- The comment's own text said "next review available in 44 minutes" — i.e. ~15:26Z.
  Every re-poll happened long after that instant, so the quota had already reset.
- The comment's `updatedAt` was `null`. It had never been touched since it was
  posted; it was a historical artefact, not a live status field.

The actual cause was different in kind from a rate limit. Review bots act on
**events**. The PR had zero activity after 14:43Z and HEAD never moved, so nothing
re-triggered any bot. No amount of waiting or re-polling can satisfy a
missing-trigger condition — the remedy the workflow offers is structurally
incapable of resolving the failure it was applied to.

Posting an explicit `@coderabbitai review` comment produced a full review in 8
minutes. Later in the same run, pr-agent hit the same class: it did not react to a
push event and required an explicit `/review`. Two of the three review bots
configured on this repository require an explicit trigger.

## The generalizable defect

1. **A refusal comment is evidence of a past state, not of a current one.** A bot
   comment whose stated expiry has passed, or whose `updatedAt` is null, must not
   be treated as a live refusal signal. The staleness test is cheap and
   deterministic: compare the comment's stated availability instant (and its
   `updatedAt`) against now.
2. **"No comments found" has at least two causes that need different remedies.**
   *Rate-limited* is fixed by waiting. *Never triggered* is fixed by posting a
   trigger. The step conflated them and applied the wait remedy to both. An
   unchanged HEAD with no PR activity since the last bot comment is positive
   evidence for the second cause, not the first.
3. **Bot triggering is per-bot configuration, not a global property.** The set of
   bots that need an explicit trigger comment should be declared, not discovered
   by exhausting the iteration budget.

## Suggested corrective rule

Before a second no-findings re-poll at an unchanged HEAD, evaluate the
missing-trigger hypothesis: if HEAD has not moved and the PR has had no activity
since the last bot comment, post the bot's explicit trigger phrase rather than
re-polling. Treat any rate-limit comment whose stated availability instant has
passed, or whose `updatedAt` is null, as expired evidence.

## Filing note

The corrective surface is the `plan-marshall:automatic-review` bundle, which the
epic's host repository (`cui-http`) does not own. Lifting this into the corpus
either needs `--allow-foreign-store` or should be carried to the plan-marshall
repository.

## Cost

~3 hours; 3 of 3 admissible loop-back iterations consumed (see the sibling
candidate on loop-back ceiling accounting, which this run caused).
