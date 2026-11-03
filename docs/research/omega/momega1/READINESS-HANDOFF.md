# Voice, camera and connector readiness

Status: **SOURCE_AUDIT / IMPLEMENTATION_PENDING / FOLD7_NOT_RUN**.

Baseline `d8a927dd6ced4dea489e2042afb4b5624157fa3b`, branch
`codex/momega1-readiness-20261103`. This lane preserves the existing design and
the existing agent, tool and owner-approval architecture. EXP-05, EXP-08 and
EXP-11 are affected by integration, without promoting their device status.

## Observed call paths

- `AgentActivity` creates Android TextToSpeech, selects an installed offline German
  voice, applies `JarvisVoiceProfile`, and connects actual synthesis callbacks to
  `TtsHologramSignal` and `AvatarDirector`. Voice preference is initially off.
  Available TTS is not proof that an owner heard a successful test.
- `startVoiceInput` uses `AndroidOnDeviceSpeechInput`, explicit microphone consent,
  Android's on-device recognizer, a 30-second deadline and a preview in the existing
  composer. It does not automatically submit speech as an authorized command.
- No camera permission, Camera2/CameraX capture adapter or observed face-to-gaze
  path exists at baseline. Avatar gaze is procedural state presentation.
- `AndroidDeviceAppActions` can launch an explicitly named installed package and
  read an authorized active screen through existing ToolGate/accessibility paths.
  That is not a Gmail, Facebook, TikTok or Google account API connection.
- No production OAuth client, redirect handler, token store or account API adapter
  for those services was found in the app/backend. Connected ChatGPT tools do not
  grant account access to the Android application.

## Authorized narrow implementation

Chief allocated only these production paths to this lane:

- New pure `cognitive/ContinuumReadinessGuide.kt` and focused tests: derive honest
  spoken/visible prompts from current capabilities; no network calls, permission
  grants, fake PASS records, new cognitive scheduler or owner-fact mutations.
- New native foreground Camera2 adapter and pure policy: advertised hardware
  `STATISTICS_FACE_DETECT_MODE_SIMPLE` only, explicit owner opt-in and runtime
  CAMERA permission, one confident face, normalized center/gaze, bounded rate and
  smoothing, no recognition of identity/emotion, no retained or uploaded images.
- Add CAMERA permission to the existing manifest only. This is not a runtime grant
  or authorization to install an APK.

`AgentActivity.kt` remains with the coding-draft integrator until Chief performs
sequential integration. `AvatarDirector` and renderer source belong to the visual
lane. No local model, ToolGate, signing or owner-memory source is modified here.

The camera must release native resources on cancellation, background transition,
permission withdrawal, error or missing frame heartbeat. Late callbacks from a
previous generation must not reactivate capture. Unsupported hardware is reported
unavailable. Host contract tests cannot establish actual camera tracking or motion
quality on a Fold7.

## Connector prerequisites

Google account authentication and Gmail authorization are separate. A client
registration matching the package/signing identity, consent configuration and
specific required Gmail scopes are missing. Public distribution can require
provider verification. Facebook and TikTok similarly need registered apps,
selected scopes and verified redirect/platform settings. A development/sandbox
environment is distinct from public availability and cannot be described as a
working account connection without an actual authorized call.

No credentials are requested, stored or transmitted in this audit. No account
login, email read, social post or external service test has been performed.

## Validation and recovery

RED tests and the final implementation SHA will be recorded before acceptance.
Current tests: **NOT_RUN**. Camera/voice device and account endpoint tests:
**NOT_RUN**. The user's confirmed missing device access remains authoritative.

Rollback removes this research increment while preserving owner data and existing
revocations. Disabled/unsupported camera means no capture, not a hidden alternate
vision provider. Missing connectors remain unavailable, not a web or screen
automation fallback masquerading as account authorization.
