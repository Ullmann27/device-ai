# Reference-avatar v2 evidence

Packaged candidate: SHA-256 `70b93bfdfb10c65662f6d37857e4a6a403dfb6ad950a9220acf91cb4145ab145`, 12,552,372 bytes. These are source-bound host results and real GLB renders, with native Android/Fold7 acceptance still open.

| Directory | Evidence |
| --- | --- |
| `provenance-red/` | Original package/NOTICE mismatch: 85/86 JUnit pass, one expected failure in the new provenance test, 26/26 motion pass. |
| `host-final/` | Same unchanged test; compile exit 0, 86/86 JUnit, 26/26 motion, actual Kotlin GLB contract PASS. Summary binds all 22 compiled sources and frozen inputs. |
| `derivation/v1/`, `derivation/v2/` | Candidate manifests and seam maps; v2 includes the baked 2048² atlas. |
| `geometry/` | Geometry/morph/skin/UV review, compressed rejected-v1 asset, six negative controls, historical V1 and separate hinge-V2 outcomes. |
| `renders/v1/`, `renders/v2/` | Real GLB front, quarter, blink and speaking renders with explicit diagnostic poses and image/model hash manifests. |
| `renders/hero/` | Accepted GLB at 768 × 1024, 32 Cycles samples, separate inspection camera. Image hash `faeb8a377179326724404640d295c28bdaa047cbeecf9114c5a9143a51d4fe10`. |
| `motion/final-runtime/` | Fresh source compilation/export and numerical replay of all 108 actual mapper poses on the final GLB; exact equality to earlier video pose inputs. |

Direct UV RED/GREEN comparison: `geometry/v1-red-final-verifier.json` versus `geometry/v2-independent-review.json`. Both use frozen verifier `b70cb4b99835cbc171e9def8c4d15c63e3a2bc30e7f9cea6fe468e2c1528e434` at 2048². V1 has 608 conflicting sampled texels; v2 has zero conflicts and zero degenerate UV triangles. Earlier 1024² evidence remains historical. Finite sampling does not prove absence of every subtexel overlap.

`geometry/final-hinge-v2.json` preserves actual-candidate historical V1 pivot-window **FAIL** and separate V2 **PASS_SEPARATE_HINGE_AND_FIXED_REGION_V2**. The original V1 fixture passes. The transformed diagnostic legacy view is not the actual candidate.

Final pose export SHA-256: `43b1e4e65c5d52de20c0efee60886a17bcd9b30e070286e0fbd20a3d7e10080f`. Its receipt records local HEAD `460730178b4f0a52648462ddb61a0f8737fc1b0a` and explicitly marks changed inputs as uncommitted. Per-file hashes bind the tested working tree; that earlier commit is not claimed to contain the new package. Across 108 frames, maximum control-matrix error is approximately `3.67e-7`; maximum face-position error is `6.44e-8` metres, below unchanged `2e-6` gates.

Raw command/log archives preserve exact bytes with hash manifests. Original absolute paths describe the observed run; portable commands and dependencies are in `tools/avatar-3d/reference-atlas/README.md`. JARs and temporary Blender scenes are excluded. Source, generated input, official baseline commit and review scripts support reproduction.

The PNG in `assets/avatar/reference/` is **generated 2D artwork used as input**. Images in `renders/` are **actual host renders of the exported GLB**. Native renderer checks and owner likeness review remain separate.

## Delivered animation preview

`animation/` preserves the validated eight-second host preview receipt: 96 frames at 12 fps, H.264 384 × 512, no audio, 1,434,559 bytes; video SHA-256 `657e63521b925f7919de28552ea1bedea287545d9a2a65aaec892fb699690f87`. Root checked all 96 frame hashes, frame count and ffprobe metadata, and visually reviewed frames 0, 54 and 95. The video and frame PNGs are separate deliverables and are not duplicated in Git. The compact compressed frame manifest is preserved. The original render command lacks the later recommended `--python-exit-code 1`; validation relies on the completed manifest, exact frame hashes and encoded-video probe, not a claimed fail-closed process exit. The video uses parent pose export `5cf3394d41c3ccfdd8c70016bc819acd8ee705197e0ad337076920db8174fb51`; `motion/final-runtime/parent-pose-comparison.json` proves exact equality of all frame inputs and camera focus with the final source export.
