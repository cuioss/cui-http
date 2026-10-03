envelope_version=1
sender_type=plan
sender_id=plan-08-client-cache-and-result-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T20:13:06Z

# Candidate lesson: both the reviewer's proposed fix and the orchestrator's own task description reproduced the bug they were describing

## What happened

Once the unreachable unsafe-method 304 branch (see the sibling candidate-lesson on that defect)
was identified, two independent sources proposed a fix. Both proposals were wrong in the same way,
and both were wrong *because they preserved the very predicate that caused the defect*.

The reviewer's committable suggestion (`coderabbitai`, finding `26ab48`):

```diff
-        if (statusCode == 304 && cachedEntry != null) {
-            return handleNotModified(method, cachedEntry, cacheKey, etag);
+        if (statusCode == 304) {
+            if (cachedEntry != null) {
+                return handleNotModified(method, cachedEntry, cacheKey, etag);
+            }
+            return HttpResult.<T>failureWithFallback(
+                    "HTTP 304 cannot be resolved without a cached response",
+                    null, null, HttpErrorCategory.INVALID_CONTENT, null, 304);
         }
```

The orchestrator's own remediation task description proposed the same shape.

## Why both were wrong

`cachedEntry != null` **implies** `method` is GET or HEAD — that is exactly the invariant D6
established. So an inner `if (cachedEntry != null)` still routes only safe methods into
`handleNotModified`, and the RFC 7232 unsafe-method branch inside it stays dead. The proposal
converts an unreachable branch into a *differently* unreachable branch: unsafe methods now hit the
new generic "cannot be resolved without a cached response" return, which happens to carry
`INVALID_CONTENT` — so the test goes on passing for the same coincidental reason it passed before.
The proposed fix reproduces the defect *and* preserves the false-green.

The only fix that actually worked (commit `eee4227`) inverted the decision order: **decide on the
method first, then consult the cache**. `handleNotModified` now dispatches on method before it
reads `cachedEntry`, so the protocol-violation branch fires for POST/PUT/PATCH/DELETE/OPTIONS
regardless of cache state.

## Why it is a candidate lesson

The generalisable rule is about *how a proposed fix is evaluated*, not about HTTP:

- **A proposed fix that keeps the defect's own predicate is a restructuring, not a fix.** Before
  applying any suggested diff, restate the causal chain and check that the diff breaks it. Here
  the chain was `unsafe method -> canReadCache false -> cachedEntry null -> guard false`; both
  proposals left every link intact.
- **A reviewer who correctly identifies a defect has not thereby validated their own remedy.** The
  finding and the suggested diff are two separate claims and deserve separate scrutiny. The
  committable-suggestion format makes accepting the second look like accepting the first.
- **The orchestrator proposing the same wrong shape shows this is not a bot artefact.** The wrong
  fix is the *locally obvious* one — it is what "handle the null case too" looks like when you
  reason from the symptom rather than from the invariant. That is what makes it worth recording.

## Evidence

- Plan: `plan-08-client-cache-and-result-correctness`, PR #166.
- Finding `26ab48` carries the reviewer's proposed diff verbatim.
- Correct fix: commit `eee4227` (method-before-cache ordering in `handleNotModified`).
- Corroborating finding `788616` (pr-agent) identified the same defect independently.
