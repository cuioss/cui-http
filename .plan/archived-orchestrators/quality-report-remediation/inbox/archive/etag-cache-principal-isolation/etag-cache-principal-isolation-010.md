envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:32:03Z

# Candidate lesson: a cross-principal test passed vacuously because the invariant under test made its own premise unreachable

Source signal: `pr-comment` finding `347555` (PR #240, coderabbitai, resolved `fixed` in-run via TASK-29).
Component: `cui-http-core` — `ETagAwareHttpAdapterTest` 4xx cached-fallback test / `CachedFallbackRestriction` Javadoc.

## What happened

A test asserted that a 4xx response must NOT serve a cached fallback, seeding the cache as
principal A and probing as principal B. But `generateCacheKey` UNCONDITIONALLY appends the
credential-derived `principalBinding` (that is exactly ADR-0023's invariant), so principal B never
had a cache entry to fall back on. The `cachedEntry != null` precondition was already false, and
the test would have passed identically with the `isServerError` check deleted — it exercised
nothing. The `CachedFallbackRestriction` Javadoc compounded it by stating the two principals share
one cache key, which the implementation contradicts.

Fix: seed and probe with the SAME principal so an entry actually exists, and correct the stale
class/method Javadoc.

## Corrective rule

When a new invariant is introduced (here: per-principal cache-key binding), re-check every
existing test whose SETUP depends on the invariant NOT holding. Such a test does not fail — it
goes green and vacuous, which is the worst outcome. A negative assertion (`assertNull`,
`assertNotServed`) is only meaningful when a positive control proves the precondition it negates
was reachable.

## Generalisation for the epic

Two-part signature worth sweeping: (1) tests asserting an absence where the absence is guaranteed
by construction rather than by the behaviour under test; (2) Javadoc making a sharing/identity
claim ("the two X share one Y") that an ADR invariant has since falsified. Both were caught by an
external review bot here, not by the suite or by the plan's own gates.
