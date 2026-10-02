envelope_version=1
sender_type=plan
sender_id=test-evidence-integrity
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T14:01:41Z

# Candidate lesson: a review-bot NAME mismatch was reported as a MISSING review, and the blocking direction hid it

## Signal class

`signal_automated_review_count` / review-gate configuration. Observed in plan `test-evidence-integrity`, epic `quality-report-remediation`, repository `cuioss/cui-http`.

## What happened

`marshal.json` `required_bots` names the reviewer `pr-agent`. On this repository that reviewer actually posts as GitHub login `cuioss-review-bot`. The bot-completion producer matched no author with the configured name and resolved the bot to `absent` — a BLOCKING state — while the bot had in fact reviewed the PR and reported no issues.

So a pure configuration/name mismatch surfaced as a genuine review gap. Because the resolution is blocking, the run could not distinguish "the reviewer never showed up" from "I am looking for the wrong login", and the failure mode looks exactly like the one it is most important not to wave through. The run spent roughly two hours and several hundred thousand tokens chasing a review that already existed.

## Why it is generalisable

1. **The producer conflates two facts.** "No author matched the configured name" and "no review exists" are different observations, and only the second is a review gap. Resolving the first to `absent` reports an unobservable as a failing signal — the same fail-open/fail-closed confusion the plan-marshall zero-discrimination contracts exist to remove (`which kind of zero is this?`). The right shape is a third state (`unresolvable_bot_identity` / `indeterminate`) that names the configured token, names the logins that DID review, and says the identity could not be reconciled.
2. **Blocking on an indeterminate makes the misconfiguration expensive rather than obvious.** The cost is paid every run, in the most expensive place (a human/agent hunting for a review), instead of once, at config time.
3. **Nothing validates `required_bots` against observable reality.** A configured bot login that has never appeared as a reviewer on any PR in the repository is a detectable config defect, and it is cheap to detect the first time the gate runs.

## Recurrence surface — THIS IS THE IMPORTANT PART

The mismatch was fixed **plan-locally only**. `marshal.json` in `cuioss/cui-http` still carries `required_bots: pr-agent`. **Every other plan in this repository will hit this again**, at the same cost, until the project config is corrected. This is not a closed defect.

## Proposed corrective actions

- **Immediate, repo-scoped**: correct `required_bots` in `cui-http`'s `marshal.json` to the login the reviewer actually posts under (`cuioss-review-bot`), via `manage-config`. This is the recurrence-closing action and is out of the finishing plan's scope, so it needs an owner.
- **Tooling, generalisable**: give the bot-completion producer a distinct verdict for "configured bot login matched no author on this PR", separate from "the bot did not review". Carry, in the payload, the configured token and the set of logins that DID post reviews — with both facts on the page a reader resolves a name mismatch in seconds instead of hours.
- **Tooling, generalisable**: on first use of a `required_bots` entry, cross-check it against the observed reviewer set and warn when the configured login has never appeared. A name mismatch is a config defect, and config defects should fail at config time, not at gate time.
