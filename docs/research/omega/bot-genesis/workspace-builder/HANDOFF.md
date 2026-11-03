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

Current status: SOURCE_IMPLEMENTED=NOT_RUN; HOST_TESTED=NOT_RUN;
real local inference/three-bot execution/Fold7=NOT_RUN. No model case content has
been read and no heavy build/model process is started here. No release/signing,
main merge, shell fallback, owner-data changes or production claims.

The persistent workspace is `workspace-bot-X`; the real ephemeral container ID
has the `continuum-<hash>` shape. They are separate fields. Node host execution
does not install an Android IPC transport or demonstrate Android isolation.
