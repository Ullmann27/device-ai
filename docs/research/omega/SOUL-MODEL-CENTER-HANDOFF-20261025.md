# SOUL-Übergabe: Modellzentrum, Fold7-Oberfläche und ORION-47-Abnahme — 25.10.2026

## Branch und PR
Branch: `feature/continuum-model-center-selected-models-20261025`
Draft-PR: https://github.com/Ullmann27/device-ai/pull/148
Basis: `integration/continuum-soul-infrastructure-20261025` / PR #147.
Haupt-App und Besitzerpaket bleiben `at.heinz.deviceai.mentorpaired`. Kein Main-Merge, keine Besitzer-APK, kein Download, keine Geräteinstallation, keine Modellgewichte verändert.

## Nachweisbare neue Anschlüsse
- `ContinuumSelectedModels.kt`: feste, aus dem bestehenden `config/continuum-selected-models.json` übernommene Modellidentitäten, Gemma-Importversion, SHA-256, offizielle Modellseiten; tatsächlicher Gemma-Ladestatus getrennt von der bloßen Modellwahl; DeepSeek ohne Server ausdrücklich `nicht verbunden`. Keine Toolrechte.
- `AgentActivity.kt`: vorhandener Modellzentrum-Dialog enthält ausgewählte Modelle. Gemma-Origin öffnet nur Android ACTION_VIEW; kein automatischer Download. Importfelder für Gemma werden auf explizite Besitzeraktion vorbefüllt, die bestehende SHA-256-Importprüfung bleibt unverändert. Der Modellstatus wird nach Import/Load/Unload aus der tatsächlichen Runtime aktualisiert. Importdialog nutzt denselben Hologlas-`ContinuumBentoCockpit` wie die Haupt-Menüs.
- `ContinuumSelectedModelsTest.kt`, `scripts/continuum-model-center.test.mjs`: Kotlin-Vertrags- und Quellverdrahtungsprüfungen.
- `ContinuumStatusExcerpt.kt`: zeigt lange Lernstatusmeldungen mit vollständigen Wörtern und `…` statt des bisherigen `lastStatus.take(110)`-Abbruchs mitten im Wort. Im Chat steht die Messwert-Zeile getrennt vom Statustext. `ContinuumStatusExcerptTest.kt` dokumentiert den Vertrag. Kein Einfluss auf Gewichte oder Lernalgorithmus.

## Stand der Tests
- Quellabgleich direkt über GitHub: 17/17 strukturelle Gleichheits-, Verknüpfungs- und Sicherheitsprüfungen für Modellidentität/Import/Runtimebindung; dies ist keine Android-Kompilierung.
- Reale Kotlin-Compiler-Prüfung eines byteidentischen, aus GitHub übernommenen Dateisatzes:
  - `ContinuumSelectedModels.kt` Git-Blob `c421156300b9c8fcfe4fa80bd2f61a43edaf0ef8`; `kotlinc`-Kompilation und sechs gezielte JVM-Laufzeitprüfungen PASS.
  - `ContinuumStatusExcerpt.kt` Git-Blob `a46f1832c9fc460c5d4d801e86012b5af141ac03`; `kotlinc`-Kompilation und sechs gezielte JVM-Laufzeitprüfungen PASS.
- **Nicht gelaufen:** die neuen JUnit-Tests auf Android Gradle, allgemeine Node-Test-Suite auf vollständigem Checkout, `AgentActivity.kt` Android-Kompilation, Render-Android-Lint, Fold7-GUI, reale Gemma-/DeepSeek-Inferenz, Signierung und Besitzer-APK. Entsprechender Render-Dienst konnte wegen bestätigtem Hobby-Limit von 25 Diensten nicht erzeugt werden (HTTP 400). Bestehende Render-Dienste wurden nicht gelöscht oder umkonfiguriert.

## Gerätebeobachtung und offene Defekte
Ein Benutzer-Screenshot zeigte funktionierendes explizites Speichern und Widerrufen eines Testworts (ORION-47) und korrekte Reproduktion bei `Was weißt du?`. Ein früheres Gerätebild zeigte aber fälschliche Faktenrückgabe bei `Was ist Orion?`, da das alte Gerät den persönlichen Wert als allgemeine Definition ausgab. Die Quellenreparatur `ContinuumMemoryDialogue.ownerMemoryAnswersDefinition` mit `ContinuumMemoryDialogueTest.orion47RecallKeepsExactDigitsButDoesNotInventDefinition` ist bereits in Parent PR #143 enthalten, aber für die bisher installierte APK **nicht** bestätigt. Dieser neue UI-Branch erhebt keinerlei Geräte-Fixbehauptung.

## Folgeschritte
Vorrangig echten `AgentActivity`-Gradle-JUnit-/Lint-/Kompilationslauf auf einem vorhandenen autorisierten Worker durchführen. Danach auf dem Fold7 das Modellzentrum, den Hologlas-Import, Laufzeitstatus, die abgeschnittene Lernbanneranzeige und den Orion-Fragetyp abnehmen; Qwen nur nach positivem Gemma-Modelltest löschen. Genaues SHA, Paket, Signer und APK-Aktualisierung separat freigeben. Die DeepSeek-Virtual-Model-Fabric bleibt nach PR #147 unverändert, noch ohne GPU und Inferenzserver. Sicherheitsgates aus `AGENTS.md` und Issue #41 bleiben bestehen.
