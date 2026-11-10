# Compact module symbols — WIP

Integration owner: root, PR #715. Own branch: `fix/cinema-compact-modules-20261110`.
Base local `b498becd85b1e0a53a465791bf0bd63bf9a01b0c`, remote `4a8fc1e8b9d86151f80c5004abeab8991fbe2418`, identical tree `2a74e12b80a80a7e46bd65363df8a8757e5f2216`.

The actual cover capture and independent review #729 show closely packed module symbols: five rows in a 72dp panel, 12.8dp row pitch, fixed 18dp icon boxes. This does not establish general unreadability or a touch failure.

The existing `JarvisCockpitGridContent.modulesPanel` now caps each symbol at its row height minus 2dp, retaining the original 18dp upper bound. Text, status values, colors, panel bounds, routes and touch targets remain unchanged. No new renderer, dependency, clock or asset.

The focused test calls the real grid-content renderer. It records the database glyph's actual ellipse stroke, checks its row boundary on the cover, and checks that the inner display retains original geometry. No test results claimed at this checkpoint: host compiler lease and integrator native PNG capture are pending. Status: SOURCE_INTEGRATED in this branch, NOT_RUN verification, NOT_RUN Fold7.

Root should integrate the production file and test after the current button milestone, run `JarvisCompactModuleGeometryTest` alongside `JarvisLivePanelsRegressionTest`, and capture an unscrolled cover idle frame. Existing `CinemaNativeSequenceUiTest` provides the real API28 Canvas path with the 2D fallback; it cannot certify Filament/GPU/Fold7. Acceptance: symbols separate visibly, status text/meaning stays unchanged, inner icon detail preserved.

Compatibility: presentation-only refinement under the existing Cinema-depth work; EXP-00–12 contracts and model/worker/memory/permission paths unchanged. Revert this isolated patch to restore the fixed icon size. Main, Code52, signing, installation, original assets and research branches are untouched.
