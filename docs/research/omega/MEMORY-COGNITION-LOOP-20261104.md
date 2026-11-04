# Gedächtnis: eindeutige signierte Belege

Status: **SOURCE_INTEGRATED / TARGETED_HOST_PASS**. Zwei vorhandene Produktionsdateien geändert; keine neue Gedächtnisdatenbank, keine Modellgewichte oder Hauptchat-Aktivierung. Vollständiger Android-Build und Gerätetest auf diesem Kandidaten: **NOT_RUN**.

Arbeitsbasis: `5a3f33b4dd65d5840d22d18f4c73ea6f9e394c66`, Branch `research/continuum-cognition-learning-loop-20261104`, vom Koordinator bereitgestellter Draft #528. Kein eigener Commit oder Push. Koordinator verantwortet aktuellen Remote-/Issue-#41-Abgleich. Relevante Verträge: EXP-06 Provenienz und EXP-12 signierte Gedächtniskontinuität; kein Gesamtstatus eines Experiments wird hochgestuft.

## Befund und tatsächliche Reichweite

`MemoryMutationPayload.canonicalBytes()` verband Felder mit `|` und Listen mit `,`. Damit erzeugten beispielsweise `listOf("source:a,source:b")` und `listOf("source:a", "source:b")` identische Signaturbytes. Im echten RED-Lauf akzeptierte die Verifikation die ursprüngliche Signatur auch für die veränderte Belegaufteilung. Entsprechende Mehrdeutigkeiten bestanden in `memoryRecordHash`, einschließlich Feldgrenzen, Listen und `null` gegenüber leerer Quellenreferenz. Die alte UTF-8-Konvertierung ersetzte außerdem einzelne UTF-16-Surrogate und erlaubte dadurch einen weiteren Alias.

Der vorhandene persistierende Aufrufer ist `learning/SignedOwnerCorrectionJournal.kt`: `recordDiff` erzeugt Mutationen; `entries` dekodiert und prüft die Kette; `audit` bindet den aktuellen Faktenbestand an den zuletzt signierten Zustand. `ContinuumLearningCore.signedOwnerMutation` ruft diesen Pfad nach erfolgreichem Ledger-Schreiben auf, **wenn ein correctionSigner übergeben wurde**. Die gefundenen heutigen Activity-/Scheduler-Aufrufe verwenden den Standardkonstruktor ohne Signierer. Der Fix aktiviert keine Signaturpflicht im Hauptchat und behauptet keinen bereits signierten Gerätebestand.

Bestehende Lösungen bleiben maßgeblich: `HybridMemoryRetriever` wird unter anderem von `AiContextCompiler`, `BoundedAgent`, `AuthorizedConversationMemoryShadow` und `MemoryRetrievalResearchGate` aufgerufen; `MemoryEcology` liefert Records, Provenienz und Schreibpolitik. Zeitliche und EWM-Pfade bleiben unverändert. Die vom Koordinator als bereits behoben gemeldeten #522-/#523-Befunde wurden nicht erneut bearbeitet.

## Änderung und Formatvertrag

- `cognitive/ProofCarryingMemory.kt`: Payloads tragen `schemaVersion`, standardmäßig 2. v1-Kanonisierung und v1-Recordhash bleiben unverändert abrufbar. Neue Mutationen verwenden v2.
- v2 beginnt mit den getrennten vier Byte großen Domänen `PCM2` beziehungsweise `MRC2`. Zahlen werden durch `DataOutputStream` in Big-Endian geschrieben. Strings erhalten eine Int32-Länge in UTF-16-Codeeinheiten, danach jeweils zwei Bytes pro Codeeinheit; `-1` kennzeichnet `null`. Damit bleiben auch einzelne Surrogate eindeutig. Listen erhalten eine Int32-Anzahl und getrennte Stringlängen; die vorhandene `distinct().sorted()`-Mengensemantik bleibt erhalten.
- `learning/SignedOwnerCorrectionJournal.kt`: derselbe Dateipfad `owner-corrections-pcm-v1.log` bleibt bestehen. Der Reader unterstützt alte `v1`- und neue `v2`-Zeilen; der Writer schreibt ausschließlich `v2`. In v2 werden UTF-16-Codeeinheiten als URL-Base64 gespeichert, Referenzen einzeln codiert und mit Komma getrennt. Dadurch kann eine Referenz auch Kommas, Pipes oder Zeilenumbrüche enthalten.
- `audit` wählt die Recordhash-Version anhand der letzten signierten Mutation **pro Record**. Eine v2-Mutation verweist mit `previousMutationHash` auf den tatsächlichen v1-Hash ihres Vorgängers; ihr `beforeHash` und `afterHash` verwenden beide die v2-Recorddarstellung. Unveränderte Records mit letzter v1-Mutation bleiben mit v1 prüfbar.

Alte Zeilen werden weder neu serialisiert noch neu signiert. Die grundsätzliche Mehrdeutigkeit alter v1-Signaturbytes lässt sich rückwirkend nicht entfernen; Kompatibilität ist keine nachträgliche Verstärkung alter Signaturen. Unbekannte Versionen, Versionswechsel ohne neue gültige Signatur und beschädigte v2-Elemente werden abgewiesen.

## Eigene tatsächliche Abnahme

Evidenz: `docs/evidence/cognition-learning-loop-20261104/memory/`.

| Lauf | Compiler | JUnit | Ergebnis |
| --- | --- | --- | --- |
| red-01, red-02, red-03 | nicht gestartet | nicht gestartet | LOCK_BUSY, Exit 75; keine Testzählung |
| red-04, unveränderte Produktion | 0 | 1 | 18 Tests: 8 PASS, 10 fachliche FAIL |
| green-01, korrigierte Produktion | 0 | 0 | 28 Tests: 28 PASS, 0 FAIL |

RED enthält zwölf neue Kanonisierungsregressionen und sechs unveränderte vorhandene Journaltests. Alle sechs vorhandenen Tests bestehen bereits in RED. GREEN verwendet dieselben zwölf Regressionen bytegleich und ergänzt zehn Tests für v1-Lesen, v2-Schreiben, gemischte Kette, unveränderte Altbytes, ausbleibendes Neusignieren, Manipulation, Versionswechsel, ungültige Kodierung und versionsrichtiges Ledger-Audit. Die beiden Läufe überlappen und werden nicht addiert.

Die v1-Fixture stammt aus dem **tatsächlich ausgeführten alten Produktionswriter** in red-04. Ihre beiden signierten Zeilen wurden anschließend unverändert in den Kompatibilitätstest übernommen. Fixture-SHA256: `e6a6be3ded3802ff3c252cc99895987d99ad9ad494cb505fbcac96da64d7ba52`. Sie enthält ausschließlich synthetische Testobjekte und einen ausdrücklich öffentlichen HMAC-Testschlüssel.

Tatsächliche Einstiegskommandos im Checkout:

```bash
python3 docs/evidence/cognition-learning-loop-20261104/memory/run_host.py docs/evidence/cognition-learning-loop-20261104/memory/red-04 --base-jar /workspace/scratch/344de8bc2104/memory-jvm-base.jar --write-legacy-fixture
python3 docs/evidence/cognition-learning-loop-20261104/memory/run_host.py docs/evidence/cognition-learning-loop-20261104/memory/green-01 --base-jar /workspace/scratch/344de8bc2104/memory-jvm-base.jar
```

Die Ausgabeordner dürfen bei einer Wiederholung noch nicht existieren. RED reproduzieren: die archivierten beiden ursprünglichen Quellen und die beiden damaligen Testdateien aus `red-04/sources/` in einer separaten Basis verwenden; keine aktuelle Produktionsdatei im gemeinsamen Checkout zurücksetzen. Vollständige tatsächliche Java-/Compiler-/JUnit-Befehle stehen je Lauf in `commands.json`, Quelldigests und Abhängigkeiten in `manifest.json`, unveränderte Rohausgaben in `compile.txt`, `junit.txt` und `legacy-fixture.txt`.

Toolchain: vorhandener Temurin JDK 17.0.20.1, Kotlin 2.4.0, JUnit 4.13.2; je JVM höchstens `-Xmx512m`. Beide vorhandenen Maschinenlocks wurden bei den tatsächlichen Compiler-/Testläufen gehalten. Die vollständigen beiden Zielquellen und Testklassen wurden frisch kompiliert. Abhängigkeiten stammen aus einer eigenen eingefrorenen Kopie echter C1-Klassen, **ohne Hash-/Journal-Testdoubles**. Alle 2.292 kopierten Dateien waren vor/nach dem Kopieren und innerhalb der ZIP hashgleich. Snapshot: 6.511.666 Bytes, SHA256 `44e45d4c047a1cd65a4ca8b8faccae498a559b09cb4bd5ae99a04db4ac94048d`. C1-HEAD beim Kopieren: `4da039d55d44f7087a9e4cd7bd72dc5a0089029e`; dies allein ist kein erneuter vollständiger Buildnachweis dieses HEADs. Die verwendeten Jars blieben während der Läufe unverändert. Keine Android-Keystore-, APK-, Geräteleistungs- oder Gedächtnisqualitäts-Benchmarkbehauptung.

## Geänderte Dateien, Grenzen und Rücknahme

Produktionsumfang: ausschließlich `app/src/main/java/at/heinz/deviceai/cognitive/ProofCarryingMemory.kt` und `app/src/main/java/at/heinz/deviceai/learning/SignedOwnerCorrectionJournal.kt`. Neue Tests: `cognitive/ProofCarryingMemoryCanonicalEncodingTest.kt` und `learning/SignedOwnerCorrectionJournalEncodingTest.kt` unter `app/src/test/java/at/heinz/deviceai/`. Vorhandener Journaltest unverändert. Dazu dieses Handoff und die eigene Evidenz.

Der bestehende 8-MiB-Journalgrenzwert bleibt erhalten; v2-Zeilen benötigen wegen der eindeutigeren Zeichendarstellung mehr Platz. Die Ledger-/LEX2-/LEX3-Formate, Besitzerfreigaben, Offline-Schalter, ToolGate und Modellgewichte werden nicht verändert. Vollständige Neukompilierung der kombinierten App mit den anderen Botänderungen sowie Android-/Geräteabnahme bleiben eigene Gates.

Vor ersten v2-Schreibvorgängen lassen sich ausschließlich diese beiden Quelländerungen gemeinsam zurücknehmen. **Nach v2-Schreibvorgängen ist ein alter ausschließlich-v1-Reader kein kompatibler Rollback.** Dann muss der v1/v2-Reader erhalten bleiben oder ein gesondert geprüfter Rückweg mit bewahrten Originalbytes erfolgen. Keine v2-Zeilen löschen, keine Altbelege umsignieren, keine automatische Migration von Besitzerdateien.

Nächster Schritt: unabhängiger Quellreview und gemeinsame Integration durch den Koordinator; kein Main-Merge, Release oder Geräteeingriff durch diesen Bot.
