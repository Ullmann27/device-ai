# Synapse registry independent acceptance contract

Frozen before candidate API or implementation inspection, 2026-11-03.
Baseline: efe622dda00e620952483dedad19257efb3284e6.
Reviewer: separate `synapse_registry_challenger` session; not a product bot.
Scope: EXP-11/EXP-12 persistent metadata and bounded private working context.

## Hypothesis and limits

Persisting stable identities separately from display names can preserve bot context
and workspace bindings across restart without granting tools or claiming execution.
A registry does not establish a local model, OS isolation, learning, or MΩ-1 success.
No owner data, model downloads, production signing, device installs, or main changes.

## Acceptance frozen before implementation

1. Three distinct stable identities survive a fresh store instance and fresh process.
2. Renaming changes display text only: identity, namespace, workspace, permissions,
   verified capabilities, task history, and stored private notes remain unchanged.
3. Duplicate IDs, duplicate memory namespaces or workspaces cannot silently merge
   identities. Unsafe path segments, separators, traversal, NUL and oversized values
   are rejected before filesystem effects outside the registry root.
4. Private context for A never appears in B lookups, listing or mutation results.
   A profile is not a capability: no automatic owner-fact admission, grant issuance,
   execution, polling, worker creation, or model call occurs during load/list/save.
5. Empty verified capabilities and empty permissions stay empty after creation,
   rename, serialization and restart. Descriptive role names do not certify skills.
6. Only bounded valid resource settings are accepted. Exhausted registry/context
   budgets reject changes without damaging previously committed identities/notes.
7. Pagination at 3, 20 and 1,000 synthetic profiles is deterministic and complete
   with no duplicates/omissions; restart preserves the set. This is scalability
   evidence for metadata only, not 1,000 simultaneous workers.
8. Unknown schema versions, truncated/corrupt data, invalid stored fields and
   duplicate bindings fail closed. Loading must not silently reset, downgrade or
   overwrite previously stored data. Preserve diagnostic evidence.
9. Concurrent store instances/processes cannot silently lose accepted updates.
   Atomic replacement and failed writes leave a readable committed state, or an
   explicit unavailable/corrupt state. Stale writes require rejection or safe merge.
10. Restart does not relaunch tasks, replay tools, certify uncertain completion,
    display old executions as currently model/tool active, or resurrect cancellation.
    Runtime projection must use actual existing run state; unavailable links are
    displayed as unavailable/dormant, never simulated.
11. Registry APIs are a trusted application seam, not a security boundary against
    arbitrary same-UID code. Directory bindings are not container/VM isolation.
12. Caller integration and tests must distinguish SOURCE_IMPLEMENTED, HOST_TESTED,
    INDEPENDENTLY_VERIFIED, ANDROID_BUILD_VERIFIED and FOLD7_VERIFIED.

## Test protocol

Use synthetic identities/notes only, isolated temporary roots and explicit limits.
Freeze executable adversarial cases before candidate run; record hashes, source SHA,
commands, exit codes and raw logs. Compare baseline API/caller availability without
inventing a baseline success percentage. A missing feature is NOT_IMPLEMENTED, not a
fabricated failing model answer. Review discovered failures independently, retain RED,
and rerun unchanged checks after fixes. Inspect affected existing regressions.

Budget: one focused Kotlin/JVM compilation at a time, no full Gradle or Blender;
1,000-profile metadata probe bounded to 120 seconds and 512 MiB Java heap. Stop on
corruption, unsafe path access, uncontrolled process creation or budget overruns.
No independent generalization claim after exposed cases are used for repair.
