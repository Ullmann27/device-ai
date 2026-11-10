# Android System Intelligence — work checkpoint

Owner-authorized additional development; isolated from ongoing Cinema, memory and brain work.

- Canonical feature branch: `feature/android-system-intelligence-20261110`.
- Integration target: Cinema PR #715, `cinema/fluid-light-stage-20261110`.
- Verified starting commit: `c1fdbaeab95d8f808715c180fbc77762e459a59c`.
- Integration owner for this feature: this PR's root integrator.
- EXP-08/EXP-11: reuse the existing Android tool dispatch and approval contracts.
- No main merge, signing, installation, publication, model/owner-memory change.
- #721 at `340bfb0972db1bbc76ef4fb7d0afeb00fdf45bce`: REJECT_PROMOTION; context stays disabled.
- #727 at `ff27033e36e2e6dfa552c570a0bea0f8f57fc386`: scoped host tests; full Android build unresolved; not imported or recommended for production.

## Scope and acceptance

Audit existing normal-API/Shizuku/screen paths, connect bounded read-only diagnosis,
preview-only content-based file analysis and individually approved own-cache cleanup,
use existing Cinema events/UI, and add condition-aware before/after measurements.
No second device engine, permission authority or background surveillance.
Reject promotion on permission bypass, protected-data deletion, fabricated metrics,
unbounded work, unverified shell identity or material performance regression.

## Current verification

Initial architecture audit in progress. Implementation, tests, rendering and physical
Fold7 validation: NOT_RUN. PORTIERT: no; VERIFIZIERT: no; GERÄTEBESTÄTIGT: no.

Next: compare actual callers, establish negative tests, implement through existing
DeviceToolExecutor/ToolGate/AgentActivity and source-bound native render tests.
Rollback: revert this feature's commits; existing package, assets and research gates remain intact.
