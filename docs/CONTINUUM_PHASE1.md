# Continuum Phase 1

Phase 1 adds provider-neutral Continuum primitives above the existing local-first
assistant. It does not replace the existing `BoundedAgentOrchestrator`, local runtime,
ToolGate, or explicit Deep Research path.

## Status

### IMPLEMENTED

- `ContinuumSeed` / `AiSeedKernel` holds typed instance, governance, state-format, model-ABI,
  public owner-reference, authorized-node, state-root, and migration-history fields. Encoding
  is deterministic and bounded; forward schema/version values are rejected. Signed envelopes
  bind the encoded seed to the existing `IdentitySigner` abstraction. Private key bytes are
  not seed fields.
- The state foundation has SHA-256 object IDs, canonical state roots, immutable object
  semantics, a bounded file-backed store, atomic local writes, integrity checks, metadata
  binding, and hash-derived filenames. Its intended deployment is app-private storage.
- `AiContextCompiler` creates bounded contexts from identity/governance, the request, supplied
  memory, active-task summary, explicitly supplied research evidence, capability metadata, and
  contradictions. It filters privacy classes, uses deterministic section priorities, redacts
  common credential formats, and escapes untrusted-evidence delimiters. It never initiates
  research.
- `ReasoningStrategy`, deterministic selection, and `DeliberationBudgetEnforcer` bound calls,
  candidate/critic/verifier passes, context/output bytes, research rounds, elapsed time, and
  checkpoint cadence. Candidate/critic/verifier/judge passes use a data-only reasoner interface
  with no tool, shell, executor, or device-authority parameter.
- The local compute foundation has typed capability manifests, least-privilege task capsules,
  owner-allowlisted registration, manifest/result verifier interfaces, expiry/revocation,
  resource compatibility checks, runtime/output bounds, and a local adapter interface. It has
  no network listener, remote adapter, or automatic enrollment.
- Cognitive RAID policy and local verification planning are metadata-only. The fixed typed
  benchmark corpus includes all ten task families and records success, support coverage, citation
  coverage, contradiction detection, invalid tool proposals, calls, harness-supplied latency,
  context bytes, correction, and recovery metrics. Optional device metrics are only recorded
  when supplied; none are fabricated.
- The model-mortality harness compares seed/governance/state/context continuity independently
  from model-output differences.
- AgentActivity has a context adapter behind `CONTINUUM_CONTEXT_COMPILER_ENABLED`, which is
  `false` by default. With the default setting, the existing local prompt path is preserved.

### CI VERIFIED

- No CI result for the new Continuum source changes is claimed until the Actions run for their
  committed head completes.
- The inherited baseline at commit `3923aac` passed workflow run
  [38349968863](https://github.com/Ullmann27/device-ai/actions/runs/38349968863). That run
  predates the Phase 1 implementation and is not evidence that this code compiled or passed
  Android unit tests.
- In this sandbox, the 14 focused Continuum tests passed using a standalone Kotlin/JVM runner
  and temporary API/JUnit stubs. `node --check backend/src/server.js` and `git diff --check`
  passed. Those checks are not a substitute for Gradle/Android CI.

### NOT YET DEVICE VERIFIED

- `:app:testDebugUnitTest` could not start because this environment could not resolve Android
  Gradle Plugin `8.10.0`; it failed before Kotlin compilation.
- `:app:assembleDebug`, the `DeviceAI-v0.3-debug-apk` artifact, Android Keystore signing of a
  Continuum seed, and the feature-enabled local-model path remain unverified until CI/device
  evidence is available.
- No physical-device inference, app-private storage recovery, real compute-node execution,
  distributed behavior, battery, or thermal result is claimed.

### FUTURE

- Encrypted replicas, internet/object storage, remote compute, network listeners, and
  distributed scheduling are not implemented. Local object contents are not encrypted by this
  foundation; sensitive/credential-bearing material must not be written into these objects.
- Replica metadata does not copy or reconstruct data. Replica restoration and owner-configured
  encrypted storage adapters require later work and security review.
- The `DataOnlyReasoner` and verifier interfaces are adapter points; the candidate pipeline is
  not yet wired to `LocalModelRuntime`.
- No Astra-class or frontier-model parity claim is made without fixed-benchmark evidence.

## Permanent boundaries preserved

- One user-facing assistant; model changes do not redefine identity.
- Model-generated raw shell remains forbidden. Device actions continue through registered tools
  and ToolGate.
- Web/research text remains untrusted evidence; ordinary local chat does not contact the web.
- No autonomous account creation, paid resource acquisition, node enrollment, replication to
  unapproved hosts, or shutdown evasion.
- No owner private key or device credential is present in a compute task.
- New loops are bounded, cancellable, and checkpoint-aware; no public/network compute path is
  introduced.
