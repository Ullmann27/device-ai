# Independent caller review — criteria frozen before candidate inspection

Reviewer session: synapse_lifecycle_builder, now reassigned as an independent
reviewer of Chief-authored Activity/Roster integration; not its implementation author.
Date:2026-11-03. Baseline4b881f81ddfbe38729a551d9e460e818c6b3b715.
Candidate assigned7473ffb6584b70b1023b1156fca823e2a76dff36.
No candidate production source inspected before this criteria commit.

Scope is actual Activity/Roster caller wiring, shared LocalModelRuntime, private
bot-controller admission, speech/lifecycle, stale callbacks and storage I/O. The
separate UI reviewer owns its29 widget/speech tests; do not duplicate or call its
results new independent evidence here. No heavy Gradle, native build or inference.
No unknown g04 task bodies or benchmark solutions may be inspected.

## Independent invariants

1. One bot run's cancellation must not interrupt an unrelated model job after the
   shared runtime changes ownership. Admission, cancellation and callback identity
   must be correlated to the same live run and model/session instance.
2. Private working input/output/history must not enter owner-fact admission,
   shared memory, public conversation history, public summary or training paths.
3. A selected bot's recovered history must belong to its stable ID/namespace;
   rename, bot switch, Activity recreation or queued I/O must not splice another
   bot's or JARVIS's history into it.
4. Completion/streaming/error/speech callbacks from a cancelled, superseded,
   destroyed or replaced model session must have no effect on current state.
5. Runtime busy/queue admission and model/token/time budgets must be enforced at
   the actual caller. A presentation flag is not a lock; a stale status display
   cannot authorize another inference or multiply budgets.
6. Speech cancellation must correspond to the speech owner/turn; stopping a stale
   private turn cannot stop a later unrelated utterance. No stale result may speak
   after session cancellation or Activity destruction.
7. Outstanding storage/future operations are bounded and cancellation preserves
   unknown effects honestly. Deferred initialization must not silently reseed,
   overwrite corrupt data or revive old work. No synchronous unbounded main-thread I/O.
8. Errors and exceptions cannot leave the shared runtime permanently claimed or
   permit a second concurrent mutable conversation. Recovery must preserve granted
   permissions, current task identity and durable private context boundaries.

Deliver concrete source-bound findings or explicit bounded acceptance. Distinguish
source audit from executed evidence. Freeze any new executable counterexample before
applying an author repair and preserve negative results. Only reviewer docs/tools
may change here; Chief owns all candidate production files.
