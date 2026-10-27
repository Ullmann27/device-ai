# Continuum Fold7 – usable neon cockpit with visible chat dock

**Scope:** Existing primary app `at.heinz.deviceai.mentorpaired`, 0.3.24/code25 source candidate only. Owner screenshot and subsequent explicit direction on 2026-10-27. Preserve the cyan/violet neon glass styling, actual animated orb, existing wave charts, donuts, all six KPI cards and their real collectors. Correct **layout/navigation**, not the identity of the product.

**Base:** exact PR187 head `54d9de3cde4a4f72f61e2f6c4e62a6dc9bd5439e`. A separate ordinary editable review branch. No change to `main`, source approvals, owner signer, production APK, learned weights, Shizuku grants, Agent Activity chat inference, or Astra PR189.

## Owner-observed defects

Owner screenshot from the real unfolded device shows six dashboard buttons pressed around the orb, narrow status tower occupying the right side, clipped EVOLVIER... label, second chart row below fold and no visible primary Chat navigation. The old Activity intentionally omitted both the landscape rail and the phone bottom bar in Fold portrait. The chat flow already existed but was discoverable only through a less obvious DENKEN tile. The old dashboard itself already sat inside a `ScrollView`, but its scrollbar was not visible persistently.

## Implemented source deltas

1. `ContinuumHomeLayout.portraitFold`: six symmetrical 216-design-dp wide navigation controls at x14–230 and x610–826, equal 82-dp heights, and a clear 360-dp central hero stage x240–600. No control overlaps the native hologram view. Existing `FOLD_PORTRAIT` renderer remains unchanged. Control visuals and 56-dp vector discs remain from the source branch; no new aesthetic replaces them.
2. Retains the six actual collector-backed cards in a true 3×2 aligned grid at x12–276, 288–552, 564–828, equal 216-dp heights, 12-dp gutters. The cards are **not** static placeholders; source `ContinuumPortraitElements.kt` retains their charts/metrics.
3. Preserves measured app CPU, current network, RAM, context, source count, and real activity wave in `PortraitSystemStatusStrip` **beneath** the charts. The former status tower class remains present for rollback/comparison but is not selected in portrait. No fabricated sample activity. Portrait content height includes the strip and allows scrolling.
4. Real native `LinearLayout` work-area dock fixed to the bottom of Fold portrait with existing dispatch destinations **Start / Chat / Lernen / Entwickeln / Mehr**, genuine click callbacks and accessibility labels. It uses familiar dark glass/cyan neon visuals and the same `ContinuumWorkAreaNavigationSpec`; no duplicated Chat activity/brain. Portrait dock hidden when the IME is visible, reserving exactly its vertical pixels in the Activity shell.
5. Dashboard ScrollView has a non-fading visible 5-dp vertical scrollbar; composer and chat messages remain in the original conversation pane, not a new overlay. Existing cover-phone nav and landscape left rail are preserved.

## Structural evidence (read-only remote-source audit)

Ten checks conducted against the fetched exact branch content: live native dock mounted to root, work-area dispatch, keyboard-conditional margin handling, strip CPU+wave data, six 3x2 cards, symmetric unclipped core, scrollbar, unchanged orb renderer/animations, replacement source test and four measured viewport geometry variants: 10/10 SOURCE_CHECK PASS. For 656×690dp and 750×790dp portrait, content exceeds viewport by approx 93dp and 105dp respectively (before Android runtime insets), so the *intended* bottom status content is vertically scroll reachable rather than clipped. Exact pixel values remain device-dependent.

New Kotlin/JUnit source assertions cover mirrored buttons, chart/status clearance and scroll-reachable content. Navigation-policy test checks Fold portrait, keyboard and landscape. Node source-contract regression has been updated to reflect owner-corrected proportions and restored Chat dock; obsolete pixel-reference assumptions removed. **These tests have not been independently run under native Node/JVM/Android here.** A remote GitHub source read or JS structural scan is *not* a Gradle build or runtime acceptance.

## Remaining acceptance and release boundaries

- From an authorized clean checkout, run:
  `node --test .github/continuum/fold7-portrait-reference.test.mjs`
  `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  and the current integration QA profile and platform security regressions, capturing actual exit codes and logs. Fix any genuine compiler/layout issue and repeat.
- Real Samsung Fold7 portrait, font scales and cover/landscape: verify neon dock immediately accessible with one-tap Chat; send real message; keyboard hides dock without hiding composer; Back or Start reopens same live cockpit; scroll reaches all six cards/status; no cropped text; no cover/landscape regressions. Export the native screenshot/metrics; visually compare to the owner's latest reference, not a simulated image. Check live orb animation and the waveform/CPU plots during relevant real activity; no mock data or visual proofs.
- This branch changes source bytes. It **invalidates the prior unapproved 1095-blob source manifest** and cannot be owner-signed using historical approvals. After complete QA and owner review, a new source inventory, builder SHA, QA evidence and explicit separate owner approval are required. Existing package ID/owner certificate must be retained and the APK must update the same app in place. Never distribute a disposable QA-key APK over the owner's installation.
- Fold portrait is the targeted modification. All Android runtime, signed release and device acceptance steps: **NOT_RUN** in this tool session. Review branch is not a release.

## Independently recovered existing integration QA (27 October 2026)

The existing non-owner Render QA static site was already correctly pointed to `integration/continuum-owner-v26-live-candidate-20261027` and had a successful deploy `dep-dbg9i5ifngtc7393hqcg`, from `2026-10-27T12:29:10Z` to `12:36:52Z`. The actual build log reported `CONTINUUM_OWNER_LIVE_QA_PASSED=54d9de3cde4a4f72f61e2f6c4e62a6dc9bd5439e` and `CONTINUUM_QA_EXIT=0`. The published `STATUS.txt` at `https://continuum-0320-all-connected-qa-20261024.onrender.com/STATUS.txt` reported `status=V25_INTEGRATED_QA_NONOWNER_BUILD_PASSED`, `version_code=25`, `version_name=0.3.24`, `package=at.heinz.deviceai.mentorpaired`, `qa_apk_sha256=e011c7ccefe892f3cd253cfba3cea15ecb599322c0ca739de4dd2d5565450263`, `owner_installable=FALSE`, and `release_authorized=FALSE`. This is **real proof for PR187 at exact head only**, not for changed PR190, PR189, an owner-signed APK or installed-device Gemma.

The owner subsequently showed a real chat screenshot containing `und wie aktiviere ich den Reparatur Agent`, which had fallen through to the generic memory response. The source `ContinuumWorkerChatRouter` formerly recognized only enumerated, explicitly prefixed Worker commands. This branch now maps only bounded explicit CCC1/reparatur-agent questions to `RepairHelp`, which explains the existing `Entwickeln → CCC1 · Reparatur-Agent (Termux)` navigation and opens the safe development-center screen, **never launches a repair, shell, memory write or owner approval**. A new Kotlin JUnit regression includes the screenshot question, harmless variations and ambiguous/malicious rejections. Isolated installed-Kotlin regex execution passed 8/8 positive/negative cases; full application JUnit and Gradle for PR190 remain NOT_RUN. Remote source checks confirmed every sealed Worker command has a corresponding Activity branch, 9/9 structural checks passed.

**Next test gate:** Run the exact new branch HEAD on the non-owner QA runner (requires an explicitly branch-targeted independent checkout or changing the QA-only Render service branch, not the owner-signer service). The historical successful QA on `54d9de3c` must never be relabelled as passing the changed `chatgpt/continuum-fold7-usable-navigation-20261027` source. No owner-signing invocation until its new source inventory is independently reviewed and approved.
