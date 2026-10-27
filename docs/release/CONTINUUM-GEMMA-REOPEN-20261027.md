# Gemma 4 E4B on-device reopen regression – owner screenshot 2026-10-27

**Owner evidence (actual previously distributed Android APK):** in the MODELL center, `gemma4-e4b-it`, version `official-0b2a8980`, SHA-256 `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`, size `3659530240` bytes is reported **loaded**. When the owner exits and opens the app again it can be **unloaded**. The owner's installed APK is explicitly the **last distributed APK**, not the newer developer source. This screenshot does not prove real text inference, benchmark scores or which exact Android process lifecycle occurred.

## Actual root cause in checked source

`AgentActivity.onDestroy()` formerly calls `localRuntime.unload()` on ordinary activity finish (but not a configuration change), losing the already initialized LiteRT-LM engine even while its process-wide `LocalModelHost` remains alive. If Android kills the process, the in-memory engine inevitably disappears. `onCreate()` reopens the model catalog but originally did **not** automatically reload previously imported original Gemma. The catalog itself persists under the app's internal `filesDir/local-models`. It verifies the exact file SHA-256 via `catalog.get` before native Engine construction.

## Implemented correction – existing main APK only

- Added pure `ContinuumGemmaRestorePolicy` that selects ONLY the pinned selected original Gemma ID, version and SHA, when the owner has not explicitly disabled restoration and the runtime is `EMPTY` with no active engine. No silently substituted Qwen or similarly named file.
- `onCreate` performs ONE asynchronous local-only catalog lookup via the existing `onLearningIo` executor, then loads the already present file with the existing native `loadModel`; no downloads, external AI APIs, repeated polling, timers or foreground services.
- Existing process-wide loaded Gemma is retained on Activity finish to allow quick reopen, while Android remains free to reclaim the whole background process. A new process auto-restores from the local file. No guarantee of staying resident after memory pressure or force-stop; loaded state on return is earned only after real native initialization.
- Successful original-Gemma load sets a private owner preference enabling future reopening. Explicit `MODELL ENTLADEN` clears it before unloading; loading another model clears it; a failed original Gemma load clears it to avoid endless cold-start retries. Upgrading a legacy APK defaults to restoring an imported original Gemma, consistent with this owner's explicit request to keep it loaded; the user can opt out by manually unloading.
- The native `LocalModelRuntime` itself, SHA verification, conversations, model data and weights are unchanged; the existing M2 direct inference test still requires a new actual completed response. No fake app-ready, offline, model-improved or repair success claim.

## Independent checks and hard release boundary

- Kotlin command-line smoke with a matching pure model-selection policy and minimal stubs: **11/11 passed** (not a full Android compile).
- GitHub exact-source structural/lifecycle assertions: **18/18 passed** against the newly committed Activity, policy, catalog, runtime and tests.
- Added actual Kotlin JUnit policy regression tests and a Node lifecycle/source contract for the full repository. Full JUnit/Gradle/Android build of this NEW source **NOT RUN**, because the Render workspace previously exhausted build pipeline minutes.
- Do not publish an APK without the ordinary independent full build, owner signing approval and on-device Fold7 regression tests. Preserve `at.heinz.deviceai.mentorpaired`, owner signing certificate and installed local model/data on update. The old APK cannot gain this fix without installation of a new owner-signed update.
- Do not change Astra PR189, `main`, owner memories, learner weights or production signing configuration.

## Future Fold7 validation

1. On new same-package owner-signed main APK, confirm the existing imported original model appears.
2. Wait for native ready state; run one *real* direct `Meilenstein Modelltest`, observe nonblank answer and exact model identity.
3. Switch to another app and back without force-stop. Confirm the engine remains loaded if the process survives.
4. Swipe the app away / close Activity and reopen. Confirm the model is restored from local disk if needed; preserve imported metadata and original bytes.
5. Tap `MODELL ENTLADEN`, close/reopen. Confirm opt-out persists.
6. A tampered or deleted model may not be auto-loaded; diagnostic states failure without retry loops. Verify no new download, network request or unapproved tool call.

**Verification status: source-level correction committed, production Android runtime acceptance NOT RUN.**
