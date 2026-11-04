# Continuum Personality Bots Implementation Plan

> **For agentic workers:** use the recorded file ownership below; execute tests
> against the actual shared source and preserve source-bound evidence.

**Goal:** Make the existing twenty bots useful, distinguishable and directly
callable from the current Android interface, with an explicit working-task path.

**Architecture:** Extend ChiefBotProfileStore/ChiefBotConversation and the
existing AgentActivity owner dispatcher. Private dialogue stays private. An
explicit local tool task uses the existing runLocal → BoundedAgentOrchestrator
→ ToolGate path with the unchanged owner request. Coding tasks enter the existing
reviewable development draft path. No second scheduler or model runtime.

**Tech Stack:** Existing Kotlin, Android Views, JUnit and Robolectric.

**Spec:** `docs/research/omega/PERSONALITY-BOTS-HANDOFF-20261104.md`.

## Global Constraints

- Base #483 at `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`; own draft PR #493.
- Preserve stable bot IDs, owner-edited names/roles/styles and private histories.
- Preserve complete owner requests, especially trailing negations and corrections.
- Persona metadata is not a tool grant, verified skill or independent worker.
- Reuse the existing local model, ToolGate, CCC1 and owner execution confirmations.
- Do not send private bot history/notes into owner task execution implicitly.
- No main merge, paid API, S01 replay, weight change, signing/release or installation.
- Confine Activity changes to bot hooks and optional task-origin context; other
  chats own avatar layout, memory retrieval and model weights.

## Review Focus

- A renamed/edited or custom bot must use the current profile, not a stale seed.
- A delayed/double-tapped task cannot execute after close, replacement or cancel.
- A model-unavailable task must retain the written request and report blocked.
- A personality string cannot enlarge authority or hide a late owner restriction.
- Phone/font-scale layout must keep search, task text and actions reachable.

## Task 1: Personality and actual model prompt

Owner: personality specialist.

Files: create `chief/ChiefBotPersonas.kt`; modify `chief/ChiefBotConversation.kt`;
test `chief/ChiefBotPersonasTest.kt` and `chief/ChiefBotConversationTest.kt`, all
under existing app main/test source roots.

Interface: `ChiefBotPersonas.describe(profile): ChiefBotPersona` returns
`tagline`, `approach`, `starterRequests` and `accent` (`CYAN`, `VIOLET`, `AMBER`,
`TEAL`). `promptData(profile): String` renders bounded sanitized personality data.
The existing saved name/role/style take precedence; custom IDs get a generic
method using their current profile. Twenty stable IDs get distinct approaches.

- [x] Add prompt regressions against existing real conversation assembly; run RED.
- [x] Implement distinct methods/examples and model-visible own-voice guidance.
- [x] Verify full final request, private context isolation and role/authority limits.
- [x] Run focused tests; root reviews and publishes the checkpoint.

## Task 2: Explicit task intake and existing execution path

Owner: root. Files: new `chief/ChiefBotTaskIntake.kt` and focused tests; narrow
`AgentActivity.kt` changes with a bot task UI test.

Interfaces: `ChiefBotTaskMode` values `PRIVATE_REPLY`, `LOCAL_TOOLS`,
`CODING_DRAFT`; `ChiefBotTaskRequest` carries stable bot identity/profile revision,
complete text and mode. Intake verifies the current profile before dispatch.
Do not infer authority from free-form dialogue. Local tool work retains the
original request for ToolGate and receives only selected persona metadata as
separate bounded context. Its observed progress/result comes from the actual
existing task callback. Coding handoff remains visibly a draft until accepted.

- [x] Add regressions for missing assignment route, full request retention,
  unknown/stale profile, duplicate/stale callback and unavailable runtime.
- [x] Add typed intake and Activity connections to existing private/local/coding paths.
- [x] Keep private profile budgets unchanged; explicit owner tasks admit at most four model calls, four tools and two minutes, with lower profile tool/time limits respected.
- [x] Add cancellation/current-session checks and truthful task status labels.
- [x] Independent source review complete; focused task/widget gate and full regular unit/configured-API gate passed; publish source-bound evidence with the interrupted broad-widget limit retained.

## Task 3: Modern bot roster and assignment composer

Owner: UI specialist. Files: `ContinuumBotRosterDialog.kt`, optional new
`ContinuumBotTaskDialog.kt` / `ui/ContinuumBotBadgeView.kt`, focused widget tests.
No Activity edits by this worker.

Interface: add optional `onAssign: ((ChiefBotTaskRequest) -> Boolean)?` as the last
roster constructor parameter. Existing callers remain valid. Use persona API
from Task 1 and typed request from Task 2. Search by current name, role and style;
use actual bounded stored profiles and preserve pagination behavior.

- [x] Add failing widget checks for assignment entry, search and lifecycle.
- [x] Build cyan/violet cards, compact bot identity marks, role/method/status,
  direct conversation and assignment actions, plus editable starter requests.
- [x] Make task mode explicit; never auto-submit a starter example or on opening.
- [x] Preserve all storage diagnostics, revision checks and close/destroy behavior.
- [x] Test narrow width/font scaling; capture actual host-widget screenshots.

## Verification ledger

- Baseline: **62 PASS, 0 FAIL, 0 SKIP**, actual application source #483. Evidence
  in `docs/evidence/personality-bots-20261104/baseline`.
- RED/GREEN runs: recorded in the handoff; current focused gate **114 PASS, 0 FAIL, 0 SKIP**. Three first-run render-fixture failures and their corrections are retained as evidence.
- Full regular unit suite: **2,402 total, 2,383 pass, 19 skip, 0 fail/error**; configured NewApi lint **0 issues**; Gradle exit 0.
- Broad all-unit/all-widget attempt: interrupted without a final summary; **UNVERIFIED**, not counted as passing. Targeted widget/Activity gate passed separately.
- Independent source review: no concrete important/blocking issue found; no separate reviewer test execution.
- Textual integration preview: clean against pinned avatar `09e23507` and combined avatar/memory `fab5f094`; no combined build or actual branch merge.
- Physical Fold7 / native model / release: NOT_RUN.

## Decisions

Ruling: continue autonomously using the existing Android stack and typed task
modes — owner requests autonomous implementation and the code already defines
the canonical execution gates; adding another framework would duplicate them.
Ruling: reuse specialists after reconnaissance and parallelize disjoint source
files — this preserves their actual code knowledge; shared builds remain locked
and root alone handles Activity and remote checkpoints.

Ruling: LOCAL_TOOLS is an explicitly chosen owner task with its own advertised four-call budget; the existing one-call private dialogue budget remains unchanged. This allows a tool observation and a final answer without silently changing stored profiles. Coding draft goals keep the canonical 600-character limit.
