# Cinema outcome evidence support

Owner: current support root; integration owner: Cinema root, target PR #715
(`cinema/fluid-light-stage-20261110`). Combined dialog candidate #725 must retain
this fix after exact-source integration and focused caller checks.

## Initial checkpoint — WIP / UNVERIFIED

Remote baseline: `041abb081ae7dac637d3f0fabdae525f19acf117`.
Equivalent local baseline: `409bf1118546165b228d4b33fb63330daaa5fad0`.
Shared tree: `dc17f550b84cf368c997975134e80a12bdbdb2c2`.

The real AgentActivity completion path passes TurnLifecycle.deliver() into
CockpitAnimationTriggers.turnEnded before checking the returned result and
current delivery grants. deliver() establishes one-shot lifecycle closure,
not a successful task. The trigger currently turns all unfinished work and
approval steps into DONE whenever that Boolean is true.

Plan: reproduce on the actual current classes, preserve single delivery and
scene return, and distinguish accepted response from verified task outcome.
Reuse AgentRunResult/AgentTaskEvidence and the existing bus and renderer.
No new store, engine, inference, permissions or learned authority.

Experiment compatibility: EXP-07 negative regression evidence and EXP-11
existing orchestrator/presentation contract. This is a product correctness
repair, not a promotion of research or model quality.

## Implementation checkpoint — WIP / one known failing regression

Response delivery now follows the existing session, consent and result guards.
Only accepted, complete responses are delivered; the existing matching task
evidence is separately required for verified work completion. Unconfirmed work
uses a neutral UNVERIFIED state. Pending approval never becomes approval through
response delivery; already observed tool results retain their own state.

Scoped actual Android-source compilation uses frozen dependencies from #725,
source `ca839c0555c121b248b843c4030c0e928603a535`, runtime JAR SHA-256
`cd03ed3a98c63754ad6f02324c383e548ce479a56b210e77b9ca9f8bf9fc9fb2`.
These checks execute the real AgentActivity, BoundedAgent and cockpit triggers
with a controlled LocalModelRuntime; they do not run model inference or a device.

- Baseline: 15 tests, 5 expected assertion failures after correcting the harness
  resource paths. The initial resource-path error log is retained separately.
- First implementation: 15/15 pass.
- Expanded review: 18 tests, 1 failure. Accepted ordinary text still retains a
  generic BLOCKED milestone emitted for an unverified result. This is not ready
  for promotion; the failing assertion is retained for the next repair.
- Cancellation, stale-session rejection, model failure, approval and existing
  tool-result controls pass in that expanded run.

Evidence: `evidence/cinema-outcome-20261110/`, including exact source hashes.
Remaining work: fix the ambiguous presentation milestone, test the neutral
aggregate animation, rerun focused controls, integrate with #724/#725.

PORTIERT: support source only. VERIFIZIERT: partial; one known failing regression.
GERAETEBESTAETIGT: NOT_RUN. Full Gradle build of this patch: NOT_RUN.
No main, protected #706, signing, install or release changes.

Rollback: revert this bounded support patch; no persistence migration.

