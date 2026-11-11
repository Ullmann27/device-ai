# Thermal root-cause repair — 11 November 2026

## Authorized scope and integration

The owner requests exclusive investigation and repair of excessive heat during local
model loading and questions, with exactly three development helpers. Preserve model
quality, context, features, visible rendering and useful throughput. Remove redundant
work and lifecycle defects; do not substitute throttling for a root-cause repair.
Existing Android thermal protection remains mandatory.

- Integration owner: this thermal coordinator.
- Canonical target: `integrate/omega-apk54-20261111`, PR #769.
- Verified remote baseline: `f88d7d1936b9c0d53c176a0af31108fb863199e6`.
- Baseline tree: `ef75ff93f7d3535cdc66b55fe2cb43380fb70773`.
- Thermal branch: `fix/thermal-root-20261111`.
- Installed Code53 source reported in #670: `050a57cb019dcc1b7fe6cfa2fb5c11176b048e05`.
- Source issues: #569 (heat), #670 (actual owner observations).

## Work allocation

1. `fix/thermal-inference-20261111`: model/catalogue verification, native generation,
   cancellation and repeated inference. No AgentActivity edits without coordination.
2. `fix/thermal-render-20261111`: actual frame/render lifecycle and redundant work,
   preserving visible output and configured frame cadence.
3. `fix/thermal-background-20261111`: schedulers, periodic telemetry, learning and
   background lifecycle. No unnecessary compute or background retries.
4. Coordinator: Activity entry/exit path, independent review, regression validation,
   evidence and canonical integration. No unrelated memory, graphics or feature work.

## Evidence at initial checkpoint

The owner has reported real severe heating. #569 records a Code53 thermal guard stop;
no controlled CPU/GPU/power trace establishes the full physical cause. Prior render
budget changes and a two-thread runtime did not establish a device-level repair.

Two source candidates are under investigation, not yet accepted fixes:

- Loading verifies the full model through catalogue lookup and then apparently repeats
  that verification through `modelFile`.
- The five-second dashboard collector creates a learning core and obtains a status
  that may reload/evaluate unchanged local research weights repeatedly.

No new code repair or PASS is claimed at this checkpoint.

## Acceptance

For each change preserve an unchanged-source failing behavior test, the minimal fix,
passing neighbor regressions and exact source identity. Measure operations avoided
without inventing watts, battery savings, temperatures or Fold7 speedups. Keep
integrity verification, consent, cancellation, model outputs and thermal guards.

Physical Fold7 A/B temperature, CPU/GPU, first-token and completion latency, battery
power and STOP-to-native-exit remain `NOT_RUN` until a device channel is available.
Do not run prolonged tests on an already hot device. No signing, installation,
main merge, public release or paid CI job is part of this repair checkpoint.

## Status

`AUDIT_IN_PROGRESS`; no APK; no device-level thermal resolution claimed.
