# ZETA native cockpit evidence

This lane changes the existing Android home composition. It does not create a second UI application, speech pipeline, model session, renderer clock or avatar sink.

## Frozen comparison

Production baseline: `04b4292d796bc151c40ab18405226cd9faef01cf`. Test preregistration: `07c855b19d43609c51fb426d2f538b6049d847ff`; frozen tests: `6dc42d44`.

- `baseline/legacy-layout-test.kt.txt` and `legacy-screen-test.kt.txt` preserve the previous scaled composition assertions.
- `baseline/legacy-junit.txt`: six original layout tests passed on the baseline.
- `baseline/acceptance-red.txt`: both new adaptive acceptance tests failed on the unchanged baseline (320dp scale 0.776699, hero/action intersection).
- `baseline/junit.txt`: real Android Canvas snapshot/callback test passed; 15 images are preserved in `baseline/images`.
- `development/font-bounds-red.txt`: new native text geometry check caught a 1.8dp subtitle overrun at font1.6. This failure was fixed in source, not removed from the test.

## Reproduction

The prepared host contains JDK17/Kotlin2.4, Java21 for Robolectric, SDK36 and Robolectric4.14.1. `run-layout-tests.sh FRESH_OUTPUT` compiles the production layout and tests. `run-native-ui.sh FRESH_OUTPUT candidate` compiles the changed Home source and the UI test against the saved baseline Android classpath, then runs real native Android Canvas under Robolectric SDK28. Its friend path targets the actual runtime classes JAR, not the unrelated classes directory. No Gradle daemon, app installation or device is started by these focused scripts.

A clean full Android checkout can run the source-set gate with the existing `-PcontinuumUiWidgetTests=true` and `:app:testDebugUnitTest --tests at.heinz.deviceai.ContinuumAdaptiveCockpitUiTest` using the project SDK/JDK requirements. The Quality lane owns the single full Gradle build and final integrated source gate.

## Screenshot meaning

Each PNG is the **actual full scrolling native Home widget**, measured at the width encoded in its filename, with system font scale1.0/1.3/1.6. The nominal height is the input window height; the image can be taller because the Activity's ScrollView scrolls the Home content. The Activity-owned navigation rail, composer, system bars and keyboard are not part of these widget snapshots. The baseline shared real `ContinuumHologramView` draws its existing original orb with animation disabled. These are neither reference concept pictures nor actual3D/Fold7/device screenshots.

The requested reference informs navy glass surfaces, cyan/violet edges, a free central avatar stage, actions below and telemetry beside it on broad screens. Reference comparison is manual, not a pixel-equivalence claim: the actual3D asset and full Activity composition are separate work. No mock avatar image or invented counters are inserted to resemble the reference. Empty state shows unknown values.

## Integration boundary

The same `ContinuumDashboardState` collector and `AgentActivity.openHomeSlot` continue to drive all cards. `WORKERS` displays the known development backlog as **Aufträge**, not a fabricated number of autonomous agents. Storage still distinguishes device occupancy from knowledge sources. Holdout quality is labelled **Prüffragen**, not intelligence. Old developer and owner functions remain reachable through unchanged callbacks and permissions.

The free stage sizes are: phone320:296×310dp; phone412:388×390dp; Fold600:576×390dp; Fold840:816×470dp; tablet1280:836.19×470dp at ordinary font size, beside a292dp status column. EPSILON owns the optional real3D72%/orb28% composition inside that same stage. At large fonts, cards reflow and the tablet sidebar joins the grid.

No physical folding, microphone, TTS engine, GPU frame rate, power/thermal behavior, model inference, camera, actual3D visual likeness, APK or release is verified by this lane.

## Final author gate at de86cff67b1b028645d7bf77f5fee171d66530a0

- Native Android Canvas: **2/2 tests PASS**,53.387s host wall time. Fifteen real widget snapshots; separate measured text-bounds/collision checks at font1/1.3/1.6/2 and long synthetic labels. Timing includes the whole host test and is not device render latency.
- Layout: **9/9 tests PASS**,0.285s host test wall time; two unchanged pre-implementation acceptance tests now pass, alongside preserved reflow/rail checks and explicit new grid/font assertions.
- Header target at320dp: previously32×320/412=24.85dp, now48dp. All six actions are below the hero; every declared data-card destination is present at every tested size.
- `final/manifest.json` pins exact production/test hashes and the compiled baseline runtime JAR used for host linkage. `image-sha256.json` pins original baseline/final PNGs.
- Java2D screen/data regressions: **3/3 PASS**,1.997s host test time, including unknown values/no example counters and all existing renderer modes.
- Independent holdout and full integrated Android build are separate gates; not claimed from author tests.
