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

## Frozen verification wave

Visibility source `ab55e440860f5b8d04d9f44f714809e13292af76`: root authored4-case baseline at8f876306 executed4 assertion failures,0errors/skips. Independent6-case class uses the actual `buildConversationPane` and navigation methods, one attached original head, actual TTS listener/Director, phone/bothFold geometry and real compact composer. A plain Activity hosts the produced pane to avoid unrelated full AgentActivity boot; this is explicit fixture scope, not a full startup/device claim. SHA256 `17572793bc4b7efbcce6f38b5bb9d9bd25af72b1d2d78fe858647c824f99121d`.

Effects source `8a6861730440bdfa046d1a4e24950757643411b4`: authored original13-case assertion RED at1535e31e preceded candidate0477c9e5. Candidate44 had41PASS/3FAIL, not acceptance. The author diagnosed a genuine Android static-track state bug: the initial initialization check prevented writing the PCM that transitions MODE_STATIC out of STATE_NO_STATIC_DATA. Source8a changes write-before-initialized ordering. The initial Canvas comparison also lacked stable geometry across pending decor traversal; corrected fixture keeps exact root and opaque-child dimensions, without production drawing changes. The protected existing3D gaze test failure remains separately under baseline/timing investigation; do not count it as a proven effects regression.

Independent effects17:8 pure event/painter cases (`ea7ec7f331482f6af114386f02973863afb2cbd8650bedd97219d38330624e5e`),4 actual View/nativeCanvas boundary cases (`ac7290fe05aee1ef590b4488be93703251c00107b5f257d9264f999063cd956c`),5 feedback cases (`e517083c87158040bbd92e9f38cb401f49a279dc0317685fdfbe02a9ddc3b609`). The author raised a possible pre-pause captured-arrival replay if a caller reapplies the old scope; concrete tests also cover hidden and explicit-end boundaries. No runtime finding is claimed before execution.

Installed Robolectric4.14.1 ShadowAudioTrack implements byte JNI writes but not native_write_short. The independent feedback fixture supplies only that missing native short-write return/sample capture while retaining real Android public constructor/write/play/release state logic. It proves helper/framework calls and bounded PCM, not physical audio. Reflection/API/fixture failures must be reported separately. All classes are snapshotted by QA before compilation; no direct compiler or model process is launched by reviewer. Execution remains pending.

## AE-1 — observed stale arrival replay (P2 / REQUEST_CHANGES)

Exact8a686173 independent17 executed14PASS/3FAIL/0errors/0skips, Gradle exit1, source comparison exit0. The three actual View failures occur after pause, visibility loss, and explicit scope end: a caller reapplies the same scope and a Started event whose original uptime preceded the boundary is accepted as a new arrival. The author first raised this possibility in self-audit; reviewer supplied actual cases under the previously frozen lifecycle/replay challenge group. It is not claimed as a blind discovery. These cases fail at their intended rejection assertions; no harness failure occurred.

Requested narrow repair: keep a monotonic invalidation timestamp at the actual View boundary, reject original event times at/before the last clear/inactive boundary even after scope reapplication, and retain acceptance of strictly newer explicit events. No new clock/scheduler or caller authority. Original17 classes stay unchanged for repair repeat. Raw command/init/Gradle/XML and source identity receipts are under `docs/evidence/momega1/avatar-effects-review/effects-8a686173-independent-red`.

All8 pure correlation/time/finite-geometry cases pass. All5 audio cases pass with the disclosed short-JNI fixture. The fixed-layout nativeCanvas case proves effect pixels draw above an opaque child and return byte-exactly to the baseline after scope end; it passes, distinguishing it from the author's earlier variable-geometry pixel failure. It is a host Canvas check, not a physical TextureView/Filament composition guarantee.

## Visibility initial exact-source result (diagnosis pending)

Exactab55e440 run10 cases:9PASS/1FAIL/0errors/0skips, Gradle1/sourcecmp0. All4 authored tests and5 independent ownership/geometry/TTS cases pass. The remaining independent `movedHeadHasNoHiddenCopyAndBackgroundStopsExistingClock` fails on line126, which originally contained both isShown and callbackCount>0 without labels. This does not yet distinguish a product lifecycle bug from a fixture/window scheduling issue. Original class hash17572793 and raw logs are retained under `visibility-ab55e440-initial`.

Diagnostic-only class hash `0a37769b3d840d80272c7e7bc6293099105b7aeb3b8bddae51cb9eb2d3e03c92` adds state printing and assertion messages, preserving both predicates and the paused-clock assertion exactly. Root has made no repair on this evidence. QA will repeat the failing method on exactab55 before any decision; no hidden test weakening or source acceptance.

## AE-1 repaired; legibility still open

Exact `0d34baaeed1c28eb355a6cd37e27f89174a231ed` repeats the original17 independent tests unchanged:17PASS/0errors/skips. The View now invalidates task effects with a clear-boundary uptime and rejects original times at/before that boundary. All three stale-arrival regressions pass; AE-1 is closed. Full focused run62 includes61PASS/1FAIL, Gradle1/sourcecmp0: the single failure is the author's new explicit glyph-legibility assertion (tool width9.600002dp at412×307). This is not an overall presentation pass. Exact raw receipts retained under `effects-0d34baae-boundary-green-legibility-red`.

Reviewer inspected the actual production nativeCanvas READ PNG at412×307 (path/hash in receipt): the page outline is discernible near upper right but tiny against substantially brighter orb lines. This supports the existing legibility hold. Actual chat heights88/120/160/200dp must also be inspected; home307/291px captures do not establish compact-chat readability. Future frames must remain actual source Canvas output with explicit synthetic events, not invented worker execution or device screenshots.

The author's prior protected3D gaze test had the same resume assertion failure on unchanged baselineebefd45a (3cases2PASS1FAIL, no errors/skips). Root approved an observe-only fixture correction waiting at most100ms for real wall time to exceed the real clear watermark; original gaze assertions/productionTTL remained unchanged. Its later pass is not an effects source repair. Audio host short-write coverage likewise remains explicitly controlled. No reviewer feature edit or claim of live physical rendering/audio.
