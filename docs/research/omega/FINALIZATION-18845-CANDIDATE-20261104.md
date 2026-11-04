# Gemeinsamer Fertigstellungsstand – 4. November 2026

Status: **INTEGRIERT, GEMEINSAME ABNAHME AUSSTEHEND**. Dieser Stand wird in PR #517 geprüft. Der Referenzstand vor diesen Änderungen ist `46e5fa6b09454ad6f6862e80478eb0f2d5545149`; dessen Produktionsquellen entsprechen `20f888660a967aa1d6b5446e892540538372d319`.

## Übernommene, unabhängig geprüfte Abschlüsse

- #513, `28b228698aef8c52e46c72b71fdd5e1915d6e76c`: 21 Avatar-, Sprach-, Test- und Hostwerkzeugdateien sowie drei zugehörige Herkunftsbelege. Die dort berichteten 352 Tests sind Upstream-Belege; sie ersetzen keine Prüfung dieses gemeinsamen Standes.
- #515, `572e12ecc4ce5f7c79f2634a5b5773047b6d674f`: genau vier ECHO-Dateien für die nachvollziehbare negative Rückmeldung bei fehlendem lokalen Modellnachweis. Die produktiven Autoritätsgrenzen bleiben erhalten.
- Die fehlende Mentor-Base64-Variable im bestehenden Ausschluss von Signiermaterial für lokale QA wurde ergänzt. Die Produktionskorrektur entspricht dem bereits abgeschlossenen Teil von #505; der zusätzliche Test deckt alle sieben Variablen in beiden QA-Modi ab.

## Zusätzlich nachgewiesene und behobene Fehler

- `AvatarIdleMotion.update(-0.0f)` konnte vor dem ersten Stützpunkt indizieren. Eine Normalisierung des Null-Restwerts behebt diesen Randwert. Der identische neue JVM-Test wurde vor der Korrektur rot und danach grün ausgeführt.
- Nach einem tatsächlichen Fehler der optionalen 3D-Darstellung zeichnet die bestehende Ansicht wieder den vorgesehenen 2D-Avatar. Fehlerbehandlung, Ressourcenfreigabe und gemeinsame Animationsuhr bleiben erhalten.
- Drei bestehende Robolectric-UI-Prüfungen steuern ihre simulierte Bildschirmuhr explizit mit pausiertem VSync und 16-ms-Takten. Dies behebt den diagnostizierten rekursiven Testuhr-Lauf. Die Produktionsplanung und die bestehenden Prüfbehauptungen werden nicht geändert; die Ausführung im gemeinsamen Stand steht aus.
- Bestehende Node-Vertragsprüfungen wurden an die unabhängig geprüften aktuellen Navigations-, Layout-, Sprach- und Speicheranschlüsse angepasst. Negative Mutationskontrollen wurden separat durchgeführt. Synthetische Speicher-Fixtures bleiben Unit-Test-Daten; sie gelten nicht als Modellinferenz.

## Verbleibende Abnahme

Die drei Fertigstellungsbots prüfen diesen eingefrorenen gemeinsamen Stand auf Build/UI/Lint, Node- und Werkzeugregressionen sowie Integrations- und Herkunftskonsistenz. Rohbefunde des vorherigen roten Gesamtstands bleiben unverändert erhalten. Echte Fehlschläge, übersprungene Tests und ausgeschlossene historische oder umgebungsabhängige Prüfsätze werden getrennt ausgewiesen.

Ein erfolgreicher Quelltest belegt weder einen gebauten APK noch eine erfolgreiche Installation oder Geräteabnahme. Originalsignatur, Updatefähigkeit, reale Fold7-Grafik, Sprache und lokale Modellinferenz benötigen ihre tatsächlichen Nachweise. Der Offline-Schalter und die Regeln für Kernreparaturen bleiben bestehen. Die laufenden unfertigen Arbeiten anderer PRs sind kein Bestandteil dieses eingefrorenen Kandidaten.

Die genaue Liste der Eingangsdateien und Patch-Hashes liegt in `docs/evidence/finalization-18845-20261104/combined-integration-inputs.json`.
