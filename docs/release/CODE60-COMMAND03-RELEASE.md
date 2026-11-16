> **Delivery checkpoint 2026-11-16:** PR854 controls and PR857 QA repairs are now combined on the canonical PR851 line, retaining the existing simple owner-APK workflow from `e7178c48`. Fresh local DOM 45/45, offline-web evidence 24/24, original assets 35/35. Combined Android build/QA remains pending. The earlier full-QA run has eleven unresolved Android failures. Original signing is blocked by the existing environment branch policy. See the [current delivery handoff](../research/omega/CODE60-DELIVERY-HANDOFF-20261116.md). The formal full-release contract below is retained; the simple workflow produces an owner test prerelease, not full-device acceptance.

# Code60 – native Kommando 03 release preparation

Status: **SOURCE_PREPARED / LOCAL_CONTRACT_TESTS_PASS / CURRENT_CONTROLS_NATIVE_QA_BLOCKED_SHARED_RESOURCE_PRESSURE**.

Canonical work branch: `integrate/continuum-command03-android-20261115`, [PR #851](https://github.com/Ullmann27/device-ai/pull/851), based on published Code59 `79c16e57aeb7e0cf4a851e921d7441f7f6384fdf`. The exact final commit and real execution evidence are recorded in that PR. Earlier successful runs must not be substituted for the final commit's evidence.

Current controls work is in [PR #854](https://github.com/Ullmann27/device-ai/pull/854), source `62be11f898586eb3546f79a0f2b38ee53acc4d84`. It preserves the concurrently advanced canonical navigation history through a two-parent merge. All 44 Python and 40 Node release contracts were freshly executed on that source and passed, together with three workflow YAML files and 32 embedded Bash syntax checks. The merged Android main compile lost its daemon before the new 37/77/32/6 native groups could run. A bounded observation confirmed continuing shared OOM pressure, without proving a PID-attributed kill. No new controls APK exists; the feature remains unintegrated pending native/browser/artifact evidence. [Exact handoff and evidence](../research/omega/COMMAND03-NATIVE-CONTROLS-HANDOFF.md).

## Fixed release profile

The existing full-QA, context-QA and original-signing workflow paths retain their previous Code59 behavior and gain an explicitly selected Code60 profile. No new workflow or push trigger is added. For Code60, both QA workflows require manual `workflow_dispatch` on the exact branch and a 40-character `expected_sha` equal to the actual checked-out workflow commit.

The full workflow still requires all five existing jobs: unsigned-arm64, renderer-preflight, full-android, node-backend and native-runtime. Its unfiltered Android/UI suite, lint and native-runtime checks remain required. The context workflow is a separate required run at the same source SHA. The Code60 signer rejects automatic runs, different branches, incomplete jobs, mixed artifacts and Code59 evidence passed off as Code60 evidence.

The release profile fixes versionCode 60, versionName 0.3.59, package `at.heinz.deviceai.continuumlocal` and ARM64. The protected environment, original certificate, owner reviewer, exact single-branch rule, explicit approved source SHA, archive/digest validation, minimum coverage, 16-KiB alignment and independent release readbacks remain intact. The original `verify_code59_evidence` shell function is byte-identical to the preceding source.

## Pinned upgrade baseline

The future real preflight independently downloads and validates [the published Code59 release](https://github.com/Ullmann27/device-ai/releases/tag/continuum-local-code59-79c16e57aeb7e0cf4a851e921d7441f7f6384fdf):

| Field | Required value |
|---|---|
| Version | 59 / 0.3.58 |
| APK SHA256 | `7fa6387e68fc4be9ee46079aac1b95feb67ab2656fec3c359128c58e6a82210e` |
| APK bytes | 104438996 |
| Package | `at.heinz.deviceai.continuumlocal` |
| Certificate SHA256 | `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` |

A changed newest release causes a stop rather than silently changing these pins.

## Local adapter checks

44 Python and 40 distinct Node contract tests passed without skips. They use synthetic metadata, temporary evidence and fake service tools: no network, signer keys or environment mutations. They exercise wrong/missing SHA, wrong repository/branch/event, missing jobs, mixed/expired artifacts, bad digests and archive paths, wrong release versions and missing mandatory regressions. All six actual Code60 shell SHA guards were executed with valid and invalid inputs. Three YAML files and 32 embedded shell blocks passed syntax checks.

The Code60 profile requires 33 named cases in addition to the existing full-suite coverage contract: four existing composer cases, eleven command-navigation cases, eight history cases and ten workstation-layout cases. Each of the four new history regressions is individually required. These named cases do not replace the full suite.

Reproduce from the repository root:

```bash
python3 -B -m unittest discover -s tools/code55-apk -p 'test_code59_signing*.py' -v
node --test .github/continuum/code50-github-release-contract.test.mjs .github/continuum/code50-github-signer-preflight.test.mjs .github/continuum/code59-signing-node-proof.test.mjs .github/continuum/code60-release-contract.test.mjs
bash -n scripts/continuum-code50-github-signer.sh
node --check tools/code55-apk/code59-node-proof.mjs
```

The initial new Node test parser did not recognize digits in the job name `unsigned-arm64`; its regex was corrected and all seven new contracts passed. The 33 existing Node contracts passed in the initial run. No production workflow or app assertion was removed to obtain this result.

## Remaining release boundary

This preparation does not dispatch QA, sign, publish, install, merge to main or alter the signing environment. Local host tests and an unsigned APK are not an installable owner update or physical Fold7 acceptance.

After the final source and reviewable native artifacts are ready, the project AGENTS.md requires explicit owner approval for the concrete Code60 GitHub QA/sign-and-publish channel and source SHA. An authorized administrator must set the existing environment's one branch rule to this exact integration branch while retaining the required owner reviewer. Both real complete source-bound QA runs must succeed, and the owner must then approve the protected signing job. None of these administrative or signing steps was performed by preparing this adapter.
