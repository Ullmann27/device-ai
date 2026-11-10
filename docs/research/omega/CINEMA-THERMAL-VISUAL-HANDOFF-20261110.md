# Thermal visual composition checkpoint

Status: WIP / UNVERIFIED. Production policy unchanged.

Base: local `fac1e1e`, remote `31f61c6f1ff24f116135824403f0a6a57721c787`, identical tree `6aa5201f1bd29b2f9c5fb193aafe1050483671c5`.
Canonical integration owner: root; target Cinema PR715.

The existing policy keeps low-tier essential face frames separate from decorative motion. No confirmed runtime budget defect was found. The missing regression combines the actual ShellRenderer contact path with adaptive budgets, compute phases, construction/return poses and essential-face eligibility; existing tests covered these mostly separately.

Only `JarvisThermalVisualContractTest.kt` and this handoff are owned by this worker. Tests are staged but not executed while the shared compiler/render lane is occupied. No production source, main branch, signing, model, asset or release has changed.

Expected contract:
- Thermal/power/accessibility floors keep visible contact feedback static across every compute phase.
- Warm thinking/research/learning retains animated construction under reduced decorative budgets.
- Transition to severe heat settles all three work windows and permits return immediately.
- Essential face eligibility never re-enables the decorative pulse; foreground and accessibility gates remain authoritative.

This is policy/painter regression evidence only. Device GPU timings, energy, thermals, live TTS/lip synchronization and Fold7 acceptance are NOT_RUN. No CPU-temperature or performance claim is made.

Next: run the three focused tests with existing host tools after a compiler lease; preserve actual result and exact dependency/source hashes. Then cherry-pick this test into PR715 and run it in the root Android/JVM test set. Rollback removes the new test and handoff only.
