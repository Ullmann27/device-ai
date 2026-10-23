# I2 – Belege (Opus, 22.10.2026)

Umgebung: Linux x86_64, OpenJDK 21.0.12.1, `kotlinc-jvm 2.0.20`, echtes JUnit 4.13.2 + hamcrest-core 1.3 (kein Shim).
Kein Android-SDK, kein Gradle, kein APK, kein Gerät.

## Prüfung PR #68 (`07b66f40d098aed5b038c94ac872011065277f7d`)

| Schritt | Befehl | Exitcode | Ergebnis |
|---|---|---|---|
| Kompilieren | `kotlinc main/java/at/heinz/deviceai/learning/span/*.kt test/java/at/heinz/deviceai/learning/span/*.kt -cp junit-4.13.2.jar:hamcrest-core-1.3.jar -jvm-target 17 -include-runtime -d pr68.jar` (in `app/src`) | 0 | keine Fehler |
| JUnit | `java -cp pr68.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar org.junit.runner.JUnitCore …ContinuumResidualSpanCoreTest …ContinuumResidualSpanCoreVariantsTest …ContinuumResidualSpanCoreSpanTest …ContinuumSpanAbstentionBenchTest …ContinuumCertifiedAbstentionTest` | 0 | `OK (17 tests)` → `pr68-junit.txt` |
| I1-Reproduktion | `java -cp pr68.jar … ContinuumSpanAbstentionExperiment certify` | 0 | 40 Zeilen → `pr68-certify-reproduction.txt` |
| Vergleich | `diff docs/evidence/csx1-abstention-20261022/milestone-i1-certify.txt pr68-certify-reproduction.txt` | 0 | **bytegleich** |

Bytegleich bedeutet: Checkpoint `1786fa9e…`, Zertifikate α = 0,10 / 0,05 / 0,02 mit den Schwellen 0,00 / 0,40 / 0,64,
Holdout-, TEST- und Neuaufteilungszahlen sind unverändert. Der modellgebundene Antwortweg aus PR #68 liefert also
dieselben Antworten wie der frühere Weg über zwischengespeicherte Vorhersagen.

Codeprüfung der Modellbindung:
- `ContinuumCertifiedAbstention.answer(model, evidence, question, certificate)` hält `synchronized(model)`. Hash und
  Vorhersage laufen unter demselben Monitor. Alle verändernden und lesenden Methoden von `ContinuumResidualSpanCore`
  sind `@Synchronized` und sperren damit dieselbe Instanz: `loadParameters`, `train`, `trainSession`, `save`,
  `predict`, `parameters`, `gradient`, `loss`. Ein Gewichtstausch zwischen Hash und Vorhersage ist damit
  ausgeschlossen.
- Der Checkpoint enthält auch den Update-Zähler. Darum macht schon ein zusätzlicher Trainingsschritt das Zertifikat
  ungültig; das prüft der neue Test aus PR #68.
- Es gibt keinen Aufrufer der entfernten Signatur `answer(prediction, certificate)` mehr (`grep` über `app/src`).
- Grenze, wie im Audit (#69) vermerkt: Ein `Certificate` lässt sich weiterhin von Hand konstruieren. Es ist kein
  kryptographisch attestiertes Kalibrierergebnis.
