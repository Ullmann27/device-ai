# Actual-work effects: source handoff

Base `ebefd45ab7eef88a47cf8f66872885da6ab20382`; final production/test source
`ca429fd81007473f1b0c0e66be6112f78f652613`. Isolated branch
`codex/momega1-effects-20261103`, [draft PR465](https://github.com/Ullmann27/device-ai/pull/465)
targets Chief, not main. Implementation follows [the approved plan](TASK-EFFECTS-PLAN.md).

Only three production files changed: new `ui/ContinuumTaskEffects.kt`, new
`ui/ContinuumUiFeedback.kt`, and narrow event/lifecycle/dispatchDraw additions in
`ContinuumHologramView.kt`. Existing layout, fonts, buttons, palette, Avatar3D,
Director, Contract, asset, ToolGate, runtime and manifest are unchanged by this
lane. **AgentActivity integration is the Chief's separate branch**, not a claim
made by this helper acceptance. Camera continuation remains existing guide-only
behavior in that lane; no new camera continuation feature was attempted here.

## Exact integration hooks

On the UI thread call `hologram.setTaskEffectScope(scope)` for new actual owner
work. Capture `SystemClock.uptimeMillis()` at the actual boundary **before**
posting to UI; then `hologram.onTaskEffect(scope,event,occurredAtMillis)` returns
whether a fresh event was accepted. No timestamp default is provided.

- `Started(id, Kind.TOOL)` belongs inside the actual ToolRunner invocation,
  never model proposal/approval/busy state.
- `Started(id, Kind.WORKER)` belongs inside `WorkerJob.run`, never submit's
  accepted/queued Boolean. `Finished(id)` belongs in actual run/finally exit;
  a supervisor timeout/report can precede the job's real exit.
- `Read(id)` is only for a matching actual successful read, currently real
  `web_read` return. No content-read file tool is registered. Directory listings,
  app-screen observation and model statements are not Gmail/PDF/account reads.
- `Finished(id)` is an exit/departure, **not success or verified knowledge**.
  Leave a completed scope until normal expiry/next work if its 650ms departure
  should remain visible; cancel/session end/pause must clear immediately with
  `setTaskEffectScope(null)` / existing foreground lifecycle.

Four active IDs, twelve brief pulses and thirty-two seen IDs bound storage.
Freshness is 750ms with future/negative timestamps rejected. Strict View uptime
watermarks reject pre-pause/pre-hide/pre-end arrivals even if the same scope is
reapplied. Policy/detach clears all; resume does not replay work. Started/Read/
Finished pulses last at most 900/1400/650ms. A Read supersedes only its matching
obsolete tool-start badge. No text, counts, account logos or claimed outputs are
rendered. The single existing visible-view clock drives dispatchDraw above child
TextureViews, with existing reduced-motion/power/thermal policy and clipping.

Activity-owned `ContinuumUiFeedback(context)` defaults silent. Call
`updatePolicy(enabled=voiceEnabled, foreground=foregroundLearningActive,
voiceBusy=voiceSpeaking || voiceListening || voiceUtteranceId!=null)` before
playback and at policy boundaries. `play(Cue.BUTTON|TASK_STARTED)` is a 40/60ms
local PCM chirp, one static track, 160ms cooldown. It respects system effects,
ringer/mute/volume/music gates, changes no volume/focus and releases on policy
revocation/close. Close on destroy. Disable duplicate platform click sound only
on buttons wired to this helper. No model data controls sound or work events.

## Actual evidence

[Evidence README](../../../evidence/momega1/effects/README.md) and manifest bind
seven source/test hashes and raw receipts. Final exact **63/63 PASS**, zero
failures/errors/skips, Gradle exit 0, source-before/after identical. This is 15
authored + 17 unchanged independent + 31 existing regression tests, not full app
QA or device acceptance.

- `1535e31e`: 13 actual missing-feature failures, no errors/skips.
- `0477c9e5`: 41/44 pass; genuine static AudioTrack initialization bug plus new
  fixture geometry and pre-existing protected gaze fixture failure.
- `8a686173`: 43/44 pass; remaining audio failure was missing Robolectric
  `native_write_short` support. A test-only adapter bridges that native seam to
  existing byte-write shadow; public Android write/state/play/release stay real.
- Independent `8a`: 14/17 pass, three genuine lifecycle replay failures. Narrow
  View watermark fix closed all three on `0d34`;63 final retains those cases.
- `0d34`: 61/62 pass, intentional tiny-glyph visual RED. Nominal24dp backed
  peripheral glyphs remain23.8dp at compact 88; Root inspected actual captures.
- `688b05e3`: 62/62 pass, but Root's visual review correctly found overlapping
  Read/tool badges. Full original evidence remains preserved.
- `8bb8de49`: real production painter on official Kotlin/JUnit,7/8 pass; compact
  Read expected one unrelated tool badge but saw two. The final one-line fix
  removes only the matching Started pulse; all unrelated work remains.

The old protected3D test also failed at its original resume assertion on exact
`ebefd45a` (2/3 pass). Root/UI-author approved only a test clock correction:
wait real wall time beyond the actual private clear watermark, bounded 100ms,
fail if it cannot advance, never invent a future timestamp. Production and old
assertions/TTLs remain unchanged. Separate baseline+fixture composition
`28bd1978` passed 3/3 with the entire main source tree byte-identical.

Final 36 PNGs use the actual production Canvas/painter with explicit synthetic
events at phone/Fold and chat88/120/160/200 heights. Contact sheets retain native
pixels and identify synthetic inputs; no reference media, fake face render or
generated screenshot is substituted. These default-orb captures plus the
opaque-child overlay test do **not** prove native Filament GPU rendering.

## Remaining gates

Root owns actual Activity caller/visibility/button/voice integration and its
independent review. Physical device, heard audio/voice identity, real account
adapters, exact video equivalence, camera continuation, GPU/performance, APK
install/sign/release remain outside this host helper result. No new camera work
was added in the owner's remaining window. Rollback reverts these three
production files and associated tests; no model, permission or owner-data
migration occurred.
