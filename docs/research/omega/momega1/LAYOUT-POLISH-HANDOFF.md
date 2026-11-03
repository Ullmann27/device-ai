# Two bounded native layout corrections

Source `7c74e23a802895fd902d2993647a3154c2ffc2ab` changes only
`ui/ContinuumHomeView.kt` and `ui/ContinuumHologramRenderer.kt`. Chief separately
changes Activity idle status in `49a141fb`. Actual combined execution source is
`0a933373bf2129c9a1692e07d2659488bea12cc8`.

[Before/after comparison](../../../evidence/momega1/layout-polish/comparison.html)
uses unchanged full-window native PNGs. [RESULT.json](../../../evidence/momega1/layout-polish/RESULT.json)
binds sources, frozen tests, executed identities and raw file hashes.

The portrait hero was exactly x240/y60, 360×343 pixels, while its existing floor
rings extend beyond that narrow rectangle. The original parent clipped them.
Only portrait HomeView now allows decorative child overflow behind the unchanged
foreground controls. The original renderer's portrait backdrop fills its radial
support to alpha zero, eliminating the hard rectangular edge. No hero bounds,
radius, geometry constants, control positions, touch targets, 3D viewport or task
effect policy is changed. Phone/landscape clipping and renderer geometry remain
unchanged. Chief hides only READY status; active/blocked/outcome labels stay visible.

Actual frozen fixture SHA-256 is
`bb39895c725240b1160626374c943b4fed5f2944ccaefb257d4ad613e9a676a9`.
QA ran the real unchanged source645 baseline before either fix:

- Three native cases: **1 PASS, 2 assertion FAIL, 0 SKIP**. Active/blocked status is
  the passing positive control. Idle label is VISIBLE instead of GONE; actual
  portrait paint outside the hero rectangle is zero pixels.
- The same three cases on combined0a: **3 PASS, 0 FAIL/ERROR/SKIP**. Hiding the actual
  HologramView changes 27,339 pixels outside its unchanged horizontal bounds within
  the hero's vertical band. This measures actual rings/glow paint, not segmented
  ring pixels. All control bounds are unchanged.
- The unchanged complete-Activity fixture also passes **3/3** and emits six new
  phone/Fold home/chat screenshots. All six were visually inspected. Chief
  independently inspected the phone and portrait comparison and confirmed these
  two targeted fixes. Raw XML, Gradle logs, source-before/after and commands remain
  under `docs/evidence/momega1/layout-polish/`.

The phone now exposes the genuine existing activity report
`CONTINUUM · offline-semantic-bootstrap: angenommen (550 Schritte)`. Its technical
wording still differs from the reference. It is preserved as real status, not
hidden or relabeled to claim a perfect design. Startup telemetry also differs
between captures because the real bundled bootstrap/collector is asynchronous.
No fabricated reference figures were inserted.

These two narrow defects are resolved in host native rendering. **Full visual
equivalence remains NOT_ACCEPTED; real Fold7/Filament appearance, voice, camera,
Android17 insets/IME, motion performance and portrait status scrolling remain
separate device/UI acceptance.** The default orb is explicitly selected in
isolated synthetic test preferences. No installation, sign-in, model inference,
signing or owner-data mutation occurred. No further production changes are made
in this freeze. QA owns final combined full regression/lint/APK evidence; the six
focused passes do not replace that final gate.
