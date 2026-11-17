# Dokument-Ablauf „Lies diese PDF …“ – Host-Belege (06.11.2026)

**Kennzeichnung:** HOST (Robolectric, NATIVE-Canvas, 884 × 760 dp, mdpi) · SIMULATED_DOCUMENT_INPUT (Testseite
„Beispiel · Host-Testseite“, im Test gezeichnet – Androids PDF-Renderer ist nativ und läuft nicht auf dem Host) ·
SIMULATED_MODEL_REPLY (Entwurfstext im Test gesetzt – kein lokales Modell auf dem Host) · **REAL_DEVICE_NOT_RUN**.
Kein Bild zeigt ein Besitzerdokument oder eine Fold7-/Filament-Aufnahme; der Kopf ist hier die 2D-Host-Darstellung.

| Datei | Szene |
|---|---|
| `document-0-ready.png` | Cockpit bereit, vor dem Dokument |
| `document-1-page-on-trail.png` | 120 ms: die Seite des Dokuments als Emblem auf der Lichtbahn, Deck räumt |
| `document-2-window.png` | Dokumentfenster offen: Seite links, Fakten rechts (Seiten · Größe · Name, Textstatus, Auszug, ENTWURF ERSTELLEN) |
| `document-3-draft.png` | Entwurfsfläche: Herkunft „lokal erzeugt“, Grundlage, Entwurf, BEARBEITEN, ALS DATEI SPEICHERN |
| `before/…` | dieselben Szenen vor der Verbesserung |
| `crops/…` | Ausschnitte 3× (Uhr) und 2× (linke Fensterkante), vorher/nachher |

## Verbesserungsschleife

| Ausgangszustand (before/) | Schwäche | Änderung | Ergebnis (gleiche Szene) |
|---|---|---|---|
| Uhr oben links „07:…“ (um 07:4x) | proportionale Ziffern breiter als die Uhrbox, Kürzung mit „…“ vernichtet die Minuten | Uhrzeit und Datum werden nie gekürzt, die Schrift verkleinert sich bis 55 %, Grundlinie bleibt | `crops/clock-after.png` „07:58“ vollständig; Test `theClockShowsEveryTimeInFullAndStaysInItsBox` (07:44, 20:48, 00:00, 11:11) RED → GREEN |
| Dokumentfenster einspaltig | Seitenbild füllte die Höhe, Meta/Status/Auszug erst nach Scrollen | breite Fenster (≥ 600 dp): Seite 38 % links, Fakten 62 % rechts; schmale Fenster einspaltig | `document-2-window.png`: alle Fakten ohne Scrollen sichtbar |
| Navigationsbeschriftungen unter dem Glas lesbar | Fenster liegt als Ebene über dem Cockpit, Glasfüllung ≈ 91–94 % deckend | dunkle Unterlage (Hintergrundfarbe, 70 %) unter dem Glas nur des Arbeitsfensters; Panel-Alpha gemessen = 1,0 (keine Animationsursache) | `crops/window-left-edge-after.png`: Beschriftungen deutlich zurückgenommen; **Rest:** „Lernen“ schwach erkennbar |

Reproduktion: `./gradlew --no-daemon -q -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir=<dir> :app:testDebugUnitTest --tests 'at.heinz.deviceai.ContinuumDocumentSceneUiTest'`

Offen auf dem Fold7: echte PDF (Android 16: Textebene über `PdfRenderer.Page.getTextContents`), geladenes lokales Modell
für den Entwurf, Speichern über den Android-Dialog, Falten/Drehen/200 % Schrift bei offenem Dokument- und Entwurfsfenster,
Filament-Kopf innen/außen.
