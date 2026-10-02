envelope_version=1
sender_type=plan
sender_id=plan-12-generator-implementation-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:37:25Z

# Candidate lesson: super-linear regex backtracking introduced in a test assertion helper

## What happened

Sonar flagged `java:S8786` (MAJOR, CODE_SMELL) at
`cui-http-core/src/test/java/de/cuioss/http/security/tests/CookieChaosAttackTest.java:436`:
"Simplify this regular expression to reduce its runtime, as it has super-linear performance due to
backtracking."

The offending pattern, in `assertCarriesCookiePrefix`, was:

```
^[\s\p{Z}\p{Cc}]+|[\s\p{Z}\p{Cc}]+$
```

The anchored-suffix alternation backtracks quadratically on a run of matching characters — and this
is a **security test helper fed by adversarial generators**, i.e. exactly the input distribution
that produces long runs of whitespace and control characters. A catastrophic-backtracking pattern
in the assertion path of a chaos test is a self-inflicted denial of service on the build.

## Remedy applied

Replaced with a linear two-pointer `stripDecoration` / `isDecoration` pair — an O(n) scan with no
backtracking. Coverage was preserved deliberately: explicit `SPACE_SEPARATOR`, `LINE_SEPARATOR`,
`PARAGRAPH_SEPARATOR` and `CONTROL` category checks retain the non-breaking forms (U+00A0, U+2007,
U+202F) that `Character.isWhitespace` reports `false` for and that the original `\p{Z}` class
covered. Full gate green at 6557 tests after the change.

## Candidate rule

Two parts:

1. Trim/strip logic written as an anchored alternation (`^X+|X+$`) is the canonical
   super-linear-backtracking shape. Write it as a two-pointer scan. This is not a
   micro-optimisation — in a test fed by adversarial generators it is a correctness-of-the-build
   concern.
2. When replacing a `\p{Z}`-class regex with `Character` predicates, `Character.isWhitespace`
   is NOT equivalent: it returns `false` for the non-breaking separators. Enumerate the Unicode
   general categories explicitly, or the replacement silently narrows coverage while every test
   still passes.

Point 2 is the reusable trap; it is the kind of substitution that looks like a pure refactor and
is not.

## Provenance

- Plan: plan-12-generator-implementation-correctness
- Finding: `04497d`, sonar-issue `java:S8786`, key `AaBN-L033f23caWmYlZr`, PR #171, resolution `fixed`
