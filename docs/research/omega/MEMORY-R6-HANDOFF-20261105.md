# MEMORY R6 — Retainer-Snapshot für die beiden M2.9-Retriever

Stand: **5.11.2026**. Eigentümer: Gedächtnis-Fachspezialist. Status: **ZWEI MINIMALE PRODUKTREPARATUREN / HOST-METHOD GREEN / ANDROID NOT_RUN**. Keine neuen Unteragenten.

## Eingänge, aktueller integrativer Stand und Abgrenzung

- Ausgangspunkt ist der frisch gelesene C1-Draft [#519](https://github.com/Ullmann27/device-ai/pull/519), SHA `13c37f55789bf67b07beeb37cc1a5035526e623e`; zentraler C1-Boardpfad `docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md` wurde ebenfalls gelesen.
- Bereits von C1 einzeln übernommen: #522s Datenschutz-/Mapper-Fixes, 017A/B jeweils 23/23, #524 Layout 7/7. #529 ist bei C1 als PRIVACY-024 in eigener Testaufnahme; R5/[#531](https://github.com/Ullmann27/device-ai/pull/531) bleibt unabhängig und hat **keinen Android-Projekt-PASS**. Kein R5-/R4-Compiler wird hier übernommen, keine fremde Datei modifiziert.
- Eigener abgegrenzter Zweig: `fix/continuum-memory-retriever-snapshot-r6-20261105` direkt ab dem genannten C1-Commit. **Einziger** geänderter Produktpfad: `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`. Keine Berührung von C1-Activity, Factory, Sprache, Worker, Compiler, MemoryEcology, Modellgewichten, Offline-Modus, Version, Signatur oder Nutzerdateien.
- C1 bleibt alleiniger Einbauer. Keine gemeinsame PR-Basis, fremde Worktrees, Locks, Prozesse oder laufende Builds verändert.

## Reproduzierter Fehler und fachliche Wirkung

Beide M2.9-Retriever `Bm25GermanRetriever.index` und `CharNgramHashRetriever.index` speicherten die vom Caller übergebene Kotlin-`List<MemoryDoc>` **direkt** in `this.docs`, berechneten aber das Ranking aus den beim Indexieren vorhandenen Texten. Eine vom Caller nach dem Indexieren mutierte `MutableList` konnte deshalb den zum ursprünglichen Score zurückgegebenen Dokument-Eintrag austauschen und auch das sichtbare `confirmed`-Kennzeichen durch den Wert eines *anderen* Dokuments ersetzen. Ein späteres `clear()` konnte Treffer verschwinden lassen beziehungsweise die Dokumentreferenzen inkonsistent zur Vektormenge machen. Das ist ein konkretes Ownership-/Belegintegritätsproblem, **kein** nachgewiesener Fold7-Absturz und keine Behauptung über reale persistierte Daten.

Vier Android-JUnit-Projektmethoden wurden **vor jeder Produktänderung** bei `162da3662aaf3e59b4153823620d7897338a8168` eingefroren. Sie decken Austausch, Leeren, beabsichtigtes Neuindexieren und die Score-Part-Identität ab; diese echten Projektmethoden sind **NOT_RUN**, denn hier steht weder die gemeinsame Android-SDK-/Gradle-Toolchain noch deren freier Maschinenlock zur Verfügung.

## Reparatur 1 und 2

Die Produktionsänderung `5c34d322da8d8325d33d6c2a1eb9d64d6f46ddd9` fügt in jedem der beiden `index()`-Aufrufe **einmalig** `val snapshot=docs.toList()` ein. Interne Dokumentliste und alle abgeleiteten Posting-/Vektor-/IDF-Operationen benutzen denselben Snapshot. Damit behalten Score und Dokumentidentität eine gemeinsame Version, und ein neuer Corpus wird nur durch einen expliziten weiteren `index()` übernommen. `MemoryDoc` ist ein Datentyp mit unveränderlichen String-/Zahlen-/Booleanfeldern, deshalb genügt die flache Listenkopie. Die vorhandenen Scoringgewichte, Zeitfenster, Ranking, Modelle und Dateien wurden nicht verändert.

Produktdatei: ursprünglicher Git-Blob `857eb1fb8bbada8282a095956667bb521e44abe6` → endgültiger Git-Blob `b2d6ac9b078557f8c74fe0be9dc0032a15e52323`. Der bekannte C1-Tagesgrenzenfix bleibt im Ausgangsbaum vollständig erhalten.

## Ehrliche RED→GREEN-Prüfung

Ein **echter netzloser Kotlin/JVM-Hostlauf** extrahiert die beiden identischen, unverändert aus der jeweils tatsächlichen produktiven Kotlin-Quelldatei entnommenen `index`-Methodenkörper und kompiliert diese mit minimalen Testabhängigkeiten, unter `kotlinc-jvm 1.9.0` und Java 21. Beide Methodenkörper wurden vor/nachher unabhängig über FNV32 mit den gelesenen veröffentlichten Produktionsmethoden verglichen:

| Methode | Vorher: FNV32 | Nachher: FNV32 |
|---|---|---|
| BM25 `index` | `8150ccb5` | `ee11dd94` |
| N-Gramm `index` | `f54404ba` | `fc6e1a8a` |

Ergebnis: **RED 12 = 6 PASS / 6 FAIL, Exit 1** → **GREEN 12 = 12 PASS / 0 FAIL, Exit 0**. Darunter werden die unzulässige Umbenennung durch `MutableList`-Austausch, das Verschwinden nach `clear()`, die unveränderte Indexstruktur und das explizit gewollte Neuindexieren geprüft. Tatsächliche kompilierten Körper entsprechen in beiden Varianten den genannten Quelldatei-Methoden, nicht einer handgeschriebenen Alternativimplementierung **der Indexlogik**.

Die Host-Hilfsfunktionen `GermanText`, `terms`, `grams`, `vec` sind dagegen **Test-Stubs**. Deshalb prüft dieser Hostlauf nur die Indexlistenbindung und ist **KEIN** Test des vollständigen Rankings, der vollständigen Retrieverdatei oder von Android, GPU, Gerät, Voice oder Modellinferenz. Die lokalen Laufdateien sind methodentragende Test-Proxys, keine identischen vollständigen Git-Checkouts. Exakte Standardausgaben sind in [RED](../../evidence/memory-index-snapshot-r6-20261105/host-red.txt) und [GREEN](../../evidence/memory-index-snapshot-r6-20261105/host-green.txt) festgehalten. Der reproduzierbare Runner `python3 tools/memory-index-snapshot-r6/run.py` entnimmt beim Ausführen die aktuellen Methoden direkt aus `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`; ein Quellen-FNV-Mismatch bricht ab. Für einen RED-Baselinelauf darf `--source <Original-Quelldatei> --allow-other-source` genutzt werden.

## Nächste Abnahme und Rückfall

C1 übernimmt **nicht blind**: zuerst aktuellen gemeinsamen C1-Head und den unverändert gebliebenen Retriever-Basisblob gegenprüfen. Dann exakt die vier gefrorenen Projekt-JUnit-Methoden mit vollem Produktionscode, zusätzlich die bestehenden 1.000-Dokumente-/44-Fragen-Retrievalversuche und die TemporalCue-/Listener-Tests unter beiden bestehenden Buildlocks auf dem finalen Baum ausführen. Bei Fehler keine Übernahme; negatives Ergebnis archivieren. Anschließend Voll-Node/Backend/Android/UI/NewApi, echte Geräteinferenz und Fold7-Abnahme. Die frühere Recall@3-Grenze 42/44 ist **weder neu gemessen noch als verbessert** behauptet. Kein neuer Thread-Safety-Vertrag für gleichzeitig laufende `index`-/`search`-Aufrufe.

Nur diesen kleinen Produktions-Patch zurücknehmen, wenn Android-Verträge rot bleiben; R4/R5/#522 und das restliche C1 unverändert erhalten. Kein Main-Merge, keine Actions, keine bezahlten Dienste, keine Release-Signierung oder Installation. Maschine-State: [MEMORY-R6-STATE](MEMORY-R6-STATE-20261105.json).
