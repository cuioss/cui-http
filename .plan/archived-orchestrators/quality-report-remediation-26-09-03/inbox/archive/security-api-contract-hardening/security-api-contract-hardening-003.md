envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T15:49:22Z

# Candidate lesson: a Lombok class-level @Getter silently grows exported public API when a field is added

**Signal source**: `signal_qgate_pending_count` — one defect carried by two records:
Q-Gate finding `7baceb` (phase `3-outline`, source `qgate`) and user-review finding
`d961e2` (phase `3-outline`, source `user_review`). Both resolved `taken_into_account`
by the same deliverable revision; they are transmitted as ONE candidate because they name
one defect.

## What happened

Deliverable D5 instructed: "Where the class does not already retain config as a field, add
a private final SecurityConfiguration config populated from the constructor argument", in
order to give five validation pipelines a value-equality basis.

Verified at HEAD, those five classes carry a CLASS-LEVEL `@Getter`
(`URLPathValidationPipeline.java:72`, `URLParameterValidationPipeline.java:74`,
`URLParameterNameValidationPipeline.java:58`, `ContentTypeValidationPipeline.java:59`,
`HTTPHeaderValidationPipeline.java:83`, `AbstractValidationPipeline.java:57`) and today hold
no instance field of their own — `config` is passed straight into `createStages(config)` and
never retained. Lombok's class-level `@Getter` generates an accessor for every non-static
instance field, so adding the field would ALSO have emitted a new public
`SecurityConfiguration getConfig()` on all five classes. `module-info.java` exports
`de.cuioss.http.security.pipeline`, so that is public API surface growth.

D5's stated scope, its Javadoc contract, and all four of its success criteria described only
`equals`/`hashCode` value semantics. The new accessor was unmentioned, undecided, and pinned
by no test — it would have shipped as an unnoticed API addition on an exported package.

## Candidate rule

Adding an instance field to a class carrying a class-level Lombok `@Getter` is a public-API
change, not an internal one. Any deliverable that adds such a field to a type in an exported
package MUST make an explicit accessor decision — suppress
(`@Getter(AccessLevel.NONE)`) or accept-and-document — and MUST pin that decision with a
test, because the API growth is invisible in the diff of the change that causes it.

Generalisation worth the orchestrator's attention: the same invisibility applies to every
annotation-driven code generator whose output is keyed on *field presence* rather than on
the annotation site (`@ToString`, `@EqualsAndHashCode`, `@Builder`, `@Data`). See the sibling
candidate on `@ToString(callSuper = true)` from this same plan — the same defect class recurred
downstream in the SAME deliverable, which is the strongest argument that the rule belongs in
the corpus rather than in one plan's review notes.

## How it was resolved in this plan

D5 was revised to carry an explicit Public-API-surface decision: SUPPRESS. Each new field is
declared `@Getter(AccessLevel.NONE) private final SecurityConfiguration config`. The decision
is stated in D5's scope paragraph, the Change-per-file field declaration (marked mandatory,
with the `lombok.AccessLevel` import), the class Javadoc contract sentence, the Approach
clean-break paragraph, and a Risks row. Two new success criteria pin it, and a per-class
reflection assertion verifies no public `getConfig()` exists, so removing the annotation
later fails the build.

## Why this is a candidate and not a filed lesson

Classification and corpus placement are deferred to the orchestrator-side pickup.

## Provenance

- Plan: `security-api-contract-hardening`
- Finding hashes: `7baceb` (qgate), `d961e2` (user_review)
- File cited: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLPathValidationPipeline.java`
- Recorded 2026-08-29T11:32:04Z / 11:37:26Z, both resolved 2026-08-29T11:42:5xZ
