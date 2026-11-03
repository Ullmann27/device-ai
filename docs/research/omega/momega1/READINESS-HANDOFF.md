# Voice, camera and connector readiness

Status: **AUTHOR_HOST_CONTRACTS_PASS / INDEPENDENT_REVIEW_PENDING /
ACTIVITY_HOOK_PENDING / FOLD7_NOT_RUN**.

Baseline `d8a927dd6ced4dea489e2042afb4b5624157fa3b`; source freeze
`d32fe38ad2f6464bfaefa8001bfed1f2469165e7`, branch
`codex/momega1-readiness-20261103`, draft [PR436](https://github.com/Ullmann27/device-ai/pull/436).
This lane preserves the existing design, agent, ToolGate and owner-approval paths.
EXP-05, EXP-08 and EXP-11 are affected, without promoting their device status.

## Actual baseline paths

- `AgentActivity` creates Android TextToSpeech, selects an installed offline German
  voice, applies `JarvisVoiceProfile`, and connects synthesis callbacks to
  `TtsHologramSignal` and `AvatarDirector`. The existing voice preference initially
  is off. Available TTS does not prove that the owner heard a successful test.
- `startVoiceInput` uses `AndroidOnDeviceSpeechInput`, explicit microphone consent,
  Android's on-device recognizer, a 30-second deadline and preview in the composer.
  Recognized text does not automatically submit or authorize a command.
- No camera permission, Camera2/CameraX adapter or observed face-to-gaze path exists
  at baseline. Existing avatar gaze is procedural state presentation.
- `AndroidDeviceAppActions` launches explicitly named installed packages and reads
  approved screens through existing accessibility/ToolGate paths. Neither is an
  account API connection. No production Google/Gmail/Meta/TikTok OAuth client,
  redirect, token store or API adapter was found. ChatGPT connections cannot grant
  account access to this Android app.

## Candidate source and boundaries

`ContinuumReadinessGuide` is a pure presentation helper. It derives visible/spoken
prompts from current observations. It cannot request permission, launch a tool,
activate a connector, write owner facts or certify an acceptance test. Its statuses
are UNKNOWN, NEEDS_SETUP, AVAILABLE, UNAVAILABLE and NOT_IMPLEMENTED; there is no
PASS status. Owner voice preferences remain untouched.

`AndroidAvatarCameraInput` uses only the front camera's advertised native SIMPLE
face-position metadata and REALTIME sensor timestamps. It requires all of explicit
owner opt-in, foreground state, Android CAMERA permission and supported hardware.
It emits one confident face center, never identity, emotion or measured eye gaze.
Multiple faces clear the observation instead of selecting a supposed owner.

Capture uses a supported YUV buffer of at most 640×480, chosen nearest 320×240,
a reported target FPS range of at most 30, chosen nearest 15, and two native image
buffers. `acquireLatestImage()` immediately closes each buffer without reading any
pixel. No image conversion, file, network path or downloaded detector is present.
The renderer receives only bounded x/y, confidence and elapsed capture time.

The policy rejects confidence below 60, invalid bounds, unknown clock domains,
missing/future capture times and capture age greater than 750 ms. An old queued
callback cannot renew a capture's timestamp or keep the five-second valid-frame
heartbeat alive. Time-based smoothing has a 50 ms motion interval and maximum
coordinate delta of two normalized units/second. Display/sensor rotation and front
mirroring are deterministic host contracts, not a verified physical Fold7 mapping.

Cancellation, pause, permission loss, error and missing valid frames close the
session/device/reader/worker. One process-level lease prevents separate adapters
from acquiring concurrent sessions. Generation checks reject callbacks from an old
run. Resource-creating callbacks remain on the process main looper: stopping the
image worker while open/configuration is pending cannot discard a late device or
session handle; a stale callback closes that handle. Native hardware cleanup and
vendor callback behavior still require real-device validation.

The manifest adds CAMERA permission plus `android.hardware.camera` and
`android.hardware.camera.autofocus` with `required=false`, so this optional feature
does not imply mandatory rear-camera/autofocus hardware. No component export,
package, signing, installation, avatar rendering, ToolGate, model or owner-memory
code changes occur here.

## Chief integration API

`AgentActivity.kt` remains allocated to the Android coding integrator until Chief
performs the sequential hook. This candidate alone does not show the guide or
start a camera in the installed app. The visual lane owns AvatarDirector/renderers.

Construct `AndroidAvatarCameraInput(context, onObservation, onClear, onState)` in
the Activity's lifecycle. Its control methods must run on the main thread:

- `start(ownerOptIn, isForeground, displayRotationDegrees): Boolean`; degrees must
  be 0/90/180/270, derived from the current display. True means asynchronous start
  accepted, not successful capture. Observe `state`/`onState` for actual status.
- `setForeground(false)` on pause/background and `stop()` on cancellation/destroy.
  Do not auto-resume on return, recreation or permission callback. A fresh explicit
  owner action starts a new foreground session. Recreate orientation mapping by
  stopping and requesting a fresh test; do not guess a Fold7 camera switch.
- `hardwareSupported()` only probes advertised metadata/clock/format/rate support.
  `permissionGranted()` reads Android permission. Neither opens a camera.
- All callbacks arrive on the main thread. Forward a sample to the visual lane's
  `AvatarDirector.onObservedGaze(GazeTarget(sample.x, sample.y), sample.confidence,
  SystemClock.elapsedRealtime() - sample.observedAtElapsedMillis)`. Forward
  `onClear` to `clearObservedGaze()`. Never mix the elapsed sensor clock with the
  Director's existing wall clock. Director remains authoritative for task priority.

Build `ContinuumReadinessGuide.Snapshot` from these actual current sources:

| Field | Required binding |
|---|---|
| `offlineVoiceAvailable` | null until TTS initialization; then an installed German offline voice actually selected |
| `voiceEnabled` | Existing owner voice preference; the guide must not silently enable it |
| `onDeviceSpeechAvailable` | Existing Android on-device recognizer capability check |
| `microphoneGranted` | Actual current RECORD_AUDIO permission |
| `localModelIdentityBound` | Current loaded model and verified binding, not installed filename or a stale READY label |
| `cameraHardwareSupported` | Camera adapter capability probe; never simulated hardware |
| `cameraPermissionGranted` | Current Android CAMERA permission |
| `cameraOwnerEnabled` | Current explicit foreground-session test choice, reset on pause/stop/error |
| `cameraState` | Adapter state, not an inferred cosmetic animation state |

Guide actions are requests to the existing UI, not grants:

| Action | Intended existing UI behavior |
|---|---|
| `OPEN_MODELS` | Existing model import/load screen |
| `ENABLE_VOICE` | Existing explicit owner voice toggle; refresh guide |
| `PLAY_VOICE_SAMPLE` | Existing offline TTS path with `VOICE_SAMPLE`; owner must confirm hearing |
| `SHOW_VOICE_HELP` / `SHOW_SPEECH_HELP` | Explain missing local component; no network fallback |
| `REQUEST_MICROPHONE` | Existing runtime permission flow; no recording solely because permission returns |
| `START_SPEECH_TEST` | Existing on-device speech preview, ask for `SPEECH_SAMPLE`; no auto-submit |
| `REQUEST_CAMERA` | Runtime CAMERA permission only; fresh `ENABLE_CAMERA_TEST` action still required |
| `ENABLE_CAMERA_TEST` | Explicit current-session opt-in, then adapter start with foreground/display checks |
| `STOP_CAMERA` | Stop and clear session opt-in immediately |
| `SHOW_CONNECTOR_INFO` | Show honest prerequisite catalog, not OAuth success or a fabricated connection |
| `NONE` | No action |

`matchesSpeechSample` compares the entire bounded normalized phrase, never a
substring or command. Even a match is a narrow recognition sample, not proof of
model reasoning or a spoken tool authorization. No transcript persistence is added.

## Evidence and repeat commands

The initial 25 frozen contracts at `50c099b67cab32137fa3f7cbaaf0b1519bf7ac95`
failed to compile against unchanged baseline production because the required APIs
were absent: `baseline-contract-red.log`, exit 1. This is explicitly an API/compile
RED, not 25 executed failing JUnit tests or a real camera fault.

At exact source `d32fe38ad2f6464bfaefa8001bfed1f2469165e7`:

| Check | Result |
|---|---|
| Kotlin 2.4 / JVM17 native Camera2 SDK + test compile | PASS, exit 0 |
| Pure camera policy/readiness contracts | 28 PASS, 0 FAIL, 0 SKIP |
| Actual adapter under Robolectric Android API28, no camera hardware | 8 PASS, 0 FAIL, 0 SKIP |
| Executed-identity evidence rerun | 36 PASS, 0 FAIL, 0 IGNORE, 0 assumption skips |
| Independent review | PENDING |
| Chief Activity/UI hook and full combined regression/lint/build | NOT_RUN for this source |
| Physical camera, audible voice, on-device speech, account API | NOT_RUN |
| Fold7 | FOLD7_NOT_RUN |

[RESULT.json](../../../evidence/momega1/readiness/d32fe38a/RESULT.json) binds source
hashes and compiled jar SHA. [COMMANDS.txt](../../../evidence/momega1/readiness/d32fe38a/COMMANDS.txt)
contains the actual host commands. Raw compile/JUnit/identity logs, test dependency
hashes and the identity runner are adjacent. The direct compile uses cached SDK and
Robolectric dependencies only, excluding other worktrees' compiled app/test output.
All executions used one CPU and 512 MiB heap in QA-approved non-model windows.
The Robolectric tests cover actual adapter admission, denied/absent hardware,
foreground handling, idempotent cancellation and main-thread ownership. They do
not emulate successful camera capture, prove native resource release on hardware,
or measure human-looking movement. The 36 cases are author tests, not independent
acceptance or a substitute for the final integrated Android suite.

## Connector prerequisites and recovery

[CONNECTOR-READINESS.md](CONNECTOR-READINESS.md) inventories Google/Gmail/Drive/
Calendar, Facebook and TikTok and records current official documentation. Real app
registrations, package/signing-bound clients, narrowly scoped owner authorization,
private token lifecycle, reviewed adapters and actual endpoint tests are absent.
No credential was requested, stored or transmitted. No account login, mailbox
read, post or external private-data request was performed.

Rollback omits this research increment before UI integration; after integration,
remove Chief's hook first and stop capture before reverting the adapter. No owner
data or revocation store needs migration. Unsupported camera stays unavailable;
missing connectors stay disconnected. No old authorization or uncertain tool run
is automatically replayed during recovery.
