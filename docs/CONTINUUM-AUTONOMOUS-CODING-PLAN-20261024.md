# Autonomous Coding V1 – ausführbarer Teilumfang

Basis: `aa8780a8489e9e972e9a35a5af9e22866a0ea6e2`, isolierter Branch
`sol/continuum-autonomous-coding-20261024`. Auftrag vom 24.10.2026 ist die
Ausführungsfreigabe für Entwicklung und Tests, nicht für Release/Installation.

## Nachgewiesene Ausgangslage

1. **Architektur:** CCC1 und Canonical Backlog sind vorhanden. Der bestehende
   `repairSession(candidateProvider)`-Anschluss wird vom Teamleader nicht
   weitergereicht. `LOCAL_MODEL_PATCH` erlaubt bewusst nur reine numerische
   JS-Module; kein Beleg für generative Modellprogrammierung.
2. **Laufzeit:** Vorlagendialog, `code-test` und CCC1-REPAIR sind unterschiedliche
   bestehende Wege. CCC1 ruft über mobilen Worker den Teamleader und dieselbe
   Repair-Pipeline mit zweitem Worktree auf. Ein grüner Baseline-Lauf beendet
   REPAIR; neue Features benötigen vorab festgelegte Akzeptanztests.
3. **Sicherheit:** Ein Worktree ist keine OS-Sandbox. Nicht-null Executor wird
   von `runProfile` abgewiesen; der Owner-Worker nimmt ihn bisher nicht an.
   Deshalb keine Freigabe beliebigen Modellcodes. Bestehende reine Ausdrucks-
   grammatik bleibt Grenze, bis echte Isolation nachgewiesen ist.

EXP-02/05/07/08/11/12 betroffen. Ein Backlog, eine Repair-Pipeline, keine
Modellgewichtsänderung. Haupt-App `at.heinz.deviceai.mentorpaired`, 0.3.21/22
und Signiermaterial bleiben unverändert. Referenz PR123/124 ist enthalten;
historische Branches bleiben erhalten.

## Implementierung und Prüfung

- [ ] Auftragsvertrag für konkrete Ziele, Arten, Commit, erlaubte Dateien,
  feste Testkette, Akzeptanzkriterien und Ressourcenlimits; unvollständig =
  Rückfrage, keine erfundene Freigabe. Erweiterten Pfad explizit opt-in halten.
- [ ] Lesendes, commitgebundenes Retrieval mit Inventar, Symbolen, Abhängigkeiten,
  Bereichssuche und hartem Kontextbudget; bestehende Kotlin-Analyse wiederverwenden.
  Negativtests für Symlinks, Quellwechsel und Pfadausbruch.
- [ ] Bestehenden lokalen Loopback-Adapter und Repair-Pipeline um begrenzte
  Revisionen anhand echter Diagnosen erweitern. Patch-Fingerabdrücke verhindern
  Wiederholung. Modelltext kann keine Kommandos oder Rechte setzen.
- [ ] Typisierte Mehrdateien-Kandidaten für ausgewählte reine Module durch
  denselben Kritiker, echte Tests und unabhängigen zweiten Worktree führen.
  Nicht unterstützte Codeformen werden explizit blockiert.
- [ ] CCC1/Teamleader-Anschluss schließen; Task/Commit/Dateigrants vor jedem
  Modellaufruf und Patch prüfen. Executor-Option niemals still ignorieren.
- [ ] Vorhandene Android-Entwicklungsoberfläche um allgemeinen begrenzten
  Auftragsentwurf erweitern; vorhandene Termux-Brücke beibehalten. Keine
  nicht nachgewiesene Ausführung oder APK-Installation anzeigen.
- [ ] Echte lokale GGUF-Inferenz auf Host versuchen; Modellhash, Prompt,
  Rohantwort, Patch, Baseline/Kandidat/unabhängige Tests und Ressourcen sichern.
  Mocks beweisen nur Infrastruktur. Kein Android-Nachweis aus Hostläufen.
- [ ] Neue gezielte Tests zuerst rot, dann grün; gesamte Node-/Backend-Suite,
  verfügbare Kotlin-/Android-Prüfung; unabhängiger Sicherheitsreview.

## Rückfall und Grenzen

Jeder abgeschlossene Schritt wird separat committed. Ohne Opt-in bleibt der
bisherige Pfad aktiv. Jeder Commit ist revertierbar; kein main-Merge, Push auf
historische Zweige, Release oder Zugriff auf persönliche Daten. Arbiträre
Projektbearbeitung, on-device-Modellqualität, kontinuierlicher Android-
Hintergrundbetrieb und neuronales Lernen gelten erst nach eigenen Belegen.
