# BOT GENESIS — native runtime recovery

2026-11-03. Baseline `4b881f81ddfbe38729a551d9e460e818c6b3b715`.
Branch `codex/genesis-native-runtime-20261103`, documentation-only lane.
Prior preparation/outage checkpoint `71761204ce64f0d443d5abb2e2e7eba5bd95fde9`
is preserved by ancestry. Weekly quota: NOT_VISIBLE.

## Actual retained state

Actual exec works again; provider readiness alone was not used as proof.
No live native build exists. Two old cc1plus entries are defunct, not executing.
The previous build log ends at51%; no build.exit, native executable or
runtime-env.json exists. Configuration exit0 was retained. No blind restart.

The full retained approved model was rehashed after recovery:
1117320768 bytes, SHA256
`cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046`.
Current ownerUID1000 matches the model owner; one hardlink. Pinned runtime source
`6cb3e672e8d10fd4feab1a048bf2967c40623133` remains tracked-clean.
Exact observations are in `genesis-recovery.json`.

The CPU gate is currently reserved for independent Android UI QA. Native build
resumption must wait for Chief's explicit resource release, then use the existing
unchanged CPU/static recipe with at most2 jobs. No model inference is authorized
by a build-success result; independent canonical-task QA owns that separate run.

## Fresh remote context

Fresh PR410 head `5feb1a93ffe5db29163e33e4a0ea002ff25252fe`, PR413
`10f4339a760f8a531993598200b1c3299d3b2b97`, PR427 unchanged
`c78cf8a551a4d12f5dbb27ec3641fd4ae9e6f76d`. Their actual handoffs were consulted;
old PR bodies still contain superseded no-model statements.

The latest QA handoff records a completed original12-task run on another source
and environment, with4 independent repairs but failed original restart recall.
It separately records a known-task diagnostic on reviewed new-write memory fix
6baec1bc with one real successful fresh-process recall. The paired-memory parent
failed and its later arms did not run. These are external lane records, not
results from this builder or proof of the current combined Chief source.
No frozen task source, test body or solution was inspected. Do not duplicate the
original benchmark or convert an exposed diagnostic into a new blind success.

## Completion gate

Preserve the interrupted logs before any resume. After the original build target
finishes, require clean tracked source,12476968 runtime bytes and exact SHA256
`c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a`.
Never change reviewed admission pins to fit another binary. Only after that match
write a nonsecret runtime-env.json with actual local paths and the six-field GGUF
manifest. Pass paths to the separate challenger; no builder-authored repair patch.
No main, package, owner data, model weight, signing, device or release change.
