# Lokaler Avatar: tatsächlicher GLB, Liveanimation und Herstellwege

Stand: 4. November 2026. Forschungsbot 3. Ausschließlich isolierte Forschung und Hostversuche; keine Produktivintegration, kein App-Build, keine Veröffentlichung.

## Ergebnis und Entscheidung

**Die vorhandene 3D-Basis lässt sich lokal animieren und sollte weiterverwendet werden. Sie erfüllt die gewünschte Ähnlichkeit zu den Originalreferenzen noch nicht.** Das aktuelle GLB besitzt bereits die benötigten Mundformen, getrennte Blinzelziele sowie Kopf-, Augen- und Kieferknoten. Ein neuer Generator ist für den nächsten Entwicklungsschritt nicht erforderlich. Die wesentlichen offenen Arbeiten liegen bei Referenztreue, Mundinnenraum und tatsächlicher Synchronisierung mit der Android-Audioausgabe.

Der untersuchte neuere Quellstand #503 verbessert zwei konkret reproduzierte Verhaltensweisen gegenüber #491: P/B/M schließt die Lippenform auch bei gleichzeitig hohem Audio-Kiefersignal, und ein erneut gerenderter identischer Zeitpunkt verändert den laufenden Viseme-Übergang nicht. Beide Stände verwenden **denselben GLB**. Diese Verbesserungen stammen somit aus der Ansteuerung und nicht aus einem ausgetauschten Gesicht.

Für schnellstmögliche Bildtreue ist ein **2,5D-Pfad mit den Originalreferenzen** eine sinnvolle Vergleichsoption: vorhandenes Kopfbild, getrennte Augen-/Lid-/Mundbereiche, begrenzte lokale Verformung und dieselben Gesprächsereignisse. Dieser Weg wahrt im Ruhezustand die sichtbare Vorlage besonders direkt, hat aber deutlich weniger Freiraum für echte Kopfrotation und anspruchsvolle Mundbewegungen. Für die gewünschte große plastische Figur bleibt der vorhandene 3D-Rig der geeignetere langfristige Kandidat, sofern das Gesicht anhand der Referenzen gezielt überarbeitet wird.

### Wichtigste Belege

| Prüfung | Tatsächlich beobachtet | Aussagegrenze |
|---|---|---|
| GLB-Bytes #491 gegen #503 | Byteidentisch; 10.165.644 Byte; SHA-256 `5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3` | Kein Nachweis eines nativen Android-Ladevorgangs |
| Geometrie | 57.000 Dreiecke, 25 Meshes, 31 Nodes, 21 Morphziele auf drei Primitives | Kein FPS-, Speicher- oder Energieverbrauchswert des Fold7 |
| Gemeinsamer Öffnungsanteil | Aus AA/I rekonstruierter JawOpen: maximal 0,05242 mm Restabweichung an der Haut | Keine vollständige phonetische oder anatomische Validierung |
| PP bei Kieferinput 0,8 | #491: JawOpen 0,8 / Jawrotation 9,6°; #503: beides 0 | Maximale Hautvertex-Differenz 24,75581 mm; **keine gemessene Lippenöffnungsweite** |
| Derselbe Zeitpunkt zweimal | Größte Gewichtsänderung #491: 0,857143; #503: 0 | Prüft die pure Mapper-Funktion, nicht den Android-Frame-Scheduler |
| Je 10.000 deterministische Wechsel | Gewichte endlich und begrenzt; maximale Viseme-Summe 0,99928254 | Synthetische Eingaben, keine aufgezeichnete natürliche Unterhaltung |
| Abbruch / veraltete Rückrufe | Mund schließt; alte Audio-/Visemerückrufe öffnen ihn nicht erneut | Direkt am tatsächlichen Reducer/Signal auf der JVM, keine Activity-Lifecycle-Prüfung |
| Vorausberechnetes PCM | Vor Playback-Anker Pegel 0; danach korrekt 0,25 und 0,75 | Modellierter Zeitverlauf, kein gemessener Lautsprecherstart |
| Blender | Sechs echte GLB-Standbilder, Cycles CPU, 47,67 s zusammen | Hostdarstellung, keine Filament-Pixel und kein App-Beweis |

Die beigepackte `NOTICE.json` enthält einen **veralteten** GLB-Hash (`6b199c5989640b516b8c45401ececeadb8ed1baae1d1e1ab5db20d19e966aa1e`) und 8.690.616 Byte. Beides stimmt in beiden untersuchten Ständen nicht mit dem tatsächlichen Asset überein. Der Forschungsbericht verwendet deshalb ausschließlich selbst berechnete Werte. Das Metadatenproblem ist dokumentiert; die Produktdatei wurde nicht verändert.

## 1. Quellenbasis und Referenzen

Eingefrorener Ausgangspunkt ist PR #491 bei `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`. Zum Vergleich wurden gezielt einzelne Git-Blobs aus PR #503 bei `2582be86c121d7f6d1179c7edc49e979bee8ba9c` gelesen. `GIT_NO_LAZY_FETCH=1` verhinderte versehentliche historische Nachladevorgänge. Keine fremde Arbeitskopie wurde geändert.

Die unveränderten Quelldateien liegen unter `source/p491/` und `source/p503/`. Pfad im ursprünglichen Repository, Commit, Dateigröße und SHA-256 stehen in `results/source-manifest.json`. Enthalten sind Mapper, Vertrag/Reducer, Director, PCM-Signal, RMS-Helfer, Filament-Renderer, GLB und dessen vorhandene Herstellanleitung. Der Hosttest kompiliert Mapper, Vertrag/Reducer und Audiohelfer; der Director wurde gelesen, aber in diesem Versuch **nicht** ausgeführt.

Alle vier vorhandenen Originalreferenzen wurden kopiert, gehasht und tatsächlich visuell angesehen:

- `ref-01-head-frontal.jpeg`: frontaler Kopf, feine geschwungene Linien, dunkles Gesicht mit cyan-/violettfarbiger Randwirkung.
- `ref-02-head-torso-threequarter.jpeg`: Kopf und Brustkorb in Dreiviertelansicht.
- `ref-03-head-profile-eyes.jpg`: weitere schräge Kopfansicht mit leuchtenden Augen. Trotz Dateinamen ist dies keine streng orthografische Profilaufnahme.
- `ref-04-dashboard-fold7.jpeg`: gestalterische Cockpitvorlage; kein Nachweis einer installierten oder laufenden App.

Die unterschiedlichen Ansichten sind künstlerische Referenzen ohne kalibrierte gemeinsame Kamera. Eine exakte geometrische Rekonstruktion lässt sich daraus nicht als bereits gelöst behaupten. Das aktuelle Hostbild zeigt gegenüber den Vorlagen gröbere, regelmäßigere Linien und eine andere Augen-/Gesichtswirkung. Die Ähnlichkeit wurde daher **nicht abgenommen**. Ein automatischer Ähnlichkeitswert wurde nicht erfunden.

Die Rahmenvorgaben bleiben: dunkles Cyan-/Violett-Cockpit, großer plastischer Kopf, feine Gesichtslinien, transparente Statuskarten und die KI-/Gehirnkugel unter dem Kinn mit etwa 40–50 % Deckkraft. Frontdisplay 1080×2520, Innendisplay 1968×2184 sind die vorgegebenen Layoutziele. Cockpit, Kugel, Fenstergrößen und Fold-Zustandswechsel wurden hier nicht implementiert oder getestet.

## 2. Was die tatsächliche Geometrie beweist

`scripts/analyze_glb.py` liest den GLB-Header, JSON-/Binärchunks, die tatsächlichen Accessors einschließlich Byte-Offsets/Strides, Indizes, POSITION-Deltas und Skin-Gewichte. Nicht unterstützte sparse/normalized Accessors würden ausdrücklich abbrechen; dieses Asset benötigt sie für die untersuchten Daten nicht.

Es gibt 15 Visemenamen (`SIL`, `PP`, `FF`, `TH`, `DD`, `KK`, `CH`, `SS`, `NN`, `RR`, `AA`, `E`, `I`, `O`, `U`) sowie `BlinkLeft`, `BlinkRight`, `BrowUp`, `BrowDown`, `Smile`, `JawOpen`. `FaceMesh` besitzt 12.980 Vertexeinträge; die zwei `NeuralThreadsMesh`-Primitives teilen denselben POSITION-Accessor mit 13.389 Einträgen. Über sämtliche Primitives summiert ergeben sich 42.292 Vertexeinträge; nach Deduplizierung wiederverwendeter POSITION-Accessors 28.903. Das ist ausdrücklich keine Zählung geometrisch einzigartiger, zusammenfallender Punkte.

Die Haut und die Linien sind an die Skin-Joints `AvatarRoot`, `Neck`, `Head` gebunden. **Jaw ist kein Skin-Joint dieser Oberflächen.** Sein Unterbaum enthält Mundinnenraum und untere Zähne. Deshalb bedeutet gleichzeitige Lippen-Morphing- und Jaw-Knotenrotation nicht automatisch eine doppelte Hautverformung. Eine bloße Addition der Winkel zu den Morphwerten wäre eine falsche Diagnose.

### Gemeinsame Öffnung innerhalb der Visemen

Die vorhandene Blender-Herstellung definiert unter anderem:

```text
AA      = 0,65 × mouth-open + 0,15 × mouth-part-later
I       = 0,10 × mouth-open + 0,57 × mouth-part-later
JawOpen = 0,80 × mouth-open
```

Damit lässt sich aus den **tatsächlichen exportierten** AA-/I-Deltas ein unabhängiger erwarteter JawOpen-Anteil berechnen:

```text
Jaw_erwartet = 0,8 × (0,57 × AA − 0,15 × I) / (0,65 × 0,57 − 0,10 × 0,15)
```

Die maximale Restabweichung beträgt 0,05242 mm auf dem Gesicht und 0,04940 mm auf den Linien. Die analoge Rekonstruktion von E aus JawOpen und I weicht maximal 0,03406 mm beziehungsweise 0,03114 mm ab. Diese kleinen, aber nicht nullwertigen Abweichungen zeigen eine gute numerische Übereinstimmung mit der gemeinsamen Herstellkomponente. Sie beweisen keine perfekte Linearität sämtlicher Herstellungsschritte und keinen geschlossenen Mundkontakt.

Der Mapper kompensiert bereits sichtbare Öffnung, einschließlich ausblendender Visemen. Bei AA-Gewicht 1 enthält die Form `0,65/0,8 = 0,8125` JawOpen-Äquivalente. Für Kieferinput 0,8 bleibt deshalb kein zusätzliches JawOpen-Gewicht. Der separate Kieferknoten erhält rund 9,75°. Die vom Mapper verwendeten Koeffizienten sind an diese Assetfamilie gebunden; ein später ausgetauschtes Modell benötigt eine erneute Kalibrierung.

Die größte einzelne vorhandene Hautverformung ist JawOpen mit maximal 30,94477 mm. AA erreicht 26,44251 mm, PP 4,82520 mm, BlinkLeft 10,67803 mm. Alle geprüften Deltas sind endlich. Die Gewichte der verwendeten Skin-Vertices summieren sich im gemessenen Float-Datenbestand exakt zu 1. Sprachdeltas aller 15 Visemen sind bei Basis-Y ≥ 0,260 m exakt null: In dieser konkret geprüften oberen Region bewegen Sprachformen weder Augen noch Stirn. Blink/Brow sind absichtlich andere Kanäle.

Zusätzlich wurden je betroffenem Primitive 500 deterministische Paare aktiver Visemen plus kompensierter JawOpen angewendet. Alle resultierenden Positionen waren endlich; die größte synthetische Gesamtverformung am Gesicht betrug 34,59387 mm. Daraus folgt **keine** Kollisionsfreiheit von Lippen, Zähnen oder Mundinnenraum.

## 3. Tatsächlicher Kotlin-Versuch

`scripts/run_kotlin_probe.py` kompiliert die unveränderten Kotlin-Dateien getrennt für #491 und #503 mit Kotlin 2.0.20, JVM-Ziel 17. Als einzige Ersatzdatei wird eine klar markierte **Namensliste** für `AvatarGlbContract.TARGETS` verwendet. Sie ersetzt nicht Renderer, PCM-Signal oder Reducer-Logik; der Android-GLB-Parser ist ausdrücklich vom JVM-Test ausgeschlossen. Compiler-JAR-Hashes und vollständige Aufrufargumente stehen im Ergebnis-JSON.

Pro Stand liefen 10.000 Wechsel mit festem Seed, unterschiedlichen Frameabständen und unterschiedlichen Viseme-/Kiefergewichten. Hinzu kamen NaN/Unendlich, Zeitwiederholung, Stille, deaktivierte Bewegung, Abbruch, verspätete Rückrufe und synthetische PCM-Puffer. Je Stand wurden 20.017 Assertions ausgewertet. Das ist eine Anzahl von Prüfbedingungen innerhalb dieser Szenarien, nicht die Behauptung von 20.017 unabhängigen Unit-Tests.

### Zwei reproduzierte Verbesserungen im Stand #503

**PP/Lippenschluss:** Bei hohem Kieferinput 0,8 lässt #491 zusätzlich zur PP-Kompression JawOpen 0,8 bestehen. #503 setzt bei aktivem PP sowohl das zusätzliche JawOpen als auch die Rotation auf null und beendet auslaufende Vokalformen. Die größte berechnete Hautvertex-Differenz zwischen den stabilisierten Posen beträgt 24,75581 mm, am unteren Gesicht bei neutraler Position ungefähr `(0; 0,18321; 0,14626)` Meter. Dies beschreibt die Änderung eines Meshpunktes, nicht die Öffnung zwischen zwei Lippen. Im Hostrender ist der qualitative Unterschied sichtbar: #491 bleibt bei diesem Test offen, #503 geschlossen.

**Wiederholter Renderzeitpunkt:** Nach AA→O bei 10 ms Abstand springt #491 bei einem zweiten Aufruf mit exakt derselben Zeit zum neuen Ziel; die größte Gewichtsänderung beträgt 0,857143. #503 behandelt diesen wiederholten Zeitpunkt idempotent; beobachtete Änderung 0. Dieser konkrete Fall ist behoben, ohne einen neuen Taktgeber einzuführen.

In den 10.000 deterministischen Wechseln fand sich in beiden Ständen kein unbeschränktes Aufsummieren der Visemen: maximale Summe 0,99928254 und maximale Kieferrotation 11,999685°. Auf eine globale Normalisierung sämtlicher 21 Morphs sollte daraus nicht geschlossen werden: Lid-, Stirn- und Lippenkanäle müssen unabhängig kombinierbar bleiben.

### Abbruch und Audioanker

Der tatsächliche Reducer schloss den Mund unmittelbar nach `SpeechEnded(interrupted=true)`. Nachgereichte Audio-/Visemecallbacks derselben beendeten Äußerung öffneten ihn nicht. Ein altes Ende löschte eine inzwischen neue aktive Äußerung nicht; ohne neue Audiopegel ging der Kiefer nach Ablauf der Frischegrenze auf null. Der Mapper räumte Sprachformen bei Stille beziehungsweise abgeschalteter Bewegung auf.

Für den Signalhelfer wurden zwei vorausberechnete 100-ms-PCM16-Blöcke mit RMS 0,25 und 0,75 bei 16 kHz eingespeist. Bis zum synthetisch erst bei 5.000 ms gesetzten Wiedergabeanker blieb der Pegel null; bei 5.000 ms erschien 0,25 und 100 ms später 0,75. Stop und alte Anker-/Audiocallbacks wurden ebenfalls geprüft. **Es wurde kein echter Lautsprecher und keine reale Audioverzögerung gemessen.**

Android dokumentiert, dass `onAudioAvailable` Syntheseausgabe meldet, die erst später hörbar werden kann. `onStart` liegt kurz vor der Wiedergabe; `onRangeStart` bezeichnet den erwarteten Beginn eines Textbereichs am Lautsprecher und ist nur verfügbar, wenn die Engine Timinginformationen liefert. Der in #491 gelesene Activity-Code speichert PCM-Metadaten und setzt Wiedergabeanker in `onStart`/`onRangeStart`; ein unmittelbarer PCM-Ankunftstakt ist dort nicht die gesamte Logik. [Android-Primärquelle][android]

Der #503-Director erzeugt weiterhin eine deutsche Graphem→Viseme-Annäherung mit nominell 320 ms je Wort und mindestens 40 ms je Form. Das sind keine von der Stimme gelieferten Phonemzeitstempel. Deshalb bleibt die phonetische Präzision selbst bei stabilem Scheduler offen. Eine Beobachtung von Audio-Stille allein wird im Director bei frischen Wortereignissen außerdem gegebenenfalls als gekennzeichneter `WORD_RANGE_FALLBACK` animiert; das ist keine gemessene PCM-Energie.

## 4. Sichtbarer Hostvergleich

`renders/contact-sheet.png` enthält Neutral, AA, O, PP #503, beidseitigen Blink sowie PP #491. Alle Bilder stammen aus dem tatsächlichen GLB, mit denselben ursprünglichen Materialien, derselben Kamera und Beleuchtung. Die stillstehenden Gewichte entsprechen den genannten stabilisierten Mapper-Zuständen. Kein Kopf wurde ausgetauscht, kein generiertes Bild als Appdarstellung eingesetzt und kein Video erstellt.

Die sechs 512×576-Bilder wurden mit Blender 4.3.2, Cycles CPU, zwei Threads und zwölf Samples gerendert. Dauer einschließlich Import/Einrichtung: 47,67 Sekunden. Das ist die Dauer dieser Offline-Bilder und **keine** Aussage zur Android-Bildrate. Der Versuch verwendet keine Filament-Bloom-Nachbearbeitung. Einzelbild-Hashes, Posen und Zeiten stehen in `renders/render-manifest.json`.

Für die Reviewablage liegt dieselbe technische Kontaktansicht zusätzlich als `renders/contact-sheet.jpg` mit JPEG-Qualität 85 vor. Das PNG bleibt die ursprüngliche Montage. Es wurde keine Ansicht umgestaltet; die JPEG-Datei ist lediglich eine kleinere verlustbehaftete Kopie. Beide Hashes stehen in `renders/contact-sheet-manifest.json`.

Die Kontaktansicht bestätigt unterschiedliche Mundformen und geschlossene Lider. Sie zeigt zugleich deutlich überarbeitungsbedürftige gestalterische Eigenschaften: Augenweiß/-wirkung, Linienmaßstab und Mundinnenraum bei Vollgewicht wirken anders als die Originalvorlagen. Vor einer visuellen Abnahme sind neutraler Kopf und moderate Gesprächsgewichte gegen die Referenzen zu prüfen; maximal gewichtete A/O-Bilder sind ein technischer Extremfall, kein Nachweis einer natürlich wirkenden Unterhaltung.

## 5. Herstellwege im bestehenden System

| Weg | Referenztreue | Laufzeit und Abhängigkeiten | Konkreter nächster Aufwand | Bewertung |
|---|---|---|---|---|
| Vorhandener GLB + vorhandene Morphs | Aktuell teilweise; Kopf, Augen und Linien sichtbar verschieden. Anpassungen können kontrolliert am selben Rig erfolgen. | Bereits eingebundener Filament-/gltfio-Pfad 1.56.0; lokale Gewichte/Knoten, kein Animationsvideo und keine externe KI-API nötig. | Neutralform und Materialwirkung separat abgleichen; Mundinnenraum prüfen; #503-Ansteuerung am Gerät validieren. | Beste vorhandene Grundlage für echte plastische Rotation und Livegespräch. |
| 2,5D aus Originalreferenz | Sehr hohe sichtbare Treue im frontalen Ruhezustand, weil die tatsächliche Vorlage verwendet wird. Mimik benötigt begrenzte Verformungen oder angepasste Ebenen. | Lokale Bild-/Meshverformung mit vorhandenen Android-Grafikmitteln wäre möglich; keine Filament-Pflicht. Dieser konkrete Pfad wurde hier nicht implementiert. | Kontur/Alphamasken, Augen/Lider/Mund isolieren; wenige kleine Bewegungen; gleiche Reducer-Ereignisse anbinden. | Schnellster Vergleichspfad für Originalbild-Treue; Rotation, Seitenansicht und Mundtiefe bleiben begrenzt. |
| Lokale Blender-/MakeHuman-Herstellung | Der vorhandene Builder ist eine proportionale Anpassung, keine exakte Rekonstruktion. Neue prozedurale Parameter allein garantieren das Originalgesicht nicht. | Blender nur bei Herstellung; Export als GLB, Android-Laufzeit weiter Filament. Im Repository sind MakeHuman-Herkunft/CC0-Hinweise vorhanden. | Bestehenden neutralen Kopf gezielt formen, Morphs/Filamente übertragen, Topologie/Rig/Materialvertrag erneut prüfen. | Sinnvolle Werkbank für den ersten Weg; vollständiger Neuaufbau wäre erheblich mehr Arbeit und erzeugte neue Unsicherheit. |

Diese Aufwandseinschätzungen sind eine technische Priorisierung, keine gemessenen Entwicklungsstunden. Die MakeHuman-Herkunft und CC0-Kennzeichnung sind aus den beigepackten Quellen gelesen; ein neuer vollständiger Lizenz-/Upstream-Audit war nicht Gegenstand dieses Versuchs. Wegen der veralteten NOTICE-Werte muss bei einer späteren Assetfreigabe der konkrete Export eindeutig festgehalten werden.

**Konkrete Empfehlung:** Zunächst die existierende neutrale 3D-Form und ihre Materialien gegen Ref-01/Ref-02 abgleichen, während die nachgewiesenen #503-Mundschutzregeln als Forschungsstand dienen. Einen kleinen 2,5D-Prototyp nur als sichtbaren Vergleich für Heinz erstellen, falls die nächste geometrische Anpassung die gewünschte Ähnlichkeit nicht erreicht. Kein kostenpflichtiger Avatar-/Videodienst ist für diesen Entscheid notwendig. Vollständige lokale Neugenerierung ist aktuell nicht der Engpass.

## 6. Liveanimation: vorhandene Möglichkeiten und offene Grenze

Die passende Architektur ist bereits erkennbar: Gesprächs-/Arbeitsereignisse → Director → ein zuständiger Renderer-Sink → Reducer → Morph-/Knotengewichte. Die Darstellung soll den tatsächlichen Sprecherzustand, reale Ausgabedaten und Unterbrechungen verwenden. Kopf-Mikrobewegungen und Blinzeln dürfen darüber als kleine darstellerische Bewegung laufen; sie dürfen keine erfundene Aktivität vortäuschen.

glTF definiert Morphing als Basisgeometrie plus gewichtete Ziel-Deltas. Das erklärt die numerische Prüfung und erlaubt die Kombination unabhängiger Kanäle. Filaments offizielle API besitzt eine laufend aktualisierbare `setMorphWeights`-Ansteuerung. Der gelesene Android-Renderer ruft diese vorhandene API auf und aktualisiert Kopf/Augen/Kiefer sowie Bone-Matrizen. Damit ist der Ansatz technisch konkret; die Quellen ersetzen den fehlenden Gerätelauf nicht. [glTF-Primärquelle][gltf] [Filament-Primärquelle][filament]

Die vorhandene Kameraanbindung verwendet Camera2-Gesichtspositionsmetadaten bei ausdrücklich aktivierter Kamera und Vordergrundbetrieb. Sie ist **keine** kalibrierte Augenblickrichtung oder Identitätsprüfung. Ein Blick zum erkannten Gesicht lässt sich daraus ableiten; wirklicher Kamera-Blickkontakt und korrekte Achsen/Spiegelung beim Wechsel des Fold7-Displays bleiben Gerätetests. Kamerazugriff, Wahrnehmungsqualität und Energieverbrauch wurden hier nicht ausgeführt.

Für alle Herstellwege sollte dieselbe Animation gelten: PP schließt bei P/B/M, AA/O usw. formen den Mund, gemessene Energie skaliert angemessen, Stop setzt Sprachkanäle sofort neutral, beobachteter Blick hat Vorrang vor kosmetischer Bewegung, fehlende/veraltete Beobachtungen laufen aus. Bei 2,5D werden dieselben Werte auf Ebenen/kleine Verformungen abgebildet; es wäre kein gespeicherter Film.

## 7. Reproduktion und Artefakte

Arbeitsverzeichnis: `/workspace/scratch/9d2785b5ab4e/research/avatar/`.

```bash
python3 scripts/analyze_glb.py
python3 scripts/run_kotlin_probe.py
BLENDER_USER_CONFIG=/workspace/scratch/9d2785b5ab4e/research/avatar/tmp/blender-config TMPDIR=/workspace/scratch/9d2785b5ab4e/research/avatar/tmp /usr/bin/blender -b -t 2 --python scripts/render_contact.py
python3 scripts/assemble_contact.py
```

Die Quellkopien wurden mit gezieltem `GIT_NO_LAZY_FETCH=1 git -C <Repository> show <Commit>:<Pfad>` gelesen, binär geschrieben und vor Verwendung gehasht. Ihre vollständige Herkunft steht im Manifest. Der Kotlin-Runner akzeptiert `--kotlin-lib <Verzeichnis>`; ohne Argument nutzt er den vorhandenen lokalen Compilerpfad. Er legt seine eigenen `tmp`-/`results`-Verzeichnisse an und begrenzt jeden Compileraufruf auf standardmäßig 120 Sekunden sowie jede JVM-Probe auf 30 Sekunden. Die Grenzen sind über `--compile-timeout` und `--probe-timeout` konfigurierbar. Kein Download ist Bestandteil der Versuchsskripte.

| Artefakt | Inhalt |
|---|---|
| `results/source-manifest.json` | Exakte Commits, Originalpfade, SHA-256 aller gelesenen Dateien/Referenzen |
| `results/glb-analysis.json` | Geometrie-, Morph-, Öffnungs-, Skin- und NOTICE-Messwerte |
| `results/kotlin-probe.json` | Tatsächliche Beobachtungen, Compilerhashes, Befehle und Dauern |
| `results/p491-probe.log`, `results/p503-probe.log` | Rohresultate der getrennten JVM-Läufe |
| `renders/render-manifest.json` | Exakte Posen, Renderbackend, Bildhashes und Dauern |
| `renders/contact-sheet.png` | Technische Kontaktansicht der sechs echten GLB-Posen |
| `renders/contact-sheet.jpg`, `renders/contact-sheet-manifest.json` | Identische kleinere Reviewkopie, Qualität 85, Hashes beider Montagen |
| `results/web-sources.json` | Drei überprüfte Primärquellen und jeweilige Aussagegrenze |
| `results/artifact-hashes.json` | Hashes der Forschungsprogramme, Ergebnisse und Kontaktansicht |

NumPy-Lauf des endgültigen Scripts: rund 1,07 s. Erneuter Lauf mit dem finalen konfigurierbaren Runner: Kotlin #491 Kompilierung 6,78 s, Probe 0,246 s; Kotlin #503 Kompilierung 6,96 s, Probe 0,195 s. Beide Probes lieferten dieselben Beobachtungen wie der erste erfolgreiche Lauf. Diese Hostdauern sind keine Mobilgeräte-Benchmarks. Der erste Compileraufruf scheiterte wegen eines fehlenden Compiler-Classpath-Eintrags für das bereits lokal vorhandene `kotlinx-coroutines-core-jvm`-JAR; nach dessen Ergänzung liefen beide unveränderten Quellstände durch. Es wurde keine App-Abhängigkeit verändert.

## 8. Priorisierter nächster Versuch

**Priorität 1: eine echte kurze Unterhaltung auf dem Fold7 mit dem bestehenden Renderer und der lokalen Stimme protokollieren.** Testphrase mit P/B/M, A/O und Pause; danach Unterbrechung während eines Vokals, direkt neue Antwort und ein Fold-/Vordergrundwechsel. Für denselben Utterance-Identifier Zeitpunkte von `onStart`, PCM-Ankunft, `onRangeStart(frame)`, zuletzt gezeichneten Gewichten und Stop erfassen. Hörbaren Ausgabestart separat gegen die protokollierte Uhr bestimmen. Ohne diesen Abgleich darf kein Wert als reale A/V-Synchronität bezeichnet werden.

Prüfkriterien: Keine frühe Mundbewegung allein durch Syntheseankunft, keine Wiederöffnung durch veraltete Äußerungen, PP sichtbar geschlossen, Ende/Abbruch unmittelbar neutral, Kamera-/Renderer-Ressourcen nach Pause korrekt freigegeben. Die konkreten A/V-Abweichungen, Framezeiten und thermischen Zustände messen und offen berichten. Keine vorab erfundenen Zielergebnisse.

**Priorität 2: lokale geometrische Referenzrunde.** Neutral, moderate AA/O/PP und Blink frontal plus Dreiviertel mit gleichen Bildausschnitten gegen Ref-01/02/03 ansehen; Augenöffnung, Lippenproportion, Kinn, Linienverlauf und Mundinnenraum getrennt beurteilen. Nur den bestehenden Kopf ändern, Änderungen als Assetdelta nachverfolgen und die gleichen numerischen Checks wiederholen. Eine vom Nutzer akzeptierte Ansicht ist für Referenztreue entscheidender als ein frei erfundener Prozentwert.

**NOT_RUN:** vollständiger Android-Build, Installation, Filament-Nativlauf, GPU-/Fold7-FPS, Energie/Hitze, Audio-Lautsprechermessung, natürliches Phonemtiming, echte Kamera, biometrischer Blickkontakt, vollständige Selbstkollisionsprüfung, 2,5D-Implementierung, neuer Blender-Bust-Build, endgültige Designabnahme, Cockpit-/Kugel-/Layoutintegration. Es wurde ausschließlich in diesem Forschungsordner geschrieben.

[android]: https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener
[gltf]: https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#morph-targets
[filament]: https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/RenderableManager.java
