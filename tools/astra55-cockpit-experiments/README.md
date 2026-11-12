# Gemeinsame Cockpit-Prüfung

Bestehende lokale Werkzeuge verwenden; jeder Ergebnisordner ist neu zu wählen.
Quellen während der Prüfung unverändert lassen. Keine Abhängigkeiten herunterladen.

## Monitor A/B und unabhängiger Holdout

```sh
python experimental/code55-display-uniforms/run_experiment.py \
  --baseline 711f23b33a8fc602a3a0e4baa37aa61e42e6fc64 \
  --compiler-lib "$KOTLIN_2020_LIB" --tmp "$EXPERIMENT_TMP" \
  --output "$NEW_OUTPUT/a-primary"

python experimental/code55-display-uniforms/run_experiment.py \
  --baseline 711f23b33a8fc602a3a0e4baa37aa61e42e6fc64 \
  --compiler-lib "$KOTLIN_2020_LIB" --tmp "$EXPERIMENT_TMP" \
  --output "$NEW_OUTPUT/a-holdout" --holdout
```

KOTLIN_2020_LIB bezeichnet vorhandene Gradle 8.11.1-Bibliotheken mit Kotlin 2.0.20.
Hostfixtures prüfen Schnittstellenaufrufe und Zustände, keine native GPU-Ausführung.

## Workflow mit aktueller API

```sh
python tools/astra55-workflow-experiment/run.py \
  --repo "$EXPERIMENT_REPO" --cache "$GRADLE_MODULE_CACHE" \
  --out "$NEW_OUTPUT/workflow" \
  --baseline 711f23b33a8fc602a3a0e4baa37aa61e42e6fc64
```

GRADLE_MODULE_CACHE: vorhandenes modules-2/files-2.1 mit Kotlin 2.4.0/JUnit.
ComputerWorkObservationExperimentTest ist auch eine reguläre Gradle-Testklasse.

## 3D-Quellenbindung

Exakter B-Replay samt CLI und 30 Tests:
docs/evidence/astra55-experiment-b-scene-20261112/README.md.
Er lief im eingefrorenen separaten #806-Baum und schützt bewusst auch
unbeteiligte Dateien. Für die absichtlich veränderte A/#803-Kombination
wurden seine 64 kompilierten Eingaben exakt abgeglichen, der Transformblock
unabhängig gegen die neue Hauptbasis geprüft und vollständige Androidklassen
neu kompiliert. Die unterschiedlichen Nachweise bleiben getrennt.

## Gemeinsame Android-/Filament-Kompilation

```sh
python tools/astra55-cockpit-experiments/verify_android.py \
  --repo "$EXPERIMENT_REPO" \
  --baseline 711f23b33a8fc602a3a0e4baa37aa61e42e6fc64 \
  --environment-receipt "$EXISTING_COMPILE_RECEIPT" \
  --gradle-cache "$GRADLE_MODULE_CACHE" \
  --android-jar "$ANDROID_36_JAR" \
  --java "$JDK17_JAVA" --out "$NEW_OUTPUT/android-compile"
```

Der bestehende Compilerbeleg liefert classpath und source_hashes oder
product_source_hashes. base-classes.jar liefert übrige unveränderte
Abhängigkeiten; alle ausgewählten Produktdateien werden neu übersetzt.
Veränderliche Klassenverzeichnisse werden kopiert, JARs per SHA256 gebunden.
512 MiB, ein Prozessor; bei geteilter Umgebung die bestehende Build-Sperre nutzen.

Tatsächlicher gemeinsamer Lauf: 53 Produktionsdateien, SDK36, Filament 1.56.0,
Kotlin 2.4.0, Exit0. Beleg unter docs/evidence/astra55-experiments-combined-20261112/.
A zählt Szenarioläufe, B/C zählen JUnit-Methoden; Zahlen nicht zusammenaddieren.
