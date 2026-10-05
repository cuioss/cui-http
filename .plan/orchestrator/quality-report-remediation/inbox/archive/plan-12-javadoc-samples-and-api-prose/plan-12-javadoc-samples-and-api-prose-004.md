envelope_version=1
sender_type=plan
sender_id=plan-12-javadoc-samples-and-api-prose
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-05T07:34:59Z

component=cui-http-core
category=bug

# Javadoc sample used URLParameterValidationPipeline's form-decoded return value as a request-body value

## Source

- Plan: plan-12-javadoc-samples-and-api-prose (PR #260)
- Signal: pr-comment finding e29d95 (CodeRabbit inline comment, resolved `fixed` by TASK-008)
- File: cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java (around line 345)

## What happened

A rewritten Javadoc sample validated request-body fields with `URLParameterValidationPipeline`
and built the outgoing `User` object from the value `validate()` returned. That pipeline uses
form semantics: `DecodingStage` turns a literal `+` into a space and decodes `%XX`. A
plus-addressed email such as `a+b@example.com` would therefore be sent as `a b@example.com`.

## Rule

`HttpSecurityValidator.validate()` returns a normalized (decoded) value, not the caller's
original input. For a value that is not a URL query parameter (a JSON or body field), either use
`validate()` only as a pass/fail check and build the payload from the original input, or use a
validator whose decoding matches the value's actual encoding. Never feed the returned value of a
URL-encoding-aware pipeline into a non-URL context.

## Suggested correction

Add a short "the returned value is decoded/normalized" warning to the `HttpSecurityValidator`
and `URLParameterValidationPipeline` Javadoc. Also check every other sample in the repository
that reuses a pipeline's return value outside the URL context it was designed for.
