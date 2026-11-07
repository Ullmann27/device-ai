# Code48: verbindlicher Einstieg nach der Animationsintegration

## Quelle und aktueller Status

Buildquelle: **`5d91442e98f78a2af623b8a3c74920805f670cb9`**.
Baum: `ae79915e7b6df784f0ba78ead4a44b44dedec08a`.
PR591 baut auf PR590 / `d73fc46b5a14ea5fc3b82a3bd3b96cacebe7d4de` auf.
Branch: `codex/continuum-code48-fullstage-cinema-20261107`.
Spätere reine Beleg-/Dokumentationscommits sind nicht die APK-Buildquelle.

<!-- FINAL-STATUS-START -->
**5d-Gesamtabnahme: FAIL.** 3.776 Androidtests: 3.754 bestanden, 20
übersprungen, 2 fehlgeschlagen; keine Errors. Beide Kotlin-Kompilierungen
abgeschlossen. Lint wurde nach dem Unit-Test-Fehler nicht erreicht.
Node vollständig (2.398 PASS / 31 SKIP), Critical (135/135), Backend (64/64)
und Sonderfälle (23/23) waren auf unveränderter 5d-Quelle bestanden.

Die beiden roten Assertions wurden separat auf unveränderter 5d-Quelle
reproduziert: drei selektierte Tests, zwei FAIL. Die aktuelle begrenzte
Testanpassung ist WIP und noch nicht GREEN: Der Menütest muss tatsächliche
Logic-/Web-Arbeitsfenster und den Tasks-Dialog prüfen; der Recalltest muss
die tatsächliche Home-Vollfläche zeichnen. Kein Produktionscode wurde geändert.
Vollständiger alter FAIL, XMLs, Skips, Selektorversuch und fokussiertes RED:
[Suite-Anpassung](../../evidence/code48-suite-alignment-20261107/).
Die endgültige APK, Signierung und Geräteabnahme bleiben offen.
<!-- FINAL-STATUS-END -->

## Verbundene Produktpfade

- **Dokument-/Gedächtnisszene:** positive Dokument-/Recall-Ereignisse gelangen
  über die vorhandene Sequence-Kette zu `Video3WarpScene` und dem Home-Host.
  `Video3MemoryScene` komponiert 4.800 ms Papier-/Ordnerflug, große Originalkugel
  und Rückkehr. Derselbe Renderer, Liveframe und Callback bleiben zuständig;
  der kleine bestehende Fallback endet nach 3.000 ms.
- **Originalkopf:** bestehende Cue-Kette, Eintritt/Verkleinerung und begrenzter
  Icon-Burst sind verbunden. App-/Pane-Wechsel, STOP, Detach, reduzierte Bewegung
  und thermische Richtlinie begrenzen die Darstellung. Gepufferte alte
  Gedächtnisereignisse behalten ihre Generation und spielen nach STOP nicht neu.
- **PDF:** tatsächliche Chat-Anfragen öffnen Home und das echte Arbeitsfenster.
  Eine akzeptierte Szene wartet auf dessen frisches Layout; STOP verwirft sie.
- **Hinweiskarte:** vorhandene öffentliche Home-API erhält korrekte Shell-
  Geometrie/Layout und Z `.75 * density`, über Szene/Artwork und unter den
  echten Bedienelementen. Kein neuer CalculationShown- oder doppelter
  Account-/Dokument-Presenterpfad wurde eingeschaltet.
- **Training:** reproduzierter CDX-Abbruch nach Lock-Wartezeit wird anhand
  typisierter Cancellation und bestehendem Grund als DEFERRED/TIME_BUDGET bzw.
  INTERRUPTED erfasst. Unabhängige I/O-Fehler bleiben FAILED, geloggt und
  retryfähig, auch bei gleichzeitig abgelaufenem Budget. Trainingsschritt,
  Gewichte, Checkpoints, Qualitätsgrenzen und Ressourcenlimits unverändert.
- **Dashboardlast:** teure Status-/Datei-/Holdout-Erfassung erfolgt bei
  sichtbarem Cockpit bzw. Hardwarefenster; Rückkehr fordert frische Daten an.
  Keine gemessene Temperatur- oder Modellgeschwindigkeitssteigerung behauptet.

Experimentbezug: EXP-02/07 Regression/Evidenz, EXP-11 Ereignisverbindungen,
EXP-12 Lebenszyklus; EXP-04/05/12 Lern-/Checkpointgrenzen bleiben erhalten.
Alle 26 eingecheckten Assets und die öffentlichen Face-Modellpins sind
gegenüber der Code47-Quelle `7732b8668f912419d7aac7a1609a97097d727cbf`
bytegleich. Gerätezustand und private Modellgewichte wurden nicht gelesen.

## Befehle und Belege

Die vollständig ausgeführten Argumentlisten, Arbeitsverzeichnisse, Start-/End-
Zeitpunkte, Quellzustände und Loghashes stehen in jedem Gate-Receipt unter
[`code48-final-qa-20261107`](../../evidence/code48-final-qa-20261107/).
Die zugehörigen ZIPs enthalten unveränderte vollständige Logs, Originalreports
und XMLs; jede Summary nennt deren einzelne Hashes und den Archivhash.

```sh
node scripts/continuum-reliability-qa.mjs full-node --out <evidence>/full-node
node scripts/continuum-reliability-qa.mjs critical --out <evidence>/critical
cd backend
npm ci --ignore-scripts --no-audit --no-fund
npm run check
npm test
```

Die Sonderfälle verwenden `node --test` mit genau diesen sechs Dateien:
`scripts/continuum-atomic-acceptance.test.mjs`,
`scripts/continuum-gguf-preflight.test.mjs`,
`scripts/continuum-gguf-preflight.integration.test.mjs`,
`scripts/continuum-owner-v38-release.test.mjs`,
`scripts/continuum-owner-continuous-release.test.mjs`,
`scripts/continuum-owner-backend-gate.test.mjs`.

Gradle-Basis: `gradle --no-daemon --max-workers=2 --console=plain
-Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m"
-Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true
-PcontinuumStandaloneVersionCode=48`.

- Android ergänzt `--rerun-tasks -PcontinuumUiWidgetTests=true
  -PcontinuumLocalStandaloneQa=true :app:compileDebugKotlin
  :app:testDebugUnitTest :app:lintDebug`.
- Unsigned ergänzt `-PcontinuumLocalRenderUnsigned=true
  -PcontinuumOwnerArm64=true :app:assembleRelease :app:bundleRelease`.

**Android und unsigned seriell mit getrennten Gradle-Homes ausführen.** Der
erste ältere unsigned Versuch traf einen gemeinsamen Journalcache-Lock; der
ältere Android-Daemon verschwand. Seine genaue Ursache ist nicht bewiesen.
Ein isolierter Android-Wiederholungsversuch wurde für die Hinweisreparatur
bewusst beendet. Diese FAIL-/Unterbrechungsbelege bleiben unter
`superseded-3ff/` erhalten. Die dortige erfolgreiche unsigned APK ist ausdrücklich
ein Zwischenartefakt der älteren 3ff-Quelle, kein endgültiger 5d-Build.

## Sichtbeleg und Grenzen

Die native Activity-Bildfolge zeigt Dokumentflug, große vorhandene Kugel und
Rückkehr; die private Referenz bleibt außerhalb des öffentlichen Repositorys.
Der Export enthält 24 Samples im Abstand von 220 ms, keine FPS-Messung. Seine
Bezugsquelle ist 3ff vor der späteren Hinweisreparatur. Die gesamte Filmvorlage
ist damit nicht 1:1 nachgebildet: Dokumentdichte, Lichtgestaltung, Montage und
Format unterscheiden sich. Native Canvas-Bilder beweisen weder Filament-GPU-
Pixel noch einen fehlenden Geräte-3D-Pfad. Physisches Fold7, Sprache,
Bildrate, Temperatur, Akku, tatsächliche Modellinferenz und der konkrete
aktuelle Handy-Trainingsfehler sind weiterhin nicht durch diesen Host belegt.

Für die verbleibende Trainingsdiagnose werden installierte Version, Kanal
(PUBLIC/OFFLINE/CDX/NEURON), Phase, Fehlerdetail und Zeitstempel des vorhandenen
Monitors benötigt. Keine privaten Lerntexte oder Gewichte exportieren.

## Zweigsuche

[Vollständiger Bericht](CODE48-BRANCH-SEMANTIC-AUDIT-20261107.md): 653 Heads
inventarisiert, 96 geschlossene PR-Heads abgeglichen; 56 Android- und 69 weitere
historische Pfade vollständig zugeordnet. Die vier getrennten State-/Checkpoint-
Historien sind ausschließlich anhand Metadaten geklärt. Fehlende historische
Callerobjekte und Forschung ohne Produktcaller sind ausdrücklich gekennzeichnet.
Kein pauschales „alle Zweige integriert“. Größere Folgeintegrationen nicht in
die eingefrorene APK-Quelle hineinschieben oder alte Gates erneut öffnen.

## Nächster konkreter Einstieg und Veröffentlichung

<!-- NEXT-STEP-START -->
Die ausschließlich zwei Testdateien betreffende Anpassung zuerst auf exakt
veröffentlichtem Quellstand fokussiert prüfen. Echte Zielviews, Titel,
Sichtbarkeit, Schließen, Event-/Generation-/Pixelassertions sowie unveränderte
Request-/Chief-Turn-Grenzen erhalten. Erst nach GREEN den neuen Stand einfrieren
und seine vollständigen Gates ausführen; 5d-FAIL nicht zu PASS umetikettieren.
Android und anschließende unsigned APK/AAB weiterhin seriell ausführen.
<!-- NEXT-STEP-END -->

Der bestehende Originalpublisher und Code47-Livekatalog wurden nur gelesen.
`release-preflight/` enthält die geprüfte Service-/Branchzuordnung, den
Zwölf-Felder-Katalog und einen ausdrücklich **PREPARED_NOT_EXECUTED**-Plan.
Code48 erfordert eine Besitzerfreigabe der exakten 5d-SHA plus Freeze-Aufhebung.
Die frühere Code38-/Code47-Freigabe gilt nicht automatisch dafür. Das historische
Updatekanal-Dokument nennt teilweise noch Code38; der aktuelle Dispatcher
unterstützt Code ≥39 über den gesondert geprüften continuous-release-Einstieg.

Nach tatsächlicher Freigabe zuerst Publisher, `autoDeploy=no`, Releasebranch
und Livecode neu lesen; dann ausschließlich erlaubtes Fast-forward mit
Alt-SHA-Prüfung, nur die drei öffentlichen Freigabe-/Versionsfelder zusammenführen,
Queue prüfen und gegebenenfalls einmal deployen. Atomare CAS-Eigenschaften
werden für diesen API-Ablauf nicht behauptet. Original-P12/Passwörter bleiben
im Publisher. Dessen eigene vollständige QA läuft vor dem Signierschritt.
Anschließend tatsächlich gelieferte APK/Katalog/Originalsignatur unabhängig
prüfen. Signierte und unsigned APK haben unterschiedliche Hashes.
Keine Installation durch diesen Ablauf; Android verlangt Besitzerbestätigung.

## Rückfall und Fortsetzung

Szenen-/Layoutpfade: `ContinuumHomeView`, `ContinuumHologramView`,
`Video3MemoryScene`, `JarvisSequenceView`; Regressionen in
`FullStageMemoryReviewUiTest` sowie den bestehenden Activity-/Dokumenttests.
Training: `ContinuumAutonomousJobService`, `ContinuumCdxLargeLab` und
`ContinuumTrainingCancellationUiTest`.

Eine erforderliche Reparatur auf einem klar benannten Folgecommit isolieren;
betroffene echte Regression reproduzieren und die dadurch ungültigen Gates
ehrlich neu binden. Keine Testtoleranzen oder Qualitätsgrenzen lockern, um PASS
zu erzwingen. Bei Rücknahme nur den begrenzten Szenen-/Host-/Hinweispatch per
normalem Revert entfernen; PR590-Verbindungen und Trainings-/STOP-Schutz
bewahren. Keine gemeinsam genutzte Historie zurücksetzen, keine Branches,
Modelle, Daten oder Assets löschen, kein Main-Merge, keine GitHub Actions.
