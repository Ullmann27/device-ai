# Device experience resumption — 2026-11-04

Status: WIP / UNVERIFIED. Owner has explicitly requested continued implementation with three visible assigned development bots. This checkpoint supplies the previously missing team briefs and records actual recovery state; it contains no feature or APK completion claim.

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
| All teams / AgentActivity | Shared activity must not be concurrently edited. Coordinator owns it; teams supply exact minimal wiring changes. |
| Bots requirements | Real filesystem tests and preservation of unsafe/corrupt data are compatible; no automatic reseeding on load failure. |
| Design requirements | Earlier images/local candidate are unavailable here. Implement documented visual requirements; visual matching and physical GPU acceptance remain explicit separate limits. |
| Speech requirements | Messages may become more informative without starting services from status questions or weakening cancellation. |

Ruling: Run three independent implementation worktrees concurrently because owner explicitly requests teams; isolate file ownership and review integration. Cost if wrong: additional integration fixes, never unchecked publication.
Ruling: Use a new continuation branch based on PR477 rather than modifying a possibly still active old branch. Cost: one additional draft PR; old work stays intact.

## Release contract
No main merge, force-push, key replacement, owner-data reset or automatic installation. Preserve at.heinz.deviceai.continuumlocal, original certificate 6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23, consumed failed S01 barrier and existing Download channel. Existing owner authorization covers a higher original-signed update only after unchanged publisher gates, exact-source verification and live downloaded-byte checks. Physical device results require actual evidence.

## Next
Three teams implement and checkpoint; coordinator prepares tooling and release path, integrates bounded patches, independently reviews changed code, runs required combined gates and reports exact receipts. Never leave usable changes only in this transient workspace.

## Recovery update
All six original reference images were found privately. The original clean integration worktree at ad68e9a8cdfba50e31e99906a4eca37f6130b155, containing eac0c3656 and the FrameLayout compile fix, was recovered. Application/tool files were copied into this continuation with exact Git-blob verification; original worktrees remain untouched. See RECOVERED-SOURCE.json. The prior unavailable statements describe the earlier lookup only. Owner expanded the assignment to at least ten specialized bots, executed in waves of three. Actual code verification is in progress.
