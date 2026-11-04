# Drei zusätzliche Helfer: Evidenz und integrierte Prüfung

Forschungsbasis: `30cd3a89810ed14fa84eca95236d3177e016118d`, PR #492. Anschluss: Draft-PR #507.

## Abgeschlossene Unterstützung

Alle drei Beiträge sind integriert und unabhängig geprüft. Die unten beschriebene Ausgangsprüfung bleibt als eigener Messzeitpunkt erhalten. Am korrigierten Driver bestehen die 75 bisherigen Gates, zehn neue Veröffentlichungsfälle und ein unabhängiger echter SIGKILL-Fall: **86/86**. Dazu bestehen neun EOS-Tests mit historischer Regression und 14 Preflight-Tests: **109/109 Software-Prüffälle** insgesamt. Neue Trainingsläufe: **0**.

| Beleg | Inhalt |
| --- | --- |
| [Integrierte Gates](integrated-gates/summary.json) | Driver-/Testhashes, Exitcodes und SHA-256 der sechs vollständigen Logdateien im selben Ordner; 86 Fälle. |
| [SIGKILL-Gegenprobe vor dem Fix](independent-publish-crash-red.json) | Echter Prozessabbruch hinterlässt am ursprünglichen Driver einen unvollständigen endgültigen Marker; derselbe Fall besteht am korrigierten Driver. |
| [Teilwrite-Gegenprobe](independent-partial-write-baseline.json) | Ursprünglicher Fehler bei abgebrochener JSON-Ausgabe. |
| [Finales Prüfreceipt](final-checks/receipt.json) | 23 weitere Tests, echte historische EOS-Auswertung und tatsächlicher 16M-Preflight, genaue Befehle und Vor-/Nachher-Identitäten. |
| [Python-Preflight](final-checks/preflight-real16m.json) | 57 Tensoren und 16.011.776 endliche Floatwerte vollständig gelesen; Checkpoint und Tokenizer geprüft. |
| [EOS-Auswertung](final-checks/eos-actual-audit.json) | 16 festgelegte Git-Blobs; vier Laufaufzeichnungen, drei fortgesetzte Checkpoint-Identitäten, keine neue Inferenz. |
| [Native Kapazitätsrohdaten](real16m/capacity.tsv) / [Receipt](real16m/receipt.json) | Separater tatsächlicher Inferenztest am vorhandenen 16M-Modell. |

Die neue Gate-Suite reproduzierte am unveränderten Driver vier Fehler. Ihre unveränderten Rohbelege sind zusätzlich unter [RED](../g1-evidence/red-report.json), [GREEN](../g1-evidence/green-report.json) und den benachbarten `red.log`/`green.log` gespeichert. Die absoluten Arbeitsverzeichnisse im Helferreceipt beschreiben die ursprüngliche Ausführung; die kopierten Berichte behalten ihre dort angegebenen Prüfsummen.

Die Integrationskontrolle vergleicht Logs und finale Quellen erneut mit den Receipts. Die Veröffentlichung muss denselben Git-Baum wie die lokal geprüfte Fassung enthalten; eine abweichende Baumidentität gilt nicht als geprüfter Abschluss. Die endgültige Commit-/Baumidentität wird im PR-Kopf dokumentiert.

## Tatsächlich ausgeführt

- Die vier unveränderten Python-Suiten: 36 + 6 + 4 + 29 = **75/75 PASS**. Synthetische Gates; keine Modellprozesse in diesen Tests.
- Die unveränderten sechs LM-Kotlinquellen und drei vorhandenen Experiment-Kotlinquellen wurden mit lokal vorhandenem Kotlin 2.0.20 / Java 21.0.13 für JVM17 neu kompiliert. Quellen- und Werkzeughashes stehen in `compile-manifest.json`.
- Das gespeicherte Originalprüfpaket wurde materialisiert; Modell- und Tokenizerbytes stimmen mit dessen Manifest überein. Das Modell ist die historische funktionserhaltende 16M-Erweiterung, keine erfolgreiche Trainingsfortsetzung.
- Ein aktueller read-only Aufruf von `LmCapacityBenchmark` mit diesem Modell: **PASS**, drei gemessene vollständige 1-/32-Token-Paare nach einem Aufwärmpaar. 16.011.776 trainierbare Parameter, 64.048.511 Checkpointbytes; maximal 624,662315 ms pro separatem Ein-Token-Aufruf und 889,600476 ms pro 32-Token-Aufruf. Maximum aller beobachteten VmHWM-Werte: 289.180 KiB (282,40234375 MiB).
- Modell und Tokenizer sind vor/nach diesem Lauf bytegleich. Exitcode 0, Laufzeit des ganzen Prüfprozesses etwa 6,50 Sekunden. `real16m/capacity.tsv` enthält die unveränderte Rohausgabe.

## Grenzen

Dies ist ein neuer Lesetest auf einem Linux-x86_64-Host, kein Android-/Fold7-Test. Der Compiler unterscheidet sich von der historischen Studie; das neu gebaute JAR ersetzt **nicht** deren eingefrorenen JAR-Pin. Das Format-, Integritäts- und Kapazitätsergebnis belegt weder neue Trainingsschritte noch verbesserte Antworten. Qualität wurde nicht erneut bewertet und kein Holdout geöffnet. Neue Trainingsläufe: **0**.

Der Ein-Token-Wert ist ein vollständiger separater Generierungsaufruf einschließlich Promptverarbeitung, kein direkt gemessener Streaming-Zeitstempel. Die Speicherzahl ist der beobachtete Prozesshöchstwert dieses Hostlaufs. Die Schwellen bleiben unverändert: 1.500/5.000 ms, 768 MiB, 64-MiB-Checkpoint.

## Reproduktion

Für die Python-Gates die vier Befehle aus dem README des Experimentordners verwenden. Für den Hosttest alle sechs `app/src/main/java/at/heinz/deviceai/learning/lm/*.kt` sowie `LmWeightSurgery.kt`, `LmWeightExperiment.kt` und `LmCapacityBenchmark.kt` mit Kotlin 2.0.20 für JVM17 kompilieren. Der Build enthält keine Kotlin-Standardbibliothek; diese muss beim Start im Klassenpfad stehen:

```bash
timeout 120s java -Xms64m -Xmx1g -cp /pfad/experiment.jar:/pfad/kotlin-stdlib-2.0.20.jar \
  at.heinz.deviceai.learning.lm.LmCapacityBenchmark \
  /pfad/continuum-lm1-16011776.lm1 /pfad/lm1-tokenizer.lmbp
```

Die konkreten tatsächlich verwendeten Aufrufpfade und Identitäten sind in `real16m/receipt.json` erfasst. Der Originalcheckpoint hat SHA256 `93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312`; der Tokenizer `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`.
