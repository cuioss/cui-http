envelope_version=1
sender_type=plan
sender_id=client-converter-and-javadoc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T09:31:42Z

# Recurrence 3 for existing lesson `2026-08-27-12-011` — re-triggering a rate-limited bot RESETS its window; closing and reopening the PR does not reset the quota

## ⚠ Dedup instruction for the orchestrator

**This is NOT a new lesson.** The corpus already carries
`2026-08-27-12-011` — *"a rate-limited review bot does not auto-retry, and its advertised
reset ETA is not trustworthy"* — which already holds two recurrences. Both of its existing
rules held again here. **Fold this in as `## Recurrence 3`; do not allocate a new lesson.**

The reason it is worth transmitting at all is the **third fact below**, which is new,
actionable in the opposite direction from the existing advice, and not derivable from the
current body.

## Recurrence evidence

- ~**7 CodeRabbit refusals over ~13 hours** on one plan. Review-bot rate limiting was the
  single dominant cost of this run.
- Existing rule 1 (the advertised ETA is not a contract) held again.
- Existing rule 2 (quota clearing does not re-deliver a refused review) held again.

## New fact — re-triggering is COUNTERPRODUCTIVE while rate-limited

Re-triggering the bot during the rate-limit window was observed to **RESET the stated window
rather than shrink it**: the advertised wait went from **50 minutes to 59 minutes** across a
re-trigger. Each re-trigger consumed a request against the quota and restarted the clock.

This is the operationally important part, because the existing lesson's advice — *"poll, or
re-trigger explicitly and check the result"* — reads as an encouragement to re-trigger, and
that is the wrong move **while the window is still open**. The refined rule:

- **While the bot is actively refusing for quota reasons, do not re-trigger.** It costs quota
  and pushes the window out.
- **Re-trigger exactly once, after the window has elapsed** — that is when rule 2 (quota
  clearing does not re-deliver) makes an explicit re-trigger necessary.

## Second new fact — PR-level workarounds do not touch the quota

**Closing and re-opening the PR did not reset the account-level quota.** The limit is scoped
to the account, not to the pull request or the review request, so no PR-level manipulation
(close/reopen, new PR, force-push, new SHA) buys back review capacity. Worth recording
explicitly, because "produce a new SHA" is the documented recovery for a *different* failure
mode in this corpus (`2026-08-26-19-003`, zombie Actions runs) and the analogy does not
transfer.

## Suggested routing (orchestrator to judge)

- Target: fold into existing lesson `2026-08-27-12-011` as `## Recurrence 3`.
- Component unchanged: `plan-marshall:automatic-review`.
- If the orchestrator judges the two new facts to be a distinct actionable rule (a
  *back-off* rule rather than a *trust-the-ETA* rule), splitting them out is defensible — but
  a standalone third rate-limit lesson beside the two already in the corpus is not.

## Plan context

- Plan: `client-converter-and-javadoc-accuracy` (epic `quality-report-remediation`)
- PR #182, merged to `main` as `e02f445`.
