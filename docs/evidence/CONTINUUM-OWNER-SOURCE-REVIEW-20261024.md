# Continuum 0.3.18 – Besitzer-Quellenprüfpaket vom 24.10.2026

**STATUS: OWNER_REVIEW_REQUIRED – NICHT GENEHMIGT.**
Quellbranch: `chatgpt/continuum-hologram-context-recovery-20261024`. Grundlage: `sol/continuum-max-20261024` / PR #112 mit 13 zusätzlichen Hologramm-/Kontextreparaturen und einem Android-Widgettest auf PR #113. GitHub-Blobvergleich gegen die **unveränderten 22 Pins** in `scripts/render-mentor-owner-update-v19.sh`: **9 passen; 13 weichen ab**. Keine Pin-, Signierskript-, Besitzer-App-/Render-Änderung erfolgt.

## 13 derzeit abweichende geprüfte Dateien

| Zu prüfende Datei | Aktueller Blob-SHA40 | Funktion | Besitzerfreigabe |
|---|---|---|---|
| `app/src/main/java/at/heinz/deviceai/AgentActivity.kt` | `2faaaba60a850ab75c911c1dbcc476956db23093` | Android UI, Lifecycle, Chat | **NEIN** |
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
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumCognitiveRuntime.kt`
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
