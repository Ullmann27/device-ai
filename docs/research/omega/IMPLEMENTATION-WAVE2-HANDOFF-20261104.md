# Additional implementation wave — 4 November 2026

Owner scope: exactly three additional implementation helpers, checking branch connections for a later APK reference. No release or APK build is performed in this wave.

## Coordination

- Brain/memory: inspect actual recall/context callers and ready memory repairs.
- Worker/tools: inspect task admission, dispatch and canonical worker/result connections.
- Avatar/UI: inspect ready speech/motion components and actual visible surface wiring.

Each helper has its own isolated working tree. Existing other teams' worktrees remain read-only. The first integration wave is PR #506; this wave stacks on it and reviews additional completed work without duplicating its changes.

## Source snapshots

Remote #506 at kickoff: 8abea828833d8833d40e3d3d36feaa6d3685b82e. Available local integration snapshot: e5c06e6bf657db556bc4861c7f46a55ebcd6e03a. Source/tree equality will be checked; parent test claims do not become combined acceptance. Candidate branches #505 repair, #495 callback tests, #502/#503 avatar motion/speech are inspected independently. Research-only weight changes and unverified runtime promotion are excluded.

## Initial status

WIP / UNVERIFIED. Three actual helpers started. No combined acceptance or physical Fold7 claim. Exact changes, tests, dependency decisions and rollback will be appended after execution.

## Boundaries

EXP-11 canonical orchestration and EXP-12 memory continuity remain in place. Preserve original owner package, signer, version/update route, offline/consent switches and ToolGate. No main merge, force push, deletion of other work, paid services, GitHub Actions, model promotion, installation or release.
