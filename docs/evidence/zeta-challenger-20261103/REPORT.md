# ZETA B independent responsive UI challenge

**Result: frozen geometry/action-text/state checks pass on final candidate `de86cff67b1b028645d7bf77f5fee171d66530a0`. This is bounded UI evidence, not full integration or physical-device acceptance.**

Acceptance was committed/pushed at `3dcc58662dd7ad5772b00c7eff33a6e4075b8974` before any candidate source or author outcomes were inspected. Frozen manifest SHA256: `a9b5e3b68fb78fa52833d77b4468b10887f365376a75d00032b0aaf76c4e1cd5`. Baseline source: `04b4292d796bc151c40ab18405226cd9faef01cf`. No author test outcomes were used to set or score the frozen cases. The author later disclosed a source delta and its motivation; that did not change the frozen tests. All changed source copies, source hashes, raw results and initial harness failure are retained.

| Exact source | Actual Kotlin/Java2D frozen checks | Native Robolectric Android 15 |
|---|---:|---|
| Baseline `04b4292d` | 7,080 checks; 717 negatives | 1 test / 3 fixtures passed; screenshots retained |
| First candidate `d8340ff1` | 8,040 checks; 0 negatives | 1 test / 3 fixtures passed; screenshots retained |
| Final candidate `de86cff6` | 8,040 checks; 0 negatives | Not rerun; final production delta is the tablet sidebar font threshold |

Counts differ because layout slots and rendered line counts differ. Every original holdout ran on all three sources: 287×611, 320×431, 393×873, 521×333, 599×981, 601×481, 713×897, 911×521, 1279×799 and 713×1601, each at font scales 1, 1.3, 1.8 and 2. Invalid width/height inputs and 393→713→521→393 layout return were checked. No copied layout formula was used: source Kotlin was compiled and called directly, then source elements were drawn through the repository's actual Java2D painter and bundled font metrics.

The final candidate has finite bounded content, 48dp header targets, six separately bounded actions below the hero, nonoverlapping interactive slot rectangles, action text within bounds and without text-line overlap, and preserved live model/core descriptions. At ordinary font scale the tested dashboard transitions from one card column at 287dp, to two at 713dp, to three at 1279dp. Content scrolls rather than shrinking to fit short windows. The actual Android view retains focusability, accessibility descriptions, `performClick` slot dispatch, state refresh and offline/unknown model reset in the tested native fixtures.

Baseline negative breakdown: 232 minimum-touch checks, 240 actions-below-hero checks, 154 action-text linebox overlaps, 90 readability floors and one nonfinite-width case. The old actions-around-hero arrangement intentionally fails the new below-hero rule. Pure fontScale1.8/2 tests stress the element API; baseline Android clamps its actual fontScale to1.3, so these are not claims of baseline Android displaying at2. The candidate raises that clamp to2.

## Supplementary findings and limits

The six action slot identities survive, but literal labels change: DENKEN→Chat, ANALYSIEREN→Forschung, ENTWICKELN→Entwicklung, VERBUNDEN→Verbindung, EVOLVIEREN→Lernstatus and LERNEN→Lernen. If the assignment requires the original wording, this needs author/Chief resolution. The challenger does not silently equate slot preservation with literal-label preservation.

After the frozen run, visual inspection prompted a separate all-card text-linebox audit. It found 104 baseline, 22 first-candidate and18 final-candidate overlaps in font measurement boxes across nine fixtures. These are **advisory measurement-box overlaps, not established visible glyph collisions**. A follow-up rendered-ink audit of the final model card at713×897, font scales1/1.3/2, and model names `Continuum-Kern`, `qwen-3`, `gypqgypq` found **zero actual ink collision pairs** (alpha threshold32). All measurements and nine card renders are retained. These supplementary tests were added after source inspection and are not retroactively included in the frozen score.

The final source's only production change after d834 is reserving half-height tablet telemetry for fontScale≤1.1 instead of≤1.3. The final pure run covers1279dp/fontScale1.3 with that change. Native screenshots remain explicitly attributed to d834; full final-source Activity integration belongs to Quality.

Native fixtures render the real HomeView and original prepared hologram dependency offscreen in Robolectric, not an installed app. Screenshots are full scroll-content images, not device viewports; they do not establish scrolling gestures, actual TalkBack speech, physical touch usability, avatar animation, FPS, battery or performance. The candidate retains the original orb path and sampled navy/cyan/violet glass appearance; exact original visual identity is not established. Widths below the source's explicit240dp precondition were not accepted as supported. Ellipsis at narrow/large-font settings remains visible, including the brand and long card headings. The tests do not establish all metric-card text readability, all Activity tap subzones, permissions flow or final APK behavior.

## Reproduction and provenance

From this worktree with the existing prepared build environment:

```bash
bash docs/evidence/zeta-challenger-20261103/run-holdouts.sh
ZETA_SPECS=candidate-de86:de86cff67b1b028645d7bf77f5fee171d66530a0 bash docs/evidence/zeta-challenger-20261103/run-holdouts.sh
bash docs/evidence/zeta-challenger-20261103/run-native.sh
bash docs/evidence/zeta-challenger-20261103/run-supplementary.sh
```

Prepared support classes are hashed in `prepared-classes.sha256`; they are host build inputs, not newly built release artifacts. The four home sources and new candidate CockpitElements are independently extracted from exact commits. `toolchain.txt` records JDK17.0.20.1, Kotlin2.4.0 and painter hash; Robolectric is4.14.1 with offline Android15 dependencies. No Gradle process was launched. The first native compile failed due to missing Kotlin friend-module visibility; the preserved failure and corrected command show this harness issue. Both subsequent native runs passed.

Own branch scope is evidence and handoff only. No feature edits, AgentActivity/ContinuumHologramView/Gradle changes, main merge, signing, release, install or owner-data access. Quota NOT_VISIBLE.
