# Completed connections — R3 / 2026-11-04

Status: SOURCE_COMBINED / combined runtime gates pending. Owner requested three implementation agents and connection of compatible completed work.

## Frozen intake

- C1 #519: remote `6c34db7be4068b52de0cef20a0c9142a08db27c0`, local `38917560671b13795bf85d5a68d329658ba4eb7d`, identical tree `687fe9a8267cb0cd8546acc7818f66fdbf3a1891`.
- Prior connection #523: remote `7c1642af96ccf626c91a40a602c7127e02fb9eea`, local `cae28f2ff1eeac42f14fd165012a2825378a1654`, identical tree `e71da8ed2dde2473abe56024d5366f8a9a8a42f5`.
- Combined baseline: local merge `583ef0b9d8ab99c940a5cfc8b5b6dde5cb01628b`, tree `6aaf7108cdbf13c5686d6ddfa2a79a2b277ba5d3`.

No earlier PASS is transferred to this combined source.

C1 advanced during intake. Its published `20d9dd8779c63452815a701b8f5956e426abf4aa` and local `4da039d55d44f7087a9e4cd7bd72dc5a0089029e` share tree `974e949a022a7774c6c5c702631217fffb8f8018`. The coordinator reviewed and merged this advance before the R3 test freeze. It adds four bounded UI fixture corrections and existing receipts; no additional production behavior. This materially addresses the previously observed virtual-frame hang, making a fresh full Unit/UI attempt appropriate.

## Three bounded implementation areas

1. Memory: contradiction privacy and event accounting from #522; preserve #523 byte accounting and existing Activity/observer hooks.
2. Avatar/UI: completed packaged reference head from #521 and outward Float correction from #524. #526 is currently WIP and excluded from intake.
3. Bots/workers: private exercises from #516 and completed branch-discovery/Termux connections from #515, using existing pipelines and permissions.

Each agent works in an isolated existing sparse checkout. The coordinator alone combines changes and runs shared Android gates under both existing machine locks. Shared C1 and foreign worktrees remain untouched. Candidate changes are retained only after source review and relevant checks; unavailable device acceptance remains explicit.

## Integration and verification

Branch: `integration/continuum-connected-r3-20261104-822e`, based on current C1 with all previous #523 connections retained. Actual results, changed-path provenance and source identities will be recorded here and under `docs/evidence/completed-connections-822e-r3/`.

At this start checkpoint, Android, Node, native rendering and device tests on the combined source are NOT_RUN. Relevant prior receipts stay historical. Main, original package/signing identity, owner data, offline gates, model weights and release authority remain preserved.

## Combined implementation checkpoint

- Memory agent `3df90e34a2a6dcac22a79106acfde997e6726de6` imported as `86ae104`: two production files and three unchanged upstream regression files (14 cases). The merged compiler preserves #523 byte accounting and the current recall observer.
- Bot agent `c79486eb49d2ca5273956a161c6e3732ddfd5672` imported as `7afcfd8`: five production files and four upstream test files. Coordinator separately reapplied three #516 Activity hunks to the current Activity and added two real history-display checks with V2/V3 codec roundtrips.
- Worker agent `d0398f4e023bc3e361de661df69d48f6d448709c` imported as `76195ed`: nine exact #515 code/test files. Four ECHO files and QA script were already identical; the current QA assertions and #523 scheduler selection optimization remain.
- Avatar agent `b94a4ae06deb8c90b361a0273d7d4bddd48304f6` and `ba29094c0830f02c7a5011055d3086621c04881c` imported as `8934d37` and `6753033`: strict 48dp Float correction, original precision tests, packaged reference GLB, matching NOTICE/hash pin, provenance test and reproducible source tools.
- Packaged GLB SHA256: `70b93bfdfb10c65662f6d37857e4a6a403dfb6ad950a9220acf91cb4145ab145` (12,552,372 bytes). The newer corrected-head candidate present in the #521 source tree is not yet packaged and is explicitly excluded, as is #526 WIP. This checkpoint does not claim that newer candidate is visible in the application.

The memory agent independently reviewed the bounded practice/Activity integration at `7afcfd848ad9440b7eba205043261776af19c782`: no confirmed blocker found in prompt isolation, accepted completion, private persistence, V2/V3 compatibility or unchanged runtime gates. This is a source review, not model inference or Android execution. The two history-display fixtures do not represent full Activity startup or a live model stream.

New practice histories require the V3-capable reader once used; ordinary histories remain V2. No app-data migration or deletion is performed by source integration. The inherited avatar rig-window disagreement and prior unrelated secret-redaction edge remain outside these bounded fixes.

## First actual common gate and bounded follow-up

Frozen local `f3e70a45469a03df7032ce6d71c91a7525b91eb2` equals published `d2aedf2c272bfa20d4e1930a0f65277c3f02be0f`, complete tree `fdae24c1778b456509ab588120b8abdcf9f93aa8`. Both common Node profiles completed with unchanged source: critical **135 PASS / 0 FAIL / 0 SKIP**, full-node **2,362 PASS / 0 FAIL / 31 SKIP**. These overlap and are not added.

The UI-enabled Android run without class filters completed: **3,008 total / 2,974 PASS / 15 FAIL / 19 SKIP**, Gradle exit1; lint was not reached. This first invocation used the default app mode; the following invocation explicitly selects the existing canonical standalone-QA flags with code41 solely as a compile/test parameter. It is not a new release version. All new connection suites passed within that first run; the complete Android gate remained FAIL.

All original 562 evidence files, 530 JUnit reports, source hashes, commands and negative results are retained in `coordinator/first-run-evidence.tar.xz`, with individual hashes and a readable summary next to it. Nothing is reclassified as PASS.

The three agents then delivered bounded responses to the actual stacks:

- Twelve failures in `windowFocusChanged` at Robolectric's missing ViewRoot: advance a bounded real traversal frame before focus. The recreation fixture now executes the actual saved-instance lifecycle around a Bundle, preserving every original draft/reflow assertion.
- Two reflective tool-caller failures: pass the existing fourth `botTask` argument explicitly. Kotlin's default value does not create a three-argument reflection overload. Authorization and backend assertions stay unchanged.
- One stale avatar-slot failure: cancel the named callback on detach, recheck window attachment and the registered adapter's live-surface parent before docking. The direct ownership rejection stays intact. Three new UI cases cover stale callbacks, foreign ownership and positive redocking. The originally failing AdaptiveCockpit test remains unchanged.

The original node PASS applies to the first frozen source. Fresh Android and appropriate Node gates are required on the repaired combined source; no result is inherited across these changes.

## Second actual canonical gate

Frozen local `a6fac1f8be82bdef9c34aff726d20ebc949e575b` equals published `ca80459a3f6695c8fecff4a217fbe93318452121`, complete tree `28ccf4d90f5e58926b24c35d360a36dad26d0e04`. The configured Android Unit/UI run used the canonical standalone-QA flags, code41 only as a compilation/test parameter, and no class filters: **3,011 total / 2,984 PASS / 8 FAIL / 19 SKIP**, exit1. All 531 fresh XML reports are retained. NewApi lint was not reached after the test failure. All eleven mandatory suites and five minimum-case requirements passed without skips.

Both fresh Node profiles passed on this same unchanged source: critical **135 PASS / 0 FAIL / 0 SKIP**, full-node **2,362 PASS / 0 FAIL / 31 SKIP**. Original commands, actual compiler identity, source manifests and 561 raw evidence files are preserved in `coordinator/second-run-evidence.tar.xz`; adjacent manifest and summary bind every file and result to this source. Counts overlap and are never added.

The earlier avatar ownership failure, all three added avatar lifecycle cases, Shell/window-focus cases and both tool-caller cases now pass. The eight remaining failures are all in `ContinuumReferenceCockpitUiTest`: it still selects hidden pre-Shell controls, an old eight-entry rail and the hidden conversation rail. Two independent source inspections confirmed that the current Activity activates the Shell and intentionally hides the legacy ElementViews. Updating these fixtures must preserve actual decor-dispatched taps, minimum touch sizes, visibility, draft preservation and current destination identities. Legacy-only compact presentation must be identified explicitly; it is not an attached current-Shell feature.

## C1 reconciliation before final freeze

C1 subsequently published `a6135f24629ba9151bbc06888bc977325d95e6f1`, matching local `59d8d7e25a5936403e8579373883fa04bec5c37f` and tree `90d68760d9a0533c169697902b61eda126e9b20b`. Coordinator merged that completed checkpoint locally as `4ca999bf9893479575b1723d8b29a6570df0357a`. Its memory and landscape production imports are already present byte-for-byte, apart from the deliberately retained existing #523 byte-counter optimization. The merge preserves our production files unchanged, both histories, C1 evidence and all three added real-decor attachment assertions. Original patch evidence keeps its exact whitespace. No foreign WIP or unfrozen UI proposal was imported.

## Current cockpit focus passed; active C1 acceptance retained

Actual canonical focus on local `4223ffe3a4fb6c2906f244ec2475d22d150eefd4`, now published as `a6b314d3e8fac796ce7059a336bf5e79eb622ede`, identical tree `d820c7e0cd6cbd4bd1ce37efeab835550e05f5c0`: **9 PASS / 0 FAIL / 0 SKIP**, exit0, unchanged source. All four device profiles and three font scales execute. The native ListView selection needs its actual64ms pressed-state dispatch; a strict real test also found the39dp status target, now expanded only into unused bottom gutter. A diagnostic run measured4px actual global separation where mixed coordinate spaces falsely overlapped during a running Home animation. Both comparison rectangles now use actual clipped global coordinates; animation, strict48dp and real gestures remain.

C1 advanced again to remote `0d8a3d46c409e5918c49b6f3407bb63f3519b846`, local `9a442485f2569b39be2bcd8d6687da6f6c15c45d`, identical tree `8fe54137e976c718c91a4b3bbcfc02d50ca52bdd`. Its two worker preflight files already match our prior import exactly. Its frozen active-Shell acceptance adds real Compact and wide More-access requirements, which are not deleted to obtain a green run. The coordinator merges this checkpoint and the independently prepared ten-case semantic fixture (`b3b791ae6f1f8482bbcca4ce2b46bfb9ad2f79eb`). This retains all79 labeled C1 assertions, both safe Bundle recreations, our explicit legacy presentation case, actual native chooser case and three unchanged avatar lifecycle cases.

At this merge checkpoint the new Compact/More product connections are still being completed on our isolated review branch. The prior nine-case PASS is historical and does not accept the expanded ten-case candidate. No unfrozen foreign production proposal is copied.

## Active Compact and More connections — source checkpoint

The three bounded agents completed the active-Shell connections. Compact agent `7e33883543389e487e30aa3c7d83c2afa028df92` is imported as `737b278`; the existing native appearance chooser now offers Compact, reuses the same avatar and existing preference, updates Home geometry through an attached-only preference listener, and passes one layout flag to measurement, hit targets and painting. Normal layout remains on the original calculation path. The independent memory-agent source review found no confirmed blocker in initialization, preference lifecycle, renderer ownership or geometry/cache propagation; this is not runtime acceptance.

More agent `fb5950b4c6838f29805cf7054af44e05feab4aec` is imported as `f88f6c7`: one existing System Center action opens `showMoreWorkAreas()`, including the existing developer/coding-draft route. No new task is started by this connection.

The semantic C1 fixture is expanded by agent `d3cd7390de88b27c5a44355a977574ced1021d21`, imported as `5568f59`: twelve Reference cases now include actual wide Compact selection/restoration at840x649 and840x1050 with font1/1.5/2. All previous ten cases and the three avatar-lifecycle cases remain unchanged. New complete-source focus and full gates are PENDING at this checkpoint. Prior negative runs, startup rejections, actual nine-case PASS and separately source-bound backend64 PASS are retained in `coordinator/ui-followups-evidence.tar.xz` with full per-file hashes and exact source identities. No previous result is transferred to this candidate.

## Continuation and rollback

Read the latest version of this handoff and exact remote branch ref. Review each agent's bounded diff, combine serially, freeze source, then execute actual checks. Revert individual R3 commits to withdraw a slice; the two frozen parent histories and all previous evidence remain available. No force update, main merge, release, signing or installation is included.
