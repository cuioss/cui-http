envelope_version=1
sender_type=plan
sender_id=post-decode-character-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T11:26:20Z

component=plan-marshall:automatic-review
category=improvement
bundle=plan-marshall

# A flag whose value is a compound pair needs its grammar in the canonical-invocation block, not just its name

## What happened

`automatic-review:review_completeness` declares `--participated-bots` and
`--stale-participation-bots`. Both take values of the form `bot_kind:evidence_kind` — a
compound pair. The call supplied bare bot names (`coderabbit`, `gemini`) and was rejected.

The flag NAMES read as list-of-bots flags. Nothing at the call site distinguishes
"a repeated flag taking one bot name each" from "a repeated flag taking one
`bot_kind:evidence_kind` pair each", because the canonical-invocation block shows the flag
spelled `--participated-bots BOTS` — a placeholder that describes the *cardinality* of the
value and says nothing about its *grammar*.

## Why this generalizes

This is a distinct rejection class from the four documented recurrence signatures. Those are
all about the *shape of the argv*: which verb, which prefix, where the flag goes. This one is
about the *shape of a value*, and it is invisible to every check that reasons about flag
names:

- The flag name is correct.
- The flag position is correct.
- The verb, script, and notation are all correct.
- Only the value's internal grammar is wrong, and the value is a plain string as far as
  argparse's type system is concerned — so the rejection comes from a hand-rolled parse
  inside the script body, with whatever message that parse happens to emit.

Any flag whose value carries internal structure has this property: `KEY=VALUE`
(`mark-step-done --fact`), `bot_kind:evidence_kind`, CSV enums, `bundle:skill:script`
notations, JSON-array values (`update-field --value`). A caller reading the canonical block
sees `--fact FACT` or `--participated-bots BOTS` and supplies the most natural scalar.

Note that the codebase already solves this correctly in places — `mark-step-done`'s
`--fact KEY=VALUE` puts the grammar in the placeholder itself, and its skill body carries an
explicit parsing/rejection subsection. That is the pattern; it is just not applied uniformly.

## Proposed rule

For any flag whose value has internal structure, the canonical-invocation block must:

1. **Put the grammar in the placeholder**, not a generic noun:
   `--participated-bots BOT_KIND:EVIDENCE_KIND` (repeatable), never
   `--participated-bots BOTS`.
2. **Show one worked value** in the invocation example, using a real pair.
3. **Name the rejection code** for a malformed value in the Error Responses table, the way
   `mark-step-done` names `invalid_fact` and echoes the offending token.

A plugin-doctor rule is derivable from (1): a flag whose script-side parse splits its value
on a separator, but whose documented placeholder contains no separator, is a documentation
gap that can be detected structurally.

## Impact

Every compound-valued flag in the `manage-*` and workflow-integration surfaces. The cost per
occurrence is one wasted call plus the caller's re-derivation of a grammar that was already
known to the script author.
