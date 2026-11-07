# Final full-layout host acceptance

**Source:** `645b2215022008e2149da81b48683f15077b1fb0`.
**Draft:** [PR466](https://github.com/Ullmann27/device-ai/pull/466).
**Verdict:** actual full-window host capture **PASS**; final visual equivalence
**NOT_ACCEPTED**; physical Fold7 and Filament-in-app appearance **NOT_RUN**.

[Open the comparison](../../../evidence/momega1/layout-final/645b2215/comparison.html).
All six raw PNGs remain unmodified. This report covers the real home/chat Activity
layouts, not every settings dialog or a fabricated screenshot composition.

## Execution and exact evidence

The independent layout fixture starts the actual `AgentActivity` using Robolectric
API28 and native Canvas graphics, calls the real home/chat navigation and message
presentation paths, and captures the complete decor tree. Actual window visibility,
the same avatar remaining visible in chat, and composer visibility are positive
controls. Synthetic storage selects the original orb, mutes voice and marks the
introduction as already seen. The chat text is explicitly synthetic presentation
text; no model is called. This is the post-introduction main layout, not an asserted
first-download/onboarding recording or a physical Android17 screenshot.

Final fixture SHA-256:
`95842979381b6f3a4b95764cc11ec7f27128cb745cba360700e2cecf90f40c55`.
QA executed the unchanged fixture as part of the final unfiltered combined suite.
The three cases pass with **0 failures, 0 errors and 0 skips** in 4.987 seconds:

| Actual executed test | Unchanged captures |
|---|---|
| `phoneHomeAndChat` | 412×915 home + chat |
| `foldLandscapeHomeAndChat` | 840×649 home + chat |
| `foldPortraitHomeAndChat` | 840×1050 home + chat |

[CAPTURES.json](../../../evidence/momega1/layout-final/645b2215/CAPTURES.json) binds
each PNG and native view-tree inventory, source, fixture, references and raw logs.
Adjacent `junit.xml` preserves executed identities. `final-unfiltered.init.gradle`,
`source-environment.txt`, `harness-inputs.sha256` and `gradle-full.txt` preserve the
actual QA context. The complete combined suite is owned by QA; its unrelated test
counts are not relabeled as independent visual checks here.

The initial source72 fixture attempt failed compilation because nullable Android
`CharSequence` was serialized without converting to String. No product test ran.
Its log and frozen harness hash remain in `fixture-error-72a64618`. The assistant
message reflection signature and qualifier ordering were also corrected before the
final fixture freeze. These are reviewer harness corrections, not app fixes.

## Comparison against the agreed design

The authoritative committed references are:

- `reference-phone.png`: SHA-256
  `d28787dc1349f30896cd7769dd49f5976fbb320e1d1b67c09905cc6025833e7e`.
- `reference-fold.png`: SHA-256
  `b998b3d42ab8abc39ed0592652450c34267d4dbdfbd5a02ffaa00f63f98a371b`.

All six complete captured viewports and both reference images were visually
inspected. The original navy/cyan/violet family, infinity branding, glass cards,
paired three-action columns, icon vocabulary and navigation remain recognizable.
Phone retains its compact card stack, landscape its left rail and reference card
rows, and portrait the existing six-card 3×2 arrangement. Chat retains the same
actual orb and a visible composer; the source no longer hides the only avatar
inside the home dashboard when entering chat.

The reference images contain a device bezel, illustrative graphs/values and a
painted luminous core. Current native snapshots contain actual empty/test-runtime
states: Offline, missing measurements and zero tasks, with two bundled bootstrap
learning events on the later snapshots. Those timings differ during actual startup
and are disclosed. They must not be replaced with fictional 98%, 1M tokens, 287
sources or fake workers to increase image similarity. Consequently this report
does not fabricate a single percentage for visual correctness or pixel identity.

The orange-marked video and six companion videos remain effects/head/speech
references under the owner's clarification, not replacement layouts. Their visual
review is documented in `JARVIS-REFERENCE-MOTION-HANDOFF.md` and `reference-review/`.
This lane relies on that recorded review and does not claim to have heard the audio
or re-viewed every private video. No private video or frame is published here.

## Concrete open findings

**L01 — portrait optical clipping.** In the actual 840×1050 home capture the
HologramView is x240/y60, width360/height343. Its outer glow/base rings are visibly
cut at the straight x240 and x600 boundaries. This makes the hero read as a
rectangular inserted surface. The six actions and cards remain clear, but slot
geometry passing is not proof of optical quality. A subsequent narrow fix should
keep the agreed positions while containing or correctly compositing the luminous
extent; it needs a fresh native screenshot on the changed source.

**L02 — phone idle diagnostic band.** The actual phone view tree places
`Bereit — · 0s · Fortschritt: nicht messbar` at x0/y787, width407/height55, directly
above navigation. This technical idle band is absent from the visual reference
and competes with the otherwise coherent dashboard. It should be reviewed as a
product presentation issue, while preserving genuine task status/approval/error
information. This lane owns no production Activity or layout edit.

**L03 — portrait status requires scrolling.** All six cards fit in the initial
portrait view, but the system-status strip below them extends beyond the 976px
scroll viewport above navigation. The existing ScrollView explicitly describes
scrollable cards/status. This fixture does not exercise the final scroll position;
therefore the screenshot alone proves neither inaccessibility nor full access.
The next device/UI check must reach the strip and verify its complete controls.

**L04 — head/first-use/device remain distinct.** Production appearance preferences
still default to the original orb; the head is optional. These captures cannot
approve a human-looking head, real lip sync, camera following or Android Filament
composition. The separately rendered real GLB sequence must not be pasted into a
Canvas screenshot and presented as the actual app. First-use voice, true Android17
insets/IME, fold/unfold, real task effects and device smoothness remain separate
acceptance. Small secondary labels also need actual-device readability review.

## Reproduction and integration boundary

`tools/momega1-layout-final/ui/IndependentFullLayoutUiTest.kt` is the only test
fixture. Register its directory in the existing optional UI-test source set and
set `continuum.layout.evidenceDir` to an isolated output directory; reuse the pinned
SDK/JDK/Gradle environment recorded by QA. No production source is changed by this
lane. Preserve the actual window-visible positive controls and native graphics.

After execution, `tools/momega1-layout-final/collect.py CAPTURE_DIR OUTPUT_DIR
--source FULL_SHA` validates the six expected dimensions, rejects uniform blank
images, copies bytes unchanged and records image/reference hashes. Manual visual
judgement is recorded separately; the collector cannot award visual acceptance.

Only reviewer tools, raw evidence and this handoff are proposed for integration.
No installation, new signing, main merge, model download, account operation or
owner-data mutation occurred. A rollback simply removes this reviewer increment.
