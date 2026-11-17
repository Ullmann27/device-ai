# Full native layout capture

This reviewer fixture starts the real `AgentActivity`, makes its actual window
visible with native Robolectric graphics, and captures the complete decor/View
hierarchy at 412×915, 840×649 and 840×1050 pixels at mdpi. It captures home and chat
for each viewport, records visible-view bounds/text, and requires the same actual
avatar plus composer to remain visible in chat.

The test storage is synthetic and voice is muted. The previously introduced guide
flag suppresses its startup dialog only in this fixture. The default orb is
explicitly selected in synthetic appearance preferences; the optional Filament
surface is not claimed to render through Canvas. The chat messages are explicitly
synthetic presentation text and are not model outputs. No camera, account sign-in,
device installation or external API is invoked.

Final frozen fixture SHA-256:
`95842979381b6f3a4b95764cc11ec7f27128cb745cba360700e2cecf90f40c55`.
The QA coordinator runs this source through the existing `continuumUiWidgetTests`
Gradle source-set hook, with JVM property `continuum.layout.evidenceDir` pointing to
an isolated output directory. Do not run a second Gradle instance over its cache.
The coordinated execution target is Chief source
`645b2215022008e2149da81b48683f15077b1fb0`.

The first fixture snapshot failed compilation because Android's nullable
`CharSequence` needed explicit string conversion. No product test ran in that
attempt. Reflection was also corrected to call the existing assistant-message
method's actual `(String, Boolean)` signature, and orientation qualifiers were
ordered canonically before the final snapshot. Those corrections change only the
review harness. The initial log remains evidence of fixture error, not an app
regression or a failing physical device.

Actual Android17 system-bar/IME behavior, real display density, fold/unfold,
Filament composition, human motion/voice judgement and device performance remain
separate physical-device acceptance. Screenshot availability alone does not mean
visual equivalence to the owner's reference images.
