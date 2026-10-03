envelope_version=1
sender_type=plan
sender_id=attack-database-and-assertion-quality
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-31T17:24:00Z

# Candidate lesson: a review bot's ownership claim is inferred from the class NAME — refute it against the source, not against plausibility

## Observation

CodeRabbit proposed re-pointing an ADR cross-reference from `DecodingStage` to
`NormalizationStage`, on the reasoning that Unicode normalization belongs to the
class called `NormalizationStage`. Checked against source, the opposite holds:

- `DecodingStage` owns the `Normalizer.normalize` call (NFKC for URL paths, NFC
  for parameter values).
- `NormalizationStage` carries **no `Normalizer` reference at all** — it performs
  RFC 3986 dot-segment resolution, i.e. *path* normalization, not *Unicode*
  normalization.

The suggestion was refuted and the reference left as-is.

## Why it is durable and non-obvious

- The bot's claim was highly plausible and would have looked correct to a
  reviewer skimming the diff. Accepting it would have made the ADR's reference
  wrong while making it *read* more consistent.
- The root cause is generic: a review bot reasons over names and diff context,
  not over the symbol graph. Wherever a project has two similarly-named classes
  whose names do not partition responsibility the way the names suggest, this
  class of confidently-wrong suggestion recurs.
- cui-http has a standing instance of exactly that trap: `NormalizationStage`
  does NOT do Unicode normalization; `DecodingStage` does. That naming overlap
  is the durable project fact worth writing down.

## Corrective rule

For any review-bot comment asserting *which component owns behaviour X*, verify
by locating the actual call/symbol in source before accepting. Name-based
plausibility is not evidence. When the refutation is confirmed, reply with the
symbol-level evidence (which class holds the call, which does not) rather than
with a bare disagreement.

## Candidate scope

Two candidates, cleanly separable:

- **Project fact (KNOWLEDGE, cui-http)**: `DecodingStage` owns Unicode
  normalization (`Normalizer.normalize`, NFKC for paths / NFC for parameter
  values); `NormalizationStage` owns RFC 3986 dot-segment resolution only and
  holds no `Normalizer` reference. Good candidate for the architecture-hints
  store rather than a lesson.
- **Review-triage rule (ACTIONABLE, cross-project)**: refute bot ownership
  claims against the symbol, not the class name.

## Provenance

Plan `attack-database-and-assertion-quality`, PR #178 (merged as 30edfa3).
Signal source: `signal_automated_review_count` (CodeRabbit PR comment, rejected
with source-level rationale).
