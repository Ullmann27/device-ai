# Source-bound Shizuku host evidence

21 actual bridge lifecycle tests passed using Robolectric Android API28. The five
revocation regression methods fail against the original bridge and pass against the
corrected bridge. These five are a subset of 21, not additional unique tests.
Eight pure parser controls separately passed. No Android device was connected;
Shizuku server calls are shadowed. No full Gradle run is claimed here.

The original baseline Bridge source comes from Cinema c1fdbae (same blob as the
local docs-only 4aef7b1 parent). Current source hashes and baseline hash are retained
in manifest.json. App classes from an already compiled checkout provide unchanged
unrelated types; the actual Bridge, dispatcher, transport codec and capability source
are recompiled. Android manifest/resources are not loaded in this isolated lifecycle
harness. Full application/resource integration remains the root integrator's check.

`replay-host.py` reruns the 21 current lifecycle tests with the recorded dependency
layout; no downloads or Gradle tasks. Set CONTINUUM_SOURCE_ROOT to this checkout,
CONTINUUM_QA_CACHE to the existing cache containing qa-installed-tools, qa-gradle-home
and device-ai/app/build classes, and CONTINUUM_CHECK_OUT to an external directory.
Use the manifest to verify cached app/framework dependency identity before replay.

Normal full-project reproduction after SDK/dependency setup:
`./gradlew :app:testDebugUnitTest -PcontinuumUiWidgetTests=true --tests at.heinz.deviceai.ShizukuCapabilitiesTest --tests at.heinz.deviceai.ShizukuBridgeLifecycleTest`
This Gradle command was not run by this worker. Root's exact-tree result takes priority.

Initial harness problems are recorded rather than counted as product baseline failures:
fixture JVM getter clash (corrected field name), and javac absent from PATH (explicit
installed JDK path selected). The preserved RED is five actual runtime assertion
failures against unchanged production source, not those setup issues.
