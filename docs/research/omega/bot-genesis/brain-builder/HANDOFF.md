# BOT GENESIS Team A — direct private bot conversation

Baseline4b881f81ddfbe38729a551d9e460e818c6b3b715, EXP-05/EXP-11/EXP-12.
Independent criteria frozen4851d50f893119344db4d05f7309c2fd93d3f4eb before implementation.
Status WIP / NOT_TESTED. No local model/Fold7 acceptance.

Hypothesis: immutable selected-profile frames plus version-bound private conversation objects
can connect existing profiles to the actual shared local runtime without owner/bot context mixing.
Use existing AiContextCompiler and LocalPromptFrame. Root Activity owns the actual call into
LocalModelRuntime.streamThinking, which creates a fresh tool-free Conversation on the existing
loaded LiteRT engine. READY-state admission is atomic in that existing runtime. No new executor,
scheduler, model, TTS or global cancellation path belongs to this adapter.

Prepare snapshots only the selected bot's profile, private notes and committed private conversation.
It never receives owner memory/history/research or tool observations. Caller supplies the real
Continuum governance and loaded model, with an application session identity. Switch, cancellation,
reset, model/session mismatch and stale profile versions suppress late completion. Metadata remains
unverified: a conversation answer is not a tool receipt or verified capability.

V2 registry migration reads V1, retaining IDs/names/notes and introducing a distinct conversation
pointer/revision. Existing FileContinuumObjectStore stores conversation payloads; current notes
object ID is part of their binding. Changing/clearing notes invalidates prior conversation recall,
including notes-derived answers. Old immutable objects are retained under a hard quota, not reused.

Initial test contract: identity-scoped prompt/history, current request preservation, no tool catalog,
no owner memories, V1 migration, rename persistence, note withdrawal invalidation, corrupted payloads,
concurrent/cancelled/foreign/model-replaced callbacks, context/output/resource bounds, >20 identities.
No unavailable local inference result is substituted by scripted host output.
