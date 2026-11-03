# Actual-work effects and local feedback implementation plan

Base: `ebefd45ab7eef88a47cf8f66872885da6ab20382`, 2026-11-03. Chief supplied
the implementation approach and explicitly approved the hook API. No further
owner decision or new permission is required. This is the existing MΩ-1 visual
integration follow-up, not proof of a new account or worker capability.

Goal: small task arrivals/read flights/terminal departures and quiet local click
feedback tied to actual callbacks, preserving the existing face, colors, layout,
fonts, buttons and single visible-view clock.

Read-only audit: Hologram drawFrame/scheduleFrame already gates visibility,
foreground, Android/in-app reduced motion, power save and severe heat.
`dispatchDraw` must place effects above the optional native TextureView child.
The ToolGate runner in AgentActivity is the actual invocation boundary; model
proposal/approval/busy state is not. ChiefWorkerPool.submit only accepts/queues;
WorkerJob.run is actual start. Report text is not independent outcome evidence.
No file-content read tool is registered; web_read and actual file-import
callbacks can report actual reads, while list_files is only a listing and
read_app_screen is neither Gmail/account API nor PDF content.

Chosen approach: bounded pure painter state inside the existing Hologram clock,
plus a separate Activity-owned local PCM feedback helper. A second animation
timer, model-text-derived particles, account/PDF logos and downloaded sounds are
excluded. Existing Avatar3D/Director/Contract/asset and AgentActivity are owned by
other lanes; this lane changes none of them.

## Interfaces and files

- New `ui/ContinuumTaskEffects.kt`: `setScope(String?)`; `accept(scope,event,
  occurredAtMillis,nowMillis):Boolean`; `draw(painter,width,height,nowMillis)`;
  `clear()`. Sealed events Started(id, TOOL|WORKER), Read(id), Finished(id).
  Opaque IDs only, 4 active references, 12 pulses, 32 seen IDs per scope;
  reject further IDs rather than evicting replay guards. Freshness 750ms, no
  future timestamps; Started/Read/Finished pulses expire within 1400ms. Known
  stale terminal removes its active reference without drawing a departure.
- Narrow `ContinuumHologramView.kt`: `setTaskEffectScope(String?)` and
  `onTaskEffect(scope,event,occurredAtMillis):Boolean` on UI thread; source time
  is required and uses SystemClock.uptimeMillis. Overlay in dispatchDraw after
  super, no new scheduler. Inactive/motion-blocked lifecycle clears scope and
  all pulses, so resume cannot replay them.
- New `ui/ContinuumUiFeedback.kt`: `updatePolicy(enabled,foreground,voiceBusy)`;
  `play(Cue.BUTTON|TASK_STARTED):Boolean`; `close()`. Default silent. Fixed
  locally generated <=60ms PCM, one static AudioTrack, >=160ms cooldown, no
  thread/timer or system volume modification. Respect existing mute, playback/
  recognition, system sound toggle/ringer/mute/volume; invalid policy releases.
- Root sequentially wires actual ToolRunner / WorkerJob.run and terminal
  callbacks, actual successful read boundaries, central button helpers, and
  existing voice/mute/pause/destroy policy. No effects from model strings.

## Test-first execution

- [ ] Commit executable baseline tests using reflection only for absent APIs.
  Pure tests cover idle empty, freshness/future, scope/replay, bounds, read
  association and expiry. UI tests exercise actual View lifecycle and overlay
  above a synthetic opaque child. Audio tests exercise actual helper/AudioTrack
  state and default/mute/voice/pause/close suppression, not audible identity.
- [ ] QA runs frozen RED before product implementation; preserve exact receipts.
- [ ] Implement only the files above, run focused GREEN plus existing Hologram
  motion/audio and protected presentation regressions; freeze source.
- [ ] Capture synthetic event sequence through actual production Canvas/painter
  at phone/Fold bounds, label host inputs and non-Filament/device limits, inspect
  frame contact sheet and obtain separate independent review.
- [ ] Preserve exact raw RED/GREEN/source/asset hashes and root hook receipt,
  commit/push isolated draft PR. Device/audio/reference-video acceptance stays
  open until actually observed. Rollback removes new presentation/feedback
  hooks and helpers without changing models, permissions or owner data.
