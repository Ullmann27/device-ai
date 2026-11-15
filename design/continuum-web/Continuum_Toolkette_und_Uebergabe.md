# Aktueller Einstieg – Kommando 03

Stand 15.11.2026: Die aktuelle bedienbare Oberfläche steht unter https://device-ai-continuum-heinz.electemu.chatgpt.site bereit. Die unten dokumentierte Werkzeugkette bleibt zur Weiterverarbeitung verwendbar. Neue Laufzeitmodule: workspace-motion.js, telemetry.js und operations.js, jeweils mit CSS. Der Projektordner enthält die aktuelle Implementierung, originale Assets und bearbeitbare Blender-Dateien. Die Figma-Datei zeigt den früheren Entwurf.

# Continuum – Toolkette und nächste Übergabe

Stand: 15. November 2026. „Von App zu App“ bedeutet hier: überprüfbare Moduldateien, Quellen und Zustandsverträge weiterreichen. Ein Import überträgt nicht automatisch die gesamte App. Der Originalavatar behält den in `Continuum_Designuebergabe.md` festgehaltenen SHA-256; die Originalkugel bleibt ihr nachgewiesener Renderer.

## Pipeline und Übergabestand

**GitHub-Originale → früher Figma-Entwurf → Blender-Originalbeleuchtung und separates Display → Three.js/Canvas-Webgestaltung → Bereitstellung der Webvorschau.** Der bestätigte Link steht in der Auslieferungsnachricht. Code und Review laufen über [Draft-PR 850](https://github.com/Ullmann27/device-ai/pull/850).

Blender wurde tatsächlich ausgeführt: `Continuum_Originalavatar_Lichtstudie.blend` und das transparente PNG erhalten den importierten Kopf; `Continuum_Arbeitsdisplay.blend/.glb` enthalten das eigenständige Display. Vorder- und Rückseite wurden gerendert. Three.js lädt das unveränderte Avatar-GLB; Canvas zeichnet den Originalkern. Spline, Rive, PlayCanvas, StackBlitz und Cloudflare wurden nicht als weitere Projektstationen ausgeführt.

## Start und reproduzierbare Arbeit

Projektordner entpacken, Node.js ab Version 20 verwenden:

```sh
cd continuum-preview
npm start
```

Die ausgegebene Adresse öffnen. Der Betrieb benötigt keine zusätzlichen npm-Pakete. Änderungen am Avataradapter werden separat gebaut und geprüft; dafür gelten die neueren Node-Versionen aus `interop/README.md`. Wieder von der Projektwurzel ausgehen:

```sh
cd interop
npm ci --no-audit --no-fund
npm run build:avatar
npm run verify:avatar
```

Blender-Lichtstudie aus dem Projektverzeichnis reproduzieren:

```sh
blender --background --factory-startup -t 4 \
  --python handoff-3d/inspect_original_avatar.py -- \
  --source continuum-preview/assets/continuum-avatar.glb \
  --output handoff-3d --render
```

Das Skript prüft den Originalhash und exportiert keine veränderte Avatar-GLB. Das Display-Skript `handoff-3d/build_continuum_workdisplay.py` verwendet `--output-dir handoff-3d`; seine GLB enthält das Arbeitsobjekt ohne Studiolichter und Kamera.

## Welche kostenlose Station welchen Nutzen hat

| Werkzeug | Geeignete Aufgabe und geprüfte Grenze |
|---|---|
| [Blender](https://www.blender.org/about/license/) | Kostenloses GPL-Werkzeug für Geometrie, Riginspektion, Materialien, Licht und Renderbilder. Lokal verwendbar; erstellte Werke werden nicht automatisch GPL. Hier bereits eingesetzt. |
| [Three.js](https://github.com/mrdoob/three.js/blob/dev/LICENSE) + Canvas | MIT-Bibliothek beziehungsweise Browser-API für das echte Zusammenspiel von Originalrig, stabiler Bedienung und Zuständen. Aktueller ausführbarer Weg. |
| [Spline Free](https://spline.design/pricing) | Visuelle 3D-Anordnung und Interaktionen. Begrenzte persönliche Dateien, 20 MCP-Aufrufe/Tag, keine enthaltenen AI-Credits, Webexport mit Wasserzeichen. Kein pauschal kostenloser vollständiger Export. |
| [Rive Free](https://rive.app/docs/account-admin/pricing) | Kompakte UI-Animationen, Zustandsmaschinen und Datenbindung. Runtime-Export mit Rive-Splash, drei kollaborative Dateien, 50 CLI-Publish-/Push-Vorgänge monatlich zusammen. |
| [PlayCanvas Free](https://playcanvas.com/plans) | 3D-Szenen mit Skripten, Materialien und GLB-Assets. 1 GB Speicher und unbegrenzt öffentliche Projekte. Private Editorprojekte gehören zu bezahlten Plänen. |

Spline kann [eigene GLB-Assets importieren](https://docs.spline.design/importing-content/how-to-import-content); die bestehende App wird dadurch nicht vollständig importiert. [GLB-Export](https://docs.spline.design/exporting-your-scene/files/exporting-as-gtlf-glb) überträgt insbesondere nicht alle Licht-, Nebel-, Material- und Interaktionseigenschaften. Sein [Code-Tab](https://docs.spline.design/interaction-states-events-and-actions/code-tab) bietet eine zusätzliche HTML/CSS/JS-Ebene, ersetzt aber keine automatische Übernahme unseres gesamten Laufzeitmodells.

## Konkrete Übergabeprompts

**Figma – native Frames angleichen:**

> Verwende `continuum-preview/index.html`, `style.css` und die Original-PNGs als aktuellen Gestaltungsstand. Baue Chats links, Plugins oben, Avatar vor der feststehenden Kugel mittig, Linkleiste darunter, Hologramme beidseits und weitere Diagramme rechts als getrennte editierbare Elemente nach. Erhalte deutsche Texte, Manrope/Space Grotesk, lesbare Inhalte und responsive Seitenbereiche. Ersetze das frühere Sheet-Gesicht durch das nachgewiesene Original. Prüfe beide Formate visuell. Bezeichne statische Frames nicht als dauerhaft laufende 3D-Szene.

**Blender/Spline – Material- und Raumarbeit:**

> Öffne `Continuum_Arbeitsdisplay.blend` oder importiere `Continuum_Arbeitsdisplay.glb`. Erhalte getrennte Fassung, Rauchglas, Inhaltsfläche und Rückseite; prüfe Seitenstärke und Lichtbrechung. Für den Avatar ausschließlich das vorhandene GLB verwenden, ohne Neumodellierung oder Retusche. Seine byteidentische GitHub-GLB bleibt maßgeblich. Dokumentiere Importverluste und liefere Szene, Kamera-/Lichtwerte und Vergleichsbilder zurück. Keine Ersatzkugel erzeugen; ihr Original ist Code.

**Rive – begrenztes UI-Modul:**

> Erzeuge ein eigenständiges Bedienelement mit Zuständen Bereitschaft, Zuhören, Arbeiten und Ergebnis. Halte Text und Trefferfläche stabil; animiere Lichtkante und Aktivität. Definiere Eingänge `state` und `progress`. Liefere die bearbeitbare Datei und Runtime-Export samt Hinweis auf den Free-Splash. Ersetze weder Avatar noch Originalkern und erfinde kein Audiosignal.

**Three.js/PlayCanvas – weitere Implementierung:**

> Verwende `avatar-motion.js`, `orb-original-source.js` und die Herkunftsnachweise. Übernimm Originalknoten und Morphziele; Lippen erhalten nur tatsächliche Viseme-Ereignisse. Bei PlayCanvas importiere GLBs als Assets und portiere die Ereignisanbindung ausdrücklich; Three.js-Code ist dort kein unverändert lauffähiges Engine-Skript. Trenne Displayaufbau von echter Werkzeuglaufzeit. Liefere Änderungen, Importverluste und ausgeführte Prüfungen. Android, Mikrofon und TTS erst über belegte Schnittstellen verbinden.

## Optionale Code- und Veröffentlichungswege

**StackBlitz:** ZIP zuerst entpacken, ein Node-/WebContainers-Projekt öffnen und die Dateien aus `continuum-preview/` per Ordnerimport hineinziehen; anschließend `npm run dev`. [Offizielle Importanleitung](https://developer.stackblitz.com/guides/user-guide/importing-projects). Die Android-/Java-App selbst läuft dadurch nicht im Node-Editor; private GitHub-Importe sind keine zugesicherte Gratisleistung.

**Cloudflare Pages Direct Upload:** Fertige statische Vorschau mit `index.html`, Skripten und vollständigem `assets/`-Ordner hochladen. [Direct Upload](https://developers.cloudflare.com/pages/get-started/direct-upload/) unterstützt vorgebaute Assets; [Free-Limits](https://developers.cloudflare.com/pages/platform/limits/) gelten weiterhin. Dieser optionale Veröffentlichungsweg ist ohne zusätzliche Zugriffskonfiguration öffentlich und ersetzt keine private Bereitstellung. Ein Upload wurde hier nicht ausgeführt.
