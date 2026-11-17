# Local reasoning and skill evidence boundaries

The same loaded local model supplies sequential internal reasoning/development roles. Production uses `LocalModelReasoningAdapter` and LiteRT-LM's isolated, tool-free `streamThinking`; no cloud fallback, external account, worker network or execution capability is added.

## Cancellation and model binding

`ContinuumReasoningAdapter.cancel()` is optional for synchronous adapters and implemented by the local adapter. Development and chat cancellation reach this boundary. Adapter cancellation is sticky for one job: create a fresh adapter for a new job. Sequential passes reuse the adapter and must keep the same model ID/version and imported SHA-256 metadata. A changed model fails closed. The hash binds loaded catalog metadata; it is not a new hash verification of the multi-gigabyte file during every pass. Existing catalog byte verification at import/load remains mandatory.

Before dispatch and during completion waits, the adapter checks cancellation, thread interruption, wall-clock deadline and monotonic elapsed time. Waits poll at 10ms intervals without relying on a native Cancelled/Completed event. Each adapter pass is capped at 60 seconds independently; the callers' existing total-job/call/prompt/output bounds remain in force. Tool events invalidate a pass, including events delivered synchronously after completion. Late text/terminal callbacks cannot append returned text. Native `streamThinking` setup and `cancelProcess` themselves may block; JVM fakes cannot prove native-call latency or lifecycle safety on a device.

The Activity retains the single-assistant lane until a worker completes. Per-turn chat cancellation prevents fallback inference or tool-recovery inference after Stop. The bounded orchestrator checks cancellation at state transitions; cancelled output cannot become a successful learned experience. ToolGate continues to govern all device actions. Cancellation cannot undo a side effect already started outside the app.

## Experience evidence

`ExperienceCompiler` counts unique experience IDs. Exact repeated records count once; conflicting records sharing an ID reject the entire compilation, before eligibility filtering. Success rate, mean evaluation score and occurrence thresholds use only unique evidence. Candidate construction rejects duplicated/blank evidence IDs and an attempt to remove mandatory owner approval. Destructive sequences remain blocked.

This remains candidate preparation, not automatic skill promotion or proof that model-evaluated records are true. The compiler is not yet connected to an owner-review UI or durable promotion transaction. Future promotion must validate provenance and execution receipts through the existing owner/memory policies. No new persistent memory writes or autonomous execution are introduced.

## Verification and limitations

Regression tests use actual project classes and scripted runtime events. They test local-model boundary cancellation/deadlines/model binding, orchestrator cancellation and evidence replay/conflicts. They do not measure intelligence, compare models or prove device behavior. Use the real `gradle :app:testDebugUnitTest --stacktrace` and `gradle :app:assembleDebug --stacktrace` for release evidence, matched to the live commit and APK workflow artifact.

No third-party implementation was copied and no dependency was added. Lumo remains outside this repository change; no integration or cross-repository authority is enabled.
