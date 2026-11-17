# Thermal visual composition checkpoint

Status: HOST_TESTED, 3/3 PASS. Production policy unchanged.

Base: local `fac1e1e`, remote `31f61c6f1ff24f116135824403f0a6a57721c787`, identical tree `6aa5201f1bd29b2f9c5fb193aafe1050483671c5`.
Canonical integration owner: root; target Cinema PR715.

The existing policy keeps low-tier essential face frames separate from decorative motion. No confirmed runtime budget defect was found. The missing regression combines the actual ShellRenderer contact path with adaptive budgets, compute phases, construction/return poses and essential-face eligibility; existing tests covered these mostly separately.

Only `JarvisThermalVisualContractTest.kt` and this handoff are owned by this worker. The three tests passed in one focused Kotlin/JVM run: 0 failures/errors/skips; compiler256MB and runner128MB. Initial isolated compilation omitted the existing internal JarvisTileEnergy source; adding it resolved the harness setup without any product-source change. No production source, main branch, signing, model, asset or release has changed.

Expected contract:
- Thermal/power/accessibility floors keep visible contact feedback static across every compute phase.
- Warm thinking/research/learning retains animated construction under reduced decorative budgets.
- Transition to severe heat settles all three work windows and permits return immediately.
- Essential face eligibility never re-enables the decorative pulse; foreground and accessibility gates remain authoritative.

This is policy/painter regression evidence only. Device GPU timings, energy, thermals, live TTS/lip synchronization and Fold7 acceptance are NOT_RUN. No CPU-temperature or performance claim is made.

Evidence: `docs/evidence/cinema-thermal-visual-20261110/RESULT.json` pins all compiled production/test sources and dependency jars; `junit.txt` contains the actual result. The host dependency contains documented tile/STOP boundary shims; this is not an Android runtime test.

Next: cherry-pick this test into PR715 and include `at.heinz.deviceai.ui.JarvisThermalVisualContractTest` in the root Android/JVM test set. Standard test task: `:app:testDebugUnitTest --tests at.heinz.deviceai.ui.JarvisThermalVisualContractTest`, with the existing owner-local QA flags, not an APK/signing task. Rollback removes only the new test, handoff and evidence.
