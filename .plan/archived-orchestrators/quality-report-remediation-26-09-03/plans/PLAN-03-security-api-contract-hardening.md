# PLAN-03: Security API Contract Hardening

epic: quality-report-remediation
workstream: WS-01

## Objective

Fix the behavioural defects in the security package's public data, config, and monitoring types —
the findings where the code does something observably wrong rather than merely documenting itself
badly. A quoted charset parameter is returned with its quotes intact, so any caller resolving it to
a `Charset` gets an exception; `SecurityEventCounter`'s accessors return zero-count entries after a
reset despite Javadoc promising otherwise; every pipeline's `equals`/`hashCode` ignores its
configuration, so a `strict()` pipeline equals a `lenient()` one; a configured body-size limit above
2 GB silently drops; and two `Cookie` methods throw an undocumented unchecked exception.

## Deliverables

1. **Strip surrounding quotes in `AttributeParser`** when extracting a quoted attribute value, and
   unescape `\"`, so `text/html; charset="UTF-8"` yields `UTF-8`. (SV-8)
2. **Reconcile `SecurityEventCounter`'s Javadoc with its behaviour** — either add the `> 0` filter
   the docs promise or correct the docs; also correct the "atomically resets all counters" claim
   (each counter is reset independently, so concurrent increments interleave) and the "all
   operations complete in constant time" claim (`getAllCounts`/`getTotalCount` are O(n)). (SV-9)
3. **Make the lenient preset's double disablement explicit** — it turns off both the
   double-encoding gate and the entire Unicode-normalization/homoglyph check through one switch.
   Add an explicit callout, since presets are the common entry point. (SV-11)
4. **Handle the BODY length limit above `Integer.MAX_VALUE`** — currently silently capped, which is
   fail-closed but diverges from the configured value without any signal. Either widen the check or
   reject/warn on an unrepresentable configured limit. (SV-13)
5. **Give the pipelines meaningful value semantics** — `equals`/`hashCode` currently ignore
   configuration entirely, so pipelines with different security postures compare equal, which is
   misleading for map keys and deduplication. Include the configuration, or document why not. (SV-14)
6. **Document the undocumented throw** on `Cookie.hostPrefix`/`securePrefix`, which throw unchecked
   `UrlSecurityException` for any suffix outside the RFC 3986 unreserved set with no `@throws`. (SV-20)

Six deliverables — at the split guard. Proceeding unsplit: all six are small, independent,
single-file behavioural corrections within one package with no ordering constraint between them;
splitting would produce two plans of three trivial edits each and double the review overhead
without reducing risk. Rationale recorded as an epic decision.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| SV-8 | LOW (downgraded from MEDIUM in verification) | `AttributeParser` returns quoted values with quotes intact |
| SV-9 | MEDIUM | `SecurityEventCounter` count-accessor Javadoc is false after `reset()` |
| SV-11 | LOW | The lenient preset disables two distinct defenses via one switch |
| SV-13 | LOW | `LengthValidationStage` BODY limit silently capped at `Integer.MAX_VALUE` |
| SV-14 | LOW | Pipeline `equals`/`hashCode` ignore configuration |
| SV-20 | LOW | `Cookie.hostPrefix`/`securePrefix` throw undocumented `UrlSecurityException` |

## Claim Labels

- OBSERVED: these findings are stated at `source/security-validation-review.adoc` § `SV-8`, `SV-9`,
  `SV-11`, `SV-13`, `SV-14`, `SV-20`.
- OBSERVED: SV-8 carries a verification NOTE downgrading it from MEDIUM to LOW — the quote retention
  is confirmed, but the originally-claimed throwing consumer does not exist inside the library, so
  the impact is latent and only bites an external caller. SV-9 is recorded as reported by two
  reviewers. Read at `source/security-validation-review.adoc` § `SV-8`, `SV-9`.
- HYPOTHESIS: `AttributeParser.extractAttributeValue` returns `value.trim()` without stripping
  surrounding quotes, and `HTTPBody.getCharset()` passes it through — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/data/AttributeParser.java`
  § `extractAttributeValue` (verify-at-outline)
- HYPOTHESIS: no `Charset.forName` call exists anywhere in the main sources — an asserted ABSENCE,
  and the load-bearing premise of SV-8's downgrade. Verified exactly as a presence would be:
  confirm/refute by grepping `cui-http-core/src/main/java/` for `Charset.forName` (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: AttributeParser.extractAttributeValue still returns value.trim() with no quote stripping at f1ba539
- HYPOTHESIS: `SecurityEventCounter.getAllCounts` streams all map entries with no `> 0` filter and
  `reset()` sets each counter to 0 rather than removing entries — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/monitoring/SecurityEventCounter.java`
  § `getAllCounts`, `reset` (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: No Charset.forName call exists anywhere in cui-http-core main sources; the asserted absence holds, so SV-8's impact stays latent
- HYPOTHESIS: all five pipeline classes carry `@EqualsAndHashCode(callSuper=false, of={})` except
  `HTTPHeaderValidationPipeline`, which compares only `validationType` — confirm/refute at the five
  classes under `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/` (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: SecurityEventCounter.getAllCounts still streams all entries with no > 0 filter
- HYPOTHESIS: `LengthValidationStage` casts the `long` `maxBodySize` through
  `(int) Math.min(config.maxBodySize(), Integer.MAX_VALUE)` — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/LengthValidationStage.java`
  § the BODY limit branch (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Four pipeline classes still carry @EqualsAndHashCode with an empty of={}
- HYPOTHESIS: the derived claim that all six deliverables are independent with no ordering
  constraint — the basis of the proceed-unsplit rationale — is the orchestrator's inference from
  the reports' file attributions. Confirm/refute at outline by checking whether any two touch the
  same symbol (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: LengthValidationStage still casts maxBodySize through Integer.MAX_VALUE
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: deliverable 5 changes value semantics of public types. If any main or test
  code relies on the current configuration-blind equality, changing it is breaking — check for such
  reliance before scoping, and prefer documenting the current semantics if reliance is found.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/data/AttributeParser.java` — `extractAttributeValue` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/data/HTTPBody.java` — `getCharset` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/data/Cookie.java` — `hostPrefix`, `securePrefix` Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/monitoring/SecurityEventCounter.java` — `getAllCounts`, `getFailureTypeCount`, `reset`, class Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` — `lenient` Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/LengthValidationStage.java` — the BODY limit branch (verify-at-outline)
- HYPOTHESIS: the five pipeline classes under `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/` — `@EqualsAndHashCode` annotations (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/data/`, `.../security/monitoring/`, `.../security/validation/`, `.../security/pipeline/` — corresponding tests (verify-at-outline)
- ⛔ **NOT this plan's surface:** `.../security/generators/` and `.../security/database/` — WS-04 owns those.

## Dependencies and Sequencing

- Depends on: PLAN-02 (WS-01 is strictly sequential).
- Overlaps with: PLAN-01, PLAN-02, PLAN-04 — same package tree.
- Adjacent to: `doc/http-security/configuration.adoc`, which the report verifies matches
  `SecurityDefaults` value for value. Deliverable 3 adds a Javadoc callout but must not change any
  preset VALUE; if a value changes, that doc's verified accuracy breaks — flag it for PLAN-14.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-03-security-api-contract-hardening.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
