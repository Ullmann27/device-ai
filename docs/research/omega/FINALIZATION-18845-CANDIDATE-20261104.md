# Gemeinsamer Fertigstellungsstand – 4. November 2026

Status: **INTEGRIERT, GEMEINSAME ABNAHME AUSSTEHEND**. Dieser Stand wird in PR #517 geprüft. Der Referenzstand vor diesen Änderungen ist `46e5fa6b09454ad6f6862e80478eb0f2d5545149`; dessen Produktionsquellen entsprechen `20f888660a967aa1d6b5446e892540538372d319`.

## Übernommene, unabhängig geprüfte Abschlüsse

- #513, `28b228698aef8c52e46c72b71fdd5e1915d6e76c`: 21 Avatar-, Sprach-, Test- und Hostwerkzeugdateien sowie drei zugehörige Herkunftsbelege. Die dort berichteten 352 Tests sind Upstream-Belege; sie ersetzen keine Prüfung dieses gemeinsamen Standes.
- #515, `572e12ecc4ce5f7c79f2634a5b5773047b6d674f`: genau vier ECHO-Dateien für die nachvollziehbare negative Rückmeldung bei fehlendem lokalen Modellnachweis. Die produktiven Autoritätsgrenzen bleiben erhalten.
- Die fehlende Mentor-Base64-Variable im bestehenden Ausschluss von Signiermaterial für lokale QA wurde ergänzt. Die Produktionskorrektur entspricht dem bereits abgeschlossenen Teil von #505; der zusätzliche Test deckt alle sechs Variablen in beiden QA-Modi ab.

## Zusätzlich nachgewiesene und behobene Fehler

- `AvatarIdleMotion.update(-0.0f)` konnte vor dem ersten Stützpunkt indizieren. Eine Normalisierung des Null-Restwerts behebt diesen Randwert. Der identische neue JVM-Test wurde vor der Korrektur rot und danach grün ausgeführt.
- Nach einem tatsächlichen Fehler der optionalen 3D-Darstellung zeichnet die bestehende Ansicht wieder den vorgesehenen 2D-Avatar. Fehlerbehandlung, Ressourcenfreigabe und gemeinsame Animationsuhr bleiben erhalten.
- Drei bestehende Robolectric-UI-Prüfungen steuern ihre simulierte Bildschirmuhr explizit mit pausiertem VSync und 16-ms-Takten. Dies behebt den diagnostizierten rekursiven Testuhr-Lauf. Die Produktionsplanung und die bestehenden Prüfbehauptungen werden nicht geändert; die Ausführung im gemeinsamen Stand steht aus.
- Bestehende Node-Vertragsprüfungen wurden an die unabhängig geprüften aktuellen Navigations-, Layout-, Sprach- und Speicheranschlüsse angepasst. Negative Mutationskontrollen wurden separat durchgeführt. Synthetische Speicher-Fixtures bleiben Unit-Test-Daten; sie gelten nicht als Modellinferenz.

## Verbleibende Abnahme

Die drei Fertigstellungsbots prüfen diesen eingefrorenen gemeinsamen Stand auf Build/UI/Lint, Node- und Werkzeugregressionen sowie Integrations- und Herkunftskonsistenz. Rohbefunde des vorherigen roten Gesamtstands bleiben unverändert erhalten. Echte Fehlschläge, übersprungene Tests und ausgeschlossene historische oder umgebungsabhängige Prüfsätze werden getrennt ausgewiesen.

Ein erfolgreicher Quelltest belegt weder einen gebauten APK noch eine erfolgreiche Installation oder Geräteabnahme. Originalsignatur, Updatefähigkeit, reale Fold7-Grafik, Sprache und lokale Modellinferenz benötigen ihre tatsächlichen Nachweise. Der Offline-Schalter und die Regeln für Kernreparaturen bleiben bestehen. Die laufenden unfertigen Arbeiten anderer PRs sind kein Bestandteil dieses eingefrorenen Kandidaten.

Die genaue Liste der Eingangsdateien und Patch-Hashes liegt in `docs/evidence/finalization-18845-20261104/combined-integration-inputs.json`.

## Nachträglich fertiggestellte Reparaturen aus #505

Nach Veröffentlichung des ersten Kandidaten `f69cfef3230e7a5a8abcacef0103feb39e325a51` wurde der fertige Quellstand `b2bc510a59e29d4ece478109e12b9785be11ee7d` geprüft. Genau 17 dazugehörige Dateien werden übernommen; ihr Manifest liegt in `docs/evidence/finalization-18845-20261104/round2-505-manifest.json`.

- Relative Tagesfenster lösen Start und Ende getrennt aus dem jeweiligen lokalen Kalenderdatum auf. Eine Mitternachtslücke darf die Uhrzeit benachbarter Tagesgrenzen nicht verschieben. Zwölf neue Unit-Tests prüfen feste UTC-Grenzen, Wiener 23-/25-Stunden-Tage und die tatsächliche zeitliche Suchgewichtung. Rankingformel, Corpus, Schwellen und Bestätigungsstatus bleiben unverändert.
- Tatsächlich empfangene Auftragsabschlüsse werden auch während Motionpause oder Detach in der bestehenden begrenzten Historie vermerkt. Hintergrund-Abschlüsse werden über den Main-Handler unabhängig von einem späteren View-Attach zugestellt. Normale visuelle Ereignisse behalten ihren bisherigen Generationsschutz. Die bestehende UI-Klasse umfasst jetzt 25 Fälle; neu erzeugte Views oder nie zugestellte Ereignisse erhalten dadurch keine erfundene Historie.
- Das zugehörige Sequence-Hostwerkzeug wird vollständig mit seinen originalen Test-Fixtures übernommen. Diese simulieren weder eine Android-Queue noch Grafik oder Gerätebetrieb; ein aufgerufener Host-Handler schlägt ausdrücklich fehl. Der vorhandene Temporal-Runner bindet den neuen Tagesgrenzentest ein.

Der fokussierte Android-Lauf des ersten Kandidaten stoppte vor dem Start der UI-Tests wegen `ENOSPC` beim Kompilieren der Tests. Sein Quellstand blieb unverändert. Der parallel begonnene Node-Lauf wurde nach dem neuen Reparaturbefund kontrolliert beendet und gilt nicht als Gesamtabnahme. Der erweiterte Kandidat benötigt einen neuen, exakt gebundenen Prüflauf.

## Korrektur des tatsächlichen UI-Testaufbaus

Der gezielte Lauf auf `92f421fa81c57022d903e2c1ff45ae6279c77b22` führte alle 53 Fälle aus: 46 bestanden, sieben Avatar-/Hologrammprüfungen scheiterten. Alle 25 Sequence- und zwölf neuen Tagesgrenzenfälle bestanden. Vollständige Rohbelege dieses Standes sind in `checkpoint-92f421f.tar.gz` samt Hashmanifest gesichert.

Die anschließend unabhängig geprüfte Korrektur betrifft ausschließlich drei bestehende UI-Testklassen. Der echte erste Fenster-Traversal und feste LayoutParams werden vor Foreground/Gaze geprüft; pausierte VSync-Zeit wird in maximal 16-ms-Schritten bei gleicher Gesamtdauer bewegt. Orbprüfungen wählen den vorhandenen Orb ausdrücklich aus. Der Sprachtest prüft LIVE vor allen Bildern und spielt seine acht PCM-Samples mit realen 33-ms-Abständen auf derselben Uhr wie Director und View ab. Sämtliche 13 Testmethoden und alle 78 bisherigen Assertion-Aufrufe bleiben erhalten; zusätzliche Vorbedingungen ergeben 100 Assertions. Die Produktionsquellen wurden dabei nicht geändert. Die erneute tatsächliche Ausführung entscheidet über das Ergebnis; aus dem Review wird kein Runtime-PASS abgeleitet.
