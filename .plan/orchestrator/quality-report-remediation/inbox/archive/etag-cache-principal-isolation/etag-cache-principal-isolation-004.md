envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:31:35Z

# Candidate lesson: recurring `--plan-id` flag-position argparse rejections on two scripts in one finalize run

Source signal: `signal_script_failure_clusters_count` (distinct failing notations observed in the work log).
Component: `plan-marshall:manage-architecture`, `plan-marshall:tools-integration-ci`.

## What happened

Two distinct `exit_code=2 failure_kind=argparse_rejection` script failures fired inside the
6-finalize window, both on the SAME root cause — `--plan-id` written in the wrong position
relative to the verb:

- `plan-marshall:manage-architecture:architecture` — `--plan-id` written after the subcommand;
  the parser declares it as a TOP-LEVEL flag, so the call was rejected with
  `unrecognized arguments: --plan-id etag-cache-principal-isolation`.
- `plan-marshall:tools-integration-ci:ci` — `--plan-id` written after the verb
  (`ci checks status --pr-number 240 --plan-id X`); the `ci` router consumes it only ahead of the
  first verb token, so the same rejection fired.

Both rejections bypassed the script body entirely. Both scripts' rejection messages were good —
each printed the caller's own invocation with the flag moved — which is why recovery was fast.

## Corrective rule

`--plan-id` position is per-script and per-verb, never uniform. Before appending it, check the
script's canonical-invocation block:
- top-level/router-consumed (`architecture`, and `ci`'s read verbs) → BEFORE the verb;
- subcommand-declared (`ci prepare-body` / `prepare-comment`, most `manage-*` verbs) → AFTER the verb;
- undeclared → append nothing.

## Generalisation for the epic

This is recurrence signatures 2 and 4 from `agent-behavior-rules.md` firing twice in one run, on
two different surfaces, in opposite directions. Two mitigations worth weighing at epic level:
(a) the argparse rejection messages already name the fix — make sure every `manage-*` surface
emits that same self-correcting note; (b) consider whether `--plan-id` can be accepted in BOTH
positions on the router surfaces, since the flag's meaning is unambiguous either way.
