envelope_version=1
sender_type=plan
sender_id=post-decode-character-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T11:26:24Z

component=plan-marshall:phase-6-finalize
category=bug
bundle=plan-marshall

# A step that writes tracked files while defaulting to read-only falls between both guards and its output is destroyed on worktree removal

## What happened

Filed in-run as Q-Gate finding `6e2f49` against plan-marshall itself.

`phase-6-finalize/workflow/adr-propose.md` declares `order: 62` and declares no
`mutates_source` fact. Absent the fact, it defaults to **read-only**. Two consequences:

- The dispatcher's item-5f commit instrumentation reads the declared `mutates_source` fact
  first and skips steps (a)-(d) entirely. The step's writes are never staged or committed.
- The step is not in the `post_run_review` band, so item-5f sub-item (0) — the tracked-source
  guard that exists precisely to CHECK an asserted `mutates_source: false` rather than trust
  it — does not fire for it either.

The step nevertheless writes tracked files. In this run it created `doc/adr/0004`, `0005`,
and `0006` — 420 lines of ADR content. Caught by neither mechanism, all three would have
remained uncommitted in the worktree and been destroyed when `branch-cleanup` removed it. The
orchestrator noticed and committed them by hand.

## Why this generalizes

The defect is the interaction of two individually-reasonable design choices:

1. **`mutates_source` defaults to false when undeclared.** A sensible default for the common
   case (most finalize steps genuinely read).
2. **The verification of that claim is band-scoped**, not universal — item-5f(0) checks the
   worktree only for steps declaring `post_run_review: true`.

Individually fine. Composed, they produce a hole with an exact shape: **any step outside the
verified band that omits the fact is silently trusted, and the trust is never tested.** The
default is fail-OPEN precisely where nothing checks it.

The generalizing statement: *a fail-open default is only safe where verification is
universal.* When verification is scoped to a band, the default outside that band must be
fail-closed, or the fact must be mandatory.

Note the failure is also **silent and destructive**, not merely wrong. There is no error, no
warning, no dirty-tree report — the work simply ceases to exist when the worktree is removed.
A step that writes nothing and a step whose writes were annihilated are indistinguishable
from every signal the run emits.

## Proposed remedies (any one closes it; the first two are structural)

1. **Make `mutates_source` a required frontmatter fact** for every finalize-step implementor.
   The extension-point contract test already reads frontmatter for `head_dependent` and
   `records_facts`; an absent `mutates_source` becomes a contract-test failure rather than a
   silent `false`. This removes the default entirely.
2. **Un-scope the tracked-source guard.** Run item-5f(0)'s dirty-tracked-path observation
   after EVERY step, not only `post_run_review` ones. The observation is cheap
   (`git status --porcelain`) relative to a finalize step.
3. **Fail-closed the pre-removal path.** `branch-cleanup` should refuse to remove a worktree
   carrying uncommitted tracked changes without an explicit acknowledgement, so the
   destructive step is the one that objects — independent of which step produced the diff.

Remedy 3 is worth having regardless of 1 and 2: it is the backstop that makes the failure
non-destructive even when a new step reintroduces the gap.

## Impact

Every finalize-step implementor outside the `post_run_review` band, present and future. The
population is exactly "steps that were never asked whether they write", which is not a set
anyone has enumerated.
