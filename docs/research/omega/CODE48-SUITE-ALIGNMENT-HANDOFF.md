# Code48: zwei veraltete UI-Beobachtungspunkte

Status: WIP / fokussiertes GREEN und neue Gesamtabnahme noch offen.
Basis `c97f810b33061748b8600ea3b31ba011730e9614`; Produktionsquellen bytegleich
mit `5d91442e98f78a2af623b8a3c74920805f670cb9`.

Die vollständige 5d-Suite endete nach 32m36s mit 3.776 Tests, davon 3.754 PASS,
20 SKIP und zwei FAIL. Der fokussierte unveränderte Wiederholungslauf lieferte
ebenfalls beide roten Assertions (drei Tests insgesamt). Die 20 Skips sind
ausdrücklich dokumentiert; zwölf sind historische ausgeschaltete PENDING-
Prüfspezifikationen und keine erfolgreich ausgeführten Tests.

## Ursache und begrenzter Patch

- `semanticMenuDestinationsOpenTheirNamedScreensWithoutStartingATurn`
  erwartete für alle Ziele einen AlertDialog. `showCenter` öffnet im sichtbaren
  START-Cockpit bereits ausdrücklich ein `JarvisWorkWindow` für Logic/Web.
  Der Test verlangt dafür jetzt genau ein angehängtes, gemessenes sichtbares
  Arbeitsfenster, Panel-Opazität, exakten Titel und tatsächlichen Close-Button.
  Tasks verlangt weiterhin ausdrücklich seinen neuen echten AlertDialog.
  Kein Request und kein Chief-Turn darf beim Öffnen oder Schließen starten.
  Der vorhandene `CockpitHostFramePump` konsumiert echte Draw-Anforderungen;
  keine Produktionsuhr oder Callbackquelle wird ersetzt.
- `MemoryEventMapperStopUiTest` zeichnete ausschließlich die alte kleine
  `JarvisSequenceView`, nachdem die übrigen Effekte bewusst gelöscht wurden.
  Der positive Recall benutzt inzwischen die Home-Vollfläche. Beide Tests
  zeichnen nun die wirklich angehängte/sichtbare/gemessene Fullstage-View.
  Exakte Event-/Generations- und Pixelassertions bleiben; zusätzlich werden
  inaktiver STOP-Zustand, aktiver frischer Recall und dessen Count 3 verlangt.

Keine Produktionsdatei, Paketkennung, Renderergeometrie, Gewichte, Daten,
Qualitätsgrenze oder Freigaberegel geändert. Keine alternative „Dialog oder
irgendein View“-Assertion und keine Lockerung der vorhandenen Pixelprüfung.

## Reproduktion und Belege

[Vollständige Belege](../../evidence/code48-suite-alignment-20261107/)
enthalten den ursprünglichen Gesamtlauf, alle 664 XMLs im ZIP, ausgewiesene
Skipgrenzen, Rohlogs/Receipts und das fokussierte RED.

Der erste `--tests`-CLI-Versuch wurde vor Ausführung durch die bestehende
Disposable-QA-Taskliste abgewiesen: Gradle führt die Selektorargumente dort
unter `taskNames`. Dieses Ergebnis bleibt CONFIGURATION_REJECTED, kein
Assertion-RED. Der folgende feste externe Init-Filter fordert ausschließlich
die bereits erlaubte Aufgabe `:app:testDebugUnitTest` an und selektiert genau
die drei benannten Testmethoden. Die Build-/Signiergrenzen bleiben unverändert.
Exakte Befehle und Quellzustände stehen in `red-filter-5d/receipt.json`;
`run-focused.py` und `focused-tests.init.gradle` sind beigefügt.

Nächster Einstieg: die zwei genannten Testdateien. Nach fokussiertem GREEN
den tatsächlichen neuen Remote-SHA für vollständige Node-/Backend-/Android-
und Lintprüfung sowie anschließenden unsigned Build binden. Kein alter PASS
oder Belegcommit ersetzt diese neue Prüfung. EXP-02/07/11/12-Verträge erhalten.

Rollback: ausschließlich diese zwei Testanpassungen per normalem Revert;
Produktionsfunktionen bleiben unverändert. Der alte Full-Gate-FAIL bleibt
auch dann dokumentiert. Kein Reset gemeinsamer Branches und kein Main-Merge.
