# Actually executed verification commands

Product/test source checkpoint: `074fa80729a088644769df300c23b2e093e56553`.
All three final jobs recorded this HEAD before execution. Final tracked source was clean.
Host: Linux x86_64, Node 24.19.0, JDK17.0.21, Kotlin2.4.0, Gradle8.11.1, AGP8.10.0, SDK36.
The available toolchain was selected with `source /workspace/scratch/continuum-toolchains/env.sh`.
Use your own equivalent installed toolchain; no private service is required for these checks.

```bash
# Fresh synthetic fixtures, before final SDK execution
node .github/continuum/contrastive-parity.mjs prepare /workspace/scratch/continuum-0320/parity/contrastive
node .github/continuum/cre-parity.mjs prepare /workspace/scratch/continuum-0320/parity/cre1
node .github/continuum/backlog-fixtures.mjs roundtrip-prepare /workspace/scratch/continuum-0320/parity/backlog

export CONTINUUM_CONTRASTIVE_PARITY_DIR=/workspace/scratch/continuum-0320/parity/contrastive
export CONTINUUM_CRE1_PARITY_DIR=/workspace/scratch/continuum-0320/parity/cre1
export CONTINUUM_BACKLOG_ROUNDTRIP_DIR=/workspace/scratch/continuum-0320/parity/backlog

gradle --offline --no-daemon --max-workers=2 --console=plain \
  -PcontinuumUiWidgetTests=true \
  -PcontinuumUiEvidenceDir=/workspace/scratch/continuum-0320/verified-ui-frames \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug
node .github/continuum/cre-parity.mjs verify "$CONTINUUM_CRE1_PARITY_DIR"
node .github/continuum/contrastive-parity.mjs verify "$CONTINUUM_CONTRASTIVE_PARITY_DIR"
node .github/continuum/backlog-fixtures.mjs roundtrip-verify "$CONTINUUM_BACKLOG_ROUNDTRIP_DIR"

env -u JAVA_TOOL_OPTIONS -u NODE_TEST_CONTEXT -u CONTINUUM_REPAIR_DEPTH \
  node --test --test-reporter=tap --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
npm --prefix backend ci --ignore-scripts --no-audit --no-fund
npm --prefix backend run check
npm --prefix backend test

# Existing static inventory: exit 0; explicitly not runtime/device proof
node scripts/continuum-module-wiring-audit.mjs
# Old v19 pins remain unchanged: exit 2 without --report-only, exit 0 with it
node scripts/continuum-v19-pin-audit.mjs --report-only
# Expected fail-closed release check: exit 69, Unsupported release version
CONTINUUM_OWNER_RELEASE_VERSION=21 bash scripts/render-mentor-preview.sh

# Independent complete existing synthetic autonomous round; not owner/device training.
# Executed with the current compiled main classes and real android.jar, no stubs.
kotlinc -jvm-target 17 \
  -classpath "$PWD/app/build/tmp/kotlin-classes/debug:$ANDROID_HOME/platforms/android-36/android.jar" \
  -script docs/evidence/sol-0320-20261024/auto-dialogue-probe.kts

# Existing production renderer preview, supplementary Java2D evidence only
bash scripts/continuum-hologram-preview.sh /workspace/scratch/continuum-0320/motion

# Actual APK downloaded over HTTPS; only the older signed rollback was verified.
sha256sum /workspace/scratch/continuum-0320/rollback-0.3.19.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs /workspace/scratch/continuum-0320/rollback-0.3.19.apk
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging /workspace/scratch/continuum-0320/rollback-0.3.19.apk
"$ANDROID_HOME/platform-tools/adb" devices -l
```

Final results: Node1109/1109; backend60/60; SDK/JUnit971/971 including32 Robolectric cases;
zero skips/errors; lint0 errors/0 warnings; all final gates/parity/roundtrip exit0.
Instrumentation sources compiled, no connected-device run. No APK assembly or owner signing performed.
Gradle valid up-to-date compilation/lint tasks were retained; full unit tests actually executed.

Earlier commands/results remain in the RED/GREEN logs. The first whole SDK pass had971 cases,
one obsolete same-chat-count assertion and three parity assumptions skipped; the final run corrects
that fixture and supplies all parity inputs. The first whole Node pass had one obsolete archive-counter
assertion; the later failed pass used an obsolete comment delimiter. Both are corrected and fully rerun.
The initial context RED had six genuine core isolation failures plus one overstrict new prompt expectation;
the latter was corrected to test same-ID grounded continuation. Cursor RED had one genuine dangling-link
failure and one too-specific exception-class expectation; malformed cursors already failed closed.
