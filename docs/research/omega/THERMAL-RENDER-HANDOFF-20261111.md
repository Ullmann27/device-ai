# Renderarbeit während Modellanfragen

Status: **HOST_VERIFIZIERT**, physische Wärmewirkung **NOT_RUN**.

Basis: `f88d7d1936b9c0d53c176a0af31108fb863199e6` (PR #769).
Agentenzweig: `fix/thermal-render-20261111`. Integrationsverantwortlich: Root;
Ziel: `integrate/omega-apk54-20261111`, über den gemeinsamen thermischen Reparaturstand.

## Nachgewiesene unnötige Arbeit

`ContinuumHologramView.onDraw` ruft `JarvisEnergyFlows.draw` für die sichtbare Bühne auf.
Sieben Linien besitzen je acht zeitabhängige Kontrollwerte. Vorher berechnete
`point` dieselben Kontrollwerte für jeden der 25 Linienpunkte und beide Lichtpulse
erneut. Das sind 1.512 Kontrollwert-Sinusaufrufe, zusätzlich 14 Pulsaufrufe je Frame.

Die Kontrollwerte werden jetzt einmal je Linie und Frame in einem wiederverwendeten
Array berechnet. Alle 25 Linienpunkte, beide Pulse, Farben, Linienbreiten, Transparenz,
Frame-Uhr und Geometrie bleiben gleich. Gemessene tatsächliche Math.sin-Aufrufe:
**1.526 vorher, 70 nachher**. Das ist eine Zählung dieses Helfers, keine Aussage
über die gesamte App-Leistung, Watt oder Akkutemperatur.

`ContinuumHologramView.dispatchDraw` zeichnet außerdem statische Brustlichtlinien über
`JarvisHeadAura.drawLightStreams`. Diese Funktion erzeugte bei jedem Frame zwei neue
`VectorPath`-Objekte und ein neues `Fill.Linear`. Der Android-Painter verwendet
Identitätscaches für native Pfade und Shader; ständig neue Instanzen verhindern
deren Wiederverwendung. Jetzt hält eine einzelne größengebundene Cache-Zeile genau
diese zwei Pfade und den Verlauf. Auch Bruchteile von Pixeln in Breite/Höhe lösen
korrekt neue Geometrie aus. Der Cache wächst nicht mit der Framezahl.

## Nachweise

| Prüfung | Vorher | Nachher |
|---|---:|---:|
| Kotlin/JUnit, neue Wiederverwendung + bestehende Szenentests | 9 PASS / 1 FAIL | 10 PASS / 0 FAIL |
| Instrumentierter Produktions-Bytecode, 72 Flow-Szenarien | 1.526 Math.sin je Frame | 70 Math.sin je Frame |
| Vollständiger Draw-Befehlsstrom dieser 72 Szenarien | identischer Hash | identischer Hash |

Der Hash lautet in beiden Läufen
`33f640d7a6108c7245cf355eeb3e62131ad327d8fb11944e9ad2d73dd82dccd7`.
Der Recorder erfasst unmittelbar beim echten Aufruf alle Painter-Methoden,
Argumente, Farbarrays, Pfadoperationen und rohen Float-Bits. Vier Viewportgrößen
(auch Bruchteile), sechs Zeitpunkte (auch negative Zeit), drei Aktivitätsstärken.
Kein nachgebauter Produktionsalgorithmus dient als Messobjekt. Der Bytecode-Probe
ersetzt ausschließlich die Math.sin-Aufrufstelle durch einen Zähler, der denselben
Math.sin-Aufruf weiterleitet; diese Instrumentierung wird nicht in die App eingebaut.

Die vier neuen JUnit-Fälle prüfen 120 unveränderte Folgeframes, exakte Geometrie bei
Bruchteiländerungen, Fold-/Größenrückkehr und nicht zeichnungsfähige Größen.
Die sechs bestehenden `JarvisLivingSceneTest`-Fälle prüfen weiterhin Bewegung,
Grenzen, Farbverlauf, tatsächliche Aktivität und Reduced Motion.

Protokolle, Befehle, Produktions-/Test-/Abhängigkeitshashes:
`docs/evidence/thermal-render-20261111/{red,green}/`.
Die Tests kompilierten die vier einschlägigen tatsächlichen Produktionsquellen
mit Kotlin 2.4.0/JDK 21 und bereits kompilierten realen App-Abhängigkeiten.
Die Kandidatenquellen waren zum Testzeitpunkt noch nicht committed; ihre einzelnen
SHA-256-Werte im grünen Manifest binden die getesteten Bytes.

Reproduktion nach Bereitstellung derselben lokalen Kotlin/JUnit-Abhängigkeiten:

```sh
python3 tools/thermal-render/run.py \
  --gradle-cache /workspace/scratch/c9f74e2967e3/gradle-home/caches/modules-2/files-2.1 \
  --compiled-dependencies /workspace/scratch/6139925ecf70/device-ai/app/build/tmp/kotlin-classes/debug \
  --out /workspace/scratch/524e6ad8c911/render-qa/recheck \
  --expected-digest 33f640d7a6108c7245cf355eeb3e62131ad327d8fb11944e9ad2d73dd82dccd7
```

Für eine erneute rote Reproduktion dieselben neuen Tests/Probe auf die unveränderten
vier Produktionsdateien der oben genannten Basis anwenden. Erwartet werden genau
ein fehlgeschlagener Wiederverwendungstest und das Work-Gate 1.526 > 70.
Für reguläre Android-QA die beiden Klassen im vorhandenen `testDebugUnitTest` ausführen.

## Abgrenzung und nächste Prüfung

Dies behebt zwei konkret nachgewiesene Quellen vermeidbarer Arbeit im laufenden
Grafikpfad. Dass diese Arbeit die Hauptursache der vom Besitzer gemeldeten starken
Erwärmung ist, ist **nicht nachgewiesen**. Keine thermische Regel, FPS-Einstellung,
Modellqualität, Kontextgrenze, Lichtstärke oder Funktion wurde verändert.

Filament besitzt in diesem Stand keine zusätzliche autonome Frame-Schleife;
der sichtbare View taktet den Renderer. Die vorhandenen Visibility-/Lifecycle-Gates
wurden gelesen, jedoch nicht am Fold7 gemessen. Ein weiterer Befund: die PDF-Stage
wird auch bei gleichbleibendem Dokument auf Avatar-Frames erneut gerendert.
Diese eigene Surface-/Invalidierungslogik bleibt hier unverändert; sie erklärt
ohne geöffnetes Dokument keine allgemeine Anfragehitze.

Noch offen: vollständiger Android-Build, echte GPU-Framezeiten, Energie pro gleicher
Modellantwort, Akku-/SoC-Temperaturkurven sowie Fold7-A/B ohne Lade-/Umgebungswechsel.
Die Integrationsprüfung muss diese Einsparungen zusammen mit Inferenz- und
Hintergrundreparaturen bewerten. Keine APK, Signierung, Installation, Main-Änderung
oder Geräte-Erfolg wurde von diesem Worker ausgeführt.

Experimente: EXP-05 bleibt unverändert; keine Lern-/Berechtigungs-/Modellschnittstelle
ändert sich, keine neue Architektur oder Experiment-ID. Rücknahme: diesen kleinen
Rendercommit rückgängig machen; keine Nutzerdatenmigration nötig.
