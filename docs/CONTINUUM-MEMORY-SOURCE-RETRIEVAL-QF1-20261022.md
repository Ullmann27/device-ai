# Continuum – QF1: Quellenfehlzuordnungen bei unbekannten Chatfragen
Stand: 22. Oktober 2026 · ausschließlich isolierte, rücknehmbare Forschung

## Ausgangsbefund direkt auf Besitzer-Samsung

Der Eigentümer prüfte die installierte signierte APK v0.3.12 (VersionCode 15, Basis-Commit `619ebeb2831c8184a91e3c08e165ed4f4794f969`). Das explizite Menü `Lernen → WISSENSARCHIV · QUELLEN SUCHEN` lieferte für `Backpropagation` eine passende Quelle mit Herkunftsnachweis. Zwei anschließende Chat-Gedächtnisanfragen enthüllten dagegen unbelegte Nebenquellen:

1. `Fehlerrückführung`: richtige Backpropagation-Quelle **plus** unpassende Hinweise unter anderem zu Softwaretest/Aufrichtigkeit.
2. `Quantenkarussell987`: erfundener Testbegriff, dennoch Auszüge u.a. zu Physik, Bernard Widrow und Statistik.

**Wichtige Abgrenzung:** Die Antwort `Dazu finde ich in meinem Gedächtnis` wird durch `ContinuumMemoryDialogue.reply()` erzeugt, **nicht** durch `ContinuumKnowledgeArchive.search()`. Das ZIP mit 149 Wikipedia-Auszügen und der aktuelle SWB1-Checkpoint sind nicht die Fehlerursache. Sie wurden nicht verändert.

## Reproduzierte Code-Ursachen und begrenzte Reparatur

`ContinuumMemoryDialogue.wordEvidence()` liefert als historischer lexikalischer Ranking-Baustein bereits bei identischen ersten fünf Zeichen einen Matchwert `0.8`; auch passende Wortenden wie `Fehlerrückführung` ↔ `Führung` können `0.82` erhalten. Dieses Verhalten ist für manche deutsche Zusammensetzungen in **bestätigten Besitzerfakten** beabsichtigt, aber für **unbestätigte externe Quellen** ungeeignet: Ein allgemeines Wortfragment ist kein Nachweis für einen unbekannten Fachbegriff.

Der neue `sourceTermEvidence(query,text)`-Filter wird deshalb **nur bei `LearningExperienceType.SOURCE_TEXT`** vor der bestehenden Ranking-Grenze ausgeführt: Wenn die Frage genau einen fachlichen Inhaltsbegriff mit mindestens zwölf Zeichen enthält, muss dieser im Beleg wortgleich oder als enge Flexionsvariante belegt sein. Ein bloßer gemeinsamer Wortanfang von fünf Zeichen, ein kürzeres generisches Suffix und ein erfundener alphanumerischer Ausdruck genügen dann nicht mehr. Nutzerbestätigte Erinnerungen, SWB1-Gewichte, TF-IDF-Ranking und der allgemeine `wordEvidence()`-Baselinevertrag bleiben unverändert. Die gleiche Regel wurde im vorhandenen Node-Referenzretriever für `kind/type: SOURCE_TEXT` gespiegelt.

Die Lösung ist bewusst **kein genereller Beweis semantischen Verständnisses**. Insbesondere Mehrwortfragen und kürzere Suchbegriffe brauchen weitere eigene Kontrollsätze. Die vorhandenen Quellen bleiben ausdrücklich unbestätigt und können weder App-/Gerätebefehle autorisieren noch trainierte Owner-Fakten ersetzen.

## Tatsächlich geprüfte Regressionen

Die beiden exakt aus GitHub gelesenen JS-Quelldateien wurden mit ihren Git-Blob-SHAs byteidentisch lokal rekonstruiert und mit `node --test retrieval-compound.test.mjs retrieval-false-positive.test.mjs` ausgeführt. Ergebnis: **8 Tests, 8 bestanden, 0 fehlgeschlagen, Exitcode 0**. Die historische `retrieval-compound.test.mjs` blieb unverändert; sie prüft weiter deutscher Zusammensetzungen wie `Schlüssel` ↔ `Haustürschlüssel` und `Forschungsprojekt` ↔ `Projekt`.

Zusätzlich wurde der vom Besitzer hochgeladene, **schreibgeschützt untersuchte ZIP-Export mit 149 Original-Wikipedia-Auszügen** unabhängig eingelesen. Alle 149 `.rec`-Payloads bestanden ihre ursprünglichen Inhalts-/Identitäts-SHA-256-Prüfungen. Derselbe, byteidentisch lokal ausgeführte Node-Quellenretriever erhält hieraus:

| Anfrage | Erwartetes und beobachtetes Ergebnis nach Filter |
|---|---|
| `Fehlerrückführung` | genau `Backpropagation` |
| `Was ist Fehlerrückführung?` | genau `Backpropagation` |
| `Quantenkarussell987` | kein Treffer |
| `Was weißt du über Quantenkarussell987?` | kein Treffer |
| `Backpropagation` | genau `Backpropagation` |

Diese Quellen-Auswertung ist ein **offline Retrieval-Nachspiel mit dem Archiv**, keine auf dem Gerät durchgeführte neue Chat-Antwort und kein unabhängiger Sprachfähigkeitsbenchmark. Die aktive Gedächtnisledger-Auswahl kann zusätzliche, andere Source-Records enthalten. Bereits mehrfach geprüfte Beispiele sind Entwicklungs-/Regressionstests und keine neuen Blind-Holdouts.

Zwei Kotlin/JUnit-Prüfungen sind in `ContinuumMemoryDialogueTest.kt` ergänzt; sie kontrollieren den realen Source-Record-Typ, den alphanumerischen Unsinnsbegriff, den falschen Pädagogik-`Führung`-Treffer und den echten Backpropagation-Treffer. **Vollständige JVM-/Android-Gradle-Regression noch nicht ausgeführt; deshalb nur `SOURCE_PATCHED / NODE_TESTED / REVIEW_ONLY`, nicht `APK_BUILT`.** Nach GitHub Checkout: `node --test .github/continuum/retrieval-compound.test.mjs .github/continuum/retrieval-false-positive.test.mjs` sowie vollständiges vorhandenes Node-, JUnit- und Android-Lint-Gate ausführen. Das historische `docs/retrieval-benchmark.json` wird nicht stillschweigend neu geschrieben.

## Übergabe und Freigabegrenzen

Abgezweigt vom exakt veröffentlichten APK-Stand `619ebeb` in `chatgpt/continuum-memory-retrieval-no-false-evidence-20261022`. **Kein Schreibzugriff** auf `main`, SWB1-/CDX1-/CSX1-Checkpoints, Besitzer-Erinnerungen, Wissensarchiv, signierte APK oder Opus-Branches. Quellcode vollständig editierbar; ausschließlich Draft-Review. Neue APK/Signaturversion erst nach vollständigen Android-Tests und ausdrücklicher Freigabe. Das ältere Produkt läuft unverändert weiter.
