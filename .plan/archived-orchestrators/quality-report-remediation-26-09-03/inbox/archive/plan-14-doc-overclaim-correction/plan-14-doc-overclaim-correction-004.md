envelope_version=1
sender_type=plan
sender_id=plan-14-doc-overclaim-correction
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-26T19:26:46Z

component=plan-marshall:tools-integration-ci
category=improvement
bundle=plan-marshall

# A zombie GitHub Actions run is unrecoverable in place — detect it and recover by producing a new SHA

plan-14-doc-overclaim-correction lost 75+ minutes waiting on two GitHub Actions runs
that could never reach a terminal state:

- A `push`-event run reporting `conclusion=failure` / `status=completed` at the run
  level while every one of its jobs stayed `status=queued`. The run was already
  "finished" so it could not be cancelled, and it could not be re-run because the
  re-run surface acts on jobs that never started.
- A `pull_request`-event run stuck in `startup_failure`. Neither rerunnable nor
  cancellable.

Neither is a slow run. Both are terminal-but-wrong states on GitHub's side, and no
amount of additional waiting changes them. The await loop, however, treats "not yet
green" as "keep waiting", so the default behaviour is to burn the entire timeout
budget on a run that was dead on arrival.

The signature is cheap to recognise and is what makes this actionable:

- run-level `status=completed` with `conclusion=failure`, but **zero** jobs having
  ever left `queued` — a real failure has at least one job in `completed` with a
  failing conclusion; and
- run-level `startup_failure` on any event.

## Solution

**Detect.** When a CI await is not progressing, do not simply extend the timeout —
inspect the JOB-level states, not just the run conclusion. A run whose jobs are all
still `queued` while the run itself claims to be completed is a zombie, full stop.
Similarly `startup_failure`. Both should short-circuit the await immediately rather
than consuming the remaining budget.

**Recover.** A zombie run is bound to its head SHA. The only way past it is a NEW
SHA, because that is what makes GitHub schedule fresh runs. Where the commit content
is already correct, the sanctioned move is an operator-approved
`git commit --amend --no-edit` plus force-push, producing a byte-identical tree under
a new SHA. That is what unwedged this run.

Two constraints on the recovery:

- It rewrites published history, so it requires explicit operator approval. It is
  not an automatic remediation.
- Verify the tree really is byte-identical afterwards (`git diff` against the
  pre-amend commit is empty), so the recovery is provably a re-trigger and not a
  silent content change riding an infrastructure workaround.

An empty commit is the alternative when amending is unacceptable, at the cost of a
junk commit in history.

## Impact

Applies to any GitHub-Actions-gated plan. The value is almost entirely in the
detection half: the recovery is a well-known move, but without a zombie check the
await loop cannot tell "CI is slow" from "CI will never answer", so it defaults to
waiting out the full timeout on every occurrence.
