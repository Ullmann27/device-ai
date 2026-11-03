# Continuum Ω — Independent quality handoff, 2026-11-03

Status: SOURCE_INTEGRATED / REAL_APK_BUILT (unsigned local host artifact). Executed host gates passed; explicitly skipped cases remain unverified. No owner signing, publication, device installation, Actions workflow/dispatch, or main merge occurred.

## Exact sources and published evidence

- Baseline: #390 `b4f61ae9765e26dd401db928b76fa9f233c0742a`.
- Full Node execution: `67f437e35bfe32a343c1b89b7c60e49090a2f59a`.
- Final Android tests, configured lint, compile and unsigned APK: `64ec06a73f50cae9ee49035c840710d87777d50f`.
- Between those final SHAs, application main sources, Node sources, backend and tools are byte-identical. Only executable difference is the missing `assertNull` import in an Android test. Exact proof: `combined-64ec06a7/NODE-SOURCE-EQUIVALENCE.txt`. Node results are not mislabeled as execution at the later SHA.
- Quality branch `codex/team-05-quality-omega-20261103`, Draft PR https://github.com/Ullmann27/device-ai/pull/396. Evidence root: `docs/evidence/omega-quality-20261103`. Chief integration remains an isolated Draft PR, not main.

## Measured results

| Check | Baseline | Final |
| --- | --- | --- |
| Full Android JUnit | 2058 total: 2040 pass, 1 fail, 17 skip | 2101 total: 2089 pass, 0 fail, 12 skip |
| Full Node | 2074 total: 2061 pass, 0 fail, 13 skip | 2160 total: 2153 pass, 0 fail, 7 skip |
| Backend Node | 64/64 pass | 64/64 pass |
| Configured Android lint | pass | pass; existing `NewApi` checkOnly configuration |
| Real CRE1/SWB Node→Kotlin→Node parity | fixtures initially absent | preparation, three JUnit checks and verification pass |
| M3 exposed 81-case report | 77 correct, 4 wrong, harmful0 | unchanged; no per-case regression |
| Exposed German LearningCore40 | 34 correct, 5 abstain, 1 wrong | 35 correct, 5 abstain, 0 wrong; forbidden0 |
| Native avatar UI / existing orb motion | new head absent | 3/3 native tests and 6/6 existing motion tests pass |

The five avoidable JUnit skips were executed by supplying existing synthetic parity fixtures and using installed Java21 for tests. Twelve authored `@Ignore` Chief integration placeholders remain. Seven Node cases require optional native graph/Harness SDK/Docker setup; they are recorded as skips, not passes. Final test-case identity comparison records two explicitly reviewed replacements for the authorized legacy-capacity extension; no unexplained removal or same-named previously-passing regression. The hard4096 bound has a separate fail-closed test.

These are host and exposed development-set results, not a blind intelligence benchmark. Actual Gemma inference and Fold7/on-device acceptance are NOT_RUN. Current native avatar is original procedural **2D**, not the subsequently requested 3D milestone.

## Failures preserved and corrected

- Baseline `CoreGemmaRaceTest` constructed forbidden negative latency before its intended assertion. Existing constructor guard is unchanged; final test asserts its rejection and retains missing/skipped/failed/zero-latency controls.
- First combined `67f437e3` Android run failed test compilation because the new capacity test omitted `assertNull` import. `attempt-67f437e3` retains raw failure; the import-only fix is in final64ec.
- Independent memory28c test initially failed a newly added timestamp oracle for a repeated identical status. Existing no-op behavior was retained; oracle correction and failed evidence are preserved.
- Independent LEX3 checks passed migration/legacy preservation,4000 records, hard bound, corruption/refusal, real two-process locking and real process halt before commit. Additional independent review found and repaired CLPT boundary overhead and fsync-error retry acknowledgement; corresponding fault-injection evidence is in the integrated memory lane.
- CCC1 final7bd39ef3 independently passed142/142 existing cases and26/26 extra adversarial checks. Receipt consistency is not worker authentication.
- The initial embeddable compiler CLI adapter failed `-include-runtime`; official Kotlin CLI fixed the harness and unchanged Kotlin delegate tests passed11/11. Both outputs retained.

## Actual APK

Local artifact: `/workspace/continuum-artifacts/omega-64ec06a7-code40-unsigned.apk` (54,865,941 bytes).

SHA-256: `dc201d6b674b458fd13bbf1404b74b87fd94fcfb4b289cab670dd3f707f1ba57`.

Official `aapt` confirms `at.heinz.deviceai.continuumlocal`, versionCode40, versionName0.3.39, minSdk26, targetSdk36. `zipalign` passes. `apksigner verify` rejects it as expected for this unsigned artifact. It is **not an owner update and was not signed, published or installed**. The original signed live Code39 APK/catalog was separately inspected read-only by Chief. Original signing, monotonic live-version check and Fold7 acceptance remain release gates.

## Reproduction and environment

Shared environment `/workspace/continuum-build-env/env.sh`: pinned Temurin JDK17.0.20.1+1, official SDK36/build-tools35, Gradle8.11.1, Kotlin2.4.0, Node24.19.0. Android compilation targets JVM17; an external init script selects `/usr/bin/java`21 for the complete unfiltered final Test task. No project build files were changed for the harness. Official downloads matched published hashes; `JVM-DEPENDENCIES.txt` records jar URLs/hashes.

Exact scripts, commands, environment/tree SHA, raw logs, all JUnit XML (tar), summaries, score reports, parity outputs, native head PNGs and APK checks are in `combined-64ec06a7/RESULT.json` and adjacent files. Default orb and native head were reviewed on actual Robolectric screenshots. A targeted JVM21 setup call using `--tests` was refused by the existing QA task allowlist; an external Test filter verified that setup. The **final** init script contains no filter.

## Next entry / rollback

Finish with the actual tested SHA and evidence before any new cycle. Current user instruction authorizes a subsequent independent breakthrough QA cycle on a fresh branch from64ec, with newly frozen unseen cases kept from candidate authors until their implementations freeze. It does not change current APK/release limits. Quality owns the shared build environment and coordinates one Gradle job at a time. Revert isolated feature commits for source rollback; original LEX2 migration backups are diagnostic only and must not automatically restore stale owner consent.
