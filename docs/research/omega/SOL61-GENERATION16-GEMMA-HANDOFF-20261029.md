# Continuum Generation 16 / Gemma — reviewable source checkpoint, 2026-10-29

**Status: WIP / UNVERIFIED. No new Kotlin, Android, Node-process or device PASS.**
Official draft: [PR #218](https://github.com/Ullmann27/device-ai/pull/218).
Branch: `sol/continuum-generation16-gemma-20261029`.
Source checkpoint: [36b8d5a1643b435d691cd1605273d72f34ed9f9d](https://github.com/Ullmann27/device-ai/commit/36b8d5a1643b435d691cd1605273d72f34ed9f9d).
Later commits add evidence/handoff only. Read the final exact HEAD from PR218; its description pins that documentation HEAD.

## Actual baseline and branch decisions

PR216 was fetched through official git and independently read through the GitHub connector: `b47f4fac043b2124a9ef4dd1cfb469a3fde88bc6`. It descends directly from PR215 `043ffe6255d9beece7069f20a6cf5463e1d0af90`: **11 commits /8 changed files**, rather than the stale4-commit/3-file PR description. It already includes the worker untracked-file/failed-cycle changes, archive how-to/capability routing exclusions, small first-turn prompt cap and timeout diagnostics. These were inherited, not reimplemented. Current216 head was re-read unchanged before closeout.

- #196 `126309f9a7069c7e7b5bea3e2082462cab4f9f9c`: historical owner/signing lineage; not an update signed by the Continuum Lokal key.
- #206 `979f1e3c3dafb5e0414ac5c2bbae54991c4369d3`: divergent bounded-import/update-wrapper research remains separately accessible; no blind merge or claim it explains current chat timeout.
- #215: retained through216 ancestry. Its historical1169 JUnit/156 Node pass is bound to414ce4a2, **not this source**.
- #217 `7aeb670d8965dbc4d7dec9abbb0afd750100d2f6`:5 commits/4files on historical196 lineage. Owner reports real Fold7 Unit/lint and3Node checks PASS, not rerun here. Its bounded SAF readback idea was adapted into the already existing new-app exporter. No package/build-history merge. Its old-app export cannot be deployed without the matching original signing key.
- #219 `6d86fe2afec79f4da1f6768eb20d5d3dc0473121`: exactly the2 rescue source/test files selected and remote content matched. Original branch/handoff preserved.
- #220 `61e3aefc8e97d490306165e7b7c15f47959650c6`: exactly worker + process-test files selected and reviewed. Original history preserved.
- **#221 `3a5c91446ca3cc13e5f311ef13d9983978705ed1`: DO_NOT_INTEGRATE.** The broader callback rewrite holds a Kotlin monitor across native calls while native error callbacks can run under a scheduler mutex acquired by cancellation. Potential lock inversion requires redesign and real cross-thread tests. Its6JUnit cases are NOT_RUN and were not imported. Only the independent, minimal whitespace correction was applied to the original runtime.

No force-push, reset, main merge, branch removal, app installation, source-data deletion, key change or permission grant. The target remains `at.heinz.deviceai.continuumlocal`. Installed code27 is owner-reported; a later approved update needs a versionCode greater than27 (normally28), original local certificate and full local checks.

## Implemented source connections

### 1. Read-only rescue of the original ledger

`scripts/continuum-generation16-rescue.sh` is an owner-invoked Bash/Node helper with no new dependency. It requires an **already running local loopback ADB server** and exactly one authorized USB/local-loopback device, or explicit supported serial. It ignores remote-server/implicit-device environment overrides. It never starts/pairs/connects ADB, changes permissions, force-stops an app, signs or installs.

Fixed source: `at.heinz.deviceai.mentorpaired`, `files/learning-core-v2/experiences-v2.bin`. It checks nonroot run-as UID/private cwd/regular-file parents, reads two bounded captures using exec-out, and retains both in a newly created0700 directory under `$HOME/.continuum-generation16-rescue/`; files are0600 and fsynced. Equality, LEX2 framing, whole-payload checksum, all record enums/identities/content hashes, timestamps and expected256 count are checked. Public output contains only counts/size/hash/path. Metadata includes counts by type/status and canonical owner-fact/revocation counts. Failure retains partial local captures and says FAILED; the original app/primary/lkg remain untouched.

The Node verifier is a separate transport audit, not the production memory engine. The new main-app review additionally uses the **actual existing Kotlin codec**. A matching checksum does not authenticate source identity/trust. No raw private file was available to this session, and no256-record rescue has been performed.

### 2. Original LEX2 -> existing personal-learning review

`FileLearningExperienceStore.inspectSnapshot` exposes a bounded **read-only** call to its existing private codec. `PersonalLearningTransfer.inspectDocument/previewDocument` accept either CLPT export or original LEX2 bytes and retain the complete record set. Original LEX2 has no package field, so source is honestly labelled `unverified.legacy`.

The existing main-app One-App migration center now opens both formats. It shows total source entries/revocations/hash separately from eligible owner-fact proposals. Shared gates remain: target revocation blocks reactivation even by a newer-looking backup; incoming tombstones remain reviewable; research/commands/audits gain no fact/device authority. **There is no apply API and no automatic import**. Review does not change files or restore consent. Capacity256 remains explicit; this checkpoint does not solve long-term active-ledger eviction.

The existing SAF exporter now closes/flushed output, reopens through the same provider, uses bounded reads and requires byte equality before saying backup confirmed. This is selected integration of217 behavior in the new app, not deployment of217 onto the historical package. A provider which cannot reread yields unconfirmed export; original data is preserved.

### 3. Native deltas -> visible normal-chat draft

The real `generateLocal` TextDelta path now renders a transient draft in the existing chat pane, bounded8000chars and throttled250ms. Displayed first-text/elapsed/chunk values derive only from actual callback events. Label explicitly says ANTWORTENTWURF / NOCH UNGEPRÜFT and uses the loaded model ID. Unique token, active cancellation identity/state and Activity checks reject stale updates; Stop synchronously clears; finally removes only that generation's draft.

Drafts do not call TTS, addAssistantMessage, memory writes, evaluator or ToolGate. The original final orchestrator/quality/authorization path remains. The normal response is still published only through that path. **This wiring is not a Fold7 streaming demonstration.**

Minimal native correction: whitespace-only deltas survive (`isEmpty`, formerly `isBlank`), preserving spaces/newlines/code indentation. No template/token/backend/model/runtime replacement was integrated.

### 4. Worker shutdown -> lease lifecycle

The same existing offline test worker now waits for its scheduled group SIGKILL escalation after leader close before resolving/releasing the checkout lease. Two authored process regressions use a real resistant synthetic descendant for timeout/SIGTERM. Existing clean tracked/untracked checkout check, finite resource budget, original backlog/CCC1 and failed-cycle exit remain.

This is health/test-worker hardening. It is not generative autonomous coding. Worktree/process groups are not an OS sandbox; escaped process groups and arbitrary Termux-UID access to signer material remain outside this fix. No new queue/daemon/remote shell was added.

### 5. Canonical QA and open source continuation

`continuum-fusion-full-regression.sh` now includes the3new script suites and existing2worker suites, alongside unfiltered Android JUnit/configured NewApi lint and required public CRE1/SWB1/SWB2 parity. No signing env is admitted. Existing separate QA identity/task restrictions remain; no QA APK/install is permitted. QA's existing test version27 is not a release artifact/version decision.

All changes are ordinary Git commits in the official repository; source is editable, descendant branches remain open and all selected content was fetched again after publication.

## Files changed by this checkpoint

- `AgentActivity.kt`: existing personal SAF action and transient native-chat preview.
- `learning/ContinuumLearningExperience.kt`: internal pure snapshot inspection hook only.
- `learning/PersonalLearningTransfer.kt`: original-ledger format detection and shared review.
- `localmodel/LiteRtLmLocalModelRuntime.kt`: minimal whitespace admission only.
- `learning/HistoricalLearningLedgerTest.kt`:5authored actual-codec JUnit methods.
- `scripts/continuum-generation16-rescue.sh`, `continuum-generation16-rescue.test.mjs`:24authored fake-adb process cases.
- `scripts/continuum-historical-ledger-review.test.mjs`:3authored source contracts.
- `scripts/continuum-local-chat-streaming.test.mjs`:4authored source contracts.
- `scripts/continuum-local-development-worker.mjs`, `.github/continuum/local-worker-process.test.mjs`: shutdown wait +2authored process cases.
- `scripts/continuum-fusion-full-regression.sh`: suite inclusion.
- This handoff and `docs/evidence/continuum-generation16-20261029/*.json`.

## Evidence and actual execution

[Session/tool outcomes](../../evidence/continuum-generation16-20261029/session-checks.json) · [exact12source blobs/readback and static checks](../../evidence/continuum-generation16-20261029/source-readback.json).

| Check | Result | Actual limit |
|---|---|---|
| Official GitHub/private-git fetch, heads/diffs | PASS | Read-only inventory |
| Exact source readback12files | PASS | Byte/text identity, not compilation |
| V8 parsing of4selected script bodies | PASS | Imports/import.meta adapted for Function parser; no Node/process semantics |
| Native worker Function-parser attempt | SKIP | Parser rejects module/shebang; no source failure inferred |
| Independent migration + preview source review | PASS | No blocking source issue identified; not runtime proof |
| Broader221native rewrite review | BLOCKED | Lock-order risk found, excluded |
| `adb devices -l` in original cloud shell | UNAVAILABLE, exit127 | adb absent here; owner Termux unaffected |
| New worktree/next shell command | UNAVAILABLE, no exitcode | Exec transport disconnected; later: No usable execution environment is available |
| New5JUnit /33Node test cases | NOT_RUN | Execution environment UNAVAILABLE |
| Full Android compile/JUnit/lint, Node/parity | NOT_RUN | No usable executor, no substitute stubs/CI |
| Fold7 run-as/data/Gemma measurements | NOT_RUN | No authorized device route |
| Sign/APK/install/training/weight promotion | NOT_RUN | Not attempted |

The provider status endpoint continued to describe a running environment, but the selected shell executor was unavailable. No new toolchain was provisioned, and previous tool versions/results were not relabelled as current execution. Test-first commits are saved, but **no red/green cycle was observed**.

## One immediate owner action: reviewed read-only rescue

First enable Android's normal wireless debugging/pairing locally, only on a trusted private network, or use an already authorized USB device. No public5555/debug daemon and no code/key sent to chat. This helper deliberately supports USB and connected loopback serials, not mDNS/192.168.* serials. If loopback wireless transport is unavailable, stop; no public-IP fallback is configured.

Then, in Termux, this single block fetches the exact source, runs synthetic rescue tests, and only if they pass invokes rescue. It refuses a dirty checkout; no APK is installed:

```sh
cd "$HOME/device-ai-fold7-candidate" &&
test -z "$(git status --porcelain --untracked-files=normal)" &&
git fetch origin sol/continuum-generation16-gemma-20261029 &&
git switch --detach 36b8d5a1643b435d691cd1605273d72f34ed9f9d &&
node --test scripts/continuum-generation16-rescue.test.mjs &&
bash scripts/continuum-generation16-rescue.sh
```

If no server/device is authorized, the helper fails safely. Android pairing and its ephemeral code must be completed **locally by the owner**. `adb pair` uses the port from Android's pairing dialog; `adb connect` uses the separate debugging port. Neither port/code can be inferred remotely. After rescue, disable wireless debugging if no longer needed. Keep private captures off GitHub; make a separate owner-controlled encrypted second-medium backup after success.

If RUN_AS_UNAVAILABLE: historical APK may be non-debuggable; no debug/re-signed replacement is a solution. The repository manifest has `allowBackup=false`, so ordinary adb-backup is not a promised fallback. Existing owner-invoked archive/SWB1 exports remain available. A historical full-ledger exporter requires the missing original signing key;217cannot be installed using the Continuum Lokal key. No legitimate complete private-data extraction is proven here.

## Next developer gate

On the same clean exact source, with the already installed JDK17/SDK36/Gradle8.11.1/native ARM64aapt2 paths:

```sh
bash scripts/continuum-fusion-full-regression.sh "$(git rev-parse HEAD)"
```

It runs actual unfiltered JUnit/lint, parity and the named Node/process suites, outside the signer lane. Preserve raw output/exitcodes and repair genuine failures before release. No separate setup or source-only substitute. After successful checks and separate owner approval, the existing standalone builder may build28 with the original local P12; no build/sign/install was attempted here.

## Unresolved product goals, without success claims

- Historical256private records, bootstrap facts/chats/settings and all4installation data: not accessed, not rescued, not migrated. Review is not apply. Source-independent authenticity and reversible, per-fact approved application remain future work.
- New556record/374748byte ZIP SHA256 `92e95ac2708d192bd2596cd649db7491fc66ab59481d8861ef906ac2d91c24dc` and SWB1 SHA256 `5ba8ba7e22efc19a099148d224ab000dcd21953bb74b5d6504f49621fe56b2b6` are **latest owner-verified metadata**, not re-audited here. Earlier548source audit is a different ZIP. No raw backups or weights were fetched/published this round.
- Old335150SWB1updates are not Gemma weights. Existing frozen intent/semantic holdouts/import rejection remain. No forced import, replay, distillation or quality gain demonstrated. Auto-practice/public background research remain under existing stopped owner settings; no schedule enabled.
- Android Keystore identities/keys are app/UID-bound and may be nonexportable. No identity continuity through file copying is promised; keep original installation.
- Owner reports native Gemma direct generation33217ms on installed27. Normal-dialog first-token/total latency, memory pressure, offline stability, quality, fold lifecycle and current-source cold reopen are NOT_RUN.120s timeout remains unresolved; accounting begins after synchronous start. Native cancel/poisoned-conversation ownership remains open. Preserve raw diagnosis; do not merely enlarge deadline.
- History is appended after compiled/evidence context and may be clipped by the prompt cap;216's smaller first-turn cap is disabled when memories exist. These are source findings, not measured latency causes. No new prompt policy was guessed.
- No SWB2/CRE1/CDX1/CTX3/CSX1/LM1 promotion, new learner architecture, learned-weight quality result or true model-generated CCC1 repair demonstrated. Existing memory/provenance/ToolGate/CCC1 boundaries remain.
- No newly measured unfolded Fold7dimensions/layout redesign, remote route, signer access, APK or Play publication. Local arbitrary agent shell under the signer's Termux UID would not be secret isolation.

Rollback is review/revert of this branch's small commits on a clean checkout. Do not reset shared history or restore an old ledger as active consent. Immutable captures/original packages remain the data-preservation fallback. Largest established result this session is an openly saved, source-reviewed preservation/streaming/worker patch; **functional or intelligence improvement is not yet measured**.
