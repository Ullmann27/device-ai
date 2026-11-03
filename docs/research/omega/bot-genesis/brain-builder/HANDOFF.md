# BOT GENESIS Team A — private bot conversation

SOURCE_IMPLEMENTED / HOST_TESTED at **a9acd007bc7bd485b2bda3c2db2dc2e57791af73**.
Independent final signoff pending at this writing. Root owns Activity integration and combined QA.
Baseline4b881f81ddfbe38729a551d9e460e818c6b3b715, EXP-05/EXP-11/EXP-12. Draft PR448.
Independent criteria4851d50f893119344db4d05f7309c2fd93d3f4eb froze before API/implementation.
No Fold7, local inference quality, verified coding capability or release claim.

## Actual connection and authority

`ChiefBotConversation.prepare` binds a selected stable profile and its private committed data to
an immutable turn. It calls the existing AiContextCompiler and LocalPromptFrame; root Activity
calls existing LocalModelRuntime.streamThinking through existing generateLocal/executor. The
actual LiteRT implementation creates a fresh tool-free native Conversation on the single loaded
engine, with the existing atomic READY admission. No new model, thread pool, scheduler or TTS.
This lane does not invoke a model itself: fake/host scripted completions prove adapter contracts only.

The private compiler policy suppresses **all** owner-specific seed IDs/state roots/custom genome
policy strings and tool catalogs. Owner archive memory, dialogue, research, skills and observations
are never copied. Real Continuum state is only validated as compiler input. The bot's stable ID,
name, role and style are quoted data; personality grants no tools, rights, evidence or expertise.
Model id/version/hash bind the ticket and stored provenance, not the prompt's owner-sensitive labels.

Only the selected bot's notes/history enter the frame. Previous text is explicitly UNVERIFIED;
notes/style/model output cannot authorize any action. The root must reject tool proposals and use
the fresh tool-free native method, not respondToTools or a retained native conversation.

## Public API for root integration

- `ChiefBotConversation(store, compiler=private-default)`.
- `select(agentId?)`, `invalidate()`: correlation changes only, no runtime/global cancellation.
- `prepare(agentId,request,state,model,sessionId):ChiefBotConversationTurn`.
- Turn exposes agentId/displayName/turnId/sessionId/profileVersion/model/prompt/deadlineMillis
  and `remainingMillis()` based on System.nanoTime. No injected clock authority.
- `complete(turn,response,currentModel,sessionId):Boolean`: true only for the pending exact object,
  correct selected identity/model/session/epoch, unexpired task and unchanged persisted profile.
  Accepted private answers are contextual data, not independent execution receipts.
- `isCurrent(turn,model,sessionId)`: strict bounded persisted revision check; run off the UI thread.
  A committed turn compares against its post-commit profile, not the older request version.
- `isPresentationBound`: cheap in-memory final UI correlation, **not** persistence/admission proof.
  Root must perform strict check off-UI and synchronously invalidate before accepted UI mutations.
  No instantaneous cross-process notification claim; strict reads/next preparation detect changes.
- `cancel(turn)` only invalidates its pending correlation; it never releases a native runtime lease.
- `reset(agentId)` clears that bot's current conversation pointer and invalidates selected frames.
- `store.loadConversation(requesterId,agentId)` returns immutable entries plus discardedTurns.

Root Activity must keep its existing runtime lane occupied until native termination even after
cancellation. It owns actual generation timeout/output cancellation, model transitions and UI/TTS.
Preparing a frame is not MODEL_CONNECTED or MODEL_ACTIVE evidence. Stale frames never gain success
or reactivate work. Accepted root UI edits must invalidate before enqueueing the mutation, closing
the strict-background-check to UI-presentation race for app-owned edits.

## Persistence and privacy

Registry schemaV2 retains the `bot-profiles-v1.bin` filename/magic and reads old schemaV1 records.
Each V2 record appends Boolean conversation-pointer presence, optional UTF object ID, and Long
conversationVersion after the old working-context pointer. V1 gets null/0 without resetting any
identity, name, namespace, workspace binding, permission, note or profile version. New writes use
V2 atomically under existing process/JVM locks. Unknown versions/corruption fail closed; no downgrade.

Conversation objects reuse FileContinuumObjectStore under
`conversations/<memoryNamespace>/<sha>.obj`, type TASK_CHECKPOINT. Current writes use marker
`BOT_CONVERSATION_V2`, UTF agentId/namespace, optional notes-object ID, Long discardedTurns,
Int count, then request/response as Int UTF8-byte-length plus strict UTF8 bytes, followed by UTF
modelId/modelVersion/hash. Old `BOT_CONVERSATION_V1` objects with writeUTF dialogue remain readable.
Identity and current notes-object binding are verified on every recall. This checksum/binding is
not authentication against total trusted-state rollback.

Changing or clearing notes atomically drops the conversation pointer: notes-derived past answers
cannot return through history. Rename preserves committed conversation but invalidates stale
in-flight snapshots. Reset/withdrawal needs no new conversation object and works at full quota.
Old immutable objects remain on disk but are not adopted through reset/restart; reviewed reclamation
and deletion are still open. A cleared pointer is not a claim of forensic physical erasure.

## Bounds and measured limitations

Input<=4,000 UTF8 bytes, response<=6,000 UTF8 bytes. Profile maxModelCalls=0 blocks preparation;
this adapter authorizes at most one data-only call. Native task deadline is supplied to root from
remainingMillis(); preparation/commit independently reject expiry. Atomic file fsync is not claimed
hard real-time. No model call or native cancellation is performed inside the persistence adapter.

Prompt cap is the existing **15,500** character/UTF8-byte limit. Mandatory request/profile/notes
are whole; if they do not fit, preparation refuses rather than clipping a correction. Newest whole
conversation pairs fit as atomic units; omitted history is explicitly counted and marked incomplete.
A follow-up with no retained pair refuses. Stored history is<=16 pairs/48KiB UTF8; whole oldest pairs
are dropped when needed, with discardedTurns. This is bounded context, not years of complete dialogue.

Conversation objects have a hard4MiB quota per bot. Independent pressure reached4,172,924 bytes after
90 long ASCII turns and then failed closed. Reset clears recall but **does not reclaim old objects**,
so a full store needs a future reviewed cleanup path. No unlimited-chat/storage claim. Quotas allocate
lazily; registering twenty identities does not load twenty models or create polling workers.

## Executed checks, RED and repairs

Exact sourcea9acd007: **24/24 author focused JUnit PASS**, no skips, JUnit time0.528s.
Seven conversation cases, nine existing profile cases, four existing coordinator cases and four
existing AiContextCompiler cases. Real production source and official Kotlin2.4/JUnit4.13.2/JRE21,
with unchanged real compiled baseline classes for transitive dependencies; no replacement APIs.
Raw logs, source/dependency/class hashes: `docs/evidence/bot-genesis/brain-builder/green-a9acd007/`.
Reproduce with `run-focused.sh`, official dependency dir, real baseline classes and external output.
Final combined Android compilation/lint/runtime caller tests remain Chief/third-QA owned.

Initial source3f5c293b: **18 passed,6 failed** out of24 author cases. Raw JUnit records the authoritative totals.
The existing LocalPromptFrame rejects a requested cap above15,500; our initial16,000 caused every
prepare to fail. We changed the caller to the existing cap, not the acceptance threshold.

Atfcea5908 all24 author cases passed, but independent Unicode pressure found another real defect:
Java modifiedUTF stores a surrogate pair as6 bytes, exceeding our4-byte UTF8 accounting and then
64KiB object size. StrictUTF8 text encoding in a new conversation object format fixed it while
preserving limits and whole-pair retention. Independent original and repaired logs belong to the
reviewer branch. Initial and interim author logs are retained, not overwritten or called full acceptance.

Additional independent review found isCurrent omitted persisted revision checks. Repaired checks
include current notes/profile revision, and distinguish prepared from post-commit profile versions.
The separate reviewer reported15 conversation,3 storage/migration,5 pressure and4 existing compiler
groups passing at a9acd007; final V1-object compatibility/malformedUTF8 probes were still in progress
when this handoff was written. These are contract tests, not local intelligence improvements.

## Next step and rollback

Finish independent exact-source review, integrate root Activity/roster adapters, run combined
Android/JVM/Node checks and device acceptance. Measure actual model behavior/resource costs separately.
Add reviewed conversation-object reclamation and task-authorized experience use before long-lived
unattended bots. No main merge/signing/installation/publication/owner-data mutation occurred here.
Rollback code must not reset or silently downgrade newer persisted schemas or revive old pointers.
