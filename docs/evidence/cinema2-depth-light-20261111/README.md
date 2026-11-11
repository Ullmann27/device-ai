# Cinema 2.0 – Fokus-Tiefe und Tiefenglas: Vorher/Nachher (11.11.2026)

**Art der Bilder:** HOST – Robolectric-API28-Softwarezeichnung des echten Android-Canvas-Pfads
(`CinemaNativeSequenceUiTest`, `-PcontinuumUiEvidenceDir`), innen 884 × 760 dp, außen 412 × 915 dp.
**Kein Gerätebild, kein Filament-/GPU-Bild** (der Host zeichnet den 2D-Vorschaukopf), kein Blender, kein Java2D.
Der Prüfauftrag im Fenster ist ein skriptierter Testauftrag der Sequenz, keine Modellleistung.

**Vorher:** `e2350618` (Stand #740). **Nachher:** dieser Zweig, enthält zusätzlich den #747-Laufzeitstand
`050a57cb`. Unterschiede in den Bildern können daher auch aus #747 stammen; die hier beschriebenen zwei
Änderungen sind jene dieses Zweigs.

| Datei | Szene |
|---|---|
| `01-inner-idle.jpg` | Leerlauf (unverändert – kein Fenster, keine Abdunklung) |
| `02-inner-work-start-140ms.jpg` | Arbeitsbeginn, 140 ms |
| `03-inner-window-construction-650ms.jpg` | Fensteraufbau, 650 ms |
| `04-inner-running-work-2100ms.jpg` | laufende Arbeit, Fenster steht |
| `05-inner-result-900ms.jpg` | Ergebnis, 900 ms |
| `06-inner-stop-return-650ms.jpg` | STOP-Rückkehr, 650 ms |
| `07-inner-returned.jpg` | zurückgekehrt (Deck wieder voll hell) |
| `08-cover-running-work-2100ms.jpg` / `09-cover-result-900ms.jpg` | Außendisplay |

## Was sich geändert hat

1. **Fokus-Tiefe:** Sobald das Glas des Arbeitsfensters steht, tritt das Deck ab der Fensteroberkante abwärts
   zurück (weich einsetzend, nach unten tiefer) und das Fenster wirft einen Kontaktschatten. Die Zeile mit
   Originalavatar und Originalkugel wird nicht abgedunkelt. Gezeichnet in der Tiefenebene des Fensters (nicht im FUI-Overlay). Folgt dem Aufbau-/Schließbild des Fensters, keine
   eigene Uhr, keine zusätzlichen Bilder pro Sekunde.
2. **Tiefenglas:** Das Fensterglas ist nur dort dicht, wo wirklich Inhalt steht. Endet der Inhalt mindestens
   56 dp über dem unteren Rand, klärt die Fläche darunter zu Rauchglas (Dichte 0,46) mit erhaltenem Rahmen; das
   Hologrammraster und das Deck scheinen durch. Voller oder scrollender Inhalt: unverändert eine dichte Scheibe.

Das Energieband Kugel→Fenster stammt aus #747 und wurde **nicht** ein zweites Mal gebaut; ein eigener Entwurf
dafür wurde in diesem Zweig wieder entfernt.

## Geprüft (HOST)

`Cinema2DepthLightTest` (2 Fälle: Fokus-Tiefe folgt dem Glas und geht mit ihm; Glas nur hinter Inhalt dicht) und
`CinemaNativeSequenceUiTest` grün; dazu alle Klassen `*Cinema*`, `*HollywoodFui*`, `*JarvisWorkWindow*`,
`*JarvisFluidLight*`, `*JarvisRenderQuality*`: **170 Tests, 0 Fehler** (Quellstand `907553f8`, Bilder von diesem Stand).
Die komplette Android-Suite (≈4 670 Tests) wurde für diesen Zweig **nicht** ausgeführt.

## Bekannte Grenzen / offen

- An der Grenze dicht → klar steht eine sichtbare waagrechte Kante; das ist gestalterisch noch nicht fein.
- Durch das klare Glas sind Deck-Beschriftungen schwach lesbar – auf dem Gerät auf Unruhe prüfen.
- Steht ein geschützter Bereich (Dialog) neben statt über dem Fenster, kann seine Aussparung in der Abdunklung
  als Kante sichtbar sein; in den zwei geprüften Layouts tritt das nicht auf.
- **NOT_RUN:** Fold7 innen/außen, Filament-Kopf, Bildzeiten, Wärme, Akku, 200 % Schrift, TalkBack.
