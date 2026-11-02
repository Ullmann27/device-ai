# Continuum – Android-Dialog, native Appwahl und lokale JARVIS-artige Stimme

Forschungszweig: `research/continuum-autonomy-dialog-voice-20261102`, isoliert vom Fusion-IV-PR #345.
Gepinnte Basis: `cb700e03e4d6ed2ab5e8cd4283692e44280d9d62`.

## Arbeitsverträge

1. Native, **rein lesende** Suche nach installierten Apps aus gesprochener/geschriebener Bezeichnung; unklare Trefferauswahl darf nie autonom starten. Bestehender `launch_app`-ToolGate/Owner-Confirm und Accessibility-Vordergrundnachweis bleiben obligatorisch.
2. Offline-Sprachwiedergabe mit expliziter Besitzeraktivierung, begrenzter JARVIS-artiger Tonhöhen-/Tempo-Anpassung auf vorhandener Android-TTS-Stimme. **Keine Imitation** einer konkreten Darstellerstimme, keine Cloud-TTS und keine mitgelieferten Modellgewichte. Gesprochene Antworten aus dem bereits vorhandenen Chatpfad beibehalten.
3. Gesprächssicherheit/F08: Fehlschläge und Korrekturen nicht durch vermeintliche Erinnerung oder erfolgreiche Modellantwort kaschieren; keine fremden Appdaten als Besitzerfakt speichern.

Keine neue App, kein alternatives Gehirn, kein produktiver Modell-/Besitzerdatenschreibzugriff, kein Main-Merge, kein Signing, kein Fold7-Claim.

Status beim Öffnen: Nur Basiskopie, neuer Code **NOT_IMPLEMENTED / NOT_TESTED**. Etappen, exakte Commits, Tests und bekannte Einschränkungen nach tatsächlicher Ausführung hier ergänzen.

## Tatsächlich implementierte Quelländerungen

- **M1 Native Appsuche:** `cognitive/NativeAppLookup.kt` und `AndroidInstalledAppLookup.kt` mit Android PackageManager Launcher-Discovery. Tool `find_app` ist im bestehenden `ToolRegistry` als READ_ONLY registriert und in `DeviceToolExecutor` über den nativen Pfad vor Shizuku geroutet. Exakte Treffer, mehrdeutige Vorschläge, deterministische Sortierung, maximal zwölf Kandidaten, Negations-/Kontrollzeichen-Schutz. Appstart bleibt ein eigener bestätigungspflichtiger Auftrag mit überprüfter Vordergrundbeobachtung (Fusion IV).
- **M2 Lokale JARVIS-artige Stimme:** `cognitive/JarvisVoiceProfile.kt` wird durch die vorhandene TextToSpeech-Initialisierung und `speakBootstrap` in `AgentActivity` benutzt: Pitch 0.86, Rate 0.93, nur bereits lokal verfügbare deutsche Stimme, Markdown-/Code-/URL-Filter, höchstens 480 gesprochene Zeichen. Die vorhandene Voice-AN/AUS-Besitzeroption wird respektiert. Keine geklonte Schauspielerstimme oder bezahlte API.
- **M3 Gesprächskontext nach fehlgeschlagener Inferenz:** `LocalConversationContext.recordUnanswered` erfasst die unveränderte Benutzeräußerung nur als unbestätigten, flüchtigen Sitzungskontext. `AgentActivity` ruft es bei Modellfehlern oder blockierten lokalen Antworten auf. Der alte Kontext wird für gleiche Nicht-Screen-Sitzung nicht mehr pauschal gelöscht; keine Übernahme in SWB1/EWM2 und keine Autorisierung von Werkzeugen. Bei unvollständigem Original wird das gesamte historische Beweisfenster konservativ unsicher.
- Drei neue JUnit-Testklassen im Forschungszweig: `NativeAppLookupTest`, `JarvisVoiceProfileTest`, `UnansweredOwnerTurnTest`.

## Prüfnachweis und offene Gates

Siehe [QA-RESULTS](../../evidence/dialog-v-20261102/QA-RESULTS.md): **11/11** lokale Kotlin-Contract-Checks auf manuellen Pure-Kotlin-Spiegeldateien (kein APK- oder GitHub-Gradle-Beweis). Neue JUnit-Tests noch **NOT_RUN**; vollständige Android-/LiteRT-/Lint- und Render-QA **NOT_RUN**. Fold7 **DEVICE_NOT_RUN**. Vom Modell autonom ausgewählter `find_app`-Call noch **NOT_RUN**.

Vor Release: neue Quellen in exakten Render-Android-Build und Unit-/Lint-/LiteRT-Prüfungen testen, echte Fold7-Gemmapfade, Android-Appwahl mit ToolGate-Freigabe, lokale Offline-Stimmqualität und F08-Regression prüfen. Keine Selbst- oder Main-Merges und keine APK-Signierung ohne Besitzerfreigabe. **Code-Integration ≠ bestätigte Gerätefunktion.**

## Sauberer nächster Agenten-Einstieg

1. Remote-FusionIV PR #345 und diesen PR #346 lesen, aktuelle SHAs/Abstammung vergleichen, neue FusionIV-Commits nicht blind überschreiben.
2. Neue JUnit-Tests auf exakt diesem HEAD starten; zuerst Kotlin-Kompilierbarkeit und Android-Manifest-/Toolcall-Protokolle überprüfen.
3. F08-Korrektur mit getrennten Sessions, verneinten Benutzerangaben und fehlgeschlagenem Gemma realistisch simulieren; keine alte positive Aussage als Fakt retten.
4. `find_app` bei echten Appnamen und Mehrdeutigkeit über Gemma→ToolGate→Android-Klassen nachweisen; Original-Confirm + `VerifiedForegroundLaunch` erhalten.
5. Auf dem Fold7 nur durch ausdrückliche Benutzerfreigabe originalsignierten Gesamt-Release erproben.

Alle Änderungen sind auf eigenem öffentlichen GitHub-PR innerhalb des privaten offiziellen Repositories reviewbar und einzeln rücksetzbar. Keine Änderung von `main`, produktiver Geräteinstallation, Credentials, App-Paketidentität, owner-gated Wertdaten oder Gewichten.


## Nachfolgende kleine Korrekturen auf demselben Branch

- F08 ergänzt: auch der frühe Pfad `Gemma not READY` bewahrt die Originalbenutzeräußerung als unbestätigten flüchtigen Sitzungskontext, statt mit `addAssistantMessage` den Gesprächskontext zu löschen.
- Die Kotlin-Appnamen-Suche versteht zusätzlich sichere, häufige deutsche Formulierungen mit `Bitte`, `Suche mir die App ...` und dem App-Suffix. Die Ausgabe ist weiterhin reine read-only Vorschlagsliste und kein automatischer Appstart.
- Android-Mikrofon-Berechtigung: verzögerte Permission-Callbacks sind an Chat-Sitzung, Generation, aktiven Foreground-Zustand und gegebenenfalls das ursprüngliche Entwicklerdialogfenster gebunden; nach Sitzung-/Dialogwechsel kann kein nachträgliches Starten der Aufnahme erfolgen. Neuer Lifecycle-Guard **noch nicht mit Android ausgeführt**.
- `find_app` kennzeichnet Ergebnisse ausdrücklich als `VISIBLE_LAUNCHER_ACTIVITIES_ONLY`; Nichttreffer sind wegen PackageManager-Sichtbarkeit kein Nachweis, dass eine App nicht installiert ist.
- Quellbyteidentischer Host-Test der reinen Kotlin-Appsuche und TTS-Aufbereitung nach der Erweiterung: **23/23 PASS**, GitHub-Blob-SHAs und exakte Grenzen im [QA-Report](../../evidence/dialog-v-20261102/QA-RESULTS.md). Android-Gradle, JUnit, LiteRT, Render und Fold7 **NOT_RUN**.

**Nicht als fertig deklarieren:** Die reguläre App kann noch keine frei formulierten, kontinuierlichen Sprachdialoge automatisch absenden oder Apps frei tippen/wischen. Lokaler ASR wird durch eine Benutzeraktion gestartet; Aufnahme resultiert vorerst im Eingabefeld, und `launch_app` bleibt separat zustimmungspflichtig. Die JARVIS-artige Klangfarbe ist eine lokale TTS-Parameterwahl, deren Klangqualität vom Fold7 offline nachzuweisen ist.
