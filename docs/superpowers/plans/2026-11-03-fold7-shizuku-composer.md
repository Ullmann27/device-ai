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
