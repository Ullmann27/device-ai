# Reproduction and interpretation

The tests were updated on unchanged production source853a8ee0. `manifest.json` pins original/candidate test bytes and exact counts. `originals/` preserves every prior test file. `baseline-node.stdout.txt` is the original Node default reporter output; `final-node.tap` explicitly uses the TAP reporter. Both contain37 cases, none skipped. The full original integration failure log is also retained by Quality at `final-node-853a8ee0/node-full.tap`.

Run from repository root:

```sh
node --test --test-reporter=tap .github/continuum/android-holographic-home.test.mjs .github/continuum/app-navigation-wiring.test.mjs .github/continuum/continuum-owner-mainapp-quality.test.mjs .github/continuum/fold7-portrait-reference.test.mjs .github/continuum/nexus-beginner-ui.test.mjs .github/continuum/premium-phone-viewport-wiring.test.mjs
bash docs/evidence/zeta-ui-20261103/source-contracts/run-core-mirror.sh /tmp/zeta-mirror-fresh app/src/test/java/at/heinz/deviceai/ui/ContinuumCoreMirrorTest.kt
```

For the original Kotlin RED run, copy `originals/ContinuumCoreMirrorTest.kt.txt` to a temporary `.kt` file and pass that path to the same helper. The helper compiles current production HomeLayout/HomeScreen/HomeElements/CockpitElements with the selected test; its saved baseline dependency classpath comes from the prepared QA host. It does not start Gradle or an Android device.

Negative checks use `ZETA_MUTANT=<name> node --import ./docs/evidence/zeta-ui-20261103/source-contracts/mutation-check.mjs --test --test-reporter=tap <the same six test files>`. Names and exact exit statuses are in `mutation-results.json`; each raw TAP identifies the expected failing case. Source is changed only in the test process's read function, never in the checkout.

These Node cases guard source wiring and intentional design contracts. They are not replacements for the previously executed Kotlin geometry, native Android Canvas and independent ink audits, or the final real Android/device gates.
