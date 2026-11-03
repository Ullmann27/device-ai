# Existing runtime isolation, inspected before candidate

Baseline 4b881f81ddfbe38729a551d9e460e818c6b3b715.
Actual source: localmodel/LiteRtLmLocalModelRuntime.kt and LocalConversationBoundary.kt.

- `stream(full prompt)` passes `freshPrompt=true`, closes a previously used native
  conversation and creates a new one. The conversation initialized during model
  load is reused only while it is pristine and never dispatched.
- `streamThinking` creates a new conversation with tools empty and a data-only system
  instruction. Its native close completes before terminal delivery and ownership
  release. Immutable engine weights can remain loaded; native conversation state
  is separated by this path.
- `respondToTools` deliberately continues the native tool conversation. It must not
  carry another bot's independent prompt into that conversation.
- Admission is under the runtime lock and requires READY. `cancel()` sets cancellation
  state and requests native cancellation but does not mark the runtime READY. No
  simultaneous second inference may be admitted while native work remains active.
- `cancel()` is a global method without request identity. The application/controller
  must avoid cancelling an unrelated coding/research lease. AgentActivity's current
  generic cancelLocalRequest also cancels executor, research, development and other
  scopes; bot selection must not blindly call that general stop routine.
- Runtime callbacks are serialized through LocalInferenceDelivery outside native
  monitors. A controller still needs its own identity/generation binding when UI
  delivery is delayed or a selected profile/model/session changes.

Existing boundary and delivery regression tests were independently recompiled from
actual source and ran **6/6 PASS**. Their small Conversation fixture/queue callbacks
prove the software selection and delivery logic, not JNI behavior, model inference,
KV memory disposal, loaded weight sharing, resource costs or Fold7 correctness.
Raw commands/source hashes/JDK/exit/log files are in runtime-baseline/.

Candidate scope remains NOT_RUN until a frozen API/source is delivered. No production
source changed in this independent branch.
