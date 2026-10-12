# Device AI — Open-Source Engineering Reference Pack

This pack is the implementation guide for reusing proven public AI/agent engineering ideas in Device AI.

Priority:
1. Google AI Edge Gallery + LiteRT-LM for Android local inference.
2. llama.cpp as fallback/benchmark backend.
3. Letta + Mem0 for memory/identity/retrieval patterns.
4. LangGraph + Microsoft Agent Framework for checkpoints, retries, tool approval and orchestration.
5. Reflexion only for bounded self-review.
6. ExecuTorch as an alternate runtime benchmark.

The first rough prototype is complete only when it runs a local model on Android, persists identity/memory locally, streams chat, proposes structured tool calls, routes device actions through the existing Shizuku risk gate, can perform read-only web research when online, and retains reduced useful behavior offline.

Do not vendor whole frameworks by default. Prefer small Android-native adapters and ports. Preserve required upstream notices for any direct code reuse.
