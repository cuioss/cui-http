envelope_version=1
sender_type=plan
sender_id=cross-origin-credential-forwarding-proof
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-03T09:29:11Z

# Candidate lesson: a deliverable declared `intent (read)` over a file its own technique mutates

**Class**: solution-outline authoring anti-pattern. Caught in-run by the phase-3 Q-Gate,
so this is a slipped-then-caught record, not an escape.

## Observation

Q-Gate finding `815555` (phase `3-outline`, severity `warning`, 06:43:16Z):

Deliverable 2 listed `cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpHandler.java`
under Affected files with `intent (read)`, and its blockquote asserted that
"Every declared file carries intent read ... this deliverable authors no test, it executes
the existing suite three times and judges surefire output".

The same deliverable's *Change per file* block contradicted that: it forced
`HttpHandler.java` line 789 (`boolean forwardCredentials = redirectPolicy.forwardsCredentials(...)`)
to `false`, ran the suite, then restored the line — two real writes to a path declared
read-only.

## Why this shape recurs

The technique is a **fixture-level neutralization**: mutate a production line, prove the
test fails for the stated reason, restore, prove the diff is clean. Its END STATE is
net-zero, and that is exactly what makes the read-only declaration feel true while
authoring. The declaration records *intent over the file*, not *the net diff*, so a
transient write is still a write.

Consequence had it not been caught: the transiently mutated path never enters
`references.affected_files` as a write, so the phase-5 edit is an undeclared write, and
the declared-set closure check at phase-4-plan Step 8 sees a step target its parent
deliverable does not declare.

## Resolution in this run

Corrected at outline time using the survey/mutation-scope form — `Files expected to mutate:`
naming `HttpHandler.java`, `Files to survey:` for the genuinely read-only paths — and the
blockquote reworded to claim a *net-zero diff gated by `git diff --exit-code`* rather than a
read-only intent it did not hold. `sync-affected-files` then confirmed `HttpHandler.java`
moved into `affected_files` (`mutation_count 2`). Execution subsequently falsified the new
assertion three-pass (baseline 7790/0, neutralized 7788/2 failing for the stated reason,
restored 7790/0, clean `git diff --exit-code`).

## Candidate corrective action

The generalisable rule: **a net-zero diff is not a read intent.** Any deliverable whose
technique includes neutralize-then-restore, temporary-stub, or force-a-branch must declare
the touched path under `Files expected to mutate:` and justify net-zero via the diff gate,
never via the intent field.

Worth checking whether `persona-module-tester`'s fixture-level-neutralization guidance (which
prescribes the technique) cross-references the outline declaration obligation — the technique
is documented in one place and the declaration rule in another, which is the shape that lets
an author get one right and the other wrong.

## Evidence

- plan `artifacts/findings/qgate-3-outline.jsonl` record `815555` (full detail + resolution)
- plan decision.log entries `276961`, `630c0d` (phase-4 q-gate re-verification of the corrected declaration)
