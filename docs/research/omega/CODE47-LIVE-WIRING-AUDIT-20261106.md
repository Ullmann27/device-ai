# Code47: owner-dialogue and workscene wiring repair

Status: SOURCE_AUDIT_COMPLETE / REPAIRS_IN_PROGRESS / FINAL_QA_NOT_RUN.

## Fixed sources and acceptance boundary

- Published Code46: `577f0931e7937ffcac02dc855a540f093facdb3b`.
- Audited primary Code47 M2: `9ee7535bb131504cc85419afd30f446089a40171`.
- Reviewed repair base: `5572edfbb02570ae479a8f0f72179f5ca33027c5`, [PR578](https://github.com/Ullmann27/device-ai/pull/578).
- Code43 release source identified from the release handoff: `ff8f6070bb3a6f4dfbb6215ce3e3e269974c1d53`; visual comparison is in progress, not yet a concluded regression diagnosis.

The Code46 APK passed packaging, hash, original-signature and release-catalog checks. The owner's subsequent device observations identify missing natural app actions, absent workscene presentation, weak work-directed head movement, inconsistent self-description, context loss and answer interruptions. Packaging success does not establish those behaviours. No new physical Fold7/GPU/model/audio acceptance is claimed here.

Existing reviewed fixes in the base address active decision conflicts, committed-dispatch journal starts and the clock-tail test contract. They do not repair the new dialogue/workscene findings below. Preserve all three fixes during integration.

## Verified source connections and defects

| Path | Observed source behaviour | Required repair/check |
| --- | --- | --- |
| Owner input → app action | `OwnerAppLookupIntent` recognises opening phrases, but `sendCurrentInput` returns after `runExplicitAppLookup`; that path executes only `find_app`. | Distinguish finding from opening; resolve a real candidate and continue through the existing confirmation and ToolGate path. Preserve the original compound request. |
| Composer → workscene | Focusing the composer selects CHAT; adding a user message normally keeps CHAT. `presentToolScene` returns outside START and chat layout refresh closes the window. | Current authorised work must have a visible presentation from normal chat as well as cockpit input. |
| Tool start → gaze | `windowOpened` causes a 2.5-second glance. Sustained `workStarted` is called for document drafts, not ordinary tool scenes. | Bind start, held work gaze and terminal return to the same real work episode. |
| STOP → queued scene start | Posted tool presentation lacks the session/presentation-generation fence used by STOP. | A stale queued start must not reopen the window after STOP/session change. This is a source gap; the race still needs execution. |
| Renderer → head | Director, morph mapper, joint transforms and bone-matrix update are connected. Work look is bounded to roughly 14.4 degrees yaw; camera pullback is small. | Preserve the original model/materials; verify work-specific movement and framing. Do not claim GPU appearance from a 2D preview. |
| Tool scene → result | Current work window is an app/tool/status card. Finish receives a Boolean, not result details; foreground external-app launch can pause the Activity. | Show actual known stages/results and truthful unavailable states. An arbitrary live external-app screen is not implemented by this card. |
| Long owner turn → persistence | In-memory context rejects incomplete owner evidence; transcript storage truncates to 1,000 characters and restore can treat that prefix as complete. | Persist and restore completeness coherently; late corrections and negations must not disappear or revive older conflicting statements. |
| Previous answer → continuation | Only the first 350 answer characters are kept; “weiter”/“fortsetzen” are not specifically recognised. | Preserve a bounded, marked continuation basis including the actual answer end. |
| History → prompt | History is one optional block; if it does not fit, all of it can disappear. One long owner turn also suppresses the current history. | Fit coherent complete units within the existing prompt/UTF-8 budgets, and clarify when required context is incomplete. |
| Memory lookup → chat context | A pure memory-answer path uses the default `addAssistantMessage` behaviour, which clears conversation context. | Preserve dialogue for read-only memory answers; retain invalidation for actual revocation/change. |
| Identity → answer | Bootstrap self-description says it is not a large language model regardless of loaded Gemma. Active-model context is optional; chosen identity/profile are largely omitted. | Ground both fixed and generated self-description in actual app identity and loaded model state. Actual weight switching was not established. |
| Complete capture → model output | Capture accepts 20,000 characters, while `AgentModelOutput` rejects over 8,000 even without a tool proposal. | A narrow complete owner-dialogue path may use the existing chat budget; tool/planner/coding/bot contracts retain their stricter boundary. |
| Capture → terminal state | Deltas are silently clipped; an exact-cap answer can complete or abort depending on callback order. Stalled partial output is discarded. | Atomic text/terminal capture; genuine overflow distinguished from exact-cap completion; incomplete text explicitly labelled and never granted tool authority. |

Primary files: `AgentActivity.kt`, `cognitive/OwnerAppLookupIntent.kt`, `cognitive/LocalConversationContext.kt`, `cognitive/LocalChatTranscriptStore.kt`, `cognitive/LocalConversationFollowUpPolicy.kt`, `cognitive/LocalPromptFrame.kt`, `cognitive/AiContextCompiler.kt`, `BootstrapBrain.kt`, `cognitive/BoundedAgent.kt`, `cognitive/LocalGenerationDeadlinePolicy.kt`, and the existing JARVIS workwindow/director/renderer classes. All paths are under `app/src/main/java/at/heinz/deviceai/`.

## Three bounded repair streams

| Worktree / branch | Ownership | Integration boundary |
| --- | --- | --- |
| `codex/continuum47-natural-actions-20261106` / PR579 | Natural app actions, visible work episode, work gaze, terminal/stale scene handling, Code43 comparison | Sole agent writer of `AgentActivity.kt`; preserve existing ToolGate and app resolver. |
| `codex/continuum47-context-identity-20261106` | Context completeness/persistence, continuation, bounded identity projection | No concurrent Activity or BoundedAgent edits; provide connection fragments to the primary integrator. |
| `codex/continuum47-response-completion-20261106` | Capture/terminal state, explicit complete-owner output boundary, partial-result contract | Own BoundedAgent and deadline/capture classes; preserve committed-dispatch observer. Provide Activity connection fragments. |

No nested helper teams, second memory database, second renderer/event bus or parallel heavy Gradle builds. Source and evidence checkpoints stay in the authorised public repository; real private messages, contacts, signing keys and model weights do not.

## Acceptance cases being implemented

Synthetic cases use multiple arbitrary app labels and the configured `IdentityState.chosenName`:

- Direct, polite and differently ordered opening requests must reach the same app resolver and permission boundary.
- Finding an app and opening an app are distinct intents; quoted or negated opening requests must not execute.
- Compound opening/message requests retain the full original wording; a missing recipient, message or app requires a focused clarification or draft stage. The examples do not authorise sending any real message.
- Vague “I need something from this app” requests must not invent the intended action. Contextual follow-ups should reuse only complete, relevant dialogue evidence.
- Normal composer entry, START entry, action duration beyond 2.5 seconds, cancellation, stale starts, missing apps and missing grants need actual path tests.
- A long owner message with a late negation must retain equivalent completeness semantics before and after storage/restore.
- “weiter” must use the real last answer segment and preserve whether the prior answer completed.
- Complete 8,000/8,001-character owner replies; 8,001 characters with a tool proposal; exactly 20,000 characters under both callback orders; true overflow; stalled partial output; late callbacks after terminal/STOP/session change.

Calling private scene methods by reflection in a 2D preview alone does not prove natural-input routing, work visibility or physical renderer appearance. Focused tests must use the real production path or clearly delimit what they cover.

## Validation and release sequence

1. Capture meaningful RED cases on the repaired boundary, then execute the focused GREEN and negative cases against real production classes.
2. Independently review each branch and combine its changes on PR578. Resolve Activity fragments once, preserving generation/session/model binding and existing gate behaviour.
3. Freeze exact combined source; run required native Node/backend, Android unit/widget, lint and APK packaging checks with the established toolchain. Source mutation during a run invalidates its release claim.
4. Use only the existing original publisher for an authorised higher-version release; independently inspect actual APK bytes, package, version, signer, catalog and source receipt.
5. Physical Fold7 visual/model/audio behaviour remains pending until actually observed. No source-only test is promoted to device acceptance.

Relevant contracts: EXP-05 (real local inference), EXP-12 (identity/context continuity), EXP-02/07 (repair and independent negative checks), EXP-00 (actual APK/toolchain evidence). No research flags or model weights are promoted by these repairs.

Rollback: each stream remains an isolated branch; revert a reviewed integration commit if a regression is established. Keep Code46's verified hosted artifact available. Do not force-push another writer, merge main, retrieve the original signer or install on the owner's device.

## Restored SDK baseline — 20:25 UTC

Exact source `24547da95790ca43b0293ed06b4f2bd6e5a7814c` compiled with the real Android36 SDK, pinned Temurin17.0.20.1+1, Kotlin2.4.0 and Gradle8.11.1: `:app:compileDebugKotlin`, exit0 after4m29s, source unchanged. This run restores real dependency classes for focused repair tests; it ran no unit tests and built no APK. It is not verification of the pending new repair branches. Receipt, full log and toolchain checksums: `docs/evidence/code47-live-wiring-20261106/toolchain/`.

The first configuration attempts failed because an inherited Gradle proxy setting named an unreachable endpoint. The successful attempt uses the session's current inherited HTTP(S) proxy and CA with TLS verification intact. No repository build guard or network access policy was relaxed.

All three repair streams now have visible drafts: PR579, PR580 and PR581. Their latest targeted results and actual source commitments must be read from their own handoffs before integration; none is yet a complete final-source release gate.
