# Personal voice review dialog — isolated research component

`AndroidPersonalVoiceReviewDialog` is a concrete Android review screen over
`PersonalVoiceLearningStore`. It is compiled as a research component and has no
AgentActivity or application-manifest caller. It therefore does not enable
capture or change the current owner APK.

The screen offers a separate text-learning opt-in, a local example list,
full unchanged recognized text and owner context, editable confirmed wording,
editable intended instruction/outcome/restrictions, separate wording/meaning
confirmation, owner-only and sensitive-content checks, and explicit deletion.
No functional audio toggle is shown because audio recording is not implemented.
The UI contains no cloud client or training scheduler.

Existing records load with the store's atomic `inspectForReview` snapshot. Every
save must match that form's review revision; stale forms are not reauthorized.
Changing editable text clears its review checks. Store I/O and cryptography run
on the caller-supplied executor, not the UI thread; callbacks are bound to the
visible dialog generation and Activity lifetime. A save captures the checked
proposal and disables form changes until the operation finishes.

For a later narrow, owner-approved app integration, construct this component with
the existing Activity, dedicated protected store and existing I/O executor.
Call `show()` only from an explicit owner action. `reviewCandidate(candidate)`
reviews a proposed text example before it is staged. Call `dismiss()` when that
Activity scope ends. The proposal itself must still come through the bounded
speech/context bridge; this screen does not silently read chat history.

Compiled successfully with the actual Android 15 API classes from
android-all-15-robolectric-12650502.jar and the coordinator's current store,
bridge and Android Keystore factory. The compile receipt pins source hashes.
This is a compiler check against API classes, not a Robolectric test execution,
complete Android app build, screenshot inspection or physical-device test.
Android view/lifecycle behavior, accessibility and Fold7 layout remain NOT_RUN.
