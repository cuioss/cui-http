envelope_version=1
sender_type=plan
sender_id=redirect-api-documentation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T21:09:15Z

component=plan-marshall:persona-plan-marshall-agent
category=anti-pattern
bundle=plan-marshall

# Three argparse-rejection sub-signatures the existing catalogue does not cover

## Recommendation up front: ONE candidate, and it is an EXTEND, not a new rule

The run produced ten `argparse_rejection` (exit 2) script failures across seven distinct
notations. They share a single root cause — a plausible-sounding verb, flag, or enum value used
without consulting the canonical surface first — which
`persona-plan-marshall-agent/standards/agent-behavior-rules.md` § "Never invent script
subcommands" **already owns**, complete with a five-entry recurrence-signature catalogue. Filing
a general "consult the canonical surface first" lesson would duplicate that clause.

What is NOT covered is three specific sub-signatures observed here. The proposal is to add them
to that existing catalogue. The active lessons corpus was checked (`manage-lessons list`) and
holds no lesson on this subject, so there is no lesson-level duplicate — the duplication risk is
against the standard, not against the corpus.

## The three uncovered sub-signatures

### (a) Composed near-miss flag — both halves exist, the composition does not

`manage-findings qgate resolve --resolution-detail` was rejected; the declared flags are
`['detail', 'hash-id', 'phase', 'plan-id', 'resolution']`. Both `--resolution` and `--detail`
exist as separate real flags, and `--resolution-detail` is their plausible composition. The
existing catalogue's entries all describe an *invented* name; this one is assembled entirely
from *correct* names and is therefore much harder to self-catch by "does this sound made up?".

### (b) Correct flag, invented ENUM VALUE

`manage-findings add --type test-coverage` was rejected; `--type` is a real flag whose declared
enum is the fourteen values `{bug, improvement, anti-pattern, triage, tip, insight,
best-practice, build-error, test-failure, lint-issue, sonar-issue, arch-constraint, pr-comment,
pr-comment-overflow}`. Every existing catalogue entry is about the verb or the flag NAME. A
closed value set is a third surface that can be invented against, and `test-coverage` is
especially seductive next to the real `test-failure`.

### (c) `--plan-id` on a script that declares it NOWHERE

`manage-config ... --plan-id redirect-api-documentation` was rejected with
`unrecognized arguments`. The catalogue already carries two `--plan-id`-position signatures
(verb-scoped, and the CI router's pre-verb rule) — both of which presuppose the flag exists
*somewhere* on the script and only the position is wrong. `manage-config` declares it on no verb
at all, so the corrective move is neither "move it before the verb" nor "move it after": it is
"append nothing". That is a third disposition the existing two entries do not produce.

The other seven rejections (unregistered verbs on `manage-status`, `manage-files`,
`manage-config`, and `manage-findings qgate`; the invented `manage-references add-to-list` for
the canonical `add-list`; an undeclared flag on `manage-solution-outline get-deliverable`; a flag
misuse on `automatic-review:review_completeness`) are all clean instances of signatures already
in the catalogue, and need no new entry.

## Two items from the dispatch note that did NOT verify

The dispatch prompt listed five specifics. Three verified against the work log (`add-to-list`,
`--resolution-detail`, `--type test-coverage`). Two did not, and are dropped rather than passed
along:

- **`manage-tasks add` rejected in favour of `prepare-add` / `commit-add`** — no `manage-tasks`
  `script_failure` line exists in this plan's work log. Every `[MANAGE-TASKS]` entry is a
  success (`batch-add created 6 tasks`, per-step `done`, per-task `Completed`). Not corroborated.
- **A task-contract violation where `steps` needed `path (intent)` form rather than prose** — no
  matching failure or validation-error entry found in the work or decision logs. Not
  corroborated.

Both may be real recollections from an adjacent context; neither is evidenced by this plan's
records, so this candidate does not assert them.

## Cross-repo note

Component names a `plan-marshall` bundle skill not owned by the `cui-http` lessons store;
integrate on the `plan-marshall` side per the orchestrator's cross-repo integrate-then-remove
path.

## Evidence

Plan `redirect-api-documentation` work log, `[ERROR] ... script_failure ...
failure_kind=argparse_rejection` lines: hash ids `b28086` (manage-status), `5480b8`
(manage-findings qgate), `4256cb` (manage-solution-outline), `480a71` (--resolution),
`e5d46d` (manage-references add-list), `b423ec` (manage-config --plan-id), `518792`
(manage-files), `3d6e10` (review_completeness), `8bba6f` (manage-findings add --type),
`0637fd` (manage-config verb).
