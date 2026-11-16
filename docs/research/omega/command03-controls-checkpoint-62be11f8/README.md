# Command03: gesicherter Controls-Zwischenstand

**Status: PORTIERT / native Abnahme durch Ressourcenlage blockiert.**
Produktquelle: 62be11f898586eb3546f79a0f2b38ee53acc4d84 (PR854).
Dieser Satz ist kein vollständig grüner Android- oder APK-Abschluss.

## Tatsächliche Ergebnisse

| Bereich | Ergebnis |
|---|---|
| Releaseverträge auf der Produktquelle | 44 Python + 40 Node bestanden; keine Skips |
| Syntax | Drei YAML-Dateien, 32 Bash-Abschnitte, Signer und JavaScript bestanden |
| Originalassets | 35 Dateien / 15.864.219 Bytes bytegleich |
| Identischer Webadapter im Entwicklungslauf | 44/44 bestanden; aktueller 45er-Interop offen |
| Frühere native Quelle 68a0 | Haupt-/Testkompilation bestanden; 29 PASS / 5 FAIL bei 34 Tests |
| Korrigierter Merge 62be | Hauptcompile verlor Daemon; keine Kotlin-Compilerdiagnose |
| 37 HTML + 77 Unit + 32 Klassik + 6 Dokument | Noch nicht ausgeführt |
| Neue unsigned APK und unabhängiger Audit | Noch nicht ausgeführt |

Die sieben nativen Controls sind implementiert. Der Merge enthält zusätzlich
sämtliche drei zwischenzeitlich kanonisch ergänzten Navigationstests und den
vorhandenen Aktualisierungsaufruf. Die früheren Fixturefehler wurden gezielt
bearbeitet, aber noch nicht durch einen neuen erfolgreichen Lauf bestätigt.
Kein früheres Medien-/Navigationsresultat wird zum aktuellen Erfolg umgedeutet.

## Belege und Grenzen

release-contracts/ enthält die frischen Befehle, Quell-/Dateihashes, Ausgaben
und 84 tatsächlichen Ergebnisse. web-development/ dokumentiert den früheren
44er-Entwicklungslauf des identischen Adapters und den gezielten Rot-/Grünbefund;
es enthält keinen abschließenden frischen nativen Interoplauf.

historical-failed-junit/ erhält die drei unveränderten XML-Berichte mit den
fünf früheren Fehlschlägen. native-receipts/ bindet Diagnose, frühere Läufe
und den aktuellen Daemonverlust an die tatsächlichen Quellstände.

provenance/ dokumentiert das begrenzte Ressourcenfenster, den lokalen
WebView-Providerbefund und die vollständige Zuordnung der 37 aktuellen
Android-HTML-Tests zu den sieben Bedienbereichen. Der globale OOM-Zähler
stieg ohne eigene JVM; eine Kill-Zuordnung zum konkreten Gradle-PID ist
nicht nachgewiesen. Die positive Fokusassertion bleibt eine echte View-Prüfung.

Die produktive Anwendung bleibt nach der Dokumentationssicherung bytegleich
zur genannten Quelle. Die neue Controls-Arbeit wird erst nach tatsächlicher
nativer/browserseitiger Abnahme und neuem APK-Audit kanonisch integriert.
Die genauen Wiederaufnahmeschritte stehen in
[COMMAND03-NATIVE-CONTROLS-HANDOFF.md](../COMMAND03-NATIVE-CONTROLS-HANDOFF.md).

status.json trennt bestandene, frühere fehlgeschlagene und nicht ausgeführte
Prüfungen. SHA256SUMS bindet alle Dateien dieses Belegsatzes außer sich selbst.
Rohberichte wurden weder normalisiert noch für ein grünes Ergebnis verändert.
