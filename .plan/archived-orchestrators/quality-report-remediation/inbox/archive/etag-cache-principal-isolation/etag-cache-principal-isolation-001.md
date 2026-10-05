envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:31:21Z

# Candidate lesson: credential concatenation before hashing allowed a delimiter-forgery cache-key collision

Source signal: `signal_automated_review_count` / finalize `default:finalize-step-security-audit` (real defect found and fixed in-run).
Component: `de.cuioss.http.client.adapter.ETagAwareHttpAdapter` (cui-http).

## What happened

`ETagAwareHttpAdapter` derives a per-principal `principalBinding` component of the ETag cache key
from the request's credential headers. The original implementation joined the raw header
name/value pairs into a single string with `&` and `=` delimiters and hashed the concatenation.
Because the header VALUES were not escaped and may themselves contain `&` / `=`, two different
principals could produce byte-identical concatenations — a classic delimiter-forgery collision.
The consequence is a merged cache identity: principal B could be served a representation cached
for principal A.

This was caught by the proactive security-audit sweep during finalize, not by the test suite, and
not by any pre-existing gate.

## Corrective rule

When deriving an identity/partition key from attacker-influenced string components, **digest each
component individually and then join the digests** — never join the raw values and digest once.
Equivalently: use an injective encoding (length-prefixing or per-component hashing) before any
concatenation that feeds a security-relevant key. Applied fix: each credential header's value is
digested on its own, and the fixed-width digests are joined.

## Generalisation for the epic

Worth a cross-project check: any cache key, dedup key, tenant partition key, or signature input
assembled by `String.join` / string concatenation over externally-supplied values is a candidate
for the same defect. A grep-able signature is a `+ "="` / `+ "&"` (or `String.join` over raw
header/param values) immediately upstream of a `MessageDigest` / `hashCode` call.
