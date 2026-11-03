# Actual work-to-avatar caller integration

Status: WIP / NOT_ACCEPTED. Base visible-avatar source `ab55e440`, effect helper
source `0d34baae`. Their independent results remain separate; this branch does
not promote either dependency into the combined Chief acceptance.

Relevant experiments: EXP-08/11/12, existing ToolGate and ChiefWorkerPool.
One live HologramView and its existing frame clock remain. Only actual runner
entry may show an arrival; queued/model-proposed work is not running work.
An actual successful `web_read` may show a generic document. This is neither a
PDF-specific claim nor a connected Gmail/account capability. Terminal departure
does not assert verified success. Monotonic event times are captured before UI
posting; session, foreground, scope and freshness are rechecked at delivery.

Button/task sonification uses the existing bounded local helper. App mute,
system sound settings, foreground, pending/active TTS and recognition all apply.
No download, voice imitation, system volume change or network is introduced.

Planned RED: real AgentActivity ToolGate/worker caller tests on unchanged caller
source, plus pending-voice and lifecycle audio controls. Then the smallest
Activity adapter, independent adversarial caller review and affected regressions.
No Fold7 access: FOLD7_NOT_RUN. Full source QA/build is still pending.

Fixture amendment before the meaningful RED: the plain host Activity must use
its actual `visible()` lifecycle. Independent visibility diagnostics proved that
`setup()` alone retained windowVisibility=GONE, correctly blocking animation.
The minimal fixture also supplies the existing chat container for the real
worker report. Assertions and production source are unchanged. Preserve the
pre-amendment run separately; it cannot establish a positive-arrival defect.

Rollback is this isolated research branch only; main, original signing/package,
owner facts and Code40 rollback remain unchanged. Raw logs bind exact commits.
