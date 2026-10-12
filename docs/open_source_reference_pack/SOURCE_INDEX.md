# Device AI — Open-Source Reference Index

Pinned upstreams for reproducible inspection before adapting patterns.

| Project | Device AI use | License | Pinned SHA |
|---|---|---|---|
| [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) | closest full Android/on-device assistant reference | Apache-2.0 | `b9c459406fb8ab6e256b4b6032e843b73a7336d7` |
| [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) | primary Android local-LLM runtime candidate | Apache-2.0 | `b1faae7b13e1f71a507df4082c934db200f48b30` |
| [llama.cpp](https://github.com/ggml-org/llama.cpp) | GGUF fallback/benchmark runtime | MIT | `8bb3cf0d216a5ae48360179f2931daf284be4d01` |
| [ExecuTorch](https://github.com/pytorch/executorch) | alternate on-device runtime | BSD-style | `ad1c24d648b5a12bb51d488573fab94d83dac39e` |
| [Letta Code](https://github.com/letta-ai/letta-code) | identity/memory worker patterns | Apache-2.0 | `c0c8ee982946d32e99844e0c698ad25217d86ba7` |
| [Mem0](https://github.com/mem0ai/mem0) | memory retrieval/evaluation ideas | Apache-2.0 | `d26ba5e4518cef7fff3c66d4f715b21adf9feabe` |
| [LangGraph](https://github.com/langchain-ai/langgraph) | state/checkpoint/retry patterns | MIT | `395c145c047aaf962a4f599582bbc8d717bd9fa1` |
| [Microsoft Agent Framework](https://github.com/microsoft/agent-framework) | tool/workflow orchestration patterns | MIT | `b53d25ed1cd2f54ee5e5e5ed548bb2c5515022f3` |
| [Reflexion](https://github.com/noahshinn/reflexion) | bounded self-review pattern | MIT | `218cf0ef1df84b05ce379dd4a8e47f17766733a0` |

## High-value upstream paths

**Google AI Edge Gallery**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/ModelManagerViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/ChatViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatScreen.kt`

**LiteRT-LM**
- `docs/api/kotlin/getting_started.md`
- `agents/skills/create-litert-lm-android-demo-app/references/inference_implementation.md`
- `kotlin/java/com/google/ai/edge/litertlm/Config.kt`

**Letta**
- `src/skills/builtin/syncing-memory-filesystem/SKILL.md`
- `src/skills/builtin/self-configuration/SKILL.md`
- `src/agent/subagents/builtin/reflection-v2.md`
- `src/agent/memory-conflict-repair.ts`

**Mem0**
- `docs/core-concepts/memory-operations/search.mdx`
- `docs/core-concepts/memory-evaluation.mdx`

**LangGraph**
- `libs/checkpoint/README.md`
- retry/checkpoint tests and state execution loop

**Microsoft Agent Framework**
- `python/samples/03-workflows/`

**llama.cpp**
- `docs/android.md`

**Reflexion**
- bounded attempt → evaluation → reflection → retry pattern only.
