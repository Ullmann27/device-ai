# Device AI Continuum – unabhängiger Argon-Review und begrenzte Implementierung
Stand: 21.10.2026 · isolierter PR-Zweig, **keine Änderung an PR #44, Main oder der installierten APK**

## Gesicherter Review gegen tatsächlichen Kotlin-Quellcode
1. Die bisherige CDX1-Paarannahme speichert Decoder-Checkpoint und SWB1-Snapshot-Bindung getrennt. Eine Unterbrechung nach dem ersten Write kann sie inkonsistent machen. Das ist eine echte Korrektheitslücke.
2. Der CDX1 hat **HIDDEN=16**, **VOCAB=258**, CONTEXT=8, EMBED=8. Das Output-Projektionsmodul hat 16 × 258 + 258 = 4.386 von 7.746 Parametern, nicht die im Argon-Text hypothetischen 82,6 %. Das enge Kontextfenster und die statische 16-dimensionale Konditionierung sind reale Generalisierungsgrenzen.
3. Die vorgeschlagene "orthogonale Prokrustes-Brücke" ist tatsächlich Ridge-Regression ohne Orthogonalitätsnebenbedingung. Ohne unabhängige Ankerdaten und Verlusterhebung garantiert sie keine exakte Koordinatentransformation. **Nicht integriert.**
4. Der vorgeschlagene StageRunner würde u. U. **teilweise trainierte Epochen als vollständig übernommene Stufe** speichern. Seine 2.500-ms-Reserve garantiert keine harte Deadline für unbekannte Audit-/fsync-Dauern. **Nicht integriert.**
5. Die vorgeschlagenen festen Hardware-Latenzen, Android-Core-Zuweisungen und CCS-Grenzwerte sind bisher keine gerätemessungsbasierten Tatsachen. Ein altes Heartbeat-Alter beweist nicht, dass ein Prozess beendet ist; es belegt nur veraltete Telemetrie.

## Implementiert: einzelnes atomisches Paar-Bundle statt fehleranfälliger rotierender Slots
Neue Klasse: ContinuumAtomicPairedBundleStore.kt
- Speichert **validierte CDX1-Gewichte + exakt gebundenen SWB1-Encoder in EINER Datei** context-decoder-paired.bundle; SHA-256 über vollständigen Inhalt, strukturierter Header und strenge Größen-/Modellvalidierung.
- Die bisher aktive, verifizierte Generation wird vor dem Austausch als eine eigene atomar veröffentlichte, vollständig verifizierbare .bundle.lkg bewahrt. Der aktive Dateiaustausch erfolgt nur per ATOMIC_MOVE. Fehlt diese Dateisystemfunktion, wird **abgebrochen statt auf einen unsicheren Move auszuweichen**.
- Ein Prozessabbruch vor der Veröffentlichung lässt den bisherigen aktiven Stand unverändert; Crash nach atomarem Austausch zeigt auf das komplette neue Paar. Eine defekte aktive Datei kann den letzten validen Bundle-LKG nur lesend wiederherstellen. Über defekte Primärdaten hinweg wird **kein Training zugelassen**, um nicht unbemerkt eine Geschichte zu überschreiben.
- Synchronisierung im Prozess sowie ein OS-Dateilock und generationengebundener Optimistic-Concurrency-Check verhindern blindes Überschreiben durch einen zweiten Trainings-Commit.
- Alte Dateien context-decoder-paired.cdx und context-decoder-paired.swb1-binding und private Besitzer-/SWB1-Daten bleiben unverändert. Wenn noch kein Bundle existiert, verwendet der Decoder weiterhin die bestehende Legacy-Ladelogik. Die erste akzeptierte neue Paartrainingsrunde migriert **nur den neuen Forschungscheckpoint**, ohne alte Daten zu überschreiben.
- Android-Lernansicht kennzeichnet die Wiederherstellung als RECOVERED_LKG und verhindert verdeckte Chat-Promotion.
- Regressionstests: simulierter Abbruch vor Publish, unveränderte Legacy-Dateien, beschädigte Primärdatei mit LKG-Recovery, Sperre nach Recovery, stale-generation-Reject und symbolischer Link als unerlaubter Payloadpfad.

## Bekannte Grenzen / offene nächste Arbeit
- Das separate CDX1-Curriculum-Stage-Cursor-File ist weiterhin **nicht** gemeinsam mit dem Modell atomar. Ein Crash nach Annahme, aber vor Cursor-Speicherung kann eine Stufe wiederholen; noch nicht als vollständig gelöst betrachten.
- Einzelne Datei-ATOMIC_MOVE/Datei-fsync garantieren ohne verifizierte **Directory-fsync-/Power-Loss-Eigenschaften des Zielgeräts** keine absolute Persistenz bei vollständigem Stromausfall. Die Tests simulieren Ausnahmen, nicht tatsächliche Power Cuts.
- Die neuen Kotlin-JUnit-Tests müssen auf dem aktuellen neuen Commit kompiliert und ausgeführt werden; weitere Android-Build-, Signatur-, Instrumentations- und Samsung-Lerntests fehlen.
- SWB1↔CDX1 bleibt einseitig konditioniert; wir behaupten ausdrücklich keine neue Generalisierung, selbstständige Sprachkompetenz oder neue ANSK-Fähigkeiten.
- Das existierende 15-s-Jobbudget und 5-s-Trainingsbudget bleiben bis zu belastbaren Samsung-Laufzeitmessungen **unverändert**. Weder automatische Wiederaufnahme unterbrochener Gradientensequenzen noch ein budgetfester Epochenrunner wurde heimlich aktiviert.
- Es gibt keine externe kostenpflichtige KI-API und keinen automatischen Produktionsmerge.

## Freigabegate
Node-Wiring-Tests, bestehende und neue Decoder-JUnit-Tests, vollständige Gradle-Kette :app:testDebugUnitTest :app:lintDebug :app:assembleDebug; Status und APK-Identität prüfen. Danach Samsung-Installation **als Update**, echte Generation-1-/Generation-2-Läufe und provozierte Unterbrechungen prüfen. Erst danach die nächste Forschungsänderung freigeben.
