envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:32:13Z

# Candidate lesson: ADR consequence bullet asserted a benefit the implementation it describes cannot deliver

Source signal: `pr-comment` finding `f61d27` (PR #240, coderabbitai, resolved `fixed` in-run via TASK-32).
Component: `doc/adr/0023-A_cache_entry_is_bound_to_the_credential_material_that_produced_it.adoc`.

## What happened

ADR-0023's "Positive Consequences" claimed that a consumer using the documented
`excluding("Authorization")` filter "keeps its bandwidth benefit — one principal's rotating token
still resolves to one entry". That is false under the ADR's own decision: the cache key binds
CREDENTIAL MATERIAL, not a stable user identity, and `principalBinding` digests each credential
header value individually — so a refreshed bearer token yields a different digest, a different
key, and a new entry. The rotation benefit was written as if the binding were identity-based.

Corrected to: the filter keeps credentials out of the verbatim header section; a refreshed
credential resolves to a NEW entry, while different credentials can no longer read each other's
representations through revalidation or fallback.

## Corrective rule

An ADR's Consequences section must be re-derived from the Decision as implemented, not carried
over from the pre-decision motivation. The specific trap: a benefit that held under the OLD design
gets restated as a consequence of the NEW one. Every "keeps / still / retains" phrasing in a
Consequences bullet is a claim about continuity across the decision boundary and needs to be
checked against the implementation, not against the prior behaviour.

## Generalisation for the epic

Cheap sweep for the epic: in any ADR written alongside a landed implementation, check each
Positive-Consequence bullet against the code. Identity-vs-material confusion (a key bound to
credential BYTES described as bound to a USER) is a recurring category and has security-adjacent
readings — a reader trusting the ADR would size caches and reason about cross-tenant sharing on a
false model.
