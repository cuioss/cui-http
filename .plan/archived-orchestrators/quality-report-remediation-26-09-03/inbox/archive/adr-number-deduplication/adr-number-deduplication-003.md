envelope_version=1
sender_type=plan
sender_id=adr-number-deduplication
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T13:44:16Z

# Candidate lesson: the argparse-rejection guard is edit-time only, and the recurrence is at the orchestrator tier

## Observation

All five script-failure clusters in this run were argparse rejections of the
documented "Never invent script subcommands" class. **Four of the five were
issued by the main-context orchestrator**, guessing a verb or flag name rather
than reading the surface first. Only one came from anywhere else.

That distribution, not the individual rejections, is the durable finding.

## Why this is a recurrence datum rather than a new rule

The rule is present, correct, and **loaded** at the tier that violated it:

- `persona-plan-orchestrator/SKILL.md` line 41 declares
  `plan-marshall:persona-plan-marshall-agent` as the *"unconditional
  foundational base every persona inherits"*.
- `persona-plan-marshall-agent/SKILL.md` carries "Never invent script
  subcommands" as an inline **hard rule (never override)**, pointing at the
  five-signature checklist in `standards/agent-behavior-rules.md`.

So this is not a coverage gap in the prose and not a loading gap in the persona
graph. The rule was in context, at the top of the hard-rules list, and was
violated four times in one epic-orchestrated run. Filing a fifth restatement of
"read the surface first" would be recording the rule, not the recurrence.

## The actual gap

The rule names exactly one structural guard:

> The `ARGUMENT_NAMING_*` plugin-doctor rule cluster (unconditionally active
> under `quality-gate`) catches drift at edit time as the structural guard
> against this class of failure.

That guard is **edit-time**: it inspects invocations *written into marketplace
documents*. It cannot fire on an invocation the orchestrator *composes at
runtime and hands to Bash*, which is precisely how all five of this run's
rejections were produced. The orchestrator tier is therefore the one tier where
the class has a documented rule, a documented checklist, and **no structural
guard at all** — enforcement there is entirely self-discipline, and the
observed self-discipline rate in this run was 1-in-5.

## Proposed durable content (options, for the orchestrator to weigh)

1. **A runtime pre-flight seam.** A cheap, script-side check that resolves
   `{notation} {subcmd}` against the live argparse surface *before* the call is
   issued — the runtime analogue of `manage-invocation-invalid`, which already
   derives its accept-set from a live `--help` walk. The accept-set machinery
   exists; what does not exist is a caller-facing verb that consults it.
2. **A rejection-counting signal.** `signal_script_failure_clusters_count` is
   already computed per-run by the finalize dispatcher. Attributing each
   cluster to its issuing tier (orchestrator vs dispatched leaf) would make the
   1-in-5 distribution above measurable across runs instead of anecdotal — and
   would tell the marketplace whether this is one bad run or a standing
   property of the orchestrator tier.
3. **Accept the residual and stop re-filing it.** If neither guard is worth its
   cost, the honest disposition is to record that the class is *known,
   prose-guarded, and structurally unguarded at the orchestrator tier* — so the
   next run's five rejections are recognised as this same accepted residual
   rather than re-triaged from scratch.

## What NOT to do

Do not file one lesson per rejected notation. The five notations
(`manage-solution-outline`, `manage-execution-manifest`, `manage-architecture`,
`tools-integration-ci`, `workflow-integration-git`) have nothing in common
except the caller. A per-notation lesson would attribute a caller-discipline
defect to five innocent scripts and would grow the corpus by five entries that
`manage-lessons aggregate` would then have to re-merge.

## Evidence

Five of five script-failure clusters in plan `adr-number-deduplication`
(epic `quality-report-remediation`); four attributed to the main-context
orchestrator. Other signals this run: `signal_qgate_pending_count: 0`,
`signal_automated_review_count: 0`.
