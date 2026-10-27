# Local main-app integration handoff

Verified source integration, owner APK BLOCKED. Owner final assignment, 2026-10-27. Draft PR #196; no production merge.

Branch `sol/continuum-local-owner-integration-20261027`, based on PR #195 at `8b75080cbfb40e8e6a284ee95f65b8700d9105a8`. GitHub API verifies PR #187 now at `7665a3dcb02f9bd1fa79cb6d4b63e95b147eb9bb`; #195 already combines Gemma restore (#193) and memory evidence fixes/tests (#194). Compare exact files before any integration. Do not duplicate these paths.

Priority: HTTPS Git authentication and repeatable local owner APK build; bounded compatible integration only. No new inference, training, model download or second app. Existing owner certificate must remain `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`. Target owner package `at.heinz.deviceai.mentorpaired`, next 0.3.25/code26. Original signing key availability is not implied by repository access.

Research PR #189 is safely preserved, including the unfinished status UI, at `9f2ebecc`. Last focused core checkpoint `aafc382e`; no autonomous formatter repair succeeded (9 model requests across retained rounds; ABSTAINED). Do not integrate unverified UI or label research as a confirmed capability. No branch deletion or forced rewrite.

Code checkpoints:
- `e8ee09a91742bfed0af0aa8be9f55d8515de55bc`: intake and exact branch audit.
- `0ec3f03d1d7eb756fef86c64491880e51fa1b38c`: HTTPS helper, consistent full JDK, native aapt2 execution check, Node-LTS 24, per-version artifact reports.
- `06d5950ea26eab7bfad326c5bedbd175af0332fe`: selected #189 core patches and missing model-admission metadata handoff.
- `6c835307cb840461ad22ebee33e7be6649cd5de0`: exact local HTTPS mapping defeats stale SSH rewrites; regression fixtures match explicit admission; five current-version assertions verify both original code25 default and explicit 26+ mode. Historical signers, inventories and authorization gates are unchanged.

## Integrated and retained branches

| Source | Result |
|---|---|
| #187 `7665a3dc` | Inherited through #195, including existing main chat, native Gemma diagnostics, ToolGate, CCC1 and model format gates. |
| #193 `9a299e61` | Already present in #195; AgentActivity, restore policy and policy tests compare byte-identically. No duplicate engine. |
| #194 `f5d462f9` | Production memory retrieval and evidence-switch test compare byte-identically. No repeat import. |
| #195 `8b75080c` | Integration base; owner 0.3.25/code26 opt-in and original signer checks retained. All Android app sources remain identical to this base. |
| #189 `4e0338fc`, `aafc382e` | Selected normal source patches: complete small-file context; caller-bounded inference time; typed timeout stops blind retries; fsynced canonical report checkpoints; existing independent-verifier path. Focused tests executed after integration. |
| #189 `9f2ebecc` | Unfinished status UI and failed model experiments remain openly editable in original branch; excluded here. No new autonomous repair success claimed. |
| `sol/continuum-experiment-bridge-20261027` | Unqualified CDX wiring remains separate, as required by #194; no productive CDX promotion or duplicate CRE1. |

The actual main-app path remains `AgentActivity → CognitiveDecisionBridge → ContinuumCognitiveRuntime/ContinuumLearningCore` for SWB1, CRE1 shadow and authorized memory retrieval. `LocalFirstPolicy.afterMemory` hands permitted generation/synthesis to the existing native local runtime. Exact authorized recall retains priority. Generated tool proposals still pass the existing owner-confirmed ToolGate. Gemma restore reopens only the pinned original imported model; manual unload disables restoration. Source-path review and JVM tests are not Fold7/native inference proof.

The canonical CodingProvider now forwards only the four explicit format/runtime/digest/alternate-consent fields required by the new CLI admission gate. Previously it silently dropped them, blocking valid configured tasks. It does not authorize a fallback or make Gemma LiteRT-LM into a GGUF coder.

## Actual validation

Raw evidence: `docs/evidence/local-owner-integration-20261027/`.

| Check | Result |
|---|---|
| Builder/access behavioral + safety tests | PASS 12/12; includes JRE rejection, JAVA_HOME selection, failed HTTPS unchanged remote, stale SSH rewrite, wrong certificate refusal. |
| Focused coding/admission/core integration | PASS 121, SKIP 2 (actual optional Harness SDK unavailable). |
| Full Node suite, 238 files | PASS 1540, FAIL 0, SKIP 13. Exact file selection and skip reasons retained. |
| Backend | PASS 60/60. |
| Real Android JDK17/Gradle8.11.1/SDK36 | `compileDebugKotlin testDebugUnitTest lintDebug`: BUILD SUCCESSFUL. 1094 JUnit tests, 0 failures/errors, 3 skips. Lint is repository-configured NewApi checking. |
| Termux AIDL replacement source path | Offline `compileDebugKotlin` with `deviceAiMobileAidlFallback=true` and explicit local aapt2: BUILD SUCCESSFUL on Linux. This is source compatibility, not ARM64/device execution. |
| Gemma restore / evidence switch / memory dialogue | Real JUnit: 7/7, 4/4, 16/16 respectively. Evidence-switch uses no Gemma inference. |
| HTTPS access | Real authenticated private-repository READ_VERIFIED in this Linux environment; Fold7 session NOT_RUN. |
| Local doctor | Real DOCTOR=READY, original_keystore=NOT_PRESENT. |
| Signed owner build | EXPECTED BLOCK: original P12 missing, before Gradle build. No APK generated, signed, installed or published. |
| Historical source/signing gates | Both reject unapproved changed source. Two immutable source-approval test files are excluded from current-source QA, not modified or declared passing. |

The initial broad run found 7 regressions (5 obsolete fixed-version assertions, 2 fixtures missing explicit admission); after targeted corrections 35/35 and the full suite passed. Initial failure and final output are retained. Android QA used `-PdeviceAiMentorPreview=true` to compile the same mentorpaired source; it did not run owner-signed assembly or prove code26 APK packaging. App source hashes match #195. Native Gemma inference, restored process on Fold7, Termux HTTPS login and ARM64 APK assembly remain NOT_RUN here.

## Repeatable local entry point

On the existing Fold7 checkout, run `bash scripts/continuum-github-https.sh setup`, fetch this branch and use `git pull --ff-only`. If gh authentication is unavailable, the helper prints a bounded 300-second device-login command; complete it in the private Termux terminal. Credentials never belong in chat/Git URLs. `gh` keeps its own private local credential storage. No source lock or proprietary dependency is introduced.

Run `bash scripts/continuum-local-owner-apk.sh doctor`. Provide the **original** `deviceai-mentor` P12 via `CONTINUUM_MENTOR_SIGNING_FILE` and its password locally, outside Git/chat. Set `CONTINUUM_OWNER_LOCAL_APPROVED_SHA` to the reviewed exact HEAD, `CONTINUUM_OWNER_NEXT_VERSION_CODE=26` (must exceed installed code), then `bash scripts/continuum-local-owner-apk.sh build --offline`. The first dependency-cache fill may require `build` without `--offline`. The builder validates original certificate before and after, package/version and SHA, and retains output outside source. Subsequent updates choose increasing codes. No automatic install. Full commands are in `docs/release/CONTINUUM-LOCAL-OWNER-APK-20261027.md`.

Remaining blocker: no original private signer provisioned here; repository/API access cannot recover it from an APK. Next owner-device action is authenticated HTTPS setup, key provisioning outside Git, doctor, exact-source owner build, then device acceptance. No additional model experiments needed. Rollback: discard this unmerged integration branch; every source branch and historical artifact remains available. `main`, production, signing identity, Gemma bytes, owner memories and learning weights are unchanged.
