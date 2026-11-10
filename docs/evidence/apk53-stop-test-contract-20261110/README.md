# STOP regression contract update

Base: `c7a8b7f415f738993df2f26b1d2b589ef90ea885`, tree
`205ccc6b93ff9b4279f1f067dee3a0d6bdaaeaac`.

The full root run reproduced one obsolete expectation in
`AvatarTtsLiveConnectionUiTest.lookupCancellationClosesWorkWindowAndLateStatusCannotReopenIt`:
line 373 required immediate View removal before any frame. The authorized STOP
path now invalidates work and speech synchronously while the visible window
enters its controlled return. The test fixture uses reduced motion; its next
shared frame can complete that return immediately.

The revised test requires synchronous lookup and presentation generation
invalidation, cleared work/busy/lease state, stopped speech, and CLOSING for any
remaining visible window. Late status may neither create a replacement nor
restart the cancelled window or work. Removal is required within the canonical
close interval plus frame margin; late speech still cannot reopen it. Original
silence and avatar identity assertions remain.

No production source changes. Root's ongoing full run is untouched. Revised-test
compilation/execution: **NOT_RUN**, pending integration into the next root test
run. `git diff --check`: PASS. No device, audio, renderer or APK claim.
