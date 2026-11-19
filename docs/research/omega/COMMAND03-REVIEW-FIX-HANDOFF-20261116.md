# Device AI Continuum · Command03 Review-Fix Übergabe (16.11.2026)

**Status: QUELLE KORRIGIERT / QUELLTESTS ERGÄNZT / ANDROID-QA NICHT AUSGEFÜHRT / NEUE APK NICHT GEBAUT.**

- Repository: `Ullmann27/device-ai`
- Entwicklungslinie: `fix/command03-native-controls-20261116`, [PR854](https://github.com/Ullmann27/device-ai/pull/854)
- Exakt korrigierte und testergänzte Quelle: `d51827024b957a2e0a79d8588815292488cf6c10`
- Vorgänger: `9c9568344fa8871995cdc51cb170d38325a5c86b`
- Ziel: [PR851](https://github.com/Ullmann27/device-ai/pull/851), Branch `integrate/continuum-command03-android-20261115`; kein Fast-forward ohne tatsächlich bestandene neue QA.
- CodeRabbit-Konversation zu PR854: [Review mit vier geschlossenen Findings](https://github.com/Ullmann27/device-ai/pull/854#issuecomment-6910769033). Dies ist **kein** Android-Build- oder Gerätebeleg.

## Gezielte Quellkorrekturen

1. `app/src/main/java/at/heinz/deviceai/ui/ContinuumCommand03OriginalStage.kt`: `bind()` gestattet nach einer bestehenden Bindung entweder den exakt identischen Scope oder einen **höheren** `viewRevision` **mit neuer** `viewId`. Geänderte `sessionId` bei gleicher Revision sowie eine neue Revision unter alter View-ID werden abgewiesen. Die Android-Aktionsprüfung verwendet dadurch dieselben Scope-Regeln wie der HTML-Empfänger.
2. `app/src/main/assets/continuum-command03/native-bridge.js`: `receive()` verwendet die identische Revisions-/View-/Sitzungsregel. Eine widersprüchliche Aktualisierung darf weder den Text verändern noch den ursprünglichen Handlungs-Scope ersetzen; ein echter neuer View-Scope bleibt zulässig.
3. `ContinuumCommand03OriginalStage.kt`: Die Ablehnung für einen geänderten Entwurf lautet `stale-edit`, genau wie der lokalisierte Eigentümerhinweis des HTML-Adapters.
4. `app/src/main/java/at/heinz/deviceai/AgentActivity.kt`: `replaceCommand03View()` veröffentlicht die neue View, **bevor** die bisherige Computer-Sitzung gestoppt wird. Stop-/Busy-Rückrufe können deshalb keine zweite View erzeugen und den dokumentierten `command03TransitionCause` verlieren. `command03ViewCurrent()` ist für verspätete Rückrufe nun ein rein lesender Vergleich.

**Die 35 freigegebenen Original-HTML-/3D-/Design-Assets wurden nicht geändert.** Der Android-only-Adapter ist eine zusätzliche Schicht. Capture, Dialog-/Speicherverwaltung, Sprachsteuerung, ToolGate, Berechtigungen und Originalsignatur wurden nicht ausgetauscht.

## Neue Regressionstestquellen (noch nicht ausgeführt)

- `tools/command03-native-qa/native-bridge-behavior.test.mjs`: ein zusätzlicher DOM-Test für widersprüchliche Scope-Rebinds, nutzbare alte Eingabe, lokalisierte `stale-edit`-Meldung und gültigen neuen Bot-View.
- `app/src/uiTest/java/at/heinz/deviceai/ContinuumCommand03StageUiTest.kt`: zwei zusätzliche Robolectric-Tests für native Scope-Admission und die `stale-edit`-Ablehnung.
- `app/src/uiTest/java/at/heinz/deviceai/ContinuumCommand03BridgeUiTest.kt`: ein zusätzlicher Activity-Test für gleichzeitige PDF-Projektion, aktive Computer-Sitzung, Botwechsel und unbestätigten Entwurf; er erwartet genau **eine** erhöhte Viewrevision sowie den unveränderten Übergangsbezug und erhaltenen Entwurf.

Neu vorhandenes Testinventar: **40 Android-HTML-/Medienfälle, 77 Unitfälle, 32 Klassikfälle, sechs Dokumentfälle = 155 native Fälle**. Web: **45 DOM-Fälle**, mit verpflichtendem frisch aus Kotlin erzeugtem Computerpaket **46**. Dies sind Codeinventar-Zahlen, **keine aktuellen bestandenen Testergebnisse**.

## Prüf- und Releasewahrheit

- **Quellkontrolle:** sechs geänderte Produktions-/Testdateien aus den zwei Fix-Commits wurden von der GitHub-Quelle zurückgelesen. Die drei Review-Regeln, das Vorziehen der Eigentümer-Zuweisung und die vier neuen Testfälle sind im Commit vorhanden. Dies ist eine statische Quellenprüfung.
- **Vorheriger, anderer Stand `62be11f8`:** 44 Python- und 40 Node-Releasevertragstests bestanden; 44 reguläre DOM-Entwicklungstests des damaligen unveränderten Adapters bestanden.
- **Vorherige Android-QA `68a0eba6`:** 29 PASS, fünf FAIL von 34; die damaligen Testfixtures wurden korrigiert, jedoch nicht grün nachgeprüft.
- **Android-Kompilierung und die 155 neuen nativen Fälle auf `d51827024b957a2e0a79d8588815292488cf6c10`: NOT_RUN.** Der lokal hier verfügbare Container hat Kotlin/JDK, aber keinen Android SDK, Gradle-/Projekt-Cache oder vollständig montierten Repository-Buildpfad; die verbundenen GitHub-Aktionen bieten lesende CI-Abfragen und Wiederholung vorhandener Jobs, jedoch keinen neuen freigegebenen manuellen Workflow-Dispatch. Der frühere Host hatte Speicher-/Daemonprobleme; deren damaliger Befund ist **keine Aussage über neue GitHub-Runner**.
- **Frischer 46er-DOM-Lauf mit Kotlin-Paket, `assembleRelease`, unsigned APK-Audit, Gerätetest Fold7, originale Signierung und Besitzerveröffentlichung: NOT_RUN.**
- **Kein Merge nach main, keine Änderung am Code59-Besitzerrelease, keine Geheimnisse oder Signaturschlüssel.**

## Wiederaufnahme und unveränderte Freigabegrenzen

1. Die vollständige Quelle `d51827024b957a2e0a79d8588815292488cf6c10` inklusive vier Tests auf einem Android-SDK-/Gradle-fähigen Host auschecken, SHA und sauberen Status bestätigen.
2. Mit vorhandener `tools/command03-native-qa/run.py`-Harness/Toolchain normal kompilieren und die **155** vorbereiteten nativen Fälle (einschließlich der neuen vier) ausführen; frühere fünf Fehler gesondert nachprüfen. Keine Testassertion entfernen und keinen übersprungenen Fall als bestanden zählen.
3. Das frisch von der **gleichen** Kotlin-Stage serialisierte Bildpaket exportieren und den 46er-DOM-Test inklusive Interop ausführen. Alle 35 Originalassets neu hashprüfen.
4. Regulär unsigned ARM64 APK mit Code60/0.3.59 bauen, ZIP, 16-KiB, Version, ursprüngliche Designassets und tatsächlich fehlende Unterschrift unabhängig prüfen.
5. Bei vollständig grünen Belegen den fehlerfreien Quellstand nach dem bestehenden PR851-Integrationsverfahren führen. Für die vollständige GitHub Code60-QA und geschützte Originalsignierung sind die im Projekt-Releasevertrag verlangte **explizite** Besitzer-/SHA-/Branch-Freigabe und die zwei vollständigen, source-bound QA-Läufe erforderlich. Eine allgemeine Zustimmung zur Code-Reparatur ersetzt diese Gates nicht.

**Rollback:** PR854 bleibt getrennt vom kanonischen Integrationszweig; ursprünglicher PR851 bleibt ohne diese neuen, noch ungetesteten Änderungen weiter nutzbar. Die beiden Fix-Commits können bis zur QA unabhängig beurteilt werden; `main`, der publizierte Code59 und Originalsignaturschlüssel bleiben unverändert.
