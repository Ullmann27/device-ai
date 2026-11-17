# Continuum CSX1 – Erster **selbst gebauter** Residual-Pointer (22.10.2026)

**Status: JVM_SHADOW_PROTOTYPE / NOT_ANDROID_VERIFIED / NOT_PRODUCT_APPROVED**

## Was tatsächlich neu gebaut wurde

Branch `chatgpt/continuum-residual-pointer-built-20261022`, direkt auf dem signierten unabhängigen Review-Branch PR #60 (`5a1926e57794faabd8b5de8634b4a1ad73e77ebf`). Experimente EXP-04, EXP-05, EXP-06, EXP-07, EXP-12. Keine Opus-/fremde Ausführung: Der Kernel und die Probe wurden direkt in der aktuellen Sitzung erstellt, lokal mit `kotlinc-jvm 1.9.0` und JDK 21 ausgeführt und nach GitHub übertragen.

- `ContinuumResidualSpanCore.kt`: isolierte Kotlin-Implementierung mit exakt 4.110 trainierbaren Parametern (2×128×16 Embedding-Deltas, neun Span-Gewichte, Span-Bias, vier Null-/Enthaltungsgewichte). Fixed sin/cos-Basis plus `B=<u0, deltaK>+<deltaQ, v0>+<deltaQ, deltaK>` als trainierbarer Residualterm; der unmittelbare Embeddinggradient muss bei null trainierbaren Werten nicht null sein. Dies ist **kein** allgemeines Sprachmodell, kein Nachweis für echtes universelles Begriffsverständnis; handcodierte grammatische Marker bleiben erhalten. Exakte Ursprungs-Substring-Offsets, Fail-Closed bei fehlendem/mehrdeutigem Goldtoken, nicht geclampte LogSumExp-Cross-Entropy, komplette Gradientensammlung vor einem einzigen gebündelten Update. Schutzgrenzen auf UTF-8-Belegfrage, Tokens, Klauseln, Kandidaten, Buckets, Updates und Gewichtsgrößen. Deterministische Initialisierung und korruptionsprüfbare **eigene** Checkpointbytes mit exakter Länge + SHA-256. **Keine Persistenz in aktive Modellpfade.**
- `ContinuumCounterfactualCurriculum.kt`: reiner Offline-Triplet-Generator. Setzt einen bereits unabhängigen *menschlich/autorisierten* Prüffall voraus. Kontrolliert eindeutige Originalspanne, Ersatzspanne, keine wertbezogenen Lecks in Austausch/Negativfall und echte Kandidatenextrahierbarkeit. Ein Negativbeleg ohne die Zeichenkette allein ist **kein automatischer semantischer Wahrheitsbeweis**; negatives Gold benötigt separate Autorprüfung. Kein ungeprüftes Webtraining.
- `ContinuumResidualSpanCoreChecks.kt`: ausführbarer Kotlin-JVM-Gradienten-/Checkpoint-/Selbstspiel-/Cancellation-/Preflight-/Mess-Test. `ContinuumResidualSpanCoreTest.kt`: JUnit-Brücke, damit Androids existierende `:app:testDebugUnitTest` diese Check-Routine nach späterem tatsächlichem Gradle-Setup ausführen kann.

## Reale, lokal beobachtete JVM-Ergebnisse – offengelegte Entwicklungsfälle

Auf dem aktuellen lokalen JVM-Kotlin-Prototyp wurden die folgenden Befunde **tatsächlich ausgeführt** (keine simulierten Werte):
- `kotlinc` kompiliert ohne Fehler; `java` Testlauf Exitcode 0.
- Residual-Gradient: 38 aus aktiven Embedding- und Kopfparametern ausgewählte finite-difference-Gegenprüfungen bestanden; maximale absolute Abweichung `1.5443709852624998E-10` (Schranke `1e-6`).
- Ungültige und doppelte positive Goldspannen wurden **ohne Gewichtsupdate** abgewiesen; Stornierung und Preflight-Poisoning vor jedem ersten Update bestanden.
- Checkpoint-Roundtrip, exakte Länge und manipulierte SHA-256-Bytes wurden korrekt geprüft.
- Positiv/Austausch/ausdrücklicher separat geprüfter Negativbeleg erzeugt; wertbezogene Leckage korrekt abgewiesen.
- Auf **fünf öffentlichen synthetischen Entwicklungsfällen**: `2/5` Treffer vor Training, `3/5` nach Training; 300 tatsächlich ausgeführte Updates, Trainingsverlust `1.2136851176488221 -> 0.6067638047117935`.
- Die drei positiven **neuen Werte** `Salzburg`, `XZ-902`, `Zange` wurden nach Training richtig extrahiert; die zwei unbelegten Fragen wurden leider **falsch beantwortet statt abgelehnt**. Das ist ein relevantes negatives Forschungsresultat: `unsupportedClaims=2/2` auf dem Entwicklungsset.
- Diese fünf Fälle sind **kein** unabhängiger Blind-Holdout und beweisen keine kalibrierte Enthaltung oder langfristige Generalisierung. Die verbesserte Gesamttrefferzahl `+1` ist nicht mit einer klaren kombinierten Sicherheitsverbesserung gleichzusetzen.

Die in GitHub eingecheckten Dateien wurden nach dem lokalen Prüflauf manuell in ihre endgültige Kotlin-Struktur übertragen. Für die **exakten GitHub-Blobs** steht der nächste vollständige Gradle-/kotlinc-Rücklauf im sauberen Repository-Checkout noch aus. Keine behauptete Android-APK- oder Samsung-Gegenprüfung.

## Reproduktion im geklonten Repository

```sh
kotlinc \
  app/src/main/java/at/heinz/deviceai/learning/span/ContinuumResidualSpanCore.kt \
  app/src/main/java/at/heinz/deviceai/learning/span/ContinuumCounterfactualCurriculum.kt \
  app/src/test/java/at/heinz/deviceai/learning/span/ContinuumResidualSpanCoreChecks.kt \
  -include-runtime -d /tmp/continuum-csx1-checks.jar
java -cp /tmp/continuum-csx1-checks.jar \
  at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreChecks

# Erst in einer Gradle-konfigurierten Checkout-/CI-/Android-Buildumgebung:
gradle :app:testDebugUnitTest --tests \
  at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreTest
```

## Nächste Eigenentwicklungsrunde

**P0:** Anstehende Schwäche direkt bearbeiten: Abstention bei fehlenden Belegen auf *anderen* Entitäten/Satzfamilien, unabhängige harte Gegenbeispiele und Abgrenzung gegenüber einem einfachen Lookup-/Heuristikvergleich. Nicht die fünf bereits sichtbaren Messbeispiele als „Blindtest“ recyceln. Zusätzliche Fragen nach mehreren Entitäten, Negation, Antwortstring mit Sonderzeichen, mehrteiliger Spanne und echten/scheinbaren Widersprüchen dürfen aktuell nicht als gelöst bezeichnet werden.

**P1:** Erst wenn die Invarianz-/Gradienten-/Abstention-Gates eingehalten werden, Vergleich mit ungeübtem CSX1, Regeln-allein, Original-CDX1 und trainierten Komponenten. Danach optional eigens getestete semantische P31/P279-Provenienzquelle; **keine** ungesicherten Freitextbeschreibungen, API-Aufrufe oder Web-Scraper als autonome neuronale Trainingsquelle.

## Kontinuität

Alle Dateien offener Kotlin-Klartext, eigener Debug-/Forschungsnamespace. Keine Bearbeitung an SWB1, CDX1, `ContinuumAtomicPairedBundleStore`, Render-APK, Produktivchat, originalen Signierassets, Besitzerfakten, privaten Gewichten oder `main`. Kein automatischer Merge oder Installationsaufruf. Rücknahme durch Nicht-Mergen des gestapelten Draft-PRs; keine Benutzerdatenmigration.
