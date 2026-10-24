# Continuum 0.3.18 – Besitzer-Quellenprüfpaket vom 24.10.2026

**STATUS: OWNER_REVIEW_REQUIRED – NICHT GENEHMIGT.**
Quellbranch: `chatgpt/continuum-hologram-context-recovery-20261024`. Grundlage: `sol/continuum-max-20261024` / PR #112 mit 13 zusätzlichen Hologramm-/Kontextreparaturen und einem Android-Widgettest auf PR #113. GitHub-Blobvergleich gegen die **unveränderten 22 Pins** in `scripts/render-mentor-owner-update-v19.sh`: **9 passen; 13 weichen ab**. Keine Pin-, Signierskript-, Besitzer-App-/Render-Änderung erfolgt.

## 13 derzeit abweichende geprüfte Dateien

| Zu prüfende Datei | Aktueller Blob-SHA40 | Funktion | Besitzerfreigabe |
|---|---|---|---|
| `app/src/main/java/at/heinz/deviceai/AgentActivity.kt` | `1f636f35b09837c13bb954b48da1fabe7039cb05` | Android UI, Lifecycle, Chat | **NEIN** |
| `app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt` | `071af65313d79c0840bf3d772b4cac3dc8e09825` | Animationstakt / Vordergrundsteuerung | **NEIN** |
| `app/src/main/java/at/heinz/deviceai/ui/ContinuumOrbitalRing.kt` | `35a92c7a8587e22d6e5dfaa93550407371d5e304` | Sichtbarer Schwebehub | **NEIN** |
| `app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt` | `a3f6d68db2cbccabd640928926c3ca28d92dc058` | M4/M5 Builder-Lifecycle | **NEIN** |
| `app/src/main/java/at/heinz/deviceai/ContinuumMobileBuildPlan.kt` | `6ebec7bad39cb842e22a8031f4728338221b0da2` | Feste, geprüfte Buildaufträge | **NEIN** |
| `app/src/main/java/at/heinz/deviceai/ContinuumMobileResultPolicy.kt` | `79f365032047fa1818c5d60ed83485a32d9c626b` | APK-Echtheit, Rezeptbelege | **NEIN** |
| `app/src/main/java/at/heinz/deviceai/ContinuumMobileBuildResultReceiver.kt` | `b1cdf34bb2cfdca831d874a63b443eedcaf6c857` | Job-IDs und CCC1-Abbruchbindung | **NEIN** |
| `app/src/main/java/at/heinz/deviceai/ContinuumWorkerChatRouter.kt` | `a900cdc73b757cec751ecc23825a92153da257ad` | Explizite Projektplan-Chatroute | **NEIN** |
| `app/src/main/java/at/heinz/deviceai/ContinuumOwnedAppContractTest.kt` | `a4f1b385739d99912081fffbaa405900e98e8fed` | M4-Vertragstest | **NEIN** |
| `app/src/main/java/at/heinz/deviceai/ContinuumWorkerChatRouterTest.kt` | `9a6f7a4c4bebc36c4dab8041491643bca2696df5` | Chat-Routentest | **NEIN** |
| `scripts/continuum-owned-app.mjs` | `c735606419fcdec445fd3b344eecba2bdee55989` | M4-Template-/Buildskript | **NEIN** |
| `scripts/continuum-owned-app.test.mjs` | `65cec140fa13896131f18403c69c8a868781e9b7` | M4-Build-/Recovery-Tests | **NEIN** |
| `app/src/main/java/at/heinz/deviceai/BootstrapBrain.kt` | `e0981a9a8052f72bc5959ae2de68319fc1692e66` | Sichere Kommandoklassifizierung | **NEIN** |

Die übrigen neun Einträge sind identisch mit ihren bisher attestierten SHA40. Die dreizehn neuen Werte sind **ein Prüfangebot und keine Aufforderung, die Signier-Sperre zu umgehen**. Jede einzelne Quelle ist vor Freigabe mit den bereits abgelegten Einzeldiffs aus Sols `docs/evidence/sol-max-20261024/source-diffs/` und den PR-#113-Diffs zu kontrollieren.

## Zehn weitere neue/geänderte APK-Quellen ohne eigenen alten Pin

- `app/src/main/AndroidManifest.xml`
- `app/src/main/assets/continuum-owned-project-plans-v1.json`
- `app/src/main/java/at/heinz/deviceai/ContinuumOwnedProjectPlan.kt`
- `app/src/main/java/at/heinz/deviceai/ContinuumCodingConversation.kt`
- `app/src/main/java/at/heinz/deviceai/ContinuumM5LabActivity.kt`
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumCognitiveRuntime.kt` – aktueller Git-Blob `656532f9798b0b5b53cf2973d296faf2ee8e6afe` (zusätzliche konservative Abgrenzung der Modellprompts nach Runtime-Identität)
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt`
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyCycleResult.kt`
- `app/src/main/java/at/heinz/deviceai/learning/context/Ctx3Context.kt`
- `app/src/main/java/at/heinz/deviceai/learning/context/Ctx3Shadow.kt`

Die Manifest-/Exportentscheidung ist zu verifizieren: `ContinuumM5LabActivity` und Ergebnis-Receiver sind nicht exportiert, der bestehende Builder ist exportiert, aber verfügt über bestätigungspflichtige Aktionen. CTX3 bleibt OFF/SHADOW mit gescheitertem unabhängigem TEST3. Kein produktives Gewichtslernen aus diesen Änderungen. Zusätzlich neu im Testquellset: `app/src/uiTest/java/at/heinz/deviceai/ContinuumHologramMotionUiTest.kt`.

## Erforderliche exakte Eigentümerentscheidung und Releaseabfolge

1. Besitzer prüft bewusst alle **13 gelisteten** SHA40 und **zehn** zusätzlich benannten APK-Quellen einschließlich Sicherheits- und Paketwirkungen. Ohne ausdrückliche Freigabe bleibt `0.3.18` signiert **GESPERRT**.
2. Erst nach solcher Freigabe dürfen genau diese Quellpins im bestehenden Besitzer-Skript durch einen neuen, überprüfbaren Commit angepasst werden. Kein automatisches Überschreiben, kein Geheimnisexport, kein Force-Push.
3. Auf dem dann endgültigen Commit erneut **alle** Node-/Backend-/Kotlin-/Gradle-/Robolectric-Widget-/Lint-/APK-Gates ausführen. Der sichtbare Sol-Screenshot zeigt einen fehlgeschlagenen Offline-Gradlelauf (Exit 1, ohne Stacktrace), also **kein** erfolgreiches Testresultat. Die neue Widget-Testdatei ist committed, jedoch noch nicht im echten SDK ausgeführt.
4. Nur dann den bestehenden privaten Render-Dienst und das unveränderte Besitzerzertifikat `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc` für Paket `at.heinz.deviceai.mentorpaired`, `versionCode 19`, `0.3.18` einsetzen. Vorher v0.3.17-Rollback prüfen; nachher echten APK-SHA256, Signer, Paket, Version und direkten HTTPS-Download unabhängig validieren.
5. Fold7-Animation, Termux und Update-Datenerhalt benötigen einen echten Gerätelauf. Alte Preview-APKs und Host-Simulationen ersetzen ihn nicht.

**Keine Besitzerfreigabe wurde erteilt, kein Code hinter eine private Sitzung verschoben.**


**Nachtrag zur Kommunikation:** Die zweite Quellenkontrolle fand einen verbliebenen Umweg über `turn.resolvedQuery`, den eine neue Gesprächs-ID ohne expliziten Baseline-Reset nutzen konnte. Der aktuelle Runtime-Code erlaubt einem lokalen Sprachmodell kontextuelle Vervollständigung nur noch aus einer **vorherigen bestätigten Frage derselben Runtime-Instanz**, nicht aus einer ungebundenen profilweiten Historie. Zwei Regressionen reproduzieren den ursprünglichen Fall mit und ohne UI-Reset; sie müssen bei vollständiger Android-JUnit-Regression noch tatsächlich bestehen. Keine Änderung der Besitzergewichte oder Freigaben.


## Sicherer Sitzungsanschluss an die Haupt-App
`AgentActivity.kt` führt für die aktuelle `sessionId` nun eine flüchtige `LocalPromptSession` mit der letzten **nachweislich geerdeten** eigenen Frage. Vor einem neuen Runtimeaufruf wird nur der explizit zur gleichen Chat-ID gehörende Kontext übergeben. Der generierte `nextScopedGroundedQuestion` wird nur beim erfolgreichen und identitätsgebundenen Rückruf übernommen. Bei `newSession()` wird er gelöscht; nach Activity-Neuerstellung standardmäßig kein Fremdkontext. `ContinuumCognitiveRuntime.kt` prüft Länge und Steuerzeichen des übernommenen Kontextes. Dies ändert **keine** Gewichte, Besitzerfakten, signierenden Schlüssel oder andere Buildklassen.

Zusätzliche JVM-Regressionen zur isolierten Fortführung derselben Session und zur Verweigerung von Steuerzeichen angelegt. Die Runtime- und Activity-Blobs oben entsprechen dem gepushten Stand. Die vollständige JUnit-/Robolectric-Prüfung auf dem neuesten Commit ist weiterhin offen. Neue APK-Signierung blockiert.


## Unabhängig einzeln abgefragte GitHub-Blobs für alle zehn zusätzlichen APK-Quellen

Die folgende Tabelle ergänzt den früheren Prüfbericht. Sie identifiziert exakt die Quellbytes, die tatsächlich in die nächste APK eingehen sollen; sie ist **noch keine Signierung**.

| Quelle | Blob-SHA1 | Sicherheits- und Funktionsrolle |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | `983138c8e8cc9b25189288c7202d4e17f699f959` | Berechtigungen, Komponenten, Exportgrenzen |
| `app/src/main/assets/continuum-owned-project-plans-v1.json` | `d19297d5715821ac1073934adde07c64ddf25d5b` | Nur zwei Hash-verankerte Projektvorlagen |
| `app/src/main/java/at/heinz/deviceai/ContinuumOwnedProjectPlan.kt` | `53efcb096fe152189a1731948706b4ccb1294790` | Strikter, kanonischer Projektkatalog |
| `app/src/main/java/at/heinz/deviceai/ContinuumCodingConversation.kt` | `b2732626709517ee49fb0213796ed272e8e0f76c` | Freigabepflichtige Auftragsdialoge |
| `app/src/main/java/at/heinz/deviceai/ContinuumM5LabActivity.kt` | `22234d244c08f6e7b041509eca7ea6df4d13cc24` | Nicht exportiertes M5-Labor; ungesendete Entwürfe |
| `app/src/main/java/at/heinz/deviceai/learning/ContinuumCognitiveRuntime.kt` | `656532f9798b0b5b53cf2973d296faf2ee8e6afe` | SWB1-/CRE1-Kontext mit sitzungsgebundener Prompt-Herkunft |
| `app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt` | `8c9da619e62608155c496c25919f2c213616e7b7` | Bestehende bestätigte Besitzerfakten und Lernschutz |
| `app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyCycleResult.kt` | `9872c2209b550d8b4c361f087ddc09595324b681` | Abgeschlossene Forschungszyklen und Ergebnisvertrag |
| `app/src/main/java/at/heinz/deviceai/learning/context/Ctx3Context.kt` | `cb9e51b1362c22df06cde7e21247a48f12d69e8c` | CTX3-Forschungsmodell ohne Freigabe |
| `app/src/main/java/at/heinz/deviceai/learning/context/Ctx3Shadow.kt` | `f93b6479e5d6577ba6cb33c5bf74b0077aeff6fe` | CTX3 ausschließlich OFF/SHADOW; keine Antwortüberschreibung |

Prüfung des AndroidManifest: `ContinuumM5LabActivity` `exported=false`, `ContinuumMobileBuildResultReceiver` `exported=false`, `ContinuumMobileBuilderActivity` `exported=true` bei `enabled=${mobileBuilderEnabled}`. Der Besitzer-Dialog bestätigt feste Termux-Aufträge nochmals vor Ausführung; keine Modellantwort darf ein freies Shellkommando werden. Dies ist eine quellbasierte Sicherheitsprüfung, kein Ersatz für den echten SDK-Test.
