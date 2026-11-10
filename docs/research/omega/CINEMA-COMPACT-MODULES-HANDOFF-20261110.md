# Compact module symbols — host verified

Integration owner: root, PR #715. Own branch: `fix/cinema-compact-modules-20261110`.
Base local `b498becd85b1e0a53a465791bf0bd63bf9a01b0c`, remote `4a8fc1e8b9d86151f80c5004abeab8991fbe2418`, identical tree `2a74e12b80a80a7e46bd65363df8a8757e5f2216`.

The actual cover capture and independent review #729 show closely packed module symbols: five rows in a 72dp panel, 12.8dp row pitch, fixed 18dp icon boxes. This does not establish general unreadability or a touch failure.

The existing `JarvisCockpitGridContent.modulesPanel` now caps each symbol at its row height minus 2dp, retaining the original 18dp upper bound. Text, status values, colors, panel bounds, routes and touch targets remain unchanged. No new renderer, dependency, clock or asset.

The focused test calls the real grid-content renderer. It records the database glyph's actual ellipse stroke, checks its row boundary on the cover, and checks that the inner display retains original geometry. Actual bounded host verification: baseline8/9 with the expected cover glyph boundary failure, candidate9/9 PASS (two new geometry checks and seven existing LivePanels regressions). Both arms compiled the actual GridContent source and tests against the integrator’s already compiled Android classes, with 256MB Kotlin compiler and128MB JUnit limits. An initial preview-only classpath lacked Dashboard classes and was corrected; that setup failure is not application RED evidence. Status: SOURCE_INTEGRATED here, HOST_VERIFIED; native PNG and Fold7 acceptance remain NOT_RUN for this patch.

Root should integrate the production file and test after the current button milestone, run `JarvisCompactModuleGeometryTest` alongside `JarvisLivePanelsRegressionTest`, and capture an unscrolled cover idle frame. Existing `CinemaNativeSequenceUiTest` provides the real API28 Canvas path with the 2D fallback; it cannot certify Filament/GPU/Fold7. Acceptance: symbols separate visibly, status text/meaning stays unchanged, inner icon detail preserved.

Compatibility: presentation-only refinement under the existing Cinema-depth work; EXP-00–12 contracts and model/worker/memory/permission paths unchanged. Revert this isolated patch to restore the fixed icon size. Main, Code52, signing, installation, original assets and research branches are untouched.

## Reproduction and evidence

Use the repository Android test path for `at.heinz.deviceai.ui.JarvisCompactModuleGeometryTest` and `at.heinz.deviceai.ui.JarvisLivePanelsRegressionTest`. For the direct host comparison, compile the unchanged GridContent from `b498bec` for baseline and this commit’s GridContent for candidate, together with those tests and `JarvisPresentationRecordingPainter`, against the same actual Android build classes. Put each arm’s jar first on its JUnit runtime classpath. Both arms use identical dependencies and inputs; only the actual renderer source changes. `CINEMA-COMPACT-MODULES-RESULT-20261110.json` records pinned source and log hashes.
