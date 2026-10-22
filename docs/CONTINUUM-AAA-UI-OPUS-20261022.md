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

## 5. Prüfstatus

| Stufe | Stand |
|---|---|
| SOURCE_INTEGRATED | ja (PR #79 + PR #80 + Korrekturen) |
| KOTLIN_COMPILE_CHECKED | ja, SDK-freier Check: keine Fehler außerhalb der bekannten LiteRT-LM-Dateien (kein APK-Build) |
| JVM_TESTED | UI-Tests 10/10 (`ContinuumHomeLayoutTest`, `WaveChartMarkerTest`, `ContinuumDashboardMathTest`) |
| Node-Tests | UI-Verdrahtung 27/27 |
| APK_BUILT | **nein** (Actions blockiert, Render hier nicht erreichbar) |
| DEVICE_VERIFIED | **nein** |
