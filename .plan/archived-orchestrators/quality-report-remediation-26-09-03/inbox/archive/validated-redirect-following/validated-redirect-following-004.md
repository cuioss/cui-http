envelope_version=1
sender_type=plan
sender_id=validated-redirect-following
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T19:56:28Z

component=cui-http-core
category=improvement

# THIRD recurrence: @EnableMockWebServer(useHttps = true) is still unusable project-wide

## Routing note for the orchestrator

**This is not a new lesson — it is a third recurrence of existing active lesson
`2026-08-29-12-001` ("HTTPS MockWebServer unusable: okhttp/mockwebserver3
binary incompatibility").** Gate 1 resolves to `merge_into`. Please append the
`## Recurrence` section below to that lesson rather than allocating a new one.

## Recurrence — plan `validated-redirect-following` (PR #186)

`@EnableMockWebServer(useHttps = true)` remains broken on the same pre-existing
`mockwebserver3` / okhttp mismatch (`Platform.configureTlsExtensions` no longer
declared by the resolved okhttp).

This is the **third consecutive plan** the defect has cost coverage on, and the
**second consecutive plan** in which the specific casualty was the same
assertion: the **HTTPS → HTTP downgrade refusal could not be driven
end-to-end** and had to be asserted at the redirect-policy seam instead.

That repetition is itself the signal. This plan's entire subject was
validated redirect following — i.e. transport-security decisions on the
redirect chain — and the single most security-relevant behaviour in that
subject (refusing a downgrade hop) still has no end-to-end test, for the third
time, for the same reason.

## Escalation this recurrence justifies

The previous recurrence note called this "a standing hole in the project's
ability to test its own transport security". Three plans in, it is now
predictable rather than incidental: **any plan in this repo that touches TLS or
redirect security will hit it, and will pay for it by downgrading an
end-to-end assertion to a seam-level one.** The remediation (align the
okhttp / mockwebserver3 versions on the test classpath, then add one HTTPS
smoke test as the regression guard) should be scheduled as its own plan rather
than continuing to be absorbed as a per-plan caveat.

*Filed from plan `validated-redirect-following` on 2026-09-01.*
