# Natural owner actions and visible work episodes

Status: WIP / UNVERIFIED. Base: 5572edfbb02570ae479a8f0f72179f5ca33027c5 (PR578).
Branch: codex/continuum47-natural-actions-20261106; target: codex/continuum-code47-qa-candidate-20261106.

## Authorized scope
Repair natural app requests through existing installed-app lookup, local planning, explicit ToolGate approval and genuine execution receipts. Preserve complete multi-part owner requests; clarify vague or ambiguous requests. Examples are synthetic tests, not permission to send real messages. Keep preparation, recipient clarification and actual sending separate. No WhatsApp-specific route.

Connect accepted current owner work to the existing cockpit and JarvisWorkWindow, including entry from CHAT; hold the existing directed gaze until the work ends, show actual states/results, and reject stale session/generation/call callbacks after STOP. Preserve the original head, orb, materials, permissions, package and signer. No new renderer/event bus, fictitious external-app screen or invented completion percentage.

## Reproduced source defects
- OwnerAppLookupIntent treats open/start commands as lookup only. AgentActivity terminates after find_app, asks for another confirmed opening request, then captures that same wording again.
- Normal composer submission selects CHAT; presentToolScene accepts only visible START.
- Tool scenes call windowOpened (2.5-second glance), never workStarted (held reading gaze).
- Posted scene starts do not bind the existing workPresentationGeneration/session scope.
- Existing scene tests invoke show helpers directly; they do not establish input-to-real-tool wiring.

## Implementation plan
1. Add bounded natural-action regressions on genuine existing lookup/ToolGate contracts.
2. Preserve original requests and route incomplete/compound requests to existing local planning or a concrete clarification; derive no permissions from model text.
3. Add a candidate-selection/approval continuation for exact installed packages using existing executeTool/showToolConfirmation.
4. Bind the existing work window to current accepted work and real terminal results, with generation/session/call checks and STOP cleanup.
5. Compare published Code43 ff8f6070bb3a6f4dfbb6215ce3e3e269974c1d53 to Code46 577f0931e7937ffcac02dc855a540f093facdb3b for removed screens/progress widgets.

## Architecture and experiments
EXP-05: reuse LocalModelRuntime/BoundedAgentOrchestrator; EXP-06: no owner-fact promotion; EXP-07: adversarial and lifecycle regressions; EXP-11: existing ToolGate only; EXP-12: no persistent schema migration. CCC1/build worker and model weights remain untouched.

## Verification
Source audit completed on immutable Code46/47. New repair tests: NOT_RUN; central JDK/Kotlin/SDK restoration in progress. Full Gradle/lint/APK/Fold7/Filament/audio/model acceptance: NOT_RUN. No production stubs or host success presented as device evidence.

## Ownership, next step, rollback
This branch owns AgentActivity, OwnerAppLookupIntent and nearby action/work-window helpers only. Other agents own separate context/runtime cores; their Activity hooks are integrated centrally. Next: add RED cases and implement the narrow continuation. Rollback is reverting this branch's additive commits to the base; no data migration or release change.
