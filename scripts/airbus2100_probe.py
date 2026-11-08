#!/usr/bin/env python3
from pathlib import Path

CHECKS = {
    "app/src/main/java/at/heinz/deviceai/AgentActivity.kt": [
        "private fun refreshConversationLayout",
        "private fun syncConversationAvatar",
        "private fun addAssistantMessage",
        "LocalReasoningWorkScenePolicy.decide",
        "CockpitAnimationEvent.TurnEnded",
        "override fun onPause",
        "private fun cancelSpeechInputScope",
        "cockpitBus.subscribe",
        "localConversationContext.record(",
        "resetDialogueConversation",
    ],
    "app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt": [
        "fun setHeadFraming",
        "override fun onDraw",
        "override fun dispatchDraw",
        "private var cockpitSequence",
    ],
    "app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt": [
        "fun setWorkspaceFootprint",
        "private fun applyDeckToActions",
        "enum class Slot",
    ],
    "app/src/main/java/at/heinz/deviceai/cognitive/HybridMemoryRetriever.kt": [
        "object HybridMemoryRetriever",
    ],
}

for name, needles in CHECKS.items():
    path = Path(name)
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    print(f"\n### {name} ({len(lines)} lines)")
    for needle in needles:
        matches = [i for i, line in enumerate(lines, 1) if needle in line]
        print(f"{needle!r}: {matches}")
        for line_no in matches[:4]:
            lo = max(1, line_no - 4)
            hi = min(len(lines), line_no + 8)
            print(f"--- {lo}:{hi}")
            for n in range(lo, hi + 1):
                print(f"{n:05d}: {lines[n - 1]}")
