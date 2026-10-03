envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:31:39Z

# Candidate lesson: a test named/documented for the builder default asserted an explicitly-passed value instead

Source signal: Q-Gate finding `112627` (phase 6-finalize, `pre-submission-self-review`, resolved `fixed`).
Component: `cui-http-core` — `ETagAwareHttpAdapterTest.builderDefaultsToTheAllHeaderFilter`.

## What happened

`builderDefaultsToTheAllHeaderFilter` claimed — by name and by Javadoc — to verify the builder's
default cache-key header filter. Its body passed `CacheKeyHeaderFilter.ALL` explicitly to
`generateCacheKey(...)` instead of exercising the adapter's configured default (no accessor
exists, and `generateCacheKey` requires an explicit filter argument). The test therefore would
have passed unchanged even if the real builder default were something other than `ALL`, and it
duplicated coverage already asserted by `cacheKeyFiltersShouldSelectExactlyTheHeadersTheyName`.

## Corrective rule

A test whose NAME or Javadoc claims to verify a default MUST reach that default through the
production construction path. When the production API offers no way to observe the default
(no accessor, mandatory explicit argument), the test cannot make the claim — either add the
observation seam, or rename the test to what it actually asserts. A test that supplies the very
value it purports to be checking is vacuous regardless of how it is named.

## Generalisation for the epic

Detection signature: a test method whose name contains `default`/`defaultsTo` while its body
passes the asserted value as a literal argument. Cheap to surface mechanically, and this run shows
the defect survives ordinary review — it was caught only by the structural self-review pass.
