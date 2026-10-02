envelope_version=1
sender_type=plan
sender_id=attack-database-and-assertion-quality
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-31T17:23:40Z

# Candidate lesson: a guard that skips an assertion is the same defect as an assertion that does not assert — fix it with a fail-closed `matched` flag

## Observation

The plan's whole purpose was to eliminate assertions that do not assert. Its own
NEW test code reintroduced that exact class twice:

1. **Guard/normalisation mismatch** — a test guarded its assertion on an NFC
   property while the production pipeline normalises with NFKC. For 3 of 8
   generator inputs the guard was false, so the assertion body never ran. The
   test still passed, reporting coverage over inputs it never checked.
2. **Unguarded name-prefix branches** — an `if (name.startsWith(...)) { assert }`
   chain with no terminal `else`. Several declared attack-database entries
   matched no branch and were silently walked past.

Both were caught by **CodeRabbit on the PR**, not by the plan's own review steps
— even though the plan was explicitly a hunt for this defect class.

## Why it is durable and non-obvious

- The vanishing-assertion class survives *inside the very work that exists to
  remove it*, because the guard reads as defensive good practice. `if (applicable)
  { assert }` looks safer than an unconditional assert; it is strictly weaker.
- Green + silent is indistinguishable from green + verified from the outside.
  Neither coverage nor the assertion count catches it: the guard line IS covered.
- The plan's own gates did not catch it. The catching signal was an external
  review bot, which means the defect is not reachable by "run the tests again".

## Corrective rule (the fix that stuck)

Do not let a conditional decide whether an assertion runs. Instead make
non-matching an explicit failure:

```java
boolean matched = false;
for (...) { if (applies) { matched = true; assertThat(...); } }
assertTrue(matched, "no input exercised the assertion: " + describe(input));
```

The flag converts "the guard was never true" from an invisible pass into a
named failure. Apply it to every data-driven test whose per-input assertion sits
behind a predicate, and to every `startsWith`/`switch`-style dispatch over a
declared entry set (which additionally needs a terminal `else fail(...)`).

Secondary rule: when a test guard mirrors a production normalisation step, it
must name the same normalisation form the pipeline uses (NFKC vs NFC here). A
guard that paraphrases the production predicate will drift from it silently.

## Candidate scope

Two related but separable rules: (a) fail-closed `matched` flag for guarded
data-driven assertions — broadly applicable Java/JUnit testing standard;
(b) guard-must-mirror-production-normalisation — cui-http-local, since it is
about the DecodingStage NFKC/NFC split.

## Provenance

Plan `attack-database-and-assertion-quality`, PR #178 (merged as 30edfa3).
Signal source: `signal_automated_review_count` (CodeRabbit PR findings,
remediated in-run).
