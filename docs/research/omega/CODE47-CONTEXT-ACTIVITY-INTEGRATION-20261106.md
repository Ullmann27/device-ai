# Activity integration for PR580

AgentActivity is owned by the action/UI agent. This branch does not edit it.
Base:5572edf. Apply these small substitutions to the current Activity, preserving Bot3's output gate.

## One frame and verified deduplication

Replace the existing agentTask composition:

```kotlin
val followUpFrame = at.heinz.deviceai.cognitive.LocalFollowUpTaskFrame.composeFrame(
    text, resolvedQuestion, localConversationContext.sessionEvidence(localSessionId))
val agentTask = if (botTask != null) text else codingPlanning?.ownerGoal ?: followUpFrame.task
```

After the existing promptEnd assignment and before LocalDecisionPromptFrame.assemble:

```kotlin
val continuingAnswer = botTask == null &&
    at.heinz.deviceai.cognitive.LocalConversationFollowUpPolicy.requestsContinuation(text)
if (continuingAnswer && !localConversationContext.hasContinuation(localSessionId))
    error("LOCAL_CONTINUATION_UNAVAILABLE")
val boundedConversationContext = if (botTask != null) "" else
    at.heinz.deviceai.cognitive.LocalConversationPromptFirewall.safeHistory(
        localConversationContext.render(localSessionId,
            internetMode = boundedInternetMode,
            ownerForbidsExternalNetwork = ownerForbidsExternalNetwork,
            onlineApprovalActive = modelWebSessions.any { it.isAuthorized() },
            maxBytes = at.heinz.deviceai.cognitive.LocalPromptFrame.historyBudget(
                promptBody, promptEnd, promptCap),
            requireCompleteOwnerHistory = requiresPriorDialogue,
            continuation = continuingAnswer,
            ownerTurnsInTask = followUpFrame.ownerTurnsIn(promptBody)))
```

Use boundedConversationContext in BOTH LocalDecisionPromptFrame.safeHistory and LocalContextIntegrityProbe.safeRenderedHistory.
Keep the earlier conversationContext for initial admission and prompt-cap selection. Keep requireHistory, priorOwnerEvidence and compiledContext.requireCompleteIn unchanged. Deduplication is disabled unless the complete actual framedTask occurs unchanged in promptBody.

For LOCAL_CONTINUATION_UNAVAILABLE show: "Der letzte vollständige Antwortabschnitt ist nicht verfügbar. Bitte gib den Schluss der Antwort erneut an; ich setze keinen unbekannten Text fort." Do not label it a new model answer.

## Persist every admitted owner turn once

Inside persistContextInvalidations, capture the existing store AND writer as local values; no Activity capture:

```kotlin
val writer = chatTranscriptIo
localConversationContext.onTurnRecorded = { id, turn ->
    if (at.heinz.deviceai.cognitive.LocalChatTranscriptStore.ID.matches(id)) {
        val at = System.currentTimeMillis()
        writer.execute { runCatching { store.appendContextTurn(id, turn, at) } }
    }
}
```

Keep onTurnsInvalidated. Remove the explicit rememberContextTurn call after record, otherwise a success is stored twice. The old unused helper can be removed.
Replace restoreModelContext's `record(id,it.request,it.response)` with `restore(id,it)`.

For Bot3's accepted owner partial output, use the same conditional as visible accepted text:

```kotlin
val acceptedPartial = !run.responseComplete && run.response.isNotBlank()
if (successful || acceptedPartial) {
    lastLocalReply = LocalReplySnapshot(localSessionId, text.take(512), run.response.take(2_000))
    localConversationContext.record(localSessionId, text, run.response,
        responseComplete = run.responseComplete)
} else localConversationContext.recordUnanswered(localSessionId, text)
```

The raw accepted run.response is stored, without the visible partial-output warning. Preserve all existing STOP/session/model/screen/consent gates. Rejected partial text remains empty and must never become continuation context. The snapshot stays a quality-gate input; render supplies the actual answer end.

## Pure memory recall

In runContinuumDialogue, replace the exact-memory reply for an oversized groundedRecall and the terminal memoryFallbackAnswer reply with addNonModelReply(request,value,NonModelReplyKind.MEMORY_RECALL). Existing memory mutation and revocation routes still clear context.

## Runtime identity

Use LocalAssistantIdentity.safeName(continuumState?.archive?.identityState?.chosenName) for name-aware app addressing.
At the ordinary final BootstrapBrain.decide(request,emptyList(),learnedCommands) call, supply its new fourth argument:

```kotlin
identityDescription = at.heinz.deviceai.cognitive.LocalAssistantIdentity.selfDescription(
    continuumState?.archive?.identityState?.chosenName,
    localRuntime.loadedModel, localRuntime.state)
```

AiContextCompiler already injects validated name, bounded profile and loaded-model identity as required context. In the promptBody fallback branch only, when botTask==null and compiledContext==null, prepend LocalAssistantIdentity.renderPrompt(null,imported) before AgentContextFallback.render(reflection). Do not expose owner identity in private bot frames or duplicate it on the normal compiled path.

## Verification boundary

Test the real combined Activity after integration. Required negative controls: compiler-redacted/changed framed task cannot suppress owner history; missing/oversized owner evidence still refuses strict follow-up; clock stays in requiredTail; cancelled/stale output never enters retained context; storage callbacks do not capture the Activity.
