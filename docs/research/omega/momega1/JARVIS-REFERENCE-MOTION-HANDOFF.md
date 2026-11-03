# Reference-driven native avatar motion

Status: WIP / PARTIAL CONTRACT EVIDENCE; appearance is NOT_ACCEPTED and device
execution is FOLD7_NOT_RUN. Baseline `d8a927dd6ced4dea489e2042afb4b5624157fa3b`.
Branch `codex/jarvis-reference-ui-20261103`, target `codex/momega1-chief-20261103`.

The owner's latest clarification is authoritative: preserve the existing agreed
Continuum design, including its colours, typography, buttons and layout. The
orange-marked video is a reference for effects, head movement, speech behaviour
and working animation only. It does not replace the existing design.

Primary video: `gemini_generated_video_33fd6ada.mp4`, 10.026667 seconds, 1280×720,
24 fps; SHA-256 `a9c525346b200c2aa062ff9d2c7495f8492fbaa9016a4cf125364dda77b35893`.
Private media stays outside Git. The other four videos are functional inspiration,
not proof that their advertised external systems work inside Continuum.

Existing code inventory: `ContinuumAvatarRenderer` is an optional procedural 2D
head using the shared native/Java2D painter, existing Director/reducer and the
existing HologramView motion/lifecycle policy. The original orb is retained.
Foreign Chief `7247e99d0b93b62dfb2b5eee22486d22911cb722` contains a separate,
optional real 3D path. PR404 supplies the CC0 humanoid geometry; PR405's final
source `7caef9b624beebdb78c8c081a17f4978461ae496` supplies its bounded native
Filament integration. The existing 2D renderer is unchanged and remains a fallback.

After the owner's humanoid-head clarification and the Chief's path approval, the
scope is a narrow, exact import from PR405: `avatar3d`, bundled `assets/avatar3d`,
the optional path in `ContinuumHologramView`, the two Filament 1.56.0 dependencies,
focused native contract/lifecycle tests and their existing build helpers. PR408's
layout changes are excluded. The native renderer shares the existing Director,
reducer, frame clock and lifecycle policy; no new scheduler, TTS or permissions.
`AgentActivity`, ToolGate, Manifest, theme, typography and dashboard geometry are
unchanged. Existing owner renderer preferences are not changed.

The bundled real GLB is 8,690,400 bytes, SHA-256
`f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e`.
Its CC0 MakeHuman provenance, morph targets and joint names were inspected.
Thirty upstream host-render anchors demonstrate actual humanoid geometry and
depth, but the independent reviewer rejected reference appearance: the skin is
too dim/opaque, the wires sparse and the eyes/lips insufficiently readable.
Those host renders are not Filament or Fold7 screenshots.

Independent RED on the unchanged PR405 source: reviewer freeze `8c43f017`,
12 actual Kotlin/JUnit cases, 10 PASS and 2 FAIL: idle head and idle gaze never
move. The other ten cover bounds, event gaze, speech provenance, blink, reduced
motion and buffer reuse. These are presentation contracts, not visual acceptance.
The import was checkpointed as `f5cd37e9`. Motion/gaze implementation is
`da067717`, followed by the 460-second clock-wrap correction `1649a043`.
Actual native contract run on `1649a043`: 33 PASS, 0 FAIL, 0 SKIP, including all
eight imported GLB/morph contracts, existing Director/reducer regressions and
eleven new observed-gaze/clock-boundary cases. Actual packaged GLB preflight PASS.
Actual Android SDK36 + pinned Filament/gltfio AAR API compile on `da067717` PASS;
one pre-existing unnecessary-cast warning. These author checks are recorded under
`docs/evidence/momega1-avatar-motion/`, each bound to its own exact source commit.
They do not constitute independent acceptance, a full APK build or device evidence.

Observed gaze API: `AvatarDirector.onObservedGaze(GazeTarget, confidence, ageMillis)`
accepts confidence 60..100 and age 0..750 ms only. The Camera2 adapter computes age
in its monotonic domain; the Director translates that age into its existing clock.
The reducer expires observations and gives task/approval/blocked gaze priority.
Pause/detach/reduced motion clear observations, and inactive views reject late
camera events. `gazeObserved=false` preserves existing pose constructors. The
adapter/Activity caller is a separate integration lane and is not proven by these
synthetic event contracts. Face position is not eye tracking or identity.

Independent reviewer checked `1649a043` against 12 unchanged mapper tests,
13 pre-frozen new gaze cases and a post-inspection future-timestamp counterexample:
25 PASS / 1 FAIL. A rejected future sample poisoned later observation ordering.
The narrow fix `26555aa6c59fdd4dd2e190defcdf49b983305555` restores only the last
actually fresh watermark when rejecting a future sample. The same unchanged
26 independent cases now PASS (0 FAIL, 0 SKIP). Independent acceptance is limited
to the motion/gaze contract. Actual camera and visual/device acceptance remain open.
The final author rerun on `26555aa6` also has 34 Kotlin/JUnit, 5 Node source
contracts and 8 Python geometry contracts PASS; actual SDK36/Filament API compile
PASS. Raw outputs are under `docs/evidence/momega1-avatar-motion/26555aa6/`.

Next: complete independent motion/gaze counterchecks and remedy measured failures.
Observed face-position gaze must expire, yield to real task state and remain
distinct from cosmetic idle gaze. Material/look changes require same-conditions real GLB
renders and independent review. Audio identity, real TTS synchrony, device
thermal/FPS, actual work-event effects and final visual approval remain unrun.

The two added 20.032-second reference videos extend the original clip with
document/folder/agent flights and Gmail/PDF/choice/weather/calendar illustrations.
Hashes are `5c73ce6cf204fe2c29cc572da80514c7a2c8657a411c049255a476647bfe7c878`
and `7bd81c97a8c6d1a8e84b776461bbb454dcb4368777c6ecf97a90c1be7d10ea1d`.
Both were visually sampled; audio hearing remains NOT_RUN. Their fictional
telemetry and capability displays never become actual app status.

Material experiment tools derive a separate GLB from the known `f39a89fc` source,
preserving its geometry/skinning/morph BIN bytes, and render A/B front, oblique
and profile views with identical Blender settings. The first derivative
`15573963f9c3f862a73c5c963b00c3a8106f1090c1bc03c8e7ed4b9a271937f5`
is EXPERIMENT_NOT_ACCEPTED and has not replaced the packaged asset. Rendering is
complete: six same-condition real GLB renders were independently inspected and
rejected for appearance (opaque face, coarse wires, pale staring iris).

Second experiment `fe76e85d8396c24543ae14d7fe31cc2c952d383ab30606393f0deb18204f9e8a`
adds UVs and a procedural embedded texture, preserving all original binary mesh,
skinning and morph bytes. Four actual 32-sample A/B renders show denser lines but
an unsuitable cylindrical grid over anatomy; this variant is also NOT_ACCEPTED.
Further experimental tools derive lines from the actual triangle topology, and
separately test a 15% neutral blink relaxation with rescaled remaining blink
targets and 1.30x pupil XY scale. These are explicitly changed geometry/morphs,
not a material-only claim. No experimental derivative replaces the packaged GLB.
The independent review lane has exported actual reachable production-mapper poses
for a fixed-camera replay; synthetic inputs are labeled and never called real TTS.

The current unintegrated look candidate is
`03caa24f5c7b8db40a91f42a53538f2b6f43c1db02147b091ea6cb0685e2759e`.
Density-normalized actual topology edges avoid the white eyelid/lip masks seen in
the preceding dense variant. The front surface remains opaque so backfaces do
not show through anatomical features; the intended holographic appearance is
light and fine topology, not a false claim of physically transparent anatomy.
Root's visual review finds facial volume and relaxed eyes improved, while cyan
silhouette/feature illumination and violet midface need further work.
Five actual production-mapper poses (neutral, full blink, AA+jaw, O+jaw, stop)
were rendered on both the original and this candidate with the same fixed camera,
lights and 16-sample Cycles settings. Source-bound comparison/neutral images,
manifests and raw logs are in `docs/evidence/momega1-avatar-look/03caa24f/`.
This is not a Filament, real-TTS, camera or device result and the asset is not
promoted to the app. The independent reviewer is investigating abnormal lateral
tooth visibility in O+jaw; it also appears in the unchanged original GLB under
the same replay, so darkening teeth is not used to hide the issue.

The mouth investigation established two separate causes. The pinned upstream
builder composes the same `mouth-open` basis into both speech visemes and
`JawOpen`; the old mapper added both at full strength. The outer face skin does
not include the Jaw bone, so bone rotation was not a third skin deformation.
The original low hinge instead pushed rigid lower teeth through the outer face.
The independently frozen canonical triangle-intersection checker confirmed the
original failures; merely dimming teeth was explicitly rejected.

Mapper candidate `9a23e5d6a9dc96d15525347c99ff682d632d7277` subtracts the
viseme's known common opening from the additional `JawOpen` weight and drives the
inner jaw from the maximum actual shared opening. Independent pursing, spreading
and protrusion residuals remain intact. Author contracts: 38/38 PASS. Independent
version-2 contracts: 27/27 PASS. The old version-1 suite is preserved; its exact
double-opening expectation now fails intentionally (25/26 PASS), not silently
weakened. The initially overbroad author deformation bound and its actual RED
are retained alongside the corrected shared-basis RED, frozen before the fix.
Author evidence is under `docs/evidence/momega1-avatar-mouth/`.

The rig derivative
`6b199c5989640b516b8c45401ececeadb8ed1baae1d1e1ab5db20d19e966aa1e`
raises the Jaw hinge 40 mm and inversely rebases all nine rigid children. It
preserves every mesh, morph, material and neutral child world transform.
Neither that rig alone nor the mapper alone fixes all original failures. Their
combination passes the independent six maximum poses and all 113 additional
actual-reducer poses with zero front-surface tooth-edge intersections. The
reviewer owns those receipts in PR #434. Five actual fixed-camera replay images
use the original materials, so this result does not conceal geometry with color.
The complete source/rig fix now updates the packaged asset and its runtime hash
pin together, retaining the original materials and all existing avatar choices.
Final-pin source/asset regressions are pending on this checkpoint; earlier
mapper results are not relabeled as that new source result. This correction
does not accept the avatar's reference appearance.

Separate material derivative
`c016237ab6d7be99dc0c0c0cc49a4de1b11f03896e8a03a4a8efaa20f6ff08f8`
adds a standard glTF emissive texture to the `03caa24f` look candidate. The
texture follows interpolated real surface normals. It is a fixed object-space
artistic contour pattern, not dynamic Fresnel or a Blender-only shader. The
same-pose/camera/light comparison increases cyan/violet visibility, but broad
emission flattens cheek volume; a narrower, darker-midface variant is pending.
Experimental material assets remain outside the app. The complete 224-frame
idle replay, final combined-look geometry check and actual Filament/device
appearance are still NOT_RUN. No default avatar choice is changed here.

Rollback: revert this isolated import/increment or leave the optional 3D choice
disabled; no persistence migration or owner data is involved. The existing orb
and 2D selection remain available.
