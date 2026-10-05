envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:16:10Z

# Candidate lesson: Javadoc claimed a guard ordering the calling code does not have (caught by review bot, fixed in-run)

**Source**: PR-comment finding `627162` (PR #237, author `coderabbitai`, resolution `fixed`)
**Plan**: plan-07-forwarded-parsing-and-validation
**Component**: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ContextPaths.java:117`
**Remediated by**: TASK-012 (commit `d8bbf1c`)

## Observation

`ContextPaths.containsUnsafePathConstruct`'s Javadoc asserted an ordering property — that the guard
runs **before** sanitisation. In fact `containsUnsafePathConstruct` is invoked only inside
`ContextPaths.normalize()`, which `ForwardedHeaderResolver.resolveContextPath` calls **after**
`sanitize()`. The resolver never applies that guard pre-sanitisation, so the documented ordering did
not exist anywhere in the code.

The bot's stated rule is the generalisable part: *when a change states that something is skipped,
disabled, guarded, validated, enforced or removed, verify that the mechanism exists in the file that
would have to implement it.*

The fix reworded the Javadoc to state the true rationale (this class never decodes, so
percent-encoding is rejected wholesale) without asserting ordering it does not have.

## Why this is lesson-bearing

This is a **slipped-then-caught** defect: it passed the plan's own outline Q-Gate, its per-task
verification, and the pre-push quality gate, and was caught only by an external review bot. That is
the exact class lessons-capture exists to record.

The failure class is **a security-relevant ordering claim written at the callee, verifiable only at
the caller**. The claim is locally unfalsifiable: nothing in `ContextPaths.java` can confirm or refute
"runs before sanitize", because the ordering lives in `ForwardedHeaderResolver`. It is worth noting
that this plan's own Q-Gate findings (see sibling candidates) were largely about *stale claims in
prose* too — the same class recurred inside one plan, in both the outline and the shipped code.

Candidate corrective: an ordering/guard claim in a doc comment must either (a) be stated at the
call site that establishes the ordering, or (b) name the caller it depends on, so a later reader has
somewhere to check it.

## Suggested disposition (orchestrator judges)

Possibly a `pm-dev-java:javadoc` or `plan-marshall:persona-implementer` rule, and/or a
pre-submission self-review detector. The bot rule already exists as a CodeRabbit path instruction;
the open question is whether it should be enforced earlier in the pipeline than external review.
