# Unabhängige Integrationsprüfung

**Quellstand:** `7c3523cde9dae302a52d69818867a9abe8cf0fb7`.

Der betroffene Nachlauf besteht **39/39 Tests**, ohne Fehler oder übersprungene
Tests. Der Receipt bestätigt `exitCode=0` und `sourceUnchanged=true`.
`lintDebug` ist im vorhandenen **NewApi-Prüfumfang** erfolgreich; die tatsächlich
gelesene Lint-XML enthält **0 gemeldete NewApi-Issues**. `checkOnly` bleibt auf
`NewApi` beschränkt und `abortOnError=true`. Dies ist kein vollständiger Lintlauf.

## Echte Fehlerkette

Der umfangreiche Lauf auf `9468eb87` bestand 274 von 275 Tests. Die einzige
fehlgeschlagene Prüfung betraf Hintergrundscrollen nach erneutem Öffnen.
Die korrigierte isolierte RED-Prüfung reproduzierte diesen Fehler:
Hintergrund `0→32`, Arbeitsfläche weiterhin sichtbar. Ihre vorherige
Blick-Vorbedingung wurde erfüllt. Die zwei früheren isolierten Läufe scheiterten
an einer zeitabhängigen Fixture-Vorbedingung vor dem Scrollen und sind kein
zusätzlicher Scroll-Fehlernachweis.

Nach direkter Bindung an den tatsächlichen Hintergrund-ScrollView bestätigt
das finale XML:

- Inneres Scrollen `0→48`: Arbeitsfläche bleibt offen.
- Hintergrundscrollen `0→32`: Arbeitsfläche geschlossen und entfernt;
  Fensterblick beendet.
- Kein synthetisch ausgelöstes ViewTreeObserver-Ereignis.

Die Zahlen verschiedener Läufe werden nicht addiert. Es gibt keinen einzelnen
vollständigen 277-Test-GREEN-Lauf auf dem letzten Quellstand.

## Szenen und sichtbare Daten

Alle **20 neuen PNGs** wurden einzeln visuell geprüft: vier Profile mit Öffnen,
offenem Fenster, tatsächlich gescrollten Daten, Schließen und geschlossenem
Zustand. Alle vier geschlossenen Viewdumps enthalten null WorkWindow-Roots.
Die offenen Panels stehen exakt auf Alpha 1 und Skalierung 1.

Die vollständige tatsächliche Karte **„Sprachmodell — keines geladen“** liegt
in jedem Profil innerhalb der inneren Scrollfläche:

| Profil | Modellkarte, native Pixel | Innere Scrollfläche, native Pixel |
|---|---|---|
| Cover 412 × 915 | `[210,592,369,654]` | `[16,592,396,825]` |
| Fold 884 × 760 | `[456,483,641,545]` | `[45,483,668,744]` |
| Fold, Schrift 130 % | `[604,60,841,133]` | `[577,60,868,173]` |
| Tablet 1536 × 1024 | `[502,557,687,619]` | `[91,557,714,1008]` |

Die Tests verlangen volle native Kartenbreite und Kartenhöhe, denselben
Hologramm-View, erhaltenen Fensterfokus und unveränderte Cockpitposition beim
inneren Scrollen. CLOSE bleibt sichtbar. Die neue Arbeitsfläche überdeckt in
keiner geprüften Phase Kopf, Originalkugel oder bestehende Dialogkarte.

Die kleine Fläche bei 130 % Schrift zeigt anfangs vor allem Titel und Einleitung;
Statuswerte benötigen dort inneres Scrollen. Die neue Aufnahme belegt genau
diesen tatsächlich ausgeführten Schritt.

## Identität und Beleggrenzen

Das Original-GLB bleibt 12.038.416 Byte groß, mit 21 benannten Morphzielen und
SHA256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.
Die acht gebundenen Quellen des vorhandenen Blender-Replays stimmen auch im
letzten Commit bytegenau überein. Beide zugehörigen Manifest-Hashes wurden
erneut unabhängig geprüft.

Diese Screenshots zeigen den bestehenden Canvas-Ersatzkopf. Bei breiten
Profilen überlagert die vorhandene Dialogkarte bereits einen Teil seiner
unteren Silhouette; das Arbeitsfenster verursacht diese Überlagerung nicht.
Der Blender-CPU-Replay belegt Posen des Original-GLB. Beide Belegarten ersetzen
keine Filament-Aufnahme und kein Frametiming auf dem Gerät.

Die Schutzprüfung tastet die Arbeitsfläche im Abstand von 7 Pixeln mit
1 Pixel Randabstand in den tatsächlichen Kopf-/Kugelregionen ab. Sie ist keine
vollständige Pixelprüfung. Die ausgegebenen Systemwerte stammen aus dem
Robolectric-Lauf, nicht aus einem physischen Fold7. Gmail-, Dokument- oder
Werkzeugerfolge werden damit nicht behauptet.

**Physische Fold7-/Filament-Abnahme: NOT_RUN.** Keine eigenen Gradle-Läufe,
Produktdateiänderungen, Commits oder Pushes durch Bot3.

## Referenzfarben

Der neu auf `7c3523c` gebundene Farbbericht wurde unabhängig anhand von drei
PNG-Hashes, dem nativen Viewdump, zwei Skript-Hashes, zwölf Farbregionen und
39 Lichtprofil-Streifen geprüft. Die numerischen Werte stimmen vollständig
mit dem getrennten Bericht auf `9468eb87` überein.

| Rolle | Referenz | App |
|---|---|---|
| Füllung | `#051B30` | `#08132D` |
| Rand | `#17687F` | `#31C5FD` |
| Symbol | `#C9FFFF` | `#C9FFFF` |
| Haupttext | `#FFFFFF` | `#FDFDFD` |

Der App-Rand ist deutlich heller; seine RGB-Abweichung beträgt
`+26/+93/+126`. Das zusammengesetzte Lichtprofil ist nicht monoton; deshalb
bleibt der 1/e-Wert undefiniert. Keine behauptete exakte Farbtreue, keine
Wahrnehmungsmetrik und kein vollständiger Barrierefreiheitsnachweis.

## Rohbelege

- `build-env/logs/final-scroll-cockpit-validation-receipt.json`
- `build-env/logs/final-scroll-cockpit-validation-xml/`
- `build-env/logs/real-scroll-red-corrected-xml/`
- `build-env/final-visual-scroll-fixed/`
- `integration-work/final-phase-review-7c3523c.json`
- `integration-work/final-color-review-7c3523c.json`
- `integration-work/QA_STATUS.json`
- `avatar-motion/replay-integrated-8c851a09/source-final-binding.json`
- `neon-flow/work-window/ref5-app-colors-7c3523c.json`

Das Reviewmanifest enthält die SHA256-Werte aller 20 PNGs und Viewdumps,
die einzelnen Testklassen sowie Lint-, Scroll- und Identitätsbelege.
