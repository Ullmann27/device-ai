# Bot Genesis — actual caller map

Checkpoint 2026-11-03. This table describes source integration, not device acceptance.
The baseline is Synapse4b; new code stays on PR439 until independent checks pass.

| Function | Existing implementation and actual caller | Current evidence / gap |
|---|---|---|
| Persistent identity | `chief/ChiefBotProfileStore.kt` → roster and direct-chat lookup | Prior registry/restart acceptance; unchanged stable IDs, pageable beyond20. |
| Bot selection | `ChiefBotAddressRouter` → `AgentActivity.sendCurrentInput/routeBotInput` → actual persisted profile | Source connected; no inferred rights from name. Independent widget/address checks pending. |
| Shared model / private state | `ChiefBotConversation` → existing `AiContextCompiler` → `AgentActivity.generateLocal` → `LocalModelRuntime.streamThinking` | Existing LiteRT engine creates a fresh tool-free Conversation. New controller host tests pass except a discovered Unicode persistence defect being repaired. Actual Android model acceptance pending. |
| Context and correction | Existing object store; versioned profile conversation pointer; notes changes invalidate derived dialogue | Whole turns retained under explicit limits; no owner facts, history, network consent or tool catalog in private frames. 4MiB immutable conversation storage is bounded and has no reclamation yet. |
| Real work / independent tests | `runCodingTask` → canonical repair/CCC1 workspace → fixed isolated executor → candidate and fresh-worktree verifiers | New independent A0:0/3 verified repairs; real local inference and actual containers, no false-success admission. New bot-principal adapter is under independent review. |
| Workspace association | `ChiefWorkspaceWiring` / `ChiefBotHandoffAdmission` → existing mailbox and trusted canonical authority | Logical persistent workspace and ephemeral sandbox remain distinct. Android IPC/strong-isolation backend is still unavailable; an injected port is not acceptance. |
| Verified experience | `ccc1-technical-recall.mjs` → existing technical journal, current grant/revocation and source checks | Reviewed durable-JSON proof fix integrated; exact-source51/51 independent regression checks. Failed A0 cases create no verified technical experience. |
| Presentation | `ContinuumBotRosterDialog` → existing Activity chat, TTS and AvatarDirector | Selected bot only; existing sphere/3D pipeline remains. Profile presence never reports tool execution. New UI acceptance pending. |
| Barge-in / stale speech | Existing UtteranceProgressListener → central playback ID → AvatarDirector | Independent baseline race reproduced:2/3PASS,1FAIL. Source serializes current-ID check and start/range/end on UI; fixed rerun pending. Bot cancellation does not call the coding-job stop path. |

## Resource and trust boundaries

One shared loaded model is used; no new scheduler or per-bot model host exists.
The selected profile limits native generation time and output. Cancellation does
not release the shared lane while native state is still GENERATING. Private chat
is tool-free; CCC1 uses the separately authorized fixed runner and canonical grants.
Owner edits invalidate selected private presentation before their writes are queued.
Cross-process instantaneous cache invalidation has not been demonstrated.

The host A0 used source `fd743adce297c0958aadce1576b80580817837f0`, approved
Qwen model `cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046`
and binary `c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a`.
Six calls were reserved; five admitted genuine observations came from six native
process attempts. Three tasks abstained, with zero false success and zero verified
technical memories. Total179.750s; task p50=58.766s/p95=65.251s (n=3), sampled
native peak2,079,204KiB. These are host measurements, not a comparative gain,
energy measurement or Fold7 result. Reviewer raw evidence is on PR443.

Fresh ADB check: no attached devices. No Android version was measured. No APK,
original-signature, update installation, camera, thermal or display acceptance is
claimed by this checkpoint. Quota remains `NOT_VISIBLE`.
