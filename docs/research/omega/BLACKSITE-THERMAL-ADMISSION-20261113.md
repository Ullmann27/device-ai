# Shared local-model thermal admission — 2026-11-13

Base: `205ef0f587d38c06ea4094820e8f92588cfd9bd5`.
Original guard blob: `18a0377c8b593c53041c9b0b2a73109dd171937e`.

## Source finding

`LocalModelHost` exposes one thermally guarded LiteRT-LM runtime to chat, reasoning and tool continuation. Previously, `guarded()` sampled Android before adding the pending run to `inFlight`. A SEVERE callback between those operations saw no run and was lost. A second interleaving sent cancellation before native admission; that cancellation could do nothing, after which the native start proceeded.

The correction registers the pending run before sampling and latches heat for that run. A stale cool sample cannot override a received SEVERE callback. One native-owner lease follows the existing exclusive runtime. Cancellation is consumed only after observed GENERATING admission or native-start return; heat received before admission is reconciled afterward. An admitted but still-blocking start can be stopped before its start method returns.

Native start, native cancellation and user callback delivery run outside the shared guard lock. The existing `LocalInferenceDelivery` preserves callback order. A heated run cannot publish late text or successful completion, and duplicate terminals are discarded. The native-owner lease remains held until an in-progress cancel returns, preventing its global cancellation from reaching a new owner request.

Threshold remains Android SEVERE (3). Unknown thermal readings keep the prior policy; they are not labeled measured cool. Nothing is queued or automatically retried. A later explicit request can start when its current sample permits it.

## Regression coverage

The four existing `LocalModelThermalGuardTest` cases remain. Six deterministic cases were added to the same test class and production factory:

| Case | Required observation |
| --- | --- |
| SEVERE callback inside sampler, followed by stale 0 | No native start/cancel; PAUSED_THERMAL, for stream/thinking/tools. |
| Heat after guard admission but before native admission | One native start, exactly one effective cancel, STOPPED_THERMAL, clean later owner retry; all three entry points. |
| Native start blocks after admission | Thermal stop completes without waiting for start return; one cancel. |
| Native start rejects | Ownership is released; later cool owner retry completes. |
| Cancel returns after native terminal | A new start cannot steal the cancellation lease; later retry works. |
| Native completion races thermal cancellation | No late text/success; one STOPPED_THERMAL terminal, no duplicate cancel. |

No model weights, thread budget, inference limits, rendering settings, scheduler framework, signing route or CI filters were changed.

## Validation

Source review includes actual LiteRT-LM admission (`inferenceActive`/cancellation latch before GENERATING), terminal delivery and cancellation ordering.
Kotlin compilation and execution of the ten test methods: **NOT_RUN** in this connector-only session. The two threaded tests use bounded latches/joins and release blockers in `finally`.
Fold7 thermal behavior and performance: **NOT_MEASURED**.
Integration must run the existing source-bound QA on the new SHA.
