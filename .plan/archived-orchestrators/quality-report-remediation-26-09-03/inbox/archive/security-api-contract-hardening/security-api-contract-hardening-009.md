envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T15:51:33Z

# Candidate lesson: sonar fetch_findings invoked twice with an invalid flag set, then with a missing required flag

**Signal source**: `signal_script_failure_clusters_count` — one distinct failing notation,
`plan-marshall:workflow-integration-sonar:sonar`, behind two consecutive
`[ERROR] ... script_failure` markers in the `6-finalize` work log.

## The two failures

Both at `default:sonar-roundtrip`, seconds apart, both `exit_code=2`,
`failure_kind=argparse_rejection`:

1. `2026-08-29T15:22:06Z` — "Use a declared flag for
   `plan-marshall:workflow-integration-sonar:sonar fetch_findings`:
   `['ce-wait-timeout', 'plan-id', 'pr', 'project', 'severities', 'types']`"
   → an undeclared flag was passed; the rejection enumerated the accepted set.
2. `2026-08-29T15:22:12Z` — "Add the required flag(s) to
   `plan-marshall:workflow-integration-sonar:sonar fetch_findings`: `['project']`"
   → the retry dropped the bad flag but omitted a REQUIRED one.

The step recovered (`sonar-roundtrip` completed `outcome=done` at 15:29:41Z, and finding
`340fdb` — Sonar `java:S127` — was subsequently filed and fixed), so this cost a
guess-and-retry cycle rather than a failure.

## Candidate rule

This is the argparse-rejection recurrence signature the persona rules already name
("Never invent script subcommands / flags"), instantiated on the `sonar` surface. What is
notable is the SHAPE of the retry, and that is the part worth capturing:

**Rejection 1 handed back the complete accepted flag set. Rejection 2 was still avoidable
from that output alone** — `project` was listed there, in the accepted set, and was simply
not supplied on the retry. The caller corrected the reported error without re-reading the
information the same error message contained.

Candidate rule: when an argparse rejection enumerates the declared flag set, the retry MUST
be constructed against that enumerated set in full — checking both "is every flag I pass in
the set" and "is every required flag in the set present" — rather than by deleting the one
token the message complained about. A rejection that names the whole surface should cost one
retry, never two.

Weaker but worth stating: `fetch_findings`' `--project` is required and is not derivable
from `--plan-id`, so a call site that has the plan id still needs the Sonar project key
independently. Whether that is a documentation gap in
`plan-marshall:workflow-integration-sonar` or just a call-site error is a judgement this
plan cannot make from one occurrence.

## Recurrence question for the orchestrator

Whether the same notation fails the same way in sibling plans of this epic is exactly the
cross-plan question that distinguishes "one caller guessed" from "the canonical-invocation
block for `fetch_findings` is under-specified". This plan transmits the observation only.

## Provenance

- Plan: `security-api-contract-hardening`
- Work-log entries: `67e035` (15:22:06Z), `b2b9ea` (15:22:12Z), both level ERROR
- Step: `default:sonar-roundtrip`, which subsequently completed `outcome=done`
