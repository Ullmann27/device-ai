# Zweite Dreiergruppe: LM1 / 16M-Training

Status: **SUPPORT_COMPLETED / NATIVE_UPDATE_OBSERVED / REGISTRATION_CONFLICT_RETAINED**. Heinz hat am 04.11.2026 drei weitere Helfer beauftragt. Alle drei Beiträge sind geliefert, integriert und unabhängig geprüft.

## Abschluss dieser Runde

| Beitrag | Geprüftes Ergebnis |
| --- | --- |
| `weight16m_gradient_check_r2` | 234 ausgewählte Koordinaten in zwei winzigen minGRU-Fixtures stimmen innerhalb der festen Toleranz mit einer unabhängigen Double-Rechnung überein. Direkte Float32-Differenzen bestehen teilweise nicht; alle Abweichungen sind erhalten. Root hat den Lauf bytegleich reproduziert. |
| `weight16m_training_probe_r2` | Ein echter nativer AdamW-Update am vorhandenen 16.011.776-Parameter-Modell: 16.000.944 Werte numerisch geändert, Verluste/Gewichte endlich, Originaldateien unverändert. 435,332 ms im Trainingsaufruf, 2,604 s gesamter Prüfprozess, 397.604 KiB / 388,285 MiB maximaler Kindprozess-RSS. |
| `weight16m_recovery_audit_r2` | 56 Git-Blobs, 20 nicht versiegelte registrierte Dateien, 12 Receipt-/Logpaare und 192 Entwicklungsfallzeilen aus #508 konsistent. Root hat den vollständigen Audit erneut ausgeführt. |

Zusätzlich bestehen 19 neue Python-Prüffälle (14 Recovery-Audit, fünf Sequenzbindung) und der unabhängig wiederholte native Tiny-Selbsttest. Root hat eine konkrete Lücke des Recovery-Prüfers geschlossen: Fehlende registrierte Quelldateien verhindern jetzt den Gesamtstatus PASS. Fehlereinspritzungen vor und nach der Korrektur sowie der zusätzliche Regressionstest belegen die Reparatur; der vollständige Audit mit allen vorhandenen Dateien ergibt unverändert dasselbe Resultat. Die ursprüngliche Helferlieferung mit 13 Tests bleibt archiviert. Diese Zahlen sind keine Qualitätswerte des 16M-Modells. Die erste Runde mit 109 Prüfungen bleibt als historischer eigener Prüfstand in #507 erhalten; ihre unveränderten Gates wurden hier nicht unnötig erneut ausgeführt.

**Offengelegte Registrierungsabweichung:** Root hat im numerischen JSON vor dem Modelllauf fälschlich BOS/EOS 256/257 eingetragen. Der eingefrorene Harness und das Protokoll verwendeten korrekt `tokenizer.bos/eos`; tatsächlich waren dies beim Tokenizer mit 254 Merges die IDs 510/511. Deshalb gilt **nativeUpdate=PASS_OBSERVED, registrationMetadata=FAIL_SEQUENCE_CONFLICT, vollständige Protokollkonformität=false**. Registrierung und Logs bleiben unverändert. Der neue semantische Vorabcheck weist diese falschen Metadaten zurück. Die unabhängige read-only-Abnahme bestätigt Befund und Einstufung. Es gab keinen weiteren echten Modelllauf.

Die NLL auf denselben sieben synthetischen Trainingszielen sank von 6,7283 auf 2,4385. Das belegt keine allgemeine Sprachqualität. Kein trainierter Checkpoint wurde gespeichert. Direkte Gradienten-/Optimierermomentprüfung am echten Modell, längere Sequenzen, größere Batches, Dauertraining und Android bleiben ungeprüft.

Beim separat geprüften #508-Snapshot `7131c1a50a9c697e481a6debe259758dbf422eef` stiegen Intent-Entwicklungsantworten nach 128 Schritten von 0/24 auf 8/24 (klein) beziehungsweise 10/24 (6,56M). Die festgelegte 12/24-Hürde wurde verfehlt; alle sechs nachgelagerten Kapazitätsprüfungen scheiterten. Spätere #508-Ergebnisse sind anhand ihres neuen Commits separat zu prüfen.

Vollständige Befunde, Erratum, Rohbelege und reproduzierbare Befehle: [R2-Evidenzindex](../../../experimental/weight-space-20261104/support-round2/README.md). Die neue Software und das Ergebnis liegen ausschließlich im experimentellen Bereich. Keine Produktionsruntime-, Modellwahl-, Main-, APK- oder Offline-Einstellungsänderung.

## Nächster Einstieg

Aktuellen #508-Stand prüfen und dessen laufende Qualitätsversuche koordinieren. Ein neuer 16M-Ressourcenversuch muss eine neue, vorab widerspruchsfreie Registrierung erhalten und Batch-/Sequenzlänge ausdrücklich festlegen; die unveränderte fehlerhafte V1-Registrierung darf nicht nachträglich korrigiert oder als vollständig bestanden ausgegeben werden. Ein größerer Trainingslauf oder ein Gerätemeilenstein folgt aus dieser Ein-Schritt-Beobachtung nicht automatisch.

## Historischer Startauftrag und Zwischenstände

## Gemeinsamer Anschluss

- Basis dieser Unterstützung: PR #507, `e726deb96dc74bacd40c2fe0ef640c6db57903f5`, Baum `ef283f27ccd7ca2c2951391a9f205e994d85bc09`.
- Unveränderte LM1-Kernel: PR #492, `30cd3a89810ed14fa84eca95236d3177e016118d`.
- Führende Trainingsforschung: PR #508, zuletzt gelesener Stand `7131c1a50a9c697e481a6debe259758dbf422eef`. Dort existieren inzwischen study-v1-Rohbelege und ein gesondert registrierter long-study. Die ältere Statuszeile WIP/EXPERIMENTS_NOT_RUN beschreibt den Dateistand daher nicht vollständig; konkrete Ergebnisse werden vor Aussagen geprüft.
- Eigener Branch: `research/continuum-16m-training-support-r2-20261104-319f3e`. Produktive Dateien, #508 und seine Datensplits bleiben unangetastet.

## Genau drei neue Helfer

| Agent | Auftrag | Eigener Dateibereich unter experimental/weight-space-20261104/support-round2/ |
| --- | --- | --- |
| weight16m_gradient_check_r2 | Numerische Gegenprüfung der existierenden minGRU-Gradienten mit kleinen synthetischen Beispielen, korrekter Float32-Fehlerbewertung und Negativkontrollen. | LmGradientAudit.kt, GRADIENT_AUDIT.md, eigene Evidenz |
| weight16m_training_probe_r2 | Native Messung vorbereiten, ob ein begrenzter echter 16M-Trainingsschritt durchläuft und welche Ressourcen er benötigt. | LmTrainingResourceProbe.kt, TRAINING_RESOURCE_PROTOCOL.md, eigene Tests/Evidenz |
| weight16m_recovery_audit_r2 | Unabhängige Konsistenzprüfung der neuen #508-Rohbelege und Entscheidungen, ohne dessen Trainingsläufe zu duplizieren. | recovery_evidence_audit.py, eigene Tests, RECOVERY_AUDIT.md, eigene Evidenz |

Root integriert, prüft und veröffentlicht. Vor einem tatsächlichen 16M-Optimizerschritt werden der fertige Harness, die genaue Konfiguration, Quellen-/Eingabehashes und der Ablauf separat festgeschrieben und veröffentlicht. Tiny-Gradienten-/Format-/Controllerprüfungen sind keine Modellqualitätsexperimente.

## Koordinator-Zwischenstand

Der neue externe Messwrapper `support-round2/run_resource_measurement.py` ist erstellt; Python-Syntaxprüfung PASS, echte Ausführung noch NOT_RUN. Er verlangt eine vorher committed Registrierung und überprüft deren exakte Bytes sowie die Dateiidentitäten vor dem Start. Er verwendet den vorhandenen GNU-timeout-Prozess mit festem 120-Sekunden-Limit, startet keinen Shelltext und hält native Ausgabe, Fehlerausgabe, unveränderte Eingabehashes und Linux-Ressourcendaten fest. Der Peak wird als Prozessmessung mit offengelegtem Git-Preflight-Basiswert ausgewiesen. Eine erfolgreiche Prozessbeendigung allein wird nicht als erfolgreicher Lernnachweis ausgegeben.

## Geplanter eng begrenzter Ressourcenversuch

Geplant ist ein einzelner AdamW-Update mit dem vorhandenen 16.011.776-Parameter-Checkpoint, einem frisch festgelegten synthetischen Beispiel mit höchstens acht Tokens, Batch 1 und einem Worker. Maximale JVM-Heapgrenze 3 GiB, Prozesszeit höchstens 120 Sekunden. Keine adaptive Erweiterung nach Fehler oder Timeout. Die konkreten Hyperparameter und Ein-/Ausgabebelege werden vor Ausführung festgelegt; ein einzelner Schritt muss tatsächlich eine von null verschiedene Lernrate verwenden.

Modell-SHA-256: `93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312`; Tokenizer-SHA-256: `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`. Originale sind schreibgeschützt zu behandeln. LM1C v1 enthält keinen vollständigen Optimizerzustand; ein frischer Optimizer ist ausdrücklich Teil des Versuchs. Das Ergebnis soll die Trainingsspeicherlücke schließen und belegt allein keine Sprachqualität.

## Grenzen und Rückfall

Der neue versiegelte Testsplit wird nicht geöffnet. Alte Holdouts werden nicht zum Tuning benutzt. Kein neues Trainingsframework, kein Austausch der Produktionsruntime, keine fremden vortrainierten Gewichte, kostenpflichtigen APIs, Actions-Läufe, Modellpromotion, Main-Merges, App-Installation oder Signierung. App-Offline-Einstellung bleibt unverändert.

Scheitert eine Prüfung, bleibt der Befund erhalten und wird ehrlich als FAIL/UNAVAILABLE/NOT_RUN berichtet. Keine ursprünglichen Modelle oder historischen Belege werden verändert. Rollback: diese separaten Forschungsdateien nicht übernehmen. Die erste Dreiergruppe ist in #507 abgeschlossen; diese zweite Gruppe bearbeitet neue, abgegrenzte Mess- und Prüfaufgaben.

## Registrierung des tatsächlichen Ressourcenversuchs

Der native Harness ist separat kompiliert und sein Tiny-Selbsttest wurde von Root unabhängig wiederholt: PASS, identische zwei frische Updates und Abweisung falscher Eingabeidentität vor Trainerkonstruktion. Der tatsächliche 16M-Schritt ist zum Zeitpunkt dieses Registrierungscommits NOT_RUN. `support-round2/resource-registration.json` bindet den fertigen Harness, unveränderte Kernel, Wrapper, Tool-/JAR-/Eingabeidentitäten und den vollständigen Aufruf. Die feste Lernrate ist 0,0001 mit warmup1/totalSteps1, sieben Targets und einem Worker. `ActiveProcessorCount=1` und `-Xmx3g` sind Teil des registrierten Prozesses. Das genaue Protokoll steht in `TRAINING_RESOURCE_PROTOCOL.md`.
