# Continuum Ω — Independent quality handoff, 2026-11-03

Status: WIP / UNVERIFIED. Baseline source: `b4f61ae9765e26dd401db928b76fa9f233c0742a` (#390). Branch: `codex/team-05-quality-omega-20261103`. No implementation changes are owned by this lane.

## Scope and authorization

Independent host acceptance for EXP-00/02/06/07/11/12; baseline Node/JUnit, compile/lint where an authentic toolchain can be established, then SHA-bound lane review. The current master instruction permits isolated local environment setup, tests, commits, pushes and Draft PRs. Historical v31 research freeze is superseded by the documented Code39 release and current task. No Actions workflow or dispatch, main merge, signing, publishing, or device action is authorized.

Required AGENTS, experiment contract/register, Issue #41, current-state/frontier documents, release-stop reconciliation, PR #287 and Chief plan were inspected. Existing tests will not be weakened to obtain green results. Previous #375 evidence cannot attest #388/#390.

## Initial observations

- `java -version`: OpenJDK 21.0.12.1 runtime; `javac`, `kotlinc`, `gradle`, `sdkmanager`, and `adb` unavailable on PATH.
- `node --version`: v24.19.0. `gh` is available and repository read access works.
- No existing `/opt` Android SDK or `$HOME/.gradle` cache found.
- Project pins AGP 8.10.0, Kotlin 2.4.0, Gradle 8.11.1, compile/target SDK 36 and JVM target 17.
- Existing QA scripts contain pinned official JDK17 / Android CLI downloads and a pure Kotlin/JUnit dependency-directory harness. They are inspected as references, not executed as release jobs.
- Managed-environment status API returned `The environment configuration changed. Try again.`; local network policy reports unrestricted HTTP with proxy and no configured VPN. No credential values were read or logged.

## Pending checks

All baseline tests, lane acceptance, Android compile/lint and APK inspection are currently NOT_RUN. No new APK exists from this lane. Actual Gemma inference and Fold7 acceptance are NOT_RUN and cannot be inferred from host tests.

Shared toolchain provisioning is exclusively owned by Quality at `/workspace/continuum-build-env`. Agents may use prepared pure JVM jars, but full Gradle jobs must be coordinated to prevent competing memory use. Raw evidence goes in `docs/evidence/omega-quality-20261103` with source SHA, command, environment and exit status.

## Resume / rollback

Read this handoff and actual remote HEAD first. Resume dependency setup, record baseline results, then review exact lane SHAs supplied by Chief. This branch changes documentation/evidence only; no feature rollback is needed. No existing branch was reset.

## Checkpoint 2

Draft PR: https://github.com/Ullmann27/device-ai/pull/396. The requested nested branch name conflicted with an existing remote branch; the existing branch was preserved and this lane uses the hyphenated name above.

Official Kotlin2.4.0/JUnit4.13.2 harness, pinned JDK17.0.20.1+1, SDK36/build-tools35, Gradle8.11.1 are provisioned. Full Node baseline: 2061 pass / 13 skip / 0 fail (2074 total); backend: 64/64 pass; unchanged timeline/store Kotlin: 11/11 pass. Gradle with real SDK, widget tests and Code40 QA flags is RUNNING. Real Kotlin CLI skipped tests are being rerun after setup.

Chief granted exclusive lease for `scripts/continuum-omega-quality-acceptance.test.mjs`. It holds independent repair-receipt adversarial checks, RED on #390 and 24/24 GREEN on c9e52a64. Original CCC1 repair suite independently passes 27/27 including real isolated repair/replay. No synthetic consistency test establishes receipt authenticity or Android behavior. See `docs/evidence/omega-quality-20261103/COMMANDS.md`.

## Independent lane checkpoint

- CCC1 final source `7bd39ef3`: full CCC1 suite 142/142 and independent holdouts 26/26 pass, zero skips. No blocking source finding; Chief notified.
- Chief evaluation gate `d2c12c6e`: 11/11 unit cases plus 10 independent CLI cases pass, covering regressions despite aggregate gain, harmful statuses and malformed coverage. This judges submitted reports only.
- Intelligence `5346e769`: independent frozen timeline suite 12/12 pass. Later temporal-barrier commit `5cafd09b` reviewed as a narrow owner bare-resignation uncertainty barrier; combined regression pending.
- Memory `28c77782`: independent 27 cases, 1 failure in a newly added oracle expecting no-op status timestamps to refresh. All records matched except that timestamp. The failure is preserved. New frozen `04f9db6b`: independent 39/39 pass, including historical reader tests; separately, two real JVM halts before commit preserve the old ledger/revocation and retry to 258 records. These are Linux process/filesystem results, not Android power-loss evidence.
- Avatar `29ff3a5a`: source review includes synchronous stop/end notification, existing event sink and default-orb opt-in. Native UI checks still await combined Gradle run.

The first Kotlin CLI adapter attempted `-include-runtime` through the embeddable compiler and failed to locate its runtime. Those 4 harness failures are retained separately. Provisioned the official JetBrains Kotlin 2.4.0 CLI zip, published SHA256 `ba1b9e6eb6ddc3275079224f2e9ea4a2b02eef7d59ce2d38404f04b22613c20a`; unchanged real Kotlin delegate/owner-preview tests then pass 11/11, no skips. The pure compiler/JUnit harness was unaffected.

Full baseline Gradle compilation has succeeded; lint/unit tasks are still running. Do not infer total acceptance from partial task output. Source files for per-lane memory acceptance are extracted from the exact frozen commit, to avoid another lane's concurrent edits.

## Complete #390 baseline

Actual Gradle/JUnit baseline completed: **2058 tests, 2040 pass, 1 fail, 17 skip**, 378 XML suites. Only failure is existing `CoreGemmaRaceTest` constructing a prohibited negative latency outside an assertion. `TaskMeasurement` already rejects it; Chief is correcting the test to assert the constructor rejection, retaining all other controls. No production rule is relaxed. Full raw XML archive and machine summary are preserved. Compilation passed. Separate configured lint (`NewApi` only, per existing project configuration) and compilation passed, exit0.

M3 score reporter: 77/81, ROUTE14/18, every other category full score, harmful0. Unchanged German LearningCore40 development set:34 correct/5 abstain/1 wrong (A8 stale employer)/forbidden0. Both raw reports preserved before integration.

The17 skipped cases are12 existing @Ignore pending Chief integration placeholders,3 missing optional Node parity fixture tests,2 LiteRT lifecycle tests explicitly requiring JDK21. For final acceptance, Chief approves external Test executable configuration to installed `/usr/bin/java`21, retaining JDK17 compile target and unchanged project build files; report this environment difference. Synthetic CRE1/SWB parity fixtures can be generated by existing local scripts and verified separately. No skipped test is counted as passed.
