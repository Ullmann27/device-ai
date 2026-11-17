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

## Kontrollgang 3 und erneute tatsächliche Source-Verifikation

### Zweigabstammung
Verglichen mit `chatgpt/continuum-hologram-context-recovery-20261024`: `fix/continuum-fold-motion-regression-gates-20261023`, `fix/continuum-fold-live-responsive-20261023` und `gpt6/continuum-0.3.18-release-preflight-20261023` waren **0 Commits voraus**, vollständig enthalten. `sol/continuum-hologram-intelligence-20261024` liegt weiterhin exakt auf dem alten Sol-HEAD `f65181fe` und ist 13+ Review-Commits zurück. Die alten `astra/continuum-live-hologram-20261023` und `integration/continuum-all-current-20261023` weichen nach Git-Vergleich ab, aber `ContinuumHologramRenderer.kt` hat **denselben GitHub-Blob** wie unser Zweig (`2bb8817da268bdcca676aa9a9b48f56b50d06609`); pauschaler Fremdbranch-Merge wurde aus Sicherheits- und Regressionsgründen nicht vorgenommen. `opus/continuum-next-20261023` ist ein älterer abweichender Testquellpfad, nicht direkt in den produktiven Kern zu mergen.

### Zusätzlicher UI-Test und Besitzerquellenmatrix
Neu gepusht: `app/src/uiTest/java/at/heinz/deviceai/ContinuumHologramMotionUiTest.kt` mit zwei echten Robolectric-Hostwidget-Prüfungen auf `@Config(sdk=[28])`: Vordergrund-/Pause-/Reduced-Motion-Verhalten und eine Canvas-Zeichnung ohne eigenen Phasenfortschritt. **Noch nicht per Android-SDK ausgeführt**. Sols Screenshot dokumentiert einen *anderen, lokalen* Gradle-Offlinelauf `--tests ...ContinuumHologramMotionUiTest` mit **Exit1 nach 57s**, zeigt jedoch nicht den Stacktrace oder die konkrete Testursache. Deshalb ist keine Bestätigung eines Vorher-/Nachher-Erfolgs zulässig.

Alle22 SHA-Pins wurden danach erneut live über GitHub verglichen: 9 passend, 13 verschieden; die seit dem alten Release nicht attestierten zehn Android-Dateien zusätzlich. Der vollständige Eigentümerprüfantrag mit **13 exakten aktuellen SHA40-Werten** und allen zehn APK-Quellpfaden liegt in `docs/evidence/CONTINUUM-OWNER-SOURCE-REVIEW-20261024.md`. Kein Releasepin, kein Owner-Zertifikat, keine Render-Konfiguration und kein signierter App-Build verändert.

### Tatsächlich durchgeführte Prüfungen in dieser Sitzung
- Drei getrennte Quellprüfgänge: branch ancestry/Blobs, UI-/Cognition-Eingriffstests auf GitHub, Quell-Signiermatrix.
- 20/20 Quellinvarianten für Frame-Takt, Policy/Lifecycle, Context prompt und kontrollierte Datenflüsse bestanden.
- Vier eigene negative Code-Mutationen wurden erwartungsgemäß von den Quellverträgen erkannt.
- Frühere isolierte Kotlin/JVM-Prüfung des echten OrbitalRing-Blobs, beschrieben oben, keine gesamte Gradle-Regression.
- Android SDK und Gradle in diesem ChatGPT-Container nicht vorhanden. GitHub direkte DNS-Git-Verbindung hier nicht verfügbar; GitHub-Connector sicherte alle Commits. **Kein behauptetes komplett grünes `testDebugUnitTest` auf diesem aktuellen HEAD.**
- Vorherige 1099 Node, 940 SDK-/JUnit-/Robolectric und Gradle-/Lint-/Preview-Erfolge stammen **ausschließlich** von Sols eingefrorenem Code `ec28d661` und gelten nicht als frisch bestandene Tests dieser Hologramm-Änderungen.

### Next exact steps
1. In einer autorisierten Umgebung mit Android SDK 36/Gradle 8.11.1 auf dem **neuesten HEAD** `-PcontinuumUiWidgetTests=true :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` und komplette Node-/Backend-Gates ausführen; die beiden hinzugefügten Robolectric-Fälle gegen den echten Animator-/Power-Mode unter Robolectric prüfen. Sols fehlenden `hologram-green.txt`-Stacktrace nicht erfinden.
2. Bei Testfehlern gezielte Rot-/Grünreparaturen auf einem neuen Reviewbranch, erneut testen und sichern. Physische Fold7-Animation und Termux danach separat bestätigen.
3. **Nur nach genauer ausdrücklicher Besitzerfreigabe** aller13 geänderten Pin-Quellen und zehn weiteren Android-Quellen die attestierten Werte ändern, einen neuen finalen SHA einfrieren und die Gesamtsuite vollständig erneut ausführen.
4. Danach und nur bei vollständig grünen Release-Gates die bereits konfigurierte owner-signierte Render-Pipeline verwenden, v0.3.17-Rollback erneut unabhängig prüfen und den signierten v0.3.18/code19-APK mit SHA256, App-ID, Versionscode, Zertifikat und direkt abrufbarer HTTPS-Datei verifizieren.

## Später letzter Sicherheitsdurchgang: auch `resolvedQuery` darf keine fremde Session liefern

Nach den drei großen Runden wurde an der Modell-Übergabe ein zusätzlicher Restfehler nachgewiesen: `core.dialogueHistory()` war entfernt worden, jedoch konnte `turn.resolvedQuery` bei einer **neuen conversationId ohne expliziten UI-Reset** noch eine vorherige profilweite Frage enthalten. Der lokale Modellprompt nutzte damit potentiell fremden Sitzungskontext.

Fix in `ContinuumCognitiveRuntime.kt`: `@Synchronized reply` verwaltet jetzt nur für **diese eine Runtimeinstanz** die letzte tatsächlich geerdete eigene Frage als unpersistierten Kontext. Eine neue Runtime hat keinen geerbten Referenten; eine unaufgelöste Rückfrage erzeugt einen Klarstellungshinweis, keine alte Nutzerfrage. Bei bekannten eigenen Follow-ups kann ein Rückfrage-Prompt entstehen, der seine Unsicherheit ausdrücklich nennt. Die persistierte SWB1-Gewichtsdatei, CRE1-Zulassung, Eigentümerfakten und Offline-Modellbytes bleiben unangetastet. Der alte Baseline-Gesprächsdatensatz selbst bleibt profilweit; die Änderung ist eine **sichere Modellprompt-Grenze**, keine Behauptung allgemeiner per-ID-Baseline-Isolation.

Neu sind zwei JVM-Regressionsfälle: Wechsel der ID ohne Reset bei vorhandener alter `resolvedQuery`; sowie sichere Fortsetzung derselben Runtime. `.github/continuum/continuum-hologram-context-wiring.test.mjs` sichert den neuen Promptpfad auch als Quellvertrag. Tatsächlich durchgeführt: **14/14 neue spezifische GitHub-Blob-Quellkontrollen** bestanden, die neuen JVM-/Robolectric-/Node-Suiten wurden auf diesem Commit noch **nicht** im vollständigen Android-SDK ausgeführt. Neuer `ContinuumCognitiveRuntime.kt`-Git-Blob: `9e78dadd95dde7cc59aea712d99ac518c31d061a`. Die Eigentümermatrix wurde entsprechend aktualisiert; **weiterhin 13/22 Pinabweichungen plus zehn ungepinnte APK-Quellen**, keine Freigabe.

### Weiterführungspriorität
Führe die neue `ContinuumCognitiveRuntimeTest`-Klasse und `ContinuumHologramMotionUiTest` mit `-PcontinuumUiWidgetTests=true :app:testDebugUnitTest` real aus, inklusive Vergleich der im Sol-Screenshot gezeigten Fehlersituation. Ist der Android-SDK/Gradle-Lauf weiterhin nicht verfügbar, markiere explizit `SDK_UNAVAILABLE` und lasse Owner-V19-Signierung blockiert. Kein Main-Merge und kein Umgehen der PIN-Matrix. Nach konkreter Besitzerfreigabe erst finale Source-SHA freigeben und sämtliche Gates neu ausführen.

## Letzter Integrationsschritt – Hauptchat zu lokalem kognitivem Modell

Bei unabhängigem Lesen der aktuellen `AgentActivity.kt` ergab sich: Sie erstellt für jede Nachricht einen **neuen** `ContinuumCognitiveRuntime`. Deshalb konnte der zuvor eingebaute runtimeinterne Referenzkontext im normalen Chat nicht die nächste Nachricht erreichen, obwohl der isolierte Unit-Test mit derselben Runtime bestanden hätte. Der sichere Kontextpfad wurde jetzt direkt angeschlossen:

- `AgentActivity` besitzt eine flüchtige `LocalPromptSession(id,groundedTopic)`, die nur zur aktuellen `sessionId` gelesen wird; die Anfrage bindet diese ID vor dem Hintergrundjob.
- `ContinuumCognitiveRuntime` erlaubt eine optionale bereits geerdete, auf 320 Zeichen begrenzte Vorfrage. Sie muss frei von Steuerzeichen sein. Eingehende alte profilweite `dialogueHistory()` oder `resolvedQuery` dienen weiterhin **nicht** als Modellpromptquelle.
- Das echte Runtime-Ergebnis trägt `nextScopedGroundedQuestion`. Nur nach identitätsgeprüftem Ergebnis wird es von `AgentActivity` für dieselbe Session übernommen. `newSession()` löscht es explizit; Activity-Restart beginnt konservativ ohne lokales Vorwissen. Keine Besitzerfakten/Gewichte/Toolberechtigungen geändert.
- Zwei zusätzliche JVM-Regressionen prüfen echte Neuinstanziierung der Runtime mit derselben Session und die Ablehnung von Kontext mit Steuerzeichen. `.github/continuum/continuum-hologram-context-wiring.test.mjs` kontrolliert nun den tatsächlichen Activity-Übergabepfad.
- Die bestehende **isolierte** v19-GitHub-Android-QA-Workflow-Datei wurde auf diesen Fortsetzungszweig verdrahtet. Erster Run `40087677781` ist mit `failure` beendet, **ohne Runner-Schritte/Logs**. Dies ist keine Aussage über einen Kompilierfehler dieser Änderung. Weitere identische Neustarts vermeiden.

Außerdem mit dem GitHub-Connector **lesend** geprüft: Render-Workspace `tea-d7qe6dosfn5c73e2rnag`, bestehender Besitzer-Signierdienst `srv-dbaue53bc2fs73crkop0` ist weiterhin auf Branch `astra/render-mentor-apk-builder-20261019` mit Live-Build `285639ba` (0.3.17) und `autoDeploy=off`. Kein 0.3.18-Build getriggert, keine Schlüssel gelesen.

Technologieabgleich: Aktuelle offizielle LiteRT-LM-Android-Anleitung und llama.cpp-Android/GGUF-Anleitung geben unterstützte lokale Wege; daraus wurde **keine neue ungetestete Abhängigkeit** in den bevorstehenden Owner-Release hineingezogen. Ein tatsächlich geladenes neues Sprachmodell auf Fold7 ist weiterhin nicht nachgewiesen.

**Testgrenze:** Kein Android SDK/Gradle im aktuellen Container; direkter Netzwerkzugang zu GitHub/Google SDK über diesen Container funktioniert nicht. Die Gesamtregression auf dem nach diesen Commits neuen SHA ist **nicht bestanden beziehungsweise nicht ausgeführt**. Die Sol-Belege zu `ec28d661` bleiben historische, korrekte Belege und wurden nicht auf diesen HEAD umetikettiert. Sicherheitsfreigabe und Besitzer-Signierung bleiben gesperrt.
