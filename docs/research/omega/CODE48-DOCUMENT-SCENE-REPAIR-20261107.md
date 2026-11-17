# Code48 document scene admission repair

Status: SOURCE_INTEGRATED / MOCK_TESTED. EXP-02/07 regression repair.
Production commit: `27d86f3c3dedc107a23eeb4d1ad45cf427a8586a`.
Base: `be256a1ae3f31fa04f0acdd7c7537a43beb219b3`.

## Cause and bounded change

- `AgentActivity.presentDocumentScene` required START plus a visible home. A typed PDF request stayed in CHAT, so no work window was requested. The accepted document now returns from CHAT through the existing `showHomeDashboard` before requesting the window.
- `JarvisWorkWindowHost` planned the first opening from the last drawn shell cache. A compact-layout change clears that cache, dropping the document scene. Both initial planning and refitting now use `currentShell`, which validates and recomputes current geometry.
- No deferred callback or retry loop was added. Session/generation rejection, STOP, hidden-home admission, shell motion clock, reduced motion, collision protection and minimum geometry remain in their existing paths. Tool access, consent, local inference, memory, SWB1 and P12 are unchanged.

## Verification

Both existing PR589 REDs pass with the two modified production classes freshly compiled against the cached SDK/baseline. Native Android host Canvas, actual Activity/input routing and real work-window classes are exercised; parsed PDF data is a synthetic fixture and model responses are controlled.

| Existing test | Result |
|---|---|
| `typedPdfRequestReturnsFromChatIntoAnimatedDocumentWork` | PASS; OPENING then OPEN, same page bitmap and truthful chat facts |
| `documentWorkWaitsForFreshShellGeometry` | PASS; window survives invalidated geometry cache |
| `stopRejectsAQueuedPdfFailureAndItsPicker` | PASS |
| `aReplyQueuedBeforeStopIsDiscardedAfterIt` | PASS |
| `stopDuringTheDraftEndsReadingWindowAndRunAsAborted` | PASS |

Production compilation, test compilation and JUnit each exit 0; total 5 tests, 0 failures. Raw commands, source hashes and logs: `docs/evidence/code48-document-scene-repair-20261107/`. Earlier RED evidence remains in `docs/evidence/code48-workscene-thermal-20261107/`.

This focused run uses a production overlay, not a newly built APK. No physical Fold7, Android17, temperature, FPS, voice, reference-video or integrated cinematic acceptance is claimed.

Next: integrate this commit with the independently owned cue/video/lifecycle repairs, then run the combined source/build gates. Rollback: revert only `27d86f3c3dedc107a23eeb4d1ad45cf427a8586a`; owner data is unaffected.
