# Packaged avatar: bounded independent connection check

Source observed: `5e2a0c2b26d1e2978b19d70878f30c98c36d94e9` (integration parent
`e101e393dd6c80803ac9d9ac91fccd9812c4a18d`). Comparison: helper PR495 head
`6cf6e2128463e6a24dfac4652bb65e325ff6f18a`.

## Actual executions

The requested `tools/avatar-3d/validate_glb.py` path does not exist. The existing
canonical Python checker is `tools/epsilon-avatar-contract/validate_glb.py`.
Its complete source was read before execution; it uses only the Python standard
library. Neither source, fixture, contract nor asset was changed. Python was
run with `-B` to avoid writing bytecode into the source checkout.

The packaged GLB is **byte-identical** to PR495: 10,165,644 bytes, SHA-256
`5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`.
Both Git asset blobs are `3d58ca55faa680d0e5d1108b83ceb63a81ce5541`.
The validator is also unchanged from that helper checkpoint.

1. The unmodified Python checker exits **1 / FAIL** at
   `rig_hierarchy_orientation`: `Eye/Jaw pivots outside preregistered metre-scale bust anatomy`.
2. Its existing eight synthetic unittest controls pass, exit **0**. This is
   checker/fixture coverage, not a PASS for the actual asset.
3. Pivot measurements use the checker's actual `Glb.world` parent-TRS composition
   and `transform` on the local origin. Local and world coordinates are retained
   in `pivots.json`, without normalizing or moving any pivot.

| World measurement | Actual | Unmodified legacy range |
| --- | --- | --- |
| Mean eye Y | 0.2884144186973572 m | 0.25–0.32 m |
| Jaw Y | 0.26699999809265135 m | 0.20–0.25 m |
| Eye minus jaw Y | 0.02141442060470583 m | 0.04–0.09 m |
| Eye separation | 0.06102388724684715 m | 0.01–0.15 m |

The failing values are jaw height and eye-to-jaw vertical distance; the eye
height and separation meet those individual ranges. After this hierarchy
failure, the checker returns early. Its later morph-localization, skin-rotation
and resource-budget checks therefore are **not executed** for this asset in
this run. A failure at the old contract must not be described as all later
geometry tests failing.

## Interpretation and remaining boundary

The evidence establishes a mismatch with the unchanged legacy numeric contract.
It does not, alone, identify a corrupted GLB or an Android loader rejection.
Source inspection of `AvatarGlbContract.kt` confirms that the Kotlin runtime
preflight does not enforce these old numeric pivot windows. The existing avatar
owner handoff documents that the higher jaw hinge was a later correction with
neutral-preserving compensation of rigid children; it specifically warns
against undoing that change just to satisfy the old region landmark.

The repository also contains `validate_hinge_v2.py` and a historical semantic
resolution receipt at `docs/evidence/momega1/reference-review/hinge-v2-final-receipt.json`.
That receipt explicitly applies to asset SHA `6b199c5989640b516b8c45401ececeadb8ed1baae1d1e1ab5db20d19e966aa1e`,
not the currently packaged `5f49...` asset. Its PASS is **not transferred** here.
No fresh V2, Kotlin runtime, renderer, real speech or device execution was
performed by this helper. The source-backed distinction between an old landmark
contract and the real articulated hinge remains necessary; no range was widened
and no asset replacement is proposed by this check.

## Raw files and reproduction

- `summary.json`: exact source, Git blobs, SHA-256, commands and exit codes.
- `validator-result.json`, `validator-stdout.txt`, `validator-stderr.txt`: actual
  canonical validator result without edits.
- `validator-self-tests.txt`: all eight original self-test identities/outcomes.
- `pivots.json`: actual local/world transforms and the unchanged numeric ranges.

From the named source checkout:

```sh
python3 -B tools/epsilon-avatar-contract/validate_glb.py \
  app/src/main/assets/avatar3d/continuum-avatar.glb --output /tmp/avatar-contract-result.json
python3 -B -m unittest discover -s tools/epsilon-avatar-contract \
  -p test_validate_glb.py -v
```

No Gradle job, asset edit, production-source change or external dependency
installation was required for this bounded check.
