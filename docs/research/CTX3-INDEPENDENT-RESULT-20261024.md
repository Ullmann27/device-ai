# CTX3 / M5 – unabhängiges TEST3-Ergebnis vom 24.10.2026

**FAIL – keine Promotion.** CTX3 senkt die falschen ersten Quellenzuordnungen gegenüber B0 von 77 auf 3,
verliert aber 98 korrekte Zuordnungen: **13 statt 111**. Damit scheitert die vorregistrierte Bedingung
`CORRECT >= B0`. Das Ergebnis stützt die Hypothese in ihrer vorregistrierten Form nicht.
CTX3 darf ausschließlich ein ausdrücklich als erfolglos ausgewiesener, abschaltbarer Forschungskandidat
mit Standard OFF bleiben. Dieser synthetische Vergleich erlaubt keine Übernahme produktiver Antworten.

## Umfang und unabhängige Erwartungen

Der unabhängige Autor erstellte 24 fiktive deutschsprachige Dialoge mit 198 Zügen und 125 aktuellen,
bestätigten Quellensätzen. Jeder Dialog hat mindestens sieben Züge; sechs Dialoge haben je zwölf Züge.
Die vorab festgelegten Einzelerwartungen umfassen **154 QUERY, 19 CLARIFY und 25 ABSTAIN**.
21 QUERY-Züge besitzen mehrere zulässige unterstützende Goldquellen. Die Goldquellen sind wortgetreue
Quellensätze aus dem jeweiligen Dialog; CLARIFY und ABSTAIN haben eine leere Goldmenge.

Die Annotation wurde vor Kenntnis der neuen Implementierung und ohne Lesen vorhandener
CTX-Korpusvorlagen erstellt. Sie umfasst unter anderem Negation und Referenzkorrektur, konkurrierende
Pronomen und Aliase, Personen, Tiere, Geräte, Apps, Dateien, Projekte, österreichische Formulierungen,
zusammengesetzte Namen und Themenrückkehr. Es wurden keine privaten Besitzerdaten verwendet.
Die Erwartungen wurden weder nach dem Freeze noch nach der Messung geändert.

Die vier Systeme wurden auf denselben 198 Zügen ausgewertet. Alle **792 Einzelvergleiche** einschließlich
Eingabe, Erwartung, Goldmenge, Quellen, Entscheidung und Ausgang bleiben im
[maschinenlesbaren Ergebnis](ctx3-independent-test3-report-20261024.json) erhalten.
Kein Fall wurde entfernt. QUERY zählt als CORRECT, wenn die **erste** zitierte Quelle zur Goldmenge gehört.
Das belegt weder eine vollständige Antwort noch die Abdeckung aller Goldquellen. Weitere zitierte Quellen
werden separat geprüft. Die erwartete notwendige Rückfrage ist ein eigener Ausgang; ein fehlender
Beleg zu einer bekannten Frage wird als MISSED gezählt.

## Systeme und vollständige Ergebniszahlen

B0 ist der unveränderte Gedächtnisdialog. B1 verwendet CTX1 default unverändert. B2 verwendet
`generalized=true` mit dem separat geprüften Negationsfix. CTX3 verwendet den neuen Shadow-Gate mit
derselben statischen Retrievalfunktion. B0/B1/B2 führen die vorhandene persistierte Dialoghistorie;
CTX3 verwendet den tatsächlichen Shadow-Rankingpfad ohne zusätzliche Historydatei.

| System | CORRECT | WRONG | MISSED | UNNEEDED_CLARIFY | CLARIFIED | MISSED_CLARIFY | ABSTAINED | CRASH |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| B0 | 111 | 77 | 5 | 0 | 0 | 1 | 4 | 0 |
| B1 | 2 | 1 | 136 | 15 | 8 | 11 | 25 | 0 |
| B2 | 84 | 50 | 15 | 32 | 8 | 1 | 8 | 0 |
| CTX3 | 13 | 3 | 109 | 31 | 6 | 13 | 23 | 0 |

Jede Zeile summiert sich auf 198 Züge. Für CTX3 stehen 109 ausgelassene bekannte Informationen und
31 unnötige Rückfragen den 13 korrekten ersten Belegen gegenüber. Von 19 erwarteten Rückfragen
werden sechs gestellt; 13 bleiben aus. Von 25 erwarteten Enthaltungen werden 23 korrekt eingehalten.
Die niedrige Fehlerzahl darf deshalb nicht ohne den Verlust korrekter Antworten berichtet werden.

## Zusätzliche falsche Quellen

Die erste Quellenzuordnung und weitere Belege sind getrennte Messgrößen. `ExtraWrongSources` zählt
jede zweite oder dritte zitierte Quelle außerhalb der jeweiligen Goldmenge, auch wenn die erste Quelle
korrekt war. Die Zahlen sind Quellenvorkommen; sie sind keine Anzahl verschiedener Dateien oder Dialoge.

| System | Züge mit erster Quelle | Weitere Quellen insgesamt | Davon außerhalb Gold | Quellen außerhalb bestätigter Dialogquellen |
|---|---:|---:|---:|---:|
| B0 | 188 | 310 | 255 | 0 |
| B1 | 3 | 5 | 4 | 0 |
| B2 | 134 | 192 | 156 | 0 |
| CTX3 | 16 | 18 | 12 | 0 |

CTX3 liefert somit **12 falsche Zusatzquellen** trotz nur drei falscher erster Zuordnungen.
B0 liefert 255 falsche Zusatzquellen, B1 vier und B2 156. Keine zitierte Quelle lag außerhalb der im
jeweiligen Dialog bereitgestellten bestätigten Quellen. Dieser Befund gilt für den statischen Korpus;
ein Quellenwiderruf während eines TEST3-Dialogs wird durch dessen festes Schema nicht abgebildet.
Die gesonderten Quellen-ID-/Widerrufstests sind davon zu unterscheiden.

## Unveränderte vorregistrierte Entscheidung

Die Kriterien stammen unverändert aus der
[Vorregistrierung](CTX3-INDEPENDENT-PREREG-20261024.md). Alle müssen erfüllt sein.

| Kriterium für CTX3 | Messwert | Ergebnis |
|---|---|---|
| `WRONG < B0` | 3 < 77 | PASS |
| `CORRECT >= B0` | 13 < 111 | **FAIL** |
| Keine CRASH | 0 | PASS |
| Zusätzliche falsche Quellen <= B0 | 12 <= 255 | PASS |
| Keine unberechtigten Quellen | 0 außerhalb der bestätigten Dialogquellen | PASS im statischen Korpus |
| Median <= 5 ms | 0,034532 ms | PASS |
| p95 <= 20 ms | 0,481886 ms | PASS |

**Gesamtentscheidung: FAIL.** Der Verlust korrekter Belege verletzt eine notwendige Bedingung.
Weder die Verringerung falscher Belege noch die niedrige Latenz kompensiert dieses Scheitern.
Es erfolgt keine Promotion, keine Produktkorrektur aus diesem Ergebnis und keine Anpassung der
CTX3-Algorithmen anhand von TEST3. Eine spätere Wiederverwendung wäre ausschließlich bekannte Regression.

## Laufzeit und begrenzter Zustand

| System | Median ms | p95 ms | Maximale explizite Zustandseinträge | Maximale Historydatei in Bytes |
|---|---:|---:|---:|---:|
| B0 | 1.274901 | 4.702115 | 0 | 4558 |
| B1 | 0.018828 | 0.066180 | 16 | 565 |
| B2 | 0.627937 | 1.144695 | 16 | 4695 |
| CTX3 | 0.034532 | 0.481886 | 2 | 0 |

Der beobachtete Heap-Peak beträgt **82,442,000 Bytes** für die gesamte JVM.
Er ist keine isolierte Speicherallokation eines Arms. Die expliziten Zustandseinträge umfassen ebenfalls
nicht den gesamten Prozessspeicher.

## Freezeidentitäten und einmalige Ausführung

- Code-Freeze: `fc1d996135d98165a7102d0a4e67b9ff34a0b1f0`.
- Freeze-Manifest: [ctx3-freeze-20261024.json](ctx3-freeze-20261024.json), dokumentiert in Commit `363475a`.
- Manifest SHA-256: `9dfb6855869b8d269b16447238d046b08a0e5fea55ce50bd2c4382e41d1b7399`.
- Datensatz: [ctx3-independent-test3-20261024.json](ctx3-independent-test3-20261024.json), 42.923 Bytes.
- Datensatz SHA-256: `e5681030efe4f83a471cc2e91dae7bf3ed577f889b0fc0aabd48cf39bfd39077`.
- Runner-Quelldatei SHA-256: `872af9473dc9ef95d1421c387b16f41fc7e9e712272e43054266f0090e5a1cf4`.
- Ergebnis SHA-256: `a0a54c8783c87954c318093c59fca79e72f3a310cd8bc499251df0976fd2cd96`.
- [Erfasstes stdout/stderr](ctx3-independent-test3-stdout-20261024.txt) SHA-256: `dc58f2d2f87d9dc657a5352b7f08b80ef69851706ced39714afbcb3699c2447a`.

Vor dem Start wurden **alle 23 Manifest-Einträge** sowohl mit den Arbeitsdateien als auch mit ihren Bytes
am angegebenen Codecommit abgeglichen. Datensatzhash und Byteanzahl stimmten ebenfalls überein.
Der Runner-Quellhash entsprach dem Freeze; die frisch kompilierte Runnerklasse war zeitlich neuer als
die Quelldatei. Das Compilelog enthält eine Warnung über ein fehlendes javac-Verzeichnis im Classpath;
der eigentliche Messlauf endete erfolgreich mit Exitcode 0.

Es gab **genau eine Invocation des finalen Experiments und keine Wiederholung**. Vorherige,
vor dem ersten Lauf abgelöste Freezevorbereitungen enthielten keine TEST3-Auswertung. Ausgeführt wurde
Java 17 unter `/workspace/toolchains/jdk-17.0.21+9/bin/java` mit `-Xmx2g` und
`at.heinz.deviceai.intelligence.maxultra.Ctx3Experiment`. Argumente waren Datensatzpfad, neuer Berichtspfad
und der vollständige Code-Freeze-Commit. Der Classpath bestand aus dem frischen Overlay
`/workspace/scratch/m5/frozen-runner-9a30327`, dem tatsächlichen SDK-/Gradle-Runtime-Classpath aus
`/workspace/scratch/test-runtime-classpath.txt` sowie `app/src/test/resources` und
`app/src/main/resources`. Der historische Verzeichnisname des Overlays bezeichnet keinen anderen
Messcommit. Das zugehörige Compilelog ist `/workspace/scratch/m5/frozen-runner-compile.log`.

Es gab während dieser unabhängigen Auswertung keine Codeänderungen, keine Datensatzänderungen,
keinen erneuten Gradlelauf und keine APK- oder Geräteaktion.

## Prozess- und Aussagegrenzen

1. **Unabhängigkeit durch Arbeitsdisziplin.** Ein frischer Agent schrieb den Korpus ohne Implementierungs-
   oder Altvorlagenkenntnis. Der Implementierer las laut dokumentiertem Prozess bis zum Freeze weder
   Korpusinhalt noch Ergebnisse. Der Korpus war jedoch öffentlich im gemeinsamen Repository zugänglich;
   Hash und Freeze sind keine technische Zugangssperre. Korpus, Runner, Quellen und Einzelvergleiche
   bleiben unverschlüsselt und allen Entwicklern offen.
2. **Ein synthetischer Autor.** Die Daten und semantischen Goldentscheidungen stammen von einem
   einzelnen unabhängigen Agenten. Es gibt keine zweite unabhängige Annotation oder menschliche
   Inter-Rater-Prüfung. Die 24 fiktiven Dialoge beweisen keine Qualität auf realen Besitzerunterhaltungen.
3. **Statische Quellen.** Quellen sind innerhalb eines Dialogs unverändert bestätigt. Laufender Widerruf,
   Freigabewechsel und reale Besitzerprovenienz sind nicht durch dieses Korpusformat nachgewiesen.
   Quellen außerhalb Gold und Quellen außerhalb der Freigabe sind verschiedene Fehlerarten.
4. **Gemeinsamer Host, feste Reihenfolge.** Die Arme liefen in einer JVM in fester Reihenfolge
   B0, B1, B2, CTX3, ohne standardisierten Warm-up, Randomisierung oder Wiederholungsmessung.
   Kaltstart- und Reihenfolgeeffekte können die Zeiten beeinflussen. CTX3s häufiges Enthalten und
   Auslassen sowie die verschiedenen Historypfade beschränken den direkten Latenzvergleich.
5. **Speicher nur eingeschränkt gemessen.** Der Heap-Peak betrifft die gesamte JVM; keine präzise
   armweise RAM-Zuordnung und kein Android-, Akku- oder Telefonbenchmark wurden erhoben.
6. **Quellenzuordnung statt vollständiger Antwortqualität.** Bewertet wird zuerst die Quellenzuordnung,
   dann die Präzision zusätzlicher Quellen und die Dialogentscheidung. Eine richtige erste Quelle
   garantiert keine vollständige oder sprachlich richtige Endantwort. Die unterschiedlichen Systeme
   erlauben keinen isolierten Wirksamkeitsnachweis einer einzelnen CTX3-Regel.

Das vorregistrierte negative Ergebnis und sämtliche Einzelvergleiche bleiben vollständig erhalten.
