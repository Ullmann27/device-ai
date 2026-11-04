# Read-only landscape target diagnostic — existing Float boundary failure

## Executed result

The original `everythingFitsOneScreenWithUsableTargetsAndNoOverlap` JUnit method
was rerun in an isolated JVM and reproduced its original assertion failure at
832 x 700 dp, font scale 1.0. JUnit: **1 test, 1 failure**, process exit 1.
A separate geometry probe of the actual layout classes exited 0.
The Java diagnostic compiled with JDK17, exit 0.

No production file, original test, assertion, viewport or threshold was edited.
No Gradle/Android build, APK, pixel capture or device test was started by this audit.

## Source binding

- Local executed Android source: `ec2543bef908d7d346a9767f9b095e84254090d1`.
- Full source tree: `ed4984ba9ac873e081a66d7a7be871f029ef5be5`.
- Frozen C1 local baseline: `e19bf2768dc24b7c2ea1040538fdd661269a540c`.
- Its public equivalent: `b04daf5db2cbe40a9fa757499a0d295176a1bfce`.
- Published equivalent of the executed source: `3704b62585c6ce6881ab0dfe0c9947cddd1531c2`.
- Original failed Android run: [result.json](../android-gates/full-unit/result.json).

The diagnostic copied 304 actual compiled UI classes and the original compiled
JUnit class from the completed source-bound Android compilation into an isolated
read-only input snapshot. Every copied class was hashed before/after copying
and byte-matched to the original compiled class file. The diagnostic fixture itself was newly
compiled. This was not a fresh recompilation of the 304 production classes and
must not be described as another full Android gate.

All nine relevant production/helper/test source blobs match the frozen C1
baseline exactly; see `manifest.json`. The checkout remained clean and at the
same HEAD throughout the diagnostic. No Android substitute or alternate layout
implementation was used by the geometry probe.

## Exact geometry and cause

The three presentation controls have these actual vertical coordinates:

| Value | Actual Float value |
| --- | --- |
| top | 51.487094879150390625 |
| bottom, computed as top + 48f | 99.487091064453125 |
| stored height, bottom - top | **47.999996185302734375** |
| deficit from 48 dp | **0.000003814697265625 dp = 2^-18 dp** |
| height in hexadecimal Float notation | `0x1.7ffffep5` |

VIEW_AVATAR and VIEW_ORB are about 98.818145752 dp wide; VIEW_COMPACT is about
98.818115234 dp wide. All three share the same failing height. These are the
only strict target failures in the ten existing viewport/font combinations
enumerated by the diagnostic.

The production expression is in `ContinuumLandscapeCockpitLayout.kt`:
`Box(l, avatar.top, l + pillWidth, avatar.top + TARGET)`, where TARGET is 48f.
`ContinuumHomeLayout.Box.height` returns `bottom - top`.

The exact real-number sum of the represented top and 48 is halfway between
two representable Float bottom coordinates. Rounding the addition selects the
lower bottom coordinate. The subsequent subtraction therefore produces the
Float immediately below 48. The original test compares both dimensions with
a strict `>= 48f`; it has no numerical tolerance for that target assertion.

This identifies the numeric geometry-contract boundary, not an observed
physical hit-target or rendering defect on Fold7. The assertion remains a real
unresolved gate failure.

## Ownership / newer-source check

C1 owns serial integration of the common app. The existing layout/design
workstream is #491. This audit does not duplicate or apply a UI repair.

`latest-source-comparison.json` records full text comparison of the production
layout and original test against these pinned sources; all twelve comparisons
are identical:

- #491: e101e393dd6c80803ac9d9ac91fccd9812c4a18d
- #505: e83f6ac18243b42aadbe8da88df074c7ea1811ef
- #513: 28b228698aef8c52e46c72b71fdd5e1915d6e76c
- #519: a54583816df92bcd0cdf4e75f06cdefffb5dafcc
- #520: 9094032b05257ef12f882e37cefd7dd8f7ebc203
- #521: 653a885435d95a19f341a80a89be1e9fbeccf1f1

The newer #520 repair concerns AvatarIdleMotion's negative-zero boundary.
The #521 work concerns the packaged GLB/atlas. Neither reviewed delta describes
a repair of this layout target. No already-fixed layout revision was identified
within this inspected scope.

## Actionable handoff

Preserve the original 48dp assertion and all original viewport cases. The UI
owner can make the coordinate construction guarantee a representable height
of at least TARGET (for example, outward-round only when subtraction would
undershoot). Any chosen production correction still needs its own unchanged
JUnit RED/GREEN and complete common gate; no such correction was applied here.

The full Android unit suite must remain FAIL until that separate repair is
integrated and validated. Root's later bounded Activity/Handler and configured
NewApi-lint PASS results remain distinct evidence and do not erase this failure.

## Retained files

- `original-junit.log`: actual unchanged JUnit method and failure.
- `geometry.tsv`: actual boxes for all ten original viewport/font combinations.
- `manifest.json`: source/class/dependency hashes, executed commands and exit codes.
- `latest-source-comparison.json`: pinned-source comparisons.
- `LayoutDiagnostic.java` and `run_diagnostic.py`: actual diagnostic harness.
- `float-arithmetic.json`: arithmetic-only boundary calculation, with no
  production change or candidate-PASS claim.

The original nine source copies and 305 compiled input class files remain
diagnostic workspace inputs; they are not included in this published packet.
Their hashes are preserved in the manifest. Recreate the classes from the
pinned source above when reproducing this diagnosis elsewhere. The frozen
runner retains the exact original workspace paths and expected local commit;
it is an execution record, not a portable auto-installer. No dependency jars,
build cache, APK or foreign working tree is part of this packet.

The audit made no production or test-source edits. Root subsequently added this
evidence and its handoff on the isolated memory branch.
