# LM1 / 16M: zweite Dreiergruppe

**Drei Beiträge fertig und unabhängig geprüft. Ein echter nativer 16M-Update wurde beobachtet; die zugehörige numerische Registrierung enthält einen offengelegten Sequenzkonflikt.** Deshalb wird der Lauf nicht als uneingeschränkt protokollkonform abgenommen.

Forschungsbasis: PR #507, `e726deb96dc74bacd40c2fe0ef640c6db57903f5`; diese Runde: Draft-PR #514. Die führende Qualitätsforschung bleibt in PR #508. Produktionsruntime und aktive Modellwahl bleiben unverändert.

## Ergebnisübersicht

| Beitrag | Tatsächlicher Befund | Grenze |
| --- | --- | --- |
| Mathematischer Gradientenaudit | 234 ausgewählte Koordinaten in zwei kleinen minGRU-Fixtures ohne Überschreitung der festen Toleranz gegenüber einer unabhängigen Double-Rechnung; geteiltes Embedding, Akkumulation und Nullausgänge geprüft. | Direkte Float32-Differenzen überschreiten die Toleranz teilweise. Kein allgemeiner Gradienten-/Lernqualitätsbeweis. |
| Native 16M-Ressourcenbeobachtung | Ein echter AdamW-Update, 16.011.776 Parameter/57 Tensoren geladen, 16.000.944 numerisch verändert, endliche Verluste/Gewichte, Originaldateien unverändert. | V1-Registrierungsmetadaten widersprechen der korrekten symbolischen Sequenzdefinition. Kein voller Protokoll-PASS; kein trainierter Checkpoint gespeichert. |
| Recovery-Evidenzaudit | 56 Git-Blobs, 20 nicht versiegelte registrierte Dateien, 12 Receipt-/Logpaare und 192 Intent-Fallzeilen konsistent; Entwicklungsauswahl unabhängig nachgerechnet. | Belegkonsistenz, keine erneute Modell-/Binärprüfung oder Testauswertung. Die Modellgates bleiben negativ. |

Root hat den nativen Gradientenaudit neu kompiliert und ausgeführt. Rohlog und alle 234 Koordinatenwerte sind bytegleich mit dem finalen Helferlauf. Zusätzlich bestehen **18 Python-Prüffälle**: 13 zum Recovery-Audit und fünf zur Tokenizer-/Registrierungsbindung. Der vollständige Git-Audit wurde durch Root erneut ausgeführt und stimmt exakt mit dem Helferergebnis überein. Der kleine native Trainingsprüfer wurde ebenfalls unabhängig kompiliert und mit zwei frischen Tiny-Updates plus falscher Eingabeidentität geprüft.

## Tatsächlicher 16M-Schritt und Registrierungsfehler

Quellen, JAR, Eingaben, Hyperparameter und Budget wurden vor dem Lauf in Commit [01a2370d](https://github.com/Ullmann27/device-ai/commit/01a2370df4399191ebfd3555506a0052f7d0a324) veröffentlicht. Die eingefrorene Kotlin-Datei und das schriftliche Protokoll definierten die Sequenz korrekt über `tokenizer.bos` und `tokenizer.eos`.

Bei der Erstellung der zusätzlichen numerischen JSON-Registrierung hat Root jedoch die BOS-/EOS-Werte des Byte-Tokenizers übernommen. Der tatsächliche Tokenizer hat 254 Merges; nach der unveränderten Produktionsimplementierung sind seine Spezialtoken `256 + merges` und `257 + merges`.

| Bezug | Sequenz |
| --- | --- |
| Numerische V1-Registrierung | `256,112,114,111,98,101,33,257` |
| Eingefrorener Harness / symbolisches Protokoll | `tokenizer.bos,112,114,111,98,101,33,tokenizer.eos` |
| Tatsächlicher Lauf mit dem gebundenen Tokenizer | `510,112,114,111,98,101,33,511` |

Der Unterschied ist semantisch relevant. **Native Beobachtung: PASS. Registrierungsmetadaten: FAIL_SEQUENCE_CONFLICT. Vollständige Protokollkonformität: false.** Die ursprüngliche Registrierung und sämtliche Rohlogs wurden unverändert erhalten. Die unabhängige read-only-Abnahme durch `weight16m_training_probe_r2` bestätigt diese getrennte Einstufung.

| Tatsächlich beobachteter Wert | Ergebnis |
| --- | ---: |
| Modellparameter / Tensoren | 16.011.776 / 57 |
| Optimizerschritte | 0 → 1 |
| Batch / Worker / Targets | 1 / 1 / 7 |
| Lernrate | 0,0001 |
| Numerisch veränderte Parameter | 16.000.944 |
| Nativer Trainingsaufruf | 435,331973 ms |
| Gesamter externer Prüfprozess | 2,604109896 s |
| Maximaler beobachteter Kindprozess-RSS | 397.604 KiB = 388,28515625 MiB |
| Preflight-Kindprozessmaximum davor | 11.392 KiB |
| NLL auf denselben sieben Trainingszielen | 6,728323238 → 2,438547849 nats/Ziel |
| Originalcheckpoint / Tokenizer | Bytegleich geblieben |
| Gespeicherter trainierter Checkpoint | Keiner |

Das RSS-Ergebnis stammt aus Linux `RUSAGE_CHILDREN`. Es ist das Maximum der überwachten Kindprozesse, einschließlich Laden, Prüfungen und Update, nicht die gleichzeitig aufsummierte Speichernutzung aller Prozesse und kein isolierter Optimizerpeak. Ein einzelner acht Tokens langer Batch erlaubt keine Aussage über längere Sequenzen, größere Batches, mehrere Worker, dauerhafte Trainingslast oder Android. Der NLL-Rückgang betrifft unmittelbar trainierte synthetische Ziele und belegt keine allgemeine Sprachqualität. Die Änderungszahl enthält auch native Weight-Decay-Effekte.

Der aktuelle Messwrapper enthält jetzt einen semantischen Vorabvergleich mit den aus dem Tokenizer abgeleiteten Spezialtoken. Ein gezielter Regressionstest weist die unveränderte fehlerhafte V1-Registrierung zurück; korrekte 258er- und 512er-Vokabulare bestehen. Der alte registrierte Wrapper bleibt im ursprünglichen Commit nachprüfbar. **Es erfolgte kein automatischer Wiederholungslauf.** Die aktuellen Wrapperbytes unterscheiden sich bewusst vom alten Registrierungs-Pin. Ein späterer neuer Ressourcenversuch benötigt eine neue widerspruchsfreie Registrierung; V1 darf nicht stillschweigend umgeschrieben oder wiederaufgenommen werden.

## Was der Recovery-Audit bestätigt

Am eingefrorenen #508-Snapshot `7131c1a50a9c697e481a6debe259758dbf422eef` steigen die korrekt erzeugten Entwicklungsantworten nach 128 Schritten von 0/24 auf **8/24** beim kleinen Modell und von 0/24 auf **10/24** beim Modell mit 6.562.304 Parametern. Die Wahl von Schritt 128 folgt der festgelegten Rangfolge. Das größere Modell verfehlt die vorab geforderte Schwelle von 12/24; `NO_DEVELOPMENT_SIGNAL` bedeutet daher keine völlige Wirkungslosigkeit.

Alle vier Diagnosefortsetzungen und beide Intent-Modelle scheitern an ihren separaten Kapazitätsprüfungen wegen sofortigem EOS ohne neue Tokens. Die Testdaten wurden laut den geprüften Aufzeichnungen nicht ausgewertet; der Audit selbst verweigert versiegelte Pfade. Der am Snapshot registrierte 512-Schritt-Versuch ist damit noch kein nachgewiesenes Ergebnis. Spätere #508-Fortschritte müssen an ihrem eigenen neuen Stand geprüft werden.

## Quellen und Reproduktion

- [Gradientenbericht](GRADIENT_AUDIT.md), [finaler Helferlog](evidence-gradient/run.log), [unabhängiger Root-Lauf](root-final-verification/gradient/run.log).
- [Trainingsprotokoll](TRAINING_RESOURCE_PROTOCOL.md), [unveränderte Registrierung](resource-registration.json), [native Rohausgabe](real16m-v1/native.tsv), [Ressourcenreceipt](real16m-v1/receipt.json), [Abnahme und Erratum](real16m-v1/observation-review.json).
- [Recovery-Bericht](RECOVERY_AUDIT.md), [vollständige 56-Blob-Auswertung](evidence/recovery-audit.json), [Root-Prüfreceipt](root-final-verification/receipt.json).

Aus dem Repository-Stamm, ohne Modelltraining:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s experimental/weight-space-20261104/support-round2 -p test_recovery_evidence_audit.py -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s experimental/weight-space-20261104/support-round2/tests -p test_resource_sequence.py -v
python3 experimental/weight-space-20261104/support-round2/recovery_evidence_audit.py --repo . --commit 7131c1a50a9c697e481a6debe259758dbf422eef
bash experimental/weight-space-20261104/support-round2/replay_gradient_audit.sh /absolute/repository /absolute/kotlin-2.0.20-lib /absolute/new-gradient-output
```

Der Git-Audit arbeitet ohne Lazy-Fetch und benötigt die angegebenen historischen Blobs lokal. Die fünf hier zunächst fehlenden Blobs wurden über den autorisierten Connector bezogen und vor Aufnahme in den lokalen Git-Objektspeicher gegen dessen Blob-SHA, `git hash-object` und den festgelegten Git-Baum geprüft. Kein Netzwerkfilter wurde umgangen. Die portierbare Gradientenausführung verlangt einen neuen Ausgabeordner; die historischen Helferskripte daneben dokumentieren die damals tatsächlich verwendeten absoluten Pfade.

Die JARs sind reproduzierbare Buildartefakte und nicht Bestandteil dieses Quellcommits; ihre damaligen Hashes bleiben in den Receipts erhalten. Keine produktiven Dateien, bestehenden Studiengates, Main-Branches, Modellwahl, App-Offline-Einstellungen, Signierung oder Installation wurden verändert.
