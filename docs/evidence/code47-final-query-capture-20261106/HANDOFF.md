# Code47 Query- und Capture-Abnahme

## Quellbindung

- Basis: `c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e` (PR578).
- Geprüfter Reparaturstand: `3bac4166c9fcd0dca4889ca3eee9ca29f2a1daff`, [PR582](https://github.com/Ullmann27/device-ai/pull/582).
- Diese Belegsicherung ändert ausschließlich `docs/evidence/code47-final-query-capture-20261106/`.
- Produkt- und Testquellen bleiben zum geprüften Reparaturstand unverändert. Hier wurde kein Test erneut gestartet.

## Änderung und Anschluss

`ToolRegistry.find_app` und `NativeAppLookup.search` verwerfen ISO-Steuerzeichen sowie Unicode-FORMAT-Codepoints. Die bestehenden 1..80-Zeichen- und Nichtleer-Regeln bleiben erhalten; X und lesbare Unicode-Namen funktionieren weiterhin. Die neue Regression ruft den echten ToolGate und NativeAppLookup auf. Ungültige Queries erreichen keinen Runner; eine erfolgreiche Suche autorisiert keinen App-Start. Der ursprüngliche AppLookupToolPolicyTest bleibt unverändert.

Die zwei beauftragten Node-Dateien prüfen den bestehenden Pfad `generateLocal → Capture → Deadline/Terminal` und `Capture-Snapshot → gebundene Preview`. Sie sichern Fortschrittszeit, exaktes Cap, STOP, späte Callbacks, native Cancellation, Bot-Deadline/UTF8-Bytebudget und erhaltenen Teiltext. 20 kontrollierte In-Memory-Quelländerungen müssen an denselben Prüfungen scheitern. AgentActivity, Capture und Deadline wurden nicht geändert.

## Bereits ausgeführte Ergebnisse

| Prüfung | RED | GREEN |
| --- | --- | --- |
| Kotlin/JUnit, vier echte Klassen | 24 Tests / 4 Fehler / Exit 1 | 24 Tests / 0 Fehler / Exit 0 |
| Zwei Node-Vertragsdateien | 9 Tests / 4 Fehler / Exit 1 | 11 Tests / 0 Fehler / Exit 0 |
| Kontrollierte Quellmutationen | — | 20 abgelehnt |

Kotlin-Klassen: AppLookupToolPolicyTest, AppLookupReadableQueryTest, NativeAppLookupTest, OwnerAppActionFlowTest. Kotlin 2.4.0 / Temurin JDK17.0.20.1+1 / JUnit4.13.2 / 256 MiB Heap. Beide geänderten Produktionsdateien und vier Testklassen wurden frisch kompiliert. Abhängigkeiten stammen aus isoliert kopierten echten SDK-Produktionsklassen der Basis; keine Stubs. Geänderte Klassen standen zuerst im Laufzeit-Classpath.

Die 31 Format-Codepoints werden allein, mehrfach und eingebettet geprüft, einschließlich ergänzender Unicode-Codepoints. Dazu Blank/Control, 80/81, X, Ö, 日, arabische Buchstaben und kombinierte Akzente.

## Belege lesen

- `final-receipt.json`: Ergebnisübersicht und exakte Quellhashes; `final_commit` bezeichnet weiterhin den getesteten Code, nicht diesen späteren Dokumentationscommit.
- `red/green-*-command.json`: tatsächliche Befehle, Pfade, Zeitpunkte und Exitcodes.
- `red/green-compile.txt`: originale leere Compiler-Ausgabe bei Exit 0.
- `red/green-junit.txt`: vollständige JUnit-Rohausgabe.
- `node-red.txt.gz`: vollständige ursprüngliche TAP-Ausgabe, verlustfrei komprimiert; `gzip -dc node-red.txt.gz` stellt sie wieder her.
- `node-red-summary.txt`: abgeleitete kurze TAP-Übersicht; der vollständige Rohbeleg bleibt maßgeblich.
- `node-green.txt`: vollständiger GREEN-TAP-Beleg einschließlich Negativkontrollen.
- `evidence-manifest.json`: Dateihashes und Hash/Größe der unkomprimierten RED-Ausgabe.
- `run-focused.py`: unverändertes damals verwendetes Hostskript mit ursprünglichen absoluten Pfaden. Ein neuer Host benötigt entsprechend bereitgestellte SDK-Klassen und Toolchain; keine dieser Abhängigkeiten ist hier als Binärdatei kopiert.

Node-Befehl: `node --test --test-reporter=tap scripts/continuum-fold7-stream-scroll.test.mjs scripts/continuum-local-chat-streaming.test.mjs`.

## Übergabe und Grenzen

Root integriert PR582 in den gemeinsamen Kandidaten und prüft dessen exakten Gesamtstand. Kein Gradle-Lauf, keine Geräte-/Modellabnahme, kein APK-/Signatur- oder Veröffentlichungserfolg durch diese Belege. Der laufende Gesamtbuild der Basis wird nicht für den Reparaturstand beansprucht. EXP-02/EXP-07: begrenzte Gegenbeispiel-Reparatur bei unverändertem ToolGate. Rückfall: die fünf Reparaturdateien auf den Basisstand zurücknehmen; keinen fremden Branch zurücksetzen.
