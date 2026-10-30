# JARVIS VOICE-1 · Stale-ASR-Sitzungsgrenze (isoliertes Review)

Basis: PR #252, Commit `2747f1a0600cba386defeb1a4641338fe8519d18`. Zuerst eingelesen: `AgentActivity.onActivityResult`, `JarvisVoiceDispatchGate`, `newSession`, die vorhandenen Kotlin-/Node-Regressionsprüfungen.

**Reproduzierbarer Defekt im Quellfluss:** Bei Start von Android-ASR wird `voiceRequestSession` gesetzt. Nach dem Wechsel auf eine neue Sitzung wird ein verspäteter ASR-Rücklauf korrekt nicht automatisch ausgeführt; `JarvisVoiceDispatchGate.decide` gibt aber `DRAFT` statt `IGNORE` zurück. Die `SPEECH_REQUEST`-Verarbeitung schreibt den Text deshalb in den neuen Eingabekomposer. Ein veraltetes/fehlendes Sitzungskennzeichen darf dort weder Daten anzeigen noch eine Antwort oder Werkzeugaktion starten. `newSession` kann während offenem Recognizer unter anderem asynchron den Sitzungswechsel auslösen.

**Minimaler Soll-Fix:** `IGNORE` für fehlende oder fremde Ausgangssitzungen und veraltete Rückläufe vor Behandlung als Draft; reguläre riskante Äußerungen in aktueller Sitzung bleiben `DRAFT`, geprüfte Dialogfragen bei gültiger Sitzung und freier UI `AUTO_DIALOGUE`. Kein neuer Worker, keine neue Berechtigung, kein dritter Erkennungsdienst, kein Umbau der TTS-Route.

**Vorregistrierter Testvertrag:** Bestehende Gate-Testfälle beibehalten, plus negative Fälle für fremde und nicht mehr existente Start-Sitzungen und Quellpfadtest gegen versehentliche Composer-Ausgabe. Erst roten Ausgangsfall reproduzieren, dann isolierte Korrektur. Host-JVM- und Node-Gates getrennt von Android-/Fold7-Gates nennen. Keine APK/Installation.

## Implementierte Korrektur, tatsächlicher Teststand

**Ausgangsbasis:** PR #252 `2747f1a0600cba386defeb1a4641338fe8519d18`. Die ursprüngliche, bytegenau überprüfte Kotlin-Policy hatte Git-Blob `1f7df97552d0393155f02fc10bc7061b86434a2c`.

1. `JarvisVoiceDispatchGate.decide`: Ungebundene oder nicht mehr aktuelle Start-Sitzungen erhalten sofort `IGNORE`, **nicht** `DRAFT`. In der gültigen Sitzung bleiben riskante Anfragen reviewpflichtige Entwürfe; einfache Gespräche können nur beim ausdrücklich aktivierten Sprachdialog automatisch starten.
2. `AgentActivity.onActivityResult(SPEECH_REQUEST)`: Bereits **vor** der Verzweigung zwischen normalem manuellem Composer und aktiviertem Jarvis wird `voiceStartedIn != null && voiceStartedIn == sessionId` geprüft. Auch bei ausgeschaltetem automatischem Dialog dürfen alte Mikrofonrückläufe daher den neuen Composer nicht überschreiben.
3. `JarvisVoiceConversationPolicyTest.kt`: Zusätzlicher negativer JUnit-Test für einen alten/fehlenden Start-Sitzungswert und riskante Äußerungen aus einem vorherigen Chat; bisherige gültige DRAFT-/AUTO-Kontrollen bleiben erhalten.
4. `.github/continuum/jarvis-voice-wiring.test.mjs`: Neue Source-Wiring-Regressionsprüfung schützt die vorgeschaltete Sitzungsidentität vor beiden Eingabewegen.
5. `experimental/jarvis-voice/StaleRecognizerSessionHarness.kt`: Unabhängig ohne Android-SDK kompilierbarer 12-Fälle-Kotlin-Hosttest, der das tatsächliche Dispatch-Gate direkt ausführt.

**Echter, lokal ausgeführter Rot-/Grün-Nachweis:** `docs/evidence/voice1-stale-20261030/verified-host-gate.log`.

| Hostprüfung (Kotlin 1.9.0, JDK 21.0.12.1) | Ausgangscode | Korrektur |
|---|---|---|
| Originale Policy ohne/mit Sitzungs-Veto | Blob `1f7df97552d0393155f02fc10bc7061b86434a2c` | Blob `e974aabc4e3d42249d23a1fa1085235614fc144c` |
| Kotlin-JVM-Compiler | Exit 0 | Exit 0 |
| Stale-Recognizer-Host-Harness | Exit 1, erwartetes `IGNORE`, tatsächliches `DRAFT` | Exit 0, **12/12 PASS** |
| Harness-BLOB | `dbd2c41cd5108193834c6a8ba3de2a1b44d1d5c4` | derselbe, vorher festgelegte Test |

Alle drei genannten Quell-Blob-Werte wurden lokal mit `git hash-object` bestimmt und nach GitHub-Readback abgeglichen. Die Unverändertheit der Baseline ist dadurch prüfbar.

**Separat nur statisch anhand aktueller GitHub-Quellen abgeglichen:** Acht Pfad-/Sicherheitsbedingungen, einschließlich weiterhin vorhandener `allowLocalModel = false`-Grenze. Die neu verfassten Node-Source-Wiring- und Android-JUnit-Tests sind **eingecheckt, hier aber nicht als vollständige Checkout-/Gradle-Suite ausgeführt**.

**Hier nicht verfügbar / nicht ausgeführt:** vollständiger privater Checkout, Android-SDK mit Android-Gradle-Gesamtsuite, ADB/Fold7, App-ASR/TTS-Integration, tatsächliche Installations-/Signaturprüfung und APK. Der dokumentierte Opus-Basistest aus PR #252 (`1319/1319` Host) gilt ausschließlich für dessen HEAD, nicht automatisch für diesen neuen HEAD. Keine Aussage über zusätzliche Intelligenz oder Energieverbesserung.

**Rückfall:** Diesen auf PR #252 gestapelten Draft nicht integrieren oder die eindeutig benannten Änderungen auf diesem Review-Branch isoliert revertieren. Die vorherige manuelle Mikrofon- und Textbedienung bleibt unabhängig verfügbar.

**Genau nächster ausführbarer Schritt:** Einen sauberen Checkout des endgültigen Integrationskandidaten auf dem Fold7 mit der bestehenden Termux-/Android-Toolchain bauen, dann gezielt `VOICE-1` mit fünf freigegebenen Sprachfragen, neuer Sitzung während laufender ASR, TTS-Echo-/STOP-Test und Erkennungsdienst-/Offline-Nachweis prüfen, ohne automatisch zu installieren.
