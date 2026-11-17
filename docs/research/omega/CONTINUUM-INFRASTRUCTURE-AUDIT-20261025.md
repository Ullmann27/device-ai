# Continuum: GitHub-, Anschluss- und Fehleraudit vom 25.10.2026

Geprüfte Basis: `5d944d49af099a0a66660f5a126c9c7231e0547c`, identischer Android-Implementierungsbaum zu PR #148 `30303ca9103fa2034aa384fcf5f730402ecd0e6c`. Eigene Reparaturen: Branch `sol/continuum-infrastructure-repair-20261025`, Draft [#150](https://github.com/Ullmann27/device-ai/pull/150). Dies ist eine Quell-/Hostprüfung, keine Geräteabnahme oder Releasefreigabe.

## Vollständigkeit und Grenzen der Inventur

Die beiden JSON-Dateien im selben Verzeichnis konservieren **200 vorhandene Remote-Refs** mit SHA/Abstammungsabständen und fehlenden Quellpfaden sowie **118 offene PRs** (GitHub-REST-Seiten 100 + 18). Der Vergleich fand **80 unterschiedliche Quell-/Testpfade**, die mindestens ein anderer Branch enthält und die der Auditbasis fehlen. Darunter sind eigene neuere Reparaturtests und Releasewerkzeuge. Ein fehlender Pfad ist ein Prüfkandidat, kein Beweis für fehlende Funktionalität; abweichende Historien dürfen nicht blind gemergt werden. Eine vollständige semantische Prüfung jeder historischen Zeile ist damit ausdrücklich nicht behauptet.

- `continuum-branch-inventory-20261025.json`: reproduzierbar mit `git for-each-ref`, `git rev-list --left-right --count` und `git ls-tree`; keine fremde Ref verändert.
- `continuum-open-pr-inventory-20261025.json`: echte Head-/Base-SHAs, Draftstatus und URLs aller gefundenen offenen PRs.
- `main`: unverändert `190f5eabaa60fc59292b2b80d3086aafd7743a80`.

## Aktuelle Integrationslinie

| PR | Tatsächlicher Head | Befund |
|---|---|---|
| #139 | `d368220596912a2b3d0766b0bb3f324db60ec658` | Owner-v23/JARVIS-Basis; kein aktueller Modellzentrumstand |
| #140 | `015e1bf53f40c223a0fb2d52b557618c4a544572` | älterer Releasekandidat; Quellfreigabe gilt nicht für spätere Integrationen |
| #142 | `62d6bb10971d4702de9daf391c65c9823dcef0c5` | ORION-Belegsubjektkorrektur enthalten; Gerätebeweis fehlt |
| #143 | `413fa3c2a49f9c589b1f92d62525a621c8b9eeda` | Lernzentrum/CTX3/lesende Skillprüfung vorhanden |
| #145 | `2cc3c1e53943a73a7f9e24321d321dda1bb3e0b1` | vier Forschungsdateien in #147 inhaltsgleich übernommen; kein Training |
| #146 | `fe1b23300346946c748b24903fa85c8ec5999dfd` | verbindliche Modellentscheidung |
| #147 | `52c4fdadf9aa97cd7683a30be005da9b67db1311` | Modellentscheidung, Kapazitäts- und Metadatenprüfung zusammengeführt |
| #148 | `30303ca9103fa2034aa384fcf5f730402ecd0e6c` | aktuelle Modellzentrum-/Lernstatus-UI; Android-Vollprüfung war offen |
| #149 | `febffb293f12702df5714ee20c8fb66471b493b9` | zusätzlicher paralleler Gemma-/Qwen-Budgetzweig; UI überlappt #148 |

Alle genannten PRs waren beim Live-Abgleich offen und Draft. `ops/deepseek-v41-1017-h200-deployment-20261025` bei `a466a89604a0aad09ff2fcdaaba14e3d86730ea0` ist in #147 enthalten. Die Geschichte bleibt erhalten. Die Releasewerkzeuge von #140 fehlen auf dieser Integrationslinie absichtlich; ihr altes Quellmanifest darf spätere Änderungen nicht autorisieren.

## Tatsächliche Aufrufketten und offene Anschlüsse

| Experiment / Modul | Erreichbarer Ist-Pfad | Grenze / nächster Nachweis |
|---|---|---|
| EXP-00/08/09 M4/M5 | Hauptdialog → vorhandener MobileBuilder → typisierte Pläne → bestehende Termux-Aufträge/Receipts | zwei begrenzte Android-Rezepte; aktueller Fold7-BUILD-Rückkanal mit exakt gebundenem Artefakt offen |
| EXP-01 Teamleiter | bestehende Scheduler, `continuum-teamleader-termux.sh`, kanonischer Worker | mehrfache echte Gerätezyklen, Doze/Temperatur/Abbruch/Wiederaufnahme offen |
| EXP-02 CCC1 | `AgentActivity` typed prepare/run → `ContinuumMobileBuilderActivity` → `ContinuumTypedCodingBridge` → `continuum-typed-coding-bridge.mjs` → `coding-orchestrator.mjs` → CCC1/Teamleiter/mobile repair | Quellanschluss vorhanden; OS-Isolation und Besitzerfreigabe bleiben erforderlich; kein beliebiges Modell-Shellkommando |
| EXP-03 CEFR | bestehender beratender Reparaturranker | prospektive unabhängige Messung, keine automatische Autorität |
| EXP-04 SWB1/CRE1 | `runContinuumDialogue` → `CognitiveDecisionBridge` → `ContinuumCognitiveRuntime.reply` → `replyWithEvidenceInConversation` → Gedächtnisdialog | CRE1 darf nur qualifizierte vorhandene SWB1-Kandidaten umordnen; Hash-/Schrittmanifest wird geprüft |
| EXP-05 LiteRT | unbeantworteter Gedächtnisdialog → gesonderter Aufruf des tatsächlich geladenen lokalen Modells | Auswahl/Import/Laden/Inferenz/Qualität separat; der frühere CognitiveDecisionBridge-Aufruf erhält einen null-Modelladapter |
| EXP-05 CDX1 | Lernzentrum → Paarlabor / Lesetest / Trainingsmonitor; opt-in Scheduler | Forschung; keine Hauptchat-Promotion. Kleine Bytearchitektur und Loss sind keine allgemeine Sprachkompetenz |
| EXP-05 LM1 | `learning/lm/LmRuntime.kt` bietet Generation | kein produktiver Android-Aufrufer gefunden; negative Benchmarkgrenze bleibt bestehen |
| CSX1/I1/I2 | Lesetest zeigt `ContinuumEvidenceSpanProbe.answer` als regelbasierten Vergleich | diese Extraktion ist nicht der trainierte Residual-Span-Kern im Hauptchat |
| EXP-06 Atlas | `continuum-workshop.mjs` → `worker-atlas.mjs` | bibliografische `UNVERIFIED_WEB_LEAD`, `trainingApproved:false`; separater epistemischer Graph ohne produktiven Importer gefunden |
| EXP-07 Academy | Workshop-CLI → Skilllektion; auch begrenzter Workshopzyklus | kein direkter Hauptdialog-/Backend-Anschluss gefunden; unabhängige Kompetenzbelege bleiben erforderlich |
| EXP-10 Mentor | existierender zustimmungspflichtiger externer Textpfad | keine stillen Uploads persönlicher Erinnerungen, keine Werkzeugrechte |
| EXP-11 Entwicklung | `DevelopmentCoordinator` ruft Entwickler/Kritiker/Testentwerfer/Richter; Vorschlag landet im bestehenden Backlog | vorgeschlagene Tests sind `NOT_EXECUTED`; kein direkter Vorschlag→CCC1-Patchadapter |
| EXP-11 Skills | Lernzentrum → `showExperimentIntegrationReview` → `ExperienceCompiler.compile(experienceStore.all())` | `InMemoryExperienceStore`: nur laufende Sitzung; Review ohne Freigabe/Ausführung |
| EXP-12 Kontinuität | bestehende Checkpoint-/Rollback-/Paarbundlemechanismen | Modellwechsel und Besitzerfakten müssen pro Quelle unabhängig geprüft werden |
| EXP-13 LCT1/WSC1/TTT1 | derzeit Test-Quellset `intelligence/maxultra` | kein produktiver Android-Pfad; keinen Testcode als neue Sprachintelligenz aktivieren |
| EXP-14 CTX3 | Speicherpfad → `Ctx3Shadow.evaluate`, Lernzentrum → M5-Labor | Standard OFF, SHADOW überschreibt keine Antwort; unabhängiger TEST3 FAIL (13 richtig/3 falsch, zusätzliche falsche Quellen) |
| EXP-15 OMEGA | vorhandener opt-in Gegenbeispiel-Verifier im Reparaturpfad | synthetische Messung, keine Produktionspromotion |
| EWC | keine benannte EWC-/ElasticWeight-/Fisher-Implementierung in den geprüften produktiven Lern-/Workerquellen gefunden | nicht als integrierte Fähigkeit ausgeben |

Backend `/v1/continuum/knowledge` verwendet vorhandene Research-State-Suche mit `WEB_OBSERVATION` / `authority:NONE`; es konsumiert nicht automatisch den separaten Atlas-Graph. Der Spezialisten-Workshop exportiert eine Bibliothek, aber ein produktiver Importer wurde nicht gefunden.

## Erhaltene ältere Entwicklungen mit fehlenden Quellpfaden

| Zweig / SHA | Noch zu bewertende Arbeit | Entscheidung |
|---|---|---|
| `experiment/continuum-synapse-zero-20261022` · `2b58ed17861de986d97c756be4205993e1233f32` | SynapseZeroShadow, ContrastiveRoleProbe, Tests | Shadow-Forschung bewahren, keine Aktivierung ohne neue unabhängige Messung |
| `opus/continuum-synapse-v2-20261022` · `79a3079efb1de80e0fd648e2192882bc3d4c1ed4` | SynapseV2Reasoner + Benchmarks | explizit JVM/in-memory/kein Chat; negativen Transferbefund erhalten |
| `copilot/continuum-phase2-adaptive` · `3d7608023b3f7ebbdf09ecb29aef48816685b594` | TaskComplexityAnalyzer + AdaptiveReasoningTest | erklärbare Heuristik, keine kalibrierte Intelligenzmessung; Vergleich mit aktuellem Router nötig |
| `development/continuum-backlog-20261015` · `cd860569ba0a2596bf3270873e0dccd98770c201` | Backlog-Interop/CLI/V2-Tests | gegen kanonischen Backlog vergleichen, keine zweite Verwaltung schaffen |
| `copilot/continuum-phase1-runtime` · `9eaa591239b54dc486ecc9d94457621963e34a91` | acht alte `continuum/*` Laufzeitdateien + Tests | entsprechende neuere `cognitive/*` Konzepte vorhanden; kein Vollfile-Overwrite |
| `copilot/continuum-selective-correction-revocation-20261015` · `c3b77c0093cfddbc7193f805730dd6ba2eae7823` | AnswerCorrection + alte Korrekturtests | semantisch gegen aktuelle Owner-Korrektur/Widerruf prüfen |

## CDX1: Befund zum wiederholten „n“

Die Byteumwandlung verwendet UTF-8 mit unsigned Byteindizes; kein Tokenisierungsfehler wurde nachgewiesen. Der Decoder hat 7.746 Parameter, 16 Hiddenunits und acht Kontextbytes. Jeder Paarprompt endet mit `\nAntwort: `, also beginnt jede Generierung mit demselben sichtbaren Kontext `ntwort: `. Beleg/Frage unterscheiden sich nur durch die separate eingefrorene 16D-SWB1-Repräsentation aus gehashten Zeichen-N-Grammen. Bei Greedy-Decoding kann nach acht `n` derselbe Zustand immer erneut `n` auswählen. Dies ist eine plausible Kollapsursache, **keine Reproduktion des fehlenden Fold7-Checkpoints**. Die vorhandene Qualitätswertung zählt solche Ausgaben nicht als Erfolg; sinkender Teacher-Forced-Loss beweist keine richtige freie Antwort.

Zwei konkrete Quellfehler wurden gefunden: (1) der lesende Auditpfad ruft den schreibend wiederherstellenden SWB1-Loader auf; (2) der manuelle Lesetest beschreibt einen korrupten CDX1-Checkpoint als fehlend und empfiehlt ein Training, das der vorhandene Schutz zu Recht blockiert. Reparaturen brauchen Dateiunverändertheits- und Statusregressionen, keine neuen Gewichte oder Wiederholungsunterdrückung.

## Reihenfolge nach dieser Reparaturrunde

1. Aktuelle Android-Compile/JUnit/widget/lint-Gates schließen, Modellidentität über echten SHA statt nur Katalog-ID anzeigen, lesende CDX1-Diagnose wirklich lesend halten.
2. Bestehende Fold7-Kette kontrolliert abnehmen; bestehendes Termux nicht neu erfinden. Ohne Geräte-/Signierzugriff Quellstand und präzise Blocker liefern.
3. Einheitliche bestehende Hologlas-Unterdialoge und Navigation gerätebezogen prüfen; keine verlorenen Funktionen oder doppelte Gemma-Einstiege aus #148/#149.
4. Bestehende Skill-Reviewbelege an kanonische dauerhafte Evidenz anbinden; Atlas/Academy zunächst nur über authentifizierten vorhandenen Statuskanal zeigen.
5. Erst danach freigegebene DevelopmentCoordinator-Vorschläge hash-/pfadgebunden in den bestehenden typisierten CCC1-Vertrag überführen. Modelltests bleiben Vorschläge, feste unabhängige Tests und OS-Sandbox bleiben Autorität.

DeepSeek: 510.296.708.312 deklarierte Bytes sind rund 475,25 GiB. Die vorhandene konservative Speicherpolitik verlangt rund 695 GiB inklusive Reserve; vier H200/384 GiB RAM sind lediglich ein Planungsprofil, keine verfügbaren Ressourcen und kein Inferenzbeweis. Netzwerk-/Persistenz-/Quantisierungs-/Offloadwerte sind vor realer Bereitstellung zu messen. In dieser Runde keine DeepSeek-Gewichte, GPU-Instanzen oder neuen Render-Dienste. Original-Gemma-Byteprüfung wird getrennt protokolliert. Enterprise-Mandantenfähigkeit, Discovery Engine und freie Spielgenerierung bleiben künftige Meilensteine, keine aktuellen Produktbehauptungen.
