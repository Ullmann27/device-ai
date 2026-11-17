# Status semantics and independent producer review

Reviewed 2026-11-04 against the source carried by application commit `a8e58ae09f6f1a8f43bd0a6f3b30b7e81bb79be6`. The owner screenshot is the observation; source inspection explains its labels, without turning it into additional physical-device acceptance.

## Observations and limits

| Screen label | Actual source meaning | Limit |
|---|---|---|
| Device AI 0.3.39 (40) | `AgentActivity.systemCenterStatus()` prints BuildConfig version fields. | The screenshot shows that version opened; it does not independently attest a commit or full functionality. |
| Shizuku connected | `ShizukuBridge.isReady()` checks that the bridge started and the Shizuku and shell-service binders respond. | No device command was executed by this review. The diagnostic phrase Shell UID is not a separately measured numerical UID. |
| Gemma READY | The local load path checks imported metadata, initializes the native Engine and creates a Conversation before recording loadedModel/READY. | Successful native generation, response quality and S01 acceptance are not established. AgentActivity explicitly documents this distinction. |
| Internet ALWAYS_ON | The saved consent mode is displayed. | It is permission state, not a connectivity probe or proof of every remote integration. |
| Cloud backend not configured | `backend.isConfigured()` needs an HTTPS backend URL and a nonblank stored token; status catches exceptions as false. | Does not mean every network feature is disabled. The local model does not require this backend configuration. |
| generation 56 / verified | The startup note uses the verified persistent manifest generation; recovery would have a different suffix. | This is not an intelligence score or a count of successful training improvements. |
| 33 receipts stored | The status counts all entries returned by receiptStore.all(). | Receipts have proposed, denied, failed and other states as well as successful ones. No claim of 33 successful actions. |
| Offline job scheduled | The saved scheduled flag is true. | No guarantee of an immediate or exact-time Android execution. |
| Last state FINISHED | Historical persisted end-state; earlier RUNNING records precede their end records. | No measured learning gain or current active execution is established. |

## Source anchors

- `app/src/main/java/at/heinz/deviceai/AgentActivity.kt`: systemCenterStatus, openContinuum, and the READY/native-generation comment beside the explicit offline model check.
- `app/src/main/java/at/heinz/deviceai/ShizukuBridge.kt`: statusDetail, service connection and isReady.
- `app/src/main/java/at/heinz/deviceai/AgentBackendClient.kt`: hasToken and isConfigured.
- `app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt`: model load, Engine initialization and state transitions.
- `app/src/main/java/at/heinz/deviceai/cognitive/ContinuumRuntimeStore.kt`: checkpoint generation.
- `app/src/main/java/at/heinz/deviceai/ToolExecutionReceiptStore.kt`: receipt states and enumeration.
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumOfflineJobStatus.kt`: saved scheduled flag, historical status and bounded events.

## Independent review scope

The read-only reviewer inspected status producers and the described two-hunk UI correction. No blocking problem or required producer relying on a no-space `Label:Value` delimiter was identified. Such input would remain visible as one value even if supplied. The reviewer confirmed that INTERRUPTED and all five recognized DEFERRED phases are real stored states and recommended keeping the repair in the UI adapter.

The reviewer explicitly did **not** independently inspect the final regexp wording, replay tests, verify the final commit or inspect the publisher. The root agent inspected the final diff, actual widget reports and native captures, and verified the uploaded Git tree matched the tested tree. This review is not a substitute for the pending publisher or physical-device results. No models, scheduler, backend configuration or owner contents were changed by the review. The failed S01 no-run barrier remains active.
