# Device AI Continuum – detaillierter Samsung-Testplan SWB1 und CDX1
Stand: 20. Oktober 2026. Branch `astra/continuum-experiment-charter-20261019`; Draft PR #42.

## Architektur

| Lernweg | Zweck | Durch Nutzer bereits geprüfter Zustand |
| --- | --- | --- |
| SWB1 | 4.113 Parameter, einfache Intent-/Semantik-Klassifikation und bestätigte Owner-Muster | Vorheriger Holdout 4/8 → 5/8 durch bestätigtes Beispiel; ältere Großstufe 2/12 verworfen |
| CDX1 | 7.746 Parameter, getrennte zeichenweise konditionierte Forschungsantworten | Bislang 120 Updates und ein eigener Paarlabor-Checkpoint; nicht für produktiven Chat freigegeben |
| Öffentliche Forschung | Wikipedia-Quellen abrufen, neue verwandte Thementitel entdecken, Archiveinträge mit URL/Hash speichern | JobScheduler wurde auf dem Gerät aktiviert, erste abgeschlossene Archivierung noch nicht bestätigt |
| Qwen3-0.6B | Vollständige lokale Textgenerierung, separat von SWB1/CDX1 | Letzter beobachteter Fehler: `Local generation cancelled`; kein Freigabenachweis |

## Neu im Experimentbranch: CDX1 im Großformat

`ContinuumCdxLargeCurriculum.kt` liefert 576 deterministisch erzeugte,
rein synthetische `Beleg → Frage → kurze Antwort`-Beispiele:
48 fiktive Roboter mit jeweils 12 Fragestrukturen zu Stadt, Farbe,
Energiequelle und Werkzeug. Keine daraus abgeleitete Tatsachenbehauptung.

- 12 Stufen mit je 48 Trainingspaaren.
- Je Stufe bis 600 **CDX1**-Gewichtsupdates (5-Sekunden-Zeitbudget für das
  reine Training) und getrennte Qualitätsprüfung.
- Eingefroren: 48 neue Beleg-/Frage-Auditbeispiele von vier bislang
  ungesehenen Robotern.
- Zusätzliche ältere Schutzgruppe: 2 Auditfälle aus dem bisherigen
  App-Labor. Keine Schutzgruppe wird für Gradienten benutzt.
- Neues Kandidatenmodell wird nur bei Verbesserung des Trainingsverlusts,
  keiner Verschlechterung des neuen Audits **und** keiner
  Verschlechterung der historischen Schutzgruppe aktiviert.
- CDX1 speichert weiter im **eigenen** `context-decoder-paired.cdx`;
  `.lkg` dient als vorheriger Checkpoint.
- Das experimentelle Trainingsskript markiert nur abgeschlossene
  Stufen. Ein unvollständiger Versuch bleibt wiederholbar.
- Die Auswahl der Gradientenfälle verteilt die Updates über die 48
  Trainingspaare, statt ausschließlich die ersten langen Antworten
  zu lernen.
- UI: `LERNEN → Experimente → CDX1 · GROSSES PAARARCHIV · 12 STUFEN`.
- Zusätzlicher UI-Test: `LERNEN → Experimente →
  CDX1 · EIGENE TESTFRAGE (OHNE TRAINING)`.
  Die Ausgabe wird neben einer optionalen erwarteten Kurzantwort
  dargestellt, ohne irgendetwas zu trainieren.

Diese Verfahren **trainieren nicht** das separate große Qwen-Sprachmodell
und erlauben keine automatische Freigabe von CDX1 als produktive
Chat-KI. Synthetische Tests beweisen keine Generalisierung auf
natürliche Alltagsfragen.

## Bedienplan am Samsung

### A. Vor dem nächsten APK-Update

1. Nicht deinstallieren. Wenn noch Gradle-Ausgaben in Termux laufen:
   abwarten, bis ein eindeutiger Build-Erfolg oder -Fehler vorliegt.
2. Device AI öffnen.
3. Auf `KI personalisieren` tippen.
4. Auf `Lernen` tippen.
5. `LERNKERN · DETAILS` antippen, aktiven SWB1-Hash, Updates und
   Holdouts fotografieren.
6. Im Lernzentrum `SWB1 · EIGENE GEWICHTE EXPORTIEREN` tippen,
   Android-Speicherort auswählen und im Downloadordner speichern.
7. `WISSENSARCHIV · BACKUP / WIEDERHERSTELLEN` →
   `BACKUP EXPORTIEREN` → ZIP im Downloadordner speichern.
8. Erst danach den neuen Build installieren.

### B. Kontrollierter Build und Chrome-Download

1. Termux öffnen. Falls der alte Downloadserver aktiv ist:
   `CTRL+C` nur dann drücken, wenn dort der lokale Downloadserver
   und nicht noch Gradle läuft.
2. Ins Verzeichnis wechseln: `cd "$HOME/device-ai"`.
3. Den neuen Branchstand mit bekanntem read-only SSH-Schlüssel holen:
   `GIT_SSH_COMMAND="ssh -i $HOME/.ssh/continuum_device_ai_readonly -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o BatchMode=yes" git pull --ff-only`.
4. Danach `bash scripts/continuum-one-tap-update.sh` ausführen.
   Das führt alle Node-Tests und anschließend den offiziellen
   Termux-Android-Build mit `testDebugUnitTest`, `lintDebug`,
   `assembleDebug` und Signatur-/Quellprüfung durch.
5. Nur nach vollständigem Erfolg in Chrome auf **demselben Samsung**
   `http://127.0.0.1:8765/` öffnen.
6. Die neu erzeugte APK herunterladen und **als Update** installieren.
   Bei Signaturkonflikt *nicht* die alte App deinstallieren:
   Abbruch und genaue Fehlermeldung erfassen.

### C. SWB1 und CDX1 unabhängig prüfen

1. App starten, `KI personalisieren` → `Lernen`.
2. `LERNKERN · DETAILS` → Ausgangswerte mit dem Screenshot vor Update
   vergleichen. Bereits erlernte Owner-Fakten und bestätigte Beispiele
   sollen weiterhin vorhanden sein.
3. Im selben Lernzentrum zu **Experimente** scrollen.
4. `CDX1 · BELEG + FRAGE → ANTWORT (LABOR)` öffnen.
   Prüfen: alter Paarlabor-Checkpoint noch vorhanden und Anzahl
   der CDX1-Updates nicht auf 0 zurückgesetzt.
5. **Nicht** den alten Knopf `EXPERIMENT STARTEN` mehrfach benutzen –
   er trainiert nur das Mini-Paket mit vier Fällen.
6. `GROSSES ARCHIV` wählen (oder Experimente →
   `CDX1 · GROSSES PAARARCHIV · 12 STUFEN`).
7. `NÄCHSTE STUFE PRÜFEN` **genau einmal** drücken. Vor Abschluss
   keinen weiteren Auftrag starten.
8. Bericht fotografieren: Stufe, tatsächliche Updates, Status
   `KANDIDAT ÜBERNOMMEN` oder `KANDIDAT VERWORFEN`, Trainingsverlust,
   unabhängiger neuer Audit, historischer Audit, Modell-Hash.
9. `CDX1 · EIGENE TESTFRAGE (OHNE TRAINING)` öffnen.
10. Feld **Beleg**: `Roboter Z77 steht in Graz.`
11. Feld **Frage**: `Wo steht Z77?`
12. Feld **Erwartete Kurzantwort (optional)**: `Graz`
13. `ANTWORT TESTEN` drücken. Vorhergesagten Text und Vergleich
    fotografieren. *Falsch* bedeutet: Der kleine Decoder hat diesen
    unbekannten Input noch nicht verlässlich gelernt; keine Fakten
    in die Owner-Erinnerung übernehmen.
14. Erst danach den **SWB1**-Bereich `GROSSES ÜBUNGSARCHIV ·
    NÄCHSTE STUFE` öffnen und maximal eine weitere Stufe prüfen.
15. Die detaillierten SWB1-Holdoutberichte (welche Tests sich
    verbessert/verschlechtert haben) fotografieren.
16. `AUTONOME INTERNETFORSCHUNG · EIN/AUS` → `PLANUNG PRÜFEN`.
    Job sollte als registriert erscheinen; Themenwarteschlange,
    Quellenzähler, Versuche und Wartegrund dokumentieren.
17. Beim Laden WLAN oder mobile Daten eingeschaltet lassen.
    Android darf Jobs verschieben. Der Forschungsjob trainiert **CDX1
    nicht heimlich**. Seine Hauptaufgabe bleibt das Quellenarchiv.

### D. Drei eigenständige Kontrollfragen

CDX1: drei Fragebeispiele aus eigener Eingabe, jeweils einzeln über
`EIGENE TESTFRAGE (OHNE TRAINING)`:

| Beleg | Frage | Erwartete Ausgabe |
| --- | --- | --- |
| Roboter Z77 steht in Graz. | Wo steht Z77? | Graz |
| Der Roboter X15 hat die Farbe blau. | Welche Farbe hat X15? | blau |
| Der Roboter W80 hält einen Hammer. | Welches Werkzeug hält W80? | Hammer |

Diese synthetischen Prüfungen sind **manuell ausgewählte Smoke-Tests**;
sie ersetzen weder die eingefrorene automatische Auditgruppe
noch echte externe Evaluation.

SWB1: fünf eigene Gruß- und fünf Frageformulierungen im normalen
LERN-GESPRÄCH nacheinander bestätigen, **keine** privaten Fakten
in die öffentlichen Testdatensätze mischen.

## Qualitäts- und Verfügbarkeitsgrenzen

- Die Änderungen sind bisher Quellcode im Draft-Branch.
  Vollständige Gradle-/Android-/Samsung-Tests sind offen.
- Ein vorhandener CDX1-Checkpoint darf für Tests weiterbenutzt werden;
  neue Paare werden nicht über SWB1-Gewichtspakete importiert.
- Bei fehlender Übereinstimmung oder schlechterem unabhängigen Audit
  wird der neue Kandidat verworfen. Stufen nicht blind wiederholen.
- Ein Android-APK-Update braucht **keine** Deinstallation.
