envelope_version=1
sender_type=plan
sender_id=decoding-normalisation-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-06T07:10:23Z

component=cui-http
category=anti-pattern

# A fix for an asymmetry defect reintroduced the asymmetry — re-check every review fix against the invariant it restores

## What happened

Plan `decoding-normalisation-hardening` (epic `quality-report-remediation`) existed to remove
raw-versus-encoded verdict asymmetries in `DecodingStage` — the principle later written down as
ADR-0017: *DecodingStage security gates must not create a raw-versus-encoded verdict asymmetry*.

**Review round 1 (PR #209, commit 9fc411d).** CodeRabbit finding `16d02c` on
`DecodingStage.java:317`: NFKC can fold fullwidth hex into an ASCII escape AFTER the existing
surviving-encoding check, so `%25%EF%BC%92%EF%BC%A6` can become `%2F` in the returned value.
The suggested patch — applied verbatim in `b307f6f` — was:

```java
String result = config.normalizeUnicode() ? normalized : decoded;
if (SURVIVING_ENCODING_PATTERN.matcher(result).find()) { ... }
```

**Review round 2 (PR #210, commit 1bf0fae).** CodeRabbit finding `0b5571` on the very same
method: `result` is the value SELECTED FOR RETURN, and that selection is config-dependent. When
`normalizeUnicode()` is `false` (the `lenient()` preset), `result` holds `decoded`, so the
NFKC-assembled `%2F` living in `normalized` is never checked. The round-1 fix made the verdict
depend on a configuration flag — the exact defect shape the plan and ADR-0017 exist to forbid.
The round-2 fix reads `normalized` unconditionally and adds a lenient-preset regression
(`d41da0d`).

A second instance of the same class rode the same round: finding `be7115` — the round-1
`NormalizationStage` delimiter change rejected scheme-bearing complete URIs with a query or
fragment, contradicting ADR-0016's own promise that scheme-bearing input is unaffected.

Only the second review round caught either. There was no self-review surfacer for this domain
(see the sibling candidate-lesson on `pre-submission-self-review`), so CodeRabbit's incremental
re-review was the sole net.

## Rule

When a plan exists to eliminate a defect CLASS, every fix applied during review must be
re-checked against the class invariant before it is committed — the review fix is written under
time pressure, against one reported symptom, and is exactly where the class silently returns.

Operationally, for a verdict-symmetry invariant:

- Check the operand, not the outcome. A security gate must read the **canonical/normalized**
  form, never the value selected for RETURN, because the return selection is a presentation
  choice that a configuration flag is allowed to change.
- For any gate touched during review, ask explicitly: *does this verdict differ between
  `defaults()` and `lenient()`?* If yes, the fix reintroduced a config-gated verdict.
- Pin the answer with a regression that runs the SAME input under BOTH presets and asserts the
  SAME failure type. A single-preset regression cannot observe an asymmetry.
- When the plan has an ADR, re-read the ADR's own claim against the patched code before
  committing — both round-1 defects here were detectable as direct contradictions of ADR-0016
  and ADR-0017 text that already existed in the same PR.

## Impact

The reintroduced gap was externally reachable (`URL_PATH` under `lenient()`), CWE-177 class, and
would have shipped had the review budget not allowed a second round.
