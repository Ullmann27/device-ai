# Natural owner actions and visible work episodes

Status: WIP / UNVERIFIED. Base: 5572edfbb02570ae479a8f0f72179f5ca33027c5 (PR578).
Branch: codex/continuum47-natural-actions-20261106; target: codex/continuum-code47-qa-candidate-20261106.

## Authorized scope
Repair natural app requests through existing installed-app lookup, local planning, explicit ToolGate approval and genuine execution receipts. Preserve complete multi-part owner requests; clarify vague or ambiguous requests. Examples are synthetic tests, not permission to send real messages. Keep preparation, recipient clarification and actual sending separate. No WhatsApp-specific route.

Connect accepted current owner work to the existing cockpit and JarvisWorkWindow, including entry from CHAT; hold the existing directed gaze until the work ends, show actual states/results, and reject stale session/generation/call callbacks after STOP. Preserve the original head, orb, materials, permissions, package and signer. No new renderer/event bus, fictitious external-app screen or invented completion percentage.

## Reproduced source defects
- OwnerAppLookupIntent treats open/start commands as lookup only. AgentActivity terminates after find_app, asks for another confirmed opening request, then captures that same wording again.
- Normal composer submission selects CHAT; presentToolScene accepts only visible START.
- Tool scenes call windowOpened (2.5-second glance), never workStarted (held reading gaze).
- Posted scene starts do not bind the existing workPresentationGeneration/session scope.
- Existing scene tests invoke show helpers directly; they do not establish input-to-real-tool wiring.

## Implementation plan
1. Add bounded natural-action regressions on genuine existing lookup/ToolGate contracts.
2. Preserve original requests and route incomplete/compound requests to existing local planning or a concrete clarification; derive no permissions from model text.
3. Add a candidate-selection/approval continuation for exact installed packages using existing executeTool/showToolConfirmation.
4. Bind the existing work window to current accepted work and real terminal results, with generation/session/call checks and STOP cleanup.
5. Compare published Code43 ff8f6070bb3a6f4dfbb6215ce3e3e269974c1d53 to Code46 577f0931e7937ffcac02dc855a540f093facdb3b for removed screens/progress widgets.

## Architecture and experiments
EXP-05: reuse LocalModelRuntime/BoundedAgentOrchestrator; EXP-06: no owner-fact promotion; EXP-07: adversarial and lifecycle regressions; EXP-11: existing ToolGate only; EXP-12: no persistent schema migration. CCC1/build worker and model weights remain untouched.

## Verification
Source audit completed on immutable Code46/47. New repair tests: NOT_RUN; central JDK/Kotlin/SDK restoration in progress. Full Gradle/lint/APK/Fold7/Filament/audio/model acceptance: NOT_RUN. No production stubs or host success presented as device evidence.

## Ownership, next step, rollback
This branch owns AgentActivity, OwnerAppLookupIntent and nearby action/work-window helpers only. Other agents own separate context/runtime cores; their Activity hooks are integrated centrally. Next: add RED cases and implement the narrow continuation. Rollback is reverting this branch's additive commits to the base; no data migration or release change.

## Parser checkpoint

- Real baseline source: 5572edf, OwnerAppLookupIntent.kt SHA256
  `145d8dbad68fb19dae0cf4366701302d7478f8d75a1d8b87c63b38db34468d4c`.
- Kotlin 2.4.0 / JDK17 / JUnit4.13.2, maximum256MiB compiler heap.
- RED: 3 new tests,2 failures. Addressed and polite requests return null.
- GREEN: same3 plus existing3 parser tests,6/6 pass.
- Compound requests retain their original text for planning. No execution permission
  derives from recognition; Activity continuation is still in progress.
- No Android/GPU/device verification has run for this checkpoint.

## Action and visual checkpoint

- Production parser/NativeAppLookup/ToolRegistry/ToolGate/receipt sources compiled
  against real compileSdk36 android.jar: 18/18 targeted JUnit tests PASS.
- Exact selected package receives a new explicit confirmation. Lookup alone,
  declined approval, STOP, missing/ambiguous app, stale call and replay cannot
  acquire launch authority. Missing permission cannot become verified success.
- Whole compound requests remain in owner context. Message preparation asks for
  recipient/content and states the current lack of a connected foreign-app
  typing/sending tool. No synthetic example sends a real message.
- Activity now uses this flow; launch results wait for foreground return when
  opening another app pauses Continuum. Real confirmation remains ToolGate-owned.
- Tool scenes capture existing session/scope/generation/callId, move accepted work
  from CHAT to the existing START surface, hold workStarted until backend return,
  and return to CHAT after the result hold. STOP invalidates queued starts.
- Existing center dialog presentation is the fallback if protected geometry has
  no safe work-window rectangle; no new renderer or embedded external app.
- Activity changes and new real-input UI regressions: SDK compilation/UI runtime
  still pending central integration. The 18 pure tests are NOT Activity E2E.

## Code43 to Code46 source comparison

- Code43 source ff8f6070bb3a6f4dfbb6215ce3e3e269974c1d53 is identified by
  docs/research/omega/OPUS55_HANDOFF_VOICE_AVATAR_HUD_20261105.md lines4–5 and
  docs/release/CONTINUUM-CODE44-BUILD-HANDOFF-20261105.md line8.
- Compared to Code46 577f0931e7937ffcac02dc855a540f093facdb3b, Activity's
  showCenter replaces its exclusive AlertDialog with JarvisWorkWindow when
  eligible and retains AlertDialog as fallback. WorkWindow/Host/Depth/Geometry
  are additions; this is not evidence that a previous working virtual app screen
  was removed.
- No removed progress/loading/work-surface line appears in the reviewed visual
  UI/ContinuumHologram diffs; no Activity ProgressBar removal appears. The existing
  neuralProgress visibility remains conditional on actual training.
- Source-proven causes for missing tool work scenes are CHAT admission, the
  START/geometry gates, lookup-only termination and the missing workStarted call.
  This source comparison cannot establish what a physical Code43 screen looked
  like; no Code43/Fold7/video comparison was performed.

## Integration handoff

- Root's same-millisecond old/current/old scope holdout reproduced as actual
  JUnit RED (8 tests/1 failure), then fixed with historyScope independent of
  active; GREEN8/8. Retired call ids survive close/new-start within one scope.
- Final targeted combined suite:30 tests/0 failures. Real production sources,
  real SDK36 android.jar and stable SDK-compiled AvatarDirector/AvatarPoseReducer
  from Root's24547da baseline; compiler256MiB, JUnit128MiB. No production stubs.
- One-character installed labels (synthetic X) now pass all three lookup gates;
  blank/control input still fails and exact-package launch still needs approval.
- Indeterminate Android ProgressBar covers the real backend interval only.
  End/STOP/close remove its running state. No percentage was introduced.
- Only tool work-window gaze is emphasized (x1.35/y1.25), still capped by existing
  AvatarPoseReducer limits .65/.35; layout refits retain it. Documents keep their
  existing default and all renderer/head/orb/material files are unchanged.
- Bot3's exact Activity patch was applied; Bot2's finalized six-section contract
  was integrated, including complete metadata restore, one writer callback,
  memory-read retention, current assistant identity, actual history budget,
  verified frame deduplication and accepted partial retention. Private bot/coding
  paths do not read owner history while constructing followUpFrame.
- Activity now requires the matching core files from PR580 and PR581; PR579 alone
  is not claimed to compile without that integration.
- Four real-Activity tests submit actual composer text/Send, traverse Android
  app lookup and explicit confirmation into the real ToolGate/native adapter
  boundary, and cover CHAT/START, a3.2s held gaze, progress end, queued STOP,
  background pause/result return, missing app and missing permission. Device
  boundary is controlled test input; no real app or message is sent.
- Those UI tests are NOT_RUN: Robolectric artifacts are not yet present in the
  restored toolchain. Full combined SDK compilation, UI tests, APK and physical
  Fold7/Filament/model checks remain Root's central validation gates.
