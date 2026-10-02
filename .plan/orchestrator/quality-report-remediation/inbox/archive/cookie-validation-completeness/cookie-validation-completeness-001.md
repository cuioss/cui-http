envelope_version=1
sender_type=plan
sender_id=cookie-validation-completeness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T15:10:23Z

## Candidate lesson: `mutates_source: false` on a step that classified its own loop-back as inline-fixable strands verified-green source edits

**Component:** `plan-marshall:phase-6-finalize` (step `default:pre-submission-self-review`)
**Category:** bug / contract gap
**Observed:** plan `cookie-validation-completeness`, PR #231, this run.

### What happened

`pre-submission-self-review` declares `mutates_source: false` in its frontmatter. During this
run it raised a Q-Gate finding (`0204c8`, `weak_regression_assertion` at
`CookiePrefixValidationStageTest.java:414`), classified the disposition as **inline-fixable**
(`loop_back_target: 6-finalize`), and then **made source edits in the worktree** to fix it
(strengthening `assertComponentRejected` to assert `UrlSecurityFailureType` and
`ValidationType` under both presets).

Because the step declares `mutates_source: false`, the phase-6-finalize dispatcher's item 5f
reads that declared fact FIRST and skips commit instrumentation (a)-(d) entirely. The edits
were verified green and then sat **uncommitted**. The push barrier's clean-tree assertion
would have discarded them silently. The orchestrator had to commit them by hand as `89d143a`.

### Why it is a contract gap, not an operator slip

The two facts the step asserts are mutually inconsistent by construction:

- `mutates_source: false` claims the step writes no tracked source, so it is never
  commit-instrumented.
- `loop_back_target: 6-finalize` (inline-fixable) is precisely the disposition class that
  authorises the step to apply the fix *itself*, in place, without allocating a fix task.

A step cannot legitimately hold both. Any source-editing disposition needs either commit
instrumentation or a fix-task allocation; this configuration provides neither.

### Two honest fixes (pick one)

1. **`loop_back_target: 5-execute` for any source-editing disposition.** The finding allocates
   a fix task and phase-5 executes it under normal commit instrumentation. Keeps
   `mutates_source: false` truthful.
2. **`mutates_source: true` on the step.** Admits the step edits source, so item 5f's
   (a)-(d) commit instrumentation runs and the edits ride the PR.

Option 1 is the narrower change and preserves the step's declared read-only character;
option 2 is the smaller edit but widens what the step is allowed to do.

### Generalisation for the epic

This is a **declaration-versus-behaviour** class, not a one-off. Any finalize step whose
frontmatter declares `mutates_source: false` while its disposition vocabulary contains an
inline-fix branch has the same latent defect. Worth a sweep across the finalize-step roster
for the pair (`mutates_source: false`, an inline/self-apply disposition).
