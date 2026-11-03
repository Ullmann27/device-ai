# Fold7 Shizuku and composer repair plan

> For agentic workers: use superpowers:executing-plans for inline implementation.

Goal: restore usable Shizuku connection feedback and a multiline chat composer, preserving PR363's local-intent repairs.
Architecture: repair the existing Activity, bridge and native controls in place; use the existing ToolGate, model host and memory stores.
Tech stack: Kotlin, Android Views, Shizuku 13.1.5, Node regression tests.
Spec: owner screenshots and malfunction report, 2026-11-03.

## Constraints
- Base PR363 at 7dacf0fb8f21e0dec9957fec23d8edb7feea3e77; isolated official repair branch.
- EXP-00/05/08/12 compatibility: no new tool/memory architecture or learning promotion.
- No main merge, owner signing, installation, data deletion or GitHub Actions.
- Host checks and real Fold7 acceptance must be reported separately.

## Review focus
- Missing Shizuku manager, stopped binder, denied permission: visible actionable state.
- Repeated connect taps, delayed callbacks and Activity disposal: bounded binding without stale UI.
- Long drafts, text selection, scroll and keyboard: editor remains usable.
- Fold resize and navigation: draft and selection retained.
- Greeting/capability/storage questions: existing PR363 routes retained.

## Task 1: Shizuku
- [ ] Reproduce menu dismissal and inspect bridge/service lifecycle.
- [ ] Write failing lifecycle/UI regression checks.
- [ ] Keep connection status visible; offer manager launch; prevent repeated pending binds; bound missing callback.
- [ ] Run targeted checks and commit a reviewable checkpoint.

## Task 2: Composer
- [ ] Reproduce focus/foreground and viewport constraints from native view code.
- [ ] Write failing widget/regression checks for long drafts and scrolling.
- [ ] Remove editor foreground overlay; allocate multiline space and preserve native text selection.
- [ ] Run targeted checks and checkpoint.

## Task 3: Acceptance
- [ ] Run real Android compile/JUnit/lint if toolchain can be provisioned, plus affected Node checks.
- [ ] Review final diff; record exact-source results and remaining device limitations.
- [ ] Update draft PR and handoff; do not claim an installed fix.


## Implementation checkpoint — 2026-11-03

Prepared on this existing draft repair branch:
- `ShizukuBridge`: one pending user-service bind, one pending permission request, 15-second timeout, per-attempt connection identity, and revoked shell ownership on stop/death/disconnect/null binding. Late callbacks cannot make an old session READY. The last status is available to the UI. Shizuku must still be installed and started by Android; no shell bypass is introduced.
- `AgentActivity`: a visible status/action dialog with a direct Shizuku manager launcher; persistent system status includes the exact bridge diagnostic instead of only `nicht verbunden`. It never performs an unapproved shell command.
- `ContinuumControls`: native `EditText` no longer draws a foreground above the caret and scrolled content. The actual composer now supports 7 displayed lines with vertical scroll and native selection.
- Robolectric lifecycle regressions added for repeated binds, timeout and stale callback (existing), as well as binding death/retry and actionable status (new).
- Existing Neuron tutor and irrelevant-memory fixes are retained.

**Evidence boundary:** This checkpoint is a source-code change. Full Android SDK/Gradle lint/JUnit and a physical Fold7 Shizuku permission/bind test have NOT been executed within this GitHub-only environment. Do not claim the release/device bug is solved until the device verifies that the manager runs, permission is granted, the shell binder becomes READY, and a consented read-only device command succeeds.

Next: execute full Android QA on this exact commit, run UI tests, check any Robolectric Shizuku shadow behavior, and perform a real device test. Only after GREEN and owner acceptance prepare a separately versioned, originally signed APK through the pinned update channel; leave `main`, current APK 0.3.37, and user data unchanged.

## Model-proof shortcut

The existing `runLocalSmokeTest(direct = true)` is now exposed in the model center as **GEMMA-DIREKTTEST · OFFLINE**. It switches to the chat display and invokes the unchanged tool-free local runtime; it does not reinterpret a READY UI badge as successful inference. An actual nonempty response and elapsed time (or a concrete failure) are shown. The test uses the currently loaded local model only, without Web, memory retriever or an external API. No Fold7 native generation has been executed in this GitHub-only checkpoint.


## Additional owner recovery fix (2026-11-03, after 83c1a6d2)

Review of the concrete Shizuku state machine identified two still-open P0 failure modes:

1. If `Shizuku.requestPermission(4107)` never produces a permission callback, `permissionPending` stays true indefinitely. The owner can tap **Verbinden** repeatedly but no further request is sent. The bridge now has a 20-second permission deadline, cancels it on grant/denial/stop/server death and allows a deliberate retry after timeout. An already granted permission discovered by refresh bypasses an old pending callback.
2. If a previously bound shell's Binder dies without the expected Android service-disconnect callback, `activeConnection` stays non-null after its 15-second bind timer was cancelled. Every future retry reported "Verbinde" without binding. The bridge now invalidates that stale session, unbinds its old connection and tries again. Also, a live old shell proxy cannot claim READY while the Shizuku server is no longer running.

Three additional Robolectric source tests cover silent permission timeout/retry, permission granted despite missing permission callback, and server death without binder-death callback followed by safe rebind. Existing timeout/reentrancy/late callback/permission-dedup cases remain.

**Verification boundary:** This is a source and regression-test patch, not a physical Fold7 test. The reviewed exact Kotlin files must still be compiled and the complete Robolectric suite run with the project's Android SDK36/JDK17/Gradle8.11.1 toolchain; no GitHub Actions run/status was available for the previous commit. Refuse publication or main merge until this exact head has GREEN QA and owner-device evidence. Do not loosen tool authorization, request shell effects or uninstall code38.


## Executable local QA gate — same research branch

The new `scripts/continuum-fold7-repair-qa.sh` provides a **noninstallable, unsigned, source-pinned** local QA path on a computer with Android SDK36, JDK17, Node, Python and Gradle. It runs with:

- `-PcontinuumLocalStandaloneQa=true`, `-PcontinuumUiWidgetTests=true`, and `-PcontinuumStandaloneVersionCode=39`; Gradle's existing QA guard expressly prohibits assemble/install with this configuration.
- `:app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` using `--rerun-tasks`.
- Strict parsing of **all** JUnit XML reports and minimum executed-case counts for Shizuku's 11 Robolectric lifecycle cases, `ContinuumControlsTest`, `OwnerChatIntentRouterTest` and `HybridMemoryRetrieverTest`. Malformed, stale, failed, absent or skipped mandatory suites block the gate.
- Focused Node UI routing, Gemma-picker lifecycle and coding-bridge tests.
- Independent output logs/status outside the checkout, including a signed-APK/device-test **NOT_RUN** declaration and a source cleanliness check.

Usage on the exact pushed branch: `CONTINUUM_FOLD7_QA_SHA="$(git rev-parse HEAD)" bash scripts/continuum-fold7-repair-qa.sh`. For an intentionally offline run add `--offline`. The QA runner never reads an owner signer, signs, publishes, installs, merges branches or changes app data.

**Special compatibility boundary:** `LocalModelReloadLifecycleTest` has an explicit JDK21-only prerequisite because the host LiteRT 0.18 classes have Java 21 bytecode. The JDK17 Android QA run must **not** label the intentionally skipped JDK21 cases as successful. Their unverified status remains recorded; a separately configured JDK21 host experiment is required. None of this proves actual on-device generation.

**Status at authoring time: SCRIPT WRITTEN / NOT EXECUTED.** This remote GitHub editing environment has not run the new Android/Node gate. No green build, physical Shizuku connection or signed code39 APK is claimed. Next step is execution by the existing isolated Codex build environment, RED->GREEN repairs based on raw output, then owner Fold7 acceptance.
