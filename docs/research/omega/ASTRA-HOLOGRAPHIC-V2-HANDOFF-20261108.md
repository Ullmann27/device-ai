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
fehlendem SDK/Google-Maven noch NOT_RUN. Finale Wiederholung folgt.

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
`839017298f78d825d82118f359c5d56874bfae57` (abgenommene Host-Evidenz).
Jeweils Commit -> Push -> `git ls-remote` mit identischem SHA ausgefuehrt.
Finale Evidenz liegt in `docs/evidence/astra-holographic-v2-20261108/`.

| Pruefung | Ergebnis / Grenze |
|---|---|
| Material-RED | 5 Tests, 4 reproduzierte Fehler auf unveraendertem Baseline-Painter |
| Material-GREEN | 9/9; Anker-Ausblendung, Close-Puls, scheibengrosse Gradienten, konstante Shader-/Path-Identitaeten und harter 24-Motes-Deckel |
| Vollstaendige Kotlin-Main-/Test-Kompilation | PASS, JDK21.0.13 / Kotlin2.2.21; bestehender compile-only LiteRT-Stub, kein echtes LiteRT |
| Vollstaendige JVM-Regression Fenster | 632 JUnit-Klassen, 3551 Tests PASS; vier ausfuehrbare Checks PASS (fuenfter Helper hat kein main) |
| Folge-Meilenstein Kernlicht | Zusaetzliche Filament-/View-Typpruefung und 5 neue Policytests, zusammen 3556 JVM-Tests PASS |
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

## Rueckfall / naechster Einstieg

Zentraler Praesentationsschalter: `JarvisCinematicSettings.KEY`,
`continuum-cockpit-presentation/cinematic-work-windows` (Standard true).
Aus bedeutet vorhandener einfacher Fenstereinflug ohne Film-Layer/Neigung und
die originale statische Cyan-Fuellleuchte;
kein Abschalten der Avatar-/Sprach-/Berechtigungsfunktionen.
Rollback nur eigener Commits, niemals Reset der SOL-/Fable-Branches.
Naechster Einstieg: finale Host-Frames und Regressionen; danach autorisierter
Android-QA-Host und Fold7-Abnahme. SOL hat inzwischen #658 als separaten
Reconciliation-Kandidaten; dieser Branch bleibt bewusst auf dem beauftragten
#653 gepinnt. Keine fremden Aenderungen uebernommen oder ueberschrieben.
