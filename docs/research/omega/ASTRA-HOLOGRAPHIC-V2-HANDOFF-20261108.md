# Astra: holografische Glaskonstruktion V2

Status: SOURCE_INTEGRATED / KOTLIN_UNIT_TESTS_CHECKED / NOT_DEVICE_VERIFIED.
Experiment: VIS-HOLO-V2-20261108. Draft-PR #659, kein Release-/AAA-Abnahmeversprechen.

## Integrationsvertrag

Eigener Branch `astra/continuum-holographic-v2-20261108`, Basis
`cb11db2f967110680da92db8c0b07f01f3d2b47d` aus #653. #651
`09722adf6c88453ac7607a2a484ae97f00a8fc00` ist nachweislich Vorfahr.
SOL-/Fable-Branches, main, Signierung, APKs und Modellgewichte bleiben unveraendert.
Kanon #642, Auftrag #638/#643/#645 und beide Review-Hinweise aus #651 gelesen.
EXP-00..12: keine Job-, Tool-, Memory- oder Modellvertraege geaendert; ausschliesslich
Praesentation bestehender Ereignisse. Kein neuer Avatar, Eventbus oder Renderer.

## Referenz- und Codeaudit / IST-SOLL

Tatsaechlich betrachtete Repository-Bilder: `ref5-fold7-offen.png`,
`jarvis-master-20261104.jpg`, `ref4-phone-hochformat.png`,
`ref2-fold7-tablet-phone.png`, `phone-einflug-250ms.png` in
`docs/design-screenshots/`. Das letzte Bild ist eine fruehe schematische Vorschau,
kein Nachweis des aktuellen Geraetestands. Die Referenzen sind Designziele,
ihre dargestellten Statuszahlen werden nicht als echte Daten uebernommen.
Nicht erreichbare Chat-Anhaenge/Gemini-Originalvideos werden nicht als gesehen behauptet.

| Bereich | Nachgewiesenes IST | SOLL / priorisierter Eingriff |
|---|---|---|
| Material / Licht (P1) | Cinematics verwendet absolute Gradienten 0..1, obwohl die Scheiben hunderte dp gross und versetzt sind; deshalb praktisch einfarbige Raender und fehlender Reflex | Lokale, scheibengrosse Cyan/Violett-Verlaeufe, dunkles Glas, gerichtete Reflexe, schmale scharfe Lichtkante |
| Tiefe / Schaerfe (P1) | Host fuegt Overlay nach dem nativen Arbeitsfenster ein: volle hintere Glasscheiben, Raster und Canvas-Profiltexte liegen ueber bedienbaren Texten | Rueckwaertige Konstruktion unter dem nativen Inhalt; Kopf/Orb/Eingabe geschuetzt; native sp-Texte bleiben vorne |
| Animation (P1) | Fenster: Shell-Uhr und 600-ms-Einflug; FUI: eigene ValueAnimator und 900-ms-Aufbau/420-ms-Abbau; Fenster schliesst in 300 ms | Eine Shell-Zeitbasis, abgestimmte Freigabe des Inhalts und vollstaendiger Rueckbau; keine zweite Animationsuhr |
| Dauerlicht / Budget | Ankerhalo und Glint bleiben nach Aufbau sichtbar (#651); Overlay zeichnet bei aktiver Arbeit unabhaengig von Sichtbarkeit/reduzierter Bewegung nach | Vollstaendiger Anker-Fade, kein eigener Live-Callback; bestehendes JarvisRenderQuality bleibt massgeblich |
| Avatar / Kugel | Original-GLB, MorphMapper, Neck/Head/Eye/Jaw, Blink/Viseme, PoseReducer und Director vorhanden; originale Kugel/Ringe laufen im HologramView | Assets/Rig/kanonische Pose unveraendert erhalten; keine unbelegten Behauptungen ueber neue Gesichtsqualitaet |
| Sieben Welten | DOCUMENT_BLUEPRINT, APP_CONDUIT, CODE_FORGE, SWARM_GRID, EVIDENCE_CONSTELLATION, CHRONO_GAUGE, SECURITY_XRAY vorhanden; typisierte LiveData und echte Timeline | Gemeinsames Material/Bewegungsgesetz fuer alle; keine erfundenen Agenten, Fortschritte oder Ergebnisse |
| Layout / Dialoge | Native WorkWindow-Geometrie schuetzt Gesicht, Kugel und Dialog; graue Lern-/Backlog-Dialoge und Navigationsduplikate sind separate Owner-Befunde | In diesem ersten Meilenstein keine riskante Neuverdrahtung der Bestaetigungs-/Berechtigungsdialoge; bleibt offen |

Untersucht: ContinuumHologramView, FilamentAvatarRenderer, AvatarMorphMapper,
HollywoodFuiConstruct/Cinematics/StageOverlay/LiveData, JarvisWorkWindow/Host,
JarvisMotion, JarvisRenderQuality, JarvisShellView, ContinuumPainter und
AndroidContinuumPainter. Filament nutzt feste Belichtung, drei existierende
Lichter und begrenztes Bloom; keine zusaetzlichen GPU-Paesse vorgesehen.

## Nachweise / Umgebung

Initial: JDK27, Gradle8.11.1; kein adb, Emulator, Android-SDK oder kotlinc auf PATH.
Vorschauversuch: FAIL (kotlinc fehlt). Android-Versuch: BLOCKED (JDK27 nicht
unterstuetzt); Google-SDK-Host `dl.google.com` nicht per DNS erreichbar.
Kotlin2.2.21 fuer vorhandene Host-Vorschau lokal wiederhergestellt.
Material-Checkpoint: RED 5 Tests / 4 Fehler (Anker, Gradienten, Path-Identitaeten,
Partikelobergrenze), danach GREEN 9/9 inklusive bestehender Cinematics-Regressionen.
Je 36 Baseline-/Nachher-Frames mit bestehender Java2D-Vorschau gerendert.
Frame 760 ms visuell geprueft: Cyan/Violett-Verlauf und diagonaler Reflex sichtbar,
persistenter Ankerstrahl entfernt. Keine Android-/Filament-Pixelbehauptung.
JDK21-Wiederholung des Android-Builds: BLOCKED an Google-Maven/AGP8.10.0.
Zeitbasis/Layering/Fallback angeschlossen: Overlay hat keine eigene Uhr,
ValueAnimator oder postInvalidate-Schleife mehr. Fenster liefert normierte
Shell-Zeit, Aufbau und 420-ms-Abbau. Native Daten erscheinen erst ab 640 ms;
geschuetzte Bereiche gelten auch fuer das Overlay. Dekor liegt unter dem Inhalt.
Der Schalter unter Darstellung -> Film-Effekte wirkt auch auf offene Fenster,
entfernt die neuen Schichten und Neigung, ohne Inhalt oder Avatar-Lease zu verlieren.

Vollstaendige SDK-lose Kotlin-Main-/Test-Kompilation PASS. Erster Gesamtlauf:
3549 Tests, 2 Umgebungsfehler (ASCII-Prozesslocale und root-DAC-Bypass).
Gezielte Wiederholung beider Klassen mit C.UTF-8 und ohne DAC-Bypass:
22/22 PASS, keine Codeaenderung an diesen Modulen. Vier Checks-Programme PASS.
Neue UI-Lifecycle-/Z-Order-/Fallbacktests hinzugefuegt, Android-Ausfuehrung wegen
fehlendem SDK/Google-Maven noch NOT_RUN. Finale Ergebnisse stehen unten.

Original-GLB: SHA256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`,
12038416 Bytes, 31 Nodes / 25 Meshes / 8 Materialien. FaceMesh und NeuralThreadsMesh
haben je 21 Morph-Targets inkl. BlinkLeft/Right, JawOpen und 15 Visemen; Neck,
Head, EyeLeft/Right und Jaw vorhanden, keine externen Ressourcen.
Keine Asset-/Rig-/Morph-/Filament-Materialaenderung.

Zugaengliches Video tatsaechlich dekodiert:
`docs/evidence/claude-jarvis-visual-20261106/jarvis-cockpit-app-szene-host.mp4`,
884x804, 15 fps, 20,2 s. Frame bei 3 s betrachtet: explizite
Robolectric-Hostaufnahme mit 2D-Kopf und simuliertem Toolinput, **kein**
Fold7-/Filament-/Gemini-Referenznachweis.

## Finaler Nachweis und Reproduktion

Gesicherte Entwicklungs-Checkpoints: `3e481449bc9671ce74e974959e96294d6d003f0b`
(Audit), `a9cf4b8de89589b085943681cc0293da5652aace` (Material),
`452591edd31215fecea7a3110036157a7c1ed438` (Zeitbasis/Fallback),
`839017298f78d825d82118f359c5d56874bfae57` (gepruefte Host-Evidenz),
`76b26e227e898f375820b872d674859016e6bad2` (bestehendes Cyan-Kernlicht),
`6d392e3efc38feaf12debb91dc1b6149ee5e43aa` (projizierter Fensterblick).
Jeweils Commit -> Push -> `git ls-remote` mit identischem SHA ausgefuehrt.
Finale Evidenz liegt in `docs/evidence/astra-holographic-v2-20261108/`.

| Pruefung | Ergebnis / Grenze |
|---|---|
| Material-RED | 5 Tests, 4 reproduzierte Fehler auf unveraendertem Baseline-Painter |
| Material-GREEN | 9/9; Anker-Ausblendung, Close-Puls, scheibengrosse Gradienten, konstante Shader-/Path-Identitaeten und harter 24-Motes-Deckel |
| Vollstaendige Kotlin-Main-/Test-Kompilation | PASS, JDK21.0.13 / Kotlin2.2.21; bestehender compile-only LiteRT-Stub, kein echtes LiteRT |
| Vollstaendige JVM-Regression Fenster | 632 JUnit-Klassen, 3551 Tests PASS; vier ausfuehrbare Checks PASS (fuenfter Helper hat kein main) |
| Folge-Meilenstein Kernlicht | Zusaetzliche Filament-/View-Typpruefung und 5 neue Policytests, zusammen 3556 JVM-Tests PASS |
| Folge-Meilenstein Blickprojektion | Aktuelle Window-/Host-Klassen typgeprueft, 634 JVM-Klassen / 3563 Tests PASS; `window-gaze-junit.txt` |
| Folge-Meilenstein paralleler Arbeitsbeginn | RED: vier neue Fehler in 12 Reading-Tests; GREEN: 95 gezielte Avatar-/Blicktests; finale Gesamtsuite 3567 PASS, `work-admission-final-junit.txt` |
| Schlusskorrekturen | Erneute Typpruefung der Android-Fenster-/Overlay-Klassen, voller JVM-Lauf mit diesen Klassen zuerst im Classpath |
| Neue UI-Tests | 5 Robolectric-Faelle typgeprueft: nur eine Uhr, Content-Freigabe, spaetes Schliessen, Rueckfall/reduzierte Bewegung, geschuetzte Bereiche/STOP/Thermik; Ausfuehrung BLOCKED |
| Android-Gradle / Lint / native UI | Tatsaechlich versucht, AGP8.10.0 aus Google-Maven nicht aufloesbar; kein SDK/adb/Emulator. Kein APK |
| Java2D | 36 Baseline-, 36 Material- und 40 finale Frames; Materialvergleich bei identischen 760 ms, danach korrigierte Preview mit Frontinhalt vor dem Dekor |
| Fold7 / Android17 / ARM64 | NOT_RUN: Filament-Pixel, Touch/TalkBack/200%-Schrift, Falten/Rotation, STOP waehrend Inferenz, Vorder-/Hintergrund, Framezeiten/Jank/GPU/Temperatur/Akku |

Kommandos (aus Repo-Root; vorhandene Dependencies gemaess
`scripts/android-kotlin-compile-check.sh` und `android-kotlin-unit-check.sh`):

```sh
export JAVA_HOME=/pfad/zu/jdk21
export PATH="$JAVA_HOME/bin:/pfad/zu/kotlinc/bin:$PATH"
export LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS=-Xmx3g
export CONTINUUM_ANDROID_DEPS=/pfad/zu/deps
export CONTINUUM_JUNIT_JARS="$CONTINUUM_ANDROID_DEPS/junit.jar:$CONTINUUM_ANDROID_DEPS/hamcrest.jar"
# Normaler unprivilegierter Benutzer empfohlen. Im root-Host wurde nur fuer
# diese Testprozesse DAC-Bypass entzogen, damit read-only wirklich read-only ist:
setpriv --bounding-set=-dac_override,-dac_read_search \
  scripts/android-kotlin-unit-check.sh "$PWD/build/astra-v2/final-unit"
scripts/hollywood-fui-cinematics-preview.sh build/astra-v2/final
./gradlew -PcontinuumUiWidgetTests=true :app:testDebugUnitTest \
  --tests at.heinz.deviceai.ui.HollywoodFuiWindowUiTest :app:lintDebug --no-daemon
```

Baseline-Frames lassen sich in einem separaten Checkout von `cb11db2f9` mit
dem dortigen Preview-Skript reproduzieren; Material-Frames entsprechend auf
`a9cf4b8de`. Keine Golden-Master-Gleichheit mit dem Referenzbild behauptet:
Layout, Avatar, nativer Inhalt und Android-Perspektive fehlen der Java2D-Vorschau.
Die Kontaktblaetter sind verkleinerte Zusammenstellungen dieser Frames; die
Vollbilder werden durch das Skript erzeugt. Blau/Violett-Verlauf, Reflex und
entfernter Anker wurden in den gerenderten Vorher-/Nachher-Frames betrachtet.

## Ressourcen: gemessen versus Budget

`HollywoodFuiPainterBenchmark` misst 2000 Warmup- und 4000 Sample-Aufrufe pro
Budget mit ThreadMXBean auf dem **x86_64-Host**. Derselbe Probe-Quellcode wurde
gegen Baseline- und neuen Painter kompiliert. CountingPainter ohne echte
Rasterisierung, keine Android-Canvas-/GPU-/Filamentkosten; JIT kann Arbeit
eliminieren. Das ist ein begrenzter Allokations-/Dispatch-Vergleich, **keine
App-Framezeit und kein 60-FPS-Beweis**. Drei Glasschichten und Scanraster sind
fuer diesen Vergleich in beiden Versionen absichtlich identisch beauftragt;
die App reduziert zusaetzlich auf zwei/eine Schicht und blendet das Raster aus.

| Host-Probe | Bytes/Aufruf vorher -> nachher | p95 Mikrosekunden vorher -> nachher |
|---|---:|---:|
| HIGH | 4328 -> 232 | 6,630 -> 4,346 |
| Warm + Inferenz | 1664 -> 119,4 | 3,995 -> 1,021 |
| LOW (ein statischer Aufruf, keine App-Dauerschleife) | 704,8 -> 64,1 | 3,065 -> 0,341 |

JarvisRenderQuality bleibt alleinige Budgetquelle: HIGH 16 ms, maximal 24
Motes/3 Scheiben; warme Inferenz 80 ms, 6 Motes/2 Scheiben; LOW/SUSPENDED
keine Live-Dekoration, kein Bloom, 0 Motes und eine statische Scheibe.
Sichtbarkeit, echte Arbeit, reduzierte Bewegung, Close und Kill-Switch
muessen alle Live-Frames erlauben. Die Shell taktet; kein eigener Overlay-Timer.
Keine Aenderung der essentiellen Avatar-/Sprach-Policy.

## Grenzen und SOL-Abnahme

Die drei priorisierten Luecken sind im Android-Code geschlossen: korrekt
dimensionierte Materialien, Dekor hinter nativem Inhalt, gemeinsame Zeitbasis.
Beide #651-Review-Hinweise sind adressiert: Anker verschwindet vollstaendig;
Experiment-ID, Kill-Switch, Rueckfall und naechster Einstieg sind dokumentiert.
Keine fremden Review-Threads als erledigt markiert.

Sieben vorhandene Profile bleiben am selben Ereignispfad. Timeline-Zeilen
heissen jetzt SCHRITT statt erfundener AGENT-Instanzen; ein numerischer Nullwert
erzeugt keinen kuenstlichen Mindestbalken. Die Profile sind **nicht** sieben
neu fertiggestellte Fachanwendungen: Gedaechtnis nutzt derzeit Evidence,
Planung Swarm, Chrono zeigt echte Calculation-Daten. Die umfassende
Memory-/Timeline-Gestaltung, visuelle Abnahme der neuen Filament-Kernbeleuchtung,
Dialog-/Typografie-/Navigation-Ueberarbeitung und alle sieben Geraete-Workscenes
bleiben offen. Keine Ersatzavatararbeit.

SOL kann diesen gepinnten Vorschlag separat auf #653 oder den spaeteren
Reconciliation-Stand pruefen. Vor Integration: native UI-Suite ausfuehren,
Original-3D sichtbar + Blick/Blink/Visemen erhalten bestaetigen, Ankerherkunft
und Neigung am Fold7 filmen; alle sieben Profile mit wirklichen Ereignissen,
fontScale 2.0, beide Displays, Rotation/Fold, STOP, Hintergrund und Wechsel
des Kill-Switch waehrend Aufbau/Arbeit/Abbau pruefen. Je 2 Minuten Idle/Arbeit/
Modellinferenz mit Framezeiten, Jank, CPU/GPU, RAM und Temperatur erfassen.
Ohne diese Nachweise bleibt die filmische Produktabnahme offen.

## Autonomer Folge-Meilenstein: bestehendes Kernlicht

Nach dem gesicherten Fenster-Meilenstein wurde eine unabhaengige, kleine
Verbindung ergaenzt: Die **vorhandene** Cyan-Point-Fill von Filament erhaelt
dieselbe pro Frame einmal gelesene reale Energie wie die originale Kugel
(geglaetteter KI-/Sprachzustand und bestehende orbPulse/recallBrightness-Ereignisse).
Keine neue Lichtquelle, kein neuer Renderer, keine Uhr und kein Avatarcontroller.

`AvatarCoreLighting` bildet .18 Idle-Energie auf die unveraenderten 24000 lm ab,
aktive Energie weich und monoton auf maximal 30000 lm (+25%). Authored
Materialien, Morphs, feste Belichtung, Farbwerte, Lichtposition, Schatten und
Bloom bleiben unveraendert. Updates unter 1 lm werden nicht erneut an Filament
gesendet. Bei reduziertem Budget, reduzierter Bewegung oder ausgeschaltetem
zentralem Film-Schalter gilt exakt die alte statische Lichtstaerke.
Die funktionale Avatar-/Sprachverarbeitung bleibt aktiv.

Fuenf echte Kotlin-Tests pruefen Neutralwert/Fallback, monotone begrenzte
Arbeitsreaktion, Determinismus, nicht-endliche Samples und weiche Randsteigungen.
Die produktiven Filament-/HologramView-Klassen wurden gegen Filament1.56.0 und
Android-Framework typgeprueft. Gesamte JVM-Suite mit den neuen Klassen erneut:
**3556 PASS**, Beleg `core-light-junit.txt`. Das ist ein Anschluss-/Policybeleg,
kein Bildnachweis der Leuchte: Filament-/Fold7-Pixel und GPU-Kosten bleiben
NOT_RUN. Das Licht wird deshalb nicht in Java2D als echter Avatar nachgebaut.

## Regie-Folgeauftrag: den wirklichen Einflug verfolgen

Bei der erneuten Pruefung waren #651 weiterhin auf `09722adf6`, #653 auf
`cb11db2f9`, #658 auf `f153758df` und der eigene Licht-Checkpoint auf
`76b26e227`. Keine fremde Linie wurde veraendert.

Nachgewiesene Anschlussluecke: Der Host setzte bislang nur die **Endposition**
als Blickziel und aktualisierte sie bei einem Layout-Refit. Waehrend des
Einflugs sah der Avatar damit nicht auf die bewegte Scheibe.

`JarvisWorkWindow` meldet jetzt nach `applyFrame` die durch **View.matrix**
projizierte Scheibenmitte, inklusive nativer rotationY-Kameraperspektive,
Skalierung, Translation und Layoutversatz. Ein wiederverwendetes FloatArray
genuegt. Der Host normiert den Punkt im selben Overlay-Koordinatenraum wie
Kopf/Viewport und ruft ausschliesslich `AvatarDirector.windowMoved` auf.
Refit aktualisiert die Bezugsgeometrie vor der naechsten Projektion.
Keine neue Uhr, kein Polling und kein zweiter Kopfcontroller. Identische Ziele
erzeugen kein zweites Ereignis; die bestehende Lease wird nicht verlaengert.
STOP/Ende/Schliessen koennen durch folgende Projektionsframes nicht wieder
aufgehoben werden. Sprache behaelt Vorrang, ihr Ende verwendet das letzte
waehrenddessen bewegte Arbeitsziel.

Der bestehende Mapper liefert weiterhin die schnelle Augen- und langsamere
Kopffeder sowie den 65%-Halsanteil. Ein reiner Pipeline-Test prueft eine
sichtbare Seitendrehung im Bereich 15..30 Grad und fortgesetzte Augenbewegung
mehr als zwei Sekunden nach dem Einflug. Das ist **kein** Filament-Bildbeleg.
Zwei zusaetzliche native UI-Faelle pruefen echte Matrixprojektion, Uhrstillstand,
Docking, Reduced Motion und Live-Fallback; typgeprueft, Ausfuehrung BLOCKED.

Zweiter nativer Ausfuehrungsweg ohne AGP versucht: Standalone-Robolectric.
Die JDK21-Zertifikatskette wurde pro Prozess mit dem vorhandenen Host-JDK-
Truststore bereitgestellt (keine TLS-Pruefung abgeschaltet). Maven Central
ist erreichbar, aber AndroidX Monitor 1.7.2 und Espresso-Idling 3.6.1 bleiben
wegen DNS-Ausfall von Google Maven und dem getesteten Mirror unaufloesbar.
Beleg: `native-runtime-blocked.txt`. Kein nativer UI-PASS behauptet.

## Regie-Folgeauftrag: Arbeitsbeginn waehrend einer Bestaetigung

Zweite echte Anschlussluecke: `AgentActivity` meldet PDF-/Modell-/Toolarbeit
einmal ueber `workWindowHost.workStarted`. `AvatarDirector.windowWorkStarted`
hat diese reale Meldung aber waehrend LISTENING/SPEAKING oder einer aktiven
TTS-Utterance verworfen. Ein spaeteres Sprachende hatte dann keinen
Arbeitsanspruch, zu dem der Avatar haette zurueckkehren koennen.

Der **bestehende** `workIntent` nimmt den echten Arbeitsbeginn nun auch dann
auf. Das bedeutet nur Annahme der Praesentations-Lease, **nicht** sofortige
Blickuebernahme und niemals Ausfuehrungsfreigabe. Sprache/Zuhoeren behalten die
Augen. Der vorhandene Resume-Pfad uebernimmt danach die zuletzt projizierte
Position. STOP, echtes Ende, Fensterschliessen und TTL loeschen auch einen
solchen wartenden Anspruch. Keine Queue, kein Timer, kein Eventbus hinzugefuegt;
Inferenz und Werkzeuge werden nicht fuer die Inszenierung verzoegert.

RED gegen den vorherigen Director: 12 Tests, genau die vier neuen Faelle
scheitern. GREEN: 95 Avatar-/Blicktests PASS. Anschliessend gesamte JVM-Suite
mit neu kompiliertem Director, Window/Host, Kernlicht und deren Tests:
**3567 PASS**. Ein bisheriger Test erwartete absichtlich die alte Ablehnung
waehrend SPEAKING; er prueft nun Annahme **bei weiterhin ungerichtetem Blick**.
Das geaenderte Verhalten ist damit explizit geprueft, nicht stillschweigend
aus einem fehlenden Test entfernt.

### Weiter offene Regieanforderungen

| Forderung | Praeziser Stand |
|---|---|
| Gemeinsamer Raum | Geteilte projizierte Overlay-Koordinaten und bestehender normierter Rig-Vertrag; **noch keine** gemeinsame metrische Filament-/Android-Kamera oder z-genaue optische Blickrekonstruktion |
| Laufende Arbeit | Vorhandene reale Arbeits-Lease, Reading-/Sprach-/Idle-Morphs und budgetierte FUI-Liveframes bleiben aktiv; semantische PDF-Zeilen-/Codebereichsziele fehlen |
| Sprache | Vorhandene TTS-Ranges/PCM-Huelle und PP-Visem bleiben unveraendert; keine neue phonemgenaue Audioanalyse oder Geraete-Lipsync-Abnahme |
| Mehrere bedienbare Fenster | Host besitzt weiterhin ein aktuelles Fenster; hintere Dokumentebenen sind keine parallelen interaktiven Fenster |
| Abschluss | Reale Arbeit gibt die Blick-Lease frei; editierbare Ergebnisse/Bestaetigungen bleiben zugaenglich. Bestehendes Schliessen ist filmisch, kein neuer automatischer Inhaltsverlust |
| Sieben Arbeitswelten | Vorhandene echte Datenpfade erhalten; umfassende fachliche Live-Flachen und sieben durchgefilmte Owner-Auftraege offen |
| Vollstaendiger Abnahmefilm | BLOCKED: kein Android-Runtime-/Originalavatar-Render und kein Fold7; Java2D-Frames/Policytests ersetzen diesen Nachweis nicht |

## Fokusfortsetzung 09.11.: Original-Avatar / Animation / Stimme

Neuer Fokus ausschliesslich auf den Owner-Regieauftrag. Start/Remote geprueft:
`34b2aa1f498f08779596b36331e6e786983b3e12`; #658 weiterhin `f153758df`.
Master- und Fold7-offen-Referenz erneut tatsaechlich betrachtet.

### A: selektive innere Strahlung des Originalkopfs

Der GLB besitzt bereits die detaillierte Cyan-/Violett-Textur. Globale
Neonfarbe oder neue Linien wuerden gerade diese Struktur zerstoeren.
Die Loesung multipliziert daher nur die **authored Emission von DeepIndigoSkin**:
1.10x in Ruhe, weich bis maximal 1.32x mit der vorhandenen echten Kernenergie.
Die Basis wird aus dem GLB gelesen (derzeit RGB jeweils 1.35), nicht geraten;
Filament1.56.0 AssetLoader.cpp bestaetigt `emissiveFactor * emissiveStrength`.
Iris, Pupille, Mund, Masken, Geometrie, Morphs, Texturen und GLB-Bytes bleiben
unveraendert. Kein zusaetzlicher Pass/Licht/Timer, bestehendes Bloom unverstaerkt.
Unter Quality-Reduktion, Reduced Motion oder Film-Aus exakt authored Baseline.

Blender4.0.2 und fehlendes Importer-NumPy nach nachgewiesenen Toolfehlern
wiederhergestellt. **Original-GLB importiert**, keine Ersatzfigur. Drei identisch
gerenderte CPU-Frames: Baseline, 1.32x und verworfener 1.60x-Probe. Gleiche
Kamera/Beleuchtung/Seed/12 Samples, bestehender Fog-Glow-Hostpfad. Verglichen:
staerkere Stirn-/Wangenkonturen, weiter erkennbare Pupillen, kein pauschal
weiss gefaerbtes Gesicht. Die 1.60x-Probe verbreitert helle Cyanstellen zu stark.
ROI 90,65..295,325: mittlere 8bit-Luma 47.63 -> 52.69 (+10.6%),
weiss geclippte Pixel 0.053% -> 0.148%; 1.60x erreicht 0.238%.
Einzelkanal-Clipping steigt von 9.90% auf 17.13% (1.60x: 21.21%):
**Filament-Tonemapping/Belichtung muessen deshalb nativ nachgeprueft werden**.
Kein Nachweis neuer geometrischer Detailaufloesung oder Android-Pixelgleichheit.

Reproduktion:
`blender -b -t 2 --python-exit-code 1 --python tools/avatar-3d/reference-atlas/render_reference_avatar.py -- app/src/main/assets/avatar3d/continuum-avatar.glb OUTPUT --views front --samples 12 --surface-gain 1.32`
Analog Baseline 1.0 / Probe 1.6; dann
`python tools/avatar-3d/measure_radiance.py BASE/front.png BOUNDED/front.png PROBE/front.png OUTPUT`.
Evidenz: `docs/evidence/astra-avatar-focus-20261109/`.

### B: kontinuierliche Augen und verzoegertes Mitgehen des Kopfes

A gesichert: `2587fadd92fd480addffc771ce72ad994b0c679c`, Push/Remote identisch.
14 gezielte Asset-/Emissions-/Rigtests PASS.

Die bisherige Arbeitsaugenbewegung sprang beim Zeilenzyklus um **1.35 Grad
in einem Frame**, der Kopf blieb dabei nachweislich exakt starr (0 Grad
Spannweite). Auch das Entfernen des Lese-Offsets verursachte einen Sprung.
Alle drei Regressionstests zuerst RED auf dem unveraenderten Mapper.

Jetzt laufen horizontale Rueckkehr und vertikaler Wechsel ueber Minimum-Jerk,
der Arbeitsanteil blendet mit 180-ms-Zeitkonstante ein/aus. Die bereits
vorhandenen Augen-/Kopffedern erhalten denselben Offset, der Kopf folgt mit
18% Anteil verzoegert. Kein neuer Controller oder Zeitgeber.
Gemessen: groesster vertikaler Augenschritt bei 120 Hz **0.06493 Grad**
statt 1.35; Kopfspanne **0.8116 Grad**, Augenspanne **4.3049 Grad** auf
dem sichtbar gedrehten Arbeitsblick. 205 Avatar-/Originalrigtests PASS,
einschliesslich Wiederholzeit, Reduced Motion, Sprache und STOP.

Das ist **kosmetische Aufmerksamkeit innerhalb einer realen Arbeits-Lease**,
kein erfundener Beweis fuer die Verarbeitung konkreter Dokumentzeilen.
Das bestehende Blink-/Idle-/Visemsystem bleibt erhalten.

Der vorhandene Three.js-/SwiftShader-Pfad rendert das **unveraenderte
Original-GLB mit den exportierten Produktions-Mapperwerten**: je 147 Frames
vorher/nachher, 14.553 s Vergleichsfilm. Im Ablauf: Idle, Zuhoeren, Einflug-
Blickziel, fortgesetzte Arbeit, direkte Sprachunterbrechung, Arbeitsrueckkehr,
STOP. Frames bei 5.94/7.92/11.88 s betrachtet: seitlicher Arbeitsblick,
sprechende Frontalansicht, wieder seitlicher Arbeitsblick. Der Film ist
sichtbar als HOST / SCRIPTED EVENTS / NO AUDIO / NOT FILAMENT/FOLD7
beschriftet; keine echten Auftragsdaten oder Audioaufnahme werden behauptet.
Blender-Emissionsvergleich und Three-Motionsfilm haben verschiedene
Tonemapper; ihre Pixel werden nicht gegeneinander bewertet.

Reproduktion: `AvatarMotionEvidenceTest` mit
`-Dcontinuum.ui.evidenceDir=OUTPUT` ausfuehren; `avatar-work-timeline.json`
als `timeline.json` fuer `tools/avatar-host-render` verwenden. Three0.170.0,
Playwright1.56.1, vorhandenes `/usr/bin/google-chrome` (gestallten Browser-
Download abgebrochen, keine zweite Renderarchitektur). Kamera
`{"left":-0.165,"right":0.165,"halfHeight":0.22,"cameraCenterY":0.232}`,
384x512, Schrittweite3. Native Darstellung weiterhin separat abzunehmen.

### Fortsetzungsstand

Zentraler Praesentationsschalter: `JarvisCinematicSettings.KEY`,
`continuum-cockpit-presentation/cinematic-work-windows` (Standard true).
Aus bedeutet vorhandener einfacher Fenstereinflug ohne Film-Layer/Neigung und
die originale statische Cyan-Fuellleuchte;
kein Abschalten der Avatar-/Sprach-/Berechtigungsfunktionen.
Rollback nur eigener Commits, niemals Reset der SOL-/Fable-Branches.
Naechster Einstieg: die sieben nativen UI-Faelle auf einem autorisierten
Android-QA-Host ausfuehren, Blickprojektion am Original-GLB filmen und die
offenen metrischen Kamera-/Mehrfenster-/Live-Inhaltsanforderungen daran
weiterentwickeln. Kein weiteres subjektives Grafik-Abnahmeversprechen aus
Host-Policytests ableiten. SOL hat inzwischen #658 als separaten
Reconciliation-Kandidaten; dieser Branch bleibt bewusst auf dem beauftragten
#653 gepinnt. Keine fremden Aenderungen uebernommen oder ueberschrieben.
