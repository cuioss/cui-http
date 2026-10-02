envelope_version=1
sender_type=plan
sender_id=plan-09-client-config-and-retry-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-30T10:27:00Z

component=cui-http-core
category=bug

# NaN passes every ordered-comparison range guard, so it bypassed the very validation the deliverable added

## Observation

Deliverable 1 of this plan added range validation to `RetryConfig` specifically to
make a hot-retry loop unreachable. The new compact constructor guarded its inputs
with ordered comparisons:

```java
if (jitter < 0.0 || jitter > 1.0) { throw ... }
if (multiplier < 1.0) { throw ... }
```

Every ordered comparison against `NaN` evaluates to `false` (IEEE 754). `NaN`
therefore satisfied `!(jitter < 0.0)` and `!(jitter > 1.0)` and `!(multiplier <
1.0)` simultaneously, passed all guards, reached `calculateDelay()`, and produced
exactly the hot-retry loop the deliverable existed to prevent.

Two further facts matter more than the bug itself:

1. **It was found by coderabbit, not by us.** The plan's own quality gate, its
   solution outline, and its pre-submission self-review all passed over a
   validation hole in the validation the plan was shipping. The defect class —
   "the new guard does not guard" — is precisely what a self-review of a
   validation deliverable should be looking for.
2. **The hole was at three seams, one more than the review comment named.** The
   reviewer identified the compact constructor. The same missing `NaN` check was
   also present in *both* eager Builder setters, which validate at set time rather
   than deferring to the record constructor. Fixing only the seam the reviewer
   pointed at would have left two live paths to the same failure.

## The generalizable rules

- **A floating-point range guard built from ordered comparisons is not a range
  guard.** `x < lo || x > hi` admits `NaN`. Range validation over `double`/`float`
  must test `Double.isNaN(x)` explicitly (or use a positive-form predicate such as
  `!(x >= lo && x <= hi)`, whose negation is `NaN`-safe).
- **When a defect is found at one construction seam, enumerate every seam that
  constructs the same invariant.** Records with compact constructors plus builders
  that validate eagerly have more than one entry point; a fix scoped to the seam
  the reviewer happened to name is incomplete by default. Enumerate, then fix.
- **A validation deliverable's self-review must adversarially test its own new
  guard**, including the degenerate values of the guarded type — `NaN`, the
  infinities, and negative zero for floating point; `MIN_VALUE` and overflow
  boundaries for integers.

## Suggested corrective action

Add the degenerate-value sweep for floating-point parameters to the review checklist
for any deliverable whose stated purpose is input validation. The orchestrator may
prefer to promote the `NaN`-versus-ordered-comparison rule to the shared Java
standards surface rather than keeping it project-local to `cui-http-core`, since it
is a language-level trap and not a cui-http one.
