envelope_version=1
sender_type=plan
sender_id=configuration-surface-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T09:41:05Z

component=plan-marshall:manage-references
category=anti-pattern
signal=script-failure
failure_class=argparse_rejection

# `set-list --values` takes ONE comma-joined string, not repeated or space-separated values

## Observation

`manage-references set-list` was rejected with `--values: expected one argument`. The live
argparse surface is:

```text
usage: manage-references.py set-list [-h] --plan-id PLAN_ID --field FIELD --values VALUES
  --values VALUES    Comma-separated values
```

`--values` is a single-argument flag whose value is a **comma-joined string**. It is not
`nargs='+'` and it is not repeatable. Passing several space-separated tokens, or repeating the
flag, is an argparse rejection whose message (`expected one argument`) reads as "the flag is
missing its value" rather than "you passed too many", which is why the caller's next attempt
tends to add MORE values rather than joining them.

## Corrective rule

Call it as `--values "a,b,c"` — one shell argument, values joined by commas, quoted so the shell
does not split it. Do not write `--values a b c` and do not repeat `--values`.

More generally: before passing a multi-valued flag, read the flag's own help text for the word
"comma-separated". A `--values`/`--files`/`--phases`-style flag in this marketplace is far more
often a single comma-joined string than a repeatable one (`--fact` and `--lesson-id` are the
repeatable exceptions), and the two forms are indistinguishable from the flag name alone.

## Candidate scope

Global — this is the "never invent script invocation shapes; quote the declared surface" rule
applied to multi-valued flags, and it is a distinct recurrence signature from the five already
catalogued in `agent-behavior-rules.md` (all five are about verb names and flag POSITION, none
about flag ARITY). Worth considering as a sixth signature rather than a standalone lesson.
