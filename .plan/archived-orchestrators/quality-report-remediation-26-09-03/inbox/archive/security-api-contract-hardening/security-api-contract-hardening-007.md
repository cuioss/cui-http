envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T15:50:46Z

# Candidate lesson: unquote() stripped an escaped final quote, contradicting its own Javadoc

**Signal source**: `signal_automated_review_count` (pr-comment finding `8fcf23`'s sibling — finding hash `75ae6a`, PR #169 inline comment 3886843730, author CodeRabbit, resolution `fixed`)

## What happened

`AttributeParser.unquote()` treated EVERY trailing quote as a closing delimiter. For input
`name="abc\\"` — where the final quote is escaped by an odd number of preceding backslashes,
so the value is genuinely unbalanced — the method stripped the quote and returned `abc\`
instead of the verbatim input.

The method's own Javadoc stated that unbalanced values are returned unchanged. So the
implementation contradicted its documented contract, on a security-relevant parser, and the
existing quoted-pair test suite did not catch it: the tests covered escape handling INSIDE
the value but never the boundary case where the escape lands on the delimiter itself.

Caught by an automated review bot on the PR, not by the plan's own verification sweep.

## Candidate rule

For any delimiter-stripping routine that also honours an escape mechanism, the escaped-
delimiter-at-the-boundary case is a mandatory test, in BOTH directions:

- odd backslash count before the final delimiter → escaped → not a delimiter → return verbatim
- even backslash count before the final delimiter → the backslashes escape each other → the
  delimiter is real → unwrap

The parity of the escape run, not the presence of an escape character, is what decides. A
test suite that exercises escapes only in the interior of a value will pass while the
boundary is wrong.

Second, sharper half: this defect was a *documented contract violated by its own
implementation*. The Javadoc already stated the correct behaviour. A review pass that
compares each method's stated contract against its branch structure would have found it
without any knowledge of quoting rules — which is a cheaper and more general detector than
domain-specific parser review.

## How it was resolved in this plan

Fixed in `3313a97`: an odd-backslash guard added before the strip; both Javadoc blocks
corrected to name the escaped-trailing-quote case explicitly; regression tests
`shouldReturnValueUnchangedWhenTrailingQuoteIsEscaped` and
`shouldUnwrapWhenTrailingQuoteFollowsEvenBackslashes` pin both directions. CodeRabbit
confirmed the fix and the thread is resolved.

## Why this is a candidate and not a filed lesson

Classification and corpus placement are deferred to the orchestrator-side pickup. Note this
is a caught-in-run defect: it slipped the plan's own verification and was recovered by the
review bot, which is the failure mode most worth counting across the epic.

## Provenance

- Plan: `security-api-contract-hardening`
- Finding hash: `75ae6a`, type `pr-comment`, severity `warning`
- File: `cui-http-core/src/main/java/de/cuioss/http/security/data/AttributeParser.java`
- PR #169, inline comment 3886843730; recorded 2026-08-29T15:33:48Z
