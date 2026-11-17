# Cinema55 – Ausgangszustand (Vorher-Bilder, 12.11.2026)

**Art:** HOST – Robolectric-API28-Softwarezeichnung des echten Android-Canvas-Pfads
(`CinemaNativeSequenceUiTest`, `ContinuumReferenceCockpitUiTest`, `-PcontinuumUiEvidenceDir`).
**Kein Gerätebild, kein Filament-/GPU-Bild** (der Host zeichnet den 2D-Vorschaukopf, nicht das GLB in Filament).
Quelle: Code55-Zweig `24efd1d1f` = Code54 `9713854bf` + #788 + Intelligence55 (keine Grafikänderung gegenüber Code54).
Die Tests liefen grün (exit 0); 64 Bilder erzeugt, 10 repräsentative hier als JPG (Qualität 86).

| Datei | Szene |
|---|---|
| `01-innen-ruhe.jpg` … `05-innen-zurueck.jpg` | Innendisplay 884 × 760 dp: Ruhe → Fensteraufbau 650 ms → Arbeit 2100 ms → Ergebnis 900 ms → zurück |
| `06-aussen-ruhe.jpg`, `07-aussen-arbeit-2100ms.jpg` | Außendisplay 412 × 915 dp |
| `08-fold-hoch-schrift100-home.jpg`, `09-fold-hoch-schrift200-home.jpg` | Fold hoch 840 × 1050, Schrift 100 % / 200 % |
| `10-aussen-412-schrift100-home.jpg` | Außendisplay Home, Schrift 100 % |

Zweck: Vergleichsbasis für Meilenstein 1 (Cinema55 Home) und 2 (Cinematic Workflow). Nachher-Bilder werden mit
demselben Test und denselben Maßen erzeugt. Fold7 innen/außen, GPU-Bildzeiten, Wärme: **NOT_RUN**.
