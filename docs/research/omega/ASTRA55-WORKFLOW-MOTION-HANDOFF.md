# Astra55: real computer workflow and motion

Canonical integration: `work/astra55-cockpit-integration-20261112`, PR #799,
parent Code55 PR #791. Owner: Astra root. This branch supplies the bounded
per-turn workflow and the adapter to the existing cockpit bus.

## Source

- `OwnerComputerIntentParser`: explicit opening, search, downloads and WhatsApp
  drafts. Retains the exact draft; does not turn an unsupported tail into an
  opening command. SEND remains a separate approval step.
- `OwnerComputerWorkflow`: the real approved app is already open when invoked.
  Search-button click, query entry, fresh observation, contact selection and
  draft entry use the bound Android port. Same-name contacts require owner
  selection. At most three actual contact-list scrolls. No repeating uncertain
  input. Existing different or unobservable drafts are not overwritten.
- Downloads require a new heading for the selected folder. A merely changed
  tree does not prove opening the right directory.
- `CockpitWorkEvent` extends the existing `CockpitAnimationEvent.Extension`.
  It carries task, capture session, sequence, monotonic occurrence, actual
  dispatch, observation and verification. No contact/message text enters it.
- `LiveActionCockpitAdapter` binds the separate accessibility grant epoch;
  capture session and accessibility epoch are deliberately different IDs.
- `ComputerWorkAvatarBridge` uses existing AvatarDirector and pose. Each real
  click, text-set or scroll produces one short bounded movement; no typing
  loop and no decorative input while waiting. Stop/pause/question/error clears
  work movement and releases gaze.
- `ComputerWorkCuePlayer` reuses existing `ContinuumUiFeedback` PCM and system/
  foreground/speech policy. Queued obsolete sounds are discarded. Bulk text
  set produces one input cue, not fabricated key-by-key audio.

## Integration API

1. Parse explicit owner text. Keep existing app discovery and `launch_app`
   ToolGate. Missing or unsupported steps are returned as an owner-readable
   result, never silently discarded.
2. Start the real Android live source after owner consent. Read `sessionId`
   and `observationEpoch` separately.
3. Create `CockpitWorkEmitter(taskId, sessionId, cockpitBus)` and bind the
   subscribed `ComputerWorkAvatarBridge` to that task/session.
4. On the worker call `OwnerComputerWorkflow(taskId, packageName,
   observationEpoch, emitter).run(intent, port)`.
5. Port delegates `observe`, `perform` and `click` to `AndroidLiveComputer`.
   Every native input must pass the actual scoped owner grant / ToolGate.
   Neither a parsed instruction nor a model boolean is authorization.
6. `Port.click` receives `expectedEditable` and `expectedHeading`; pass these
   to `ClickRequest`. This preserves absent-before / present-after verification
   even where search-button and search-field labels are identical.
7. Stop the live source and cancel the workflow on revocation, owner Stop or
   lifecycle loss. Close the bridge on Activity destruction.

## Verification boundary

At this checkpoint: PORTIERT / host tests NOT_RUN / Fold7 NOT_RUN.
Focused suites: `OwnerComputerIntentParserTest`, `OwnerComputerWorkflowTest`,
`CockpitWorkEventTest`, `AvatarDirectorTest`, `AvatarContractTest` (if present).
Run Gradle with the shared `agent-gradle.lock`, Java17 and the verified local
SDK. Android UI labels and heading exposure vary by app/version; missing proof
pauses rather than claiming success. No real account messages were sent.

No Main merge, signature, model, package or original avatar/sphere asset change.
Relevant compatibility: EXP-08 task/permission binding, EXP-11 existing per-turn
execution and bus, EXP-12 cancellation/session continuity. No second backlog,
renderer, database or audio engine. Rollback the workflow/motion commits while
retaining prior C foundation; default pose fields preserve renderer source
compatibility.
