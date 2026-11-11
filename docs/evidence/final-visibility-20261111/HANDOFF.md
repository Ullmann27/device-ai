# Final visibility continuation — WIP

Baseline #740 e2350618. Own branch integration/continuum-final-visibility-20261111.

Confirmed runtime defect: default-enabled EXP15 overwrote the existing coolInferenceLighting decision. Preserve that resolved field while retaining the same frame/particle/segment caps. Warm/unknown thermal state, reduced motion, battery saving and background still reduce illumination. No new renderer, bus, memory, executor, asset or permission. EXP-11 compatibility unchanged.

Native isolated Kotlin 2.2.21/JDK21 + JUnit4.13.2: 9 tests; baseline 2 FAIL, patched 9 PASS. This is not the Android Kotlin2.4 build. Reproduce by compiling JarvisRenderQuality.kt and JarvisRenderQualityTest.kt with JUnit, then JUnitCore at.heinz.deviceai.ui.JarvisRenderQualityTest. Raw red/green logs retained.

Android preflight failed before compilation: Gradle wrapper network unreachable; previous SDK/cache absent. Environment restoration in progress. Full Android, lint, new screenshots and new APK NOT_RUN. Previous APK source remains 3ae6e225; it does not contain this correction. No main merge, signing or publication.

connections.json is an initial source-path matrix with explicit remaining gaps, not a completed full audit. #746 is evidence-only; Claude head equals baseline. Do not manufacture before/after images or import unpublished work. Next: restore Android toolchain, execute full gates and native captures, then continue caller audit. Rollback: revert this isolated quality-policy change.

## Executed checkpoint

Remote runtime d92f888520e66a896d6e383d6999bf06b21b5ab9, same tree as local cb0827fd. Full Node: 2456 PASS, 37 SKIP, 0 FAIL. Backend syntax PASS; after installing locked dependencies, 64 PASS/0 FAIL. Initial missing-express failure retained separately. Isolated quality tests 9 PASS with two prior failures retained.

Gradle 8.11.1 and official Android platform archive downloaded successfully through HTTPS, but Java wrapper could not access the network. Offline Gradle confirms AGP 8.10.0 missing. A distinct explicit-proxy online attempt timed out after 90 seconds before compilation; no Gradle daemons remain. No repeated full app tests were claimed. Android compilation, Android suite, configured lint, fresh native images and new APK remain BLOCKED/NOT_RUN. The overall implementation request is NOT COMPLETE. Existing APK remains on source 3ae6e225 and does not contain this fix.

The actual lighting consumers are FilamentAvatarRenderer.applyCoreLight and applySurfaceRadiance, both gated by !reducedQuality. ContinuumHologramView feeds setQuality(renderBudget().reducedBloom). This confirms the overwritten flag reaches visible lighting; hardware intensity and thermal measurements remain unverified.
