# Fusion IV — source-first open-source integration

Isolated worktree /workspace/continuum-fusion-iv, branch research/continuum-fusion-iv-20261102; exact basecee7d2c974ae9e01ec41edfc03ee41b2b582c7bb (PR344). Baseincludes allSynapseIII corrections incl independently found source-binding race. Its exactsourceRender dep-dbk0b9942hec73e8o9cg is RUNNING at fork, never assumedPASS. Parentold0278PASS is historical. ExistingbroadNode636/639:3legacyreleaseapprovalfails, tests retained.

Owner37/80062368 installation/package/signature/knowledge/weights unchanged. This worktree prevents modifying files read by SynapseIII host/Render jobs. No main/forcepush/release/automatic installation.

## Audited upstream pins (GitHub API, actual source fetched before reuse)

- tokenarc/open-jarvis:04873998933830ce582018fe4664ebce5ec64d2f, MIT (Copyright2024TokenArc).
- open-jarvis/OpenJarvis:45504acbb49459993d60e19d5b37c85fbc0c35ed, Apache-2.0.
- Dix01/JARVIS:dff115ca4f30b74240fc6199b55b4770474e7ac8, MIT (Copyright2025Dix01).

Actual selected source downloaded read-only through connector to /workspace/upstream-audit. No upstream binaries/install scripts/servers/model calls executed. Pin fullsource/license/dependency/testing results before copying code. Preserve notices and changed-file provenance for any adapted algorithm.

## Initial findings and bounded implementation targets

I: Continuum has ToolGate/receipt/cancellation/BoundedAgent plus DeviceToolExecutor launch_app (Shizuku monkey command), but NO AccessibilityService/ScreenReader yet. A dispatched launcher command is currently reported successful without observing foreground state. TokenArc has useful root traversal/action shapes, but unbounded BFS/recursive screen extraction, problematic node recycling, unconditional success for some actions, no lifecycle cancellation, and source constructor mismatches; do NOT import its full executor/agent/app. Minimal integration should reuse safe traversal/launch concepts in existing ToolGate route, require explicit user-granted service, verify fresh postcondition, bounded lifetime/singleflight/STOP, no transactional taps or automatic external content authority.

II: Existing Android recognizer uses EXTRA_PREFER_OFFLINE (a preference, not offline guarantee); TTS engine state/offline status must be stated honestly. Screen data should be per-request, bounded/redacted/untrusted, passed only through existing tool observations/firewall, not memory. No permanent microphone/camera observation. Assess local/system modality contracts before adding dependencies.

III: Existing BoundedAgent and CCC1 already own orchestration, job identity, receipts and experience. OpenJarvis loop guard offers bounded repetition/cycle detection; adapt only if measured current-model repeated-call failure improves. No Python/Rustruntime, newworker/backlog or memoryDB. No paidAPI requirement. Verified experiences remain distinct from confirmedownerfacts.

Status at opening: AUDIT/PREFLIGHT; newimplementation NOT_RUN. This handoff is extended per real code/test checkpoint. DEVICE_NOT_RUN unless actual hardware access exists. Final ownHEAD fullQA required.
