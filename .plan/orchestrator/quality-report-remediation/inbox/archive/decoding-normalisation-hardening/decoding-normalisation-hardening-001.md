envelope_version=1
sender_type=plan
sender_id=decoding-normalisation-hardening
epic=quality-report-remediation
kind=finding
created=2026-09-05T21:21:57Z

# `allowDoubleEncoding` now gates nothing in production, and two `SecurityDefaults` prose claims went stale

Filed by PLAN-01 (`decoding-normalisation-hardening`), deliverable 5, for the workstream that owns
the security-configuration public surface and its consumer documentation (WS-06 / PLAN-13).

## What deliverable 5 changed

Deliverable 5 closed the raw-versus-encoded verdict asymmetries in
`cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java`. Two gates that
were configuration-dependent became unconditional:

1. The wire-form double-encoding gate (`DOUBLE_ENCODING_PATTERN`, step 1) and the post-decode
   surviving-encoding gate (`SURVIVING_ENCODING_PATTERN`, step 2.25) no longer read
   `config.allowDoubleEncoding()`.
2. The Unicode structural-fold check no longer sits inside `if (config.normalizeUnicode())`. The
   fold is always computed and always inspected; `normalizeUnicode` was narrowed to deciding only
   whether the canonical form or the decoded input form is the value returned.

PLAN-01's own scope bounds forbade it from acting on either consequence below, so both are recorded
here rather than changed in place.

## Consequence 1 — `allowDoubleEncoding` is now read by no production code

A repository-wide content search for `allowDoubleEncoding` (586 files scanned, complete coverage)
returns production hits in exactly four files: `SecurityConfiguration.java`,
`SecurityConfigurationBuilder.java`, `SecurityDefaults.java` and `DecodingStage.java`. The first
three only declare, build or set the flag. `DecodingStage` was its sole consumer, and after
deliverable 5 it no longer reads it.

The flag therefore remains a fully supported, documented, Maven-Central-published configuration
property that no longer changes any observable behaviour. PLAN-01 deliberately retained the record
component and its builder setter unchanged: removing them is a breaking change to a public surface
governed by ADR-0008, which the operator ruled out of scope for this plan. Deciding whether the
flag should be deprecated, removed in a future major, or given a new consumer belongs to the
owning workstream.

## Consequence 2 — two `LENIENT_CONFIGURATION` Javadoc claims are now inaccurate

`cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` is on PLAN-01's
do-not-touch list, so its Javadoc was left as-is. Its `LENIENT_CONFIGURATION` "security callout"
block now makes two claims that no longer hold:

- "`allowDoubleEncoding = true` disables the double-encoding gate, so an input that hides an attack
  behind a second layer of percent-encoding (for example `%252e%252e%252f`) is no longer rejected
  on that basis." — the gate is now unconditional, so that input IS rejected under `lenient()`.
- "`normalizeUnicode = false` disables Unicode normalization, and with it the homoglyph/confusable
  detection that depends on normalization." — detection is now unconditional; only the returned
  form still depends on the flag.

The same file's `SENSITIVE_PATH_PATTERNS` Javadoc is unaffected by deliverable 5 (the decoded
backslash reachability argument still holds — PLAN-01 deliberately did not add a decoded-backslash
rejection, precisely because it would have invalidated that documented reachability).

`doc/http-security/configuration.adoc` and `doc/forwarded-header-resolution.adoc` each reference
`allowDoubleEncoding` and are reserved to WS-06 / PLAN-13 by PLAN-01's Exclusions; they were not
inspected for staleness by this plan.

## Suggested follow-up

1. Decide the fate of `SecurityConfiguration.allowDoubleEncoding()` (retain as inert, deprecate, or
   schedule removal for the next major) and record it as an ADR if the decision is removal.
2. Correct the two `LENIENT_CONFIGURATION` Javadoc bullets in `SecurityDefaults.java`.
3. Re-check the two `doc/*.adoc` consumers for the same two claims.
