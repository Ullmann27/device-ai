# Continuum – Wiederaufnahme nach Sols Cloud-Ausfall, 24.10.2026

## Wiederherstellung und Quellstand
Ausschließlich `Ullmann27/device-ai`. Offener Sol-Zweig `sol/continuum-hologram-intelligence-20261024` hat laut Remotevergleich **0 Commits** vor `sol/continuum-max-20261024`, beide auf `f65181fe87244071ddb3286eccd6f11a23da2e52`. Sols Bildschirmausgaben zeigen lokale Arbeit an Animation und Gesprächskontext, aber weder ein Push noch den Abschluss der letzten Runner-Prüfungen. Nicht gerettete Cloud-Dateien werden nicht als integrierter Quellcode behauptet.

Ausgangspunkt dieser unabhängigen Fortsetzung ist exakt `f65181fe87244071ddb3286eccd6f11a23da2e52`. Branch `chatgpt/continuum-hologram-context-recovery-20261024`; Review-PR #113 gegen den unveränderten Sol-Branch. Fremde Branches, `main`, Render, Secrets, Besitzergewichte, Signierer und Besitzerpaket wurden nicht verändert. **Keine privaten lokalen Patches nötig, um den GitHub-Stand zu übernehmen.**

## Implementiert und unmittelbar gepusht
1. `078af0d4`: ContinuumHologramView akzeptiert expliziten Activity-Vordergrundzustand; Hintergrundanimation stoppt unabhängig von Fenster-Sichtbarkeit, aktive Frame-Callbacks werden entfernt.
2. `dba9ce8f`: AgentActivity.onResume/onPause verbindet den Activity-Lifecycle mit der Orbitaluhr.
3. `38ee98e7`: ContinuumCognitiveRuntime verwendet nicht länger `core.dialogueHistory()` (profilweiter Verlauf ohne Konversations-ID) als Quelle des lokalen Modellprompts; stattdessen ausschließlich die vom bestehenden Dialogkernel gelieferte aktuelle `turn.resolvedQuery`, mit Rückfragehinweis bei unaufgelöster Referenz. Kein CTX3-Promotion und keine Gewichtsänderung.
4. `e8f345f0`: neue JVM-Regressionen für übergreifenden Themenleckversuch und bewahrten Anschlusskontext.
5. `0406ed25`: Weiterentwicklung der Orbitalphase durch den bestehenden einzelnen Frame-Callback statt Canvas.onDraw; `postInvalidateOnAnimation()` fordert Rendering an, ohne von einem abgeschlossenen Draw abhängig zu sein. Vorhandene Ringprojektionen, Schwebehub, reduzierte Animationen und Stromsparmodus bleiben erhalten.
6. `e014b3db`: neuer Node-Quellvertrag für Hologramm-Lifecycle/Frame-Takt/Prompt-Herkunft.
7. `3eaec8db`: Neu aufgebaute Startseite übernimmt im laufenden Activity-Zustand sofort die Vordergrundberechtigung (Fold-, Start- und Chat-Umbau).
8. `7b52aea5`: Quellvertrag für diesen Fold-/Home-Recreation-Pfad ergänzt.

## Validierung und ehrlicher Stand
Am tatsächlichen gepushten Code wurden via GitHub-Connector 20 spezifische Quellinvarianten ausgeführt, **20/20 erfüllt**: Single Callback, Tick unabhängig vom Canvas, Foreground/Resume/Pause, Reset/Powerpolicy, Fold-Recreation, neuer sicherer Promptvertrag und Testquellen. Das ist **kein** Lauf von `node --test`, Gradle, Robolectric, Android-SDK oder Gerät.

Node v22.16 und Kotlin 1.9 sind lokal erreichbar; echtes Android SDK/Gradle liegen in der ChatGPT-Containerumgebung nicht vor, GitHub ist dort direkt per Git DNS-unerreichbar. GitHub-Actions-Runs aus der vorigen Runde scheiterten vor Runner-Schritten; keine grünen Runs für **diese** neuen Commits nachgewiesen.

Frühere vollständige, unabhängige SDK-Testbelege gehören ausschließlich zum unmodifizierten Sol-Code `ec28d661e27090c91a3eb83707bf5f46376b97b5`: Node 1099, Android/JUnit 940 einschließlich 26 Robolectric-Fälle, Gradle-Unit/Lint/Preview erfolgreich. Sie ersetzen keine Regression gegen diesen neuen HEAD.

## Release-Sperren
Keine Besitzer-APK 0.3.18/code19 gebaut oder signiert. Das bisherige v19-Pin-Manifest hatte 11/22 abweichende attestierte Quellen plus zehn unpinnte APK-Quelländerungen. **Erneuter konkreter GitHub-Abgleich auf diesem Branch:** 9 von 22 Pins passen, **13 von 22 weichen ab**. Zusätzlich bleiben die zehn bislang nicht attestierten APK-Quelldateien aus Sols unabhängigem Review zur Freigabe offen. Die vormals passende Signierquelle `ContinuumHologramView.kt` änderte sich überprüfbar von freigegebenem Git-Blob `8b99c6dae6626d2ed251bb723d75ed0a9ba3b8a3` auf aktuellen Blob `071af65313d79c0840bf3d772b4cac3dc8e09825`, um den Frame-Takt und Lifecycle zu reparieren. `AgentActivity.kt` hat aktuellen Blob `2faaaba60a850ab75c911c1dbcc476956db23093`; sie gehört ebenfalls zur 12er-Reviewgruppe. `ContinuumCognitiveRuntime.kt` ist eine der zehn nicht attestierten Quelldateien und trägt nun Blob `bd2635ed6e5bf5bf051bc3125365fd928c7853a0`. Zusätzlich wurde der Schwebehub der bisher attestierten `ui/ContinuumOrbitalRing.kt` von ±2,4 % auf sichtbarere ±5 % angehoben; alte Blob-SHA `412d643296deb76ab07e9515f6881eba9df3a9b4`, neuer Blob `35a92c7a8587e22d6e5dfaa93550407371d5e304`. Der vorhandene Umfang-/Bewegungs-Regressionscheck wurde entsprechend angepasst. **Keine** dieser Fingerabdrücke wurde in das Signierskript eingetragen oder damit genehmigt. Es gibt weder einen neuen Besitzer-Hash noch einen Chrome-Download. Das v18-Rollback ist im bisherigen Sol-Bericht bereits unabhängig über Hash/Zertifikat belegt, wurde in diesem Lauf nicht erneut heruntergeladen.

Die Freigabe von geänderten Source-Pins und neuen APK-Quellen erfordert eine konkrete ausdrückliche Besitzerentscheidung. Weder Pins noch Signier-SHA oder private Render-Umgebung dürfen ohne diese Freigabe geändert werden.

## Als Nächstes – kürzester sicherer Weg zur APK
1. Branch-HEAD und PR #113 abgleichen. Vollständige `node --test`-Suiten inklusive `continuum-hologram-context-wiring.test.mjs`, Backend, JVM-/Kotlin-/Robolectric- und Gradle-Lint-/Assemble-Gates auf dem **aktuellen HEAD** ausführen; die zwei neuen Kontexttests und das Hologramm-Clock-Verhalten auf realen Android-Widgets kontrollieren.
2. Den aktuellen v19-Auditor erneut ausführen. 22 geschützte Pfade, zusätzlicher unpinnter Android-Code und `AndroidManifest.xml` konkret diffen; sicherheitsrelevante Änderungen mit Tests belegen, Quellen nur nach ausdrücklicher Besitzerfreigabe neu attestieren.
3. Nach Freigabe gepinnte Render-Releasequelle kontrolliert übernehmen und alle Gates erneut auf genau dem Release-Commit ausführen. Keinen anderen Signierer/andere App-ID verwenden. Ziel `at.heinz.deviceai.mentorpaired` / `0.3.18` / code19 / Zertifikat `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`.
4. Besitzer-Signierdienst nur nach allen grünen Gates/Freigaben verwenden; APK unabhängig auf Byte-SHA, Paket, Versionscode und Signer validieren, Rollback-Artefakt sichern, erst dann eine überprüfte direkte HTTPS-Adresse veröffentlichen. Fold7-/Termux-Geräteprüfung für tatsächliche Schwebegrafik, APK-Installation, Datenkontinuität und Issue #76 bleibt separat erforderlich.

### Fortsetzungskommando für jeden Nachfolger
`git fetch; git switch --detach <aktueller exakter HEAD von chatgpt/continuum-hologram-context-recovery-20261024>`, auf neuem überprüfbarem Branch fortsetzen; **kein Main-/Signer-Write**, keine Force-Pushes. Keine Entwickler- oder Testsitzungsdatei ist nötig, alle Arbeiten sind GitHub-Quellen. Priorität frische SDK-Tests, Releasepin-Review und Owner-Gate; produktive SWB1/CDX1-Gewichte, CRE1 und CTX3 unverändert lassen.

### Zusätzliche echte lokale Kotlin-Laufprüfung
`app/src/main/java/at/heinz/deviceai/ui/ContinuumOrbitalRing.kt` wurde aus dem vorliegenden GitHub-Blob **bytegenau** lokal rekonstruiert; `git hash-object` ergab exakt `35a92c7a8587e22d6e5dfaa93550407371d5e304`, identisch zu GitHub. Dieser Produktions-Kotlin-Code wurde mit vorhandenem `kotlinc` (1.9, JVM) kompiliert und gemeinsam mit einem unabhängigen ausführbaren Test gestartet. **Exit 0**: Zwei projizierte Tiefenhälften, Ringkoordinaten ändern sich bei vier Sekunden, sichtbarer Lift größer als vier Pixel bei Radius 101 und 1.001 Grenzwertprüfungen innerhalb ±5,051 Pixel. Dies ist ein echter **isolierter Kotlin/JVM-Lauf**, ausdrücklich **kein** gesamter Android-SDK-/Robolectric-/Gradle-Test und kein Bildschirmanimationsbeweis.
