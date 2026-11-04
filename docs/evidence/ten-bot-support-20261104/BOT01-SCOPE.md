# Bot 01 — workstream ownership inventory

Read-only inventory on 2026-11-04. Observed local heads, not claims of remote or
device acceptance:

| Workstream | Last locally inspected head |
| --- | --- |
| Weights | `5461464e926228b5a225a6bda3e983db37ae60a9` |
| Brain | `00adea2b6bf7ecddb986a146a40c46bd33524a6f` |
| Avatar | `725559fab32af5ee44bb9a73a3b9a9c1d708a4e0` |
| Bots | `8d90b97c00f04aa9e055e424fc6ca869dd269f5f` |

Protected files: experimental/weight-space-20261104 and its protocol/runner;
cognitive AiContextCompiler, HybridMemoryRetriever and MemoryEcology; the new
ContinuumMemoryEventSource adapter; ContinuumAvatarRenderer, ContinuumHomeView,
avatar rendering/lip-sync and related UI tests; ChiefBotConversation,
ChiefBotPersonas, ChiefBotTaskIntake and their tests. Recovery remains with the
avatar/takeover team. None of those worktrees or unfinished files was changed.

`AgentActivity.kt` is the primary shared integration surface between bot entry
and avatar presentation. Memory events and cognitive-loop contracts also cross
brain/avatar ownership. Historical branch names in handoffs must be compared
with real current heads. The weights branch starts before the support #483 base;
do not apply its complete tree over a newer integration.

Use dedicated support evidence paths, exact source hashes and the existing shared
Gradle lock. Written coordination is not acknowledgement by another chat.
