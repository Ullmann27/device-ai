# Actual Activity and Android main-looper gate — PASS

Executed public source: fab5f094862c5abae259196f6be1d7a8bc80a75b (#499),
exact tree3183aeeeae3532e7f3253de976eabd46efcb940a. Source remained unchanged.
Offline Gradle with continuumUiWidgetTests=true compiled the Android UI test
sources and ran MemoryRecallActivityUiTest under Robolectric SDK28, LEGACY
graphics and PAUSED looper. **2tests passed;0failures,0errors,0skips.**
Gradle exit0; BUILD SUCCESSFUL in37s. Complete command and raw JUnit XML included.

The real AgentActivity is constructed and attached. Its actual lazy compiler
stays uninitialized until requested, then the actual publisher sends compiled
memory recall through the real app subscription and Android Handler(mainLooper)
to the Activity's own cockpit bus and real BrainOrbAnimationRenderer effects.
Nothing is rendered before the real looper flush. IDs/counts/hash and actual
orb brightness are checked without exposing synthetic query/memory text.

The second case invokes the exact subscription-cleanup closures installed by
the Activity while the renderer stays registered. Queued old work is suppressed;
compilation while unsubscribed is silent; re-subscribing receives the next real
recall. The actual hub worker and three Activity executors are closed and their
termination is checked with bounded waits.

Scope limits: this deliberately does not invoke full application onCreate or
onDestroy, a complete chat/model turn, microphone/TTS, native GPU or a physical
Fold7. It validates actual Activity construction and the hooked communication
path; it is not an APK, full device or full native visual acceptance result.
