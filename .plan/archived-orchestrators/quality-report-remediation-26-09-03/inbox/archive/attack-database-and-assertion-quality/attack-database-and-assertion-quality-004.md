envelope_version=1
sender_type=plan
sender_id=attack-database-and-assertion-quality
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-31T17:24:25Z

# Candidate lesson: an ADR's Decision statement must be scoped to what its enforcement mechanism actually enforces

## Observation

ADR-0010 exists to prevent overclaiming. Its own Decision statement overclaimed:
it was written as a general rule, while the mechanism backing it is an
**explicit-list registry** — it enforces the rule only for the entries the list
names, and says nothing about anything else. The Decision text was rescoped to
match the registry during this run.

## Why it is durable and non-obvious

- The defect is recursive: the document whose subject is overclaiming was itself
  the overclaimer. That is not a coincidence — a normative document is written in
  the voice of the rule it wants, and the gap between "the rule I want" and "the
  rule my mechanism applies" is invisible from inside the document.
- An explicit-list registry is the specific trap. It reads like a complete
  enforcement mechanism because every entry in it is genuinely enforced; what it
  cannot tell you is what is *not* in the list. A Decision written against it
  will describe universal coverage unless someone deliberately checks the
  quantifier.
- No gate catches this. It is prose-vs-mechanism agreement, so neither the build,
  the tests, nor static analysis has any purchase on it.

## Corrective rule

When writing or reviewing an ADR (or any normative doc) whose enforcement is an
explicit list, an allow-list, or a registry, state the Decision with the
mechanism's actual quantifier: name the registry, and say the rule holds *for
registered entries*, with the unregistered residual stated rather than left
implicit. Review trigger: any Decision sentence with an unqualified universal
("all", "every", "no X may") whose backing mechanism is enumerable.

## Candidate scope

Documentation / ADR-authoring standard; cross-project. Possible overlap with
existing doc-authoring or ADR standards — the orchestrator should dedup against
those before filing.

## Provenance

Plan `attack-database-and-assertion-quality`, PR #178 (merged as 30edfa3).
Observed while editing ADR-0010 in this run.
