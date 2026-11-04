# Device experience resumption — 2026-11-04

Status: WIP / COMBINED_ACCEPTANCE_PENDING. The owner explicitly requests continued implementation, at least ten named development bots and three additional animation specialists. Jobs run in waves within three worker slots. Actual assignments, ownership and acceptance are tracked in BOT-ROSTER.md. There is no feature, visual-match or APK completion claim.

## Verified recovery
- PR477 remains open at 337ab0195434c8657bf131bf431f4c391aacc28c; only PLAN.md and REFERENCE-COCKPIT-HANDOFF-20261104.md are changed there.
- PR476 remains a documentation-only checkpoint at aba86cf023327ce4ce0954045251bd25b16edbab.
- GitHub could not resolve eac0c365. No claim that old local changes were recovered or lost.
- Fresh private authenticated sparse checkout succeeded; source worktrees are isolated. Original publisher srv-dbf1tr2vcj2c73ehpfog is present with autoDeploy off.
- No Android SDK or Gradle was initially available in the resumed environment. Tests, build, original signing, publication and physical Fold7 acceptance are NOT_RUN for this candidate.

## Plan and interface rulings
The existing PLAN.md remains the scope. Team briefs here provide the missing concrete ownership and acceptance information. EXP-05/11/12 apply. Current source and 2026-11-04 plan supersede historical 2026-10-31 release-version descriptions without changing safety boundaries.

| Interface | Finding and decision |
| --- | --- |
| Bots / capabilities | Both consume canonical bot state. Bot team owns store/roster; capability team reads existing public summaries without private conversations. |
| Design / capabilities | Both use dashboard state. Reuse the current collector; no duplicate worker or state store. |
| All teams / AgentActivity | Shared activity must not be concurrently edited. Coordinator delegates exclusive integration ownership to Bot06; all other teams supply bounded patches. |
| Bots requirements | Real filesystem tests and preservation of unsafe/corrupt data are compatible; no automatic reseeding on load failure. |
| Design requirements | Original candidate and six references were recovered. Nine direct attachments now supply the authoritative design and high-resolution face references. Compare actual renders with these images; preserve physical GPU acceptance as a separate check. |
| Speech requirements | Messages may become more informative without starting services from status questions or weakening cancellation. |

Ruling: Run three independent implementation worktrees concurrently because owner explicitly requests teams; isolate file ownership and review integration. Cost if wrong: additional integration fixes, never unchecked publication.
Ruling: Use a new continuation branch based on PR477 rather than modifying a possibly still active old branch. Cost: one additional draft PR; old work stays intact.

## Release contract
No main merge, force-push, key replacement, owner-data reset or automatic installation. Preserve at.heinz.deviceai.continuumlocal, original certificate 6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23, consumed failed S01 barrier and existing Download channel. Existing owner authorization covers a higher original-signed update only after unchanged publisher gates, exact-source verification and live downloaded-byte checks. Physical device results require actual evidence.

## Current integration and next steps
The recovered app/tool source is remotely preserved in commit 000364f9dc1f875cdc83644a4b55945b5c21f6d0. Bot01, Bot03 and Bot04 source, tests and actual evidence are remotely preserved in 2458e43bab37bfd74455ca7a59d45560762fda6d on draft PR478. The remote tree bb92e10e9a584cb680b728dda7a7eecf91c22b52 exactly matches local integration 7351768e4953d00b81abe10b288fe49c4b01dd6d. GitHub API commit metadata differs from local commit metadata; tree equality was verified before the lease-guarded non-force branch update.

Bot06 integrates store/capability/action/composer wiring. Bot05 is refining real 3D anatomy and material against the direct head references. Bot11 implements event-driven neon motion, followed by Bot12 app/document surfaces and Bot13 rendered animation previews. Permission, independent regression, release and final acceptance bots follow with concrete source snapshots. Never leave usable changes only in this transient workspace.

## Recovery update
All six original reference images were found privately. The original clean integration worktree at ad68e9a8cdfba50e31e99906a4eca37f6130b155, containing eac0c3656 and the FrameLayout compile fix, was recovered. Application/tool files were copied into this continuation with exact Git-blob verification; original worktrees remain untouched. See RECOVERED-SOURCE.json. The prior unavailable statements describe the earlier lookup only. Owner expanded the assignment to at least ten specialized bots, executed in waves of three. Actual code verification is in progress.

The previous build cache was also recovered: JDK17, API36, pinned Gradle and real Robolectric are available. The standalone QA guard permits only its named QA tasks; do not weaken it to run tests. Focused filtering uses Gradle's Test.filter via an external init script with the sole testDebugUnitTest task. Failed task-filter invocations are tooling evidence, not app test failures. Final exact combined Gradle/lint, unchanged Render gates and physical device acceptance remain pending.

## Visual work contract
The supplied images define the dark navy background, cyan/violet glass surfaces, prominent humanoid head with fine curved luminous filaments, identifiable eyes/lips/nose/ears, orbit rings and original brain orb. Portrait, Fold and landscape layouts must preserve legible navigation and usable input. Reference images contain illustrative runtime labels and numbers; these must never be copied as live app facts.

Motion consumes actual session-scoped app/tool events. A command may show starting, reading, awaiting a choice, blocked, failed or complete states only from corresponding evidence. A generic finally/Finished event is not success. Presentation does not grant permissions, connect accounts, send messages or bypass the existing owner ToolGate/codeword. Connected-app/document contents are never placed in logs or evidence images. Original owner reference images are not uploaded to GitHub.
