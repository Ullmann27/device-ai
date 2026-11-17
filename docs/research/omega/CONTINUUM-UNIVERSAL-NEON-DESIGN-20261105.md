# CONTINUUM – Lebendiges Neon-Design auf allen Arbeitsflächen (Besitzerauftrag 05.11.2026)

**Status: verbindliche Design-/Integrationsanforderung; Umsetzung der gesamten App ist OFFEN, kein Geräte-PASS.**

## Ziel, ohne Interpretation als bloßes Standbild

Heinz möchte die bestätigte cyan-violett-magenta leuchtende Qualität des 3D-Kopfes und die Gesamtwirkung der Referenz `59509.png` im **gesamten** Continuum-Erlebnis: Startseite, Gespräch, Gedächtnis, Forschung, Agenten, Lernen, Einstellungen, Systemstatus, Dokumente, interne Tools und jede tatsächlich angeschlossene externe Anwendung. Kleine Statussymbole erhalten eine sparsame Variante derselben Lichtsignatur; große Hologramm-Arbeitsflächen ein stärkeres räumliches Material. **Kein zweites Designsystem pro App.**

Die Technik ist **übertragbar**, aber **nicht identisch**: Der Kopf benötigt echte GLB-/Filament-Materialien mit Oberflächenlicht/Fresnel; Panels, Buttons und Dokumentflächen verwenden die native 2D-/Glas-/Bloom-Pipeline. Gemeinsame Farb- und Zeitregeln stellen die visuelle Einheit her. Die Oberfläche soll animiert, beweglich und scrollbar bleiben, nicht als Bild hinterlegt werden.

## Bereits vorhandene Quellen – zuerst verwenden, keine Doppelarchitektur

- **Farben/Schrift/Komponenten:** `JarvisDesign.Colors`, `Styles`, `Glow`; `ContinuumTheme`.
- **Neon und Glas:** `JarvisNeon`, `JarvisReferenceHud.surface`, `JarvisShellRenderer.GlassBackend` und vorhandene Android-Implementierung. Keine neue unverbundene Render-Schleife.
- **Gemeinsame Fills:** `JarvisSceneFills` (Shader/Fills wiederverwenden).
- **Hero-Animation aus #535:** `JarvisEnergyFlows` (sieben 3D-anmutende Bézier-Lichtbahnen, Cyan→Blau→Violett→Magenta, 7–13-s-Bewegung), `JarvisHeadAura` (Kopfringe, Randglühen, Lichtimpulse) **erst nach separatem Anschluss-/Test-Slice**; nicht in diese Orb-Teilintegration stillschweigend aufnehmen.
- **Original-Kugel:** `ContinuumBrainOrbRenderer`; bestehende Ereignisquelle `BrainOrbAnimationRenderer` unverändert. Diesen Renderer weiterverwenden, nicht durch Bitmap oder andere Kugel ersetzen.
- **Avatar:** GLB `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`, 21 Morphs, bestehender Director/Filament-Path.

## Konkrete Designregeln für JEDE App-Oberfläche

1. **Einheitliche Palette:** Cyan `#00E5FF`, Violett `#A43BFF`, Magenta `#F04DFF`, Glühkern `#ABF8FF`; dunkle transparente Glasflächen aus dem bestehenden `JarvisDesign` statt frei erfundener Farben je Modul.
2. **Drei Lichtschichten:** feine scharfe Innenkante, weich verbreitertes Farblicht, dezentes äußeres Bloom. Emission und Intensität zeitlich sanft interpolieren; Text/Leseflächen bleiben scharf, nicht mitgeglüht.
3. **Kontinuierliche Ambient-Bewegung:** animierte Farbimpulse auf wichtigen Rändern/Verbindungen, zurückhaltende periodische Farbwechsel und Lichtfluss – abgestuft nach Größe und Bedeutung des Elements. Nicht jedes Icon muss flackern; im Idle keinen falschen `RUNNING`-/Erfolgszustand anzeigen.
4. **Zustandsbindung statt Dekoration:** NORMAL, FOKUS, PRESSED, LOADING, ACTIVE, SUCCESS, WARNING, ERROR erhalten unterscheidbare, reale Ereignisse. Arbeitsvisualisierungen bestätigen erst nach tatsächlichem Tool-/App-Ergebnis.
5. **Öffnung jeder Arbeitsfläche:** App-/Werkzeugkachel leuchtet auf; Kontextfläche erscheint mit sanftem Tiefen-/Einflugübergang; Avatar schaut in Richtung der tatsächlich geöffneten Ansicht und kehrt anschließend in ruhige Bewegung zurück. Gleicher Übergangsvertrag für Browser, WhatsApp, Facebook, TikTok, System-Tools, PDFs und Word. Spezifische Bedienrechte/Einwilligungen respektieren: fremde App-Inhalte nur aus freigegebenen echten Schnittstellen anzeigen, keine gefälschten Chat-Verläufe oder behaupteten Vollzugriffe.
6. **Konsistenter Takt:** alle laufenden Effekte über vorhandene Sichtbarkeits-/Frameuhr, keine unabhängigen Timer. Bei Stopp, Pause, Hintergrund, Power-/Thermalschutz oder reduzierter Bewegung korrekt stoppen oder absenken. Übergänge dürfen nicht die Steuerung blockieren.
7. **Reaktionsreiches 3D-Gesicht:** Originalmodell nicht ersetzen; Idle, Blick, Blink, Mund und Licht nicht als separate Puppenspieler. Für weitere Köpfe gleiche Architektur, eigene geprüfte Modell-/Rig-Identität.
8. **Scroll/Responsive:** Auf Fold7 offen/geschlossen, Telefon und Tablet bleiben Inhalte erreichbar, Mindest-Touchfläche 48 dp, keine Textfeldüberdeckung des Avatars, keine überbelichteten Texte. Karten dürfen in einer Scrollansicht wandern; wichtige Navigation/Status darf bei Bedarf stehen bleiben.

## Präzise Abnahmekriterien

- **Aktueller Zielbild-Vergleich** Host-Szene #535: gemessene Werte gegen 59509 bei vergleichbaren Bildregionen: Helligkeit `0.346` vs `0.351`, Farbigkeit `0.241` vs `0.258`, Neonanteil `9.6 %` vs `12.0 %`. **Nur Host-Werte**, kein Geräteversprechen und nicht eigenmächtig als absolute globale Zielmetrik nutzen.
- **Gemeinsame Interaktionsprobe:** 1. App starten → Avatar/Licht aktiv; 2. eine eigene Funktion öffnen → Einflug und wechselnder Panelzustand; 3. reale Aktion → Fortschritt nur während echter Aktivität; 4. Ergebnis/Fehler → reale Rückmeldung; 5. zurück zum Hauptbild, keine zweite Uhr/Surface.
- **Differenzierung:** Für jede Oberfläche und jeden Subscreen ein sichtbares Bild im Idle/Busy/Error, eine Touch-/Scrollprüfung sowie eine kurze native Aufnahme mit animiertem Farblicht. Fremde Apps brauchen zusätzlich Rechte-/Quellenbeweis; keine Eigenmächtigkeit.
- **Prüfformen getrennt:** Java2D-Host ≠ Filament/Android ≠ Fold7. Vollständige Android-/UI-/Lint-/APK-Buildgates sind auf exakt derselben integrierten Quelle erneut auszuführen. FPS/Jank/Temperatur nur anhand echter Messung.
- **Übergabe:** C1 ist Eigentümer gemeinsamer `ContinuumHologramView.kt`, `AgentActivity.kt`, Navigations-, Sprach- und Freigabeanschlüsse. Fachzweige liefern isolierte Änderungspakete und Regressionen; keine pauschale Übernahme des divergierten #535-Zweigs. Jeden sinnvollen Schritt committen, pushen, PR und Belege aktuell halten.

## Reihenfolge

**D1:** Den hier staged Original-Orb-/Shader-Fill-Slice korrekt testen. **D2:** #535-Energieflüsse/Kopfaura auf gemeinsamen Laufzeitclock mit eigener Regression übernehmen. **D3:** gemeinsame Neon-/Glas-Zustände in Buttons, Kacheln und Menüs an den realen Aufrufpfad anbinden; keine Verfälschung der Statuswerte. **D4:** dieselben Interaktionsmaterialien auf weitere interne Anwendungen und genehmigte externe Arbeitsflächen übertragen. **D5:** komplette native Geräte- und Releasegates, danach erst Signierung/Download.

**Releasebarriere:** Originalpaket `at.heinz.deviceai.continuumlocal`, Originalsignatur `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`, nur vorhandener Render-Host und streng steigender VersionCode. Kein `main`-Merge, keine Signaturumgehung oder stillschweigende Installation.
