# Device AI — holografisches Android-Cockpit

**Referenz:** vom Nutzer am 18. Oktober 2026 freigegebenes futuristisches Device-AI-Entwurfsbild (Smartphone-Mittelteil). **Geltungsbereich:** ausschließlich Device AI Continuum, nicht andere Lern- oder Spielprojekte.

## Bereits im Entwicklungsbranch umgesetzt

1. **Marke:** transparentes Android-VectorDrawable `app/src/main/res/drawable/device_ai_holo_logo.xml`, sechseckig, Cyan/Violett.
2. **3D-Kern:** `ContinuumHologramView.kt` rendert auf einem **transparenten Canvas** eine mehrschichtige gläserne KI-Sphäre mit Cyan-/Violett-Ringen, neuralen Verbindungen, Sternenfeld, Lichtfunken und holografischem Sockel. Keine Internetgrafik, keine Animation im Hintergrund, keine Behauptung einer laufenden Inferenz.
3. **Anwendungskopf:** Device-AI-Schriftzug, Logo, Privatsphäre-Motto und echte Systemverwaltung.
4. **Statusanzeige:** vorhandene dynamische `localBadge`, `shizukuBadge` und `cloudBadge`, niemals hartkodierte grüne Modellverfügbarkeit.
5. **Feature-Dashboard:** zwei Spalten mit funktionsfähigen Aktionen Chat, Forschung, Lernen, Modelle, Entwicklung und Speicher. Breite Systemkachel führt zur vorhandenen Systemverwaltung. Alle Menüs sind an die vorhandenen Implementierungen gekoppelt.
6. **Chat-Composing:** abgerundetes Eingabefeld, neue Sitzung, Mikrofon, lokale Modell-/Denkfunktion nur bei `LocalModelState.READY`, freigabepflichtige Webrecherche, vorhandene Dateiauswahl für Entwicklungsaufgaben, Ton-Einstellung und Senden/Stop.
7. **Zugänglichkeit:** ScrollView für das Dashboard, IME-/Systemleisten-Verwaltung mit `ComposerUiPolicy`, zugängliche Kachelbezeichnungen, dynamisch wachsende Kachelhöhen. Nicht auf 360dp-Festmaße beschränkt.
8. **Ältere Geräteaktionen:** Downloads, Speicher und Akku bleiben über `System → Geräte-Schnellzugriffe` erreichbar.

## Wahrheit und technische Grenzen

Das Werbebild zeigt auch frei schwebende Präsentationskarten außerhalb des Smartphones, beispielhafte Modellnamen und illustrative Fortschrittswerte. **Diese Elemente sind keine verifizierten Live-Zustände und werden nicht als solche in die App kopiert.**

Das native Hologramm nähert die ursprüngliche 3D-Grafik mit Gradienten und glühenden Orbits an; die gerenderten Pixel können von der Bildvorlage abweichen. Eine Zusicherung von »kein Millimeter Abweichung« ist ohne feste Bildschirmauflösung, denselben Schriftsatz, denselben Bildausschnitt, reale APK-Renderings und Screenshot-Differenzprüfung nicht überprüfbar. Ein responsives Android-Layout muss außerdem mit größeren Schriften, Tastatur und faltbaren Displays umgehen.

## Abnahme vor Veröffentlichung

- [ ] Kotlin/Android-Compile von `AgentActivity.kt`, `ContinuumHologramView.kt` und `device_ai_holo_logo.xml` auf dem **exakten neuen Commit**.
- [ ] Android-Lint, App-Unit-Tests, Parität und geprüfter Preview-APK-SHA.
- [ ] Android-Screenshot des Dashboards auf dem Samsung: Portrait bei systemeigener Schrift und 150%-Schrift; Dark Mode; Querformat/Fold; Tastatur offen.
- [ ] Screenshots gegen den **Smartphone-Bereich** des Referenzbilds vergleichen; Größen, Abstände, Farben, Kontrast, Ringe und Transparenz kontrollieren. Quantitative Toleranzen erst nach Zielgerätmessung festlegen.
- [ ] Tippen aller sieben Funktionskarten, Statusanzeigen, Modellverwaltung, neuer Sitzung, Eingabe, Senden, STOP, Sprache, Webfreigabe, Quelldatei und Teamleiter.
- [ ] Fehlende lokale Modell-Datei darf weder als »geladen« noch als »generativ bereit« angezeigt werden.
- [ ] PR bleibt Draft; kein Auto-Merge nach `main`.

## Prüfnachweis in dieser Runde

Die zwei Android-UI-Quellcode-Regressionen (`android-holographic-home.test.mjs` und `android-cockpit-shortcuts.test.mjs`) wurden **9/9** im isolierten JavaScript-Testharness gegen GitHub-Dateiinhalte ausgewertet. Das ist keine Android-APK-/Screenshot- oder Geräteabnahme.

**Nächste Aufgabe:** echter Android-Preview-Build, Layout-Screenshot, optischer Referenzvergleich, UI- und Accessibility-Test.
