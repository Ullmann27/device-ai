# Native avatar host evidence — 2026-11-03

Feature source checkpoint: `5a460874088a85e0b16a5fcd1b1fb4f58a9841da`. Baseline: `b4f61ae9765e26dd401db928b76fa9f233c0742a`. Native source lives in PR #395. These are host observations, not Fold7 measurements.

- `jvm-baseline-red.log`: exact new AvatarExistingContractRegressionTest against unmodified baseline AvatarContract.kt and AvatarDirector.kt: 3 tests, 3 expected failures.
- `jvm-green.log`: same regression + AvatarNativeRegressionTest + original AvatarDirectorTest/AvatarPortedPartsTest against feature: 14 tests, zero failures.
- Six PNGs use the production ContinuumAvatarRenderer through the repository's Java2DContinuumPainter at phone 412×307 and Fold slot 410×291 logical sizes, exported 2×. Synthetic idle/blink/speech poses, no real audio recording, camera, device FPS or learned states. Source harness: tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/ContinuumAvatarPreview.kt.

Host: OpenJDK 17, Kotlin compiler 2.4.0, JUnit 4.13.2. Command shape (dependency directory provisioned by Quality, no downloads performed by these commands):

```bash
cp=$(find /workspace/continuum-build-env/jvm-deps -maxdepth 1 -name '*.jar' -printf '%p:')
java -Xmx512m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" -jvm-target 17 -d /tmp/avatar-classes \
  app/src/main/java/at/heinz/deviceai/avatar/*.kt \
  app/src/main/java/at/heinz/deviceai/ui/ContinuumPainter.kt \
  app/src/test/java/at/heinz/deviceai/avatar/*.kt
java -cp "/tmp/avatar-classes:$cp" org.junit.runner.JUnitCore \
  at.heinz.deviceai.avatar.AvatarExistingContractRegressionTest \
  at.heinz.deviceai.avatar.AvatarNativeRegressionTest \
  at.heinz.deviceai.avatar.AvatarDirectorTest at.heinz.deviceai.avatar.AvatarPortedPartsTest
# For preview also compile ContinuumFonts.kt, Java2DContinuumPainter.kt and ContinuumAvatarPreview.kt,
# then run at.heinz.deviceai.ui.ContinuumAvatarPreview <repo-root> <output-directory>.
```

Android native Canvas/lifecycle tests are separate (`ContinuumAvatarNativeUiTest`); their status is tracked in the handoff. Full Activity/model/real TTS, Fold7, APK, installation, thermal/battery performance: NOT_RUN at this evidence checkpoint.
