envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:31:26Z

# Candidate lesson: a Sonar re-scan can return byte-identical issue keys after a verified fix — spot-check the code, do not trust the re-scan

Source signal: `default:sonar-roundtrip` / finalize wait-region observation (recurring gotcha).
Component: `plan-marshall:phase-6-finalize` (`workflow/sonar-roundtrip.md`) / finalize triage.

## What happened

After fix commits for a set of Sonar findings landed on the branch and were independently verified
by reading the post-fix source, a subsequent Sonar re-scan returned the SAME issues with
byte-identical issue keys and identical line numbers. The re-scan was stale — it had analysed a
superseded snapshot — but nothing in the returned payload distinguishes "still present" from
"analysed an older commit": the key and line are the same either way, so the re-scan reads as a
confirmation of an unfixed defect.

Treating that output at face value would have re-opened already-closed findings and driven an
unnecessary loop-back iteration against code that was already correct.

## Corrective rule

When a Sonar finding **reappears after a fix for that exact finding has landed**, do not accept
the re-scan as evidence the defect persists. First establish provenance: read the current source
at the reported file/line and confirm whether the flagged construct is still there. Only treat the
finding as live when the code itself corroborates it. This is the "establish provenance before
re-attempting a failing fix" principle applied to an external analyser's output rather than to a
test failure.

## Generalisation for the epic

Any finalize-time external-analyser roundtrip (Sonar, review bots, CI annotations) can report
against a stale revision. The durable remedy is to compare the analyser's reported analysed-commit
against the live HEAD before triaging, and to treat an absent/mismatched analysed-commit as
UNKNOWN rather than as a live finding. Worth considering as a hardening of the sonar-roundtrip
FIND step rather than as advice to the triaging agent.
