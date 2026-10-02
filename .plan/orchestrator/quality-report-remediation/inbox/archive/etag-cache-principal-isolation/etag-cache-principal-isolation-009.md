envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:31:58Z

# Candidate lesson: builder setter bypassed the caller-supplied provenance flag its own validation branch checks

Source signal: `pr-comment` finding `a6a9e6` (PR #240, coderabbitai, resolved `fixed` in-run via TASK-28).
Component: `cui-http-core` — `HttpHandlerBuilder.tlsVersions(...)` / `HttpHandler.java:1276`.

## What happened

`HttpHandlerBuilder.tlsVersions(...)` set `secureSSLContextProvider` WITHOUT marking it as
caller-supplied. The cleartext (`http` URI) build branch rejects a caller-supplied SSL context by
consulting `sslContextCallerSupplied` — a flag `tlsVersions(...)` never set. An explicit TLS
setting on a cleartext URI was therefore silently accepted and then discarded.

Not a TLS bypass (the handler still built with the default provider), but a violated validation
contract that hides a real configuration error from the caller. The fix added
`secureSSLContextProviderCallerSupplied` tracking plus a `derivedTlsVersions(...)` re-injection
seam so `asBuilder()` can restore a DERIVED setting without tripping the new rejection.

## Corrective rule

When a validation branch gates on a provenance flag ("was this caller-supplied?"), EVERY setter
that can populate the underlying field must set that flag — and every internal re-injection path
must have an explicit non-flag-setting variant. A provenance flag written by only some of the
writers is worse than no flag: it makes the guard look present while leaving a silent hole.

## Generalisation for the epic

Detection signature: a boolean `xCallerSupplied` (or `xExplicit`, `xSet`) field read by a guard,
where a `grep` for writers of `x` returns more sites than writers of `xCallerSupplied`. Mechanical
and cheap to check across builder-heavy code. The `asBuilder()` round-trip is the reason the naive
fix (set the flag in every setter) is wrong, so the derived-variant seam is part of the lesson.
