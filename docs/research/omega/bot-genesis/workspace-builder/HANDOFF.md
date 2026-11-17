# Bot workspace adapter — implementation checkpoint

Baseline: `4b881f81ddfbe38729a551d9e460e818c6b3b715`.
Independent acceptance was frozen by another session at `f0b5405e`, before this
implementation. Reviewer: `synapse_sources`, PR441. This branch owns only the
typed handoff/CCC1 binding seam, not registry, chat, UI or native model execution.

Hypothesis: existing persistent profile identities can be attached to the current
CCC1 job without a second registry, task journal, model runtime or scheduler.
The adapter must reject mismatched identity, source, request, grant, attempt,
workspace, expiry and budget before any effect. Consumption rechecks authority;
mailbox arrival alone never authorizes execution. Different builder/verifier IDs
are necessary but are not by themselves proof of independent tests.

Implementation plan:

1. A bounded immutable Kotlin envelope connects ChiefBotProfileStore to the existing
   AgentMailbox and ChiefWorkspaceWiring. A trusted canonical authority port owns
   durable reservation/claim and replay semantics. No fake Android runner.
2. A process-branded Node assignment binds the selected stable profile/workspace
   to the prepared task fingerprint and current canonical workspace request.
   Keep the historical caller compatible. The model cannot create this capability.
3. Host tests exercise admission, replay, expiry, mailbox rejection and existing
   canonical boundaries. Independent reviewer supplies its own frozen cases.

Current implementation and tests (source revisions remain separate):

- `b66534f0`: initial source. Independent reviewer reproduced three omissions:
  sender task membership, recipient profile budget, and profile/tool mapping.
- `0297b268`: those admission fixes; actual Kotlin compile and eight author
  stateful software-contract checks PASS. Authority and workspace ports in these
  tests are explicit doubles. The independent 22 original admission cases pass;
  supplemental review and artifacts are on the separate reviewer branch.
- `396ade51`: actual Docker integration PASS, one test/zero skips, about9.1s.
  The test seeds20 profiles through the real Kotlin store. Builder observations
  carry `bot-forge`; independent candidate RED/GREEN and final fresh-worktree
  tests carry `bot-lint`, under the same canonical task/job/claim. The patch is a
  disclosed scripted fixture, not a local-model answer. First fixture execution
  completed correctly but expected the wrong status (`COMPLETED` vs existing
  `DONE`); the failed assertion is retained and only that expectation changed.
- `40508126`: actual deterministic Echo reporting from canonical MAC-validated
  records. Independent review found final grant/source revalidation races.
- `ad7747b1`: shared full grant/source checks before write and return. Fresh
  focused Node run:33/33PASS, zero skips (assignment, reporting, workspace and
  canonical runner regressions). Independent unchanged reporting8/8 and
  assignment9/9PASS after fixing the two reproduced races.

Source is implemented; host software contracts have the above scoped evidence.
Genuine model inference with the final profile assignment is owned by the
independent g04 reviewer and remains NOT_RUN in this builder's evidence. No
unknown model case has been read or solved here. Fold7 and Android IPC: NOT_RUN.
No release/signing, main merge, shell fallback or owner-data change.

## Exact API and integration

`ChiefBotHandoffAdmission` uses the existing registry/mailbox/workspace job.
Its trusted `ChiefBotHandoffAuthority` is intentionally not faked in production:
durable atomic stage claim, original-budget debit and replay semantics require
the canonical host/transport implementation. Receiving a message is not a claim.
No Android transport is installed. Aggregate per-bot tool quotas across messages
are not proven by these per-envelope caps; do not label them durable enforcement.

`createCcc1BotAssignment({task,ccc1TaskId,builderId,verifierId,reporterId,profileStore})`
accepts a trusted reader of the existing registry. Its process-private brand is
not serializable. `runCodingTask({...,botAssignment})` requires real admitted
local model execution and isolated execution. The builder's task model-call cap
cannot exceed its profile cap (defaults1); original task/deadline/claim budgets
remain shared. Both actual verifier call sites use the independently bound
executor. `reporterId` is optional; with `bot-echo`, the real report operation
uses zero model calls and writes a bounded hashed artifact under existing
`coding-evidence`. It is a deterministic report, not independent verification,
an LLM conversation, a new capability proof, or a success claim for the whole task.

The read-only host bridge `tools/genesis-workspaces/adapter-bridge.mjs` calls the
actual Kotlin profile store for every lookup. `run-host.sh` compiles the final
candidate's V2 conversation/compiler sources when present. Earlier V1 compiled
classes must not be reused as final V2 source evidence.

The combined V2 bridge needs Kotlin module friendship for existing internal
context helpers. The original focused script omitted that flag and failed before
any model call. The repaired script uses `-Xfriend-paths`, keeps all production
visibility unchanged, records dependency class hashes and requires matching clean
`app/src/main` trees. First build the source, or explicitly provide its verified
Gradle classes, for example:

```sh
SYNAPSE_BASE_CLASSES=/workspace/synapse-ui-candidate/app/build/tmp/kotlin-classes/debug \
  tools/genesis-workspaces/run-host.sh /workspace/new-genesis-host-evidence
```

The output directory must be new. Matching source trees and class hashes support
reproduction; they do not replace the recorded Gradle compilation evidence.

Author evidence paths: `workspace-builder/evidence/`; Kotlin source hashes/logs
are saved separately from compiled class artifacts. Independent findings and
raw RED/GREEN logs belong to PR441. Next step: Chief exact-source integration,
final Kotlin/Node/Android checks, then the separate frozen g04 real-model task.

The persistent workspace is `workspace-bot-X`; the real ephemeral container ID
has the `continuum-<hash>` shape. They are separate fields. Node host execution
does not install an Android IPC transport or demonstrate Android isolation.
