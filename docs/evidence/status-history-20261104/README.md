# Status-history screenshot correction — 2026-11-04

The owner supplied a real status-screen screenshot of version 0.3.39 / code 40. Its historical event cards split `19:59` into a cyan heading ending in `19` and a value beginning with `59`. The same mistake affects standalone URLs. Inspection also found that persisted INTERRUPTED and five DEFERRED phases were not recognized by the display formatter, while unknown suffixes could be partly rewritten.

## Narrow repair

The first colon is a label/value delimiter only when followed by whitespace or an empty value. Clock times and standalone URLs remain complete. Known persisted interruption/deferral tokens are decoded with exact boundaries; unknown phases and non-13-digit timestamps remain untouched. Labels, tab/space delimiters, empty-value placeholders and all original state data are preserved. Only the UI adapter and its actual-widget tests changed. EXP-01 status visibility and EXP-12 checkpoint interpretation remain compatible; no experiment milestone or new capability is claimed.

## Actual verification

- Baseline actual Android/Robolectric widgets: three focused regressions executed and all three failed for the reported defects. See red.xml and red-log.txt.
- Repaired complete ContinuumControlsTest: 10 PASS / 0 FAIL / 0 SKIP, including all seven existing controls tests and the three regressions. See green.xml and green-log.txt.
- Existing cockpit/integration source wiring: 29 PASS / 0 FAIL / 0 SKIP. See wiring.xml.
- Configured NewApi lint: 0 issues. See lint.xml.
- Actual host Android Canvas captures at 320/720 dp and font 130% are included. They use synthetic test data and are not additional physical-device evidence.

The source-file hashes in summary.json bind the tested files before commit. The release pipeline must subsequently bind its full suite and original signing to the exact pushed commit. No S01/native-model execution or crash-barrier change was made. The old code 40 remains the published version until a later signed publisher/readback receipt proves code 41.

Original run: `python /workspace/scratch/391c0f6de827/build-env/run_gradle.py <red-or-green-log> -I /workspace/scratch/391c0f6de827/build-env/night-ui.init.gradle -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir=<red-or-green-images> -PnightForceTests=true -PnightTestFilter=<selection> :app:testDebugUnitTest [green additionally :app:lintDebug] --offline`.

Red selected the three new fully qualified methods in `at.heinz.deviceai.ui.ContinuumControlsTest`; green selected that complete class. The external harness/toolchain are retained in the preceding code 40 evidence under `../night-integration/0ff2cef5/reproduction`. Node command: `node --test --test-reporter=junit --test-reporter-destination=<wiring.xml> .github/continuum/aaa-cockpit-and-effects.test.mjs .github/continuum/android-integration-wiring.test.mjs`.

## Meaning of the owner's screen

`READY` reports the loaded local model's runtime state, not successful unknown coding tasks. `generation56 · verified` comes from the verified persistent archive checkpoint; it is not an intelligence score or 56 training generations. 33 receipts is the number stored, not 33 successful actions. `FINISHED` records the prior offline job ending without an uncaught failure/deferral; it does not claim measured learning gain. HTTPS backend URL plus a token determine `Cloud-Backend` configuration independently from the local model. The historical RUNNING entries precede their FINISHED entries and are not current active jobs. The screenshot does not prove end-to-end Shizuku actions, direct ChatGPT/MCP connectivity, the failed S01 case or complete Fold7/GPU/voice acceptance.

The raw screenshot itself is not copied into Git; only its hash and relevant observed values are recorded. Raw log trailing whitespace is preserved intentionally. MANIFEST.sha256 covers every file except itself.
