# WS-06: Documentation Set

epic: quality-report-remediation

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-06-documentation-set.md` and is tracked in the epic
> `status.json` `workstreams[]` field. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

This workstream owns every document that describes the library to a reader: Javadoc and
`package-info.java` prose, the AsciiDoc set under `doc/`, the fifteen ADRs, `LogMessages.adoc`,
and the requirement and traceability documents. It is the epic's largest finding population (28
`F-documentation-*` items plus the documentation halves of a dozen code findings) and the one
with the sharpest failure mode: samples that do not compile, samples that invert the
fail-secure contract, requirement documents marked VERIFIED for properties the library does not
provide, and a pipeline-selection matrix that routes host and IPv6 concerns to a pipeline which
only character-checks them. It closes when every code sample compiles against the real API,
every numeric and coverage claim is re-derived from the code that backs it, and no document
asserts a protection the library does not deliver.

## Scope

- In scope: every `package-info.java` and class-level Javadoc, every `.adoc` under `doc/`
  (including `doc/adr/`, `doc/http-security/`, `LogMessages.adoc`), `README.adoc`, `CLAUDE.md`,
  `agents.md`, and `src/site`.
- Out of scope: all production Java code except Javadoc comments — a documentation plan never
  changes behaviour; all test Java code except its Javadoc. When a document is wrong because
  the *code* is wrong, the code fix belongs to WS-01..WS-04 and this workstream only reconciles
  the prose afterwards.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-12-javadoc-samples-and-api-prose | staged | Non-compiling and contract-inverting samples, promised-but-absent detections, missing usage examples |
| PLAN-13-asciidoc-specs-requirements-adrs | staged | Specification, requirement, traceability and ADR reconciliation; stale counts and coverage claims; the ADR index |

## Sequencing and Surface Notes

- **Both plans are sequenced last in the epic**, after WS-01 through WS-05 have landed. Most
  documentation findings are "the document describes behaviour X while the code does Y", and
  the correct prose is only knowable once the code plans have decided whether to implement X or
  retract the claim. Fixing the prose first guarantees a second rewrite.
- The two plans are surface-disjoint — PLAN-12 owns `.java` files (Javadoc only), PLAN-13 owns
  `.adoc` and `.md` files — so they MAY run concurrently at the epic's scope of 2.
- PLAN-12 conflicts with WS-01..WS-04 at file granularity: it edits Javadoc inside the same
  `.java` files those workstreams edit. It must not run concurrently with any of them, which is
  a second, independent reason for the last-in-queue placement.
- PLAN-13 is genuinely disjoint from every code plan (`doc/**`, `README.adoc`, `CLAUDE.md`,
  `agents.md` only) and is therefore the epic's best concurrency partner for a late code plan
  if the queue needs one.
- `F-documentation-1` (the canonical build command points at `.plan/execute-script.py`, which
  is gitignored and not in the repository) is a genuine fork: the fix is either to commit the
  executor or to change every document that names it. PLAN-13 surfaces it to the operator
  rather than deciding it.
