# Continuum AAA-UI – Opus-Arbeitsstand (22.10.2026)

- Zweig: `opus/continuum-aaa-ui-20261022`, aufgesetzt auf PR #79 (`77f517a1`), PR #80 (`fe869f07`) nach Review
  übernommen.
- Nur native Kotlin-/View-/`ContinuumPainter`-Architektur; kein Flutter, kein Compose.

## 1. Was geändert wurde

| Commit | Inhalt |
|---|---|
| `fad3e8bc` | PR #80 (gemessene Neon-Welle) zusammengeführt. Review: Animation nur bei echter Lernaktivität, sichtbarem Fenster und Messreihe > 0; Callback wird beim Ablösen entfernt; keine erfundenen Messwerte. |
| `2fa05526` | Der wandernde Punkt der Lernwelle lag zwischen zwei Stundenwerten neben der gezeichneten Kurve (lineare statt Bézier-Interpolation). Jetzt liegt er exakt auf der Kurve und zeigt nie einen Wert außerhalb der beiden Nachbarwerte (`WaveChartMarkerTest`). |
| `29fe357c` | Emulator-Workflow für echte Android-Screenshots und Tipp-Test (siehe Abschnitt 4). |
| `bccc5f71` | **Hauptbefund und Korrektur:** Siehe Abschnitt 2. |
| `b54e7dc6` | Abnahme-Werkzeug „Startbildschirm für Design-Abnahme sichern“ im Diagnosezentrum. |


## 2. Ursache der großen Abweichungen auf echten Geräten (gefunden, behoben)

- Die Platzhalter (Slots) des Startbildschirms skalierten mit der Fensterbreite, **die darin gezeichneten Elemente
  aber nicht**. Jedes Element zeichnete in Referenz-dp (412 dp Telefon, 840 dp Fold).
- Folge: Bei jeder Breite außer genau 412/840 dp liefen Inhalte über die Kartenränder oder blieben zu klein.
  - Typische Samsung-Telefone (360–384 dp): Zeilen außerhalb der Karten, abgeschnittene Texte.
  - Galaxy Z Fold7 Hauptdisplay (≈ 750 dp hochkant): Karten zeigten „vor 6 Mi…“, Zeilen unterhalb des Rahmens.
- Der Fehler stammt aus meinem eigenen PR #74 und blieb unentdeckt, weil alle Vorschauen genau in Referenzbreite
  erzeugt wurden.
- Korrektur: Start-Slots, Telefon-Navigation und Fold-Leiste zeichnen ihr Element mit demselben Skalierungsfaktor.

**Zusätzlich für das Fold7:**
- Breitlayout ab **600 × 480 dp** (Wunsch des Besitzers; gilt auch bei größerem Bildschirmzoom). Das Außendisplay
  (411 dp) bleibt im Telefonlayout.
- Hochkant ist das Hauptdisplay fast quadratisch; die Referenz ist querformatig. Die zusätzliche Höhe vergrößert jetzt
  die Hologramm-Bühne (der Kern wird größer); der Rest öffnet die Abstände vor beiden Kartenreihen. Die Karten behalten
  ihre Referenzform. Vorher blieb unten ein leerer Streifen.
- Belege: `docs/evidence/ui-20261022/jvm-preview-*.jpg` (360×740, 411×900, 656×690, 750×790, 832×708 dp).

## 3. Gemessener Abgleich mit den Referenzbildern

Werkzeug `scripts/continuum-design-diff.py`:
- skaliert ein Bild des Startbildschirms auf das Pixelraster der Referenz;
- findet die Lage im Referenzbild selbst (Kantenkorrelation, grob bis fein);
- misst je Slot die mittlere Farbabweichung (0–255) und die Kantenkorrelation;
- schreibt Überlagerung, Differenzbild und Nebeneinander.

> **Korrektur 23.10.:** Die Kantenwerte dieser Tabelle stammen aus dem Grobraster der Lagesuche (Fehler, von Astra
> gefunden, siehe Abschnitt 6). In voller Auflösung und eingefrorener Lage lauten die Ausgangswerte: Telefon gesamt
> 25,5 / 0,237, Fold gesamt 26,1 / 0,203, Hologramm Telefon 39,4 / 0,085, Hologramm Fold 44,5 / 0,051.

**Erster Lauf mit der JVM-Vorschau** (echter Zeichencode, Java2D statt Android-Skia; **kein Gerätebild**):

| Slot | Telefon: Abweichung / Kanten | Fold: Abweichung / Kanten |
|---|---|---|
| gesamt | 26,4 / 0,335 | 27,1 / 0,344 |
| Hologramm (HERO) | 39,7 / 0,073 | 44,3 / 0,064 |
| Marke | 25,8 / 0,297 | 33,5 / 0,202 |
| Modellstatus | 22,0 / 0,171 | 20,4 / 0,240 |
| Lernen & Forschung | 21,4 / 0,255 | 24,7 / 0,239 |
| Speicher & Quellen | 23,1 / 0,294 | 27,2 / 0,197 |
| Entwicklung & Code | 18,2 / 0,197 | 22,6 / 0,186 |
| Systemaktivität | – | 32,4 / 0,081 |

- Gefundene Lage im Referenzbild: Telefon (116, 112) px, Fold (80, 40) px.
- Belege: `docs/evidence/ui-20261022/design-diff/`.

**Gewollte Unterschiede (Ehrlichkeit, vom Besitzer verlangt):**
- Beschriftungen nach echter Datenherkunft: „Prüffragen“ statt „Leistung“, „Lernereignisse“, „offene Aufgaben“,
  „Keine Fehlschläge“, „gespeicherte Quellen“.
- Unbekannte Werte erscheinen als „—“, zum Beispiel die GPU.

**Noch sichtbare gestalterische Abweichungen (offen, nach Gewicht):**
1. Hologramm: Die Referenz zeigt eine dichtere, hellere Kugel mit Partikelnetz.
2. Systemaktivität: Die Referenz hat eine Gebirgs-Welle mit Partikelraster.
3. Lernwelle: Die Referenz zeigt größere Amplitude und stärkeren Glow; die Messreihe bleibt dabei echt.
4. Speicher-Ring: In der Referenz dicker, mit Verlauf.
5. Kopf-Icons: In der Referenz gefüllt und glänzend.

## 4. Echte Gerätebilder – Stand und Weg

**Emulator in GitHub Actions** (`.github/workflows/continuum-ui-screenshots.yml`):
- baut, prüft und rendert Telefon, Fold7 außen/innen hochkant/quer, je mit 130 % Schrift;
- tippt jedes klickbare Element an und protokolliert das Ergebnis.
- **Nicht gelaufen:** Alle GitHub-Actions-Jobs dieses Repositorys enden seit etwa 10:30 UTC nach einer Sekunde ohne
  Protokoll, auch die stündlichen Workflows auf `main`. Das deutet auf ein aufgebrauchtes Actions-Kontingent oder
  eine Abrechnungssperre hin; der Besitzer kann das in den GitHub-Abrechnungseinstellungen prüfen. Der nächste Push
  startet den Lauf.

**Render-Builds** sind aus dieser Umgebung nicht erreichbar (Netzwerkrichtlinie).

**Abnahme auf dem Fold7 (empfohlen, ohne Emulator):**
1. Preview-APK dieses Zweigs über die Render-Pipeline bauen (Paket `at.heinz.deviceai.featurepreview`; nie die
   Haupt-App ersetzen).
2. Auf dem Außendisplay, innen hochkant und innen quer jeweils Diagnosezentrum → „Startbildschirm für Design-Abnahme
   sichern“. Das PNG landet unter „Bilder/Continuum“, die Fenstermaße als JSON unter „Download/Continuum“.
3. Vergleich:
   `python3 scripts/continuum-design-diff.py <png> docs/design_targets/continuum-2026-10-22/reference-<phone|fold>.png <out> --metrics <json>`

## 5. Prüfstatus (Stand 22.10., aktuell siehe Abschnitt 6)

| Stufe | Stand |
|---|---|
| SOURCE_INTEGRATED | ja (PR #79 + PR #80 + Korrekturen) |
| KOTLIN_COMPILE_CHECKED | ja, SDK-freier Check: keine Fehler außerhalb der bekannten LiteRT-LM-Dateien (kein APK-Build) |
| JVM_TESTED | UI-Tests 10/10 (`ContinuumHomeLayoutTest`, `WaveChartMarkerTest`, `ContinuumDashboardMathTest`) |
| Node-Tests | UI-Verdrahtung 27/27 |
| APK_BUILT | **nein** (Actions blockiert, Render hier nicht erreichbar) |
| DEVICE_VERIFIED | **nein** |

## 6. Fortsetzung 23.10.2026 (nach Astras Layout-Arbeit)

**Astras Stand:**
- Auf GitHub angekommen ist nur `21c231de` auf `chatgpt/continuum-layout-pixel-alignment-20261023`: `railWidthDp`, noch ohne Aufrufer.
- Hologramm-Netz (`mesh-v1`), Visual-QA-Tests und Resume-Notiz liegen nur in Astras Arbeitsbereich; Astras Remote-Lauf scheiterte.
- Übernommen:
  - `21c231de` per Fast-Forward.
  - Verdrahtung (`62b7d8f2`): Leiste und Dashboard-Versatz kommen jetzt aus einer gerundeten Breite. Vorher lagen auf 750 dp bis zu 3 px Versatz.

**Vergleichswerkzeug (`30be7e05`, `72563e98`), Astras Befund bestätigt:**
- *Fehler:* Die Verfeinerung der Lagesuche verglich Werte in voller Auflösung mit dem höheren Grobraster-Wert. Sie bewegte sich deshalb nie, und gemeldet wurde der Grobwert.
  - Bisher gemeldet: 0,335 / 0,344.
  - Richtig in voller Auflösung: 0,237 / 0,203.
  - Ein Test mit verrauschtem Bild weist es nach: Der alte Code liefert (40, 32) und 0,672; richtig sind (41, 30) und 0,251.
- *Eingefrorene Lage:* `docs/design_targets/continuum-2026-10-22/registration.json`. Alle Iterationen werden an derselben Stelle gemessen.
- *Live-Werte maskiert:* Die Vorschau schreibt alle Texte mit ihren Grenzen in dp (`*.texts.json`). Zahlen und „—“ werden ausgeblendet; Beschriftungen bleiben im Vergleich.
- *Neue Kennzahl `meanAbsDiffLowFreq`:* beide Bilder mit 4 dp weichgezeichnet. Sie misst Komposition und Farbcharakter, unabhängig von der genauen Lage einzelner Linien.
- Die Vorschau beschneidet das Hologramm jetzt wie Android auf seinen Kasten.

**Hologramm (`c222d592`):**
- *Ein räumliches Netz statt Großkreisen und flachem Staub:*
  - 176 verrauschte Fibonacci-Knoten auf zwei Schalen, verbunden mit den nächsten Nachbarn im Raum (einmal berechnet);
  - ein Halo außen, 420 Partikel im Volumen, 8 Lichtbögen;
  - eine gemeinsame Rotationsmatrix für alles;
  - pro Bild nur Rotation und Projektion in vorab angelegte Felder.
- *Tiefe:*
  - Hinten dünn, dunkel und violett; vorne scharf, hell und cyan-weiß.
  - Die innere Kugel verdeckt die hintere Hälfte.
  - Über dem Kern ist das Netz schwach, damit das Unendlichkeitszeichen lesbar bleibt.
  - Lichtimpulse laufen entlang fester Verbindungen.
- *Lichtbögen:* exakt als projizierte Ellipsenbögen gezeichnet, ohne Allokation (Test prüft die Abbildung).
- *Licht:* Die Lichtebenen mischen additiv (Android `PorterDuff.Mode.ADD`, in der Vorschau ein gleichwertiges Composite). Glas, Kern und Schrift decken.
- *Fold-Kugel:* 4 % größer (Rand gemessen 100,7 statt 96,5 dp).
- *Aura:* zweiseitig, türkis links und magenta rechts.
- *Stärken:* über ein Raster mit 15 Punkten gewählt (`docs/evidence/ui-20261023/grid.txt`).

**Systemaktivität (`a0d6bf2c`):**
- Die echte CPU-Messreihe wird als leuchtender Grat über einem Drahtgitter aus denselben Daten gezeichnet.
- Das Licht steigt vom glühenden Horizont auf, wie in der Referenz; jeder Messwert bekommt einen Punkt.
- Die Ringe sind dicker und leuchten nur um den gemessenen Anteil.
- Eine erste Variante mit nach unten verlöschendem Licht war schlechter und wurde verworfen.

**Gemessen** (JVM-Vorschau, eingefrorene Lage; Pixel / 4-dp-niederfrequent / Kante; **keine Gerätemessung**):

| Bereich | Vorher | Nachher |
|---|---|---|
| Hologramm Telefon | 39,4 / 25,9 / 0,085 | 37,6 / 24,3 / 0,091 |
| Hologramm Fold | 44,5 / 29,9 / 0,051 | 40,5 / 25,7 / 0,057 |
| Marke Fold (überlappt das Hologramm) | 36,4 / 20,6 / 0,124 | 35,7 / 19,2 / 0,127 |
| Systemaktivität Fold | 30,7 / 20,3 / 0,130 | 30,0 / 18,1 / 0,141 |
| Telefon gesamt | 25,5 / 15,1 / 0,237 | 24,8 / 14,6 / 0,241 |
| Fold gesamt | 26,1 / 15,3 / 0,203 | 25,2 / 14,1 / 0,206 |

Bilder: `docs/evidence/ui-20261023/hero-*-before/after.jpg`, `system-activity-fold-*.jpg`, `preview-*-after.jpg`.
Messprotokolle aller Zwischenstände: `measurements.txt`; Radialprofile: `radial-profiles.txt`.

**Emulator-Workflow (`275e08aa`):**
- *Fehler B:* Build, Tests und Lint laufen jetzt als Feature-Preview (`-PdeviceAiFeaturePreview=true`, Paket `at.heinz.deviceai.featurepreview`, versionCode 9).
  - Der Bericht prüft Paket und versionCode.
  - Die Activity wird vollqualifiziert gestartet. `.AgentActivity` hätte mit dem Suffix auf eine Klasse gezeigt, die es nicht gibt.
- *Fehler C:* Jeder Tipp muss sein Ziel öffnen; dessen Titel muss als Text erscheinen. Beispiele:
  - Lernkarte → „Lernzentrum“, Speicherkarte → „Gedächtniszentrum“, Codekarte → „Entwicklungszentrum“;
  - Systemkarte → „Systemstatus“, Modellkarte → „DEVICE AI · MODELLE“;
  - Leiste und Telefonleiste → ihre Arbeitsbereiche.
  - Nicht zugeordnete Elemente gelten als UNMAPPED, nie als bestanden.
  - 13 Python-Tests; einer davon prüft, dass jeder erwartete Titel im App-Quelltext existiert.
- *GitHub Actions:* Alle Jobs des Repositorys enden nach 2–3 s; auch die stündlichen Workflows auf `main` sind betroffen. Die Jobs bekommen keinen Runner (`runner_id 0`, keine Schritte, kein Protokoll, keine Annotation über die API).
  - Das ist ein Infrastruktur- oder Kontofehler (Abrechnung bzw. Kontingent), kein Compilerfehler.
  - Prüfen: GitHub → Settings → Billing and plans → Actions. Keine kostenpflichtigen Builds wurden ausgelöst.

**Prüfstatus 23.10.:**

| Stufe | Stand |
|---|---|
| SOURCE_INTEGRATED | ja: PR #79 + #80 + Astras `21c231de` + obige Commits |
| KOTLIN_COMPILE_CHECKED | ja, SDK-frei: keine Fehler außerhalb der 127 bekannten LiteRT-LM-Fehler |
| JVM_TESTED | UI 19/19 (+ `ContinuumHologramPlexusTest`, `RidgeChartTest`), Vorschau-Tests 3/3 |
| NODE_TESTED | gesamte Suite 776: 770 bestanden, 0 Fehler, 6 übersprungen (ein seit `bccc5f71` roter Signatur-Test korrigiert) |
| Python-Werkzeuge | 13/13 |
| APK_BUILT | **nein** (Actions ohne Runner, Render nicht erreichbar) |
| DEVICE_VERIFIED | **nein**; Bildraten auf dem Fold7 offen |
| RELEASE_APPROVED | **nein** |

**Offen (nach Gewicht):**
1. Leuchtkraft und Partikeldichte des Hologramms; die Referenz ist gesättigter, mit Pink-Akzenten und Funken außen.
2. Lernwelle (Amplitude, Glow), Speicherring (Dicke, Verlauf) und Kopf-Icons.
3. Bildzeiten auf echter Hardware. Pro Bild gibt es höchstens 2600 Zeichenaufrufe (Test); Animation pausiert bei unsichtbarer View, abgeschalteten Systemanimationen und im Energiesparmodus.
4. Abnahme auf dem Fold7 mit „Startbildschirm für Design-Abnahme sichern“ und `continuum-design-diff.py` (jetzt mit `--texts` aus der Vorschau gleicher Fenstergröße).
