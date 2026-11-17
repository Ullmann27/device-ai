# Native diagnosis surface evidence

Actual `SystemDiagnosisHologramView.kt` and `SystemDiagnosis.kt` were compiled
directly with the installed Kotlin compiler and rendered by Robolectric API28
native Canvas. One native test passed and produced both PNGs. The images were
opened and visually inspected. All displayed numbers are explicit synthetic test
inputs, not measurements from the owner or Fold7. Unknown data is shown as unknown.
The watermark is part of the Android test Canvas, not a generated-image edit.

Scope: isolated data surface only. No Activity layout, existing Cinema motion,
Filament, device GPU, thermal or actual performance acceptance is established by
this isolated check. The full integrated renderer has its own separate gate.

`receipt.json` binds actual source hashes, cached unrelated app-class dependency,
source commit, output hashes and limitations. `SystemSurfaceCaptureTest.kt` is the
exact fixture. `replay-host.py` uses the existing installed cache, with no download
or Gradle task. Set CONTINUUM_SOURCE_ROOT to the checkout, CONTINUUM_QA_CACHE to
the configured qa-installed-tools/qa-gradle-home parent, CONTINUUM_CHECK_OUT to a
new directory outside the repository. Exact cached dependency identity must be
verified when replaying. Output PNGs are actual native renders, not design targets.
