# Existing native capture tests, one configured runner

`run_native_capture.py` ports the current scratch native-sequence command into
the repository. It runs only the existing Gradle `testDebugUnitTest` task with
explicit Device AI test selections, or its two fixed Kotlin compile tasks. No dependencies, APKs, signing, CI dispatch
or production rendering implementation are added.

Before execution, acquire the integrator's render/Gradle resource lease. Set
`JAVA_HOME`, `ANDROID_HOME`, `GRADLE_USER_HOME`, `CONTINUUM_GRADLE_BIN` and the
shared `CONTINUUM_QA_LOCK` to existing configured tools/paths. Existing JVM proxy
and CA settings are inherited, never printed or replaced. No built-in machine
paths or credentials are present. All worktrees must use the same lock; older
scratch runners that do not honor it still need the external lease discipline.

Use a new empty external directory via `--output-dir` or `CONTINUUM_QA_DIR`.
`CONTINUUM_TESTS` accepts comma-separated exact names; repeated `--test` overrides
it. `CONTINUUM_GRADLE_HEAP` or `--heap-mib` controls the one Gradle worker's heap.

```sh
python3 tools/cinema-native-qa/run_native_capture.py --help
python3 tools/cinema-native-qa/run_native_capture.py --output-dir "$CONTINUUM_QA_DIR" --dry-run
```

The dry run validates configuration and reports the fixed command without
starting Gradle or taking the lock. Actual reproduction after the lease:

```sh
python3 tools/cinema-native-qa/run_native_capture.py \
  --output-dir "$CONTINUUM_QA_DIR" \
  --test at.heinz.deviceai.CinemaNativeSequenceUiTest \
  --test at.heinz.deviceai.CinemaStopReturnUiTest \
  --test at.heinz.deviceai.CinemaButtonMaterialUiTest
```

The existing `force_capture.gradle` reruns the selected test task; obsolete
images cannot survive because the output directory must be empty. Source SHA,
app subtree, selected command, fresh JUnit results, image hashes and per-frame
source metadata are recorded in `capture-run.json`. A failure/timeout stays
nonzero even when partial PNGs exist. No raw environment or Gradle log contents
are printed; inspect the retained `run.log` for failures before publishing it.
A successful test exit with missing/failing evidence becomes exit 2.

Default tests use Robolectric API28 native Canvas and explicitly select the
existing 2D head. This is not hardware AGSL, Filament, Fold7, audio or inference
acceptance. Caption/phase metadata remains the existing fixture's responsibility.
This runner does not retrofit missing capture points or make a failing UI pass.

For bounded memory use, compile first with a larger heap, then run native tests
with a smaller heap on the **same checkout and cache**, serially. Use a different
empty output directory for each phase; release the compile process before
starting capture. Example settings used by the integrator, not measured limits:

```sh
python3 tools/cinema-native-qa/run_native_capture.py \
  --phase compile --heap-mib 2200 --output-dir "$CONTINUUM_COMPILE_OUT"
python3 tools/cinema-native-qa/run_native_capture.py \
  --phase capture --heap-mib 1100 --output-dir "$CONTINUUM_CAPTURE_OUT" \
  --test at.heinz.deviceai.CinemaButtonMaterialUiTest
```

`CONTINUUM_QA_PHASE` is the environment equivalent. For the existing scratch
interface, `CONTINUUM_TASKS` accepts only
`:app:compileDebugKotlin,:app:compileDebugUnitTestKotlin` or
`:app:testDebugUnitTest`; other tasks and conflicting phase settings fail.
Compile phase expects no PNG or JUnit receipt and reports only Kotlin
compilation. Cached compilation is not a native capture result. If capture
finds changed/missing compiled inputs, Gradle can still compile; this option
does not guarantee a peak-RAM cap or silently skip required work.

Validation in this checkpoint: Python compilation, `--help`, and seven pure
configuration-rejection checks. **No Gradle/render execution through this new
wrapper yet.** Existing scratch-runner evidence is inventoried separately at
`docs/evidence/cinema-render-coverage-20261110/`.
