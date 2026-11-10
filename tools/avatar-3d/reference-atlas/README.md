# Reference atlas and production-pose review

The pipeline bakes the committed generated frontal artwork onto the existing animated mesh. The output is a real GLB with embedded texture, the original rig and all 21 morph targets. A single frontal image does not establish an exact reconstruction of the face or unseen profile.

Accepted package: `app/src/main/assets/avatar3d/continuum-avatar.glb`, SHA-256 `70b93bfdfb10c65662f6d37857e4a6a403dfb6ad950a9220acf91cb4145ab145`, 12,552,372 bytes. The builder retains the historical output filename `continuum-reference-head-v1.glb`; the accepted revision is identified by this hash and the v2 evidence directory.

## Inputs and environment

Run these Bash commands from the repository root. The recorded build/render environment is Blender 4.3.2, Cycles CPU. The independent verifier needs host Python 3, NumPy and SciPy. Fresh production-pose export needs JDK 17, Kotlin 2.4.0 and `org.json:json:20240303`. These scripts call no generation API or external service.

| Input | SHA-256 |
| --- | --- |
| Baseline GLB from official commit `28b228698aef8c52e46c72b71fdd5e1915d6e76c` | `5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3` |
| `assets/avatar/reference/continuum-neural-front-20261104.png` | `0ca742ef92bb9db1bd012df7677594c37b58e77c40e4fc92a946e8e0fb886b1e` |
| Builder | `159a64f34c392170d738e55c917ab91415005ace1b4107063653e0ad59fb914e` |
| Diagnostic renderer | `d7b90d4997033279fb3c54078f220a5717735df10209d78d095a72bd60a0d66b` |
| Independent verifier | `b70cb4b99835cbc171e9def8c4d15c63e3a2bc30e7f9cea6fe468e2c1528e434` |
| Kotlin pose exporter | `bf40bfc73bc3718048fec520823f9b2a110948b2f45c3cbbd5f0d56b0b431892` |
| Production-pose replay | `0bc1273ddf7104e28f611a17afa043de90ba4e92a8def4aeca39ee023cad42ee` |

The original user image is identified by hash in the PNG provenance. Rebuilding uses the committed **generated 2D artwork**, not another image-generation call. Mesh CC0 provenance and generated-texture provenance are separate in NOTICE; no CC0 license is asserted for the generated pixels.

The official base commit preserves the exact source GLB. The local execution twin `14bc1edcb7f9076f28dba0d4029c19425c3d3013` contains those same bytes; ordinary GitHub checkouts should use the official commit below.

## Rebuild and inspect

```bash
set -euo pipefail
avatar_rebuild_dir=$(mktemp -d)
git show 28b228698aef8c52e46c72b71fdd5e1915d6e76c:app/src/main/assets/avatar3d/continuum-avatar.glb > "$avatar_rebuild_dir/baseline.glb"
blender -b -t 2 --python-exit-code 1 \
  --python tools/avatar-3d/reference-atlas/build_reference_avatar.py -- \
  "$avatar_rebuild_dir/baseline.glb" \
  assets/avatar/reference/continuum-neural-front-20261104.png \
  "$avatar_rebuild_dir/built" --size 2048
sha256sum "$avatar_rebuild_dir/built/continuum-reference-head-v1.glb"
python3 tools/avatar-3d/reference-atlas/verify-reference-candidate.py \
  "$avatar_rebuild_dir/baseline.glb" \
  "$avatar_rebuild_dir/built/continuum-reference-head-v1.glb" \
  --output "$avatar_rebuild_dir/review.json" --uv-size 2048 --negative-controls
```

The builder checks both input hashes. It duplicates seam vertices while mapping protected geometry, morph and skin values, and isolates 189 face triangles into separate UV tiles. The accepted 2048² atlas hash is `92d5adebe1e794cfbed434ac533f53dda25053177129a399e5eaf0c07c79533b`. Compare rebuild hashes and verifier output with the committed manifests; a different environment is not assumed to produce byte-identical baked pixels without checking.

The verifier compares oriented triangles, normals, all 21 morph payloads, joint influences, weights, inverse bind matrices and node transforms. The UV check samples strict triangle interiors at finite raster resolution: no sampled conflicts does not prove absence of every subtexel or continuous overlap. All six deliberately damaged controls must be rejected. To check the actual package, use `app/src/main/assets/avatar3d/continuum-avatar.glb` as the second argument.

Reproduce the rejected v1 result with the same frozen verifier and 2048² sampling:

```bash
gzip -dc docs/evidence/reference-avatar-v2-20261104/geometry/rejected-v1.glb.gz \
  > "$avatar_rebuild_dir/rejected-v1.glb"
if python3 tools/avatar-3d/reference-atlas/verify-reference-candidate.py \
    "$avatar_rebuild_dir/baseline.glb" "$avatar_rebuild_dir/rejected-v1.glb" \
    --output "$avatar_rebuild_dir/rejected-v1-review.json" --uv-size 2048; then
  printf '%s\n' 'Unexpected acceptance of the rejected v1 asset' >&2
  exit 1
fi
```

Recorded v1: preserved geometry, 608 conflicting sampled texels. Accepted v2: zero sampled conflicts and zero degenerate UV triangles. The rejected asset and archive hashes are in `geometry/rejected-v1-archive.json`.

## Actual GLB renders

These diagnostic views set explicit test poses. They demonstrate the exported model and material, not an Android session or actual TTS callback.

```bash
blender -b -t 2 --python-exit-code 1 \
  --python tools/avatar-3d/reference-atlas/render_reference_avatar.py -- \
  app/src/main/assets/avatar3d/continuum-avatar.glb \
  "$avatar_rebuild_dir/renders" --width 384 --height 512 --samples 16
blender -b -t 2 --python-exit-code 1 \
  --python tools/avatar-3d/reference-atlas/render_reference_avatar.py -- \
  app/src/main/assets/avatar3d/continuum-avatar.glb \
  "$avatar_rebuild_dir/hero" --width 768 --height 1024 --samples 32 \
  --views quarter --ortho-scale .35 --target-y .246 --quarter-yaw -20
```

Manifests bind model hash, pose, camera and image hash. The hero uses a closer inspection camera; the Android camera was not changed. Blender lighting and compositor glow do not establish native Filament appearance or performance.

## Replay preserved current-source poses

The final export was compiled from actual app sources after the package/hash swap. Its receipt explicitly records an uncommitted working-tree snapshot at the prior local HEAD and binds every relevant source, package and NOTICE by SHA-256. The 108 frames comprise 12 diagnostic inputs and 96 deterministic idle samples.

```bash
blender -b -t 1 --python-exit-code 1 \
  --python tools/avatar-3d/reference-atlas/render-production-poses.py -- \
  --asset app/src/main/assets/avatar3d/continuum-avatar.glb \
  --poses docs/evidence/reference-avatar-v2-20261104/motion/final-runtime/actual-mapper-poses.json \
  --output "$avatar_rebuild_dir/production-replay" \
  --expected-asset-sha256 70b93bfdfb10c65662f6d37857e4a6a403dfb6ad950a9220acf91cb4145ab145 \
  --expected-poses-sha256 43b1e4e65c5d52de20c0efee60886a17bcd9b30e070286e0fbd20a3d7e10080f \
  --frames all --no-render
```

Remove `--no-render` for actual images. `--frames idle` selects the 96 idle samples. Control-matrix tolerance is `2e-6`; face skin-position tolerance is `2e-6` metres. Always retain `--python-exit-code 1`: a Blender Python exception otherwise need not produce a nonzero process exit.

### Fresh source export

Set `CONTINUUM_AVATAR_JVM_DEPS` to an existing directory containing compiler dependencies. The recorded exporter used `kotlin-compiler-embeddable-2.4.0.jar`, `kotlin-daemon-embeddable-2.4.0.jar`, `kotlin-build-tools-api-2.4.0.jar`, `kotlin-stdlib-2.4.0.jar`, `kotlin-script-runtime-2.4.0.jar`, `kotlin-reflect-1.6.10.jar`, `kotlinx-coroutines-core-jvm-1.8.0.jar`, `annotations-13.0.jar` and `json-20240303.jar`. No dependency download is performed. Use JDK 17 on `PATH` and a clean checkout so its commit identifies the compiled sources.

```bash
: "${CONTINUUM_AVATAR_JVM_DEPS:?Set this to the existing local JVM dependency directory}"
avatar_repo_root=$(git rev-parse --show-toplevel)
avatar_export_commit=$(git rev-parse HEAD)
avatar_export_dir=$(mktemp -d)
avatar_runtime_cp="$CONTINUUM_AVATAR_JVM_DEPS/kotlin-stdlib-2.4.0.jar:$CONTINUUM_AVATAR_JVM_DEPS/json-20240303.jar:$CONTINUUM_AVATAR_JVM_DEPS/annotations-13.0.jar"
java -Xmx384m -cp "$CONTINUUM_AVATAR_JVM_DEPS/*" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$avatar_runtime_cp" \
  -d "$avatar_export_dir/mapper-export.jar" \
  app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt \
  app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt \
  app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt \
  app/src/main/java/at/heinz/deviceai/avatar3d/CriticalDampedSpring.kt \
  app/src/main/java/at/heinz/deviceai/avatar3d/GazeBlinkPolicy.kt \
  app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt \
  tools/avatar-3d/reference-atlas/ExportAvatarReviewPoses.kt
java -Xmx384m -cp "$avatar_export_dir/mapper-export.jar:$avatar_runtime_cp" \
  ExportAvatarReviewPosesKt "$avatar_repo_root" "$avatar_export_commit" \
  "$avatar_export_dir/actual-mapper-poses.json" 96
sha256sum "$avatar_export_dir/actual-mapper-poses.json"
```

Use the new pose-file hash in the replay command. Export metadata contains the source commit, so its file hash can differ despite identical frames. Committed `parent-pose-comparison.json` confirms equality of all 108 frame inputs and camera focus between the earlier video export and final package export.

## Tests and separate hinge contract

The unchanged provenance test first reproduced the stale NOTICE failure and then passed after the GLB/hash/NOTICE swap. The final host run compiled 22 Kotlin files: 86/86 JUnit cases, 26/26 motion checks and actual Kotlin GLB preflight PASS. Original commands/logs are archived under `host-final/`; this is not a claim of an Android Gradle build.

The existing separate hinge-V2 review is reproducible with:

```bash
python3 tools/jarvis-reference-review/validate_hinge_v2.py \
  tools/epsilon-avatar-contract/validate_glb.py \
  assets/avatar/jarvis-runtime-v1.glb \
  app/src/main/assets/avatar3d/continuum-avatar.glb \
  "$avatar_rebuild_dir/hinge-v2.json"
```

The actual candidate still fails the historical V1 Eye/Jaw pivot window. The functional hinge/fixed-region V2 contract passes separately; its diagnostic legacy view is not the actual candidate. Neither result establishes measured human anatomy, native pixels, audible synchronization, device performance or owner acceptance of likeness.

Handoff: `docs/research/omega/REFERENCE-AVATAR-V2-HANDOFF-20261104.md`. Evidence index: `docs/evidence/reference-avatar-v2-20261104/README.md`.

### Current Cinema replay contract

Pose schema v2 includes camera azimuth, separate Neck angles and residual Head angles. The replay hides the same tooth-prefix nodes as Filament and checks every imported face vertex against canonical glTF skinning. V1 exports are rejected: silently treating their missing neck channels as zero would misrepresent the current renderer. Studio lighting/framing remain Blender host evidence, not Android Filament pixels.
