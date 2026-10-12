# Android Patterns to Adapt

Google AI Edge Gallery is the primary concrete Android reference for:
- model manager/ViewModel separation;
- per-model chat state;
- local model import/download lifecycle;
- thinking/content message separation;
- inference metrics;
- capability-gated UI;
- agent skill/tool-call presentation;
- local task modules;
- model benchmarking.

Proposed Device AI boundaries:

```
at.heinz.deviceai.localmodel
  LocalModelRuntime
  ModelDescriptor
  LiteRtLmRuntime
  LlamaCppRuntime
  ModelInstallManager
  ModelCatalog
  InferenceMetrics

at.heinz.deviceai.agent
  AgentOrchestrator
  AgentState
  AgentCheckpoint
  AgentPolicy
  ReflectionPolicy

at.heinz.deviceai.research
  ResearchProvider
  HttpReadOnlyResearchProvider
  ResearchDocument

at.heinz.deviceai.cognitive
  identity / memory / experience / source trust / router

at.heinz.deviceai.tools
  controlled tool registry / risk gate / Shizuku adapters
```

Lifecycle: inference off main thread, explicit cancellation, no Activity leaks in cognitive state, app-private persistence, low-memory recovery, and model files separated from identity/memory.
