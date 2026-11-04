# Inworld fit after the owner's clarification — 2026-11-04

## Decision

Preserve and shelve the tested text-mentor prototype. Do not merge, deploy, activate it, request a key for its live test, or add it to the owner's phone. The owner wants Inworld only if it helps create the app's AI, avatar/visualizations or memory. This prototype only answers individual approved questions and does not provide those integrations. Existing Groq/OpenRouter mentors already occupy this role; no comparative benefit was measured.

Inworld as a broader platform has relevant capabilities. Their documented availability is not evidence that they outperform the existing app or fit its performance, privacy and cost constraints. No authenticated provider comparison was run.

## Documented capabilities and actual fit

| Goal | Official capability | Implication for this app |
| --- | --- | --- |
| A speaking avatar | Realtime connects speech recognition, an LLM and speech synthesis. TTS can return phoneme/viseme timing and lip-sync spans. | A potential external speech component. The Android renderer, animation mapping, playback, interruption and lifecycle integration still need implementation. The text-mentor prototype does none of this. |
| Images and visual concepts | Inference supports image generation/editing with a compatible image model and image output. | Could provide assets or concept images. The supplied Gemma text request is not an image-generation workflow. A live rigged 3D avatar or app renderer is not established by the reviewed image-generation docs. |
| Conversation memory | Realtime can extract facts and a rolling conversation summary. Long-term persistence must be stored/restored by the application. | Potentially useful as a bounded proposal/extraction component. The existing local memory, provenance and owner-confirmation rules remain necessary; this is not a durable drop-in replacement. |
| Creating an AI application | Router exposes external model inference; Runtime supplies composable character/agent capabilities. | Could supply an external conversation model or agent component. Calling inference does not train or transfer ownership of a new local model. Quality and benefit remain untested. |

The legacy Character Studio is retired. Current character-building guidance points to Runtime templates for Unity, Unreal and Node.js. This native Kotlin app cannot be assumed to gain a complete character just by setting a Router key.

## Existing repository architecture

`AgentActivity.kt` drives the current avatar from local runtime states such as listening, speaking, researching, learning and busy. A mentor call only sets ordinary busy/thinking state; it supplies no visual asset, animation or structured avatar control.

`learning/ContinuumLearningCore.kt` already handles local learning, source observations, confirmed lessons, retrieval and controlled model imports. The prototype does not call those operations. Its text display is deliberately unverified and does not train the local model.

## Sensible future evaluation

A separately scoped evaluation could compare an optional Inworld speech/lip-sync path against the current voice system, using synthetic speech and the existing avatar. Relevant acceptance criteria would include German pronunciation, intelligibility, interruption behavior, mouth/audio timing, latency and measured cost. Memory extraction would need separate factual-accuracy, correction and persistence checks. Image work would need concrete desired assets and a supported image model.

These are candidate evaluation directions, not implemented capabilities or an authorization to send memory, create a new cloud dependency, or incur provider costs. No further implementation is started from this assessment.

## Primary sources checked

- [Router concepts](https://docs.inworld.ai/router/core-concepts/overview)
- [Chat completions](https://docs.inworld.ai/router/capabilities/text-chat)
- [Image generation](https://docs.inworld.ai/router/image-generation)
- [Realtime overview](https://docs.inworld.ai/realtime/overview)
- [Long-term memory](https://docs.inworld.ai/realtime/usage/long-term-memory)
- [Streaming speech API and lip-sync response fields](https://docs.inworld.ai/api-reference/ttsAPI/texttospeech/synthesize-speech-stream)
- [Character Studio retirement and Runtime alternatives](https://docs.inworld.ai/character-studio-deprecation)
- [Structured output contract](https://docs.inworld.ai/router/capabilities/structured-outputs)
