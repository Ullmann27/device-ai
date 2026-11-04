# Three additional Continuum helpers — completed avatar patch set

Date: 2026-11-04. Review PR: [#495](https://github.com/Ullmann27/device-ai/pull/495).

## Result and source identity

Heinz requested exactly three additional development helpers to support the four
existing workstreams and then concentrate on the avatar. Three session-scoped
helpers inspected the existing ownership and delivered three bounded avatar
corrections, 26 new behavioral tests and independent cross-review. These are
development agents used in this work session; no persistent Android bot service
or recurring automation was introduced.

The published, jointly tested production checkpoint is
`7859d5d212d61829fabc7dfdbd3ca7587852388d`, tree
`a6f8618692058c7d8d3c8eb3b019161daff1881b`. Its parent,
`20537f2af5c3c685a3a9666a87ed14f377fd7623`, contains the tests and RED evidence
with production still identical to the #491 base
`d352bab6855db2a44def0bbf60fd93202b4e3066`. Subsequent evidence-only commits do
not change the tested production or tests. The source manifest records their
exact blobs and SHA-256 digests.

Branch: `codex/continuum-three-avatar-helpers-20261104`.
Target: #491 / `codex/astra-jarvis-takeover-20261104`, not main.

## The three helpers and delivered changes

| Helper | Initial coordination/support | Delivered avatar work |
| --- | --- | --- |
| `helfer_integration` | Checked ownership, actual event wiring and integration boundaries | Isolated optional event-consumer failures; invalidated queued callbacks after every renderer registration; 9 behavioral tests; independent math and audio review |
| `helfer_avatar_mathematik` | Checked expression, morph and rig contracts | Computed common mouth opening from the visible crossfade; 7 behavioral tests; independent dispatch review |
| `helfer_pruefung_animation` | Checked speech ordering, interruption and animation timing | Rejected stale/replayed audio and recovered from future timestamps using explicit render time; 10 behavioral tests; resolved both independent audio review findings |

### Visible mouth blend

Changed `app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt`.
The old calculation subtracted only the incoming cue's authored opening while
the outgoing cue still contributed visible opening during the crossfade. The
fix first advances the existing viseme blend, sums the authored common opening
of all visible weights, then computes:

```text
extraJaw = max(0, measuredJaw - visibleVisemeOpening)
jawPitchDegrees = 12 * max(measuredJaw, visibleVisemeOpening)
```

Independent lip shapes, the existing 70 ms crossfade policy, rig/asset bytes and
public APIs are preserved. The new tests cover AA→PP, AA→O, PP→AA, outgoing
cue-only opening, stop/zero weight, reduced motion/resume and existing clock-snap
behavior. Actual baseline result: 4 failures and 3 passing controls.

In the original compiled-production probe, AA→PP after 16 ms with measured jaw
0.6 produced common opening 1.2267853: residual AA contributed 0.6267853 and an
extra jaw term added another 0.6. On the final compiled source, the same probe
produces 0.6267853 with extra jaw 0.0. These are synthetic numerical observations,
not a device image or proof of visual equivalence to the owner's reference.

### Audio ordering and future-sample recovery

Changed only `AvatarPoseReducer` within
`app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt`.
An audio event for an inactive utterance, with a non-finite level, or with an old
or equal audio timestamp returns before changing energy or the default pose
clock. The existing smoothing constant and speech reset mechanism remain intact.

When explicit `pose(atMillis)` reveals future audio, the reducer clears that
audio's energy and sets the existing audio boundary to the render timestamp.
Samples through that boundary are discarded; a later fresh sample resumes from
zero energy. This intentionally also drops delayed, not-yet-drawn samples through
the recovery frame. No additional queue, clock, public API or state field remains.

Independent review found two real gaps in intermediate implementations: multiple
callbacks before any render could reopen the mouth from old samples, and a
default `pose()` could confirm a future timestamp and block later recovery.
Separate regression tests were added for both; the final implementation removes
the unnecessary confirmation watermark and passes all ten tests. Their actual
baseline result is 7 failures and 3 passing controls.

The independent replay probe increased energy from 0.25599998 to 0.5136 on the
base. On the final source, it remains 0.25599998 after the same stale sample.

### Renderer dispatch

Changed only `JarvisAvatarSlot` within
`app/src/main/java/at/heinz/deviceai/cockpit/CockpitContracts.kt`.
Each registration, including null and the same renderer object, receives a fresh
immutable token. Posted work checks the token before invoking the renderer and
again after the coarse state callback. The registry, coarse-state and complete
event callbacks have separate exception boundaries. An optional consumer failure
therefore does not suppress the independent consumer's delivery, and a detached
or superseded registration does not receive queued work.

Nine new tests cover failures, replacement, detach, same-instance re-registration,
reentrant replacement, normal delivery and no-renderer behavior. Actual baseline
result: 7 failures and 2 passing controls. The independent final probe queues one
renderer callback despite a registry exception, lets no exception escape, and
delivers no obsolete callback after replacement. A previously queued event is
discarded, not replayed to the newly registered renderer.

## Actual verification

All Gradle executions used the existing local JDK 17 / Android SDK and the shared
builder lock through `/workspace/scratch/b3170a7ae924/run_gradle.py`, with
`CONTINUUM_BUILD_ROOT` selecting this isolated checkout. No GitHub Actions were
started. The wrapper records the source HEAD after acquiring the lock.

| Check | Exact source | Observed result |
| --- | --- | --- |
| Existing avatar, avatar3d and motion baseline | `a65cfcac63c794ea9953034349989b4a45f6ac73` | 62 tests; 0 failures/errors/skips |
| Existing cockpit baseline | `47275f5c137fa76cabc15d344bc68761e12f0dd8` | 30 tests; 0 failures/errors/skips |
| Dispatch RED | `b7091a7ffb819a816c181e5508606ef6a9229dfa` | 9 tests; 7 expected failures, 2 passes |
| Mouth and initial audio RED | `cce9ae9bcd11d06e7798632b3e886ded11268f16` | 14 tests; 8 expected failures, 6 passes; audio later extended |
| Complete audio RED | `538295da168de5ad639fb1240a195ad7d6fec4c5` | 10 tests; 7 expected failures, 3 passes |
| Combined final regression and existing contracts | `7859d5d212d61829fabc7dfdbd3ca7587852388d` | 118 tests; 0 failures/errors/skips |
| Configured Android API lint gate | same published source | `:app:lintDebug` exit 0; NewApi issues: 0 |

The RED totals overlap because the audio class was extended. Counting each new
test once gives **26 new tests: 18 expected baseline failures and 8 passing
controls**. Combined GREEN is **92 existing + 26 new = 118**, across 20 suites.
The old production files are identical to d352bab6 in all RED checkouts.

One earlier attempt used a mistyped method filter and executed no tests. Its
exit-1 log is preserved separately under `harness-filter-error`; it is neither a
product failure nor included in any test count. The complete audio class run
supersedes it. Intermediate source SHAs above are local integration commits;
the public tests/evidence checkpoint has the same final test content. The final
GREEN is bound directly to the published source SHA.

Final command, with unmodified configured lint scope:

```sh
./gradlew --no-daemon --max-workers=1 --console=plain \
  -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest \
  --tests 'at.heinz.deviceai.avatar.*' \
  --tests 'at.heinz.deviceai.avatar3d.*' \
  --tests 'at.heinz.deviceai.motion.*' \
  --tests 'at.heinz.deviceai.cockpit.*' :app:lintDebug
```

Raw XML, full console output, lint output, source manifest, original probes and
the final probe outputs are retained in
`docs/evidence/three-avatar-helpers-20261104/`. The configured lint gate checks
`NewApi`; it is not a claim that all optional lint categories were enabled.

## Independent review and contribution provenance

Dispatch review: `helfer_avatar_mathematik`, PASS under the existing main-thread
registration contract. Math review: `helfer_integration`, PASS for the visible
blend compensation and existing bounds. Audio review: `helfer_integration`, PASS
after the two reported important findings were fixed and covered by tests.
Original reviewer snapshots are kept under `review/`; their earlier pending
execution status is superseded only by the actual coordinator logs above.

| Contribution | Original helper commit(s) | Published integration |
| --- | --- | --- |
| Dispatch tests / correction | `4d0b4a6f86dc9f878a8921957fd79b184c421571` / `b708f553723ec1a94ce3265345c4e0f61b1cbffe` | Tests in `20537f2`; correction in `7859d5d` |
| Math tests / correction | `f3f7d1f849d818b5186589d73aad9d74df259705` / `0d9e3d1bf200653b79d31192409294ff9c705068` | Tests in `20537f2`; correction in `7859d5d` |
| Audio tests | `c678233`, `22329b5`, `8f4f930`, `862f02a` | Tests in `20537f2` |
| Audio correction and review fixes | `e8accee`, `fdc41fb`, final `693f2c0a5721fc2761669cabd578ea0b96f8f837` | Final combined diff in `7859d5d`; do not adopt an intermediate fix alone |

GitHub-created commit metadata differs from local commits. The published tree
was compared with the exact local tree before each ref advance; no force push
was used. The tested source was then fetched and selected before execution.

## Integration scope and remaining gates

- The current live `AgentActivity` construction ultimately attaches the existing
  `ContinuumHologramView` directly to `AvatarDirector`. The fanout correction is
  a tested dispatch component; this patch does not rewire the normal speech path
  or claim that it now traverses fanout.
- `AvatarMorphMapper` drives the optional native 3D renderer. Its arithmetic test
  result does not establish appearance changes in the default procedural head.
- The reducer keeps the existing external serialization and System.currentTimeMillis
  time domain. A default `pose()` supplies no independent clock. General
  wall-clock rollback or cross-epoch replay protection was not added or proved.
- Renderer registration/invocation continues to use the existing main-thread
  contract; arbitrary concurrent background registration is not a new guarantee.
- Native Filament/GPU, Fold7 lifecycle, real audio output, APK installation and
  reference-image equivalence remain **NOT_RUN** for this patch set. No asset
  or illustration was replaced. Existing GLB provenance/anatomical-contract
  questions remain with the avatar asset owner.

Before implementation, the helpers read #491–#494 and the existing avatar/math
handoffs. #494 retains memory retrieval/compiler/ecology and the real memory-event
adapter; #492 retains weights; #493 retains bot personalities. Other teams'
active avatar asset scripts and motion work were left untouched. The later
read-only collision snapshot found our three production files still identical
to d352bab6 in the three visible takeover/avatar/integration checkouts. This is
an observed snapshot, not an assertion that another active branch is frozen.

The applicable experiment compatibility remains EXP-07 (independent evidence),
EXP-11 (existing orchestration) and EXP-12 (source-bound rollback/checkpoint).
No model-quality or runtime-independence milestone is advanced by these tests.

## Handoff and rollback

The complete helper patch is ready for the #491 owner to integrate after checking
the latest target head and file ownership. Keep its source-bound unit evidence;
repeat affected integration gates if the target changes. Real-device/render
acceptance remains an additional gate for the owning avatar workstream.

Rollback is a normal revert of production checkpoint `7859d5d` or omission of
this review branch. There is no data migration, new dependency or configuration
change. Do not reset another active worktree or branch. This work remains in its
own open Draft PR: no main merge, release, installation, signing/update-channel
change, model/S01 execution or new paid service is included.
