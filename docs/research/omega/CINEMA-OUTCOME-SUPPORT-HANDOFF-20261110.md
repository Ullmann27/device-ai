# Cinema outcome evidence support

Owner: current support root; integration owner: Cinema root, target PR #715
(`cinema/fluid-light-stage-20261110`). Combined dialog candidate #725 must retain
this fix after exact-source integration and focused caller checks.

## Final scoped verification — 49/49 PASS

Root personally implemented this repair. Local tested source commit:
`a54293d7b86720690836bb32bd0bd7ff47ad5f39`.
Tested source tree: `95d1ebd3fccc4970694fecd6220405878589925d`.
The subsequent evidence commit adds documentation/logs only. Remote transport
uses a different commit ID; complete Git-tree equality and the 14 source hashes
bind it to the local checked files.

The actual runLocal path now closes exactly once after the existing delivery
guards. A complete, current, blocker-free response is accepted separately from
verified task completion. DONE additionally requires matching task ID and the
existing `AgentTaskEvidence.outcomeVerified` flag. That flag represents the
existing evaluator's acceptance of fresh outcomes, not independent truth.

The local Chief presentation ignores the ambiguous intermediate BLOCKED state;
BoundedAgent emits that state even for ordinary, valid, unverified text. The
final checked result supplies the terminal presentation. Core events, traces,
tool approvals, permissions, cancellation and model authority remain intact.

Unfinished work becomes neutral UNVERIFIED after accepted delivery. Known tool
DONE/FAILED results remain intact. A historical WAITING approval milestone has
no recorded decision in this timeline: accepted delivery leaves it UNVERIFIED;
failed delivery leaves it BLOCKED. Neither case fabricates an approval receipt.

All affected presentations handle UNVERIFIED. The sequence aggregate preserves
that state even when a later tool is DONE, stops its spinner and shows a neutral
marker. Changing an existing detail from DONE to UNVERIFIED removes its old
success mark and requests a new frame. The existing rule keeping an active
shared scene alive across trimmed tool timelines is preserved.

### Evidence and boundaries

- Final scoped Kotlin compilation: exit 0, all changed production classes.
- Final JUnit: **49 tests, 0 failures**, four suites: 16 trigger tests, four
  actual Activity/runLocal cases, two sequence outcome UI cases, 27 existing
  sequence lifecycle controls. Exact manifests/logs: `evidence/cinema-outcome-20261110/final/`.
- Actual Activity cases: normal complete response, model failure, replaced
  session, cancellation. One TurnEnded and an ended lifecycle are asserted.
- Sequence UI probes inspect real renderer output, labels, spinner and success
  colors; the attached settled view must also request its next frame.
- Independent dialogue and visual review identified the ambiguous BLOCKED
  milestone, retained approval line and missing invalidation. Each has a
  failing regression before repair and a passing final run.
- Baseline 15/5 failures; first patch 15/0; expanded review 18/1; animation
  regression 47/2; first animation repair 47/0; final review 49/3; final 49/0.
  Original compiler/test logs and the initial resource-path error remain saved.
- `scoped-run.py` records the actual fixed-environment runner. The final run
  compiles the current sources ahead of frozen #725 dependencies; this is not
  a full Gradle build of the root branch. Dependencies and resources are
  fingerprinted/bound as described below and are not uploaded as binaries.

PORTIERT: verified support source for #726. VERIFIZIERT: the stated host scope.
GERAETEBESTAETIGT: NOT_RUN. Combined #724/#725/#726 build: pending the integration
owner's exact-source run. APK, installation, ASR, network/model inference,
signing and main/release promotion are outside this repair's evidence.

## Initial checkpoint — historical WIP

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

## Implementation checkpoint — historical WIP

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
Remaining work at that checkpoint was the ambiguous presentation milestone,
neutral aggregate animation, focused controls and integration with #724/#725.

PORTIERT: support source only. VERIFIZIERT: partial; one known failing regression.
GERAETEBESTAETIGT: NOT_RUN. Full Gradle build of this patch: NOT_RUN.
No main, protected #706, signing, install or release changes.

Rollback: revert this bounded support patch; no persistence migration.

