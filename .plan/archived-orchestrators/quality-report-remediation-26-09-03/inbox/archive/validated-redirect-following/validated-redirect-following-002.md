envelope_version=1
sender_type=plan
sender_id=validated-redirect-following
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T19:55:19Z

component=plan-marshall:phase-6-finalize
category=improvement
bundle=plan-marshall

# The finalize review loop cannot converge while each fix push re-triggers a fresh bot review round

## Observation

Plan `validated-redirect-following` (PR #186) ran the finalize review loop to
its full 3/3 loop-back ceiling and still did not converge on its own. The shape
was identical on every round:

1. Triage the current bot findings (CodeRabbit, CodeQL/GitHub Advanced
   Security, SonarCloud).
2. Fix them, commit, push.
3. The push is a new HEAD, so every bot re-reviews from scratch and files a
   fresh round of findings against the *fix*.

The run terminated because the loop-back ceiling was reached and the operator
chose to stop re-reviewing — not because the findings stream ran dry.

## Why this is reusable

The loop's termination condition is "no pending findings at the current HEAD",
but the loop's own remediation action *changes* HEAD, which re-arms the
producer. That is a structurally non-converging feedback loop for any PR whose
bots review on every push. It is not specific to this plan or this repo.

Observed contributing factor: each round's findings were genuinely new and
mostly non-trivial (they were about the previous round's fix), so a
"findings are just noise, ignore them" heuristic would have been wrong. The
ceiling is doing real work as a circuit-breaker, but it is the *only* thing
terminating the loop.

## Suggested rule

Consider making the finalize loop's exit condition explicit rather than
incidental, e.g. one of:

- Distinguish **regression findings** (a finding against a line the previous
  round's fix introduced) from **backlog findings** (pre-existing), and let
  only the former re-arm a loop-back; the latter become follow-up plan
  candidates.
- Make the ceiling's terminal state an explicit, recorded verdict
  ("converged" vs "ceiling reached, N findings deferred") so a run that
  stopped at the ceiling is visibly different from one that stopped clean, and
  the deferred findings are scheduled rather than dropped.
- Prompt the operator at the ceiling with the deferred-finding list rather
  than requiring the operator to notice the loop is not converging.

## Evidence

Plan `validated-redirect-following`, PR cuioss/cui-http#186. Loop-back count
3 of 3. Findings store carries 61 records, including 5 `pr-comment` inline
comments plus 1 review-body comment from CodeRabbit and 3 `sonar-issue`
records, filed across successive rounds.
