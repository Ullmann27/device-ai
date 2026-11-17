# Connected spoken setup and camera guide

Accepted host/callback source: `6c2a30955fee800d6ddeffde9206158ea43537e9`.
Author [PR447](https://github.com/Ullmann27/device-ai/pull/447); independent
[PR445](https://github.com/Ullmann27/device-ai/pull/445), published reviewer
`f9c0b94cffb83a78bf9e3246b994815a316a7fdc` (same tree as local `29de6a31`).
No physical voice/camera, full-onCreate, reference voice identity or Fold7 claim.

## Actual connection

AgentActivity first foreground open shows the compact existing-style guide;
System → STIMME, BLICK & STARTBEGLEITUNG reopens it. The panel leaves the existing
avatar visible. Existing Android TTS, AndroidOnDeviceSpeechInput and the reviewed
AndroidAvatarCameraInput are its actual ports. No second agent/state machine.

An unconfigured voice preference now defaults on, as requested for first open.
An explicitly stored false always wins, and reading the default writes nothing.
Only an available German voice with neither networkRequired nor notInstalled is
selected. The one-shot initialization callback and visible first-guide scope
coordinate the welcome regardless of callback order. Pause/dismiss revoke pending
welcome; the displayed-introduction preference prevents recreation replay. A
regular later opening greeting is separately captured before TTS construction;
it cannot rescue an expired first welcome. No input sensor starts with speech.

A hearing confirmation is shown only after the actual matching TTS onStart. Its
button binds the current guide instance and sample ID, rechecks live foreground
state, consumes once and rejects stale/error/replaced/dismissed clicks. Successful
onDone may precede the owner's confirmation. This is a self-reported hearing test,
not a verifier PASS or owner-memory fact.

The dedicated speech test compares the bounded recognized phrase, shows the
actual result in RAM, and never changes/submits the chat composer. Pause/dismiss
clear the transcript. Microphone/camera permission callbacks only refresh status;
a fresh button action is needed to start. No cloud speech fallback or download.

Camera test uses actual fresh native face-position metadata, converts elapsed
sample age into the existing Director API and does not identify the person or
measure eye gaze. In this accepted increment it is scoped to the visible guide;
pause/dismiss/configuration change stop it. See READINESS-HANDOFF.md for adapter
limits. Live conversation-wide gaze remains a separate possible increment.

Account rows honestly show Google/Gmail/Facebook/TikTok disconnected. Viewing
prerequisites does not request credentials, open an account or perform API calls.

## RED, fixes and evidence

- `0755028c`: six actual Activity UI tests fail because the guide entry is absent.
- `a39b614b`: 170 executed tests, 168 pass and two fixture-timing failures. Android
  queues OnDismissListener; inspecting fields before delivering that queue was
  not product evidence. Test-only `a408d8bc` drains the real main queue; assertions
  and production remain unchanged, six author cases then pass.
- Independent CR-1 at camera source `d32fe38a`: null timestamp defaulted to now.
  `6a354bec` rejects it. The native adapter already passed an invalid sentinel;
  this was a policy API gap, not a demonstrated native timestamp bypass. Original
  22 independent assertions plus two further adapter cases pass (24/24).
- Independent CR-2 at `a408d8bc`: three of eight real caller tests show retained
  hearing buttons confirming failed/replaced/closed samples. Narrow `070d2c64`
  repairs the callback binding; six authored plus eleven independent cases pass
  (17/17), including successful completion and exactly-once holdouts.
- `3e755780`: first-voice default baseline is 1 pass/1 genuine failure. Exact
  `6c2a3095` passes 28/28: those two cases, nine independent first-welcome/actual
  callback cases and the prior seventeen. Zero errors/skips; before/after source
  index identical. Missing and network-only voice packs are explicitly tested.

Independent raw evidence and commands are in
`docs/evidence/momega1/readiness-review/`, `first-welcome-review/`, and QA's
`docs/evidence/momega1/qa/`. The 164 adjacent tests passed at the earlier `a39`
production; they do not independently certify all later source changes. Final
combined unfiltered regression/lint/build remains required.

## Remaining and recovery

Actual audible similarity, physical camera alignment, smooth Fold7 motion and
account authorization are NOT_RUN. The existing chat currently hides the home
avatar; the separate visible-avatar branch addresses that discovered connection
rather than animating a hidden view. Work effects and sound cues are separate.

Pause or stop first; no sensor auto-resume. Rollback reverts the caller before
removing camera helpers. Existing explicit mute, owner facts, tool receipts,
package, signature and release channel stay intact. No installation was done.
