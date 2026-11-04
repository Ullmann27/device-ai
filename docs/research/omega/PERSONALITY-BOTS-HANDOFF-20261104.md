# Continuum personality bots — 2026-11-04

## Scope and source

Owner request: implement callable bots inside the existing Android app, with
distinct personalities, useful task understanding and access through the current
cyan/violet Jarvis design. Continue autonomously and coordinate with the other
active development conversations.

- Base: PR #483, `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`.
- Branch: `feat/continuum-personality-bots-20261104`.
- Delivery: draft PR #493, `https://github.com/Ullmann27/device-ai/pull/493`.
- Source checkpoint: `dbb8e2f7085b0140ed437502b27fac5f988ac834`.
- Current status: **implementation complete; focused/full-unit/configured-API gates GREEN; independent source review complete**.
  Broad all-widget run: **interrupted / UNVERIFIED**. Physical Fold7/native model/release APK: **NOT_RUN**.
- Actual current code already contains 20 persisted profiles, private local bot
  dialogue, address routing, ChiefWorkerPool, typed delegation and CCC1 contracts.
  A private bot dialogue currently cannot dispatch tools. A profile is neither a
  verified skill nor an independently running model.

## Coordination

This branch owns bot profiles/personality presentation, bot addressing, bot task
entry and their focused tests. It reuses ChiefBotProfileStore,
ChiefBotConversation, the existing local model lane and the existing owner task
paths. It does not create a competing memory database, worker pool, model runtime
or repair pipeline.

Other current workstreams:

- #491 / `codex/astra-jarvis-takeover-20261104`: visual integration, avatar/orb,
  AgentActivity presentation and M2.10 recovery safety.
- `feat/continuum-memory-brain-20261104`: cognitive retrieval, evidence/reasoning
  and memory-event integration.
- #492 / `research/continuum-weight-space-audit-20261104`: weights and capacity
  experiments.

Shared-file policy: AgentActivity edits are confined to the bot selection/dialogue/assignment
hooks, their imports/fields and optional task attribution inside the existing
runLocal/createToolGate path, with raw addressed input preserved. No wholesale
Activity replacement, avatar geometry, speech renderer, model weights, cognitive
retriever or recovery-store changes. Compare exact heads before combined
integration. This written coordination is not an acknowledgement by another chat.

## Design choices

Build on Kotlin/Android Views and the existing Jarvis colors/components. Expose
each bot's name, role, personality and actual availability, plus direct chat and
assignment actions. Preserve edited profiles and private per-bot context. A
personality changes wording and working style; it never grants capabilities.

Task understanding must preserve the owner's complete request and constraints.
Read-only/local dialogue can use the existing private bot lane. Any effectful
task must enter the existing owner-reviewed dispatcher and ToolGate/CCC1 path;
model text, a bot name or a suggested task cannot itself grant execution.
Show real progress/results only; unavailable execution is visibly unavailable.

Alternatives considered: a second multi-agent framework would duplicate existing
gates and state; cosmetic profile cards alone would leave assignment disconnected.
The selected implementation extends the existing profile/dialogue/task paths.

## Verification and constraints

Relevant contracts read: AGENTS.md, experiment contract/register, current Issue
#41, current-state/frontier handoff. EXP-05, EXP-06, EXP-07, EXP-11 and EXP-12 apply.

Use focused regression/negative tests, then relevant integration/UI tests,
Android compilation and configured lint on the exact source. Coordinate Gradle
through `/workspace/scratch/b3170a7ae924/gradle-build.lock`. Local host tests are
not Fold7, physical GPU, native model or speech acceptance.

No main merge, force push, paid API/runner, model promotion, permission expansion,
S01 replay, sign/release/version changes or installation. Preserve package
`at.heinz.deviceai.continuumlocal`, the original signer and current update path.
No private owner inputs or credentials in Git.

## Implemented behavior

Twenty existing stable IDs have distinct working methods and editable starter
requests, while current saved names, roles and communication styles take
precedence. `ChiefBotPersonas` contributes bounded descriptive JSON to the real
private dialogue and explicit owner-task prompt. It never grants tools or claims
independently verified competence. Custom profiles receive a general method.

The roster exposes the current name, role, style, method, model availability and
last observed task phase, plus direct conversation and assignment actions. Search
covers names/roles/styles across all stored pages. It uses the existing Jarvis
colors/fonts, one column on narrow or large-text windows and two on wide windows.
The composer requires an explicit mode and final submit; examples only prefill.

| Mode | Existing path | Boundary |
| --- | --- | --- |
| `PRIVATE_REPLY` | `runBotDialogue` → `ChiefBotConversation` → `streamThinking` | Existing isolated private context; no tools; unchanged profile call budget. |
| `LOCAL_TOOLS` | `runLocal` → `BoundedAgentOrchestrator` → `ToolGate` | Explicit owner task attributed to the bot; original request remains tool authority; existing confirmations and receipts. |
| `CODING_DRAFT` | `showCodingTaskDraftDialog` | Prefills the complete goal only; no guessed repository/SHA/path/approval and no execution. |

An explicit owner task admits at most four **actual** model calls (including
rejected-tool recovery), four tools and 120 seconds. Lower profile tool/time
limits remain effective. Private dialogue keeps its original one-model-call
budget. The complete request is bounded at 4,000 UTF-8 bytes, and coding goals
also retain the canonical 600-character limit. Oversized or malformed input is
rejected as a whole; no trailing restriction is clipped.

The owner-task prompt starts fresh and excludes owner conversation/identity
compilation, persistent owner memories, private bot history and working notes.
The native runtime remains shared. Session/profile/deadline/cancellation are
rechecked before each actual model or tool dispatch. A logical cancellation does
not release the busy lane while the runtime is still GENERATING. Status labels
come from actual orchestrator phases and tool receipts; fresh tool output is not
labelled independent outcome verification. Bot task model responses are not
promoted into owner memory or persistent learned experience.

Address forms include `@Kora text`, `@Kora: text`, `Kora: text`, multiword names
with a comma/colon, and `Rufe Kora auf`, `Sprich mit Kora`, `Wechsle zu Kora`.
Names resolve to current saved profiles across pages; duplicate names fail
closed. Addressing alone remains private conversation, not execution authority.
Unknown explicit @-names do not silently fall back to a different selected bot.

## Host verification ledger

| Run | Actual result | Evidence directory |
| --- | --- | --- |
| Existing source baseline | 62 tests, 0 failures, 0 skipped | `baseline` |
| Real private prompt RED | 10 tests, 2 intended failures | `personas-red` |
| New task API RED | Missing API compilation failure; no runtime test claim | `task-api-red` |
| Persona GREEN / roster RED | Persona+conversation 16 pass; 2 intended UI failures; 1 task fixture byte-count error subsequently corrected | `ui-red-personas-green` |
| Address RED / task GREEN | Router 14 tests/9 intended failures; task intake 6 pass | `address-red-task-green` |
| Activity assignment RED | 5 tests/5 missing-route failures; prior unsupported dialog overload corrected before this run | `activity-red` |
| First combined implementation | 114 tests, 3 render-fixture failures; remaining 111 pass | `focused-first-run` |
| Current focused GREEN | **114 tests, 0 failures, 0 errors, 0 skipped** | `focused-green` |
| Broad all-unit/all-widget attempt | Interrupted after more than 9 minutes without visible progress; no final summary; not counted as passing | `all-widget-suite-interrupted` |
| Full regular unit suite | **2,402 total, 2,383 pass, 19 skipped, 0 failures/errors** | `full-verification` |
| Configured Android lint | **0 NewApi issues**, Gradle exit 0 | `full-verification` |
| Independent source review | No concrete important/blocking defect found in scoped source; no separate test execution by reviewer | `independent-review.md` |

All evidence paths are below `docs/evidence/personality-bots-20261104/`.
The source manifest binds the working-source bytes, not merely the old local
HEAD printed by the shared build wrapper. All 1,009 application/build/test inputs
match the published source commit by Git blob hash, with no mismatches. The
focused 114 tests include 31 Android-widget/Activity tests; unit cases overlap
the full suite and must not be summed as distinct tests. There are 52 added test
cases: 31 unit and 21 widget/Activity cases. Host fixtures use synthetic text only.

Review corrections already included: fixed an invalid owner-context compiler
configuration before a ready-model assignment; guarded old composer cancel
controls; prevented retry when a callback may have accepted before throwing;
kept feedback in scrollable content for compact, enlarged-text windows; and
revalidated task session/profile during actual model-budget reservation. Another
bot now reports the shared lane as occupied while a task is running.

## Integration notes

- Roster constructor adds optional **last** parameter
  `onAssign: ((ChiefBotTaskRequest) -> Boolean)?`.
- `true` means the Activity has custody of the complete request. `false` retains
  the draft. A thrown callback leaves acceptance unknown and disables retry.
- AgentActivity adds the optional last `botTask` parameter to `runLocal` and
  `createToolGate`; ordinary owner/coding calls retain their existing paths.
- Existing bot/menu/team entry points already reach `showBotRoster()`.
- Latest inspected sibling worktrees at 2026-11-04: avatar `09e235070cb87ed6ff3dd8d327a01659ced0d396`,
  memory-brain `8a98ea4d10126041b1a43cb7623c0f324ab18ec1`, combined avatar/memory
  `fab5f094862c5abae259196f6be1d7a8bc80a75b`. These are observations, not acknowledgements
  or combined acceptance. Git merge-tree previews with the pinned avatar and
  combined avatar/memory heads completed without textual conflicts (recorded
  in `integration-preview.json`). No branch was merged, no sibling worktree
  changed and no combined build was tested. Recheck exact heads before merging;
  AgentActivity overlaps the avatar workstream. Do not replace its complete file
  from this older base.

## Delivery and remaining acceptance

The bot implementation, source-bound evidence and handoff are delivered through
draft PR #493. Focused host/widget tests, full regular unit tests, configured
NewApi lint, independent source review and visual inspection are complete.
The broad all-widget attempt did not complete and is not a passing gate; its
cause was not established. Combined integration needs its own acceptance. No physical
Fold7, native LiteRT inference/quality, speech, GPU, installation or combined
release acceptance is asserted by these host tests.

Revert only this branch's new commits to roll back. Do not reset shared branches.
