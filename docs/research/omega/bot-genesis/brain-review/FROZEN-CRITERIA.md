# BOT GENESIS independent conversation acceptance

Frozen before builder API or candidate inspection, 2026-11-03.
Baseline: 4b881f81ddfbe38729a551d9e460e818c6b3b715.
Reviewer: separately running registry challenger, now Genesis Team A challenger.
Scope EXP-05/EXP-11/EXP-12. Production files are exclusively builder/Chief-owned.

## Hypothesis

A selected persistent profile can supply bounded private conversation context to
the existing single local model runtime without mixing another bot's conversation,
owner data, grants, mutable model state or delayed output. Personality changes
wording only. Metadata roles and host test doubles are not inference capability.

## Frozen acceptance

1. Selection is bound to stable agentId, not displayName. Renaming preserves private
   context and identity; profile text changes cannot grant tools or certify skills.
2. Each model request includes only that bot's authorized private conversation/notes
   and current input. No implicit owner facts, another bot's history, shared mutable
   reflection/tool observations, cached answers or previous grants are included.
3. A trusted application principal selects the profile. User/model text cannot
   override agentId, namespace, request identity, execution grant or model binding.
4. Requests and output/stream events carry sufficient identity: bot, turn/generation,
   model/session and relevant source/profile version. Replayed/late/foreign events
   never append to another bot, become final output after cancel, or reactivate work.
5. Switching bot, model or session invalidates outstanding conversations correctly.
   Existing unrelated coding/workspace jobs must not be cancelled by a bot selection.
   Model replacement cannot persist a response under the old identity/model binding.
6. At most one real inference owns the shared runtime; admission/queue/output/context
   budgets are hard and bounded. Rejected/queued work performs no hidden inference.
   A stalled cancellation must not admit overlapping native sessions by assertion.
7. Inspect the real LiteRT runtime/new-conversation/cache implementation. Rebuilding
   a prompt alone is insufficient if mutable KV/session history is retained. Shared
   immutable model weights are distinct from private mutable conversation state.
8. Restart preserves explicitly committed private history only, clears uncertain
   live generation leases, and never replays tools or resumes stale output by itself.
   Lost/invalid session fails closed; no phantom active-model state from profiles.
9. Persisted conversation schema/object binding, corruption, unknown versions,
   stale profile revisions and revoked/cleared notes must be checked at recall. A
   stale snapshot cannot reintroduce cleared private data through summary/cache.
10. Context and output truncation never strips governing identity/grant boundaries.
    Untrusted notes and personality fields remain data, not system instructions.
11. Registry scale above twenty works without twenty loaded models or resident
    polling workers. Actual runtime/session count is measured separately from IDs.
12. Tests use real implementation classes and actual dependencies; recording model
    ports/fakes prove software routing only. No local intelligence/Fold7/model-quality
    acceptance without actual model execution and separate evidence.

## Independent protocol and budget

Freeze public-contract counterexamples before candidate execution, retain RED and
rerun unchanged after repair. Any API spelling adaptation is documented, not a
weakened criterion. Builder receives contract and source SHA, not expected outputs.
Use synthetic data only, temporary roots, bounded latches/timeouts and no unapproved
network/model download. One selected Kotlin compilation; no competing full Gradle,
model build, Blender or hardware calls. Normal branch/Draft PR; no main/signing/install.
Final exact integration and Android UI/runtime caller tests remain Chief/third-QA
owned. Exposed cases become regression material; no generalization claim from them.
