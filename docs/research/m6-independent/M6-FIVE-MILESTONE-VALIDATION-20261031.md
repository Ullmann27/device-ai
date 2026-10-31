# Continuum JARVIS – M6-Forschungsmission, fünf technische Meilensteine

Stand: 31.10.2026. Status: fünf **Quellcode-/Validierungs-Meilensteine** erreicht. Das ist **kein Nachweis** einer auf dem echten EWM-Modell bestandenen 240er-Prüfung oder Fold7-APK.

## Mission 1 – Kontrollfakt-Invariante repariert

`M6ForeignEwmProbe.kt` meldet einen unbeteiligten Kontrollfakt jetzt nur als erhalten, wenn er weder als ersetztes Ziel (`supersededBy`) noch als Quelle einer Ersetzung (`supersedes.keys`) auftaucht. Der eigentliche EWM-Produktionsalgorithmus ist nicht geändert.

## Mission 2 – Unabhängigen JVM-Beleg gegen Widersprüche gehärtet

`scripts/continuum-ewm-v2-run-jvm.mjs` liest die tatsächliche nachgelagerte Graphenentscheidung und verweigert widersprüchliche Angaben: `replaces_before`, `edges_from_after`, binäres Signal und `before_current` müssen zusammenpassen. Die neueste Aussage muss aktuell bleiben. Ein als erhalten markierter Kontrollfakt darf nicht in den gemeldeten Ersetzungskanten stecken.

## Mission 3 – Fälschungs-/Fehlerexperimente implementiert und lokal ausgeführt

`scripts/continuum-ewm-v2-run-jvm.test.mjs` hat jetzt **15 Tests**, darunter fünf neue Gegenbeispiele zu falschen Graphkanten, veralteter neuer Aussage, widersprüchlichem Zeitstatus und verlorenem Kontrollfakt. Lokaler Node.js-22-Lauf: **15/15 PASS**; dies ist die Parser-/Orakelkontrolle, nicht der EWM-240-Lauf.

## Mission 4 – Metamorphes Forschungsexperiment angeschlossen

`M6MetamorphicInvariantProbe.kt` untersucht 288 permutationsbasierte Fälle: sechs Saatpaare, acht verschiedenartige Kontrollfakten, drei Einfügepositionen und zwei ID-Namensschemata. Zwei Eigenschaften werden am ungeänderten `Ewm1Memory` überprüft: Eine fremde Aussage darf nicht selbst ersetzt/ersetzend werden, und sie darf die ursprüngliche Ersetzungskante nicht verändern. Das Experiment nutzt **keine Gold-Labels** des eingefrorenen 240er-Holdouts. Kotlin-Kompilierung und 288/288 Durchläufe sind **nur mit einem ausdrücklich künstlichen Stub-Gedächtnis** lokal überprüft; Ergebnis auf dem echten EWM-Code bleibt NOT_RUN.

## Mission 5 – Forschungslauf und Score-Vertrag sicherer anschließen

`scripts/continuum-ewm-v2-metamorphic.sh` prüft vor Ausführung die zwei unveränderten Produktions-Kotlin-Git-Blobs und die Kandidaten-Ancestry. Script-Syntax lokal geprüft. `continuum-ewm-v2-holdout-score.mjs` lehnt nun Ergebnisse mit unvereinbarer Ersetzungskante, falsch gemeldeter Aktualität oder einer nicht verfügbaren JVM-Ereignisunterklasse ab. Er meldet diagnostisch geringe Recall bzw. fehlende Unterklassen-Ausgabe, **ohne den vorregistrierten Precision-Grenzwert nachträglich zu ändern**. Zusätzliche Scorer-Regressionsfälle wurden in GitHub committed; GitHub-CI bzw. der aktuelle echte vollständige 240er-Lauf sind noch nicht bestanden/ausgeführt.

## Vollständige Abnahme – von der Commit-Linie aus

~~~bash
node --test scripts/continuum-ewm-v2-holdout-score.test.mjs scripts/continuum-ewm-v2-run-jvm.test.mjs
bash scripts/continuum-ewm-v2-metamorphic.sh
node scripts/continuum-ewm-v2-run-jvm.mjs --receipts /tmp/continuum-ewm-240-receipts.jsonl
~~~

Der letzte Befehl ist der nur einmal gegen den vorregistrierten Holdout durchzuführende reale Kotlin-Lauf. Sichert danach die SHA-256 der Belege, den ausführenden Commit, die gemeldeten Precision-/Recall-Werte und die unveränderten Fakten in der zentralen Issue #276. Ein formal bestandener Precision-Gate ist **kein** automatisches Release-Gate; sehr geringe Recall muss ausdrücklich berichtet werden.

**Konfliktgrenze:** Jules bleibt Eigentümer des Android-Builds und des Update-Buttons. Keine Gradle-/Manifest-/Release-Dateien verändert, kein Main-Push, kein neuer PR, keine bezahlten Dienste. Alle Änderungen liegen im bestehenden Draft-PR #258.