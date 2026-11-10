# Cinema outcome evidence support

Owner: current support root; integration owner: Cinema root, target PR #715
(`cinema/fluid-light-stage-20261110`). Combined dialog candidate #725 must retain
this fix after exact-source integration and focused caller checks.

## Initial checkpoint — WIP / UNVERIFIED

Remote baseline: `041abb081ae7dac637d3f0fabdae525f19acf117`.
Equivalent local baseline: `409bf1118546165b228d4b33fb63330daaa5fad0`.
Shared tree: `dc17f550b84cf368c997975134e80a12bdbdb2c2`.

The real AgentActivity completion path passes TurnLifecycle.deliver() into
CockpitAnimationTriggers.turnEnded before checking the returned result and
current delivery grants. deliver() establishes one-shot lifecycle closure,
not a successful task. The trigger currently turns all unfinished work and
approval steps into DONE whenever that Boolean is true.

Plan: reproduce on the actual current classes, preserve single delivery and
scene return, and distinguish accepted response from verified task outcome.
Reuse AgentRunResult/AgentTaskEvidence and the existing bus and renderer.
No new store, engine, inference, permissions or learned authority.

Experiment compatibility: EXP-07 negative regression evidence and EXP-11
existing orchestrator/presentation contract. This is a product correctness
repair, not a promotion of research or model quality.

Tests at this checkpoint: NOT_RUN. No production source changed.
PORTIERT: no. VERIFIZIERT: source diagnosis only. GERAETEBESTAETIGT: NOT_RUN.
No main, protected #706, signing, install or release changes.

Rollback: revert this bounded support patch; no persistence migration.
