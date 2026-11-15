# Kommando 03 – native Android integration

Status: IMPLEMENTED CANDIDATE / native verification in progress / no owner release

## Source and responsibility

- Integration owner: current Continuum Android implementation task.
- Current base: Code 59, `79c16e57aeb7e0cf4a851e921d7441f7f6384fdf`, published 0.3.58.
- Integration target: `fix/code59-context-complete-20261114` (PR #848).
- Work branch: `integrate/continuum-command03-android-20261115`.
- Approved visual source: PR #850, `0876aa0accdf8154e06e53da42aa5c9492892066` (Kommando 03).

## Authorized scope

Adapt the approved layout to the existing Android application for Fold, ordinary phones and resizable windows. Preserve the original avatar and native orb, existing model, speech, memory, document, research, agents and device tool paths. Use actual native state and data. Do not ship the web research simulation or invented CPU/RAM samples. Keep controls reachable with larger text and keyboard/inset changes. Preserve current package, persistence and permission gates.

Use the current AgentActivity -> ContinuumHomeView -> CinematicWorkstationLayout/Filament paths; no second app, activity launcher or model runtime. Relevant existing contracts: EXP-00/08/11 transport and orchestration compatibility; no new research promotion.

## Verification and integration

Native geometry and interaction tests, existing widget tests, Kotlin compile and source-bound APK build are required. Web-preview checks are not Android evidence. Original asset hashes must remain unchanged. No main merge or installation. The work branch targets the existing integration line and will be kept recoverable with exact commits and test evidence.

## Current checkpoint

The first implementation checkpoint contains the approved Manrope/Space Grotesk assets and their licences, native header/glass styling without the former emblem, and actual app-CPU/device-RAM instruments. The instruments retain unknown readings, preserve gaps in the measured history, and never generate a demonstration curve. Canvas and native computer-panel content share the measured source.

The native implementation is now frozen for its first compile/test pass. It includes seven existing function routes (conversation, PDF files, research, agents, memory, computer, settings), a transcript-backed chat list, guarded asynchronous conversation restore, and the single existing composer pinned outside the scrolling presentations. The responsive layout uses the actual available stage width; smaller windows stack their instrument surfaces and keep large text scrollable. The original head is centred at the larger approved scale, and the original orb's authored ring phase stays fixed while the other existing animation clocks continue.

The older Filament side-panel preset has fixed positions that do not match the responsive instrument slots. The workstation therefore uses the existing native Canvas instrument surfaces at their real touch bounds. The central Filament monitor, original head/orb and surrounding scene remain in the existing native renderer. Selecting a different stored conversation closes the old computer controller and its capture grant before presenting the new context.

The first local compile of `7d1a0a49290aa8e7aab41ed76405bd96857a3635` stopped with an unexpectedly terminated Gradle daemon during Kotlin compilation; no Kotlin source diagnostic was emitted. Resource/manifest processing completed, but this is **not** a successful compile. The cause is under investigation; memory pressure is plausible and unconfirmed. JVM/UI tests and the source-bound ARM64 build are **NOT_RUN** at this checkpoint. No device, frame-rate, signing or installation result is claimed.

A separate review found and fixed the first-measure side-panel guard: the workstation must reject the old panel preset before the shell's drawing cache exists. Its regression now uses a real accepted computer-state fixture before first measure, rather than a null computer state. Added QA also includes stored-context isolation, stale queued selection rejection, actual-task-end waiting, PDF picker permissions, and the single fixed composer. The existing native Canvas capture fixture covers 392×852 and 904×768 dp and the real computer-tool route; it is not GPU/Filament evidence.

The second local compile **passed** on `c66cf77bef76130f17f9964316515ce6d98b2eca` (Gradle success, unchanged clean source). Existing byte-identical historical APK/ZIP copies were hardlinked without removing their paths or changing their contents, freeing 417,693,696 bytes of tmpfs. The measured run used one worker, a 2048 MiB heap and 512 MiB metaspace. This is a native Kotlin compilation result, not a test-suite or device result.

The final presentation correction keeps the same Home-mounted avatar surface visible above the real chat. A transient presence layout removes the inner rail and tool hit targets, keeps the existing valid monitor frame below the clipped strip, and locks the strip at scroll zero. Its height gives the real composer priority, including the existing 126 dp / large-text regression case. The original renderer is never reparented. The existing monitor camera flight is presentation-disabled while this strip is active: a current flight returns to rest, no hidden flight starts, and returning home does not replay an old event. Native editor, messages and buttons now use the same bundled Manrope typography as the scene.

The latest exact source and verification outcomes are maintained in PR #851. These paragraphs record checkpoints and do not inherit earlier APK, GPU, device or signing approvals.

## Recovery and bounded history update

Before the cloud executor reset, `a0bc97934a4fadda6346a9336e5de4b832b46bdb` also completed `:app:compileDebugKotlin` successfully (138.05 seconds, clean unchanged source). The focused test compilation subsequently completed, but the test worker could not create its cache directory as the environment was lost: zero executed tests and no assertion result. These are historical observations; the original local run files were lost in the reset and are not current release evidence.

The checkout was recovered from exactly that GitHub commit. The new local toolchain uses the repository-pinned Temurin JDK 17.0.20.1+1, Gradle 8.11.1, SDK 36 revision 2 and build-tools 35.0.0. A fresh dependency resolution and source-bound compile/test/build are required. No device or GPU result is inherited from the previous environment.

The confirmed PR review finding about history rescans is repaired: a successful bubble append updates only that conversation in the in-memory presentation cache, on the existing serial transcript writer. An explicit opening/refresh selects at most 100 recently modified transcript files before opening their contents. Titles are read from the bounded beginning of the existing transcript, not the moving 200-message restore window. No separate database, metadata sidecar or persistence-format change is introduced. Four additional tests cover stable titles after 350 messages/restart, incremental updates, greeting-to-first-question titles and bounded initial selection. The independent source review found no blocking race or persistence regression; native execution of these new tests remains pending at this checkpoint.

The existing release files now contain a fixed, manual-only Code60/0.3.59 profile with source-SHA guards. Existing Code59 push lists and protected signing gates are retained. The adapter's 44 Python and 40 Node contract tests passed locally; these synthetic contract tests are not Android, GPU or owner-release acceptance. See [Code60 preparation](../../release/CODE60-COMMAND03-RELEASE.md). No workflow was dispatched, and no signing environment was changed.
