# Astra — Holografik-Referenztreue messbar machen (VIS-REF-FIDELITY-1) — Handoff 2026-11-08

**Branch:** `astra/continuum-holo-reference-fidelity-20261108` (Basis: SOL-Pinned-Kandidat #653 @ `cb11db2f967110680da92db8c0b07f01f3d2b47d`)
**Status:** `HOST_MEASURED / NOT_DEVICE_VERIFIED` — Java2D-Stand-in des Produktions-Painters, kein Fold7, kein Filament, keine FPS/Thermik.
**Produktcode:** `app/` unverändert. Originalavatar (GLB/Filament) unverändert. #651 (Fable) und #659 (Astra v2 Material) **nicht angefasst**; #659 wurde nur **lesend** über eine Datei-Substitution gemessen.
**Experiment-ID:** keine (Render-QA-Werkzeug, kein kognitives Experiment; `docs/CONTINUUM-EXPERIMENTE.json` unverändert).

## 1. Warum dieser Meilenstein

In #651 und #659 wird Referenztreue bisher nur qualitativ beurteilt („visuell geprüft“, IST/SOLL-Tabellen). Es gab keinen reproduzierbaren Vergleich der gerenderten Konstrukt-Glaspaneele mit den SOLL-Referenzen `docs/design-screenshots/ref5-fold7-offen.png` und `jarvis-master-20261104.jpg`. Dieser Meilenstein liefert die Messung — nicht eine weitere Material-Variante — damit #651/#659 quantifizierte Ziele statt Geschmacksurteile erhalten.

## 2. Was geändert wurde

| Datei | Änderung |
|---|---|
| `tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HollywoodFuiCinematicsPreview.kt` | Den einen Produktions-Painter-Stack (`glassLayers → anchor → grid/scan → data pane → motes/energy → brackets`) als `@JvmStatic paintConstruct(...)` herausgezogen; `render()` delegiert. **Vorher/nachher 36/36 Vorschau-Frames byte-identisch (md5).** |
| `tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HollywoodFuiReferenceFidelity.kt` (neu) | Messwerkzeug: Referenz-Panels messen → Hüllkurve ableiten → 4 Proben rendern und messen → 2 Negativkontrollen → JSON/PNG/Kontaktbogen; Exit 3 wenn Metrik nicht vertrauenswürdig, Exit 1 wenn Gate FAIL. |
| `scripts/hollywood-fui-reference-fidelity.sh OUT [LABEL] [CINEMATICS_OVERRIDE.kt]` (neu) | Kompiliert und startet das Werkzeug; Provenienz (HEAD, dirty, SHA-256 aller Quellen und Referenzbilder, kotlinc/java), Out-of-Source-Guard, optionale Substitution von `HollywoodFuiCinematics.kt` für lesende Fremdbranch-Messung. |
| `docs/evidence/astra-holo-reference-fidelity-20261108/` (neu) | JSON, Run-Protokoll, Provenienz, zwei Probenbilder, halbskalierter Kontaktbogen je Label; Hue-Kontrollbild. |

Keine neue Rendering-Architektur: der Mess-Painter **ist** der Produktions-Stack (`HollywoodFuiCinematics.Painterly` + `Java2DContinuumPainter`), derselbe, den `scripts/hollywood-fui-cinematics-preview.sh` nutzt.

## 3. Metrik (vor der Kandidatenmessung festgelegt)

Für ein Panel-Rechteck (Kanten per Luminanzgradient ±8 px auf den tatsächlichen Rand geschnappt):

- `rimL` — mittlere Luminanz im Randband |d| ≤ 3 px; `haloL` — Band 3 < d ≤ 10 px außen; `interiorL` — Median im Inneren (ignoriert Referenztext).
- `rimContrast = rimL / interiorL`; `haloSpread = haloL / rimL`.
- Farbanteile des Randbands nach Hue-Eimern: cyan 165–205°, blue 205–255°, violet 255–335°, neutral (S < 0.20), offPalette (Rest); `litRimShare` = Anteil Randpixel über Innenluminanz + 0.05.
- Referenzmaßstab 1.64 px/dp (ref5 zeigt 884 dp in 1448 px), damit absolute Bandbreiten vergleichbar sind.

**Hüllkurve (einseitig, aus 9 Referenzpanels, vorregistriert):** `rimL ≥ 0.1023`, `interiorL ≤ 0.2018`, `rimContrast ≥ 0.9774`, `haloSpread ≥ 0.1186`, `offPalette ≤ 0.05`.
Die Hypothese H-VIS-1 lautete: *„Das Fable-v2-Konstrukt ist am Glasrand dunkler/flacher als die Referenzen“* — daher nur untere Schranken. Die Daten widerlegen diese Richtung (siehe 5).

**Negativkontrollen** auf der Probe `fold-settled-high`: (a) Hue-Rotation 180° (cyan → rot) muss `offPalette` reißen; (b) leere Scheibe muss `rimL` reißen. Nur wenn beide FAIL liefern, gilt `metricTrusted=true`.

**Lauf 1 (Korrektur vor jeder Schlussfolgerung):** Kontrolle (a) war mit 120° definiert; 120° bildet Cyan auf palette-internes Violett ab, die Kontrolle war damit leer → `metricTrusted=false`, Exit 3. Kontrolle auf 180° geändert; die Kandidatenwerte waren in Lauf 1 und Lauf 2 identisch (deterministisches Rendering).

## 4. Durchgeführte Läufe

```
scripts/hollywood-fui-reference-fidelity.sh <OUT> fable-v2-653                                   # Basis #653, exit 0
scripts/hollywood-fui-reference-fidelity.sh <OUT> astra-v2-659 <HollywoodFuiCinematics.kt@a9cf4b8d>  # #659 lesend, exit 0
scripts/hollywood-fui-cinematics-preview.sh <OUT>   # vor/nach Refactor: 36/36 PNG md5 identisch
```

`#659`-Datei: `git show a9cf4b8de89589b085943681cc0293da5652aace:app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiCinematics.kt` (SHA-256-Präfix `de3d24f93a47cd81`). #659 ändert gegenüber `cb11db2f` nur diese Datei, die Substitution reproduziert daher dessen Painter.

Kontrollen (beide Labels): hue-rotated-180 **FAIL wie erwartet** `[offPalette 0.997 > 0.050]`; blank-pane **FAIL wie erwartet** `[rimL 0.033 < 0.102]` → `metricTrusted=true`.

## 5. Ergebnisse

Referenzbereich (deskriptiv, 9 Panels): `rimL 0.171–0.306`, `haloL 0.046–0.141`, `interiorL 0.064–0.135`, `rimContrast 1.63–4.20`, `haloSpread 0.198–0.521`, `cyan 0.28–0.91`, `blue 0.09–0.45`, `violet 0–0.38`, `neutral ≤ 0.03`, `offPalette 0.00`, `litRimShare 0.57–0.80`.

| Probe | Label | rimL | haloL | interiorL | rimContrast | haloSpread | cyan | blue | litRim | Gate |
|---|---|---|---|---|---|---|---|---|---|---|
| fold-settled-high (2400 ms) | #653 | 0.5455 | 0.1208 | 0.0376 | 14.49 | 0.221 | 0.983 | 0.014 | 0.999 | PASS |
| | #659 | 0.5604 | 0.1440 | 0.0632 | 8.87 | 0.257 | 0.984 | 0.012 | 0.999 | PASS |
| fold-glassend-high | #653 | 0.2420 | 0.0685 | 0.0404 | 5.98 | 0.283 | 0.947 | 0.047 | 0.510 | PASS |
| | #659 | 0.2576 | 0.0971 | 0.0660 | 3.91 | 0.377 | 0.876 | 0.119 | 0.610 | PASS |
| phone-settled-high | #653 | 0.5485 | 0.1313 | 0.0376 | 14.57 | 0.239 | 0.982 | 0.015 | 0.999 | PASS |
| | #659 | 0.5641 | 0.1557 | 0.0660 | 8.55 | 0.276 | 0.983 | 0.014 | 1.000 | PASS |
| fold-settled-low (thermal low) | #653 | 0.4458 | 0.1006 | 0.0376 | 11.84 | 0.226 | 0.998 | 0.002 | 0.998 | PASS |
| | #659 | 0.4572 | 0.1228 | 0.0632 | 7.24 | 0.269 | 0.999 | 0.001 | 0.998 | PASS |

**Vorregistriertes Gate:** PASS für beide (`metricTrusted=true`). **H-VIS-1 widerlegt:** der Rand ist nicht dunkler/flacher als die Referenzen.

**Explorativer Befund (kein Gate, nachträglich beobachtet):** beide Kandidaten liegen auf der *anderen* Seite des Referenzbereichs:
- Settled-Rand ≈ 1.8× heller als der hellste Referenzrand (0.55 vs 0.31); `rimContrast` 2–3.5× über dem Referenzmaximum (8.6–14.6 vs 4.2) → harte Neonlinie statt weichem Glow.
- Inneres dunkler als das dunkelste Referenzpanel (#653 0.038; #659 0.063 ≈ Referenzminimum 0.0635) → zu wenig transluzente Glasfüllung.
- `litRimShare ≈ 1.0` vs Referenz 0.57–0.80 → Referenzränder sind partiell/gradiert beleuchtet, nicht gleichmäßig.
- Palette zu monochrom: cyan ≥ 0.98 vs Referenzmischung 0.28–0.91 cyan + 0.09–0.45 blue (+ bis 0.38 violet am Statuspanel).
- `haloSpread` im Bereich, aber am unteren Ende (0.22–0.28 vs Median ≈ 0.36).

**#659 gegenüber #653 (gleiche Proben, gleiches Werkzeug):** `interiorL` +0.026 (zur Referenz, bei glassend jetzt im Bereich), `rimContrast` −5.6 (zur Referenz), `haloL` +0.023, `haloSpread` +0.036, `blue` bei glassend +0.07 (alle zur Referenz); `rimL` +0.015 (marginal weg). Richtung des #659-Material-Durchgangs auf 5 von 6 deskriptiven Metriken bestätigt; weiterhin außerhalb des Referenzbereichs bei `rimL`, `rimContrast`, `litRimShare` und Farbmischung.

## 6. Quantifizierte Ziele für #651 / #659 (Java2D-Äquivalent, settled)

`rimL 0.17–0.31` · `rimContrast 1.6–4.2` · `interiorL 0.064–0.135` · `haloSpread ≥ 0.30` (Bereich 0.20–0.52) · `litRimShare 0.57–0.80` (gradierter Rand) · Rand-Palette `cyan 0.3–0.9 + blue 0.1–0.45`, Violett-Akzent nur am Statuspanel · `offPalette 0`.

Vorschlag zur Prüfung in #659: Randalpha senken und die Glasfüllung heller/transluzenter machen (Innenluminanz ↑), Rand als Verlauf cyan→blue statt reinem Cyan, Halo breiter statt heller.

## 7. Grenzen (ehrlich)

- Java2D-Stand-in: kein Android-Bloom/RenderEffect, kein Filament, keine Geräte-Gamma/HDR → Absolutwerte am Gerät weichen ab; Relativvergleiche (#653 vs #659, Kandidat vs Referenz) sind die belastbare Aussage.
- Referenzrechtecke sind von Hand gewählt und ±8 px geschnappt; Referenzpanels enthalten Text (daher Median im Inneren).
- Die Hüllkurve ist **einseitig**; ein PASS bedeutet nur „nicht dunkler/flacher/farbfremd“, nicht „sieht aus wie die Referenz“.
- Keine Unit-Tests des Werkzeugs; Laufzeit-Selbstprüfung = Referenz-Self-Check + 2 Negativkontrollen. Keine FPS-, Thermik-, Energie- oder Gerätedaten: `NOT_RUN`.

## 8. Nächster falsifizierbarer Versuch (VIS-REF-FIDELITY-2, vor dem Lauf registrieren)

1. Zweiseitige Hüllkurve: `[0.8·min, 1.25·max]` für `rimL`, `rimContrast`, `interiorL`, `litRimShare`; `blue ≥ 0.05`; Kontrollen wie bisher plus Positivkontrolle „Rand ×2“ (muss die Obergrenze reißen).
2. `--image`-Modus: dieselbe `measure()` auf einen echten `adb screencap` des Fold7-Konstrukts anwenden (Rect in Gerätepixeln) → Gerätebrücke ohne neuen Renderer.
3. Erwartung: #653 und #659 @ `a9cf4b8d` FAIL an `rimL`/`rimContrast`; die nächste #659-Material-Iteration soll unter denselben Bedingungen in den Bereich wandern.

## 9. Rollback

Die beiden neuen Dateien und der Evidenzordner löschen; der Preview-Refactor kann bleiben (byte-identische Frames belegt) oder per `git checkout cb11db2f -- tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HollywoodFuiCinematicsPreview.kt` zurückgesetzt werden.

## 10. Übergabe

- SOL (#653/#658): Werkzeug ist tool-/scripts-only, ohne Produktcode-Risiko; kann in den Pinned-Kandidaten übernommen werden, wenn gewünscht. Kein Build-51-Bezug.
- Fable (#651) und Astra v2 (#659): Abschnitte 5–6 als quantifizierte Zielwerte; Messung des nächsten Standes mit `scripts/hollywood-fui-reference-fidelity.sh <OUT> <label> <pfad/zu/HollywoodFuiCinematics.kt>` ohne Branchwechsel.
