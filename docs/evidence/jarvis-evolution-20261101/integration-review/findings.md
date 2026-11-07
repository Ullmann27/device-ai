Unabhängiger Read-only-Review der Span-Forschung auf `a329d5c4` und der anschließenden Arbeitskopie in `/workspace/continuum-evolution`. Keine Blind-Fixtures gelesen, keine gemeinsamen Quellen verändert.

Noch relevante Verifikationslücke: Der unabhängige Verifier prüft nur Klauseln mit dem ausdrücklich genannten Subjekt und verliert beim Satzsplit den Quotezustand. Vier selbst formulierte Gegenbeispiele wurden aus einer isolierten Quellkopie kompiliert und ausgeführt. Frage jeweils `Wo ist Clara?`, vorgeschlagener exakter Span jeweils `Linz`:

| Fall | Evidenz | Ergebnis des geprüften Verifiers / Regelwegs |
|---|---|---|
| Anaphorische Negation | `Clara ist in Linz. Sie ist nicht in Linz.` | `true` / `Found(Linz)` |
| Anaphorischer Konflikt | `Clara ist in Linz. Sie ist in Wien.` | `true` / `Found(Linz)` |
| Widerruf | `Clara ist in Linz. Das stimmt nicht.` | `true` / `Found(Linz)` |
| Mehrsatziges Zitat | `Er behauptet: „Heute frei. Clara ist in Linz.“` | `true` / `Found(Linz)` |

Diese Fälle müssen vor einer normalen Antwortfreigabe fail-closed bleiben. Kleine Regressionen können für jeden Fall sowohl `Verifier.verify(...) == false` als auch eine Enthaltung des Regelwegs verlangen. Zuerst den gesamten Evidenzkontext auf nicht unterstützte Referenzen/Widerrufe/Quotezustand prüfen; keine neue allgemeine NLP-Kompetenz behaupten. Die inzwischen hinzugefügten Guards für Fragezeichen, koordinierte Ortsnamen und exakte Aufenthaltsrelation behandeln diese vier Fälle nicht.

Reproduzierte Quellkopie: `EvidenceBoundNaturalLocationProbe-reviewed.kt`, SHA-256 `cf1634508f3a66d9335502819186ce98cba9d9d8b2b02d41260218b754e02827`. Eingaben/Runner: `VerifierAdditionalProbe.kt`; tatsächliche Ausgaben: `additional-verifier-results.txt`. Es handelt sich um neue, ausdrücklich benannte Review-Regressionen, nicht um eine Änderung oder erneute Bewertung des unabhängigen Blindkorpus.

Schema und Provenienz: Für den neuen `naturalEntityBindings`-Schalter fand ich keinen weiteren Schemafehler. Nur V3 setzt Bit 32; V2 lehnt dieses Bit ab, V1 bleibt größen-/versionsgebunden und benutzt Legacy-Features. Default/V1/V2-Rechenpfade bleiben unverändert; Parameterzahl bleibt 4.110. Die zuerst gefundenen Eingabepuffer-/Snapshot-Provenienzprobleme wurden von Root in der Arbeitskopie behoben: Größenprüfung vor Checkpoint-Kopie, Restore und Hash aus derselben privaten Kopie, Validierung der tatsächlich kopierten Evidenzliste und billige Längenprüfung vor UTF-8-Allokation. Diese Fixes sind gelesen; ihre umfassende Testsicherung erfolgt im Root-Lauf.

Laufzeitpfad: Der neue Aufruf ist jetzt `AgentActivity.showCdxPromptTest:4468 -> NaturalLocationLabComparison.compare -> LocationEvidenceSnapshot -> VerifiedLocationSpanResearch -> unabhängiger Verifier`. Er liegt im ausdrücklich gestarteten Leselabor, auf dem vorhandenen Learning-I/O-Executor. UI-Evidenz und Frage sind jeweils auf 160 UTF-8-Bytes begrenzt. `reference` wird nicht in die neuen Inferenzaufrufe gegeben. Der Asset-Reader liest exakt 32.932 Bytes plus EOF-Prüfung. Modellasset SHA-256: `c28c44c21e6b79aea5d13254760a0a782714e662294b366826d9d8c940697e92`.

Keine Owner-/Gewichts-/Datei-/Netz-/Tool-Schreibpfade in den neuen Forschungsfunktionen gefunden. Kein Anschluss an normale Chatantworten und kein Training aus der UI. Der portable Host-Runner nimmt inzwischen den neuen Lab-Test und den Asset-Classpath auf. Keine Android-/APK-/Fold7-Abnahme durch diesen Reviewer und keine unabhängige Qualitäts- oder Promotionfreigabe.
