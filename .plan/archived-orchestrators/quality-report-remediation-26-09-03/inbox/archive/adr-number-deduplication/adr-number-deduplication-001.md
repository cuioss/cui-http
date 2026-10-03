envelope_version=1
sender_type=plan
sender_id=adr-number-deduplication
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T13:43:52Z

# Candidate lesson: a sixth argparse-rejection recurrence signature — cross-script verb misattribution

## Observation

Two of this run's five script-failure clusters were the *same* rejection, on two
different notations, for the same guessed verb:

- `plan-marshall:manage-execution-manifest` — invented verb `build-decision`
- `plan-marshall:manage-architecture` — invented verb `build-decision`

`build-decision` is **not an invented name**. It is a real, declared verb — it
lives on `plan-marshall:manage-config`. What was invented was its *ownership*.

## Why the existing checklist does not cover this

`persona-plan-marshall-agent/standards/agent-behavior-rules.md`
§ "Never invent script subcommands — recurrence signatures" enumerates five
signatures. Signature 1 (**verb-paraphrase**) is the nearest, and it does not
fit: it describes "synthesizing a verb that *names the goal* rather than
quoting the declared subcommand … does not exist in the argparse `choices`".
Its examples (`qgate query`, `update-status`, `show`, `start`) are all names
that exist nowhere.

The failure here has the opposite epistemic shape. The agent's recollection
that the verb EXISTS was correct; its recollection of WHICH SCRIPT DECLARES IT
was wrong. Signature 1's self-audit question ("is this verb real, or did I make
it up from the prose?") returns *"real"* and passes the call through. So the
checklist, applied honestly, does not catch this cluster — which is exactly why
it recurred twice in one run.

## Proposed durable content

Add a sixth row to the recurrence-signature checklist:

> **Cross-script verb misattribution** — applying a REAL verb to the wrong
> notation. The verb name resolves in memory because it genuinely exists
> somewhere in the marketplace; only its owning script is wrong. The tell is
> that recall is verb-first ("there is a `build-decision` verb") rather than
> notation-first ("`manage-execution-manifest` declares X, Y, Z"). Example:
> `manage-execution-manifest build-decision` / `manage-architecture
> build-decision` → canonical `manage-config build-decision`. The self-audit
> question is not "does this verb exist?" but **"does THIS notation declare
> it?"**, and the resolving action is `--help` against the *notation*, not a
> recall check on the verb name.

## Scope judgement (for the orchestrator)

This is proposed as a checklist ROW, not a new standard. The governing rule
("Never invent script subcommands") already exists and is correct; the gap is
that its self-audit checklist has a hole a real-verb/wrong-owner call passes
straight through. Consider whether the `ARGUMENT_NAMING_*` plugin-doctor
cluster can flag a documented invocation whose verb is declared by a different
script in the same bundle — that would be the structural, edit-time form of
this row.

## Evidence

Two of five script-failure clusters in plan `adr-number-deduplication`
(epic `quality-report-remediation`); both issued by the main-context
orchestrator.
