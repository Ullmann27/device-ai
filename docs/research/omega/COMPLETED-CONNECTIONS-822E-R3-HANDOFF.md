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

## Continuation and rollback

## First actual common gate and bounded follow-up

Frozen local `f3e70a45469a03df7032ce6d71c91a7525b91eb2` equals published `d2aedf2c272bfa20d4e1930a0f65277c3f02be0f`, complete tree `fdae24c1778b456509ab588120b8abdcf9f93aa8`. Both common Node profiles completed with unchanged source: critical **135 PASS / 0 FAIL / 0 SKIP**, full-node **2,362 PASS / 0 FAIL / 31 SKIP**. These overlap and are not added.

The UI-enabled Android run without class filters completed: **3,008 total / 2,974 PASS / 15 FAIL / 19 SKIP**, Gradle exit1; lint was not reached. This first invocation used the default app mode; the following invocation explicitly selects the existing canonical standalone-QA flags with code41 solely as a compile/test parameter. It is not a new release version. All new connection suites passed within that first run; the complete Android gate remained FAIL.

All original 562 evidence files, 530 JUnit reports, source hashes, commands and negative results are retained in `coordinator/first-run-evidence.tar.xz`, with individual hashes and a readable summary next to it. Nothing is reclassified as PASS.

The three agents then delivered bounded responses to the actual stacks:

- Twelve failures in `windowFocusChanged` at Robolectric's missing ViewRoot: advance a bounded real traversal frame before focus. The recreation fixture now executes the actual saved-instance lifecycle around a Bundle, preserving every original draft/reflow assertion.
- Two reflective tool-caller failures: pass the existing fourth `botTask` argument explicitly. Kotlin's default value does not create a three-argument reflection overload. Authorization and backend assertions stay unchanged.
- One stale avatar-slot failure: cancel the named callback on detach, recheck window attachment and the registered adapter's live-surface parent before docking. The direct ownership rejection stays intact. Three new UI cases cover stale callbacks, foreign ownership and positive redocking. The originally failing AdaptiveCockpit test remains unchanged.

The original node PASS applies to the first frozen source. Fresh Android and appropriate Node gates are required on the repaired combined source; no result is inherited across these changes.

Read the latest version of this handoff and exact remote branch ref. Review each agent's bounded diff, combine serially, freeze source, then execute actual checks. Revert individual R3 commits to withdraw a slice; the two frozen parent histories and all previous evidence remain available. No force update, main merge, release, signing or installation is included.
