envelope_version=1
sender_type=plan
sender_id=build-artifacts-and-ci-safety
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-06T09:06:42Z

component=plan-marshall:phase-6-finalize
category=bug
title=A verdict-currency classifier must not read an undeclared verdict_inputs surface as invalidated

# A verdict-currency classifier must not read an undeclared `verdict_inputs` surface as `invalidated`

After each new commit in the finalize wait region, the verdict-currency
classifier returned `invalidated` for **every** head-dependent step. The cause
was structural, not situational: those steps declare no `verdict_inputs` surface
at all, and the classifier treats an undeclared surface as "everything may have
changed".

Taken at face value, that verdict implies a full finalize replay on every commit
— re-running the quality gate, self-review, simplify, security audit, CI verify,
the review round, and Sonar, each time. This run reached the correct reading
instead: the **wait-region bounded re-settle contract** governs a commit landing
inside the wait region, and it bounds the re-settle to the steps whose result the
new commit can actually move.

The step firing counts in this run's own record show the intended shape —
`ci-verify`, `automatic-review` and `sonar-roundtrip` re-fired three times each,
while `push`, `create-pr` and `architecture-refresh` fired once. A literal reading
of the classifier would have re-fired all of them.

## Why this is a defect and not a judgement call

- The classifier conflates **"the inputs changed"** with **"the inputs were never
  declared"**. Those are different facts, and only the first justifies
  invalidation. The second is a declaration gap in the step's own frontmatter.
- Because it is emitted per step and per commit, the wrong verdict is uniform —
  it looks like a systemic invalidation rather than a missing declaration, which
  is exactly what makes it read as authoritative.
- Correctly following it is unbounded: any commit in the wait region (including
  ones the review round itself produces) re-triggers the whole phase, and the
  phase can produce further commits.

## Rule

- An undeclared `verdict_inputs` surface must classify as **`indeterminate`**,
  never as `invalidated`. An unobservable input is not a changed one — the same
  discriminator the rest of the system already applies to could-not-look zeros.
- A classifier verdict that would imply a full-phase replay is a signal to check
  the classifier's premise before acting on it. Bounded re-settle is the
  governing contract inside the wait region; a per-step currency verdict does not
  override it.
- The durable fix is on the declaration side: head-dependent finalize steps
  should declare their `verdict_inputs` surface so the classifier has something
  real to compare against. Until they do, the classifier's output for those steps
  carries no information and must not be routed on.
