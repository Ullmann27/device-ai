# Android MΩ-1 contract evidence

Final production/test freeze: `037370f0544d3ed2ee7686082c41c78046daa64d`.
Classification: CONTRACT_TEST, not native inference, Termux device execution, APK or release acceptance.

`manifest.json` records each exact RED/GREEN source, test totals/exit, before/after source-receipt hashes and final candidate file hashes. The QA lane retains the complete indexed-source receipts; the digests here bind those unchanged receipts. Each directory contains raw Gradle logs, JUnit XML and the actual exit code. `green-final/test-filter.init.gradle` is the actual eight-class QA init, including the independent external test directory and JDK21 Test JVM; Gradle itself used pinned JDK17/Gradle8.11.1/SDK36 from the QA environment.

The two Kotlin classes in `independent-snapshots/` are byte-exact independent reviewer work, authored by `/root/momega1_android_review`, frozen before inspection (`4556be48`; test commits `a3e257a1` and `efa03299`). Their hashes match the recorded QA inputs. They are not authored feature-lane tests. `green-final/reproduce.init.gradle` changes only their source-directory path to the committed relative snapshot directory, allowing the same cases to be rerun from this checkout. The actual raw init remains separately preserved.

Actual final command, run from the isolated Android worktree after sourcing QA's environment:

```sh
source /workspace/momega1-build-env/env.sh
gradle --no-daemon --max-workers=2 --console=plain \
  -I docs/evidence/momega1/android/green-final/reproduce.init.gradle \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true \
  -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest
node --test scripts/continuum-typed-coding-bridge.test.mjs
```

The original Gradle invocation used the preserved `test-filter.init.gradle` at the QA evidence path; the command above substitutes the equivalent relative-path reproduction init. The Node entry imports the wiring tests itself; invoking both entries double counts four tests. Final result: Gradle45/45 (32 authored +13 independent), Node15/15 plus65/65 adjacent contracts, no skipped tests. SDK/tool provisioning receipts belong to QA; no device was installed or tested here.

The rejected mixed-source first attempt and corrected dialog-message fixture history are documented in ANDROID-HANDOFF.md and retained in the QA lane. The RED directories here use the corrected fixture with actual dispatch observations. Real LiteRT/Termux/Fold7/inference/energy/lifecycle acceptance remains NOT_RUN.
