envelope_version=1
sender_type=plan
sender_id=exception-sanitisation-and-config-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-08T13:43:35Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-09-08

# derive_gate_bundles has no notion of a Maven module, so the per-bundle quality gate selected ZERO bundles on a Java repo and the zero read as coverage

`derive_gate_bundles` resolves **marketplace bundles**. It has no notion of a
Maven (or Gradle, or any non-marketplace) module. On this Java repo
(`cui-http`, modules `cui-http-core` / `cui-http-benchmarking`) the per-bundle arm
of `pre-push-quality-gate` therefore:

- selected **zero** bundles, and
- placed **all four** source paths the plan touched into `unresolved`.

Only the whole-tree arm actually gated the change. The per-bundle arm ran,
reported `0`, and reported success.

## Root cause

The zero is ambiguous and the payload does not disambiguate it. `bundles: 0`
means either:

- *"this change touched no bundle"* — a legitimate, fully-covered outcome; or
- *"this repo has no bundles at all, so this arm cannot cover anything here"* —
  a total coverage gap.

Nothing in the output distinguishes them, so the second reads exactly like the
first: as coverage. The `unresolved` list carried the whole truth — four of four
paths unresolved — but it is not what a reader checks, and no gate keys on it.

This is the same "which kind of zero is this?" discipline that
`manage-lessons list-stalled`, `manage-findings`' store-state fields, and
`orchestrator inbox list`'s three-zero table already apply elsewhere in
plan-marshall. The per-bundle gate arm has not had it applied.

## Solution

1. **Make the arm state its own applicability.** Emit a discriminator alongside
   the count — e.g. `bundle_resolution: applicable | not_applicable_no_bundles` —
   so a structural zero is never rendered as a covered zero.
2. **Gate on `unresolved`, not just on the bundle count.** A non-empty
   `unresolved` set over changed source paths is a coverage gap, and should be
   surfaced as such rather than reported as a passive by-product.
3. **Either teach the seam about build modules, or declare the arm
   language-scoped.** If the per-bundle arm is marketplace-only by design, say so
   in its output on a non-marketplace repo instead of returning a bare zero.

## Impact

Every non-marketplace repo running `pre-push-quality-gate` — i.e. every real
project consuming plan-marshall as a plugin, which is the primary deployment.
The arm silently contributes nothing there and looks like it contributed
everything.
