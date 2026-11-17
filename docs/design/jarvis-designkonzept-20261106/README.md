# JARVIS-Designkonzept: Auftrag bis Rückkehr (06.11.2026)

**Designkonzepte, keine Screenshots der laufenden App.** Fünf zusammenhängende Bilder (2380 × 2644 px, Fold7-innen-Hochformat)
zum Beispielauftrag „Lies die PDF und erstelle eine Zusammenfassung als Entwurf.“ mit dem Beispieldokument
„Projektübersicht.pdf“. Alle Inhalte sind als „Beispiel“ gekennzeichnet.

| Bild | Phase | JARVIS-Status | Kopf (Original-GLB) | Kugel (Original-Renderer) |
|---|---|---|---|---|
| `designkonzept-1-bereit.jpg` | Ruhiges Cockpit, Eingabe „Was soll ich für dich erledigen?“ | Bereit | Blick nach vorn, leichtes Lächeln | READY |
| `designkonzept-2-auftrag-oeffnet-dokument.jpg` | Auftrag angenommen; Lichtbahn vom Dokument, PDF-Fläche entsteht (Kontur → Glas → Inhalt), Begleitflächen dahinter; Kacheln weichen (Airbus-Deck) | Öffnet Dokument | Augen führen zum neuen Fenster | THINKING |
| `designkonzept-3-analysiert.jpg` | PDF vorn, Abschnitt „Vorgehen“ markiert, Verbindung zu „Kernaussagen“ und zur Kugel | Analysiert … | Blick auf das Dokument | RESEARCH |
| `designkonzept-4-entwurf-erstellt.jpg` | Ergebnisfläche vorn („Zusammenfassung – Entwurf“, Cursor, Aktionen), PDF tritt zurück, Verbindung bleibt | Entwurf erstellt | bestätigendes Lächeln, Blick zum Ergebnis | SPEAKING |
| `designkonzept-5-zurueck-im-cockpit.jpg` | Flächen ziehen sich in die Aufgaben-Kachel zurück (Restkonturen, Lichtspur), Ergebnis bleibt erreichbar | Bereit | wieder nach vorn | READY |

## Herkunft der Bildelemente
- **Kopf:** die Original-GLB (`app/src/main/assets/avatar3d/continuum-avatar.glb`, SHA-256 `c8b88385…`), je Phase eine Pose
  (Kopf/Hals-Aufteilung 65 %, Augen führen, Morph-Gewichte Smile/BrowUp/BrowDown), gerendert mit `tools/avatar-host-render`
  (three.js in Chromium, Filament-Licht nachgebildet). **Host-Render, keine Filament-/Fold7-Aufnahme.**
- **Kugel:** der Original-`ContinuumHologramRenderer` (`transparentBackdrop = true`, Geometrie `REFERENCE_COCKPIT`), als Standbild
  über `AndroidContinuumPainter` in Robolectric (Modi READY/THINKING/RESEARCH/SPEAKING). Nicht `ContinuumBrainOrbRenderer`.
- **Oberfläche:** HTML/CSS-Komposition `tools/design-concepts/designkonzept.html` mit den App-Schriften Montserrat und Roboto
  (`app/src/main/assets/fonts/`), aufgenommen mit `tools/design-concepts/shoot-design.cjs` (Playwright, Faktor 2).
- **Referenzvideos (Google Drive „Jarvis Video“):** in dieser Sitzung **nicht zugänglich** (private Dateien); nicht gesichtet.

## Was davon in der App schon echt ist
Arbeitsfenster mit Lichtbahn aus Kachel/neben dem Gesicht, Blick des Avatars zum Fenster und zurück, Deck-Räumung
(Kacheln/Reihen weichen bei offenem Fenster), echtes App-Symbol und -Name in der Werkzeugszene, Original-Kugel und Original-Kopf.
**Noch nicht echt:** PDF-Aufnahme/-Analyse in der App (keine Quelle), Entwurfsfläche mit Bearbeiten/Speichern, „Kernaussagen“-Panel.
