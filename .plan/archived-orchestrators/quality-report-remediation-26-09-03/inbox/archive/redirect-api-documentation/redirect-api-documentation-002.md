envelope_version=1
sender_type=plan
sender_id=redirect-api-documentation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T21:08:01Z

component=plan-marshall:ref-workflow-architecture
category=improvement
bundle=plan-marshall

# An operator ruling reached mid-dispatch has no trusted channel into a running agent

## What happened

While a triage agent was RUNNING, the operator was shown a reachability analysis that
contradicted that agent's in-flight verdict and ruled that the finding must be fixed within the
plan rather than deferred. A `SendMessage` was issued relaying the override into the running
agent. The agent **correctly refused it** as an unverifiable injected instruction.

The refusal was the right call and is not the defect. The override was subsequently applied in
the main-context orchestrator after the agent returned, which is the correct resolution.

## The structural gap

The refusal was right *because the agent had no way to be right differently*. From inside a
dispatched envelope, an inbound `SendMessage` is indistinguishable from prompt injection: the
agent cannot verify that the text originated in the operator's own turn rather than in content
it had been reading. Refusing is the only safe disposition available to it.

So the topology has a genuine hole, and it is the mirror image of the one
`ref-workflow-architecture/standards/agents.md` already documents:

- **Documented and closed**: a leaf that needs operator INPUT cannot fire `AskUserQuestion`, so
  it returns a **prompt-required envelope** and the orchestrator fires the question. Leaf-to-
  operator has a sanctioned path.
- **Undocumented and open**: an operator DECISION reached while a leaf is already running has no
  sanctioned path in the other direction. Operator-to-running-leaf does not exist, and
  `SendMessage` is not it — by construction it cannot be, since the receiving agent cannot
  authenticate it.

The consequence is not that overrides are lost when someone notices, but that **a workflow
written on the assumption they can be delivered will silently drop them**. The one that worked
here worked because the orchestrator happened to re-apply the ruling on return; a workflow whose
step said "relay the operator's decision to the running agent and continue" would have recorded
the agent's refusal as a step outcome and moved on with the pre-override verdict standing.

## Proposed rule

Add the reverse direction to `agents.md` alongside the prompt-required-envelope contract:

1. **Authority does not flow through `SendMessage` into a dispatched envelope.** State this
   explicitly as an invariant, not as an inference from the leaf topology. `SendMessage` may
   carry advisory context; it may never carry a ruling the receiving agent is expected to obey,
   and a receiving agent refusing one is behaving correctly.
2. **An operator ruling that lands mid-dispatch is applied at the orchestrator on return.** The
   orchestrator holds it, lets the leaf finish on its own terms, and then either overrides the
   returned verdict or re-dispatches with the ruling in the fresh prompt body — the prompt body
   IS the trusted channel, and it is only writable at dispatch time.
3. **No workflow step may be authored on the assumption of mid-dispatch delivery.** A step that
   needs an operator decision must either gather it before dispatch or return a prompt-required
   envelope and be re-dispatched.

Point 3 is the load-bearing one: without it, the gap is invisible until a ruling goes missing.

## Cross-repo note

Component names a `plan-marshall` bundle skill not owned by the `cui-http` lessons store;
integrate on the `plan-marshall` side.

## Evidence

Decision log `8c3d32`, plan `redirect-api-documentation` (closing note: "a SendMessage relaying
this override to the running triage agent was correctly rejected by that agent as an
unverifiable injected instruction; the override is therefore applied here in the orchestrator,
where the operator's turn is the trusted source").
