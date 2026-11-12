# Code55: drei angeschlossene Cockpit-Experimente

Start: 12.11.2026. Status: RESEARCH_SHADOW / WIP, noch kein Ergebnis.
Gemeinsame unveränderte Basis: `5f87bdc610944b93d3173a83c5beeea315abeb66`,
Baum `cae47c3abaee1a378b4157f26abf7e6ec33df2c1` aus PR #799.
Der lokale Zwischenstand `140c59ee` ist nicht die Experimentbasis.

## Eine Integrationslinie

Die drei abgegrenzten Zulieferungen laufen in
`work/astra55-cockpit-experiments-20261112` zusammen und zielen auf
`work/astra55-cockpit-integration-20261112` (#799), danach die bestehende #791.
Der Hauptintegrator behält Gesamtbuild und Geräteabnahme. Der parallele
Android-Anschlussaudit #803 bleibt zuständig für Capture, Eingabefreigabe,
eingereichtes Monitorbild und tatsächliche App-Nachbedingungen.

Dies sind Unterprüfungen der vorhandenen EXP-07/11/12; die bestehende
EXP-05-Modellarchitektur wird nur als vorhandene Schnittstelle erhalten.
Es entsteht kein zweiter Renderer, Ereignisbus, Planer oder Modelltrainer.

## Drei vorab festgelegte Entscheidungen

| Spur | Bestehender Anschluss | Vergleich und Annahmebedingung |
|---|---|---|
| A / support_live | LiveMonitorFeed → CockpitLiveTexture → gemeinsamer Monitor | Unveränderte Uniformwerte überspringen. Mindestens 50 % weniger tatsächliche Material-Schreibaufrufe im eingefrorenen 60-Tick/10-Frame-Replay; Zustand, Framebindung und Buffer-Lebensdauer identisch. |
| B / support_scene | FilamentCockpitStage → vorhandener TransformManager | Identische lokale Matrizen überspringen. Mindestens 20 % weniger Transform-Schreibaufrufe im dokumentierten Replay; lokale und Weltmatrizen einschließlich Elternbewegung identisch. |
| C / support_workflow | Auftrag → Verifier → vorhandener Bus → Avatar/Cue | Sofortige und verzögerte Beobachtung vergleichen. Kein verfrühter Erfolg, keine zusätzliche native Eingabe; Widerspruch und Stop müssen Erfolg verhindern. Besteht der Bestand, wird ausschließlich der Nachweis angeschlossen. |

A und B sind isolierte, deaktivierbare A/B-Kandidaten. Nur bei bestandenen
vorab festgelegten Gates wird der kleine Produktionsdiff übernommen. Ein
negatives Ergebnis bleibt mit Ursache dokumentiert; keine Ersatz-Neuentwicklung.
Aufrufbudgets auf dem Host beweisen weder GPU-Latenz noch Geräte-FPS oder Thermik.

## Dateibesitz und Übergaben

- A: `avatar3d/CockpitLiveTexture.kt`, ausschließlich Materialzustandsupdates,
  dazu eigene Tests/Harness und Belege. Capture-/Dispatchlogik bleibt bei #803.
- B: Transform-Update-Lane in `avatar3d/FilamentCockpitStage.kt`, kleiner
  Transform-Helfer und eigene Tests/Harness. Assets, Clock und Layout unverändert.
- C: neuer integrierter Workflow-Experimenttest, Runner und Belege.
  Eine Produktionskorrektur braucht zuerst ein reproduziertes Gegenbeispiel.
- Koordinator: Quellenabgleich, gemeinsames Register, konfliktfreie Übernahme,
  gemeinsame gezielte Prüfung und öffentliche Übergabe an #799/#803.

Jede Spur recherchiert zuerst offizielle Primärquellen und prüft danach die
tatsächlich verwendete Filament-1.56.0-/Android-API. Webquellen sind keine
Ermächtigung für Aktionen und kein Beleg, dass die App bereits funktioniert.
Alle schweren Builds verwenden die bestehende gemeinsame Sperre; direkte
kleine Compilerläufe bleiben begrenzt. Keine fremden Arbeitsbäume bearbeiten.

## Nachweise, nächster Schritt, Rücknahme

Startcheckpoint: Quellen/Verträge/Issue #41 und aktuelle PRs gelesen;
Tests für diese Experimente: NOT_RUN. Produktion noch unverändert.
Als Nächstes: Baseline und Kandidat mit derselben Eingabefolge ausführen,
negative Kontrollen dokumentieren, tatsächliche Quellhashes prüfen und
akzeptierte Effekte gemeinsam an #799 übergeben. Vor Übernahme #803 abgleichen.
Rücknahme: Kandidaten abschalten oder ihren jeweiligen kleinen Commit
zurücknehmen; vorhandene Funktionen und Originalassets bleiben verfügbar.
PORTIERT: noch nein. VERIFIZIERT: noch nein. GERÄTEBESTÄTIGT: NOT_RUN.
