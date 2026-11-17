# Dialog V – Validierung, Stand 02.11.2026

## Tatsächlich ausgeführt (lokaler Host, nicht Fold7)
- `kotlinc-jvm 1.9.0` / JRE 21: Zwei reine Kotlin-Helfer (manuell in ein isoliertes lokales Testverzeichnis übertragene `NativeAppLookup`- und `JarvisVoiceProfile`-Logik) kompiliert; ausführbares JAR mit `-include-runtime` erstellt.
- `java -jar .../tests.jar`: **11/11** manuelle Contract-Harness-Checks bestanden: eindeutige Appsuche, Mehrdeutigkeit, Paket-Deduplizierung, ungültige Queries, natürliche deutsche Anfrage, Negationsschutz, Zeichen-/Bidi-Redaktion, fehlender Treffer, Markdown/URL-Sprache, Markdown-Codeauslassung, Ausgabe-Längenbudget.
- Die Kotlin-Harness-Dateien sind **manuelle Mirrors**, nicht byte-identisch zur produktiven Android-Datei nachgewiesen; damit beweist dieser Lauf weder den vollständigen GitHub-JUnit-Satz noch ein Android-Build.
- Im selben Forschungs-PR committed: native `NativeAppLookupTest`, `JarvisVoiceProfileTest`, `UnansweredOwnerTurnTest`; diese **JUnit-Tests sind auf dem neuen HEAD noch NOT_RUN**.

## Ausdrücklich nicht ausgeführt
- Vollständige Gradle/LiteRT/Android-JUnit/Lint/unsigned APK & AAB: **NOT_RUN**, Android SDK/Gradle nicht in der aktuellen lokalen Testumgebung installiert.
- Render-Android-QA für diesen Branch: **NOT_RUN** (Synapse-III-Render-PASS ist nur Eltern-QA und zählt hier nicht).
- Fold7 Accessibility/App-Start/Sprachqualität, Berechtigungstest und vollständige Gemma-Dialogfolge: **DEVICE_NOT_RUN**.
- KI-Modell für automatische Werkzeugwahl, generalisierte App-Interaktion, unabhängige Memory-Gates, TTS-Audio-Output: **NOT_RUN**.
- Fremdbibliothek Sherpa-ONNX, UI-TARS, OpenHands: **NOT_IMPORTED**. Vorhandenes TokenArc MIT-Adapterwerk gehört zu Fusion-IV-Elternstand.

## Sicherheits- und Funktionsgrenzen
- `find_app` ist READ_ONLY, liefert Kandidaten und startet keine App; das bestehende `launch_app` bleibt CONFIRM + expliziter Paketname + Accessibility-Fenster-Postcondition.
- Keine Tipp-/Wischaktion, kein automatisches Voice-Command-Submit, keine Rechteausweitung, kein Hintergrundmikrofon, kein Cloud-TTS.
- JARVIS-artige Stimme ist ausschließlich eine langsamer/tiefer eingestellte vorhandene Offline-Android-TTS-Stimme; keine Stimmkopie, kein Modellpaket.
- Unbeantwortete Besitzeräußerungen bleiben als **unbestätigter Sitzungskontext**, nicht in SWB1/EWM2, ohne Modellantwortbeleg. Lange Originale machen die Kontextbeweisansicht unvollständig statt falsche kurze Fakten zu bestätigen.

## Bevor eine Besitzer-Version möglich wäre
1. Exakten Branch HEAD abfragen, Gradle-Kotlin-Compile, vollständige JUnit, LiteRT, Lint und Android-Assembly auf diesem HEAD ausführen.
2. Prüfen, ob `find_app` vom lokalen Gemma-Tool-Selection-Format tatsächlich ausgelöst werden kann; reales ToolGate/Owner-Confirm/Launcher-Postcondition auf Fold7 testen.
3. Prüfen, ob installierte deutschsprachige Offline-TTS/ASR-Stimmen verfügbar sind und die Sprachqualität akzeptabel ist.
4. F08 neu mit fehlgeschlagenem Modellaufruf und späterer Negation/Fokuskorrektur auf der realen App reproduzieren. Unbestätigte Nutzertexte niemals automatisch in bestätigtes Gedächtnis verschieben.
5. Nur nach neuer kompletter QA und Besitzerfreigabe höheren Versionscode/Originalsignatur über bestehenden Releasekanal verwenden; nichts automatisch veröffentlichen.

Keine Änderungen an main, originalem Signierkanal, Besitzerinstallationsdaten oder Modellgewichten.

## Zusatzprüfung – source-exakt verifizierte Pure-Kotlin-Dateien

- `cognitive/NativeAppLookup.kt`: lokaler `git hash-object` **a0290ab81df5cbcc941196365a209fb335e213d1**, identisch mit dem GitHub-Blob-SHA zum Zeitpunkt dieser Prüfung.
- `cognitive/JarvisVoiceProfile.kt`: lokaler `git hash-object` **aeaa2ecd4876da9aa29ea15d70ade0b73b96cc1b**, identisch mit dem GitHub-Blob-SHA.
- Host `kotlinc-jvm 1.9.0`, OpenJDK 21: diese zwei **byte-identischen GitHub-Quellen** zusammen mit einem eigenständigen 20-Fall-Testharness kompiliert (`-include-runtime`) und via `java -jar` ausgeführt.
- Ergebnis: **20/20 PASS**. Geprüft: exakte/teilweise/mehrdeutige Appnamen, Duplikate, deutsche Sprachbefehle, verneinte Befehle, unbekannte App, ungültige/lange Queries, Unicode-Bidi/Newline-Härtung, Sortier-/Ergebnislimit, Paketname, Offline-TTS-Konfiguration, Markdown-/URL-/Codebereinigung, sichere Textkürzung und Budgetgrenzen.
- Dies ist ein **realer source-exakter Host-Lauf für genau zwei reine Kotlin-Produktionsdateien**, **nicht** die Android-JUnit-/Gradle-Laufzeit, nicht die übrigen Testdateien, nicht der DeviceToolExecutor oder das Fold7. Die früheren manuellen Mirrors sind getrennte 11/11-Vorprüfungen.


## Folgeprüfung – 23/23 source-exakt geprüfte Pure-Kotlin-Verträge

- Nach Erweiterung der deutschen Appanfragen auf höfliche Befehle, `suche/ finde` und optionale `App`-Präfixe/-Suffixe wurden die zwei tatsächlich abgefragten GitHub-Produktionsquellen mit den lokal kompilierten Bytes via `git hash-object` verglichen:
  - `NativeAppLookup.kt` **cf10ecfa022f18394ba7a509d7a6bb9dedf347a6**
  - `JarvisVoiceProfile.kt` **aeaa2ecd4876da9aa29ea15d70ade0b73b96cc1b**
- Mit `kotlinc-jvm 1.9.0` und OpenJDK21 gemeinsam kompiliert, lokales ausführbares JAR gebaut; **23/23** separate manuelle Pure-Kotlin-Assertions bestanden. Neue Prüfungen: `Bitte öffne die App WhatsApp`, `Suche mir die App Kamera`, `Starte WhatsApp App`. Die früheren 20/20 bleiben als historischer Prüfstand erhalten.
- **Geltungsbereich:** Diese zwei Pure-Kotlin-Dateien sind byte-identisch zu ihren zitierten Blob-SHAs. Das externe 23-Fall-Harness ist jedoch nicht als JUnit-/Android-Test ausgeführt worden. Kotlin-Klassen mit Android-Abhängigkeiten, `AgentActivity`, `AndroidInstalledAppLookup`, Mikrofon-Lifecycle und `LocalConversationContext` wurden **nicht** source-exakt auf Android oder mit vollständiger Gradle-Suite kompiliert.
- Erneut gilt: `GRADLE_NOT_RUN`, `LITERT_NOT_RUN`, `LINT_NOT_RUN`, `RENDER_SOURCE_QA_NOT_RUN`, `DEVICE_NOT_RUN`. Kein Owner-Release.
