envelope_version=1
sender_type=plan
sender_id=plan-16-benchmark-and-ci-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T16:06:00Z

# Candidate lesson: a fail-closed predicate that splits-and-compares does not enforce the shape its docstring claims

## Observed signal

A `_is_post_cutoff`-style filter documented itself as **fail-closed** — anything
that does not conform is excluded. Its mechanism was an `rpartition`-based split
of the name, followed by a comparison of the trailing segment against a cutoff.
A non-conforming name (`zz-bad.json`) produced a trailing segment that sorted
past the cutoff, so the filter admitted it. The predicate was fail-**open** for
exactly the input class its docstring promised to reject.

## Why it is durable

This is claim-versus-mechanism drift, and the drift is invisible at review time
because both halves read as correct in isolation: the docstring states a
fail-closed contract, and the split-and-compare is a reasonable way to extract
the field it wants. What is missing is any step that asserts the input *is* the
shape the comparison assumes. Split-and-compare silently reinterprets a
malformed input as a well-formed one whose field happens to be the malformed
remainder — the worst possible failure mode for a security- or correctness-
gating predicate, because the wrong answer is well-formed enough that no caller
downstream can tell.

## Proposed durable directive

In any predicate documented as fail-closed:

- **Validate the whole shape first, then read fields from the validated value.**
  A full-string match (anchored regex or equivalent) that either accepts the
  whole name or rejects it — never a split whose remainder is compared.
- A `partition`/`rpartition`/`split` on unvalidated input inside a fail-closed
  predicate is the smell: the separator's absence, or its presence in an
  unexpected position, both yield a value the comparison will happily process.
- Pair every fail-closed docstring claim with a **negative control test** whose
  input is deliberately non-conforming and whose expected result is exclusion.
  The claim in prose is not the guard; the negative control is.

## Suggested classification (orchestrator judgement)

Likely `bug` at the site, `anti-pattern` as the generalized rule. The
generalized form is the valuable half — the specific `zz-bad.json` case is
already fixed.
