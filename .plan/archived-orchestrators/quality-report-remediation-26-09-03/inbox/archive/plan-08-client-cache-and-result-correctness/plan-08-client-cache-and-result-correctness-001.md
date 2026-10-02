envelope_version=1
sender_type=plan
sender_id=plan-08-client-cache-and-result-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T20:12:41Z

# Candidate lesson: the plan's headline deliverable shipped as unreachable code, and five local gates passed it

## What happened

D2 ("method-gated 304") was this plan's headline deliverable: a 304 arriving in response to
POST/PUT/PATCH/DELETE/OPTIONS must be reported as an RFC 7232 protocol violation
(`INVALID_CONTENT`), not silently treated as a cache revalidation.

It was implemented behind this guard in `ETagAwareHttpAdapter.handleHttpResponse`:

```java
if (statusCode == 304 && cachedEntry != null) {
    return handleNotModified(method, cachedEntry, cacheKey, etag);
}
```

D6, a *separate* deliverable in the same plan, narrowed the cache read so that
`prepareCacheContext` only consults the cache when `canReadCache(method)` — i.e. GET or HEAD.
For every unsafe method `cachedEntry` is therefore always `null`, so `cachedEntry != null` is
always false, so the RFC 7232 branch inside `handleNotModified` was **unreachable for exactly
the methods it was written for**. The plan's headline deliverable was dead code on arrival.

The added unsafe-method 304 test passed anyway. It asserted `INVALID_CONTENT`, and the generic
non-2xx error path happens to return `INVALID_CONTENT` too. The test was green for a reason
unrelated to the feature it was asserting.

## What caught it, and what did not

Two independent review bots caught it, on the same commit, from opposite directions:

- `coderabbitai` inline on `ETagAwareHttpAdapter.java:877` — "Handle every 304 response before
  requiring a cache entry ... The added unsafe-method 304 test therefore cannot receive its
  required error category."
- `cuioss-review-bot` (pr-agent) review guide, flagged as "Unreachable Code" — "the unsafe HTTP
  method branch in `handleNotModified` is unreachable ... `statusCode == 304 && cachedEntry != null`
  never evaluates to true for those methods."

Five local gates did not: `verify`, `coverage`, self-review, simplify, and the Q-Gate. The plan
recorded zero pending Q-Gate findings for the whole run.

## Why it is a candidate lesson

The solution outline had **explicitly flagged the D6/D2 interaction** as a risk. The risk was
identified at outline time, carried into execution, and then not re-checked once both
deliverables had landed. The failure was not "nobody thought of it"; it was "nobody re-tested
the interaction after the second half of it was written".

Two generalisable rules fall out:

1. **A guard predicate must be shown to be able to fire in the scenario it exists for.** Coverage
   proves a line was executed; it does not prove the branch was reached *by the input class the
   branch is about*. A test that reaches the assert via a different path is indistinguishable
   from a passing one.
2. **When an outline flags an interaction between two deliverables, that interaction is itself a
   verification obligation** — a named check at end-of-phase, not just a note. Neither deliverable
   is wrong in isolation, which is precisely why single-deliverable verification cannot see it.

## Evidence

- Plan: `plan-08-client-cache-and-result-correctness`, PR #166.
- Findings: `26ab48` (coderabbit, `ETagAwareHttpAdapter.java:877`), `788616` (pr-agent, unreachable code).
- Fix commit: `eee4227`.
- Local gate outcome at the time: `signal_qgate_pending_count: 0`.
