envelope_version=1
sender_type=plan
sender_id=post-decode-character-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T11:26:15Z

component=plan-marshall:tools-script-executor
category=improvement
bundle=plan-marshall

# Five argparse rejections in one run: both existing guards are blind at invocation time

## What happened

A single plan run hit five distinct invocation rejections, across five different notations:

1. `plan-marshall:tools-integration-ci:ci` — router-scoped `--plan-id` placed AFTER the verb.
2. `plan-marshall:manage-architecture:manage-architecture` — doubled bundle-prefix notation.
3. `plan-marshall:workflow-integration-github:review_completeness` — script name belongs to
   `automatic-review`, not to the github provider.
4. `plan-marshall:workflow-integration-github:github_ops review_completeness` — unknown verb.
5. `plan-marshall:automatic-review:review_completeness` — `--participated-bots` /
   `--stale-participation-bots` given bare bot names where the flag's value grammar is
   `bot_kind:evidence_kind` pairs.

Four of the five match recurrence signatures that are already written down (verb-paraphrase,
doubled bundle-prefix, router-scoped flag placement). They were documented and they recurred
anyway, five times, in one run.

## Why this generalizes

The two guards that exist today both fire at the wrong time:

- **`plugin-doctor` / the `ARGUMENT_NAMING_*` rule cluster** is an *edit-time* structural
  guard. It checks that authored documents spell invocations correctly. It cannot see a
  command an agent composes at runtime from surrounding workflow prose.
- **The recurrence-signature list in `persona-plan-marshall-agent`** is *prose in a skill
  body*. It is loaded into context, but an agent composing a call under time pressure is
  reasoning from the workflow narrative in front of it, not re-deriving the signature list.

Neither guard sits on the actual failure surface, which is the moment the argv is handed to
the executor. That is a structural gap, not a discipline gap — five rejections in one run is
the population telling you that adding more prose to the same skill body will not move it.

Evidence that the invocation-time surface is the right one: this run ALSO hit a *rejected*
call that failed cleanly and legibly, because the executor's own preflight validator caught
it and named the accepted flag set:

```text
status: error
error: invalid_invocation
reason: unknown_flag
rejected: --file-path
accepted: file, plan-id
message: Use a declared flag for `...:manage-files exists`: ['file', 'plan-id']
```

That response cost one call and was self-correcting. The five rejections above cost a
loop_back, a misdiagnosis, and an orchestrator disproof — because they fell outside whatever
the preflight validator currently covers.

## Proposed direction

Widen the executor-side preflight so the classes above return the same shape as the
`unknown_flag` example rather than a raw argparse exit:

- **Unknown notation / doubled bundle-prefix** — the notation is resolvable against the
  executor's own script map before any subprocess is spawned; a miss should return
  `invalid_invocation` with a did-you-mean over the registered notations, not a traceback.
- **Unknown verb** — the accepted verb set is discoverable from the target parser; a miss
  should name it, exactly as `accepted:` names the flag set today.
- **Flag position on router-style parsers** — a flag that IS declared but at a different
  parser level is the highest-value case, because the flag name looks right. The rejection
  should say *where* the flag belongs, not merely that it was unrecognized.
- **Compound value grammars** — see the sibling candidate on `bot_kind:evidence_kind`.

The general principle: a guard for a runtime-composition failure has to live at runtime. An
edit-time linter and a prose rule are both real, and both structurally cannot see this.

## Impact

Every `python3 .plan/execute-script.py` call site — which is essentially every workflow step
in the system.
