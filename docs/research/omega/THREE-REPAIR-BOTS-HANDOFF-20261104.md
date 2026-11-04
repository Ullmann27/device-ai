# Drei Reparatur-Bots — Continuum — 04.11.2026

## Auftrag und eingefrorene Basis

Heinz beauftragt drei Bots, vorhandene Continuum-Funktionen anzusehen und nachgewiesene Fehler zu reparieren. Gemeinsame Prüfgrundlage ist PR #491, `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`, Baum `72e62267ba22309d60ad13abb3e2a5c6a155e544`. Dieser Reparaturzweig ist auf #491 gestapelt; keine Zusammenführung nach main und kein Release.

## Laufende Aufgaben

1. `repair_app_integration`: App-/UI-Ereignisse und vorhandene Anschlüsse; getrennte Quelle und Regression.
2. `repair_brain_memory`: lokale Dialoglogik, Gedächtnis und Kontext; Abgleich mit #494/#499 vor Änderung.
3. `repair_build_update`: Build-/Test-/Update-Verträge; aktuelle Quellen statt veralteter Buildnummer-Meldungen.

Die drei Unteragenten wurden in dieser Sitzung tatsächlich gestartet. Das ist keine Behauptung über fremde Chat-Agenten oder permanent laufende Geräteworker. Alle Änderungen werden auf eigenen lokalen Zweigen geprüft und nur nach Review in diesem Draft veröffentlicht.

## Parallelstände schützen

Gelesene Remote-Heads: #493 `ee2fa0ee5567e5d34bcdfe229425a71df9818183`, #494 `93f1f1130a3963062ad4c43c4a1edfe0cf7e2759`, #495 `6cf6e2128463e6a24dfac4652bb65e325ff6f18a`, #496 `3968d785df607f8d461162c50bb3d710a925503e`, #497 `06f071e5850dd405f374bbb58d826f64740d83af`, #503 `2582be86c121d7f6d1179c7edc49e979bee8ba9c`. Vor Übernahme erneut prüfen. Aktive fremde Worktrees, insbesondere die Avatar-/Kamera-Arbeit, werden nicht geändert.

## Verifikation und Grenzen

Startcheckpoint: `WIP / UNVERIFIED`. Noch kein neuer Reparaturtest, vollständiger Android-Build oder Fold7-Test abgeschlossen. Historische Testzahlen anderer PRs gelten nicht als neuer Testlauf.

Keine Modellgewichte, Paketidentität, Signatur, Versionsnummer, Internetfreigabe, ToolGate-/CCC1-/Owner-Grenzen oder Produktionszweige ändern. Keine kostenpflichtigen Dienste oder GitHub Actions starten. Relevante bestehende Verträge: EXP-00/05/06/07/08/11/12; kein neues Forschungsmodul oder zweites Backlog.

## Nächster Einstieg und Rücknahme

Je Bereich zuerst reproduzierbaren Befund und kleinstmöglichen Test festhalten, dann gezielte Reparatur und unabhängiges Review. Belege mit Quellidentität und tatsächlichen Exitcodes ergänzen. Einzelne Reparaturcommits können separat zurückgenommen werden; persönliche Daten und Persistenzformate sollen unverändert bleiben.
