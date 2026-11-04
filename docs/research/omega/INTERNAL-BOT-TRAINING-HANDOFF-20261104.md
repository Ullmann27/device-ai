# Interne Continuum-Bots: fachliche Übungen und Korrekturrückmeldung

Stand 04.11.2026. Status: Implementierung veröffentlicht; endgültige Offline-Abnahme nach zwei unerklärten Gradle-Prozessabbrüchen offen. Echte Bot-Modellversuche: **0 / NOT_RUN**. Gewichtsänderungen: **0**.

## Korrigierter Besitzerauftrag
Heinz meint die bereits vorhandenen internen Bots seiner Continuum-Architektur. Sie sollen Reparatur-, Implementierungs- und Avatar-Aufgaben üben und aus überprüfbaren Ergebnissen lernen. Die frühere Interpretation als Verbindungs-/Geräteproblem war zu eng.

## Veröffentlichter Stand und Zuständigkeit

- Basis: [Bot-PR #493](https://github.com/Ullmann27/device-ai/pull/493), `ee2fa0ee5567e5d34bcdfe229425a71df9818183`.
- Eigener Branch: `codex/continuum-internal-bot-training-20261104`, [Draft-PR #516](https://github.com/Ullmann27/device-ai/pull/516) auf dem Bot-Branch; kein Main-Merge.
- Erster Anschluss: [b1701e7e3bf52047efff05ae33f0ba602dcc3d4b](https://github.com/Ullmann27/device-ai/commit/b1701e7e3bf52047efff05ae33f0ba602dcc3d4b).
- Endgültiger Quellstand einschließlich V2-Kompatibilität: [90cc7373b2c3bac1d7b9f77e582958e359d00b20](https://github.com/Ullmann27/device-ai/commit/90cc7373b2c3bac1d7b9f77e582958e359d00b20).
- Experimente: EXP-07 Worker Academy und EXP-11 Fachbots; bestehende Laufzeit gemäß EXP-05.

Drei Helfer bearbeiteten getrennt Reparatur-/Implementierungsfälle, den vorhandenen Botdialog-Anschluss und Avatarfälle. Anschließend erfolgten gegenseitige Quellprüfungen. Der dabei gefundene Verlust der sichtbaren Bewertung beim erneuten Öffnen eines Botdialogs ist behoben; normale Gespräche behalten zudem ihr bisheriges Speicherformat.

## Was angeschlossen ist

Die vorhandenen Profile teilen eine Modelllaufzeit und besitzen private Gesprächskontexte. `ChiefBotConversation.prepare()` erkennt die explizite Übungsanfrage. `AgentActivity.runBotDialogue()` verwendet weiterhin `generateLocal()` und `localRuntime.streamThinking()`. Erst die akzeptierte terminale Antwort wird in `complete()` ausgewertet. Kein vorbereiteter Lösungstext ersetzt die Modellausgabe.

`ChiefBotPractice` erwartet genau ein JSON-Flachobjekt mit den verlangten Stringfeldern. Der vorhandene `StrictJson`-Parser verwirft unter anderem Zusatzfelder, doppelte Schlüssel, Codeblöcke und nachgestellten Text. Das Ergebnis unterscheidet `CORRECT`, `INCORRECT` und `INVALID_RESPONSE`. Antwort und Programmauswertung bleiben getrennte Felder. Das Feedback ist über SHA-256 und erneute Klassifikation an genau die gespeicherte Antwort gebunden und wird in der Live- sowie der historischen Anzeige unter **Programmauswertung** dargestellt.

Die Aufgabenstellung und die Feldnamen gelangen ins Modell. Sollantwort und Korrekturerklärung gelangen erst nach dessen Antwort in den privaten Verlauf. Folgeaufgaben können frühere Korrekturen im begrenzten Gesprächskontext erhalten. Wird dieselbe versionierte Übung wiederholt, werden ihre eigenen früheren Antworten und Korrekturen aus diesem Prompt ausgelassen. Der vorhandene Verlauf bleibt auf 16 vollständige Einträge und 48 KiB Text einschließlich Feedback begrenzt.

## 18 Fachübungen

| Bestehender Bot | Übungen 1–4 | Transferfälle 5–6 |
|---|---|---|
| Kora, `bot-kora` | Fehler zuerst reproduzieren; explizite `0` gegenüber fehlendem Wert; minimale Auswahlkorrektur unter Erhalt der Reihenfolge; unabhängige Nachprüfung | `false`/leerer Wert gegenüber fehlend; erfolgreicher Prozess ohne ausgeführte Tests |
| Link, `bot-link` | Auftragsidentität; doppelte Zustellung; volle Mailbox und erneute Zustellung; verspätetes Ergebnis nach Abbruch | falscher `requestHash`; unbestätigter Worker-Abbau |
| Iris, `bot-iris` | Zustands-/Ausdruckspriorität; Sprachabbruch; Blicksignal an Zeit- und Konfidenzgrenzen; widerrufene Kameraberechtigung | alte Äußerung gegenüber neuer Sprache; Priorität von Aufgabenblick und Beobachtung |

Die Iris-Sollwerte verwenden die vorhandenen `AvatarDirector`-/`AvatarContract`- und `AvatarCameraPolicy`-Klassen mit lokalen Ereignissen. Die Link-Tests vergleichen einschlägige Fälle mit `ChiefWorkspaceWiring` und `AgentMailbox`. Kora übt explizite Reparaturentscheidungen in begrenzten Beispielen. Die Aufgaben prüfen die Anwendung der angegebenen Regeln; sie sind kein unabhängiger Nachweis allgemeiner Coding-Fähigkeit, kein ausgeführter Reparaturauftrag und kein gerendertes Avatarergebnis.

## Bedienung nach Integration in eine passende App-Version

Im vorhandenen Botdialog Kora, Link oder Iris auswählen und `Training 1` bis `Training 6` senden. `Übung 1` bis `Übung 6` funktionieren ebenfalls; `Training` wählt Aufgabe 1. Die Hinweise stehen im Profil der unterstützten Bots. Die letzten beiden Aufgaben jeder Rolle sind Transferfälle. Andere Anfragen bleiben normale Botdialoge.

Diese Änderung wurde nicht als APK gebaut, signiert oder auf einem Gerät installiert. Die Befehle sind deshalb keine Behauptung, dass die bereits installierte Besitzer-App sie schon unterstützt. Für einen echten Versuch muss die bestehende lokale Modelllaufzeit in einer App mit diesem Quellstand antworten. Modellantwort, Bewertung, verwendeter Modellstand und Reihenfolge der Aufgaben müssen dabei als tatsächliche Versuche ausgewertet werden; Entwickler-Fixtures zählen nicht mit.

## Abnahme und Reproduktion

Erster Offline-Lauf am Quellstand `b1701e7e`: **70 Tests, 0 Fehler, 0 übersprungen**, vollständige Android-Kotlin-/Java- und Unit-Test-Kompilierung, 135,626 Sekunden. Die anschließende Kompatibilitätskorrektur erweitert den bestehenden Codec-Test um bytegenaue V2-Ausgabe und gemischte V3-Verläufe.

Der zweite Lauf auf `90cc7373` endete nach 134,048 Sekunden mit `Gradle build daemon disappeared unexpectedly` während `compileDebugKotlin`: Exit 1, kein Timeout, Quellen unverändert, keine konkrete Compiler-/OOM-/JVM-Crashdiagnose. Der identische dritte Lauf endete nach 171,402 Sekunden mit demselben Prozessverlust. In beiden Läufen wurden alle sieben älteren XML-Berichte als `STALE_RESULT_REJECTED` verworfen; beide belegen **0 aktuelle Tests**. Die Ursache des Prozessverlustes ist nicht gesichert. Der nächste vollständige Offline-Lauf verwendet deshalb einen Gradle-Worker und 1.536 MiB Java-Heap statt zwei Workern und 2 GiB. Es werden weiterhin dieselben tatsächlichen Quellen und sieben Klassen mit `--rerun-tasks` geprüft. Die endgültige Abnahme wird hier nach Abschluss ergänzt.

Der Runner `tools/internal-bot-practice/run_checks.py` verwendet eine bereits vorhandene Gradle-/JDK-/Android-Toolchain vollständig offline. Er kompiliert die tatsächlichen Quellen ohne Stubs oder Quellausschlüsse und führt sieben ausgewählte Klassen aus. Frische XML-Zeitstempel, alle ausgewählten Klassen, Testzahlen und unveränderte Quell-Hashes vor/nach dem Lauf sind erforderlich; ein alter Testbericht kann einen fehlgeschlagenen Build nicht ersetzen.

```bash
python tools/internal-bot-practice/run_checks.py \
  --gradle-home /workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1 \
  --gradle-cache /workspace/scratch/b7d23de0e715/build-toolchain/gradle-cache \
  --android-sdk /workspace/scratch/b7d23de0e715/build-toolchain/android-sdk \
  --java-home /workspace/scratch/b7d23de0e715/build-toolchain/jdk17 \
  --output /workspace/scratch/8f5df1fd155a/internal-training-checks-04
```

Für einen anderen Rechner vorhandene Pfade anpassen und ein neues, noch nicht vorhandenes Ausgabeverzeichnis wählen. Der Runner lädt keine Abhängigkeiten nach. `--rerun-tasks` verhindert die Übernahme alter Gradle-Task-Ergebnisse. Die ausgewählten Entwicklerprüfungen liefern 39 neue Fachübungs-/Anschlussfälle und 31 bestehende Gesprächs-/Profil-/Speicherfälle; ihre Modellantworten sind ausdrücklich Fixtures.

## Datengrenzen und Rückfall

V1 und V2 bleiben lesbar. Verläufe ohne Feedback werden bytekompatibel als V2 geschrieben. Sobald irgendein gespeicherter Eintrag Feedback enthält, wird V3 geschrieben; eine gewöhnliche Folgeantwort darf die früheren Übungsdaten nicht entfernen. Bestehende Objekt- und Profilspeicher werden wiederverwendet. Bot-, Profil-, Modell-, Session-, Abbruch-, Epoch- und Deadlineprüfungen bleiben maßgeblich; abgebrochene oder veraltete Antworten erzeugen keinen akzeptierten Abschluss.

**Downgrade-Grenze:** Ein älterer V1/V2-Leser kann einen tatsächlich verwendeten V3-Übungsverlauf nicht öffnen. Die vorhandenen Speicherpfade ersetzen dessen Inhalt dabei nicht; der private Botdialog meldet einen unlesbaren Kontext und bleibt blockiert. Frühere inhaltsadressierte Objekte sind keine automatisch wiederhergestellte Profilreferenz. Nach Nutzung der Übungsfunktion daher einen V3-fähigen Leser beibehalten oder eine gesondert geprüfte Datenmigration vorsehen; weder Verlauf löschen noch stillschweigend zurücksetzen. Vor erstmaliger Nutzung kann der isolierte Anschluss ohne Datenmigration aus der Integration herausgelassen werden.

Keine neue Warteschlange, Modelllaufzeit, Memory-Datenbank oder Receipt-Pipeline. Keine Änderung an Main, Paket/Signatur, Release/Installation, Modellpromotion, Offline-Schalter oder historischen Versuchssperren. Keine bezahlte KI-API und keine Actions. Keine bestätigten Besitzerfakten, Fähigkeiten, CCC1-Erfolge oder Gewichtsänderungen aus einer Übungsantwort. Der negative ECHO-Fehlerpfad wird parallel in #515 bearbeitet und hier nicht doppelt geändert.

## Nächster tatsächlicher Nachweis

Nach Integration und regulärer App-Abnahme die 18 Aufgaben über die vorhandene lokale Modelllaufzeit ausführen. Erst reale Antworten erlauben eine Aussage über Formatbefolgung, Regelanwendung und Nutzung früherer Korrekturen. Der hier vorbereitete Pfad ist kontextbasiertes Üben mit privatem Feedback; ob die Bots dadurch besser werden, ist noch ungemessen. Neuronales Gewichtstraining ist nicht Bestandteil dieser Änderung.
