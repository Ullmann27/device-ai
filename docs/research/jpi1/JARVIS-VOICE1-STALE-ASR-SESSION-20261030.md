# JARVIS VOICE-1 · Stale-ASR-Sitzungsgrenze (isoliertes Review)

Basis: PR #252, Commit `2747f1a0600cba386defeb1a4641338fe8519d18`. Zuerst eingelesen: `AgentActivity.onActivityResult`, `JarvisVoiceDispatchGate`, `newSession`, die vorhandenen Kotlin-/Node-Regressionsprüfungen.

**Reproduzierbarer Defekt im Quellfluss:** Bei Start von Android-ASR wird `voiceRequestSession` gesetzt. Nach dem Wechsel auf eine neue Sitzung wird ein verspäteter ASR-Rücklauf korrekt nicht automatisch ausgeführt; `JarvisVoiceDispatchGate.decide` gibt aber `DRAFT` statt `IGNORE` zurück. Die `SPEECH_REQUEST`-Verarbeitung schreibt den Text deshalb in den neuen Eingabekomposer. Ein veraltetes/fehlendes Sitzungskennzeichen darf dort weder Daten anzeigen noch eine Antwort oder Werkzeugaktion starten. `newSession` kann während offenem Recognizer unter anderem asynchron den Sitzungswechsel auslösen.

**Minimaler Soll-Fix:** `IGNORE` für fehlende oder fremde Ausgangssitzungen und veraltete Rückläufe vor Behandlung als Draft; reguläre riskante Äußerungen in aktueller Sitzung bleiben `DRAFT`, geprüfte Dialogfragen bei gültiger Sitzung und freier UI `AUTO_DIALOGUE`. Kein neuer Worker, keine neue Berechtigung, kein dritter Erkennungsdienst, kein Umbau der TTS-Route.

**Vorregistrierter Testvertrag:** Bestehende Gate-Testfälle beibehalten, plus negative Fälle für fremde und nicht mehr existente Start-Sitzungen und Quellpfadtest gegen versehentliche Composer-Ausgabe. Erst roten Ausgangsfall reproduzieren, dann isolierte Korrektur. Host-JVM- und Node-Gates getrennt von Android-/Fold7-Gates nennen. Keine APK/Installation.
