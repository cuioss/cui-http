envelope_version=1
sender_type=plan
sender_id=plan-09-client-config-and-retry-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-30T10:27:18Z

component=plan-marshall:tools-integration-ci
category=bug
bundle=plan-marshall

# ci_base's 60s subprocess ceiling has a one-second margin against `gh auth status` on a multi-account host

## Observation

`ci_base`'s `run_cli` enforces a 60-second subprocess ceiling. On this host, with
two GitHub accounts configured, `gh auth status` took ~60.9 seconds and tripped the
ceiling. The time is spent validating the token of the *inactive* account, whose
validation hangs; `gh auth status --active` returns in 0.34 seconds.

The failure was transient in this run — a retry succeeded — which is precisely why
it is worth recording rather than dismissing: the margin is one second, so the same
call is a coin flip on any host with a second configured account, and the symptom
(a timeout on an auth preflight) reads as a network problem rather than as a
configuration one.

## The generalizable defect

- **A liveness/identity preflight should ask the narrowest question it needs.**
  The preflight needs to know whether the *active* account is authenticated. Asking
  `gh auth status` asks about every configured account, so its latency scales with
  a dimension the caller does not care about and cannot control.
- **A fixed subprocess ceiling sized against the single-account case has no margin
  for the multi-account one.** The observed ratio here is ~180x (0.34s vs 60.9s),
  so this is not a ceiling that can be safely tuned upward — the query has to change.

## Suggested corrective action

Use `gh auth status --active` for the auth preflight in `ci_base`. This removes the
dependency on the inactive account's token validation entirely and brings the call
three orders of magnitude inside the existing ceiling, so no timeout change is
needed. If the broader multi-account view is genuinely required somewhere, that call
should carry its own, much larger, explicitly-justified ceiling.

## Filing note

The corrective surface is the `plan-marshall:tools-integration-ci` bundle, which the
epic's host repository (`cui-http`) does not own; lifting needs
`--allow-foreign-store` or carrying to the plan-marshall repository.
