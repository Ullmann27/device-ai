# Device AI Continuum – Bunker V04

## Grundlage

Der vorgegebene Workflow Continuum_Cinematic_Home_Blender_Workflow.md wurde
zuerst gelesen. Grundlage ist Continuum_Mission_Desk_V3.glb, SHA-256:
0e6dd56445d5c96f40c228101329f4583ef305de4c33d0491279211ab16e3ee1.
Beide ausgewählten Home-Referenzen wurden visuell geprüft. Die inzwischen im
Drive-Ordner vorhandenen ZIEL-PNGs zeigen dieselben Entwürfe.

Android-Basis: PR #848, Commit b8c31554ce7416540692f1d30b559be3712495d7.
Lokaler Branch: chatgpt/continuum-bunker-v4-20261114.
Der Basisbranch wurde abschließend erneut geprüft und stand unverändert auf
obigem Commit. Main wurde nicht verändert.

## Erstellte 3D-Dateien

| Datei | Bytes | Umfang |
| --- | ---: | --- |
| Continuum_Bunker_Stage_V04.glb | 3.979.204 | 329 Nodes, 320 Meshes, 53 Materialien, 101.824 Dreiecke |
| Continuum_Bunker_Stage_V04_Android.glb | 3.027.184 | 58 Nodes, 55 Meshes, 23 Materialien, 70.020 Dreiecke |
| Continuum_Bunker_Stage_V04.blend | 25.254.592 | Bearbeitbare Szene mit Kamera, sieben Flächenlichtern und separatem Originalavatar |

Die Bunkerhalle besitzt gegliederten Boden, Wände, Träger, Deckenrippen, Tür,
sechs Serverracks, Lüftung, Kabelkanäle und Leitungen. Der Mission Desk bleibt
als Grundlage erhalten und wurde proportional zum Raum verkleinert.
Monitore besitzen flachere Gehäuse und echte Rückseiten mit Kühlrippen,
Serviceblock, Anschlüssen, Gelenkarmen und Kabeln. Graphit, Navy, Titan und
Beton ergänzen Cyan links und Violett rechts.

Die vollständige GLB erhält alle vier V3-Animationsclips. Zeit- und
Rotationsaccessoren wurden nach dem Blender-Export bytegenau übernommen und
verglichen. Die Android-GLB enthält Raum, Tisch und Monitorkonstruktion als
getrennte Wurzeln. Avatar, Orb, Live-Pixel und Animationen bleiben in der
bestehenden App-Laufzeit. Die sieben Flächenlichter gehören zur Blender-Datei;
Android nutzt seine vorhandene Filament-Beleuchtung mit den neuen Materialien.

## Selbst gerenderte Ansichten

Blender 4.3.2, Cycles CPU, AgX. Alle fünf Bilder wurden geöffnet, visuell geprüft
und vollständig dekodiert.

| Ansicht | Auflösung | Samples |
| --- | --- | ---: |
| Front | 1440 × 900 | 64 |
| Rear | 1440 × 900 | 64 |
| Top | 1120 × 1120 | 32 |
| FoldWide | 1440 × 880 | 32 |
| MonitorRear | 1120 × 800 | 32 |

Die Draufsicht liegt unter den Deckenträgern. Nur für die Monitor-Nahaufnahme
wird der davorstehende Avatarbegleiter im Render ausgeblendet. Sichtbares
Cycles-Korn bleibt bei den begrenzten Samples erhalten. Die Monitortexte sind
als 3D-Vorschau gekennzeichnet. Diese Bilder sind keine Android-Screenshots.

## Im Android-Quellcode integriert

* Bunkerdatei als APK-Asset mit Größenlimit, festem Pfad und SHA-256-Prüfung.
  Fehlende oder beschädigte Erweiterungen lassen die bisherige Szene bestehen.
* Laden und Freigeben im vorhandenen Filament-Renderer; kein zusätzlicher
  Renderer oder Frame-Takt.
* Raum und Schreibtisch besitzen unabhängige, gleichmäßige Transformationen.
  Die Monitorrückseite folgt der vorhandenen Monitormatrix.
* Native Monitorfläche mit echten Dashboard-Zeilen. Sie erscheint nur ohne
  gebundene Quelle und nach einer erfolgreich eingereichten ruhenden Kamera.
* Gebundene Live-Quellen haben auch im Wartezustand Vorrang. Während der
  Kamerafahrt wird die neue Arbeitsbereichsfläche ausgeblendet.
* Reale Projekte und offene Arbeit in den breiten Seitenkarten; CPU/RAM aus
  dem App-Zustand, unbekannte Werte als —. Karten öffnen die Aufgabenansicht.
* Die vorhandene adaptive Anordnung verwendet Fensterbreite und Schriftgröße.
  Acht Navigationsziele, Live-Tabs, Dokumente und Capture-Pfade bleiben erhalten.

Originalavatar-GLB, Orb-GLB und Live-Monitor-Material sind bytegleich mit der
Android-Basis. Avatar-Rig/Morphs und Bewegungs-, Blick-, Sprach- und
Lippensynchronisationscode wurden nicht ersetzt.

## Verifikation und Grenzen

25 JVM-Tests bestanden: Asset-Pins, beschädigte/fehlende Assets, getrennte
Transformationen, Projektion sowie bestehende Monitor-/Kamerabewegung.
Die Transformationsprüfung umfasst sieben Breiten (240, 360, 412, 600, 884,
1280, 1536 dp), drei Schriftgrößen (1, 1,5, 2) und jeweils 101 Bewegungsstände.

Bestanden: native Monitoransicht mit 35 originalen Kotlin-Dateien und zwei generierten Java-Dateien gegen den echten Android-Classpath.

5 native Robolectric-Ansichtstests bestanden.
Die Ansichtstests verwenden Android-Systemressourcen; sie rendern nicht den
vollständigen Homebildschirm oder Filament. Der zusätzliche Dashboard-Test
ContinuumWorkspaceSummaryTest ist angelegt, hier jedoch nicht ausgeführt.

Die GLB-Prüfung kontrolliert Struktur, Puffer-/Accessor-Grenzen, endliche Werte,
Dreiecksindizes und Originalanimationen. Sie ersetzt keinen vollständigen
Khronos-Konformitätstest. Alle acht 3D-/Bilddateien wurden nach dem Drive-Upload
erneut heruntergeladen; Größe und SHA-256 stimmen mit den lokalen Dateien überein.

Der vollständige Gradle-Appbuild ist nicht bestätigt: mehrere Läufe endeten
mit „Gradle build daemon disappeared unexpectedly“. Auch die direkte Prüfung
aller 652 Kotlin-Dateien endete mit SIGKILL (Exit -9), ohne Kotlin-Diagnose.
Die genaue Ursache ist nicht belegt. Es wurde kein fertiges APK erstellt.

ADB zeigte kein angeschlossenes Gerät. Fold7 außen/innen, Rotation, Tablet,
Split-Screen, tatsächliche GPU-Ausgabe, FPS, Temperatur, Gerätespeicher und
sämtliche App-Funktionen sind nicht am Gerät durchgetestet. Hosttests ersetzen
keinen solchen Gerätenachweis.

## Übergabe und Fortsetzung

Das private Quellcodepaket enthält Git-Bundle, binären Diff zum Basiscommit,
Blender-Bauskript, Integritätsprüfung und Prüfbelege. Das Bundle setzt den
oben genannten Basiscommit voraus. Reproduktionsbefehle für Blender stehen
in tools/continuum-bunker/README.md.

Es wurde kein öffentlicher Branch oder PR veröffentlicht. Die automatische
Freigabeprüfung hat den Push abgelehnt, weil die bisher private Drive-GLB und
zugehörige Quelldaten dadurch öffentlich würden. Dafür ist eine konkrete
Zustimmung zur öffentlichen Veröffentlichung erforderlich.

Nächste technische Abnahme: vollständiger Gradle-Build in einer tragfähigen
Buildumgebung und echte Gerätetests unter den bestehenden Signierungsregeln.
