# JARVIS Visual – belegter Zwischenstand, 05.11.2026

## Ergebnis und genaue Quelle

**Produktcommit:** `7c3523cde9dae302a52d69818867a9abe8cf0fb7`.
Draft-PR #565 bleibt offen. Kein Main-Merge, APK-Release, Installieren oder
Paket-/Versions-/Signaturwechsel. Genau drei Fachbots; gemeinsame Änderungen
wurden von der Integrationsleitung zusammengeführt.

Der vorhandene **einzige Original-3D-Kopf bleibt bytegleich**: GLB, Rig,
Materialien, Hals und 21 Morphziele. SHA-256
`c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.
Die getrennte Originalkugel verwendet weiterhin `ContinuumHologramRenderer`
mit transparentem Hintergrund, eigenen Ringphasen, Kern und Partikeln sowie
realen Puls-/Abrufsignalen. Der richtige Renderer war bereits in der Basis
angeschlossen; er wird nicht als erst jetzt erfolgter Austausch ausgegeben.

**Erster real angeschlossener Ablauf:** Systemstatus-Kachel → Lichtspur und
Glasfläche → bestehende Statusabfrage → echte Werte/Lesefehler → Inhalts-Scrollen
→ Schließen, bei erhaltenem START-Cockpit, Avatar und Kugel. Fensterblick ist
ein eigener Director-Intent. Augen führen den Kopf an; Zustimmung nickt nur
nach ausdrücklicher Freigabe. STOP räumt Sprache/Mund/Fläche vor frühen Rückgaben
auf. Die Fensteranimation nutzt vorhandene Shell-Draws und `JarvisMotion`.

[C1-Handoff](C1-HANDOFF.md): nur **45 zusätzliche / 16 entfernte Activity-Zeilen**
gegenüber `a5723ac9`; separater Kontextpatch. `JarvisCockpitGrid` unverändert.
[Quellinvarianten](source-invariants.json) dokumentieren Modell, Paketmanifest,
Builddateien und Rendereranschluss.

## Prüfabschluss – keine addierten Summen

| Quelle / Lauf | Tests | PASS | FAIL | SKIP | Bedeutung |
|---|---:|---:|---:|---:|---|
| `7159aba8` + Patch, anschließend identisch `8c851a09` | 196 | 196 | 0 | 0 | Avatar/3D/TTS/Fenster/Lifecycle integriert |
| `8c851a09` + Patch, anschließend identisch `9468eb87` | 23 | 23 | 0 | 0 | Große Schrift, native Dichten, Framing/Layout |
| `9468eb87`, breiter Cockpit-Lauf | 275 | 274 | 1 | 0 | Später Hintergrundscrollfehler; historisch weiterhin FAIL |
| `9468eb87` + korrigierter Scroll-Gegentest | 2 | 1 | 1 | 0 | Tatsächlich 0→32 gescrollt, Fläche fälschlich offen |
| **`7c3523cd`, abschließender betroffener Lauf** | **39** | **39** | **0** | **0** | Scrollkorrektur, vier Bildprofile, TTS/Handsfree/Freigabe |

Der letzte Lauf hat Exit 0 und `sourceUnchanged=true`; kein App-Quellpatch war
während dieses Laufs vorhanden. **NewApi-Lint: 0 gemeldete Issues**, erfolgreicher
`lintDebug` unter der unveränderten Projektkonfiguration `checkOnly = NewApi`.
Das ist kein vollständiger Lintlauf und kein einzelner 277-Test-Gesamtlauf.

Die 39 Fälle verteilen sich auf zehn Klassen: Fensteradapter 7, Fenster-Lifecycle 3,
realer Systemstatus 1, tatsächliches Scrollen 2, native Telefondichten 2,
Shell-Bewegung 9, TTS-Anschluss 7, Handsfree 2, ausdrückliche Freigabe 2 und
visuelle Szenen 4. Rohlogs, XML, Quellpatches und negative Zwischenversuche:
[Prüfgeschichte](work-window-iterations/README.md),
[exakte Zählung](work-window-iterations/iteration-counts.json),
[finale Receipt](work-window-iterations/final-scroll-cockpit-validation/receipt.json),
[NewApi-Bericht](work-window-iterations/final-scroll-cockpit-validation/lint-results-debug.xml).

Bot3 hat die XMLs, Identität und alle 20 Fensterphasen unabhängig geprüft:
[QA-Bericht](QA_FINAL_7c3523c.md), [Reviewmanifest](final-phase-review-7c3523c.json).
Nachweis aus dem realen Scrollpfad: innerer Inhalt 0→48, Fenster offen;
Cockpit-Hintergrund 0→32, Fenster geschlossen und entfernt, Fensterblick beendet.
Kein künstlich ausgelöstes Scroll-Beobachterereignis.

## Sichtbare Belege und ihre Grenzen

### Originalkopf: Blender-CPU, kein Android-Filament

[Neun Original-GLB-Posen](original-glb-replay-8c851a09/README.md): Ruhe,
Zuhören, Denken, Augen vor Kopf, Kopf folgt, Bestätigungsnicken, Ende,
Sprechen und STOP. Die tatsächliche grüne Mapper-Zeitreihe wird auf das
unveränderte Originalmodell angewandt. Alle acht Abhängigkeiten stimmen auch
mit `7c3523cd` überein. Keine neuen Modellgewichte, keine neue Avatarfigur.

| Zuhören | Fensterblick | Sprechen / STOP |
|---|---|---|
| [Originalkopf](original-glb-replay-8c851a09/02-zuhoeren.png) | [Augen zuerst](original-glb-replay-8c851a09/04-fenster-augen-vor-kopf.png), [Kopf folgt](original-glb-replay-8c851a09/05-fenster-kopf-folgt.png) | [Sprechpose](original-glb-replay-8c851a09/08-sprechen.png), [STOP](original-glb-replay-8c851a09/09-stop.png) |

Die Eingaben sind geskriptet; Blender-Beleuchtung und Kamera sind keine
Filament-Ausgabe. Echte TTS-Audiosynchronität ist hierdurch nicht abgenommen.

### Cockpit: echte Activity auf Host-Canvas, bestehender 2D-Ersatzkopf

Vier Szenenprofile erzeugten 40 PNGs. **26 ausgewählte Original-PNGs** sind
archiviert: alle 20 Fensterphasen, fünf Zustände des normalen Innenformats und
START im Cover-Format. Das [Manifest](native-canvas-7c3523cd/manifest.json) enthält
alle 40 Hashes, Dimensionen, Auswahlmarkierung und zugehörige Viewdump-Hashes.
Keine Bildskalierung oder nachträgliche Montage.

| Profil | Öffnen | Tatsächliche Daten | Schließen | Geschlossen |
|---|---|---|---|---|
| Innenformat 884×760 | [PNG](native-canvas-7c3523cd/fold7-inner-884x760-6a-systemstatus-opening.png) | [PNG](native-canvas-7c3523cd/fold7-inner-884x760-6b-data-systemstatus-scrolled.png) | [PNG](native-canvas-7c3523cd/fold7-inner-884x760-6c-systemstatus-closing.png) | [PNG](native-canvas-7c3523cd/fold7-inner-884x760-6d-systemstatus-closed.png) |
| Coverformat 412×915 | [PNG](native-canvas-7c3523cd/fold7-cover-phone-412x915-6a-systemstatus-opening.png) | [PNG](native-canvas-7c3523cd/fold7-cover-phone-412x915-6b-data-systemstatus-scrolled.png) | [PNG](native-canvas-7c3523cd/fold7-cover-phone-412x915-6c-systemstatus-closing.png) | [PNG](native-canvas-7c3523cd/fold7-cover-phone-412x915-6d-systemstatus-closed.png) |
| Innenformat, Schrift 130 % | [PNG](native-canvas-7c3523cd/fold7-inner-884x760-font130-6a-systemstatus-opening.png) | [PNG](native-canvas-7c3523cd/fold7-inner-884x760-font130-6b-data-systemstatus-scrolled.png) | [PNG](native-canvas-7c3523cd/fold7-inner-884x760-font130-6c-systemstatus-closing.png) | [PNG](native-canvas-7c3523cd/fold7-inner-884x760-font130-6d-systemstatus-closed.png) |
| Referenzformat 1536×1024 | [PNG](native-canvas-7c3523cd/reference-1536x1024-6a-systemstatus-opening.png) | [PNG](native-canvas-7c3523cd/reference-1536x1024-6b-data-systemstatus-scrolled.png) | [PNG](native-canvas-7c3523cd/reference-1536x1024-6c-systemstatus-closing.png) | [PNG](native-canvas-7c3523cd/reference-1536x1024-6d-systemstatus-closed.png) |

Zuhören, Denken und Sprechen im Cockpit: [Zuhören](native-canvas-7c3523cd/fold7-inner-884x760-2-listening.png),
[Denken](native-canvas-7c3523cd/fold7-inner-884x760-3-thinking.png),
[Sprechen](native-canvas-7c3523cd/fold7-inner-884x760-4-speaking.png).
Die Statusabfrage liest wirklich im Hostprozess; dessen Werte sind keine
Messung eines physischen Fold7. Die komplette Modellkarte ist in allen vier
Datenaufnahmen sichtbar. Schrift 130 % benötigt dafür inneres Scrollen.

**Bildgrenze:** Diese Canvas-Aufnahmen enthalten den vorhandenen 2D-Ersatzkopf.
In breiten Layouts überlagert die bestehende Dialogkarte einen Teil seiner
unteren Silhouette. Das neue Arbeitsfenster verursacht diese Überlagerung nicht.
Sie darf nicht als erfolgreiche Original-3D-Framing-Abnahme ausgegeben werden.
Host-Geometrieprüfungen des Original-GLB und Blender-Replay werden getrennt
geführt; physische Filament-Pixel sind weiterhin erforderlich.

## Ref5: gemessen, nicht pauschal aufgehellt

[Messbericht mit ROIs und Grenzen](ref5-colors-9468eb87/REF5-MEASUREMENT.md),
[frische Rohdaten auf 7c3523cd](native-canvas-7c3523cd/ref5-app-colors-7c3523c.json),
[finale Quellenbindung](native-canvas-7c3523cd/REF5-BINDING-7C3523C.md),
[portable Reproduktion](ref5-colors-9468eb87/reproduce.py).
Die Originalfunktion `tools/measure_ref5_colors.py.measure` wird für getrennte
native semantische ROIs verwendet. Referenz und App werden nicht gestreckt.

| Rolle | Original-Ref5 | App, Basis / `9468eb87` / `7c3523cd` |
|---|---|---|
| Kachelfüllung | `#051B30` | `#08132D` |
| Kachelrand | `#17687F` | `#31C5FD` |
| Icon-Kern | `#C9FFFF` | `#C9FFFF` |
| Titelband | `#FFFFFF` | `#FDFDFD` |

Vier Rollen sind auch auf dem abschließenden 7c3523cd-Bild gegenüber Basis
und 9468eb87 unverändert. Die ältere Messung samt Bildern bleibt separat erhalten. Das lokale Rand-/Füllungsverhältnis
beträgt 2,756 in Ref5 und 9,231 im Appbild. Der App-Rand ist hier bereits stärker;
eine zusätzliche pauschale Aufhellung ist dadurch nicht begründet. Benachbarte
Lichtbahnen machen das äußere Profil nichtmonoton: keine belastbare 1/e-Glowbreite,
kein Gesamtbild-Gleichheitswert und kein Geräte-Farbtreueversprechen.

## Was offen bleibt

[Fold7-Besitzerabnahme](FOLD7-OWNER-ACCEPTANCE.md): Originalkopf/Krone/Kinn,
auf-/zugeklappt, tatsächliches TTS-Audio und Unterbrechung, Kamera, reale
Framezeiten, Pause/Resume/Rotation und reduzierte Bewegung. **NOT_RUN auf Gerät**.
Die passive Frame-Diagnose zählt Canvas-Draws, keine GPU-FPS. Übergangszeiten
sind Entwurfswerte bis zur tatsächlichen Messung.

Gmail-, PDF- und Coding-Zielszenen sind mit diesem Systemstatus-Ablauf noch nicht
vollständig angeschlossen. Keine E-Mails, Dokumentfunde, geprüften Rechtsquellen,
Werkzeugerfolge oder Freigaben wurden erfunden. Diese weiteren Flächen müssen
jeweils ihre echten Adapter, Daten, Ereignisse und Berechtigungen erhalten.

## Historische Start- und Framing-Belege

## Eigene Host-Belege

Ausgangsquelle: `b294fc9139f1a458fccf815bb931eb61b2f7b0e8`.
`a15624bed87470260adef810ce1610b986f9f02d` ergänzt nur die Übergabe.
JDK17 / SDK36 / Gradle8.11.1 / Kotlin2.4.0 / Robolectric, kein Gerät.

| Lauf | Tests | PASS | FAIL | SKIP | Exit |
|---|---:|---:|---:|---:|---:|
| baseline-visual | 5 | 5 | 0 | 0 | 0 |
| frame-clock-red | 7 | 5 | 2 | 0 | 1 |
| frame-clock-green | 7 | 7 | 0 | 0 | 0 |

Die Läufe überlappen; Zahlen werden nicht addiert. Die vier Bildschirmtests
zeichnen echte AgentActivity-Canvas-Aufnahmen mit einem 2D-Ersatzkopf, keine
Filament-GPU-Pixel. Die fünfte Prüfung ist die bestehende Avatar-Bewegungszeitreihe.

### Reproduzierter Frameclock-Fehler

`frameLoggingHonoursReducedMotion` und
`frameLoggingDoesNotKeepUnfocusedShellDrawing` erwarten nach dem Abklingen keine
weiteren Draws. Tatsächlicher RED: `expected 22, was 37` in beiden Fällen. Die
Debug-Diagnose startet bislang einen zusätzlichen Choreographer-Callback, der
unabhängig von den Animation-/Fokusgrenzen immer `invalidate()` aufruft.

Der Patch entfernt diese aktive Diagnoseuhr. Framezeit und Zeichenkosten werden
im ohnehin ausgeführten `onDraw` beobachtet. Kein zweiter Animationstakt;
Fokusverlust, Stromsparmodus und reduzierte Animation behalten ihre Wirkung.
Die vorhandenen fünf Lifecyclefälle und die zwei neuen Gegenproben bestehen.
Die `FrameSample`-Werte sind UI-Zeichnungen, keine Filament-/GPU-FPS.

Rohlogs, XML und ausführbare Befehle stehen je Lauf im Unterordner.
`frame-clock-red-green.patch` bindet die Änderung an die Ausgangsquelle.
Die späteren Integrationsläufe sind im aktuellen Abschnitt und in der Prüfgeschichte dokumentiert.

## Gerät und Release

Fold7, reale Kamera, echte TTS, Geräte-FPS, APK und Installation: `NOT_RUN`.
PR #565 bleibt Draft/offen; kein Main-Merge, keine Paket-/Signatur-/Versions-
änderung und keine Veröffentlichung einer App.

## Posenabhängige Kinn-Überlappung

Produktbasis `0bfa86db8b7364dc6129bd6b87e9f787f64e41b0`.
Der neue Test `turningChinNeverEntersTheDialogueCard` rechnet mit unabhängiger
Linear-Blend-Skinning-Geometrie der unveränderten GLB. RED: 7 Fälle, 6 PASS,
1 FAIL, 0 SKIP; bei 884×760 bis 67,062326 dp Eindringen in die Dialogkarte.

Ursache: bisherige Einpassung verwendet nur die Ruhe-Silhouette und überspringt
bei großen Bühnen die Dialog-Kollisionsprüfung vollständig. Jetzt umfasst die
Silhouette die geprüften Kopfposen und konservativ die Head/Neck/Rest-Mischungen.
Die Suche prüft beide zulässigen Richtungen vom bevorzugten Anker und maximiert
die Größe unter den Kollisionsgrenzen. Kronenrand, Rig, Materialien und GLB bleiben.

Die stärkere Anforderung verändert notwendigerweise frühere reine Größen- und
Ankererwartungen: 87,5 % bzw. unveränderte Tablet-Komposition dürfen kein Gesicht
über der Dialogkarte erzwingen. Zwei Zwischenläufe bleiben negativ archiviert
(7 Fälle: zunächst 5 PASS/2 FAIL, dann 4 PASS/3 FAIL). Diese alten Layoutannahmen
wurden ausdrücklich auf die nun geforderte kollisionsfreie Posenreichweite
angepasst; die neue geometrische Nichtüberlappungsprüfung bleibt unverändert.

Abschließende eigene `avatar3d.*`-Regression: **61 PASS, 0 FAIL, 0 SKIP**, Exit 0.
`framing-red-green.patch` enthält den vollständigen Delta. Das sind Host-
Geometrie- und Mappertests, weiterhin kein Filament-/Fold7-Abnahmeergebnis.
