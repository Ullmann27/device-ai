# Cognitive Architecture (Part 1/6)

Research prototype of a persistent identity that is independent of any single language model.
Code: `app/src/main/java/at/heinz/deviceai/cognitive/`. The existing assistant (MainActivity, CommandInterpreter,
ShizukuBridge, ShellUserService, ToolRegistry) is unchanged.

## Layers
Identity -> CognitiveState -> Orchestrator -> Memory, SelfModel, BehaviorProfile, ModelRouter, ModelReputation,
SourceTrust, SkillGraph, ToolRegistry, ExperienceStore.

Models are swappable engines behind `ModelEngine`; no provider is hard-wired.

## Data models
- `CognitiveGenome`: stable foundation (policies, bounds, learning rates). Hashed; changes rarely.
  Forbidden capabilities (self-replication, hidden persistence, shutdown evasion) are enforced `false`.
- `IdentityState`: immutable, versioned snapshot (generation, name, profiles, reputations, roots, genome hash,
  parent link, SHA-256 signature). Changes create a new generation; old ones are never overwritten.
- `IdentityHistory`: append-only chain with `audit()`, `restore()`, `export()`.
- `CognitiveCore`: primitives observe, retrieveMemory, askModel, compareCandidates, callTool, evaluateResult,
  storeExperience, updateSelfModel, proposeBehaviorUpdate, registerSkill, createCheckpoint (interfaces only in Part 1).

## Initial state
name undefined, behavior neutral, no skills/experience, reputations unknown, self model "uncertain".

## Safety boundary
The operator is the highest authority. Behavior changes are proposals only. Not implemented (simulation only if
ever studied): self-propagation, hidden persistence, shutdown evasion, account creation, credential acquisition,
process hiding, OS-control circumvention, autonomous installation on other systems.
The signature is an integrity hash chain, not cryptographic authenticity (future: Android Keystore signing).
