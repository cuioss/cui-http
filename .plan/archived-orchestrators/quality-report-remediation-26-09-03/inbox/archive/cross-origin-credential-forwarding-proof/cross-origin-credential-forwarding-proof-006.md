envelope_version=1
sender_type=plan
sender_id=cross-origin-credential-forwarding-proof
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-03T09:28:49Z

# Candidate lesson: seven distinct `argparse_rejection` script failures in one run

**Class**: recurring agent-invocation defect. Every one is the "invented / mis-positioned
argument" class that `persona-plan-marshall-agent` already names as a hard rule.

## Observation

`logs/work.log` carries `[ERROR] ... script_failure` markers for **seven distinct script
notations** across phases 3 through 6. Six of the seven are `failure_kind=argparse_rejection`
(exit 2), i.e. the call never reached the script body:

| # | Time | Notation | Rejection |
|---|------|----------|-----------|
| 1 | 06:55:18 | `manage-findings` | invented verb — `qgate` needs one of `add, clear, list, resolve, resolve-evidenced` |
| 2 | 06:58:31 | `phase_handshake` | `script_internal_failure` (exit 1) — genuine drift verdict, not an invocation defect |
| 3 | 06:59:49 | `manage-solution-outline` | undeclared flag on `get-deliverable` (declared: `deliverable-number`, `plan-id`) |
| 4 | 07:29:16 | `manage-findings` | missing required `--phase` on `qgate list` |
| 5 | 07:37:27 | `manage-references` | missing required `--worktree-path` on `compute-footprint` |
| 6 | 07:56:50 | `github_pr` | missing required `--bot-kind` on `bot_completion` |
| 7 | 09:01:17 | `review_completeness` | `check` invoked outside its declared flag set |
| 8 | 09:09:25 | `ci` | `--plan-id` placed AFTER the verb on `pr view` — the router consumes it BEFORE the verb |

(Entry 2 is listed for completeness but is a different failure class and is not part of
this candidate.)

Note that entries 1 and 4 are the SAME notation failing twice, hours apart, on two
different mistakes against the same `qgate` subcommand.

Entry 8 is notable in the other direction: the executor's rejection message is
excellent — it names the flag, states that it exists, states that only the position is
wrong, and prints the corrected command verbatim. That is the model the other six
rejections do not match.

## Consequence observed

None of these corrupted state — argparse rejects before the body runs, which is the whole
point of the exit-2 contract. The cost is latency and agent-attention: eight wasted
round-trips, each requiring a `--help` walk or a re-read of the canonical-invocation block
to recover.

## Correction to the forwarded signal

The dispatcher forwarded `signal_script_failure_clusters_count: 2`. The work log carries
seven distinct failing notations under the `[ERROR] ... script_failure` marker class alone.
Whether that is a defect in the cluster counter, a narrower marker-class scan, or a
deliberate dedup I did not re-derive — but the gap between 2 and 7 is large enough that the
counter itself is worth a look. **This plan did not recompute the signal count**; the table
above is a read of the underlying log records, which the lessons-capture contract permits.

## Candidate corrective action

The hard rule already exists ("Never invent script subcommands", with five named recurrence
signatures). What this run adds is evidence that the rule is not self-enforcing at the
agent tier. Two directions worth weighing:

1. **Propagate the `ci` router's rejection quality.** Its message names the flag, asserts
   the flag exists, and prints the fixed command. Six of the seven rejections here printed
   only a declared-flag list. A rejection that prints the corrected invocation converts a
   round-trip into a copy-paste.
2. **Check whether the recurrence signatures cover these eight.** Entries 4, 5 and 6 are
   all plain "missing required flag", which is not obviously one of the five documented
   signatures. If a sixth signature is warranted, it belongs in
   `agent-behavior-rules.md` § "Never invent script subcommands — recurrence signatures".

## Evidence

- plan `logs/work.log` entries `5480b8`, `c8dca4`, `4256cb`, `771256`, `954017`, `efdd1d`, `3d6e10`, `4118b6`
