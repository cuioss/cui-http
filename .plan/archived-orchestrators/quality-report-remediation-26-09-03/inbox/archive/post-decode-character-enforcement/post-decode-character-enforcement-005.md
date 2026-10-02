envelope_version=1
sender_type=plan
sender_id=post-decode-character-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T11:26:30Z

component=plan-marshall:automatic-review
category=bug
bundle=plan-marshall

# "0 new comments" and "no re-review happened" are the same observable, so a loop-back that fixed a bot's own finding reads as bot-approved

## What happened

`re_review_on_loopback: false` was in effect. The run loop-backed to fix a review bot's own
finding, pushed the fix, and then re-fired the FIND step. It returned **"0 new comments"**.

That result was reported as "re-reviewed and clean". It was not. No bot ever looked at the
fix: every review on PR #159 stayed anchored to the original commit `3e5433d`. The zero meant
*nobody re-reviewed*, not *a reviewer re-read the changed code and had nothing to add*.

The orchestrator initially reported it the wrong way and only corrected itself after manually
inspecting the review commit anchors — evidence that the correct reading is not recoverable
from the call site's return value, only from a separate observation the call does not make.

## Why this generalizes

This is the *which-kind-of-zero* discriminator problem, and this codebase already treats it
as a first-class design obligation nearly everywhere else:

- `inbox list` carries `inbox_state: present|missing` so a `count: 0` says whether the
  directory was scanned or absent — and carries `closed_senders` + `invalid_count` so an
  empty drain distinguishes EMPTY / FINISHED / BLOCKED, three zeros rather than two.
- `manage-lessons list-stalled` carries `store_resolution`, `plans_root_state`, and
  `unresolved_store` so a `stalled_count: 0` says which kind of zero it is.
- `restore-from-plan` splits `no_lesson_file` from `plan_dir_unresolved` for exactly this
  reason, and the skill body states the rule outright: reporting an unreachable directory as
  lesson-free is a fail-open.

The re-review surface violates the same rule, and violates it on the **safety-critical**
side: the zero is consumed as evidence that a reviewer approved the post-fix state, which is
the one conclusion the observation cannot support. It is a fail-open of the review barrier —
the gate reports satisfied on evidence that the gate never ran.

The general statement: **a count returned by a query is only interpretable alongside a fact
about whether the query's subject was observed at all.** When the observation may not have
happened, the result must carry a discriminator saying so. Emitting a bare `0` forces every
consumer to guess, and the plausible guess is the wrong one.

## Proposed remedy

The remedy is the one the codebase already uses, applied here:

1. **The FIND result carries the review anchor.** Return the commit SHA each participating
   bot's latest review is anchored to, alongside the new-comment count. A caller can then
   compare against the pushed HEAD without a manual inspection.
2. **Derive an explicit re-review state** over a closed vocabulary, so `0` is never bare:
   - `rereviewed_clean` — a bot review exists anchored at or after the fix commit, with no
     new comments.
   - `not_rereviewed` — every review is anchored to a pre-fix commit. This is the state that
     produced the misreport, and it must never render as "clean".
   - `no_participation` — the bot never reviewed at all.
3. **Make the barrier route on the state, not the count.** `not_rereviewed` is a gap the
   merge-authorization machinery already has a vocabulary for (`review-barrier-gap`); it
   should reach that path rather than passing silently.

`re_review_on_loopback: false` may well remain a legitimate configuration — the defect is not
the setting, it is that the setting's consequence is unobservable at the call site.

## Impact

Every loop-back that remediates a review-bot finding under
`re_review_on_loopback: false` — i.e. the normal path for the defect class review bots are
best at catching. The failure is silent, and it is a fail-open on a merge gate.
