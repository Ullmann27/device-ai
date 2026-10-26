# Continuum Interactive Intelligence Lab — gemessener Forschungsstand

Stand: 26.10.2026. **RESEARCH_SHADOW. Keine allgemein bessere Gesprächsintelligenz und keine bessere Gemma-Antwortqualität nachgewiesen.** Tatsächliche native Experimente A–G sind ausgeführt; die Grenzen und Gegenbeispiele bestimmen die Auswahl. Ein einfacher Dialogtest verbessert sich stark, scheitert aber an schwierigeren Korrekturen. Deshalb wird dieser Dialogkandidat nicht integriert. Die kleine, rücknehmbare Verwaltung synthetischer Korrekturkandidaten ist separat zur Quellprüfung veröffentlicht.

## 1. Veröffentlichte Quellen und Abstammung

- Ausgang: `b1d9556c74df9aa3c6e05defc9b4fb7d94181f17`, wie vorgegeben. Enthält PR173s Archiv-/Folgefragenreparatur; ursprüngliche Mars/Nobel-Fehlroute wird nicht erneut als eigene Reparatur verkauft.
- Forschungsbranch: `research/continuum-interactive-intelligence-lab-20261026`, [Draft PR174](https://github.com/Ullmann27/device-ai/pull/174), Basis `review/continuum-followup-archive-gate-20261026`.
- Vorregistrierung: `646dcae2377cbf79deac31de9deaf9580572bd1d`.
- Unveränderte Ausgangsmessung und versiegelte Daten: `8c11de1656d5c3d31ca7ce481583897611c48abf`.
- Erste A/B-Implementierung: `9d36e06b58a34e25aebef3cff8c7c55a32e95c41`.
- **Vor Holdout eingefrorener und tatsächlich geprüfter Kandidat:** `d7df7a45ea8002d8adaf2b45745b373416ae96dd`.
- Vollständige Holdout-, Gegenproben- und Checkpointveröffentlichung: `cdb9e7215e4f8bf17f3865d8df0f36a87d74d520`.
- Selektiver B-Review: `integration/continuum-feedback-review-20261026`, [Draft PR175](https://github.com/Ullmann27/device-ai/pull/175), `08bfe94f21a9741a2144e9e4bae72b341fa68332`. Eigenständig auf b1d9556c; übernimmt genau ResearchFeedback, vier Tests, den kleinen Prüfläufer und die Übergabe. Keine unkontrollierte Übernahme des gesamten Forschungsbranches.
- Main zuletzt unverändert geprüft: `190f5eabaa60fc59292b2b80d3086aafd7743a80`.

Der erste GitHub-Audit liegt unter `docs/evidence/interactive-intelligence-20261026/github-audit-start.json`. PR171/173 standen auf b1d9556c; PR172s Grounding-Port war separat auf ad15aab9 und wurde nicht pauschal integriert. Keine Force-Pushes, Löschung fremder Branches oder Änderung von Zugriffsrechten. Ein späterer Dokumentationscommit verändert die Zuordnung der Messwerte zur eingefrorenen Code-SHA nicht.

## 2. Versuchsmethodik und ehrliche Vergleichsgrenze

504 öffentliche synthetische Dialogfälle:168TRAIN/168DEV/168HOLDOUT, Seeds73/101/211. Namen und Rückfrageformulierungen sind zwischen den Splits getrennt; die14strukturellen Fallfamilien sind gemeinsam. Dateien wurden vor Kandidatencode gehasht. Der Läufer prüft diese Hashes und eindeutige Fall-IDs vor Ausführung. Keine privaten Chats, keine Besitzererinnerungen und keine Modellantworten als Trainingswahrheit.

Diese Daten sind **entwicklerentworfene Holdouts, keine unabhängig verblindete externe Studie**. Manche Ausgangsnamen ohne numerisches Suffix wiederholen sich innerhalb eines Splits. Zahlen über Modellseeds sind wiederholte Messungen, nicht unabhängige neue Domänen. Nach dem Holdout wurde der Kandidat nicht an die beobachteten Fehler angepasst. Die Zusatz-Gegenprobe bleibt für eine künftige Version ein Regressionstest und darf dann nicht wieder als unbekannter Holdout gelten.

Drei Kontextarme verwenden den tatsächlichen `LocalConversationContext`: RAW, STRUCTURED und ADAPTIVE. Der aktuelle RAW-Puffer ist kein eigenständiger Referenzklassifikator; dessen Antwortgenauigkeit wird nicht erfunden. Für Referenzentscheidungen wird separat der reale, bereits vorhandene CTX3-Forschungsauflöser verglichen. Das ist ausdrücklich kein Ersatz für einen Gemma-Qualitätsvergleich im produktiven Chat.

Die frühen DEV-Läufe entstanden teilweise im noch nicht committeten Arbeitsstand. Ihre `source-sha.txt` bezeichnet den damaligen Git-Basisstand. Maßgeblich für reproduzierbare Resultate sind der saubere Freeze d7df7a45, `holdout/source-blobs.sha256`, die vollständigen Befehle in `holdout/execution.json` und die Einzelzeilen der Messdateien. Siehe zusätzlich `PROVENANCE.md`.

## 3. Ergebnisse je Experiment

| Experiment | Ausgang | Kandidat / Befund | Entscheidung |
|---|---|---|---|
| A: Referenzen,168synthetische Holdoutfälle | CTX3 60korrekt,0falsch,156Enthaltungen | STRUCTURED/ADAPTIVE jeweils168korrekt,0falsch,48berechtigte Rückfragen | Allein kein Integrationsbeweis |
| A: notwendige Ausgangsaussage erhalten | RAW72/84 | STRUCTURED/ADAPTIVE84/84 | Begrenzter, tatsächlicher Kontextgewinn |
| A:8zusätzliche schwierige Korrekturen | CTX3 4korrekt,0falsch,8Enthaltungen | ADAPTIVE3korrekt,4falsch,1Enthaltung | **v1 verworfen für Integration** |
| B: Korrekturaufnahme | bestehender Ledger als unveränderte Persistenz | explizite Digest-Prüfung, versionierte Quelle, Konfliktausschluss, Widerruf, Wiederanlauf und Race-Test | kleiner separater Quellenreview; nur synthetisch |
| B/F: SWB1-Ranking,3Seeds |34/36 |34/36nach96Updates je Modell | kein Lerngewinn; keine Übernahme |
| B/F: SWB2-Ranking |36/36 |36/36 | kein Lerngewinn; keine Übernahme |
| B/F: CRE1-Ranking |36/36 |36/36 | kein Lerngewinn; keine Übernahme |
| C: zwei nicht unterscheidbare Bedeutungen |20/40ohne Rückfrage |20/40nach400zusätzlichen SWB1Updates;40/40mit einer wahrheitsgemäßen Orakelantwort je Fall, je3Seeds | Informationsgewinn im kontrollierten Spiel; keine bewertete reale Mensch-KI-Fragequalität |
| D:16endliche Gegenfaktikläufe | Druck-allein-Heuristik12/16 | explizites kausales Boolesches Modell16/16; unbekannte Folgen bleiben unbekannt | Mechanismus funktioniert; Gemma-Vergleich NOT_RUN |
| E:80synthetische Aussagen |20korrekt,60falsch/unbelegt | Orakelprüfung behält20korrekte, verwirft60;0falsche verbleiben | Filterbeleg, kein generatives Selbstverbesserungsergebnis |
| F: untrainiertes CDX1,36Generierungen | untrainierter echter Decoder |0/36exakt richtige Antworten | nicht für Antworten ausgewählt |
| G1: Belege einzeln entfernen | drei Gegenproben |2/3; redundante richtige Belege werden fälschlich als ungestützt beurteilt | verworfen |
| G2: Rangfolge gegen Listenreihenfolge prüfen |16/24korrekt,8falsch |8/24korrekt,0falsch,16Enthaltungen, doppelte Rankingaufrufe | deutlicher Abdeckungsverlust; keine Integration |

Korrekte Entscheidungen zählen auch berechtigte Rückfragen/Enthaltungen. Die Enthaltungszahl ist deshalb keine zusätzliche disjunkte Fehlerklasse, sondern eine Teilmenge aller Entscheidungen.

Die Retentionszahl72/84 betrachtet nur Fälle mit einer tatsächlich verlangten Prämisse. Frühere Rohsummen156/168 enthalten84Fälle ohne solche Anforderung, die automatisch erfüllt sind. Die finale Zusammenfassung weist beides getrennt aus und vermeidet diesen irreführenden Nenner.

A scheitert konkret an nachgestellter Verneinung (`Aster meine ich nicht`), unsicherer Benennung (`Vielleicht meine ich…`), einer Rückfrage als vermeintlicher Themenbestätigung (`Meinst du…?`) und Themenwiderruf (`…ist nicht mehr unser Thema`). Bei einer zitierten alten Benennung mit anschließender Korrektur enthält sich der Kandidat unnötig. Diese Befunde überstimmen das gute einfache Holdoutresultat; Grenzwerte wurden nicht abgesenkt.

B nutzt `USER_FEEDBACK`, `SYNTHETIC_FIXTURE` und `SYNTHETIC`, niemals `USER_FACT` oder Besitzerprovenienz. Ein sprachlicher Speicherwunsch ist keine Bestätigung. Nur ein separater expliziter Reviewaufruf mit passendem Digest und späterer Version erlaubt den Statuswechsel. Zwei bestätigte Revisionen desselben Turns werden nicht gleichzeitig positiv trainiert. Nach Widerruf verändert sich der aktive Trainingsfingerabdruck; alte Checkpoints sind nicht mehr gültig für Wiederverwendung. Das behauptet keine nachträgliche Entfernung ihres Einflusses aus Gewichten. Produktive Besitzerauthentifizierung ist noch nicht angeschlossen.

Die tatsächlichen SWB1/SWB2/CRE1-Gradienten laufen nur auf den freigegebenen synthetischen Trainingspaaren.96Updates pro Architektur sind kein gleiches FLOP-Budget und bei verschiedenen Verlustfunktionen keine identischen Gradienteninformationen. Es wurden24Snapshots gesichert; alle24dekodierten Snapshotbytes stimmen zwischen DEV-Training und eingefrorener Wiederholung überein. Der SWB2-Zweitfamilienversuch erhält12/12bekannte Trainingsfälle; er beweist weder langfristige Generalisierung noch Replay/EWC-Wirkung. Replay-/EWC-Ablationen sind hier NOT_RUN.

C verwendet bekannte, gleichgewichtete Hypothesen und ein wahrheitsgemäßes synthetisches Orakel. Zusätzliche Wiederholung ohne neue Information kann den verborgenen Zielzustand nicht unterscheiden. D hat16Läufe mit vier verschiedenen intervenierten Wahrheitswertkombinationen, keine16unabhängigen physikalischen Entdeckungen. E produziert keine neuen Modellantworten; ein vollständiger Gemma-Entwurf→Kritik→Revision-Vergleich ist NOT_RUN. G2 garantiert weder Wahrheit noch Invarianz gegenüber allen möglichen Permutationen.

## 4. Tatsächliche Verbindungen und verbleibende Grenzen

| Verbindung | Tatsächlicher Status |
|---|---|
| bestehender Android-Chat → vorhandener Kontext/Archiv-Gate/Gemma-Pfad | aus b1d9556c übernommen, in dieser Mission nicht geändert; kein neuer Gerätetest |
| `LocalConversationContext` → `InteractiveDialogueContext` | echte Kotlin-Komposition, native Forschungsprüfung; benötigt explizite scoped EntityRegistry, default produktiv unbenutzt |
| vorhandene CTX3-Entitäten/Tokenisierung → Kandidat | tatsächliche APIs; kein zweites Sprachmodell |
| vorhandener `ClaimGraph` → Widerspruchs-Rückfrage | native Prüfung expliziter CONTRADICTS-Kanten; keine allgemeine Erkennung widersprüchlicher Prosa |
| Dialogkandidat → bestehende `ContinuumArchiveAnswerPolicy` | read-only Forschungsadapter; Originalfrage bleibt erhalten, Bezugserweiterung umgeht Archivveto nicht |
| fehlendes lokales Modell → Status | `MODEL_NOT_CONFIGURED`; keine erzeugte Ersatzantwort |
| Review → `FileLearningExperienceStore` → isolierte Modellversuche | tatsächlich ausgeführt, synthetische Ledgerdateien und Checkpoint-Abstammung veröffentlicht |
| SWB/CRE/CDX → produktive Antwortwahl | nicht aktiviert; Messwerte rechtfertigen keine produktive Änderung |
| neue Kandidaten → bestehende Android-Haupt-App | **nicht angeschlossen**; A abgelehnt, B nur separater Quellenreview |
| Coding/CCC1/Worker/ComputeFabric/ToolGate | bestehender Stand erhalten, keine neue Freigabe oder parallele Pipeline |

Das System benötigt weiterhin einen zuverlässigen Adapter vom realen Dialog und autorisierten Quellen zur scoped Registry. Im Labor wird diese Registry aus dem synthetischen Fall bereitgestellt; daraus darf kein allgemeines autonomes Entitätenverständnis abgeleitet werden. Kompression ist begrenzt und verlustbehaftet:24Turns, Quellenwechsel und Sitzungswechsel leeren den Zustand, Ausgaben sind auf8192Zeichen begrenzt. Kein Beweis, dass bei jeder beliebigen langen Konversation alle relevanten Zusammenhänge erhalten bleiben.

## 5. Prüfungen, Fehler und NOT_RUN

- Native Kotlin2.2.0, Java21.0.12.1, JVM-Ziel17:15neue Laborprüfungen PASS; separat21vorhandene Archiv-/CTX3-/Antwortqualitätsprüfungen PASS.
- Kleiner B-Review:4native Tests PASS im Forschungscheckout und erneut4PASS im isolierten Integrationscheckout (dieselben4Tests, nicht8unabhängige). Zusammen40verschiedene JUnit-Prüfungen.
- Native Nodev24.19.0:1370Tests, **1361PASS / 1FAIL / 8SKIP**, Exit1,128.318s. Enthält bestehende Schnittstellen-/Testdoubleprüfungen; dies ist kein Modellqualitätsbeleg.
- Der Fehler: `OWNER_V25_BLOCKED: New unattested source .../InteractiveDialogueContext.kt`, Status69. Der eingefrorene v25-Release-Nachweis muss neue Forschungsquellen ablehnen. Unveränderter Startcommit b1d9556c im eigenen Worktree:4/4Releasekontrollen PASS. Weder Manifest noch Gate wurden geändert, um die Forschung als freigegebenen Release auszugeben. Die Gesamtsuite ist dennoch ausdrücklich **nicht vollständig grün**.
- Acht Skips:6Tests benötigen `kotlinc` auf PATH,1separat konfigurierten Kotlin→Node-Fabricexport,1explizite Docker-Reportlimitkonfiguration. Der vorhandene Kotlin-Embeddable-Compiler wurde über Java genutzt, kein `kotlinc`-CLI installiert. Keine erneute CCC1-/Docker-Erfolgsbehauptung.
- Syntax-/Shell-/Diffprüfungen bestanden für die neuen Läufer. Die tatsächlichen Originalquellen wurden kompiliert; keine Kotlin-Typstubs.
- Fehlgeschlagene wissenschaftliche Prüfungen: A-Adversarial4falsche Bezüge; G1-Redundanz; keine Verbesserung durch SWB/CRE-Training; CDX1-Exaktantwort0/36. Erfolgreiche Unit-Tests heben diese Ergebnisse nicht auf.
- Infrastruktur: `/usr/bin/time` fehlte; erster Aufruf endete127vor Versuchsbeginn. Ersatzmessung mit Python `resource.getrusage`, Rohbeleg gespeichert.
- **NOT_RUN:** vollständiger Android-/Gradle-Build, APK, Signatur-/Migrations-/Fold7-Abnahme, tatsächliche neue Gemma-Inferenz, private Besitzerkorrekturen, produktive Lernübernahme, Langzeitretention und umfassende Replay/EWC-Ablationen. Kein SDK, Gradle, ADB, apksigner oder Modellgewicht im aktuellen Workspace gefunden. Besitzerbericht über21sGemma-Generierung bleibt ein Besitzerbericht und wird nicht als eigener Lauf ausgegeben.

## 6. Ressourcen

Strukturierter Kontext p95:2.639ms, adaptive Variante3.386ms auf diesem Host; maximal471bzw.834UTF-8Bytes in diesen Fällen. Registry-Aufbereitung ist enthalten. RAW-Kandidatenzeit enthält dessen Verwaltung und ist nicht die Laufzeit des ursprünglichen Renderers allein. Keine gemessene Fold7-Latenz oder Energie.

Der eingefrorene Kotlin-Compile/JUnit/A-Lauf dauerte57.512s; native CTX3-Baseline0.815s; Modell-/C–G-Suite8.084s. Python meldete einen kumulativen Kindprozess-RSS-Höchstwert von816960KiB einschließlich Compiler; das ist keine isolierte Modell-RAM-Messung.24komprimierte Snapshots belegen17418804Bytes. Kein Modell-/Abhängigkeitsdownload, kein externer Modellaufruf, kein kostenpflichtiger Dienst; Netzwerk nur für autorisierte GitHub-Lese-/Publikationsschritte, deren Gesamtvolumen nicht gemessen wurde.

## 7. Fortsetzung für GPT-6 und Astra

1. PR174, diesen Bericht und `docs/evidence/interactive-intelligence-20261026/promotion.json` lesen. A v1 bleibt gesperrt. Haupt-App, Bilder/Hologramme/Layout, Zertifikat, bestehende Erinnerungen und Checkpoints wurden nicht verändert.
2. Bei B mit PR175 beginnen. Prüfen: echte authentifizierte Besitzeraktion über vorhandenen ToolGate/UI-Pfad, Bindung an tatsächliche Conversation-/Turnidentität und Quelle, Schutz vor prozessübergreifenden Rennen. Aktuell ist die Synchronisierung nur innerhalb eines JVM-Prozesses garantiert; synthetische Herkunft nicht durch Änderung eines Flags als echte Besitzerherkunft ausgeben.
3. A-Gegenbeispiele als bekannte Regressionen übernehmen. Für einen neuen Kandidaten eine neue unabhängige Dialogsammlung und vorab festgelegte Sicherheits-/Abdeckungsgrenzen anlegen. Nicht dieselben acht Fälle nach Reparatur als unbekannte Generalisierung verkaufen. Unterschied zwischen Frage, Vermutung, expliziter Korrektur und Widerruf muss vor Fokuswechsel geklärt werden.
4. Vor Anschluss an `AgentActivity` reale scoped Memory-/Registry-Zuordnung und begrenztes Promptbudget prüfen. Bestehende `LocalFirstPolicy.localModelQuestion`, Sessiongrenzen und `ContinuumArchiveAnswerPolicy` erhalten. Keine zusätzliche Chatarchitektur.
5. Gemma-Vergleich erst mit tatsächlichem lokalem Runtime-/Modellhash und freigegebenem Gerät: Baseline, strukturierter Kontext, begrenzter Kritiker auf denselben unabhängigen Gesprächen. Antwortqualität mit unabhängigen Oracles beurteilen, Latenz/Temperatur/RAM separat erfassen. Keine privaten Chats ohne ausdrückliche Zustimmung ins Repository.
6. Die bestehenden v25-Releasebestände bleiben eingefroren. Ein späterer Besitzerbuild braucht neue ausdrücklich genehmigte Quellattestierung, vollständige Android-Prüfungen, Paket-/Signatur-/Migrationsevidenz und Geräteabnahme. Ein grüner Laborlauf genügt nicht.

Reproduktion ohne Änderung produktiver Daten:
```bash
git fetch origin research/continuum-interactive-intelligence-lab-20261026
git switch research/continuum-interactive-intelligence-lab-20261026
# Für exakte Kandidatenevidenz einen neuen Worktree auf d7df7a45... verwenden.
CONTINUUM_FABRIC_JVM_DEPS=/absolute/kotlin-junit-jars \
  bash scripts/continuum-interactive-lab.sh /absolute/fresh-results evaluate holdout
CONTINUUM_FABRIC_JVM_DEPS=/absolute/kotlin-junit-jars \
  bash scripts/continuum-interactive-lab.sh /absolute/fresh-results baseline holdout
CONTINUUM_FABRIC_JVM_DEPS=/absolute/kotlin-junit-jars \
  bash scripts/continuum-interactive-lab.sh /absolute/fresh-results suite holdout
node experimental/interactive-intelligence/summarize.mjs /absolute/fresh-results
# Bewusst neu ausführen nur zur Reproduktion, nicht zum Nachoptimieren auf Holdout.
```
Der vorhandene Jar-Satz wird nicht automatisch heruntergeladen. Compiler-/Befehls-/Quellmanifeste liegen in den Belegen. `AdditionalStress` lässt sich mit demselben Klassenpfad ausführen; seine TSV-Gegenproben sind veröffentlicht. Für den selektiven B-Zweig: dessen eigene Übergabe und `scripts/continuum-feedback-review-jvm.sh` verwenden. Rückfall: Forschungsadapter nicht instanziieren/Review-Commits nicht übernehmen; bestehender Android-Dialog ist bytegleich zum Ausgangsstand geblieben.
