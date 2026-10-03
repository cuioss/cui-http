envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T15:51:09Z

# Candidate lesson: @Getter(AccessLevel.NONE) suppressed the accessor but not the generated toString, so the same "no API change" claim was only half-enforced

**Signal source**: `signal_automated_review_count` (finding hash `8fcf23`, PR #169 inline comment 3886843735, author CodeRabbit, resolution `fixed`)

## What happened

Deliverable D5 had already been revised — after TWO outline-phase findings (`7baceb`,
`d961e2`) — to suppress the Lombok-generated public accessor on the new `config` field, and
its contract stated "public API unaffected". The chosen mechanism was
`@Getter(AccessLevel.NONE)` on the field.

That suppresses only the ACCESSOR. The five pipeline classes also carry
`@ToString(callSuper = true)`, which still included the `config` field, so the retained
configuration leaked into the rendered `toString()` of all five pipelines. The stated
contract was therefore only half-enforced, and the leak reached the PR.

Caught by an automated review bot, not by the plan's own verification — and notably not by
the two prior findings that had raised exactly this class of concern about exactly this
field.

## Candidate rule

`@Getter(AccessLevel.NONE)` is not a general "do not expose this field" switch. It disables
ONE generator. Every other field-presence-keyed Lombok generator on the class continues to
consume the field: `@ToString`, `@EqualsAndHashCode`, `@Builder`, `@Data`, `@With`.

So: when a field is added specifically to be internal, the suppression must be applied per
generator present on the class — `@Getter(AccessLevel.NONE)` AND `@ToString.Exclude` AND
`@EqualsAndHashCode.Exclude` as applicable — and each suppression needs its own pinning
test. Enumerate the class's generators first; do not suppress the one you happened to think
of.

**The strongest form of this candidate is about review, not about Lombok.** Two prior
findings identified the field-presence-keyed generation hazard on this exact field and still
missed a second generator on the same class. A finding of the form "annotation X generates
from field presence" should trigger a sweep of ALL such annotations on the affected type,
because the first instance found is rarely the only one. Recording this as a Lombok lesson
alone would under-generalise it.

## How it was resolved in this plan

Fixed in `3313a97`: `@ToString.Exclude` added to the `config` field in all five pipelines;
each class's Value Equality Javadoc now states the `toString` exclusion alongside the
accessor one; `shouldNotExposeConfigInToString` pins it. CodeRabbit confirmed and the thread
is resolved.

## Relationship to sibling candidates

Same deliverable and same field as the `7baceb`/`d961e2` candidate transmitted earlier in
this stream. They are transmitted separately on purpose: the first is "a field addition grew
the API", this one is "the fix for that was incomplete because the hazard class has more
members than were enumerated". The recurrence within a single deliverable is the evidence.

## Provenance

- Plan: `security-api-contract-hardening`
- Finding hash: `8fcf23`, type `pr-comment`, severity `warning`
- File: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/ContentTypeValidationPipeline.java` (and four sibling pipelines)
- PR #169, inline comment 3886843735; recorded 2026-08-29T15:33:56Z
