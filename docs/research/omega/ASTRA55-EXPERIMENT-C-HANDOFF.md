# Code55 Experiment C: beobachtete Aktionswirkung

MOCK_TESTED / abgeschlossen. Aktuelle Basis `711f23b33a8fc602a3a0e4baa37aa61e42e6fc64`.
Nachlauf: 49/49 Tests, gleiche 36 Experimentabläufe; historischer Erstlauf auf `5f87bdc` bleibt erhalten.
Ziel: `work/astra55-cockpit-experiments-20261112` → #799 → #791.
Bestehende EXP-07/11/12; keine neue Planung, kein neues Modelltraining.

Nach ReAct-/AndroidWorld-/Android-Primärquellenprüfung vergleicht ein
integrierter Test sofortige und verzögerte Nachbeobachtung im vorhandenen
Workflow→Verifier→Bus→Avatar/Cue-Pfad. Harte Gates: kein COMPLETED vor
verifizierter Nachbedingung, keine zusätzliche native Eingabe; Widerspruch
und Stop verhindern Erfolg. Metriken: Eingaben, bestätigte Schritte und
synthetische Ereignislatenzen. Kein behaupteter Gerätelatenzbeleg.

Dateibesitz: ComputerWorkObservationExperimentTest.kt, eigener Runner,
Evidence/Handoff. Kein Produktionsdiff ohne vorher reproduzierten Fehler;
Composer/Grants/Dispatcher bleiben beim parallelen Audit #803.
Besteht der Bestand, wird der Nachweis in die gemeinsame Prüfpipeline
aufgenommen; zusätzliche Produktlogik entfällt mit begründetem Entscheid.
## Historischer Erstlauf auf Basis 5f87bdc

Vorregistrierung `8d523d5d524dd5ad5d6e2a0f4b2938c1535b972b`; öffentlich tree-identisch
`83821ced8c6ac7706e911887a13693e89e901524` laut Koordinator.
Geprüfter Quell-/Testcommit `0fbb410c6bd6f0393c715c0c9436f3c4c2fbbce8`.
Produktionsbasis unverändert. Direkter Kotlin-2.4.0-/JUnit-Lauf, 512 MB, ein Prozessor.

- Drei Experimentmethoden: 36/36 vorregistrierte Abläufe bestehen ihre Gates.
- 44/44 vorhandene Nachbartests bestehen; zusammen 47/47 JUnit-Methoden PASS.
- A4/B12 gültige Abläufe: jeweils genau eine Eingabe, ein verifizierter Schritt, ein Abschluss.
- 16 Widerspruchs- und vier Stop-Abläufe: jeweils eine Eingabe, kein Erfolg.
- Null frühe Erfolgsereignisse; 151-ms-Cues und nach Ende/Stop wartende Cues werden verworfen.
- Erfolgszeiten folgen exakt der injizierten Nachbeobachtung: 0/70/500/1500 ms; keine Gerätelatenzen.
- 26 Quell-/Testdateien unverändert und commitgebunden. Vorregistrierte Gates unverändert.

Stop mit gleichzeitigem Grant-Widerruf liefert intern PAUSED, aber terminale Bus-/Reduceranzeige STOPPED;
kein späterer Erfolg oder nachträglich abgespielter Ton. Vollständige Android-Antwortzustellung NOT_RUN.

Entscheid: Test/Harness in PR #807 → #804 → #799 aufnehmen; keine zusätzliche Produktlogik erfinden.
Kein neuer Planer, kein Modell, keine freigegebenen zusätzlichen Eingaben. Audit #803 bleibt getrennt.

## Anschluss an den kanonischen Auditstand 711f23b

#803 wurde inzwischen regulär in #799 übernommen. Dessen kanonischer Commit
`711f23b33a8fc602a3a0e4baa37aa61e42e6fc64` wurde in die eigene Spur gemergt
(`fa9a1e75d32885a8bef90d8635b9916eb7622897`), ohne manuelle Peer-Produktionsänderung.
Geprüfter Quell-/Test-/Runnercommit: `efe40c98118f01352967564d4c423f2faf017dbd`.

- Runner: optionales `--baseline`, Default weiterhin `5f87bdc`; aktuelle Produktquellen werden strikt gegen die gewählte Basis geprüft.
- Reale `LiveComposeSemantics.kt` wird bei Referenz aus den aktuellen Quellen frisch mitkompiliert.
- Testanschluss: nur der zusätzliche unbenutzte `expectedComposeField`-Portparameter; Fälle und Gates unverändert.
- Tatsächlicher Lauf: Kompilierung PASS, drei Experimentmethoden / 36 Abläufe PASS, 46 aktuelle Nachbarmethoden PASS; zusammen 49/49.
- Alle Messwerte bytegleich zum Erstlauf: 36 Eingaben, 16 Erfolge, null frühe Erfolge; 20 Negativkontrollen ohne Erfolg.
- 27 eingefrorene Dateien vor/nach dem Lauf identisch und commitgebunden; Produktionsdateien bytegleich zur Basis `711f23b`.

Neue Belege separat unter `measured-711f/`; historische 47/47-Belege und Vorregistrierung unverändert.
CLI mit `--baseline 711f23b33a8fc602a3a0e4baa37aa61e42e6fc64` in `RESULT.md`.
512 MB, ein Prozessor, eigenes Tempverzeichnis, kein Gradle. Kein zusätzlicher Produktcode.
Keine vollständige Audit-#803-, Android- oder Fold7-Abnahme durch diesen Hostlauf behauptet.

## Belege und Einstieg

`docs/evidence/astra55-experiment-c-workflow-20261112/EXPERIMENT.md`: unveränderte Vorregistrierung.
`sources.json`: zuerst geprüfte Primärquellen, Daten und Grenzen.
`RESULT.md`: Messwerte, genaue CLI, Entscheidung und Grenzen.
`measured/`: öffentliche Messdaten, Logs, Tool-/Quellhashes und Quellenbindung.
`measured-711f/`: getrennter Nachlauf gegen den übernommenen aktuellen Auditstand.
`tools/astra55-workflow-experiment/run.py --help`: Reproduktion ohne Gradle; Output in eigenem Scratch.
Rohlogs bleiben unverändert lokal; öffentliche Fassungen verwenden dokumentierte Pfadplatzhalter.

Issue #41 wurde vom Koordinator aktuell direkt über GitHub gelesen (updated_at 02.11.2026)
und die Vorgaben weitergegeben; eigener Webabruf lieferte DisabledError. Keine Projektblockade abgeleitet.
Android-/Monitorpixel-/GPU-/APK-/Fold7-Abnahme: NOT_RUN. Rücknahme: Test-/Harnesscommit.
Veröffentlichung und gemeinsame Integration übernimmt der Koordinator; keine eigene Remote-Mutation.
