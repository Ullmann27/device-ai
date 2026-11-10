# PR #728: source-change isolation

Read-only GitHub checks compared the complete PR file list against its base
`4b8fa96effd5a390bd5fd83ed84b81ac850b1d5b`. Pagination was completed and the
PR HEAD was read before and after each inventory; both inventories remained
stable while being read.

| Audited HEAD | Total changed files | Research directory | Handoff document |
|---|---:|---:|---:|
| `605a81fd08a3ba740c8bf728ac3d43f5cfd64133` | 103 | 102 | 1 |
| `70cf298893294fef83c6cde9e0c591522cc4f8e9` | 124 | 123 | 1 |

Every changed file is an addition under
`experimental/weight-space-20261110/`, except the single handoff document
`docs/research/omega/MODEL-WEIGHTS-20261110-HANDOFF.md`.

The PR delta contains no changed application source, production model
selection, build configuration, manifest, signing configuration or release
workflow. The added model binaries are research artifacts in the experimental
directory. This check found no production-file change that activates them.

This is a file-change isolation finding for this PR relative to its stated
base. It does not audit inherited base changes, prove what every existing
build configuration packages, or establish runtime behavior. No APK build,
APK inspection, installation or Fold7 test was performed for this check.
It provides no APK-readiness or release-approval claim.

GitHub source: PR metadata and complete `/pulls/728/files` pages 1 and 2.
No repository files, branches or remote metadata were modified by the audit.

## Scoped Cinema build-configuration read

Independently fetched these two files at Cinema PR #715 commit
`c972f56ccc4caa2f1807bcaf1d4665347c66c629`:

| File | Git blob SHA |
|---|---|
| `settings.gradle.kts` | `a28a73967af24ad4ae6fb5f3cc7e681e258b63f4` |
| `app/build.gradle.kts` | `f640d5f9a22716fbf1d1973dea30dffe9930be7c` |

Settings declares only module `:app`. The app's main-source configuration
adds generated `ownerFaceAssets` and, conditionally, `src/termux/java`.
Neither inspected file references `experimental/weight-space-20261110/`.
This narrow configuration read supplies no visible connection from this
research directory to these configured sources. It is not a transitive
packaging audit, assembled-APK inspection or runtime test.

## Integration instruction

Keep all three candidate arms—answer-only, replay and distill-v1—outside
application adoption and production asset/model selection. Candidate weights
remain research artifacts irrespective of any narrow DEV improvement.

Do not merge the historical #530 weight-training base or the full #728
research branch into Cinema #715. Any later approved product change needs
an explicit, separately reviewed integration on the current product base.
The coordinator reports product integration #725 at
`b0a1635b77f568692ac6ff6b662f52d6b6eb7231` as separate from #715; that merge
relationship was not independently rechecked in this two-file audit.

No APK was built, installed or released by this review.
