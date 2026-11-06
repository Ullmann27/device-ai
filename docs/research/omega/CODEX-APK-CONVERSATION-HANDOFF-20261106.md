# APK-Integration: Gesprächs- und Arbeitsjournal-Korrekturen

Stand: 06.11.2026. Verantwortlich: Conversation-Audit-Bot, Integration durch Root.

- Basis: `642dc0fe9f5deca306a3ad1f5be40e5e3055abc5` (Fable, PR #574).
- Code: `805b0b694714d3ec15c88b8068f93228a9488b25`.
- Branch: `codex/continuum-apk-conversation-fixes-20261106`.
- Ergebnis: reale gezielte JVM-Tests bestanden. Dieser Teilabschluss belegt keine Modellantwort, Android-Oberfläche oder APK.
- Remote-Sicherung und Übernahme in den gemeinsam getesteten APK-Kandidaten erfolgen durch die Integrationsleitung.

## Nachgewiesene Fehler und Reparaturen

| Bereich | Korrektur |
| --- | --- |
| Negiertes Weiterarbeiten | Nur ein vollständiger direkter Fortsetzungsauftrag erzeugt `ContinueOpen`. Negationen, Zitate, berichtete Rede, Fragen und Bedingungen starten diesen Pfad nicht. |
| Veralteter Fokus | `davon`, `davor` und Weiterarbeit werden gegen den aktuellen Journal-/Ledgerstand aufgelöst. Gespeicherte Entwürfe werden nicht erneut als offen gestartet. |
| Widerrufe und verschwundene Belege | Aktuelle Widerrufe bleiben sichtbar. Nicht mehr vorhandene Quellen werden nicht aus einem alten Fokus als aktuelle Tatsachen wiederholt. |
| Laufender korrigierter Entwurf | Nur `SAVED` gilt als umgesetzt; ein laufender Entwurf mit `nextStep == null` gilt nicht als abgeschlossen. |
| Gesprochene Entscheidungsfrage | Eng verankerte direkte Fragen funktionieren ohne Fragezeichen und mit `JARVIS,`-Anrede. Zitierte oder indirekte Fragen werden nicht gekapert. |
| Änderungstag | Änderungsabfragen verwenden bei Widerrufen `revokedAt`; Entscheidungsabfragen behalten `assertedAt`. `heute/gestern` wird in der Änderungsfrage erkannt. |
| Selbstständige Wortfrage | `Was heißt das Wort Haus auf Englisch?` und benannte Akronyme/Symbole benötigen keine früheren Gesprächszüge. Wirkliche Rückbezüge bleiben Folgefragen. |
| Aktueller Arbeitsstand | `gerade offen` umfasst frühere offene Arbeit. `gerade/eben gemacht` bleibt auf den heutigen Arbeitstag begrenzt. |

Der Fokus enthält weiterhin ausschließlich Verweise auf die bereits gezeigten Datensätze. Neue, nicht gezeigte Aufgaben werden beim Fortsetzen eines bestehenden Fokus nicht eingeschoben. Keine zusätzliche Gedächtnisablage, keine Persistenz-/Ledgerformatänderung, kein neuer Reasoningpfad. Öffentliche Methodensignaturen unverändert.

## Reale Tests

| Klasse | GREEN |
| --- | ---: |
| `WorkDecisionRecallTest` | 18/18 |
| `WorkRecallTest` | 5/5 |
| `LocalConversationFollowUpPolicyTest` | 4/4 |
| Gesamt | 27/27 |

**RED:** Dieselben 27 Tests gegen unveränderte Produktionsdateien aus Basis `642dc0fe9f5deca306a3ad1f5be40e5e3055abc5`: 12 Fehler, 15 bestanden. **GREEN:** 27 bestanden, keine Fehler, keine übersprungenen Tests. Lokaler JDK 17.0.20.1 (Eclipse Adoptium), kotlinc 2.4.0, JUnit 4.13.2, Hamcrest 1.3, org.json 20240303. Produktionsklassen echt kompiliert; keine Android-/Modell-Stubs in diesem Testpaket. Compiler jeweils Exit 0, GREEN JUnit Exit 0.

Rohbelege: `docs/evidence/codex-apk-conversation-20261106/red-junit.txt`, `green-junit.txt`, `source-sha256.txt`. Der Hash-Katalog umfasst alle sechs kompilierten Produktionsdateien und drei Testdateien.

### Reproduzierbarer gezielter Aufruf

Mit installiertem JDK 17, kotlinc, JUnit 4.13.2, Hamcrest 1.3 und org.json 20240303; `conversation_cp` enthält die drei JAR-Pfade, `conversation_stdlib` die Kotlin-Standardbibliothek:

```bash
conversation_main=app/src/main/java/at/heinz/deviceai/cognitive
conversation_tests=app/src/test/java/at/heinz/deviceai/cognitive
conversation_out=$(mktemp -d)
kotlinc \
  "$conversation_main"/{CockpitWorkJournal,WorkRecall,WorkDecisionRecall,LocalConversationFollowUpPolicy,OwnerCorrectionIntent,OwnerDocumentIntent}.kt \
  "$conversation_tests"/{WorkRecallTest,WorkDecisionRecallTest,LocalConversationFollowUpPolicyTest}.kt \
  -cp "$conversation_cp" -jvm-target 17 -nowarn -d "$conversation_out"
java -Xmx256m -cp "$conversation_out:$conversation_cp:$conversation_stdlib" org.junit.runner.JUnitCore \
  at.heinz.deviceai.cognitive.WorkRecallTest \
  at.heinz.deviceai.cognitive.WorkDecisionRecallTest \
  at.heinz.deviceai.cognitive.LocalConversationFollowUpPolicyTest
```

Für den RED-Vergleich stammen nur die sechs Produktionsdateien aus dem Basiscommit; die zwölf neuen Gegenbeispiele bleiben im Testsatz. Keine Rücksetzung eines aktiven Branches erforderlich.

## Übergabe und Grenzen

`AgentActivity.kt` wurde absichtlich nicht parallel verändert. Dort sind getrennt zu prüfen: ein leerer neuer Recall muss den alten `decisionFocus` löschen; asynchrone Recall-/Fortsetzungsantworten brauchen die aktuelle STOP-/Auftragsbindung. Diese Integrationsarbeiten liegen bei Root.

Auf dem zusammengeführten Stand zusätzlich `ConnectedConversationCatalogTest`, `ConversationUnderstandingCatalogTest`, `LocalFollowUpTaskFrameTest` und die Android/UI-Regression ausführen. Deren frühere Ergebnisse werden hier nicht übernommen. Echte LiteRT-Antwortqualität, Fold7, Android-Lint und APK-Build wurden von diesem Bot nicht ausgeführt.

Architekturverträge: EXP-05 lokaler Modellpfad, EXP-06 Provenienz und EXP-11 bestehende Aufgaben-/ToolGate-Anbindung bleiben erhalten; kein Experiment wird durch diesen Testlauf hochgestuft. Rollback: Codecommit `805b0b694714d3ec15c88b8068f93228a9488b25` im Integrationsbranch revertieren; bestehende Daten benötigen keine Migration.


## Nachtrag: unabhängige Integrationsreview

Die Root-Review reproduzierte eine weitere eigenständige Frage: `Was bedeutet das Leben?` wurde wegen des Artikels `das` noch als zwingende Anschlussfrage behandelt. Code-Nachtrag `1d47b181659dcb24df8b7733a330773555493ff1` begrenzt das demonstrative `das` auf Frageende oder eine konkrete Folgephrase (`für`, `jetzt`, `nun`, `dann`, `konkret`); benannte Nomen bleiben eigenständige Fragen. Nicht benannte Begriffe (`das Wort?`) und echte Bezüge (`Was folgt daraus logisch?`) bleiben auf früheren Kontext angewiesen. Derselbe Referenzausdruck wird am Nachrichtenanfang und innerhalb einer Nachricht verwendet.

Mit dem zusätzlichen Gegenbeispiel: **RED 28 Tests, 1 Fehler** auf unverändertem Code `805b0b694714d3ec15c88b8068f93228a9488b25`; **GREEN 28/28** auf `1d47b181659dcb24df8b7733a330773555493ff1` (WorkDecisionRecall 18, WorkRecall 5, FollowUpPolicy 5). Belege: `red-article-definition.txt`, `green-article-definition.txt`, `source-article-sha256.txt` im obigen Belegverzeichnis. Die ursprünglichen RED/GREEN-Belege bleiben unverändert erhalten. Keine weiteren Produktionsdateien geändert.

## Nachtrag: PR #575, natürliche Anschlussfragen

Die Review benannte zwei reproduzierte Lücken der begrenzten Folgefrageerkennung: Die Formen `Was heißt das genau?`, `Was bedeutet das eigentlich/also?`, `Was heißt das denn?` und `Was bedeutet das, was du gesagt hast?` verloren ihren Kontextbezug. Außerdem unterdrückte eine Definitionsfrage am Nachrichtenanfang die folgende Konsequenzfrage, etwa `Was bedeutet Inflation? Was bedeutet das für unseren Plan?`.

- RED-Testcommit: `0ae9c11c7eb51086658ebf6799eb1277c611be63`; unveränderte Produktion, **36 Tests: 7 Fehler, 29 bestanden**.
- GREEN-Codecommit: `8f340ddcec28aaf7e2cafa83861ee0bfb93b206c`; **36/36 bestanden**, keine Fehler oder Skips. WorkDecisionRecall 18, WorkRecall 5, FollowUpPolicy 13.
- Nur `LocalConversationFollowUpPolicy.kt` wurde produktiv verändert: vier explizite Fragepartikeln und der Relativsatz `das, was …` ergänzen den vorhandenen Referenzausdruck. Im weichen Kontextpfad prüft eine anfängliche Definition weiterhin auf eine spätere Konsequenzfrage.
- Der harte Ohne-Kontext-Filter für Nachrichten mit anfänglicher Definition bleibt unverändert. Eigenständige Definitionen von `genau`, `eigentlich`, `denn`, `also`, `jetzt`, Artikel mit Nomen und mehrere eigenständige Fragen bleiben ohne Kontextpflicht.

Echter lokaler Kotlin/JVM-Lauf derselben sechs Produktionsklassen mit den drei Testklassen; keine Stubs. Compiler jeweils Exit 0, RED JUnit Exit 1, GREEN JUnit Exit 0. Toolchain wie oben. Belege im bestehenden Verzeichnis: `particles-red-compile.txt`, `particles-red-junit.txt`, `particles-green-compile.txt`, `particles-green-junit.txt`, `particles-source-sha256.txt`. Die Hashliste bindet beide Quellstände einschließlich der Tests.

Der Nachweis betrifft die Kontextentscheidung der vorhandenen Policy. Es wurde keine Modellantwort erzeugt und kein reales Sprach-, Geräte- oder Werkzeugergebnis behauptet. `LocalFollowUpTaskFrame` und die weitere Modellanbindung wurden in diesem Zusatz nicht verändert; Android-/APK-Regression führt Root auf dem integrierten Stand aus.

### Abschluss desselben Reviews: Definitionsausnahme je Satz

Die Folgeprüfung reproduzierte zwei weitere Mehrsatzfälle auf dem obigen Zwischenstand: `Was bedeutet Inflation? Wie passt das zu unserem Plan?` und `Was bedeutet Inflation? Und die zweite Möglichkeit?`. Die erste Reparatur hatte spätere Rückbezüge noch auf Konsequenzfragen begrenzt.

- RED-Testcommit `ed479954cdf6a54554f07e3adc89298e1ae3da6a`: **38 Tests, 2 Fehler, 36 bestanden**.
- Finaler Codecommit `58fbeaca2b18b87f96e06fa85163b953228d18eb`: **38/38 bestanden**, keine Fehler oder Skips (WorkDecisionRecall 18, WorkRecall 5, FollowUpPolicy 15).

`benefitsFromPreviousOwnerTurn` prüft die Definitionsausnahme jetzt für jeden Satz einzeln; anschließend gelten dessen bestehende harten und weichen Kontextregeln. Die begrenzte Erkennung nutzt Satzzeichen mit folgendem Leerraum. Der harte Filter `requiresPreviousOwnerTurn` bleibt unverändert. Die zusätzlichen Kontrollen prüfen isolierte Definitionen mit `unser Plan` oder `die zweite Möglichkeit` sowie mehrere aufeinanderfolgende reine Definitionsfragen: Alle bleiben ohne frühere Kontextpflicht. Keine neue Gedächtnis-, Reasoning- oder Modellarchitektur.

Reale Compiler-/JUnit-Ausgänge wie zuvor: RED 0/1, GREEN 0/0. Belege: `sentences-red-compile.txt`, `sentences-red-junit.txt`, `sentences-green-compile.txt`, `sentences-green-junit.txt`, `sentences-source-sha256.txt`. Der vorherige 36er-Lauf bleibt als Zwischenstand erhalten; der finale gezielte Umfang ist **38 Tests**. Modell-, Audio-, Geräte- und APK-Aussagen bleiben von diesen JVM-Belegen ausgeschlossen.

### Schlussnachtrag: drei verbleibende Review-Varianten

Der lesende Abgleich von PR #575 mit Kandidat `48251610d9c9519678e9fc5ef652ad8286675b7f` bestätigte drei im alten Review-Vorschlag enthaltene Restfälle: `Was bedeutet das überhaupt?`, `Was bedeutet das hier?`, `Was heißt das praktisch?`. Auf ausdrücklichen Integrationsauftrag wurden ausschließlich diese drei Varianten ergänzt.

- Drei getrennte RED-Regressionen: Testcommit `353b871f9947af8721d697ac0445796d1548b92e`, **41 Tests: 3 Fehler, 38 bestanden**.
- Produktionsfix `a98869c3158736ac556a9a85693febc3069b9838`: nur `überhaupt|hier|praktisch` im vorhandenen `consequenceReferent` ergänzt, keine weiteren Regeländerungen.
- Echter GREEN: **41/41 bestanden**, keine Fehler oder Skips. WorkDecisionRecall 18, WorkRecall 5, FollowUpPolicy 18. Die bestehenden Definitions-, Artikel-, Mehrsatz- und Journalregressionen bleiben grün.

Compiler jeweils Exit 0; RED JUnit Exit 1, GREEN JUnit Exit 0. Dieselben sechs realen Produktionsklassen, dieselbe Toolchain, keine Stubs. Belege im bisherigen Verzeichnis: `review-rest-red-compile.txt`, `review-rest-red-junit.txt`, `review-rest-green-compile.txt`, `review-rest-green-junit.txt`, `review-rest-source-sha256.txt`. Der Hashkatalog enthält beide Quellstände und sämtliche neun kompilierten Produktions-/Testdateien. Die älteren Läufe bleiben als Zwischenstände erhalten; letzter gezielter Umfang jetzt **41 Tests**.

Keine explorativen Varianten, Architektur-, Root- oder Publisheränderung. Echte Modellantworten, Audio, Fold7 und APK-Abnahme bleiben außerhalb dieses Nachweises; die abschließende Kandidatenintegration führt Root aus.
