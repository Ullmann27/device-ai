# Agent tools and read-only research

## Current implementation boundary

Agent components are platform-neutral Kotlin except for `AndroidKeystoreIdentitySigner`.
They are not connected to `MainActivity`, the cloud backend, or an autonomous UI loop.

### Skills and tool execution

- `SkillGraph` stores typed, versioned metadata; installation rejects missing dependencies,
  duplicate versions, and cycles. Activation requires active dependencies, disabling an
  enabled dependency requires its dependents to be disabled first, and graph operations are
  auditable. `SkillImplementation` is separate from metadata. `BuiltInSkills.deviceStatus`
  composes only the existing device-info and battery tools.
- `ToolProposal` accepts only registered tool names and bounded scalar/list arguments. There
  is no model-facing shell tool. The existing leading-`!` expert mode remains a manual path
  in `CommandInterpreter` and is not representable as a proposal.
- `ToolGate` applies operator-enabled READ_ONLY policy, explicit confirmation for CONFIRM,
  and both explicit confirmation and a separate stronger policy/confirmation for DESTRUCTIVE.
  It bounds call count, timeout and returned output, supports cancellation, and records the
  decision, risk, status, timing and redacted argument shape. Free-form argument strings are
  represented by length only in audit records.
- `DeviceToolExecutorRunner` bridges registered requests to the existing executor. Gate
  cancellation interrupts its waiting worker; the legacy Shizuku AIDL operation itself is
  not cancellable and can continue until the shell service's existing timeout.

### Orchestration and cognitive integration

- `BoundedAgentOrchestrator` implements a finite typed state flow, retrieves Part 3 memories
  before model planning, sends tool observations back as data, limits steps/tool proposals,
  evaluates before storing `Experience`, and updates optional model reputation only from
  evaluated outcomes. A denied-by-default approval provider keeps confirmation external to
  model output. Reflection is structured and limited to 0–2 retries; stable identity is not
  mutated.
- `AgentCheckpointCodec` encodes only schema-versioned allowlisted fields, validates size and
  state, and never instantiates classes selected by checkpoint data. Resume is permitted only
  before tool execution, because the legacy device executor cannot provide transactional
  exactly-once recovery. Checkpoints are currently caller-managed; automatic process-death
  persistence is not wired.
- Detailed model-reputation and source-trust evidence remains in-memory. Part 3 summary
  reputation remains in its existing identity archive. Durable detailed evidence persistence
  is deferred until a separately versioned bounded evidence archive is designed.

### Research and claims

- `ResearchProvider` is provider-neutral; the simulated implementation supports deterministic
  tests. `HttpReadOnlyResearchProvider` uses HTTPS GET only, rejects non-HTTPS schemes,
  credentials, nonstandard ports, private/reserved/local DNS targets, unsafe redirect targets,
  unsupported MIME types, and oversized responses. Redirects are revalidated manually.
- Research documents carry URL, retrieval time, content hash, MIME type, source identity,
  bounded extracted content, and citations. `BaselineClaimExtractor` is deterministic;
  `ClaimExtractor` is the extension point for a future model-assisted extractor.
- Claim support/contradiction edges are deterministic starter heuristics. `CorroborationPolicy`
  requires evidence from independent source domains before a claim can be marked VERIFIED.
  Research memory writes pass through Part 3 `MemoryWritePolicy`; source trust changes only
  through an explicit evaluated observation.

### Signing and remaining integration

- `AndroidKeystoreIdentitySigner` provides EC P-256 / SHA256withECDSA signing through Android
  Keystore. It is isolated from the JVM `IdentitySigner` contract. Instrumentation execution
  is not available in this sandbox; no device Keystore run is claimed.
- No approval UI, `MainActivity` orchestration, automatic durable checkpoint store, detailed
  trust/reputation evidence persistence, or live-network research integration is included.
- No LiteRT-LM runtime/model import or local inference path is implemented in this phase.
  The pinned LiteRT-LM and AI Edge Gallery sources were inspected for API/lifecycle patterns;
  no upstream code was copied.

## Validation

- The inherited Part 3 head passed GitHub Actions run `38294495441` on branch
  `copilot/part4-agent-research`; its `:app:testDebugUnitTest` and `:app:assembleDebug` steps
  succeeded.
- In this sandbox, the installed Gradle 9.8 could not resolve Android Gradle Plugin 8.10.0,
  and the repository does not contain a Gradle wrapper. Android CI on the changed Part 4
  sources is required for full validation.
- Kotlin 2.4.20 standalone compilation of the cognitive/agent production sources against
  Android 36 stubs succeeded. This is not a substitute for Android Gradle tests/build.

## Upstream references

Patterns were adapted, not copied, from:

- `google-ai-edge/LiteRT-LM` (`b1faae7b13e1f71a507df4082c934db200f48b30`), Apache-2.0:
  `docs/api/kotlin/getting_started.md`,
  `agents/skills/create-litert-lm-android-demo-app/references/inference_implementation.md`,
  and `kotlin/java/com/google/ai/edge/litertlm/Config.kt`.
- `google-ai-edge/gallery` (`b9c459406fb8ab6e256b4b6032e843b73a7336d7`), Apache-2.0:
  `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt`.

The reference pack retains the upstream license texts under
`docs/open_source_reference_pack/licenses/`.
