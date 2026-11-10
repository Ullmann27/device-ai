# Cinema render coverage at a7b4d1

Audited local source `a7b4d1a090ab7117f29f6fcc3440b60b863395a5`, equivalent remote
[`5457351`](https://github.com/Ullmann27/device-ai/commit/5457351a7585eada2393f80aa81db4bff5cc8897),
Git tree `6906b84e8434d4247a759ac0e7cf701ad82416dc`.
This review only reads existing images/receipts and adds a portable wrapper for
existing tests. **No Gradle, Blender or other renderer was started by this task.**

`coverage.json` records 126 existing PNG files (122 unique hashes) from nine
named current-session directories, with actual pixel dimensions, hashes,
source attribution and provenance limits. It is not an exhaustive audit of all
historical repository previews. Workspace/toolchain locations are recorded as
aliases, not claims that those transient files are committed.

| Existing set | Exact phases / dimensions | Coverage and limits |
|---|---|---|
| Native work sequence | Inner 884x760 and cover 412x915: idle; work 140/280/650/1180/2100 ms; return 650 ms; returned; aborted-return | 18/18 PNG/receipt hashes match; source `9fd5d1` / remote `2eca96c`. Real Activity/views, scripted work lifecycle, API28 native Canvas, 2D fallback. Four PNGs committed. AgentActivity and JarvisWorkWindow changed since these captures. |
| Native static matrix, same directory | 19 PNGs: 390x844; 412x915 and font 1.5/2.0 plus dock return; 700x720; 780x360; 832x748 and font 1.5/2.0; 960x640 and font 1.5/2.0; 1280x800; 864x1536; 1384x1536 home/live; 1536x1024 | All 19 hashes match JSON; same older `9fd5d1`. Still layout coverage, not dynamic phase coverage. |
| Actual STOP | Inner 884x760 only: 32 ms, 352 ms, returned | Three PNG hashes match `completed-sequence.json`; source `0d71b82` / remote `3c8ab95`. Same app/src/main as audited source. Two PNGs committed. Actual STOP touch; worker completion is scripted. No cover STOP image. |
| Actual dock DOWN/CANCEL/UP | Inner 884x760 and cover 412x915: idle, held 160 ms, cancelled, opening 672 ms, opened | Ten PNGs at exact audited `a7b4d1`. **RED: two tests fail** at the drawn-material assertion; held `pressed_key=null`. PNG hashes computed here, not present in their original JSON. These are valid failure observations, not passing material proof. |
| Earlier native navigation | Two before + four after stills, 412x915 and 960x640; after includes Chat→Home return | Six receipt hashes match. Source `da3cb58` before, `4ac9c9b` after; current app differs. Redundant historical navigation evidence. |
| Unbound native scene set | Inner/cover: start, listening, thinking, speaking, tile-pressed, opening/open/scrolled/closing/closed system window | Twenty PNGs and view dumps exist; run.log says successful, but there is no per-image source/hash receipt. **Source UNBOUND**, so they cannot certify the current state. Existing harness: ContinuumVisualEvidenceUiTest. |
| Java2D production painter | Fold viewport 884x760 (PNG 884x816), cover viewport 411x960 (PNG 411x1016). Per viewport: idle; work 0/140/280/420/650/900/1180/1600; return 0/140/280/420/650/900/1600 ms | 32 PNGs exist. Group attribution `e2fb22d`; current JarvisFluidLight and preview helper differ from recorded compiled-input hashes. Local provenance notes helper/doc edits. No per-image receipt for all 32. Caption adds 56 px; cover geometry differs from native 412x915. Portable avatar stand-in, no shader/Filament. |
| Embedded edge shader | 12 phase samples edge-00…11, one comparison sheet, one baseline-direct image | Fourteen software-Skia PNGs. Embedded shader hash matches audited source; no per-PNG receipts existed, hashes computed here. Component-only 400x240 edge samples, not full viewport/window composition or Android hardware AGSL. |
| Original GLB / Blender | Neutral listening, speaking-AA, gaze-left, after-interruption; 480x640 | Four images match receipt hashes. Original GLB and all seven mapper/renderer input hashes still match audited source. Blender studio lighting/framing; not the app window/camera, native Filament, live audio or mobile performance. |

The four native sequence representatives and two STOP images are already in
repository evidence. Other files listed here remain in the inspected workspace
or toolchain directories; this manifest does not silently claim their upload.
No images are duplicated or fabricated. Four representative native/Blender
PNGs were visually inspected; the remaining files were checked by headers,
metadata and hashes, not claimed as a full visual-quality review.

## Missing critical proof, prioritized

1. **Fixed dock pressure at both viewports:** exact-source GREEN frames for
   held/cancelled/opening/opened are missing; the current ten frames deliberately
   preserve RED. Reuse CinemaButtonMaterialUiTest after the integrator fix.
2. **Current-source work/return:** refresh the 18-frame inner/cover sequence
   because two production files changed. Current native capture points omit
   work 0/420/900/1600 and early return 0/140/280/420/900; the Java2D equivalents
   cannot substitute. Add only necessary sampling points to the existing
   fixture if a defect falls between its current times, not a second renderer.
3. **Cover STOP and interruption:** no actual STOP-button return frames at
   412x915. CinemaStopReturnUiTest currently hardcodes 884x760; a parameterized
   cover fixture is NOT_IMPLEMENTED. Mid-flight cancellation/rapid follow-up
   image sequences are also missing; final aborted-return alone is insufficient.
4. **Runtime-specific final look:** no full-scene API33 hardware AGSL or native
   Filament image on Fold7, no real speech/audio-synchronized frame sequence,
   and no measured device FPS/thermal/energy result. Original-avatar Blender
   stills and software shader rasterization do not close this gap.
5. **Bound listening/speaking/quality states:** the 20 loose scene PNGs need a
   new source-pinned capture with per-image metadata. Native dynamic low-power,
   reduced-motion and thermally throttled phase images remain unverified.

Do not confuse “fold-open viewport” with a tested physical fold/unfold
transition. Static 1384x1536 frames also do not establish real Fold7 density.

## Existing harness and next commands

The new [portable wrapper](../../../tools/cinema-native-qa/README.md) adapts the
integrator's scratch `/tmp/continuum-native-sequence-qa.py`, using configured
existing tools and a shared nonblocking lock. It has only compile/help/pure
configuration validation; **its real capture run is NOT_RUN**.

Prefer the wrapper’s separate compile (2200 MiB) then capture (1100 MiB) phases
on the same checkout/cache to avoid overlapping a large compiler heap with the
native test JVM. These are explicit settings, not a measured memory guarantee.

After the integrator grants the single Gradle/render lease, configure the
existing tool variables named in that README and use a fresh output directory:

```sh
python3 tools/cinema-native-qa/run_native_capture.py \
  --output-dir "$CONTINUUM_QA_DIR" \
  --test at.heinz.deviceai.CinemaButtonMaterialUiTest \
  --test at.heinz.deviceai.CinemaNativeSequenceUiTest \
  --test at.heinz.deviceai.CinemaStopReturnUiTest
```

For the separately controlled static matrix, the existing
`tools/capture/capture_jarvis.py --matrix` uses its configured shared Gradle
runner; do not launch that in parallel. It disables animation by design, so
it does not replace the phase fixtures above.

Component-only alternatives, also wait for the render lease:

```sh
bash scripts/cinema-fluid-light-preview.sh "$CONTINUUM_PREVIEW_OUT" current stills
python3 scripts/continuum-fluid-edge-shader-test.py --preview-dir "$CONTINUUM_EDGE_OUT"
```

The Java2D helper needs existing ANDROID_JAR/KOTLINC/JAVA; Skia needs its existing
Python environment. Neither command installs anything. For original-avatar
stills, first use the source-pinned exporter in
[the existing avatar README](../../../tools/avatar-3d/reference-atlas/README.md),
then the same `render-production-poses.py` with the freshly computed asset/pose
hashes and `--frames neutral-listening,speaking-aa,gaze-left,after-interruption`.
Keep Blender `--background --factory-startup --threads 1 --python-exit-code 1`;
request PNGs, not video. Stale exporter hashes must not be substituted.

Regenerate this read-only inventory using the existing files:

```sh
python3 docs/evidence/cinema-render-coverage-20261110/build_inventory.py \
  --workspace "$CONTINUUM_WORKSPACE" --toolchain "$CONTINUUM_TOOLCHAIN" \
  --output /tmp/cinema-render-coverage.json
```

Integration owner: Cinema root integrator. Own paths are this evidence folder
and `tools/cinema-native-qa/` only; research #718 is untouched. No main merge,
new rendering engine, app-default change, package/signing action or device
installation is included. Rollback removes only these helper/docs files.
