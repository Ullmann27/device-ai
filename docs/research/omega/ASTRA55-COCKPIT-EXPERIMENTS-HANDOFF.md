# Code55: drei geprüfte Cockpit-Experimente

12.11.2026. PORTIERT in #804; VERIFIZIERT auf dem Host; GERÄTEBESTÄTIGT: NOT_RUN.
Alle drei Versuche haben einen Abschlussentscheid.

## Gemeinsamer Anschluss

Anfangsbasis: `5f87bdc610944b93d3173a83c5beeea315abeb66`.
Aktuelle integrierte Hauptbasis: `711f23b33a8fc602a3a0e4baa37aa61e42e6fc64`,
einschließlich des regulär übernommenen Android-Audits #803.

**#805 / #806 / #807 → #804 → #799 → bestehende #791.**
Gesamtbuild und Geräteabnahme der gesamten Code55-Linie bleiben beim Hauptintegrator.
Die Arbeitsbäume anderer Teams wurden ausschließlich gelesen.

| Unsere Spur | Partnerarbeit | Bestehender Vertrag |
|---|---|---|
| A / support_live / #805 | #803 Capture/Monitor, #799 Live-Computer | LiveMonitorFeed, CockpitLiveTexture, LiveMonitorSubmission |
| B / support_scene / #806 | #799 Filament-Szene, integrierte #801-Referenzkorrektur | FilamentCockpitStage, CockpitWorldTransforms, Originalgeometrie |
| C / support_workflow / #807 | #803 Eingabeprüfung, #799 Workflow | OwnerComputerWorkflow, Verifier, vorhandener Bus und Avatar/Cue |

Abstimmung: [Start](https://github.com/Ullmann27/device-ai/pull/799#issuecomment-6826869196),
[#803-Abgleich](https://github.com/Ullmann27/device-ai/pull/803#issuecomment-6827018323),
[Kombinationscheckpoint](https://github.com/Ullmann27/device-ai/pull/799#issuecomment-6827480793).
Ein veröffentlichter Kommentar ist kein behaupteter Live-Chat-Empfang.

## Ergebnisse und Entscheidungen

| Versuch | Vorab festgelegtes Gate | Ergebnis | Entscheid |
|---|---|---|---|
| A: Monitorparameter | ≥50 % weniger stabile Uniformaufrufe; gleiche Zustände/Bufferfolgen | 180→2 innerhalb 60 synthetischer Ticks; 42 Primär- und neun Holdout-Abläufe PASS | Reversiblen Produktionsadapter übernehmen |
| B: 3D-Transformationen | ≥20 % weniger Upload-Aufrufe; gleiche lokale/Weltmatrizen | Zwölf Phasen 108→78; Fahrt 954→642; 30 JUnit-Tests PASS | Reversiblen Produktionsadapter übernehmen |
| C: Aktionswirkung | Eine Eingabe je Ablauf; kein verfrühter Erfolg; Stop bleibt terminal | 36 Abläufe: 16 gültige Abschlüsse, 20 Negativkontrollen ohne Abschluss, 0 voreilige Erfolge; 49 JUnit-Methoden PASS | Regressionstest übernehmen; zusätzliche Produktlogik ablehnen |

A zählt nur frameAvailable/pointerAvailable/contentScale im Messfenster,
ohne Initialisierung und Abschluss. Ein unabhängig ergänzter Holdout fand
drei Fehler nach Teilwirkung vor einer Materialausnahme. Der erste Kandidat
wurde korrigiert: Cache vor Versuch entwerten, erst nach Erfolg übernehmen.
Rote Gegenproben bleiben erhalten. Beide A-Suiten bestanden erneut auf der
vollständigen gemeinsamen Produktionsklasse gegen die aktuelle #803-Basis.

B vergleicht 318.352 lokale und ebenso viele Weltmatrixkomponenten bitgenau,
zusätzlich 39.794 Projektionskoordinaten. Deaktivierung reproduziert alle
Baseline-Aufrufe. Alle 64 tatsächlich kompilierten B-Eingaben sind im
kombinierten Baum bytegleich. Die vier zusätzlich geänderten Schutzdateien
gehören zu #803/A; ihr Anschluss wurde separat gegen die neue Hauptbasis
geprüft. Ein neuer Blender-Lauf wurde nicht ausgeführt.

C verwendet Originalworkflow, Wirkungsprüfung, Bus, Avatar und Cue-Player;
OS-Port und monotone Uhr sind synthetisch. Nach Anpassung an expectedComposeField
blieben 36 Fälle, Gates und CSV gleich. Alle 27 Prüfeingaben entsprechen dem
kombinierten Baum. Bei Stop plus Freigabewiderruf lautet die Workflow-Rückgabe
PAUSED; Ereignis/Reducer bleiben STOPPED. Diese konkrete Grenze ist offengelegt.

## Gemeinsame Produktionsprüfung

Auf `5849b99ff641ee5e98238e5127c02e6e0cbf408e` wurden 53 vollständige
Produktionsdateien frisch kompiliert: Kotlin 2.4.0, Filament 1.56.0, Android SDK 36,
**PASS**. Quellen und Abhängigkeiten blieben stabil. Sämtliche 53 Quellhashes
passen auch zum anschließenden reinen Dokumentationsabschluss. Übrige
Abhängigkeiten stammen aus einem eingefrorenen bestehenden Integrations-JAR;
dies ist eine gezielte Kompilation, kein vollständiger Gradle-/APK-Build.

Genau drei zusätzliche Produktionsdateien:

- avatar3d/CockpitLiveTexture.kt: stabile Materialparameter zusammenfassen.
- avatar3d/CockpitTransformUpdates.kt: identische lokale Matrizen überspringen.
- avatar3d/FilamentCockpitStage.kt: vorhandene Transform-Aufrufe anbinden.

Die unabhängige Quellprüfung bestätigt: Nach Entfernen ausschließlich der
beiden Adapter sind die Rendererdateien exakt gleich der aktuellen Hauptbasis.
Submission, Frische, Viewport, Bufferfreigabe und Fehlerregeln bleiben erhalten.
Die echten Konstruktoraufrufer wurden geprüft und gemeinsam kompiliert.
Originalassets, Shader, Weltformeln und Clock wurden nicht verändert.

## Belege und Rücknahme

- [Gemeinsames Prüfregister](../../evidence/astra55-experiments-combined-20261112/integration.json)
- [Compiler, Rohtraces und unabhängiger Review](../../evidence/astra55-experiments-combined-20261112/)
- [A: Quellen und Gegenproben](../../evidence/astra55-experiment-a-20261112/)
- [B: Geometrie und Reproduktion](../../evidence/astra55-experiment-b-scene-20261112/README.md)
- [C: beide Läufe und API-Anschluss](../../evidence/astra55-experiment-c-workflow-20261112/RESULT.md)
- [Gemeinsame Ausführung](../../../tools/astra55-cockpit-experiments/README.md)

Recherche vor Implementierung: offizielle Android-/Filament-Quellen,
glTF-Spezifikation sowie Originalarbeiten ReAct und AndroidWorld. Abrufdaten,
konkrete Übertragung und Grenzen stehen bei jedem Versuch. Kein Fremdcodeimport.

Rücknahme A: coalesceUniforms=false; B: cacheTransforms=false. C ergänzt nur
Tests. Bestehende EXP-07/11/12-Verträge bleiben erhalten; das Hauptregister
verweist auf diese Belege. Keine neue Aufgabenverwaltung, kein Modelltraining.

Die Ergebnisse belegen Schnittstellenaufrufe und kausale Hostabläufe.
GPU-Zeit, Geräte-FPS, Thermik und Fold7 bleiben ungemessen. Kein allgemeiner
visueller Android-Planer wird behauptet. Nächste gemeinsame Abnahme ist der
bestehende Code55-Gesamtbuild mit realer Capture-/GPU-Messung.
