# Replacement ASR readiness

Source: `c83faa2639f1521948b46a10c2438bc4281c93da`.

The full Android QA run [43344572298](https://github.com/Ullmann27/device-ai/actions/runs/43344572298), job 131620673839, failed `ResearchChatApprovalUiTest.staleActivitySpeechCallbackPreservesNewRecognition` at line 131. That is the fixture's requirement that microphone readiness be false before the new Android READY callback.

## Cause and fix

`startVoiceInput` advances `speechScopeGeneration` before `LocalSpeechSession.start` releases the previous active turn. Its old `onState(false)` callback is correctly rejected by the generation guard. However, the Activity kept the previous `voiceListening = true` until the replacement eventually emitted a callback.

The Activity now clears and refreshes readiness immediately when taking ownership of the new recognition scope. A real current READY callback is still the only path that sets the listening indication to true. Existing generation/session guards, continuous-conversation state, explicit PAUSED behavior, and permission routing are unchanged.

The same failing UI fixture now also delivers stale old readiness and text callbacks before the new READY event, and stale old stop/text callbacks after it. It retains the assertion that only the new recognition can approve the new research ticket.

## Verification status

Actual prior failure confirmed from GitHub job logs. Modified Kotlin/UI test: NOT_RUN in this connector-only worker. Requires rerun of `ResearchChatApprovalUiTest` and the existing speech/continuous-conversation tests in integration CI. No hardware or audio test claim.
