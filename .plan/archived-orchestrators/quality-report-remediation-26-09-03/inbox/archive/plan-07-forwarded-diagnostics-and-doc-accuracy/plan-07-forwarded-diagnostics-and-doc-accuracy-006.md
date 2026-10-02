envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-diagnostics-and-doc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:26:47Z

component=plan-marshall:phase-6-finalize
category=bug
bundle=plan-marshall

# Fix tasks minted by finalize triage skip the phase-4 planner, so they carry none of its stamps

## Observation

`TASK-013` was created by finalize triage in response to a CodeRabbit PR comment. Because it
never passed through the phase-4 planner, it carries none of the fields the planner is
responsible for stamping.

Evidence from this plan (`plan-07-forwarded-diagnostics-and-doc-accuracy`), `manage-tasks get
--task-number 13`:

- **No `envelope_id`** — the phase-4 bin-packer never saw the task, so no envelope-group stamp
  exists. The dispatch's envelope-group equality check therefore has nothing to compare
  against.
- **Empty verification block** — `verification.commands[0]:` (empty), `criteria: ""`,
  `manual: false`. Corroborated by work.log `2026-08-29T13:14:18Z` WARNING:
  `[VERIFY] (plan-marshall:execute-task) TASK-013 missing verification — falling back to
  architecture resolve`.

Both defects have the same single cause and neither is visible from the task's own record
unless you know to look for an absence.

## Why this is reusable

The interesting part is the difference in how the two absences behaved:

- The **missing verification block** degraded loudly — `execute-task` noticed, logged a
  WARNING, and fell back to `architecture resolve`. The gap was covered and recorded.
- The **missing `envelope_id`** degraded silently. It was harmless here only because there was
  exactly one fix task, so the envelope-group equality check had nothing to disagree with. A
  loop-back that allocates two or more fix tasks would compare `null` against `null` and read
  two unrelated tasks as members of the same envelope group — or compare `null` against a real
  id and misroute. The defect is latent, and its harmlessness is a property of this run's shape
  rather than of the code.

Generic form: **when a record can be created by two different producers, every field one
producer stamps is a field the other producer must either stamp or explicitly declare absent.**
Triage-minted tasks are structurally second-class against planner-minted ones, and the
consumers do not all handle that.

## Proposed rule

1. Enumerate the fields the phase-4 planner stamps on a task and require the finalize-triage
   task creator to populate or explicitly null-declare each one. `envelope_id` and the
   `verification` block are the two confirmed here; the enumeration should be derived from the
   task schema rather than from this list.
2. Any consumer that compares `envelope_id` for equality must treat `null` as "no group",
   never as a group that can match another `null`. This is the fail-closed reading and it
   removes the latency of the defect independently of whether (1) lands.
3. Prefer routing triage-created fix tasks through the same allocation path the planner uses,
   so the two producers cannot drift again.
