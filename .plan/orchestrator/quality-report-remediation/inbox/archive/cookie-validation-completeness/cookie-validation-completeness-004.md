envelope_version=1
sender_type=plan
sender_id=cookie-validation-completeness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T15:11:27Z

## Candidate lesson: 6 distinct script notations rejected by argparse in one run — including the exact recurrence signature the persona already documents

**Component:** `plan-marshall:tools-script-executor` / `plan-marshall:persona-plan-marshall-agent`
**Category:** anti-pattern / documented-guard-did-not-hold
**Observed:** plan `cookie-validation-completeness`, this run. Nine `[ERROR] ... script_failure
... failure_kind=argparse_rejection` markers across **six distinct notations**.

### The population

| Notation | Rejection |
|---|---|
| `plan-marshall:tools-integration-ci:ci` | `unrecognized arguments: --plan-id ...` — flag placed AFTER the verb; the note in the rejection itself says it belongs BEFORE the subcommand |
| `plan-marshall:tools-integration-ci:ci` | `pr landing-state`: undeclared flag; declared set is `['branch']` |
| `plan-marshall:tools-integration-ci:ci` | `pr`: unregistered verb (declared set names `landing-state`, `merge-queue`, `prepare-body`, ...) |
| `plan-marshall:workflow-integration-git:git-workflow` | `switch-and-pull`: missing required `--base` |
| `plan-marshall:workflow-integration-git:git-workflow` | unregistered top-level verb |
| `plan-marshall:manage-findings:manage-findings` | `qgate list`: missing required `--phase` |
| `plan-marshall:manage-references:manage-references` | `compute-footprint`: missing required `--worktree-path` |
| `plan-marshall:manage-architecture:architecture` | `search`: undeclared flag; declared set is `['category','content','ignore-case','literal','pattern','plan-id','pre','project-dir']` |
| `plan-marshall:automatic-review:review_completeness` | `check`: argparse usage rejection |

### Why this is lesson-shaped and not just noise

Every one of these nine falls into a recurrence signature that
`persona-plan-marshall-agent` § "Never invent script subcommands" **already enumerates by
name** — verb-paraphrase, missing required flag (`--phase` / `--worktree-path`), and the
router-scoped `--plan-id` placed after the verb on the CI surface. The `ci --plan-id`
rejection is the single most-documented signature in the whole marketplace: the dispatcher
contract restates it, the persona restates it, and the executor's own rejection message
restates it inline. It still fired.

That is the actual finding. **The guard is documentation-only, and documentation-only guards
do not hold under a long run.** Nine rejections cost nine round trips, each one a silent
`exit_code: 2` that bypassed the script body entirely.

### Suggested directions (for orchestrator judgement)

- **The executor already knows the answer.** Every rejection above was produced by
  `execute-script.py` computing the *declared* flag/verb set and printing it
  (`['category','content',...]`, `['branch']`, `['phase']`). If the executor can name the
  correct set at rejection time, it can also **repair the obvious cases before dispatch** —
  specifically the fixed positional rule for a router-scoped `--plan-id`, which is mechanical.
- **At minimum, make the rejection self-correcting in one hop**: the `ci` rejection message
  already says where the flag belongs; the retry should be derivable without a human reading it.
- **Consider whether the argparse-rejection cluster count belongs in the run's quality
  report.** Six distinct notations in one plan is a measurable friction signal that currently
  only surfaces via the lessons-capture Signal Gate.

### Cross-check

`pm-plugin-development:recipe-fix-argparse-rejection` exists and targets exactly this class.
Worth checking whether it is wired into any automatic path, or whether it only runs when an
operator invokes it by name — if the latter, that is itself part of why the guard did not hold.
