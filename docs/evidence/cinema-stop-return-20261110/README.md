# Actual STOP button, controlled return

A real decor touch on the existing STOP button reaches cancelLocalRequest. Before the fix, the completed visible work window disappeared immediately: one targeted native-Canvas UI test failed. The fix cancels work, clears leases and stops speech synchronously, while the foreground window uses its existing shared-clock return. Pause/destroy/session cleanup retains immediate removal.

Old local/tool STOP controls are bound to the original lease or session/generation/call ID. Closing windows cancel an owned child gesture and suppress new child touches, preserving protected-area passthrough. The regression admits a new delayed request, invokes the old STOP callback, and checks the new lease, generation and active-request state remain unchanged.

59/59 targeted Android/JUnit/Robolectric tests pass at local28bd332 / remote468a479; full main source compilation passes. A later fixture-only change acknowledges the scripted worker cancellation via the existing turnEnded event. That same STOP test passes again at local0d71b82 / remote3c8ab956. Do not add the repeated case to the total.

PNG frames: native Robolectric API28 Canvas, existing 2D fallback, controlled lifecycle. The real STOP touch is tested; model work and its later cancellation acknowledgement are scripted. No native Filament, hardware AGSL, real audio, live inference, device FPS or thermal result is claimed. Fold7 remains NOT_RUN.
