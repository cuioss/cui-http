envelope_version=1
sender_type=plan
sender_id=plan-13-asciidoc-specs-requirements-adrs
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-05T15:33:41Z

# Candidate follow-up: CharacterValidationStage Javadoc/comment still claims BODY is ASCII-only at default

- Suggested component: cui-http-core (de.cuioss.http.security.validation.CharacterValidationStage), project-local
- Suggested category: bug (production-Java doc drift, deferred)
- Signal source: Q-Gate / pre-submission self-review
- Evidence: decision log fc8f11, "Follow-up: CharacterValidationStage.java comment at the >255 branch still says HEADER_VALUE/BODY are ASCII-only at the default (production Java, out of scope)". Decision log c7af13: "Follow-up (production Java): CharacterValidationStage Javadoc lines ~135-137/433-436 claim BODY is ASCII-only at default".

## What happened

PLAN-13 corrected the doc claim (functional-requirements.adoc BODY extended-ASCII default). The same wrong claim is still in production Java comments and Javadoc, which the plan's no-production-Java scope did not allow it to edit.

## Why it matters

The code comments now contradict the reconciled specification. This is the same doc-versus-code drift class PLAN-13 was meant to remove.

## Suggested fix

Stage a small follow-up plan, or add this to a sibling plan that is allowed to touch production Java, to correct the CharacterValidationStage comment at the >255 branch and the Javadoc around lines 135-137 and 433-436.
