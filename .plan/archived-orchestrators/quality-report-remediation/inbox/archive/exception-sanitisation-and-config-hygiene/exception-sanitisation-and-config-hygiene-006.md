envelope_version=1
sender_type=plan
sender_id=exception-sanitisation-and-config-hygiene
epic=quality-report-remediation
kind=landing
created=2026-09-08T15:14:00Z

# PLAN-03 landed: exception sanitisation and log safety

PR #222 merged to `main` as `7c7235b` (squash, via the platform merge queue). All three
deliverables shipped; 11 tasks across 6 envelopes; 7961 -> 7972 tests.

```landing-facts
schema=landing-facts/1
plan_id=exception-sanitisation-and-config-hygiene
epic=quality-report-remediation
pr=#222
merge_state=merged
deliverables_total=3
deliverables_done=3
total_tokens=13434874
total_wall_seconds=45240
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:skipped,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:skipped,record-metrics:done,finalize-step-print-phase-breakdown:skipped
step.branch-cleanup.merge_sha=7c7235b
step.create-pr.pr_number=222
```

## What shipped

1. **`detail` is sanitised like `originalInput`.** `buildMessage` routes `detail` through a new
   `escapeAndBound`, and `AllowBlockListStage` renders the rejected value through `renderForDetail`
   instead of splicing it raw. Closes F-B-5, L-4 and F-A-5 together, as the spec predicted.
2. **The sanitiser covers every line-forging character.** `CONTROL_CHARS_PATTERN` widened from
   `[\x00-\x1F\x7F]` to `[\x00-\x1F\x7F-  ]`, covering C1 controls (U+0085 NEL) and
   the Unicode line/paragraph separators U+2028 / U+2029.
3. **Exception messages stop reproducing credential material.** Both rendering paths emit
   `(input: <redacted, length=N>)`; the redaction is unconditional, with no configuration knob.
   `getOriginalInput()` / `getSanitizedInput()` keep returning raw values as a documented opt-in.

Regressions were written first for every change; each was observed red for its stated reason.

## Scope taken beyond the spec (all operator-approved)

The spec's three deliverables became eleven tasks. Five of the eight extra tasks came from the
finalize security audit finding the SAME defect class one layer deeper each round, and one from
an external review bot:

- TASK-7: `sanitizedInput` was still echoed in `toString()` after `originalInput` was redacted.
- TASK-8 (finding 7527c4): `getMessage` rendered `detail` unbounded while `toString` capped it
  at 200 - the two paths disagreed about the same field.
- TASK-9 (f3787f): `renderForDetail` built and STORED the 6x-amplified string unbounded, so the
  exception retained it whether or not anything rendered it.
- TASK-10 (66c501): `toString` rendered `getCause()` raw - the one field the class defended
  nothing on. The same commit made `escapeAndBound` code-point-atomic.
- TASK-11 (CodeRabbit, PR #222): the two sanitisers used DIFFERENT predicates -
  `Character.isISOControl` does not cover U+2028 / U+2029, which was deliverable 2's own target.

## Residue

- **The plan's own deliverable 2 shipped incomplete and an external reviewer caught it.** Four
  internal audit rounds checked each sanitiser against the attack class in isolation and never
  checked the two against each other. See inbox message -002.
- **`adr-propose` did not run** (`lane: off`, project config, operator-confirmed), so the spec's
  ADR-numbering-against-`origin/main` constraint had no firing site. No ADR was allocated.
- **One commit was not fully gated.** `f8d883b` (documentation-only) was covered by the whole-tree
  quality gate at its SHA, but the operator authorised skipping the self-review / simplify /
  security-audit re-fire against it, since no executable line moves. Recorded as a deliberate
  exception, not a satisfied contract.
- **Five findings remain open by decision**, all `improvement` / `info`: `049410` and `438902` (the
  `U+XXXX` escaping shape and the bound constants are duplicated across four classes / two
  packages - a non-exported internal package would retire both), `8c7dff` (NEL / LINE SEPARATOR
  tests collapsible), `df8660`, and `f43a2c` (`UrlSecurityExceptionTest` is 500 lines against a
  400-line budget, with a proposed split seam).
- **Two plan-marshall tooling defects surfaced**, filed as inbox messages -003 and -004: the
  re-review routing gap (`re_review_on_branch_cleanup` cannot fire on a merge-queue repo) and the
  `derive_gate_bundles` Java/Maven blind spot (zero bundles selected, reading as coverage).
- **A separate config PR (#224) is open** adding `file_globs` to the `java` / `java-cui` skill
  domains - domain-narrowing had emptied `references.domains` on this plan and it was restored
  by hand.
