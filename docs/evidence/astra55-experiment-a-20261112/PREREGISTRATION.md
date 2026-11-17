# Experiment A: unveränderte Uniformwerte im bestehenden Monitor

Registriert am 2026-11-12 nach offizieller Internetrecherche, vor Kandidatenänderung/Test. Baseline: `5f87bdc610944b93d3173a83c5beeea315abeb66`. Bestehende Code55-Linie, kanonischer Integrator Parent; kein neuer Renderer, Eventbus, Capture- oder Modellpfad. Registeranschluss: EXP-07 (quellgebundene Gegenproben), keine Änderung eines Modell-/Toolchainstatus.

## Hypothese

`CockpitLiveTexture.update` schreibt aktuell `frameAvailable`, `pointerAvailable` und `contentScale` auch bei identischen Werten erneut. Die lokal aufgelöste Filament1.56.0-Bindung führt diese Aufrufe direkt über JNI weiter. Nur tatsächlich geänderte Werte zu setzen vermindert das Übergabebudget, ohne die vorhandenen Frische-/Sitzungs-/Viewportentscheidungen oder den einmaligen Bildimport zu verändern. Kein vorweggenommener Latenz-/Energiegewinn.

## Vergleich

- **A:** eingefrorene unveränderte Produktionsklasse auf5f87bdc.
- **B:** dieselbe Klasse mit lokalem Vergleich der drei stabilen Uniformwerte. Kein geändert geführtes Feed-Statuspolling, keine neue Queue, keine geänderte Pufferfreigabe.
- Derselbe quellgebundene JVM-Harness kompiliert und führt beide Klassen aus. Android-/Filament-Grenzen werden ausdrücklich als protokollierende Host-Fixtures ersetzt; JVM-Funktion/Uniform-Aufrufbudget, kein tatsächliches GPU-/Android-Pixelresultat.
- Primärmetrik: Zahl der drei Uniformschreibaufrufe über60 Renderer-Ticks mit10 neuen Bildern; Zusatzsequenz60 Ticks desselben frischen Bildes.
- Gleichheitsmetrik: resultierende Materialwerte, Sichtbarkeit, Label, Pointer-UV, importierte Frame-Identitäten und Freigabereihenfolge je Tick. Zeitabhängige Pointerphase wird separat auf gültiges Intervall geprüft; bei direktem Nano-Uhrzugriff keine Scheingleichheit zweier wall-clock Läufe behaupten.

## Vorab festgelegtes Gate

Übernehmen nur bei mindestens50% weniger stabilen Uniformschreibaufrufen in beiden primären Sequenzen, ohne zusätzliche gehaltene Frames/Buffer, ohne verzögerte Frameübernahme in Ticks und mit unveränderten Ergebnissen aller Negativfälle. Bei einer Bindungs-/Freigaberegression ablehnen. Native/GPU-Latenz bleibt bis Gerätevergleich NOT_RUN. Alle Resultate auch bei Ablehnung sichern.

## Feste Negativ-/Grenzfälle

Stop, Pause/Widerruf, neue Capture-Sitzung/Task, veraltetes und überholtes Bild, ungültiger Farbraum, Resize/Portrait/Landscape, geänderter Quellviewport samt korrektem Pointer, Pointer aus alter Sitzung/Frame, verspäteter GPU-Release und zwei bereits gehaltene GPU-Referenzen. Erneutes Bind/Stream-Erstellen muss alle nötigen Materialinitialwerte erhalten.

## Umfang und Rückfall

Produktionsdatei ausschließlich `app/src/main/java/at/heinz/deviceai/avatar3d/CockpitLiveTexture.kt`, erst nach bestätigtem Dateibesitz. Harness/Evidenz in eigenem Experimentordner. Rückfall entfernt nur den lokalen Uniformvergleich. Hauptstand und Peerbäume bleiben unberührt; kein paralleler Gradle.

Basis vor jeglichem Vergleich auf neuesten kanonischen Remote5f87bdc korrigiert. CockpitLiveTexture/LiveComputerFrames/CockpitStageFrame/DispatchGuard sind zwischen140c und5f87bdc identisch (git diff leer).

Interner A/B-Schalter nach Integratorvorgabe: optionaler Konstruktorparameter. Neben eingefrorenem A werden Kandidat EIN und AUS geprüft; AUS muss das A-Aufrufbudget und die Zustandsfolge erhalten. Produktive Aufrufer verwenden weiterhin denselben Konstruktorpfad.
