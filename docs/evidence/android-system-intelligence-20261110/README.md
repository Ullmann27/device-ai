# Final system-intelligence host acceptance

Tested local source `b00ff926dc5f0fe66a8fd75ecd5d110053cb1e7c` has the exact full
tree of published `02120f079ce1bdb1d3311f16f88d1a51bb7fbe09`:
`5d054bfd1214d4175424454b01d109ce6fe743e1`. Later completion commits only add
reports/evidence. `RESULT.json` binds scope and artifact hashes; raw commands,
selected tests, application-main tree and post-run clean-source checks are in the
compile/capture receipts. There is no APK, signing or installation.

- Entire Android main and unit-test sources compile.
- Selected integrated Android/JVM suites: 155 passed, zero failures/errors/skips.
- Node wiring: 26 passed. Python Fold7 lab: 60 passed with mocked device I/O.
- Nine native Activity/Cinema frames, plus two isolated native surface frames.
- Inner and cover diagnosis images and both isolated surfaces visually inspected.
- Physical Fold7, actual Shizuku/SAF behavior, Filament/GPU and device speedup: NOT_RUN.

The original red run remains: four failures out of 152. Both outdated STOP
assertions and both incomplete render fixtures are corrected and green. Eight
previously passing navigation cases were not repeated. The final 155 cases also
include the new storage and preview regressions and the existing STOP-return
contract. Supporting agents' test subsets overlap; do not sum them again.

All diagnosis numbers in PNGs are explicit fixtures. Activity frames preserve the
existing original avatar's 2D fallback and orb, with the new data surface inside
the real Cinema work window. They are not a screenshot of the owner's phone and
not a Filament render. `surface/` supplies an isolated reproducible native view
capture and watermark. There is no image-generation mockup in this evidence.

Reproduction with an Android SDK and existing dependencies uses the exact commands
in `compile.json` and `native-green/capture-run.json`, substituting local tool/cache
paths and a fresh external evidence directory. The existing native-capture runner
uses a shared exclusive lock; this session waited for the main APK test and did
not stop any other worker. Full repository tests and Android lint were not run.
