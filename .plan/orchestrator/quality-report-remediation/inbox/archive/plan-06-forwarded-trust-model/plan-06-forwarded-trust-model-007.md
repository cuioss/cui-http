envelope_version=1
sender_type=plan
sender_id=plan-06-forwarded-trust-model
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-08T15:05:55Z

component=plan-marshall:recipe-security-audit
category=improvement
bundle=plan-marshall

# Re-firing head-dependent verification after HEAD advanced found real defects a stale green record hid

The security audit was re-run after the branch HEAD had advanced past the commit the
first audit covered. The re-fire found two genuine defects the first pass could not
have seen — one of them an exploitable port leak across a trust boundary. Two further
defects were found the same way, by re-running other head-dependent steps rather than
trusting the green record those steps had already written.

Two things made the re-fire pay:

1. **The record was stale, not wrong.** Each earlier green was accurate about the
   commit it examined. Its only defect was being consumed as a statement about the
   current HEAD. Any verification whose input is the worktree is a claim about ONE
   commit and expires the moment HEAD moves.
2. **The probes were executed, not read.** The defects were found by compiling and
   running probe code against the actual behaviour, not by reading the diff. A
   diff-reading pass over the same code had already been performed and had reported
   nothing — the port leak is invisible in the diff and obvious in a running probe.

## Solution

- Bind every head-dependent verification record to the commit it examined, and treat
  a record whose commit is not the current HEAD as EXPIRED rather than as a pass. The
  merge barrier should re-fire expired head-dependent steps rather than reading their
  stored outcome.
- For security-class verification specifically, prefer an executed probe (compile,
  run, assert against real behaviour at the trust boundary) over diff inspection. A
  diff review answers "did this change introduce X"; a probe answers "does X hold
  now", and only the second is the property being claimed.
- Budget for the re-fire. It is not redundant work: in this run it was the step that
  found the highest-severity defect of the plan.

## Impact

Applies to every verification step whose result depends on the worktree state:
security audit, coverage, build/verify, architecture gates. The stronger the audit,
the more expensive the stale-record failure — a stale security green is a security
green nobody earned.
