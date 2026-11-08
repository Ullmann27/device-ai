#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path("app/src")


def matching_files(patterns: list[str]) -> list[Path]:
    matches: list[Path] = []
    for path in ROOT.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        if any(pattern in text for pattern in patterns):
            matches.append(path)
    return sorted(matches)


def print_context(path: Path, patterns: list[str], radius: int = 12) -> None:
    lines = path.read_text(encoding="utf-8").splitlines()
    hit_lines = sorted(
        {
            index
            for index, line in enumerate(lines)
            if any(pattern in line for pattern in patterns)
        }
    )
    if not hit_lines:
        return
    print(f"\n### {path} ({len(lines)} lines)")
    emitted: set[int] = set()
    for hit in hit_lines:
        start = max(0, hit - radius)
        end = min(len(lines), hit + radius + 1)
        block = [line_no for line_no in range(start, end) if line_no not in emitted]
        if not block:
            continue
        print(f"--- context {start + 1}:{end}")
        for line_no in block:
            emitted.add(line_no)
            print(f"{line_no + 1:05d}: {lines[line_no]}")


memory_patterns = [
    "data class MemoryRecord",
    "class InMemoryMemoryStore",
    "enum class MemoryType",
    "enum class PrivacyClass",
    "enum class ProvenanceType",
    "enum class ValidationState",
    "object HybridMemoryRetriever",
]
for file_path in matching_files(memory_patterns):
    print_context(file_path, memory_patterns, radius=70)

agent_path = Path("app/src/main/java/at/heinz/deviceai/AgentActivity.kt")
print_context(
    agent_path,
    [
        "memoryStore",
        "cockpitSubscriptions",
        "CockpitAnimationEvent.TurnEnded",
        "override fun onPause",
        "newSession(",
        "resetDialogueConversation",
        "JarvisWorkWindowHost",
        "LocalSpeechSession",
        "refreshConversationLayout",
        "syncConversationAvatar",
        "addAssistantMessage",
    ],
    radius=16,
)

for relative, patterns in {
    "app/src/main/java/at/heinz/deviceai/ui/JarvisWorkWindowHost.kt": ["class JarvisWorkWindowHost", "fun show", "fun replaceCurrent", "fun close"],
    "app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiStageOverlay.kt": ["class HollywoodFuiStageOverlay", "fun retarget", "fun close", "override fun onDraw"],
    "app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt": ["class SpringAvatarMorphMapperPrototype", "omega", "dtSeconds", "Viseme.PP", "Viseme.SIL"],
    "app/src/main/java/at/heinz/deviceai/cognitive/LocalChatPromptBudget.kt": ["object LocalChatPromptBudget", "task.length"],
    "app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt": ["fun setWorkspaceFootprint", "applyDeckToActions", "JarvisDeckClearance"],
    "app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt": ["fun setHeadFraming", "override fun onDraw", "override fun dispatchDraw", "advanceFrame"],
}.items():
    print_context(Path(relative), patterns, radius=24)

print("\n### Relevant tests")
for path in sorted(Path("app/src/test").rglob("*.kt")):
    name = path.name
    if any(token in name for token in ("AvatarMorph", "Argon4", "Cockpit", "Hollywood", "Speech")):
        print(path)
