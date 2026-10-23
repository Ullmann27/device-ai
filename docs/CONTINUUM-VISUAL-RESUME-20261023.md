# Continuum Grafik: gesicherter Einstieg 23.10.2026

Eigener Branch `astra/continuum-visual-engineering-20261023`, Basis PR #81
`93cd4c08bc961e07ea0dc2834e5b9b4d38a41195`. PR #80 ist darin bereits enthalten.
PR #75/#82, main, Besitzerdateien, Gewichte und Checkpoints bleiben unberührt.
Aktuellen gesicherten SHA mit `git rev-parse HEAD` lesen; keine fremden Worktrees ändern.

## Meilenstein 1: reproduzierbare und ehrliche visuelle Abnahme

- Design-Diff trennt grobe Suche und Vollauflösung, verweigert stillen Zuschnitt,
  prüft Referenzhash/Geometrie und erlaubt feste Baseline-Ausrichtung.
- Zusätzliche Lichtmasken-, Verteilungs- und Kantenabstandsmetriken.
- Screenshot-Workflow baut ausschließlich Feature-Preview, prüft Paket und
  Signatur und scheitert bei fehlgeschlagenen Pflichtprüfungen.
- Emulator-Skript verweigert physische Geräte und Haupt-App-Pakete; Navigation
  wird anhand tatsächlicher Zielansichten geprüft. 100/130 % Schriftprofile.
- Konkrete Details, Befunde, Tests und Rollback:
  `CONTINUUM-VISUAL-QA-20261023.md`, `CONTINUUM-UI-EMULATOR-QA-20261023.md`.
- Actions Run 39852367733 startete wegen GitHub-Zahlungs-/Ausgabenlimit nicht.
  Keine Kontoeinstellungen geändert. Lokale Toolchain ist verfügbar.

Experimentbezug: bestehende Präsentation/QA (EXP-00/01/02/04/05/06/07/08/11/12),
keine Änderung an Lernalgorithmen, Datenformaten oder Sicherheitsfreigaben.

## Nächster konkreter Schritt

Hologramm mit festem READY-Zustand und Phase 0.4 gegen die gesicherte PR81-Baseline
verbessern. Hauptbefund: voneinander getrennte Großkreise und 2D-Partikel ergeben
kein zusammenhängendes räumliches Netz. Begrenzte 3D-Nachbarschaften entwickeln,
gegen feste Referenzpositionen messen und erst nach Messung/Regression sichern.
Android-Kompilierung, JVM-Tests, Lint, APK-Identität/Signatur anschließend erneut
am endgültigen Stand prüfen. Emulator-/Samsung-Abnahme und Framezeiten sind offen.
Die ChatGPT-Nutzungsquote ist hier nicht auslesbar; keine Prozentangabe geschätzt.
