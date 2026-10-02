envelope_version=1
sender_type=plan
sender_id=plan-14-doc-overclaim-correction
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-26T19:25:57Z

component=plan-marshall:phase-3-outline
category=bug
bundle=plan-marshall

# A deliverable's Affected files must be a superset of every file its own change instructions name

An outline deliverable carries two independent descriptions of its blast radius: the
structured `**Affected files:**` list, and the free-prose change instructions that
tell the implementer what to do. Only the first is machine-read — it feeds skill
resolution, lesson consult (`manage-lessons consult` maps affected paths to
`{bundle}:{skill}` components), task scoping, and the verification sweep. The second
is what the implementer actually follows.

When the prose names a file the list omits, every machine consumer under-scopes
while the implementer over-edits. The implementer is instructed to touch a file that
the plan does not know is in scope, so that file is invisible to the consult, to
skill resolution, and to per-deliverable verification.

Instance in plan-14-doc-overclaim-correction: Q-Gate finding `87956e` (4-plan).
Deliverable 2's `Affected files` omitted `URLParameterNameValidationPipeline.java`
while the deliverable's own change instructions told the implementer to link it.
Caught at 4-plan Q-Gate and fixed, but only because a gate looked; nothing in the
outline authoring step forces the two descriptions to agree.

## Solution

When authoring or reviewing an outline deliverable, run a closure check before
declaring it complete:

1. Extract every concrete path, file name, and type name mentioned anywhere in the
   deliverable's change instructions / acceptance criteria prose.
2. Resolve each to a repo path.
3. Assert each resolved path appears in that deliverable's `**Affected files:**`
   list. A path named in the prose but absent from the list is a defect in the list,
   not in the prose — the prose is what the implementer will do.

The check is cheap and mechanical; the asymmetry (prose names it, list omits it) is
the direction that actually causes harm, so it is worth checking even when the list
looks plausible.

## Impact

Applies to every phase-3-outline deliverable in any domain. The cost is silent
rather than loud: nothing fails, the implementer simply edits a file the plan's
tooling never accounted for, so lesson consult, skill resolution, and the
per-deliverable verification sweep all run against an incomplete file set.
