# Final source-wiring test repair

Base `878393350db82e2b34738b09c20dbf655f1bad9d`; final test-only source
`3b2e569f3e9b9a0fe80f0dd508d908ec33ae0dc5`. Branch
`codex/momega1-wiring-tests-20261103`. No app, CLI, backend or tool production
source changed; the four allocated Node test files are the only code diff.

Actual focused RED: **65 tests, 59 pass, 6 fail**. Actual final GREEN:
**65/65 pass**, zero skips/failures, Node24.19.0. Raw TAP and source/tree hashes
are preserved in [final-wiring-repair](../../../evidence/momega1/final-wiring-repair/manifest.json).
The original full645 RED belongs to QA and is not replaced by this focused run.

The changes retain the intended contracts while locating current approved code:

- Balanced parameter/body parsing masks strings, nested comments and triple
  strings, preserving UTF-16 offsets. An intermediate spread/codepoint bug was
  caught by the real source's two astral characters; its failed attempt is kept.
- Normal chat still composes the same-session owner frame, authorizes tools from
  untouched `text`, checks the complete-task budget before activation, and uses
  the dialogue quality gate. Optional typed planning uses ownerGoal separately,
  empty memory hints and zero tool/retry limits; candidate evaluation is not
  outcome verification or owner authorization.
- Readiness camera/microphone permission codes have their own callback branch,
  exactly refresh+return. They cannot start sensors. Readiness speech uses the
  in-process recognizer and its explicit request code; legacy scoped local
  speech permission resumption retains its existing checks.
- The renamed canAnimateFrame predicate retains actual foreground, motion,
  attachment, visibility and window visibility guards, plus existing power
  policy; it does not forbid correct READY visibility.
- The normal AgentModel fallback is isolated as a balanced lambda and still
  checks model identity before and after generation. Both the legacy patch
  preflight and current pinned coding route remain checked; pinned consent,
  provenance and file admission must precede native model launch.

No checks were deleted, converted to constants or widened to an arbitrary
wildcard to obtain a pass. Independent review tests owner input, budget bypass,
readiness auto-start, foreground removal, planning memory/tool expansion and
model-binding removal using read-only source mutations. The final mutation
receipt is owned by the separate Android review lane. Source-wiring checks
remain static contracts; existing128/13/28 host evidence and physical-device
limits are not replaced or inflated by this repair.
