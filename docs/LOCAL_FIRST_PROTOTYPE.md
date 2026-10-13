# Local-first prototype

## Working path

AgentActivity AUTO and LOCAL use an imported LiteRT-LM model when its runtime is READY.
Input is assembled with the bounded local memory retrieval and passed to
`BoundedAgentOrchestrator`. The runtime receives only function schemas derived from
`ToolRegistry`; generated function calls become validated `ToolProposal` objects. They
never execute as model-provided shell text.

The orchestrator records proposals and delegates to `ToolGate`. Read-only tools are enabled
by the app's explicit local policy. CONFIRM tools show a confirmation dialog; DESTRUCTIVE
tools require a second, stronger confirmation. Denials return as tool observations. Approved
calls go through `DeviceToolExecutor` and Shizuku. Bounded results and denials return through
LiteRT-LM `Content.ToolResponse` messages before the model generates its final answer.
Responses are evaluated and stored as in-memory `Experience`; memory writes require an
explicit remember request and pass `MemoryWritePolicy`.

The expert interpreter in MainActivity is a separate USER-ONLY screen. It is not registered
as a tool, and `ToolProposal` rejects shell names and command-like arguments.

## Model import

Use **MODELL IMPORTIEREN** and select a `.litertlm` file through Android's document picker.
The operator supplies a model id, version and expected SHA-256. The app copies the document
to its private catalog, checks the digest and inspects capabilities before accepting it.
Only text models with CPU support are eligible. CPU is the supported baseline; GPU/NPU
capabilities are displayed only when model inspection reports them. No weights are bundled
or downloaded. Import can fail when model/runtime/device compatibility is unknown.

The UI distinguishes NO MODEL, INSTALLED, READY, LOADING, GENERATING, ERROR and OFFLINE
(the selected local-only mode). Generation waits are capped at 120 seconds; model import,
load and unload surface a 120-second UI timeout. Native work or a blocked document provider
may continue after that UI timeout, so the screen reports ERROR instead of claiming the
operation was forcibly stopped. The UI displays model id, version, inspected capabilities
and backend support. OFFLINE means the local-only route does not contact the cloud; it is not
a claim about the device's network connectivity.

## Replay and side-effect guarantee

Tool receipts are stored locally in a bounded, versioned file with atomic replacement and
checksum verification. Raw argument strings and tool output are not stored; argument values
are sanitized and outputs are represented by bounded length and hash metadata. A prior
SUCCEEDED call id is returned as already completed. A STARTED call id is blocked; FAILED,
TIMED_OUT and CANCELLED call ids are terminal and never retried. On startup, receipts in
these potentially ambiguous states are shown in a review dialog without exposing raw arguments
or output.

This is durable replay suppression, not universal exactly-once execution. Android/Shizuku
side effects cannot be rolled back or reliably terminated after process death. If a process
dies after STARTED and before a terminal receipt is durable, the outcome may be unknown and
the call is blocked rather than blindly retried.

## Validation boundary

The previously green Android workflow run `38332404439` passed backend syntax, unit tests,
`assembleDebug`, and APK upload for the earlier commit
`25d2bf1b059d0664880821de73ef83ed449ac75a`. It does not validate these newer changes. The
post-change workflow run `38336739607` on commit
`8f82df43d1c01f853600bdee2bc50324ee53de85` was `action_required` with zero jobs, so it did
not run tests, build the APK or upload an artifact. Repository workflow approval is required
before a fresh green Android run and `DeviceAI-v0.3-debug-apk` artifact can be produced.
This sandbox could not resolve Android Gradle Plugin 8.10.0, so its Gradle test/build tasks
could not run locally.

No physical-device inference is claimed. Importing and running a real model, native runtime
compatibility, tool behavior through Shizuku, cancellation behavior, performance/thermal
limits, and Fold-device layout still require physical Fold testing.
