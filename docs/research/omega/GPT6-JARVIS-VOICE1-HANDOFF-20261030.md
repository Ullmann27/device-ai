# GPT-6 · JARVIS-VOICE-1 Übergabe · 30.10.2026

## Direkt fortsetzbarer Einstieg

- Offizielles privates Repository: `Ullmann27/device-ai`, Forschungsbranch `research/jarvis-voice-conversation-owner-gated-20261030`, Draft-PR [#250](https://github.com/Ullmann27/device-ai/pull/250), Basis PR #247 (`f3a5e3081428d223fc346174cd81493e8e03929a`). Kein Merge, keine Änderungen an `main`.
- **Vollständiger Funktions-/Sicherheitsbericht:** `docs/research/jpi1/JARVIS-VOICE-1-IMPLEMENTATION-20261030.md`.
- **Produktionspfad (Source only):** `AgentActivity.startVoiceInput -> onActivityResult(SPEECH_REQUEST) -> JarvisVoiceConversationPolicy.evaluate -> runContinuumDialogue(...allowLocalModel=false) -> vorhandener ContinuumCognitiveRuntime / EWM2 / Gedächtnis -> addAssistantMessage -> vorhandenes TextToSpeech`.
- Standardmikrofon unverändert, solange der Besitzer den Dialog nicht per langem Tastendruck und bestätigendem Dialog aktiviert. Aktivierung nur im lokalen Activity-Instanzzustand.
- Tool-/Geräte-/Speicher-/Geheimnisaufträge verbleiben im Composer. Sprache ist keine Berechtigung; keine proaktive Audioausgabe ohne bestätigten Besitzer- und Geräte-Endpunkt. JPI1-Realgerät-Handoff wurde nicht aktiviert.

## Echte Testbelege

1. **Kotlin-JVM Host:** Mit lokalem `kotlinc 1.9.0` wurde die reine `JarvisVoiceConversationPolicy.kt` zusammen mit einem temporären Host-Harness kompiliert und auf 38 vorgegebenen Fällen getestet. 38 PASS, 0 FAIL. Der GitHub-Git-Blob-SHA der Hauptquelldatei entspricht dem lokal ausgeführten Quellstand: `1ae940325eec16e5137e586269a4845bb26cc04c`.
2. **GitHub-Quellpfad:** Sieben statische Prüfpunkte gegen die vom Connector gelesene tatsächliche `AgentActivity.kt`, das Sicherheitsmodul und das JUnit-File bestanden. Originaler `AgentActivity`-Blob nach Verdrahtung: `73cb0f0a77f5ed3fc3f1a71fa7455010a1384e77`.
3. **Eingescheckt, aber noch nicht ausgeführt:** `.github/continuum/jarvis-voice-wiring.test.mjs` mit sechs Node-Vertragstests; `app/src/test/java/at/heinz/deviceai/jarvis/JarvisVoiceConversationPolicyTest.kt` mit fünf JUnit-Methoden.

**UNAVAILABLE / NOT_RUN:** Android Gradle, komplette bestehenden Android-/Node-/Worker-Regressionen, TTS/ASR am Samsung, LLM-Inferenz, APK-Build, Geräteinstallation und automotive/Haus-Geräte-API. Ein statischer Quellpfad-Nachweis ist kein tatsächlicher Android-Lauf.

## Nächste Schritte

1. Authentifizierten Checkout dieses Branch-HEAD bereitstellen; die beiden neuen Test-Dateien und bestehenden `coding-agent-speech-wiring.test.mjs` und `android-integration-wiring.test.mjs` unter Node v22.x sowie die Android-JUnit-Suite vollständig ausführen.
2. Eventuelle Kompilierungs- oder Verhaltenstests nach reproduziertem Rot gezielt reparieren, committen, pushen. Ohne exakten Testnachweis kein Merge und keine fertige APK behaupten.
3. Auf dem Fold7 die realen Tap-to-Talk-/Text-to-Speech-Runden und die Mikrofon-Freigaben messen; Android-Recognizer kann trotz OFFLINE-Präferenz cloudgestützt sein.
4. Erst danach ein wirklich lokales ASR-, Authentifizierungs- und Sprach-ToolGate mit der bestehenden JPI1-Grenze verbinden. **Keine** automatische Herdsteuerung, Kameraüberwachung oder Profilbildung ohne Besitzerfreigabe.

## Rückfallweg

Den isolierten PR #250 nicht übernehmen; der PR-#247-Zweig bleibt unberührt. Bereits gespeicherte Besitzerfakten, App-Signatur und ToolGate bleiben unverändert. Bei Ausfall der sprachbasierten Route erhält der Besitzer weiterhin die vorhandene manuelle Mikrofoneingabe und den normalen Text-Composer.
