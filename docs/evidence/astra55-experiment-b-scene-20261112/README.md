# Experiment B: exakte gemeinsame Transform-Updates

**Entscheidung: Call-Budget-Gate bestanden.** Quellenanschluss und Hostnachweis;
native GPU-Bildzeiten, Fold7-FPS und Thermik bleiben **NOT_RUN / NOT_MEASURED**.

## Ein begrenztes A/B

Baseline `5f87bdc610944b93d3173a83c5beeea315abeb66` betreibt Raum, Originalkugel,
Originalavatar und Monitor in derselben Filament-Szene. `FilamentCockpitStage`
schreibt pro Update neun lokale Matrizen: Raum, Seitenmonitore, Hauptmonitor,
Kugel und fünf Originalorbits. Darunter sind wiederholt unveränderte Matrizen.

A führt den bisherigen Transformblock unverändert aus. B verwendet dieselben
Berechnungen und übergibt nur geänderte Matrizen an denselben TransformManager.
Die gespeicherten Werte sind instanzgebundene Kopien aller 16 Floatkomponenten.
Es gibt kein Epsilon: Auch eine Änderung um ein ULP oder das Vorzeichen einer
Null wird übergeben. Nach einer Uploadexception ist auch der vorherige
Cacheeintrag ungültig, weil der Empfänger bereits teilweise verändert sein kann.
Nicht endliche Eingaben werden wie zuvor weitergereicht, aber nie gecacht.

Vorab-Gate: mindestens 20 % weniger Transform-Aufrufe im repräsentativen
Zwölf-Phasen-Replay bei identischen vollständigen lokalen/Weltmatrizen und
Projektionen; alle Negativfälle müssen bestehen. Keine weitere Animation,
Kamera, Taktquelle oder Assetänderung gehört zu diesem Experiment.
[Offizielle Primärquellen und Abrufdatum](SOURCES.md) wurden vorher gesichert.

## Tatsächlicher Aufrufpfad im Hostcheck

`run_experiment.py` extrahiert die Transformanweisungen direkt aus
`FilamentCockpitStage.update` der Baseline und der Arbeitsquelle. Der Block endet
unmittelbar vor `pixels.update`. Eine Quellprüfung verlangt, dass nach Ersetzen
des Empfängernamens alle Berechnungen exakt gleich sind. Die ausgeführten
Extrakte stehen unter `results/BaselineStage.kt` und `results/CandidateStage.kt`.

**Offene Hostgrenzen:** `RectF` wird durch vier Werte ersetzt; statt JNI schreibt
ein Recording-TransformManager kopierte Matrizen. Die lokale Hierarchie stammt
aus den beiden tatsächlich gepackten Stage-GLBs. glTF-TRS und Elternprodukte
werden auf dem Host ausgewertet. Produktionslayout, Fokusfahrt, Orbbewegung,
Welttransformationen und Projektion werden direkt kompiliert und ausgeführt.
Es werden keine erfundenen Transformwerte als Messergebnis eingesetzt.

Die 101 Knoten der Arbeitsstation und Kugel werden nach **jedem** Frame in
allen 16 lokalen und Weltkomponenten verglichen. Zusätzlich werden ihre
projizierten Ursprünge exakt verglichen. Der Avatar liegt außerhalb dieses
Caches: Sein Produktionspfad und Originalasset sind bytegleich zur Baseline;
die bestehenden Framing-/Welt-/Originalringtests laufen zusätzlich.
Dies ist keine Ausführung nativer Filament-Weltmatrizen oder des Renderers.

## Messwerte

| Fester Replay | Frames | A-Aufrufe | B-Aufrufe | Weniger Aufrufe |
|---|---:|---:|---:|---:|
| Sechs aktuelle Phasen je Fold/Phone | 12 | 108 | 78 | 27,78 % |
| Kontinuierliche Hin-/Rückfahrt | 106 | 954 | 642 | 32,70 % |
| Bewegungsfreie Wiederholungen | 60 | 540 | 13 | 97,59 % |
| Resize und Seitenmonitorwechsel | 9 | 81 | 41 | 49,38 % |
| Geschwindigkeitsstetige Umkehr | 10 | 90 | 66 | 26,67 % |

Die statischen Wiederholungen werden separat ausgewiesen und nicht verwendet,
um das Zwölf-Phasen-Gate künstlich zu verbessern. Die Phasen werden mit der
aktuellen Produktion neu berechnet. Die alten zwölf Blender-Bilder auf
`140c59ee` sind keine Messergebnisse dieses Experiments; es gab keinen neuen
Renderlauf. Der zentrale Vergleich ist **108 → 78**, nicht eine GPU-Zeitmessung.

Insgesamt: **318.352 lokale und 318.352 Weltkomponenten bitgleich**, dazu
**39.794 Projektionskoordinaten bitgleich**. Acht Resize-Schritte bewegen einen
Elternknoten bei unveränderter lokaler Ringmatrix. Der deaktivierte Kandidat
reproduziert die gesamte Baseline-Aufrufreihenfolge und alle übergebenen Bits.
Quelle: [replay.json](results/replay.json).

**30 JUnit-Tests PASS:** sieben neue Cache-/Fehlerfälle und 23 vorhandene
Welt-/Originalring-/Layout-/Fokus-/Projektionsprüfungen. Gegenproben umfassen
geteilte mutable Matrixpuffer, jede der 16 Komponenten um ein ULP, vorzeichenbehaftete
Null, fehlgeschlagenen Erstupload, Teilwirkung vor Exception, Deaktivierung und
erneute Aktivierung, neue Lebensdauer sowie nicht endliche/zu kurze Eingaben.

Die erste Compilerkonfiguration ließ die vorhandene Testhilfe `GlbSkinningRig`
aus. Das war ein Harness-Abhängigkeitsfehler; der erfolglose Lauf ist unter
`setup-attempt-1/` erhalten. Nach Ergänzung der unveränderten Testhilfe bestehen
Kompilation, alle Tests und alle fünf Replays. Es wurde kein Gate gelockert.

## Reproduktion

JDK 17, Kotlin 2.0.20, Android-36-JAR, tatsächliche Filament-1.56.0-Klassen,
`org.json:20240303`, JUnit 4.13.2 und Hamcrest 1.3 verwenden. Der Compiler ist
auf 512 MiB, die ausführenden Hostchecks auf 384 MiB begrenzt. Kein Gradle,
Blender oder Download. Ein eigenes temporäres Verzeichnis ist verpflichtend.

```bash
python3 "${REPO}/docs/evidence/astra55-experiment-b-scene-20261112/run_experiment.py" \
  --root "${REPO}" --kotlin-lib-dir "${TOOLS}" \
  --android-jar "${ANDROID_JAR}" --filament-jar "${FILAMENT_JAR}" \
  --json-jar "${JSON_JAR}" --tmp-dir "${TMPDIR}" --output "${OUTPUT}"
```

`${TOOLS}` ist das Compiler-Libverzeichnis mit den genannten Kotlin-/JUnit-JARs.
`${OUTPUT}` bezeichnet ein eigenes Evidenzverzeichnis, hier `results/`.
Öffentliche Logs ersetzen Umgebungswege durch diese Variablennamen. Rohlogs
bleiben im eigenen temporären Verzeichnis. Für eine neue kombinierte Baseline
ist `--baseline <Commit-vor-B>` ausdrücklich anzugeben; die Schutzprüfungen
dürfen nicht still deaktiviert werden.

## Anschluss und Grenzen

Produktiv geändert sind nur `avatar3d/FilamentCockpitStage.kt` und der neue kleine
`avatar3d/CockpitTransformUpdates.kt`. `cacheTransforms=false` im internen
Stage-Konstruktor deaktiviert den Kandidaten ohne neue UI. Cacheänderungen
erzeugen keine anderen Bewegungswerte; die bestehenden Material-, Capture-,
Bone-, Projektions- und Renderabläufe bleiben erhalten.

Der kompilierte Methodenreferenz-Probe verwendet exakt die neue Stage-Zeile
gegen echte Filament-Klassen. Er ersetzt keinen vollständigen Androidbuild:
Diesen führt Root nach Kombination mit den anderen Experimenten und den
`frameSubmitted/frameFailed`-Ergänzungen aus #803 aus. Aus dem Host-Spy folgen
keine nativen Uploadzeiten, Displaylatenzen oder Gerätebilder.

Quellcheckpoint `35122754663f040eb8f4578c38808c4db2e3f81f`; der Lauf begann noch
über dem Start-HEAD, seine einzelnen Quelldateihashes stimmen aber mit diesem
committierten Inhalt überein. [Receipt](results/receipt.json) enthält Baseline,
Quell- und Abhängigkeitshashes, tatsächliche Exitcodes und offene Gerätegates.
Rollback: Cache deaktivieren oder den begrenzten Anschluss zurücknehmen.
