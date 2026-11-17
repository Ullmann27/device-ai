# Video reference review — overnight integration

Source freeze: `0ff2cef550db8fc84c32690793921edf3a7830e9`.
Reference folder: [owner's JarvisVideo folder](https://drive.google.com/drive/folders/1VVOY4AbD_aTJE2zlI8MlJXH_SjGD4uhK).

Three recent video copies retrieved during this session were inspected through sampled contact sheets. The hashes below identify the actual inspected files; the private videos and contact sheets are not committed to this repository. Container metadata was independently read with `ffprobe`; the frame selection is not a full motion or audio acceptance.

| Inspected copy | Duration | Video | Bytes | SHA-256 |
|---|---:|---|---:|---|
| latest-1.mp4 | 20.032 s | H.264, 1280 × 720 | 15063998 | c73ce6cf204fe2c29cc572da80514c7a2c8657a411c049255a476647bfe7c878 |
| latest-2.mp4 | 20.032 s | H.264, 1280 × 720 | 12170661 | 7bd81c97a8c6d1a8e84b776461bbb454dcb4368777c6ecf97a90c1be7d10ea1d |
| latest-3.mp4 | 10.026667 s | H.264, 1280 × 720 | 6721417 | a9c525346b200c2aa062ff9d2c7495f8492fbaa9016a4cf125364dda77b35893 |

## Observed visual direction

The samples contain a translucent cyan/violet human head and upper torso, orbit lines, a luminous orb, dark glass panels, restrained bright outlines, task/status panels and document/work effects. Head and orb appear together in some compositions. Reference text and depicted service logos do not establish real service connections or system capabilities.

The integration therefore preserves the reviewed PR410 avatar rig, head/eye motion, mouth coupling, restrained bloom, orb fallback and ordinary phone/Fold composition. It does not promote an unreviewed replacement asset or infer permission to replace the complete home layout. The adaptive PR456 layout is used for enlarged text or narrow windows as documented in [LAYOUT-DECISION.md](LAYOUT-DECISION.md).

## Actual host verification

The final Android run includes `IndependentFullLayoutUiTest`, `IndependentFinalVisualPolishUiTest`, the existing avatar lifecycle/contracts and the new adaptive navigation regressions. Six Android Canvas captures cover phone 412 × 915, wide Fold 840 × 649 and portrait Fold 840 × 1050, each in home and chat. The earlier independent comparison found the reference HomeView/HologramView/element geometry and visibility preserved; new adaptive navigation tests confirm the same avatar instance and intended geometry survive the transitions. The complete final JUnit archive and counts are recorded with the local evidence for this source.

## Acceptance boundary

These are sampled reference observations and host Android/Robolectric checks. They do not prove exact video likeness, lip synchronization against complete audio, physical Fold7 rendering/performance, or native Filament GPU output. No new native model run was used to generate or validate the effects. S01 and its unresolved execution barrier remain separate.
