# JARVIS VOICE-1 · echter Android-Sprachdialog am vorhandenen Continuum-Gehirn

**Stand:** 30.10.2026 · **Status:** SOURCE_INTEGRATED + hostseitig isoliert geprüft; nicht auf dem Fold7 abgenommen.

**Basis:** PR #247, `f3a5e3081428d223fc346174cd81493e8e03929a`. **Neuer Draft-PR:** #250. Beide isoliert; main unverändert. Versuchs-ID: JARVIS-VOICE-1, anschlussfähig an EXP-12.

## Vorher tatsächlich vorhandene Bausteine

- Android-`RecognizerIntent` wird bereits manuell durch den Mikrofontaster gestartet. Seine erkannte Standard-Äußerung wurde bisher nur ins Eingabefeld geschrieben. `EXTRA_PREFER_OFFLINE` ist eine Präferenz an den Android-Erkennungsdienst, keine Offline-Garantie.
- `TextToSpeech`, `speakBootstrap`, Hologramm-Sprachanimation und der explizite Ton-Schalter existieren in `AgentActivity` bereits.
- `runContinuumDialogue` verwendet `ContinuumCognitiveRuntime`, das bestehende bestätigte Besitzer-Gedächtnis, `CognitiveDecisionBridge`, den bereits genutzten Gesprächszustand und die EWM2-/F1-Schattenvergleiche. Es gibt **keine zweite Memory-Datenbank**.

## Was mit VOICE-1 geändert wurde

1. **Berechtigungsbewusster Einstieg:** Mikrofontaste **lang drücken**, nur für diese App-Sitzung `Jarvis-Sprachdialog` explizit einschalten. Sichtbares Mikrofon `🎙•` signalisiert den aktivierten Modus. Lang drücken schaltet ihn wieder aus. Kein Autostart, keine Hintergrundaufnahme.
2. **Bewusster Mikrofontipp:** Android-Spracherkennung starten; ein neuer Tipp kann eine laufende TTS-Ausgabe unterbrechen. Kein dauerhaftes Mithören, Wake-Word-Hintergrunddienst, Android-Auto-Audio oder PC-/Haus-Audio versprochen.
3. **Konservativer Voice-Router:** `JarvisVoiceConversationPolicy` lässt einfache Wissensfragen, Erklärungen, Grüße und begrenzte Folgefragen durch. Für gefährliche Aktionen, Gedächtnisänderungen, Internet-/Datei-/Kontozugriff, Gerätebefehle, sensible personenbezogene Daten, Kontrollzeichen und unklaren Text ist ausschließlich `MANUAL_REVIEW` erlaubt; der erkannte Text bleibt als editierbarer Entwurf im bestehenden Composer.
4. **Echte Verdrahtung:** Nur wenn der Benutzer den Modus eingeschaltet hat, der normale Sprach-Recognizer erfolgreich war und das System nicht beschäftigt ist, wird die zugelassene Äußerung zu `runContinuumDialogue(decision.cleanText, allowLocalModel = false)` geleitet. Diese Variante nutzt denselben `ContinuumCognitiveRuntime` mit derselben Sitzung und kann **nicht** in den toolfähigen `LocalModel`-`BoundedAgent`-Pfad wechseln. Existierende manuelle Text- und Tool-Anfragen behalten ihr bisheriges Verhalten.
5. **Antwortausgabe:** Der bestehende `addAssistantMessage`-/`speakBootstrap`-Pfad zeigt die Antwort und spricht sie bei eingeschaltetem `TON AN` über das Android-TTS aus. Persönliche Facts erhalten keine zusätzliche Bestätigung; der Inhalt ist auf genau die bereits bestehende Erinnerungs-/Quellenbeurteilung begrenzt.

## Reproduzierbare Tests & echte Grenzen

Am 30.10.2026 auf diesem Host: Kotlin-Compiler `1.9.0`, JRE `21.0.12.1`. Der **reine Kotlin-Voice-Policy-Kern** wurde mit dem im Entwicklungslauf angelegten JVM-Harness kompiliert und bei **38 konkreten Fällen** ausgeführt: 10 zugelassene Gespräche, 25 abgewiesene/mannuell zu bestätigende Äußerungen und 3 Längen-/Null-/Leerfall-Checks; **38 PASS, 0 FAIL**. Der auf GitHub gepushte Quell-Blob `1ae940325eec16e5137e586269a4845bb26cc04c` stimmt exakt mit der ausgeführten Kotlin-Datei überein.

Zusätzlich wurden über den verknüpften GitHub-Dateizugriff **7 statische Quellpfadbedingungen** im tatsächlichen `AgentActivity`, Sprachrouter und Unit-Testfile erfolgreich geprüft. Die neu veröffentlichte `.github/continuum/jarvis-voice-wiring.test.mjs` enthält **6** eigenständige Node-Vertragstests für einen vollständigen Checkout. Der JUnit-Test `JarvisVoiceConversationPolicyTest.kt` ist vorhanden, aber **noch nicht mit Android Gradle/JUnit ausgeführt**. Die 38 bestandenen Fälle stammen aus dem direkten Host-Harness, nicht aus Gradle.

**NOT_RUN / NICHT NACHGEWIESEN:** Kompilierung von `AgentActivity` mit Android SDK, Gradle-Gesamttests, Node-Komplettregression, Build-Signatur, APK, Installation, native Gemma-Inferenz, reale Mikrofon-/TTS-Ausgabe, Fold7-Faltwechsel, Automotive Bluetooth, aktiver Home-Assistant-/SmartThings-Zugriff. Kein behaupteter neuer Modell-, Hardware- oder Geräte-Intelligenzgewinn.

## Sicherheitsgrenzen

- Spracherkennung ist keine Sprecheridentität. Eine mögliche Abfrage über einen Drittanbieter ist vor Nutzung in den Android-Einstellungen zu prüfen.
- Nur der ausdrücklich gestartete Anerkennungsdialog darf automatisch die eng begrenzte `DIALOGUE_ONLY`-Route auslösen.
- Modell- oder Webseiteninhalte dürfen keine Besitzerrechte schaffen. Gesprochene Anfragen erhalten keine Freigabe für ToolGate, CCC1, Memory-Writes oder Home-Actuation.
- `TON AN` kann Antworten über die aktuelle Audioausgabe hörbar machen; kein geschützter Privatmodus oder sicheres Fahrzeugrouting implementiert.
- Ein späterer Wechsel zu wirklich modellgenerierten, sicheren Hands-free-Dialogen erfordert **einen separat toolfrei erzwungenen Modellpfad** und Geräteprüfungen; die jetzige `allowLocalModel=false`-Entscheidung ist absichtlich konservativ.

## Nächste unabhängige Meilensteine

1. Auf dem exakten HEAD des PR #250 die bestehenden `coding-agent-speech-wiring`-, `android-integration-wiring`-, `jarvis-voice-wiring`- und JUnit-/Kotlin-Gates vollständig ausführen und Build-Blocker reparieren.
2. Die tatsächlich vom Android-Recognizer verwendete Provider- und Offlinefähigkeit auf dem Fold7 nachvollziehbar dokumentieren. Falls sie nicht lokal garantiert werden kann, ausdrücklich nicht mit „100 % offline“ werben; bei Bedarf ein geprüftes wirklich lokales ASR-Modul **optional** integrieren.
3. Reale Fold7-Tap-to-Talk-/TTS- und Kontext-Folgefragen testen, einschließlich Wechsel von Display-/Faltzustand, Audio-Unterbrechung und Prozess-Neustart.
4. Erst danach für **modellbasierte** Sprache eine separate, strikt werkzeugfreie Reasoning-Route mit Negativ- und Provenienztests erwägen. Keine Voiceprint-Berechtigungen.
5. Vor jeder Haus-/Auto-Anbindung JPI1-SessionHandoff und InitiativePolicy aus PR #247 tatsächlich testen, ohne Home-Actuation. Reale Geräte-Telemetrie nur über freigegebene sichere Read-only-Adapter.
6. Die visionierte Haus/Auto/PC-/Proaktiv-Schnittstelle erst nach tatsächlichem Gerätesicherheits- und Nutzerfreigabenachweis ergänzen; alle kritischen Aktionen brauchen den bestehenden ToolGate-/Besitzer- und Sensor-Readback-Pfad.

**Rollback:** Draft-PR #250 nicht in die produktive Integration übernehmen oder den VOICE-1-Commit isoliert rückgängig machen. Bestehende Android-STT/TTS-Knöpfe und alle PR-#247-Gerätegrenzen bleiben dann unverändert.
