# Jarvis55 graphics integration

Base: #811 `8a882e662cb48e3363213eb5005cb091828d89ec`.
Integration target: `work/jarvis55-master-20261113`, #812, then #809.

## Connected changes

- Existing three monitor meshes gain chamfer depth/normals and layered glass.
  Titanium edge/base materials are calibrated separately; glass and coated
  shells use the existing glTF clearcoat path. No second renderer.
- Source seed remains below its original 32768-byte bound (32716 bytes).
  Packaged workstation is 52228 bytes. Still 32 seed renderables / 37 draw
  primitives. Original avatar and five-ring orb bytes are unchanged.
- Live surface positions, UVs, source aspect mapping and input transforms
  remain unchanged. Geometry checks reject obstructed apertures and lost
  clearcoat during Blender import/export.
- Eight-button glass navigation again shows pointer-bound pressure rims.
  The existing pressure clock is used; reduced motion and thermal floors
  keep feedback static. No new timers or Android actions.
- Old tests referenced `system`, which now lives in the header More menu;
  route tests now use the actual `device` dock target. Assertions remain.

## Verification

`docs/evidence/jarvis55-graphics-20261113` records exact asset hashes and logs.
With the corrected dock target, original production failed 5 of 10 JVM
contracts. Repaired production passed 10/10; an additional pointer/disabled
contract passed in the final 11/11 run. Kotlin 2.0.20, JUnit 4.13.2, native JVM;
not Robolectric or a device result.

Both original and candidate asset snapshots rendered Fold/Phone idle with
Blender 4.3.2, 8 samples and stable source hashes. The visual change is a
material/edge refinement, not completion of the requested Hollywood scene.
HOST previews use labelled synthetic content and are not live Android
captures. The recorded manifests bind their production source/asset hashes.

Reproduce JVM checks:

```sh
python3 tools/cockpit-workstation/run_contract_tests.py \
  --kotlin-lib-dir "$KOTLIN_LIB_DIR" --android-jar "$ANDROID_JAR" --output "$QA_OUTPUT"
```

Add `--baseline 8a882e66` to compile the prior two button production sources
against the current test targets. Follow `tools/cockpit-workstation/README.md`
for the source-bound HOST image pipeline. Rebuild meshes with `build_scene.py`,
then Blender `build_unified_assets.py -- --workstation-only`, then run
`verify_scene.py` and `verify_unified_assets.py --write-manifest`.

## Limits and next step

Native full QA, Filament GPU appearance, folded-device readability, live
capture, thermals, frame time and all speech/motion integration: NOT_RUN here.
Clearcoat is an existing supported asset feature; anisotropy was not added
without verified version-specific import support. No weights/permissions
changed. EXP-06/07/11/12 boundaries retained. Integrate into #812, run native
QA and inspect the current APK on Fold7 before declaring device verification.
Rollback is a revert of this graphics commit; originals remain intact.
