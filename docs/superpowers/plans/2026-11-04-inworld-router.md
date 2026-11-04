# Inworld Router Integration Implementation Plan

**SUPERSEDED BY OWNER CLARIFICATION:** The tested one-question mentor is shelved. The owner wants demonstrable value for avatar/visualization, memory or creation of the app's AI. No activation, key setup, deployment or further feature implementation is authorized by this old plan. See the final fit assessment and handoff.

> **For agentic workers:** Execute the scoped tasks below with independent review. Preserve the existing EXP-10 mentor contract.

**Goal:** Add the owner-requested Inworld Router as an optional, explicit one-question online mentor in Device AI Continuum.
**Architecture:** Android uses the existing authenticated backend mentor endpoint. Only the backend reads INWORLD_API_KEY and calls the pinned Inworld chat-completions endpoint. Reuse the current provider adapter and consent/pairing boundaries.
**Tech Stack:** Kotlin Android, Node.js native fetch, Express, node:test, existing Gradle/JUnit/Robolectric tools.
**Spec:** Owner request dated 2026-11-04; this document records the implementation choices derived from the existing code.

## Base and scope

- Repository: Ullmann27/device-ai; new branch feat/continuum-inworld-router-20261104.
- Frozen base: 0e3685a3bc6cb3a4af61988450371f11e9cb0f3c, PR #500.
- EXP-10 is the applicable existing contract. The current owner request authorizes the additional optional provider.
- Use POST https://api.inworld.ai/v1/chat/completions, Authorization: Basic followed by the unchanged server credential (no additional encoding).
- Default model: inworld/models/gemma-4-31b-it; extra_body.reasoning.effort: medium.
- Configure model and effort only on the server. Retain opt-in, authentication, question bounds, deadline, response bounds and explicit unverified text handling.
- Local model/runtime, memory writes, tools, autonomous research and release/signing remain outside this feature.
- No image/audio generation, web search, Responses API, stored routers, tool calling or streaming is needed for the existing synchronous mentor contract.
- No real key from chat enters code, tests, commits, logs or client storage. Before authenticated testing, require INWORLD_API_KEY in the backend environment.
- No merge, deployment, APK signing or install is included.

## Documentation read before implementation

- https://docs.inworld.ai/router/quickstart
- https://docs.inworld.ai/router/capabilities/text-chat
- https://docs.inworld.ai/router/openai-compatibility
- https://docs.inworld.ai/router/capabilities/reasoning
- https://docs.inworld.ai/router/capabilities/request-level
- https://docs.inworld.ai/api-reference/routerAPI/chat-completions

The supplied markdown URLs were attempted; the browsing tool rejected text/markdown on some pages. Equivalent official HTML pages and their current capability links supplied the readable contract.

## Review focus

- A missing/disabled credential or missing owner consent must never call a provider.
- Inworld must not inherit the Groq Bearer header, Groq-specific reasoning fields or provider fallback.
- Provider errors, reasoning fields, response metadata and credentials must not leak into a client result or logs.
- User input is the approved question only; extra request fields cannot add memory/history/device data.
- Unknown providers and mismatched response identity must fail closed; existing Groq/OpenRouter behavior must remain valid.

## Task 1: Backend provider and HTTP contract

- [x] Add failing tests for Basic credentials, exact endpoint/default model/effort, bounded text-only requests, provider-specific errors, missing configuration and no fallback.
- [x] Run the new tests on the baseline and record the expected failure.
- [x] Extend backend/src/groq-mentor.js and backend/src/server.js using the existing adapter/routes; add INWORLD_MENTOR_ENABLED, INWORLD_API_KEY, INWORLD_MENTOR_MODEL, INWORLD_MENTOR_REASONING_EFFORT and INWORLD_MENTOR_DAILY_LIMIT.
- [x] Verify actual Express HTTP auth/consent/strict-field handling with synthetic local upstream fixtures.
- [x] Run the full backend test suite and syntax check; publish a checkpoint.

## Task 2: Android entry and protocol

- [x] Add focused failing tests for the Inworld request/response and explicit provider selection.
- [x] Extend AgentBackendClient and existing mentor UI so the owner can select Inworld without entering its provider key on Android.
- [x] Preserve device pairing, input bounds, lifecycle guards and explicit one-off approval.
- [x] Compile the Android app and run the relevant existing/new tests available in this host.
- [x] Report host tests separately from any physical-device validation.

## Task 3: Configuration, independent review and handoff

- [x] Document backend-only configuration and a safe, explicitly invoked authenticated smoke check.
- [x] Add only necessary deployment variable declarations with provider disabled by default.
- [x] Independently review the final diff and resolve concrete defects.
- [x] Publish exact source, tests and observed evidence in the branch/Draft PR.
- [x] Check only whether INWORLD_API_KEY is present; never print it. If absent, ask the owner to configure it securely before live testing.

## Current verification status

Implementation complete. Final offline verification: backend 93/93, Android protocol 9/9, actual Robolectric UI 7/7, existing Node wiring 31/31; full Android main/test sources compiled. Independent review completed with no confirmed remaining blocker. The question-to-background-topic side effect was reproduced and fixed.

Local execution environment: INWORLD_API_KEY absent. Live verification remains blocked until the owner configures the key securely in the backend environment.
Live Inworld call, deployed backend, signed APK and Fold7 run: NOT_RUN.

