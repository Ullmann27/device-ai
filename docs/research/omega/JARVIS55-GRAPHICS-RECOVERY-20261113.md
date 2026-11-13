# Jarvis55 graphics recovery

Status: WIP / UNVERIFIED after executor loss, 2026-11-13.

Base: `84268a0b868468074bf9ac904e3682ed885bf058`, tree `cf4b965b4202ad9458cea9fd4a544e08ea0ac247`.
Branch: `work/jarvis55-graphics-recovery-20261113`.
Integrator: root, Code55 line `integrate/code55-apk-fable-20261112` via #812/#809.
Scope: preserve the current renderer, original avatar and five-ring orb. EXP-00/05/06/07/08/11/12 boundaries remain unchanged.

## Changed contract

`tools/cockpit-workstation/verify_unified_assets.py` now checks all six children on each side monitor: back, bezel, glass, rim, stand and foot. All twelve must belong to `cockpit-side-monitors`, the single group hidden by the existing compact presentation. The separate aperture geometry checks remain limited to bezel/glass/rim.

Source review also confirmed:
- `FilamentCockpitStage.update` collapses the side-group transform when `monitorWidth / width > .70`.
- `render_host.py` uses the same threshold and hides the group plus all descendants.
- `build_scene.py` already parents all twelve objects to that group.
- The cyan horizontal lines seen behind the compact avatar correspond to the preserved original orb pedestal tiers. They are not evidence that hidden side monitors remain visible. No speculative visibility or original-asset change was made.

## Current verification

The packaged workstation GLB was read directly from frozen GitHub source, blob `9ce46d7358761109c3060336f0911ec5b94ffd85`, 52,228 bytes. Its GLB v2 header, declared length and JSON chunk were parsed in the connector orchestration runtime. Exactly one of each named child exists, and all twelve parent indices equal the side root (node 35). This is a direct asset-hierarchy inspection, not execution of the Python validator.

The modified Python validator, native tests and rendering were NOT_RUN after this change: executor unavailable. Run the existing full validator after integration:

```bash
python3 tools/cockpit-workstation/verify_unified_assets.py
```

No runtime geometry, material, input path, model weight or permission gate changes in this checkpoint.

## Completed render before the outage

The preceding graphics worker completed six stills and one three-second, 60-frame host clip before the executor became unavailable. Its reported source was local commit `d2b9209513330ce044de623f6f243e36fccf5372`, tree `e733500a6764faca87482c44d463e0da59cf6556`, with `sourceStable=true`.

GitHub commit `ef767adf16ea555117a1043a4117129ff8ececdd` in PR #813 has that exact tree; this equivalence was independently checked through the Git commit API. It is the remotely available source for reproduction.

The files and their full manifests were only under `/workspace/scratch/dd6a572f7b68/graphics-cinematic/`. They were not uploaded or committed before the outage and are currently inaccessible. The following hashes were recorded by the preceding worker; they are recovery identifiers, not newly downloaded artifact verification:

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| fold-idle.png | 446040 | 3f35b879cda5b67aeacb1357bc0d2dfc8dcb0a31f2e01ff28bc0102e02e4ee71 |
| fold-transition.png | 448572 | 5232bbb7c7c95919e853309bc1ec0271563fba6b684ac6a6f29e6ec9e98a2671 |
| fold-working.png | 449255 | 2041a2a60179022beb50bf7c1a56a04a818f57891546fdd734016ce321a361b3 |
| phone-idle.png | 379108 | 55a59aa1a094fe476ad574cab5e4b4cbb7bf1d5f30740b09442c7e6508eca179 |
| phone-transition.png | 380667 | d0311bd0fb206ca4192afa19bb0bc612bdcbdfb7c743dce5a86ab3aecbd6774f |
| phone-working.png | 380042 | 360d69411a26f098e377f73b6e5b03be15bc15790e664facf012c5382fe0ff0c |
| fold-monitor-motion.mp4 | 471528 | 627019adb75aa93b0f8f47d394840ffd12df9ab73e074c0add1b2794005c3592 |

Reported clip metadata: 884×830, 3.000000 seconds, 60 frames, 20/1 export cadence. This is NOT device FPS. Fold/phone working stills and clip frame 28 were visually inspected before the outage. The original avatar is in its neutral host pose; no real speech, capture or model action is shown.

## Reproduction

Use a fresh checkout of remote source `ef767adf16ea555117a1043a4117129ff8ececdd` for the completed image source, or the actual new integration SHA for a new source-bound result. Preserve the recorded SHA and manifests for each run separately.

Existing dependencies: JDK 17+, Kotlin compiler libraries, org.json, Android compile jar, Python/Pillow, Blender 4.3.2 and FFmpeg. The script does not download them. Output directory must be empty; the lock path is a shared render lock.

```bash
python3 tools/cockpit-workstation/preview.py \
  --repo-root "$COCKPIT_SOURCE" \
  --output "$COCKPIT_OUTPUT" \
  --kotlin-lib-dir "$KOTLIN_LIB_DIR" \
  --json-jar "$JSON_JAR" \
  --android-jar "$ANDROID_JAR" \
  --samples 32 --video --video-samples 12 --content example \
  --qa-lock "$COCKPIT_QA_LOCK"
```

The successful prior run used denoise=false. The earlier requested OpenImageDenoise run failed because that Blender build did not support the backend. Do not add `--denoise` unless supported and record its actual result; do not silently relabel the undenoised output.

The preview uses packaged production GLBs, compiled Kotlin geometry/ring transforms and the existing Java2D foreground. Blender lighting is not Filament equivalence. Example monitor contents are expressly synthetic. Native Android layout, real MediaProjection, Maya audio, Fold7 frame timing and thermals remain NOT_RUN.

Next integration step: root imports this contract/handoff, executes the validator on the same tree when a Python runtime is available, and regenerates/persists the source-bound visuals. Native QA and APK gates remain independent and mandatory. Rollback: revert this checkpoint; no asset or renderer rollback is required.
