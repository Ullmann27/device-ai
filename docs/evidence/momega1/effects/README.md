# Actual-work effects evidence

Transient compiled JAR/class files are excluded from Git; their hashes remain in
the manifest. Original raw commands, source blobs and dependency pins are kept.

Candidate `ca429fd81007473f1b0c0e66be6112f78f652613`, based on
`ebefd45ab7eef88a47cf8f66872885da6ab20382`. See
[handoff](../../../../research/omega/momega1/TASK-EFFECTS-HANDOFF.md).

Final raw result: **63 tests PASS, zero failures/errors/skips**, unchanged
before/after source and Gradle exit 0. `raw/effects-final-ca429fd8` includes the
actual runner, filter, XML, logs, source receipts and all 36 task PNGs plus 12
regression captures. `manifest.json` binds source and evidence hashes.

`worker-read-primary.png` compares synthetic WORKER arrivals and matching Read
flights at phone412×307, Fold410×291 and compact chat412×88/160.
`worker-read-small-wide.png` adds412×120 and720×200. Input PNG pixels are not
scaled, only alpha-composited over the existing app background with outside
captions. These are production Android Canvas/Skia host captures with synthetic
inputs, not a device, real backend action, native Filament screenshot or heard
audio result. The prior overlapping688 contact and full raw result remain in
`before-read-transition-688b05e3` / `raw/effects-final-688b05e3`.

All functional REDs, the missing short-native-write shadow limitation, protected
gaze baseline failure and baseline+fixture-only GREEN are preserved under `raw`.
Earlier diagnostic geometry captures are represented by their SHA-256 lists;
they are not reference acceptance images. The transition RED used actual pure
production helper/Painter on official Kotlin/JUnit, without Android stubs.

The17 independent snapshots are byte-exact, attributed review fixtures. Their
native audio seam supplies only Robolectric's missing short-array write; public
Android constructor/write-state/play/stop/release behavior remains tested. This
controlled native seam is explicitly not speaker/device evidence.

Actual execution used the provisioned JDK17/Gradle8.11.1/SDK36 and JDK21 test JVM,
two CPU affinity cores and max-workers2. Contact composition used one CPU. From
a docs-only descendant with app source unchanged, reproduce the focused suite:

```sh
source /workspace/momega1-build-env/env.sh
gradle --no-daemon --max-workers=2 --console=plain \
  -I docs/evidence/momega1/effects/reproduce.init.gradle \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=2' \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true \
  -PcontinuumUiEvidenceDir=/tmp/continuum-effects-evidence \
  -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest
```

No full-suite, lint, release, install, physical audio/GPU or full-app caller
acceptance is inferred from this focused result. Root owns the separate caller.
