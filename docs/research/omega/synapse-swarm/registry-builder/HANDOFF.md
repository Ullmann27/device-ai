# Synapse identity builder

SOURCE_IMPLEMENTED / HOST_TESTED at source **2c5cd84ad7f4e19b9f52430856d311bfdf9dc18c**.
Independent review in progress, not self-certified. No Android build/device/model acceptance.
Baseline efe622dda00e620952483dedad19257efb3284e6; EXP-11/EXP-12. Draft PR426.
Independent criteria froze at c5075e649ef00f6addef338a31e25fd22edaf505 before implementation.

## Existing architecture

Historical PR384 holds the actual in-memory specialist registry inside
ChiefDelegationCoordinator (source34543f9c6cb94c8dc471099dd656ad932d690b96).
That one file and its four tests were selectively brought forward, not the full branch.
The coordinator accepts an optional `profileStore`, then requires the registered identity
to exist there. Its trusted owner approval callback remains default-deny. Declared runtime
capabilities do not become verified profile skills. Its configurable registration budget can
exceed20; ChiefWorkerPool's unchanged active/queue limits still constrain actual work.
Registration and queued request collections are defensively frozen; rejected shutdown submission
rolls back its assignment rather than displaying a phantom task.

`ChiefBotProfileStore` persists identity metadata and points at existing
`FileContinuumObjectStore` objects for private working notes. It is not a new owner fact,
experience, model-memory or execution database. Imported/persisted profiles never create a
worker, model call, inference session, grant, task, verified capability or result.
No Activity caller is changed in this lane; Chief owns UI integration.

## Contract

- `seedDefaults()` installs Atlas/Kora/Lint metadata; `seedDefaults(20)` explicitly installs
  the latest owner-requested roster, without changing existing IDs, renamed descriptions or notes.
- `create`, `get`, `page(afterAgentId,limit)`, `rename(...,expectedVersion)` and
  `updateDescription` are trusted application APIs. No generic authority/state/result setter.
- IDs are lowercase ASCII `[a-z][a-z0-9-]{2,63}`; seed IDs use `bot-` prefix.
  Namespace is `bot-$agentId`, workspace binding `workspace-$agentId`. They are validated on
  every load. Names are independently editable. Workspace is currently metadata, not a running
  container or private directory populated by this lane.
- `saveWorkingContext(requesterId,agentId,text,expectedVersion)` and
  `workingContext(requesterId,agentId)` reject different principals. Caller must obtain the
  principal from trusted app runtime: arbitrary same-UID code is outside this boundary.
  Working notes stay unverified; no owner facts or task-authorized technical experiences are copied.
- Metadata uses an atomic, checksummed, schema-versioned snapshot, process/JVM locks,
  fsync before/after replacement, strict ID/field/budget checks and optimistic version rejection.
  Unknown schema, corruption, unexpected duplicate identity/binding, lowered storage limits,
  symlinks and stale versions fail closed; no fallback or automatic damaged-file replacement.
- Initial budgets: 10,000 profiles /8MiB metadata (configurable), pages1–100,
  16KiB per working note and256KiB namespace object budget. Storage limits are not bot product caps.
  `page` loads/sorts the bounded snapshot: this is keyset UI pagination, **not indexed retrieval**.
- Shared rig/JARVIS voice/mailbox metadata expresses rendering policy, not installed execution.
  V1 verified capabilities/permissions remain empty; lifecycle is REGISTERED and task/result null.
  Existing run state must supply any active projection. No active tasks resume on restart.

## Storage format and recovery

`bot-profiles-v1.bin`: Java DataOutputStream modified-UTF magic `CONTINUUM_BOT_PROFILES`,
int schema1, int payload byte length, payload, 32-byte SHA256(payload).
Payload: int profile count; ID-sorted records each contain UTF agentId, long profileVersion,
UTF displayName/role/communicationStyle/modelPolicy/memoryNamespace/workspaceId,
int maxModelCalls/maxToolCalls, long deadlineMillis/workspaceBytes, UTF provenance,
boolean context-present and optional UTF object hash. Computed empty authority fields cannot
be forged into V1 through serialized metadata. SHA detects corruption, not hostile trusted-state rollback.

Notes: existing object-store v1 under `working-context/<memoryNamespace>/<sha>.obj`;
TASK_CHECKPOINT payload has modified-UTF marker `BOT_WORKING_CONTEXT_V1`, agentId, namespace,
int byte length and strict UTF8 text. Payload identity checks prevent swapping namespace objects.
Object data is committed and its directory fsynced before metadata pointer update. Interrupted
writes may leave bounded orphan objects; they are not automatically adopted, replayed or promoted.
All files in the namespace count toward the adapter's conservative quota, including leftover temps.
Explicitly clearing notes removes the current pointer even when the namespace quota is full;
old immutable objects remain until a separately reviewed reclamation/deletion design.

## Executed author checks

Exact source2c5cd84a: **25/25 focused JUnit tests PASS**, no skips, 0.543s reported JUnit time.
Includes9 new profile cases,4 imported coordinator cases,9 existing pool cases and3 existing
ContinuumState cases. Real production sources compiled with official Kotlin2.4/JUnit4.13.2,
JRE21 and existing verified7247e99d Android classes only for unchanged transitive dependencies;
no fake APIs. Source, dependency and class hashes and raw logs are in
`docs/evidence/synapse-swarm/registry-builder/freeze-2c5cd84a/`.

First harness failed because ContinuumSeed internal hash helpers were omitted. Raw compile
and consequent JUnit class-not-found logs are retained under `initial-harness-failure/`.
Adding the actual ContinuumSeed source repaired that harness; no implementation test was disabled.
Use `run-focused.sh` with official `CONTINUUM_FABRIC_JVM_DEPS`, real
`CONTINUUM_BASELINE_CLASSES`, and an external evidence directory. Final combined Gradle is Chief-owned.

## Remaining gates

Independent review; native Activity caller and UI; actual authenticated per-bot run contexts;
real workspace isolation and current task grants; shared-runtime serialization/admission; durable
job history; accepted task results and verified capabilities; indexed registry/memory retrieval;
reviewed reclamation, migration and owner-controlled deletion. No local model weights or Fold7
are available here. Twenty synthetic identities are not twenty executions or learning success.
Rollback removes this research increment without deleting or downgrading persisted data.
