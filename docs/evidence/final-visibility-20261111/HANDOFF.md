# Final visibility continuation — WIP

Baseline #740 e2350618. Own branch integration/continuum-final-visibility-20261111.

Confirmed runtime defect: default-enabled EXP15 overwrote the existing coolInferenceLighting decision. Preserve that resolved field while retaining the same frame/particle/segment caps. Warm/unknown thermal state, reduced motion, battery saving and background still reduce illumination. No new renderer, bus, memory, executor, asset or permission. EXP-11 compatibility unchanged.

Native isolated Kotlin 2.2.21/JDK21 + JUnit4.13.2: 9 tests; baseline 2 FAIL, patched 9 PASS. This is not the Android Kotlin2.4 build. Reproduce by compiling JarvisRenderQuality.kt and JarvisRenderQualityTest.kt with JUnit, then JUnitCore at.heinz.deviceai.ui.JarvisRenderQualityTest. Raw red/green logs retained.

Android preflight failed before compilation: Gradle wrapper network unreachable; previous SDK/cache absent. Environment restoration in progress. Full Android, lint, new screenshots and new APK NOT_RUN. Previous APK source remains 3ae6e225; it does not contain this correction. No main merge, signing or publication.

connections.json is an initial source-path matrix with explicit remaining gaps, not a completed full audit. #746 is evidence-only; Claude head equals baseline. Do not manufacture before/after images or import unpublished work. Next: restore Android toolchain, execute full gates and native captures, then continue caller audit. Rollback: revert this isolated quality-policy change.
