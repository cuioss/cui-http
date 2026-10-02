envelope_version=1
sender_type=plan
sender_id=validated-redirect-following
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T19:56:01Z

component=pm-dev-java:java-core
category=anti-pattern
bundle=pm-dev-java

# A NOSONAR marker written above the declaration suppresses nothing — and here it was masking a true positive

## Observation

Sonar filed `java:S4449` twice against
`cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpHandler.java`
(`sanitizeForMessage`, line 611) during plan `validated-redirect-following`.
Two distinct defects compounded:

**1. The marker was placed where it does nothing.** The first attempt wrote

```java
// NOSONAR java:S4449 - <rationale>
static String sanitizeForMessage(String value) {
```

Sonar honours `NOSONAR` **only on the same line as the reported issue**. A
marker on the preceding comment line suppresses nothing, which is exactly why
the identical rule was re-reported on the next scan under a second issue key
(`AaBdkHT6xOuhKNiEcVQb`, then `AaBeQjeRu2...`). The suppression looked applied
and was not.

**2. The stated rationale was wrong, so the suppression was masking a real
defect.** The recorded rationale claimed a rule/annotation-model mismatch —
"the package is `@NullMarked` (JSpecify) and Sonar's
`javax.annotation.Nullable` vocabulary does not recognise it". That was not the
cause. S4449 was a **true positive**: a `@Nullable` parameter was being passed
into `sanitizeForMessage`, whose parameter is non-null. The correct fix was to
correct the declaration / call chain so a null can no longer arrive — which is
what the final commit did. `sanitizeForMessage` now carries no suppression at
all.

## Syntax facts worth codifying

- `// NOSONAR` must be on the **same line** as the finding. A marker on the
  comment line above is a no-op.
- A bare `// NOSONAR` suppresses **every** rule on that line, not just the one
  the author had in mind. It is almost never what is wanted.
- The rule-scoped, reviewable form is the annotation:
  `@SuppressWarnings("java:S4449")` on the declaration, with a plain comment
  above carrying the rationale. This file already contains the correct shape at
  the private constructor:

```java
// NOSONAR java:S107 - <rationale prose>
@SuppressWarnings("java:S107")
private HttpHandler(...)
```

  (the `@SuppressWarnings` is what actually suppresses; the comment is prose).

## Suggested rule

1. Prefer `@SuppressWarnings("java:SXXXX")` on the declaration over any
   `NOSONAR` comment. It is rule-scoped, survives reformatting, and is visible
   to the compiler and IDE.
2. Never write a bare `// NOSONAR`.
3. **Before suppressing, state the rationale and check it.** A suppression
   whose rationale is wrong is strictly worse than no suppression: it converts
   a true positive into a permanently silenced one. The tell here was that the
   rationale appealed to a tooling mismatch rather than to the actual data
   flow — if the rationale does not name the concrete call sites and show that
   the condition cannot arise, it is not yet a rationale.
4. A re-reported rule after a "suppression" landed is evidence the suppression
   is not in effect — investigate placement before re-suppressing.

## Evidence

Plan `validated-redirect-following`, PR cuioss/cui-http#186. Findings
`65fd19` and its sibling (`sonar-issue`, `java:S4449`,
`HttpHandler.java:611`), both `resolution: suppressed` with the incorrect
rationale recorded in `resolution_detail`.
