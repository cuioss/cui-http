envelope_version=1
sender_type=plan
sender_id=test-evidence-integrity
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T14:02:20Z

# Candidate lesson: `manage-run-config commit-trailer get` is a live doc/executor mismatch — a documented verb that is not registered

## Signal class

`signal_script_failure_clusters_count` — script-failure cluster 1 of 3.

## Failing invocation

```text
plan-marshall:manage-run-config:run_config commit-trailer get
  -> exit 2, unknown_verb
```

## What happened

`workflow-integration-git` documents the verb `commit-trailer get` on `manage-run-config`. That verb is **not registered in the executor's parser**, so every caller that follows the documenting skill's instruction receives an argparse rejection (exit 2, `unknown_verb`) rather than a value.

This is not an invented-verb error on the caller's side — the caller did exactly what the authoritative doc said. It is a live producer/consumer contract break between a documenting skill and the script it documents.

## Why it is generalisable

1. **A verb named in a workflow doc is a contract, and nothing was checking it.** The marketplace already has the machinery for this class (`manage-invocation-invalid` derives its accept-set from a live `--help` walk), so the gap here is one of coverage: this particular doc/script pair is not being walked, or the verb is named in a form the walk does not recognise.
2. **The failure mode is maximally confusing at the call site.** Exit 2 with `unknown_verb` reads to an agent like *it* got the verb wrong, which is the single most heavily-drilled agent error class ("never invent script subcommands"). The agent's correct response — trust the doc — is indistinguishable from its most-warned-against error. That drives expensive second-guessing rather than a fast "the doc is stale" diagnosis.
3. **A doc that names a non-existent verb is worse than a doc that names none**, because it converts a lookup into a dead end with a misleading error.

## Proposed corrective actions

- Reconcile the two sides: either register `commit-trailer get` on `manage-run-config`, or correct `workflow-integration-git` to name the verb that actually exists (and, if the capability is genuinely absent, say so rather than pointing at a phantom).
- Extend the doc/executor accept-set walk to cover verbs named in `workflow-*` skills' prose bodies, not only in `Canonical invocations` blocks — this verb was reached from prose.
- When an agent hits `unknown_verb` on a verb it read verbatim from an authoritative doc, the correct disposition is **report a doc/executor mismatch**, not silently paraphrase toward a verb that does parse. Paraphrasing is how a stale doc becomes an invisible one.
