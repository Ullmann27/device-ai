# Tool caller UI fixture correction

Source: `f3e70a45469a03df7032ce6d71c91a7525b91eb2`.

The completed Android run reported two `NoSuchElementException` failures in `ContinuumTaskEffectsCallerUiTest.call`. Its reflection helper selects methods by name and exact parameter count. Both failing tests supplied three arguments to `createToolGate`. The actual Activity method at source lines 7481–7483 has four parameters: `ownerTurn`, `consentRevisionAtTurnStart`, `ephemeralScreenContext`, and optional `botTask: ChiefBotTaskTicket? = null`. Kotlin default arguments do not create a matching three-parameter reflective overload.

The two existing fixture calls now supply the fourth `null`, matching the ordinary non-bot device runner path and the method's declared default. All authorization, backend arrival/departure, denied-backend and no-arrival assertions remain byte-identical. No Activity, ToolGate, runner or policy code changes. Mentor/standalone build flags do not change this method signature.

Verification: exact two-call replacement asserted against the source revision; `git diff --check` PASS. No Gradle invocation in this worker. Actual Android regression rerun belongs to the parent; no passing runtime result is claimed for this follow-up yet.
