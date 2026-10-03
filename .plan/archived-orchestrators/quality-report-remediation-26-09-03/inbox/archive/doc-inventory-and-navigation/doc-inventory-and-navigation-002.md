envelope_version=1
sender_type=plan
sender_id=doc-inventory-and-navigation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T07:28:39Z

# Candidate lesson: the source report's named sites were treated as the whole population

## Observation

The plan was created to fix a set of documentation defects enumerated in a
quality report. CodeRabbit's review of PR #161 then found four actionable items,
three of which were real defects the plan's own gates had missed:

- `40ede7` — `doc/http-security/README.adoc:96` publishes RFC 3986 query-component
  rules for parameter names while `doc/http-security/functional-requirements.adoc:195`
  still states HTTP-14 `PARAMETER_NAME` follows RFC 7230 token rules. This is an
  **RFC 7230 -> RFC 3986 misattribution at a SECOND site the source report never
  named** — the same defect class the plan was created to fix, and the same class
  already remediated in commit `2a86a59` on the production side.
- `5b0f94` — `doc/http-security/functional-requirements.adoc:38` carries a note
  saying BODY pipeline creation throws and body validation is application-layer,
  while HTTP-1 (lines 27-28) still REQUIRES support for request body parameters.
  Two mutually contradictory capability claims in one document.
- `038ae0` — `doc/http-security/specification/compliance-traceability.adoc:690`
  hard-codes a database inventory the same document already declares
  non-authoritative.

## Why this is epic-level

The plan scoped its remediation to the sites the source quality report named. It
did not, for any defect class, sweep the corpus for OTHER instances of that same
class. So a report that under-enumerates propagates its blind spot straight
through the plan, and the residue is found by an external reviewer after the fact
rather than by the plan's own gates.

The RFC misattribution is the sharpest instance: the plan knew the exact wrong-RFC
pattern and the exact right answer, and still shipped a second live copy of it.

## Candidate rule

When a plan remediates a defect class that is *characterisable as a pattern*
(a wrong citation, a stale count, a contradictory capability claim), the
deliverable must include a corpus-wide content sweep for that pattern, and the
report's named sites are the sweep's SEED, never its population. A defect class
worth a deliverable is worth a `search --content` pass.

## Evidence

- Findings `40ede7`, `5b0f94`, `038ae0` (all `resolution: fixed`, addressed as
  in-run follow-up TASK-7 / TASK-8 / TASK-9 on the PR branch)
- Reviewed commit sha `09284fd6166ba0b66458e75bf7b35085b12d9dd9`
- Prior same-class fix on the production side: commit `2a86a59`
