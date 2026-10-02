envelope_version=1
sender_type=plan
sender_id=security-javadoc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T07:04:22Z

# Candidate lesson: a plan's own spec asserted a defect that did not exist, and the false claim propagated through the plan's artifacts

## Suspected components

- `plan-marshall:phase-3-outline` (spec/solution-outline authoring — the origin of the claim)
- `plan-marshall:phase-6-finalize` (the `finalize-step-security-audit` step re-asserted it downstream)

## What was observed

PLAN-04 (`security-javadoc-accuracy`) is a plan whose entire subject is **documentation accuracy** — correcting Javadoc claims that did not match the implementation.

Its own spec justified removing a default-locale `toLowerCase()` call in `AttributeParser` by asserting the removal "closes a latent Turkish-locale defect".

An executing agent probed the claim empirically and **refuted** it:

- `String.equalsIgnoreCase` is locale-independent — it compares via `Character.toUpperCase`/`toLowerCase` per code point, with no `Locale` involved.
- `Character.toUpperCase('ı')` (dotless i) is `'I'`, so a `tr` default-locale lowercasing followed by `equalsIgnoreCase` still matched correctly.

The removal was therefore a **pure redundancy removal with no behaviour change** — not a latent-defect fix.

The failure did not stop there. A later `finalize-step-security-audit` agent, working from the plan artifacts, **re-asserted the original false Turkish-locale claim** in its report. The claim came within one step of being written into a code comment — i.e. of being laundered from a plan artifact into the repository itself.

## Why this is durable

The defect is not "someone was wrong about Java locale semantics". It is structural:

1. A claim introduced at spec-authoring time is treated as **established fact** by every downstream agent that reads the spec, because nothing in the artifact distinguishes an author's hypothesis from a verified finding.
2. A downstream agent that *refutes* a spec claim has no channel that propagates the refutation back into the artifacts the next agent will read. The refutation lived only in one agent's return payload; the spec kept its original wording.
3. Consequently a false claim can travel spec -> execution report -> audit report -> code comment, gaining apparent corroboration at each hop purely by being restated.

The irony is the sharpest evidence: this was a plan *about* documentation accuracy, and it nearly shipped a fresh inaccuracy of its own manufacture.

## Candidate corrective directions (for orchestrator judgement)

- Spec claims that assert a *defect* (as opposed to a *change*) should carry an evidence marker — asserted vs. verified — so downstream agents know which ones are load-bearing and which are unverified author reasoning.
- A refutation of a spec claim should be a first-class outcome that writes back into the artifact (or files a finding against it), not merely a line in an agent return.
- Report-authoring steps that quote a rationale from an upstream artifact should quote it as *the spec claims X*, not as *X*.

## Signal provenance

Surfaced during PLAN-04 execution; the refutation is reproducible from Java semantics alone (`String.equalsIgnoreCase` + `Character.toUpperCase('ı') == 'I'`).
