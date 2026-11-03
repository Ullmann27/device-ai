# Independent visible-avatar and task-effects review

Status: WIP / UNVERIFIED. Reviewer owns tests/docs only. Visibility baseline8f876306172cfbbeecf2ab91057897823ceeadde, production6c2a3095; effects initial candidate0477c9e52d4a1fc83af5c28892c80f3731494282. These challenge groups are frozen from the public design contract before either new implementation is inspected. Original colors/fonts/home layout remain outside change scope. Actual Fold7, live speech/camera and final product usability remain NOT_RUN.

## Visibility challenges

1. One existing Hologram instance and one existing AvatarDirector survive home↔chat transitions; no duplicate render surface, cloned head, detached observer or second animation clock. Actual TTS/camera/Director callbacks continue targeting the visible original instance.
2. Each view has exactly one current parent. Repeated navigation, chat turns, guide overlays, pause/resume and recreation cannot steal/reparent into a stale container or leave an invisible active copy. Home measurement/layout ignores a hologram owned by chat, while preserving all other home elements.
3. Visible head occupies positive bounded geometry in chat, including small available heights/keyboard pressure; composer and conversation retain usable space. Return-home restores original head layout, scale and hit behavior after foreign chat measurements. Repeated measure/layout cannot clobber a foreign parent's dimensions.
4. Hidden/background/detached views do not continue their own visual animation; returning foreground uses existing ownership rather than starting duplicate scheduling. Current user-selected avatar appearance and mute choices persist through reparenting.

## Task-effect and feedback challenges

1. Effects begin only from admitted actual execution events, not proposals, model prose, queued/accepted-but-not-started work, screenshots or invented worker/PDF progress. WORKER and TOOL correlation cannot cross scope/kind/ID; completion departure does not claim success.
2. Scope/reset, monotonic event time and original uptime control freshness. Reject future/stale/replayed/oversized/unknown/foreign-scope events without corrupting admissible current work. Delayed terminal events may retire their matching active item without fabricating a fresh departure. Empty IDs, duplicate starts/reads/completions and full history fail safely.
3. Bound active resources, pulse/history counts, durations and output geometry. Finite positive/zero/tiny/large view sizes and elapsed-time boundaries cannot produce NaN/Infinity/unbounded painting or revive expired pulses. Read appears only for matching actual TOOL start, once.
4. Draw actual production Canvas output above native 3D child content, with observable changes and recovery after animation expiry; use the existing frame driver only. Inactive/hidden/reduced-motion/power-save/severe-heat/detach invalidates scope and outstanding effects; resume cannot replay hidden events.
5. Feedback remains opt-in and policy gated: saved mute/foreground/voice activity/system effects/ringer/stream volume/music activity. One bounded static track, fixed peak/duration, cooldown and immediate close/release on revocation; no audio focus, volume changes, network assets or worker thread per effect. Owner button and actual task-start hooks need independent exact-source caller review after root integration.

Execution will be staged with QA-owned compiler scheduling. Pure/host tests and synthetic Canvas frames are distinct from actual device playback/rendering. Preserve every candidate SHA, immutable harness hash, raw command/exit/count/skip and fixture-only failure; do not promote baseline compile failures into behavioral RED evidence.
