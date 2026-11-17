# Complete c3d2 candidate verification — FAILED

Source: c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e, tree57027b6d4cfceda3bbd8241e77341ec67632370c, PR578. All receipts confirm source unchanged before/after their actual native runs. This is a failed integration checkpoint, not APK acceptance.

| Gate | Observed result |
| --- | --- |
| Android production and test compilation | PASS, actual Android36 SDK |
| Android unit and enabled Robolectric UI | 649 XML suites,3690 tests:3654 passed,16 failed,20 skipped |
| Android full command | EXIT1 after35m2s; started20:50:42, finished21:25:45UTC |
| Lint | NOT_RUN: prior unit failure stopped the command |
| Node special release checks | PASS |
| Node critical | FAIL: one outdated android-source-contract assertion |
| Full Node | 2427 total:2386 passed,10 failed,31 skipped |
| Backend locked dependency install/check/tests | PASS |
| Unsigned APK/AAB for this source | NOT_RUN |
| Original-signed Code47 | NOT_RUN; Code46 remains the published version |
| Physical Fold7, GPU, native model/audio/thermal measurements | NOT_RUN |

Raw unmodified gate outputs and full Node receipts are losslessly archived in native-gate-raw.tar.gz. All649 original JUnit XML files are in android-junit-raw.tar.gz; android-results.json lists every failure with its original message/stack. Archives and copied receipts are hashed in files.json. The source-bound orchestration script is retained. A preceding independent real-source RED/GREEN probe for same-millisecond stale-scene callbacks is retained separately; it is not a UI/device result.

## Coordinated follow-up

- PR582/585: actual Unicode-format app-query regression, capture source-contract tests, configured identity Fallback test and workscene UI fixture.
- PR583: sole AgentActivity writer; direct STOP acknowledgement, owner-input retention after cancellation, two reviewed prompt/identity wiring fixes, existing Activity fixtures and truthful work-status tests.
- PR586: current-turn prompt/transcript duplication regression; real context restoration and current context/clock/source contracts.
- PR584: bounded polite PDF recognition, configured address reuse and no partial intake of explicit additional app steps; two Activity arguments coordinated in PR583.

Some failures are product defects and others are obsolete/incomplete test fixtures. Fixing the latter must retain the actual user-visible/security contract; no test is disabled or waived. The exact later integrated source must pass all mandatory gates before original signing. The existing original publisher performs full QA and original-package unsigned assembly before reading signing material.

Rollback: retain published source577f0931e7937ffcac02dc855a540f093facdb3b / Code46 and its existing live artifact. This evidence commit changes no product or tests. Do not reset foreign branches, merge main, export signing keys or install on the device. Resume with the four exact follow-up heads, independently review/merge, freeze new SHA and run the gates; never reuse this failed result as a later PASS.
