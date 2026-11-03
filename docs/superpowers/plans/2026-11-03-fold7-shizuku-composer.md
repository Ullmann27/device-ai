# Fold7 Shizuku and composer repair plan

> For agentic workers: use superpowers:executing-plans for inline implementation.

Goal: restore usable Shizuku connection feedback and a multiline chat composer, preserving PR363's local-intent repairs.
Architecture: repair the existing Activity, bridge and native controls in place; use the existing ToolGate, model host and memory stores.
Tech stack: Kotlin, Android Views, Shizuku 13.1.5, Node regression tests.
Spec: owner screenshots and malfunction report, 2026-11-03.

## Constraints
- Base PR363 at 7dacf0fb8f21e0dec9957fec23d8edb7feea3e77; isolated official repair branch.
- EXP-00/05/08/12 compatibility: no new tool/memory architecture or learning promotion.
- No main merge, owner signing, installation, data deletion or GitHub Actions.
- Host checks and real Fold7 acceptance must be reported separately.

## Review focus
- Missing Shizuku manager, stopped binder, denied permission: visible actionable state.
- Repeated connect taps, delayed callbacks and Activity disposal: bounded binding without stale UI.
- Long drafts, text selection, scroll and keyboard: editor remains usable.
- Fold resize and navigation: draft and selection retained.
- Greeting/capability/storage questions: existing PR363 routes retained.

## Task 1: Shizuku
- [ ] Reproduce menu dismissal and inspect bridge/service lifecycle.
- [ ] Write failing lifecycle/UI regression checks.
- [ ] Keep connection status visible; offer manager launch; prevent repeated pending binds; bound missing callback.
- [ ] Run targeted checks and commit a reviewable checkpoint.

## Task 2: Composer
- [ ] Reproduce focus/foreground and viewport constraints from native view code.
- [ ] Write failing widget/regression checks for long drafts and scrolling.
- [ ] Remove editor foreground overlay; allocate multiline space and preserve native text selection.
- [ ] Run targeted checks and checkpoint.

## Task 3: Acceptance
- [ ] Run real Android compile/JUnit/lint if toolchain can be provisioned, plus affected Node checks.
- [ ] Review final diff; record exact-source results and remaining device limitations.
- [ ] Update draft PR and handoff; do not claim an installed fix.
