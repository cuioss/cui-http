envelope_version=1
sender_type=plan
sender_id=exception-sanitisation-and-config-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-08T13:43:41Z

component=plan-marshall:persona-plan-marshall-agent
category=anti-pattern
created=2026-09-08

# Five distinct argparse rejections in one run: two matched a documented recurrence signature, three were a flag-VALUE shape the checklist does not cover

One plan run produced argparse rejections across **five distinct script
notations**. The existing "Never invent script subcommands" recurrence-signature
checklist caught the shape of two of them and does not describe the other three
at all.

## The five

**Matches an existing signature (verb-paraphrase, signature 1):**

1. `plan-marshall:workflow-integration-git:git-workflow commit` — no `commit`
   verb exists; the registered set is `['analyze-diff', 'baseline-reconcile',
   'branch-sync-state', 'detect-artifacts', 'force-push-with-lease',
   'format-commit', 'locate-plan-checkout', 'prune-local-and-remote-ref',
   'switch-and-pull', 'worktree-*']`. The caller fell back to plain `git commit`.
2. `plan-marshall:manage-plan-documents:manage-plan-documents` invoked with a
   verb outside its only two top-level choices, `['list-types', 'request']`.

**Matches NO existing signature — flag-value shape, not verb name or flag
position:**

3. `manage-references set-list --field F --values` — `--values` supplied with **no
   argument**. An arity error, not a naming error.
4. `review_completeness check --participated-bots coderabbit` — the flag takes
   `bot_kind:evidence_kind` **pairs**; a bare `coderabbit` is a well-spelled flag
   carrying a value of the wrong grammar. Rejected twice at argparse, then once
   more inside the script as `malformed_bot_flag`. (The script's own error text is
   exemplary: it explains that silently dropping the bare token would resolve the
   bot to absent and manufacture a false merge block — so it refuses.)
5. `github_re_review re-review` — missing the **required** `--push-time` flag on
   the sub-verb. Not a wrong name and not a wrong position: a required flag simply
   absent.

## Root cause

The documented recurrence signatures are all about **which token** and **where**:
verb-paraphrase, flag declared on the router vs on the sub-verb (signatures 2 and
4, mirrors of each other), doubled bundle prefix, and one specific missing-`--phase`
case. Cases 3-5 are a different axis entirely — the verb is right, the flag name is
right, the position is right, and the **value grammar or the required-flag set** is
wrong. Nothing in the checklist prompts a check on that axis, so a caller that has
diligently run the checklist still writes these.

Case 4 is the sharpest: `coderabbit` is a real bot kind, and the flag is the right
flag. Only the *pair* grammar makes it invalid, and that grammar is discoverable
only from the flag's own help text or the script body.

## Solution

Extend the self-audit to a sixth signature, covering the value axis:

> **Flag-value shape and required-flag presence** — a correctly named flag in the
> correct position, carrying a value of the wrong grammar (a bare token where the
> parser wants a `k:v` pair, a CSV where it wants repetition), or a **required**
> sub-verb flag omitted entirely. Before invoking any verb with a
> structured-looking value, read that flag's help text for its grammar — the flag
> NAME being right is not evidence the VALUE shape is.

And reinforce the existing escape hatch: `{notation} {subcmd} --help` answers all
three axes (verb, position, value grammar) in one call, and is cheaper than any of
these five rejections.

## Impact

Five distinct notations in a single plan run, across four different skills, is a
rate the current guidance is not holding down. Three of the five are outside every
documented signature, so a caller following the checklist exactly still produces
them.
