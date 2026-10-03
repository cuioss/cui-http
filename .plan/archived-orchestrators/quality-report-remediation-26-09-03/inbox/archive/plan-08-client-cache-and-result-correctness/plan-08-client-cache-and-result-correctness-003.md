envelope_version=1
sender_type=plan
sender_id=plan-08-client-cache-and-result-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T20:13:31Z

# Candidate lesson: fixing one instance of a defect class did not prompt a search for its sibling, and the same test-fixture blind spot hid both

## What happened

Two cache-eviction gaps in `ETagAwareHttpAdapter` were found in this run, in two separate review
rounds. They are siblings — the same class of defect in adjacent branches of the same `if`:

**Gap 1** (finding `e2681f`, fixed in `7cdc7c2`): a cached GET receiving an **ETag-less** 200 whose
converter returns `Optional.empty()` returned from the conversion-failure path *before* reaching the
eviction. The stale entry survived, so a later 503 could serve content the newer 200 had already
superseded.

**Gap 2** (finding `572fcb`, fixed in `e87f76a`): the same shape one branch over. The eviction was
guarded by `else if (etag == null)`, so a cached GET receiving an **ETag-bearing** unparseable 200
neither stored a replacement nor evicted the old entry — same stale-fallback outcome:

```diff
 if (method == HttpMethod.GET && statusCode == 200) {
     if (etag != null && content.isPresent()) {
         putInCache(cacheKey, new CacheEntry<>(content.get(), etag, System.currentTimeMillis()));
-    } else if (etag == null) {
+    } else {
         evictFromCache(cacheKey);
     }
 }
```

Fixing Gap 1 did not trigger a search for Gap 2. A reviewer found it, one round later.

## The shared blind spot

Neither gap was visible to the existing tests, and for the *same* reason both times:
`StringResponseConverter` returns `Optional.of("")` for an empty body — it never produces
`Optional.empty()`, so no `StringResponseConverter`-based test can ever exercise a conversion
failure at all. Both fixes needed a `TypedResponseConverter` sequence to be provable. Both reviewer
comments said so explicitly ("Add the same sequence with `TypedResponseConverter`").

This is the **same converter blind spot that originally hid CL-6** — the defect class this plan
existed to remediate. The fixture limitation that produced the original defect was still in place
while its remediation was being written, and went on hiding the remediation's own gaps.

## Why it is a candidate lesson

Two distinct, generalisable rules:

1. **A landed fix is a search key, not a closed ticket.** When a defect is found in one branch of a
   conditional, the sibling branches of that same conditional are the highest-yield place to look
   next, and the search should happen in the same round as the fix. Here the two gaps were four
   lines apart and were found a review round apart.
2. **When a test fixture cannot represent a failure mode, every defect in that mode is invisible —
   including defects in the code written to fix that mode.** The remediation inherited the blind
   spot that caused the original defect. The fixture gap should be closed *first*, as part of the
   remediation, rather than treated as a property of the environment the remediation runs in. A
   known converter that cannot return `Optional.empty()` is a documented hole in the test surface
   and belongs in the plan's verification obligations.

## Evidence

- Plan: `plan-08-client-cache-and-result-correctness`, PR #166.
- Findings `e2681f` (ETag-less, round 1) and `572fcb` (ETag-bearing, round 2), both from `coderabbitai`.
- Fixes: `7cdc7c2` and `e87f76a`.
- Blind spot: `StringResponseConverter` returns `Optional.of("")`; `TypedResponseConverter` required
  to reach the conversion-failure path. Same converter behaviour originally hid CL-6.
