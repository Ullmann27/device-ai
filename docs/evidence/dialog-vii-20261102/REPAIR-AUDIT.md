# Dialog VII – Repeatable defect hunt
Parent 15ccf8b1cb9ae386e7e9e32cb42aba05376ab64f PR #347.
Isolated repair branch research/continuum-dialog-vii-repair-audit-20261102.
Audit current Kotlin parser, tool result trust boundary, offline speech sanitation, local-turn lifecycle, F08 source evidence. Begin with RED reproductions; preserve existing tests, read-only ToolGate, consent and PR ancestry. Keep all work in private GitHub and do not modify Main/release/weights.
Initial status: DEFECT_DISCOVERY; NEW_BRANCH_TESTS_NOT_RUN.

## Supplement: GitLab MR !299 integration triage (user-supplied MR description, no authenticated GitLab fetch)

GitLab source branch `research/continuum-m4-web-study-unblock-20261031` targets old `research/continuum-m8-render-signed-release-20261031` at Continuum Lokal 0.3.32/code33. GitLab reports eight commits, no pipeline, two open discussions and merge blocked by review plus conflicts. This is not an Android QA sign-off. Do NOT merge this old release branch or overwrite newer GitHub owner branches.

Source overlap verified directly on GitHub in this exact research lineage:
- `ContinuumSourceNovelty.knownHashes()` already unions bounded journal, archive hashes and active SOURCE_TEXT ledger.
- `ContinuumKnowledgeArchive.archivedHashes()` present.
- `ContinuumMemoryDialogue` includes `PUBLIC_WEB_SOURCE` only as `SOURCE_OBSERVATION`.
- `ContinuumOpenWebStudyFetch` has bounded candidate list (12) and `firstTransientFailure` handling.
- `Ewm1SourceIndex` supports timestamped source records; `TemporalNeuralShadow` contains eligibilityTrace, activationThreshold and skippedUnits.
Full exact equivalence with all 19 GitLab changed files **NOT VERIFIED** (no GitLab connector). Never declare it merged.

Two GitLab Duo review observations reproduced on GitHub:
1. Duplicate `app/src` existence check in `.github/continuum/autonomous-teamleader.mjs`. Removed redundant second check, unchanged logical guard.
2. `propose-repair` hash ID intentionally still uses legacy literal backslash-zero separator. A change to NUL would derive new IDs for existing owner repair tasks. Documented compatibility instead of changing the persisted ID contract; any later migration must test old and new ledgers, not silently retag jobs.

Node tests for these new changes: **NOT_RUN**. Android, Render, physical Fold7: **NOT_RUN**. The source modifications are on this isolated research PR only, not GitLab MR !299 or main.
