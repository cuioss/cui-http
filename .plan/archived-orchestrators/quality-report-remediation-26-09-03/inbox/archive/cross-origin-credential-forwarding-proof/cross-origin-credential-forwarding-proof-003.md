envelope_version=1
sender_type=plan
sender_id=cross-origin-credential-forwarding-proof
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-03T09:27:31Z

# Candidate lesson: `required_bots` takes a `bot_kind`, not an `author_login` — and the bad value is STILL ON MAIN

**Class**: configuration defect, live and unremediated. This candidate carries a
**correction to the run narrative**: the fix was NOT landed.

## Observation

Upstream commit `c7862d0` ("chore(config): rename the pr-agent reviewer token to
cuioss-review-bot", PR #192) changed, in this repo's tracked `.plan/marshal.json`:

```
plan.phase-6-finalize.steps["plan-marshall:automatic-review"].required_bots
  "coderabbit,pr-agent"  ->  "coderabbit,cuioss-review-bot"
```

That rename is wrong. `required_bots` is a set of **`bot_kind`** tokens. The registry
at bundle 0.1.1581 (`automatic-review/standards/pr-agent.md` lines 57-58) declares:

```yaml
bot_kind: pr-agent
author_login: cuioss-review-bot
```

The two are distinct fields of the SAME record. `cuioss-review-bot` is the login the
reviews are authored under; it is not a `bot_kind` and resolves to no registry record.
Commit #192's own rationale asserts "upstream the automatic-review `bot_kind` was
collapsed onto the reviewer's own GitHub author login" — the installed registry
contradicts that: `bot_kind` is still `pr-agent`.

## Consequence observed

The quorum became unsatisfiable for a bot that had in fact reviewed. decision.log
`d68e73` (07:55:13Z) records `unproven_bots=[coderabbit, cuioss-review-bot]` while
`participated_bots=[pr-agent:issue_comment, sourcery:review_body]` — pr-agent posted a
qualifying "PR Reviewer Guide, no issues found" review under exactly that login, and the
gate still could not credit it. It cannot ever resolve by waiting.

## The correction: only the PLAN-LOCAL snapshot was reverted

decision.log `460554` (07:55:56Z) records the revert to `coderabbit,pr-agent`, and that
revert landed in the plan's own execution manifest
(`.plan/local/plans/.../execution.toon` line 58 reads `required_bots: "coderabbit,pr-agent"`).

The tracked project config was **not** changed. At merged HEAD `ff12f34` (PR #194),
`.plan/marshal.json` line 103 still reads:

```
"required_bots": "coderabbit,cuioss-review-bot",
```

Working tree is clean, so this is the committed state on `main`. The defect will
re-fire on the very next plan this repo runs, and the plan-local revert will have to be
re-discovered from scratch each time.

## Candidate corrective action

Two separable actions:

1. **Land the config fix** — restore `required_bots` to `coderabbit,pr-agent` in the
   tracked `.plan/marshal.json`. This is a one-token config change and needs its own
   plan or an operator-driven commit; this plan's spec forbade absorbing production or
   config behaviour changes, which is why it was only corrected plan-locally.
2. **Consider a structural guard** — `required_bots` / `optional_bots` values are
   checkable against the registry's known `bot_kind` set at config-validation time
   (`marshall-steward` / `plan-doctor` territory). A token that is a known
   `author_login` but not a known `bot_kind` is a high-confidence, zero-false-positive
   misconfiguration signal, and naming it at config time costs nothing versus
   discovering it as a hung merge gate.

The trap generalises: the two concepts are near-identically named and live as adjacent
keys in one record, so a rename of one reads as a rename of the other.

## Evidence

- `git show c7862d0 -- .plan/marshal.json`
- `.plan/marshal.json` line 103 at HEAD `ff12f34` (still `cuioss-review-bot`)
- `.plan/local/plans/cross-origin-credential-forwarding-proof/execution.toon` line 58
- `automatic-review/standards/pr-agent.md` lines 57-58
- plan decision.log entries `f12b99`, `d68e73`, `460554`
