# Code55 Experiment C: beobachtete Aktionswirkung

MOCK_TESTED / abgeschlossen. Basis `5f87bdc610944b93d3173a83c5beeea315abeb66`.
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
## Ergebnis

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

## Belege und Einstieg

`docs/evidence/astra55-experiment-c-workflow-20261112/EXPERIMENT.md`: unveränderte Vorregistrierung.
`sources.json`: zuerst geprüfte Primärquellen, Daten und Grenzen.
`RESULT.md`: Messwerte, genaue CLI, Entscheidung und Grenzen.
`measured/`: öffentliche Messdaten, Logs, Tool-/Quellhashes und Quellenbindung.
`tools/astra55-workflow-experiment/run.py --help`: Reproduktion ohne Gradle; Output in eigenem Scratch.
Rohlogs bleiben unverändert lokal; öffentliche Fassungen verwenden dokumentierte Pfadplatzhalter.

Issue #41 wurde vom Koordinator aktuell direkt über GitHub gelesen (updated_at 02.11.2026)
und die Vorgaben weitergegeben; eigener Webabruf lieferte DisabledError. Keine Projektblockade abgeleitet.
Android-/Monitorpixel-/GPU-/APK-/Fold7-Abnahme: NOT_RUN. Rücknahme: Test-/Harnesscommit.
Veröffentlichung und gemeinsame Integration übernimmt der Koordinator; keine eigene Remote-Mutation.
