# Branch integration bots — 4 November 2026

Status: implemented and independently reviewed for the documented local scope. **183 targeted Node tests passed, 0 failed, 0 skipped. APK: NOT_RUN.**

Repository: `Ullmann27/device-ai`. Product baseline: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d` (`codex/astra-jarvis-takeover-20261104`). Integration branch: `codex/continuum-branch-integration-bots-20261104`. Draft PR: [#515](https://github.com/Ullmann27/device-ai/pull/515), based on the takeover branch.

## What now runs

The new entry point, `.github/continuum/branch-bots-cli.mjs`, coordinates bounded branch discovery, the existing canonical proposed-task backlog and the existing local scheduler. It attaches the existing APK host-environment diagnostic to each completed scan. It adds no package dependency, paid model API or GitHub Actions requirement.

| Responsibility | Implementation and result |
|---|---|
| Discover disconnected work | `branch-discovery.mjs` reads locally available heads, remote-tracking refs, available pull/archive refs and commit tags. It pins complete source/target commit IDs and the ref snapshot. |
| Separate already present work | Compare committed blobs and modes against the common ancestor and target. Distinguish inherited commits, identical trees, copied changes, missing paths, divergent edits and source deletions. |
| Prepare integration work | Use the existing `BacklogStore` to create idempotent `PROPOSED` / `BRANCH_REVIEW` tasks. A report does not create execution permission. Protected paths remain review-only. |
| Repeat locally | Reuse `runTeamleaderLoop` with its lock, checkpoint, restart and failure semantics. The CLI requires an explicit finite cycle budget. |
| Check APK prerequisites | Resolve the existing Termux tool paths and call `inspectHostEnvironment()` with that environment. Report actual host blockers while keeping `apk: NOT_RUN`. |
| Preserve negative outcomes | A strictly rejected, canonically journaled `ABSTAINED` attempt may write its permitted ECHO report and authenticated public snapshot. The original error and UNKNOWN service result remain. |

The development agents used in this session investigated sources, implemented isolated patches and independently reviewed them. That is separate from activating a persistent model worker on Heinz's Android device. No reachable live owner worker was activated here.

## Continuation: existing APK checks connected

The continuation selectively adopts the two-file QA preflight correction from PR #505 at `8b72db66e5338fbc3a791adc30254f4ca77007ad`. Both QA modes validate canonical version codes before setup/downloads and reject the omitted mentor P12 Base64 signing variable. The old script reproduced 14 failures among 43 cases; the adopted version passed all 43. This does not automatically increase an APK version or authorize publication.

Two additional red regressions established the Android environment gap. The Bot CLI now reuses `resolveTermuxEnvironment()`, and the three existing Java/Javac/Gradle probes receive the selected environment explicitly. The targeted suite passed 61/61, also in independent review. Inputs, process environment, source checks, report integrity and proposed-only queue authority are preserved.

The final combined suite passed 183/183. The tests use actual temporary subprocess fixtures and a simulated ARM64 discriminator; no Android execution is inferred. Details, pinned branch overlaps and the current build-host boundary are in `BRANCH-BOTS-CONTINUATION-20261104.md` and `docs/evidence/branch-bots-continuation-20261104/`.

## Start on a complete, clean local checkout

Requirements: Node.js and Git, a trusted installed tool environment, the code from this PR, and the branches/refs to be inspected already present locally. Run from the checkout root. Choose an external state directory belonging to this bot session; it contains the canonical `development-backlog.json`, private reports and scheduler state.

One scan:

```sh
node .github/continuum/branch-bots-cli.mjs \
  --root "$PWD" \
  --state "$HOME/.local/state/device-ai-continuum/branch-bots-once" \
  --once
```

Eight bounded cycles, one hour apart:

```sh
node .github/continuum/branch-bots-cli.mjs \
  --root "$PWD" \
  --state "$HOME/.local/state/device-ai-continuum/branch-bots-eight-cycles" \
  --loop --max-cycles 8 --interval-minutes 60
```

The same loop state resumes its remaining budget; it does not reset completed cycles. Changing the checkout HEAD or immutable loop configuration requires a new reviewed session. SIGINT/SIGTERM stops further admission. Use the existing integration/CCC1 owner workflow to turn reviewed branch findings into scoped executable tasks. The discovery bot does not turn a `BRANCH_REVIEW` proposal directly into a `REPAIR` command.

`--target` defaults to `HEAD`; `--max-sources` defaults to 1000, `--max-changes` to 200 per source, `--max-proposals` to 20 and `--timeout-ms` to 60000. Truncation is explicitly reported and returns a nonzero discovery result. No remote fetch is performed. Missing objects, inaccessible branches or refs not present locally cannot be claimed as inspected.

Exit 0 means the requested branch inventory completed. It **does not mean the APK passed**. Read `apkEnvironment`, `apk`, `executedTests`, `coverage` and `queue` separately. The report's SHA-256 is the hash of the exact saved JSON file bytes, including its final newline.

## Boundaries verified in review

The scanner refuses a dirty source, state writes inside source, redirected/symlinked state files, configured clean/process filters, active fsmonitor helpers, current worktree submodules, partial clones and legacy grafts. The CLI refuses inherited Git repository/index/config overrides. The reader removes those overrides and disables lazy fetching and replacement objects.

The scanner checks the configuration immediately before its status reads. The existing scheduler now accepts a trusted asynchronous `sourcePreflight` callback and awaits it at initial checkpoint open and before/after each work callback. The CLI binds the same source policy at those boundaries. A deterministic reviewer probe changed fsmonitor during the wait between cycles: the next check stopped with `DISCOVERY_FSMONITOR_UNSUPPORTED`, no hook marker was written, only one cycle completed and the persisted checkpoint became `ERROR`.

This is a source/configuration boundary on a trusted local host, not an operating-system sandbox against a hostile process racing filesystem changes. The APK diagnostic probes trusted installed Java/Javac/Gradle tools using the explicitly resolved environment; it does not mutate `process.env`. Its AAPT2 check is existence-only. It does not prove dependency-cache completeness, native ABI compatibility, compilation or device behaviour. The older `inspectReleaseCheckout()` release/version contract is deliberately not invoked.

Negative ECHO reporting uses the actual canonical attempt, reporter assignment, source identity and matching journal head. A blocked writer or old successful journal cannot supply the negative report. No rejected inference is promoted into success, experience or training. The unresolved older S01 resource barrier remains intact.

## Concrete branch findings

These observations concern pinned sources, not a complete live repository sweep. Full identities and the four shared file blobs are in `docs/evidence/branch-integration-bots-20261104/branch-candidates.json`.

| Source | Observation and disposition |
|---|---|
| #496 at `3968d785df607f8d461162c50bb3d710a925503e` | Worker `allowedTools` snapshots and routine overflow fixes, with their two test files, are already blob-identical in #506 at `8abea828833d8833d40e3d3d36feaa6d3685b82e`. Do not duplicate that four-file patch. This is not a claim that it is merged into main. |
| #505 at `4ddbcb092d955226bb83c2a10f7ac52bb34f59eb` | Avatar turn/lifecycle changes still need real Android regression evidence. Keep the unrelated ranking regression separate. Its full QA script may download tools and is not a discovery entry point. |
| #493 at `ee2fa0ee5567e5d34bcdfe229425a71df9818183` | Existing bot profiles, UI and local tool/coding routes span 111 changed files. No bulk integration; profiles alone do not establish active workers. |
| #510 at `2a364005b52e3240eb799f86aea31be90b390b10` | The missing negative ECHO reporting path has been repaired in this PR. The earlier activation handoff itself had not submitted live owner tasks. |

No unverified Kotlin feature patch was copied into this Node integration change. Better technology is not inferred from a branch name or from being newer. Integration requires scoped comparison and relevant real checks.

## Verification and remaining APK gates

The final continuation run executed the 12 test files named in `docs/evidence/branch-bots-continuation-20261104/node-tests.json`: **183 PASS, 0 FAIL, 0 SKIP**, Node 24.22.1 and Git 2.52.0. The five JavaScript files changed in this continuation passed `node --check`; the shell script passed `bash -n`; `git diff --check` passed. The continuation `node-tests.tap` contains the actual output, and its `patch-manifest.json` binds the six changed source/test files to exact hashes. The original 138-test log and original manifest remain historical evidence for the earlier PR515 checkpoint.

Tests include real temporary Git repositories, canonical backlog transitions, real canonical journal/report/grant stores and process-level CLI/restart/lock behavior. External model/Docker/JVM boundaries and existing mobile build/signature cases use test fixtures where documented. Those fixtures are not model inference, Docker isolation, Android compilation, APK signing or device evidence.

Source files were materialized selectively from the pinned upstream commit and checked by Git blob identity. This environment is **not a full Android checkout**. The combined run is a targeted regression suite, not the full repository suite.

The actual APK host diagnostic reports:

- `EXPLICIT_JDK_NOT_FOUND`
- `SDK36_NOT_FOUND`
- `GRADLE8111_NOT_FOUND`
- `NATIVE_AAPT2_NOT_FOUND`

`androidCompilationExecuted`, `androidTestExecuted` and `apkSigned` are all false. There is no APK artifact or installation claim. The next executable Android gate requires the existing owner host/toolchain and a complete isolated checkout, followed by the existing real unit/lint/build/device acceptance route. Keep source, package identity, version and signing verification attached to that future build.

## Review and rollback

All changes are isolated in Draft PR #515. The source-provenance, protected-path, scheduler and negative-report changes received independent review. The concrete findings were corrected and rechecked; no additional proven open finding remained in that review scope. This does not constitute release acceptance.

Rollback: omit or revert the commits of this isolated PR. Existing main/production, release identity, signing keys and owner data were not changed.
