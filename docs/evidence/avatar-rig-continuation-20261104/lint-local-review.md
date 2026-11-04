# Lokaler Lint-/JVM-Befund, 04.11.2026

Nur lesende Prüfung der beiden angeforderten Logs, der vorhandenen Toolchain-/Projektkonfiguration und des lokal installierten AGP-8.10.0-Bytecodes. Kein Gradle-Lauf und keine Codeänderung durch diesen Helfer.

## Nachgewiesene Konfiguration

- Beide Logs gehören zu `f5dc6e08acb88b30273410b8efcc7e0db446020b`, Baum `58dc99f0145272775e0a2554d7d8a4ccc7d6be4a`.
- Projekt: Gradle 8.11.1, AGP 8.10.0, Kotlin-Plugin 2.4.0. Der tatsächliche Gradle-Daemon verwendet laut beiden Logs Eclipse-Adoptium-Java 17.
- `toolchain/run_gradle.py` setzt bereits `--no-daemon`, `--max-workers=1` und `-Pkotlin.compiler.execution.strategy=in-process`. Die Logs bestätigen dennoch den erwarteten einmaligen Gradle-Daemon zur Anwendung der JVM-Optionen.
- Erster Lauf: tatsächliche Daemon-Option `-Xmx2g`. Separater Retry: tatsächliche Daemon-Option `-Xmx1536m`.
- `app/build.gradle.kts` prüft ausschließlich `NewApi` mit `abortOnError=true`; keine Regel wurde geändert.

## Exakte lokal überprüfte AGP-Schalter

- `android.experimental.runLintInProcess`: `BooleanOption.RUN_LINT_IN_PROCESS`, Default **true** in AGP 8.10.0.
- `android.experimental.lint.heapSize`: `StringOption.LINT_HEAP_SIZE`.
- `LintTool.initialize` übernimmt diese Eigenschaften als `runInProcess` und `workerHeapSize`.
- `LintTool.submit` wählt bei `runInProcess=true` ausdrücklich `WorkerExecutor.noIsolation()`. Nur der false-Zweig ruft `WorkerExecutor.processIsolation(...)` auf.

Damit adressiert `org.gradle.jvmargs` bei dem beibehaltenen In-process-Modus die maßgebliche JVM. Ein zusätzlicher Gradle-Worker-Cap wäre keine neue Begrenzung, da bereits ein Worker gesetzt ist. `android.experimental.lint.heapSize` ist keine zusätzliche Heap-Grenze des bestehenden In-process-Laufs.

Prüfquelle: `/workspace/scratch/b6ee64d418cf/toolchain/gradle-cache/caches/modules-2/files-2.1/com.android.tools.build/gradle/8.10.0/17bb1a87438ee832f8902c84b9791adf196d904d/gradle-8.10.0.jar`, gelesen mit dem vorhandenen JDK-17-`javap`, ohne AGP/Gradle auszuführen.

## Beobachtung und Grenze

Beide Logs enden bei `:app:lintAnalyzeDebug` mit verschwundenem Daemon. Im reinen Lint-Retry war `:app:compileDebugKotlin` bereits `UP-TO-DATE`. Die Logs enthalten keinen `OutOfMemoryError`, keine ursächliche Stacktrace-Ausgabe und keinen bestätigten externen Kill-Grund. Die Warnung über zukünftige Mindestanforderungen von Kotlin 2.5.0 ist kein Nachweis einer Ursache des gegenwärtigen Abbruchs.

Root plant einen materiell anderen Versuch mit tatsächlichen Daemon-Grenzen `-Xmx1024m`, `-XX:MaxMetaspaceSize=384m`, `-XX:ReservedCodeCacheSize=128m`, `-XX:ActiveProcessorCount=2`, zusätzlich `-XX:+ExitOnOutOfMemoryError` und einem dedizierten GC-Log. `android.experimental.runLintInProcess=true` und alle Prüfregeln bleiben erhalten. Aus dem gelesenen Bytecode ergibt sich dagegen kein konkreter Einwand.

Ein dabei möglicherweise neu protokollierter Heap-/Metaspace-Fehler gilt zunächst für diese neuen Grenzen. Er beweist nicht rückwirkend eine OOM-Ursache der beiden ursprünglichen Abbrüche. Das Ergebnis dieses geplanten Versuchs ist hier **nicht** als ausgeführt oder erfolgreich behauptet.

Gelesene Logs:

- `/workspace/scratch/b6ee64d418cf/toolchain/logs/avatar-round2-final-20261104-1951.log`
- `/workspace/scratch/b6ee64d418cf/toolchain/logs/avatar-round2-lint-retry-20261104-1955.log`
