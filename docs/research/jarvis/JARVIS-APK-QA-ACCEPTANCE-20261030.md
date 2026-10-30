# Continuum JARVIS · APK-/Fold7-Abnahmestand und QA-Blocker

Stand 30.10.2026. Dies ist ein **nachvollziehbarer Statusbericht**, keine simulierte APK-Abnahme.

## Tatsächlich geprüfte Bestände

- **PR #242:** `7924ba0793963dd84eef61517359a743071829bf` ist der dokumentierte Integrationsstand; geprüfter Code war `e06e17a4e8ba12a1dce3d5308e5eb455cda30b6d`. Nach veröffentlichtem Rohbeleg: Android-JUnit 1268, CCC1-/Worker-/F3-Gates 390, kanonische Node 226, Python 10, Gradle erfolgreich. Diese Tests gelten nur für den damaligen Stand, nicht automatisch für neue PRs.
- **PR #250:** JARVIS-VOICE-1-Branch `research/jarvis-voice-conversation-owner-gated-20261030`, Ausgangscommit `2e21bdaac8797a83e46e06d1b40244a616946fd6`. Android-Sprachdialog ist im Quellcode verdrahtet, aber Android-Gradle-Gesamtkompilierung, tatsächliche Spracherkennung/TTS und Fold7 sind **NOT_RUN**.
- **PR #249:** Eigenständiger Self-Play-Forschungszweig. Das Android-`PowerManager.getThermalHeadroom()`-Vorzeichenproblem wurde mit Regressionstest korrigiert. Hier liegen 17 lokale Funktionsfälle für die isolierte Admission-Logik vor, aber kein vollständiger Test des Gesamt-PR.
- **Dieser Charta-Branch:** enthält Forschungsregeln und Experimentregister; **keine neu gebaute APK**. Er basiert auf PR #250, **nicht** auf PR #249. PR-Zweige sind nicht blind zusammenzuführen.

## Tatsächlich verfügbare Laufumgebung dieser Sitzung

- Lokaler Host: `node v22.16.0`, OpenJDK `21.0.12.1`.
- Im aktuell erreichbaren Container sind **kein konfiguriertes Android SDK, keine Gradle-CLI, kein angeschlossenes Fold7 per ADB und kein vollständiger privater Repository-Checkout** nachgewiesen.
- Vorhandenes Repository-Skript `scripts/continuum-mobile-builder.mjs` kennt `:app:testDebugUnitTest`, `:app:lintDebug` und `:app:assembleDebug` sowie die Prüfung von Signatur, Paketidentität, Commit-SHA und APK-Hash. Ein Skript ist noch kein ausgeführter Build.
- **Ergebnis dieser Runde:** `APK_NOT_BUILT_TOOLCHAIN_UNAVAILABLE`. Keine APK-SHA, keine Signatur oder Installation erfunden.

## Abnahmereihenfolge

1. Einen **einzigen** Integrationskandidaten aus den tatsächlich zu übernehmenden Review-Zweigen festlegen; alle Konflikte (JPI1 #246/#247; Self-Evolution #249; Voice #250) ausdrücklich vergleichen. Kein bestehender Besitzerstand und kein `main` überschreiben.
2. Auf sauberem Repository-Checkout den unveränderten Quell-HEAD und die Toolchain erfassen. Dann das vorhandene Projekt-QA-Skript `scripts/continuum-fusion-full-regression.sh` beziehungsweise den dokumentierten Build-/Lint-Vertrag benutzen; keine angeblichen PASS ohne Roh-Exitcode.
3. Nach **tatsächlich grünem** Kotlin/JUnit, Node-CCC1-/Worker-, Python- und Lint-Lauf mit dem vorhandenen mobilen APK-Builder den `deviceAiPreview`-Kandidaten bauen, Quell-SHA/Signer-Digest/Paket-ID und APK SHA-256 dauerhaft sichern.
4. Kein APK-Update oder Installation ohne ausdrücklich freigegebene, passende Signatur und Android-Besitzerfreigabe. Keine Ersatzsignatur, die als produktiver Update-Schlüssel ausgegeben wird.
5. Fold7-Abnahme: echte Offline-/Online-Dialoge, native Gemma, Besitzererinnerung und Korrektur über Neustart, Hologramm, Aktivitätstests beim Falten, Permission-Denial/Stop, TTS-/ASR-Provider, ThermalHeadroom über Zeit, RSS, Akku und 30-min-Dauerbetrieb.
6. Erst danach selektive ARGON-Promotion für **konkret nachgewiesene** Aufgabenklasse, mit A/B-Baseline und Circuit-Breaker. Nicht aufgrund bloßer 82-%-Dashboard-Anzeige oder 390 Hosttests generell aktivieren.

## Wichtige Freigabegrenzen

`main`, ToolGate, Besitzerfakten, Schlüssel und produktive Modellgewichte bleiben unangetastet. Alle neuen JARVIS-/CTRNN-/HDC-/ZKP-/STDP-Experimente sind ohne nachgewiesene Gerätetests SHADOW. Stop/Widerruf/Löschung haben immer Vorrang vor automatischer Wiederherstellung.

**Rollback:** isolierten Forschungs-PR offen lassen und kein Preview-APK installieren. Der frühere Besitzer-APK-Stand bleibt unverändert.
