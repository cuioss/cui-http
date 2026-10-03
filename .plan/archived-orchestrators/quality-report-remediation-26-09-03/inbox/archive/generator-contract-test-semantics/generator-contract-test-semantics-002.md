envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T15:24:33Z

component=cui-http-test-generators
category=bug
source_plan=generator-contract-test-semantics
source_signal=pr-comment
source_finding=3142ee
pr=164

# Valid-input generator validated attribute presence but not attribute value

A "valid cookie" generator's `isValidAttribute` accepted any non-empty
`Domain=`, `Path=`, or `Max-Age=` value, so `Path=../../../` and
`Max-Age=-1` were emitted as VALID inputs. `split(";")` additionally
dropped a trailing empty field, so a dangling separator (`Secure;`)
passed as well. The defect was caught by a review bot on PR #164, not
by the test suite, because the generator's own contract test asserted
only that attributes were present.

## Rule

A generator that produces "valid" inputs for a security pipeline is a
trust-boundary fixture: its accept predicate must reject the very
attack markers the pipeline exists to catch. Validate the attribute
VALUE (traversal markers, sign/range for numerics), not merely its
presence, and preserve trailing empty fields when splitting so a
malformed dangling separator fails validation instead of vanishing.

## Impact

A valid-input generator that emits hostile values makes the paired
"pipeline accepts every valid input" contract test assert the opposite
of what it claims — it certifies that the pipeline accepts an attack.
