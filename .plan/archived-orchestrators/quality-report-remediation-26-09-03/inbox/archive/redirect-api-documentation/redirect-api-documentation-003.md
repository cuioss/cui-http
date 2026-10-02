envelope_version=1
sender_type=plan
sender_id=redirect-api-documentation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T21:08:32Z

component=plan-marshall:tools-integration-ci
category=improvement
bundle=plan-marshall

# `enqueue_corroboration` names a stronger claim than the evidence it carries

## The observation

`ci pr merge-queue` (GitHub provider) returns, on success:

```toon
enqueued: true
base_branch: main
enqueue_corroboration: "merge_queue rule active on branch"
```

The field is documented in `tools-integration-ci/standards/pr-operations.md` as "the probe
verdict", and `api-contract.md` describes the success as adding a **corroborated**
`enqueued: true`. Reading the implementation
(`workflow-integration-github/scripts/_github_pr.py`, `cmd_pr_merge_queue`) shows what the value
actually is:

```python
base_branch, discriminator, detail, probe_err = _resolve_base_queue_state(identifier, 'pr_merge_queue')
...
gh_args = ['pr', 'merge', identifier, '--auto']
returncode, _stdout, stderr = github_ops.run_gh(gh_args)
...
return {
    ...
    'enqueued': True,
    'enqueue_corroboration': detail,
}
```

`detail` is computed by the **pre-enqueue base-branch probe**, before `gh pr merge --auto` is
invoked. `enqueued: True` is then a hard-coded literal gated only on that command's exit status.
So the returned corroboration establishes:

> a merge queue is configured on the base branch

and does **not** establish:

> this PR entered that queue

Those are different propositions, and the field name asserts the second. The probe genuinely
closes the failure mode it was built for — `gh pr merge --auto` exits zero on an unqueued branch
having quietly enabled *plain auto-merge* instead — and that is real value. But it is a
precondition check, not a post-condition check, and nothing in the verb reads the queue back
after the call.

## Why it is worth recording

This was **not** a failure in this run. The enqueue was verified independently via the GraphQL
`mergeQueue.entries` query and PR #191 was genuinely at position 1; the plan merged cleanly
(squash `384a22c`). The lesson is about the evidence-to-claim gap in the contract, which is
exactly the kind of thing that only gets noticed on a run where it happened to hold.

The gap matters because the field's whole purpose is to be the thing a caller trusts instead of
checking. A consumer that reads `enqueue_corroboration` as "the enqueue is corroborated" — which
is what the name and the surrounding prose both say — will not perform the read-back, and the
one state the field cannot distinguish (queue exists on base, PR did not enter it) is precisely
the state where the read-back would have mattered.

## Proposed options (orchestrator to choose)

1. **Rename to what it is** — e.g. `base_queue_probe` / `base_queue_verdict`, and reword the
   contract from "corroborated `enqueued: true`" to "precondition-checked `enqueued: true`". Zero
   new API calls; the claim simply matches the evidence.
2. **Make the name true** — read the queue back after `gh pr merge --auto` (the GraphQL
   `mergeQueue.entries` query used manually here) and populate the field from the PR's actual
   entry, e.g. its position. Strictly better evidence, one extra API call per enqueue.

Option 1 is the cheaper and probably correct move; option 2 is what a caller reading today's
docs already believes is happening.

Note the GitLab asymmetry is instructive and should be preserved in whichever wording lands:
GitLab has no `enqueue_corroboration` because its dedicated train endpoint only succeeds against
a real train and reports the created `merge_train_car_id` — that IS post-condition evidence.
GitHub's field is the one making a pre-condition read like a post-condition one.

## Cross-repo note

Component names a `plan-marshall` bundle skill not owned by the `cui-http` lessons store;
integrate on the `plan-marshall` side.

## Evidence

- `marketplace/bundles/plan-marshall/skills/workflow-integration-github/scripts/_github_pr.py`,
  `cmd_pr_merge_queue` (the `enqueue_corroboration: detail` return, `detail` sourced from
  `_resolve_base_queue_state` before the `gh pr merge --auto` call).
- `tools-integration-ci/standards/pr-operations.md` and `standards/api-contract.md`
  (the "corroborated" wording).
- Plan `redirect-api-documentation`, PR #191 (`cuioss/cui-http`), squash `384a22c`.
