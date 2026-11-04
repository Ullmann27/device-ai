# Grok 2 – Continuum Ω Cognitive Brain Architect: Übergabe M2 (Gedächtnis-Kern)

Stand: **03.11.2026, Europe/Vienna**. Branch `continuum/m2-cognitive-memory`, gestapelt auf
`continuum/m1-grok-multiturn-routing-20261103` (PR #369, Grok 1, Basis-SHA `7316b1f5`).
`main` (`2949893a`, 53 Dateien) ist **nicht** die Gerätelinie und wurde nicht verändert. Kein Merge, kein Force-Push.

Belegstufen: `HOST_VERIFIED` = auf diesem Rechner ausgeführt; `SOURCE_ONLY` = nur Quelltext; `RESEARCH_HYPOTHESIS`.

## 1. Bestandsaufnahme – was es schon gibt und wie es zusammenhängt

| Komponente | Ort | Rolle heute | Bezug zu M2 |
|---|---|---|---|
| **Lernkern / Ledger** | `learning/ContinuumLearningCore.kt`, `ContinuumLearningExperience.kt` | Kanonisches, persistentes Besitzergedächtnis (`USER_FACT`, `REVOKED`-Tombstones, SHA-adressiert, max. **256** Datensätze) | **Einzige** Langzeit-Quelle von M2; Hypothesen-Gate hier eingebaut |
| **SWB1** | `TrainableSubwordEncoder`, `ContinuumMemoryDialogue.rank` | Kleiner trainierbarer Encoder (4.113 Parameter) für Abruf-Ranking | unverändert; M2 ist Zusatzspezialist, kein Ersatz |
| **EWM2-Gehirn** | `learning/ContinuumBrain.kt` (`ContinuumBrainBridge`, `Ewm1Brain`) | Zentrales Gehirn mit Besitzer-Schalter OFF/SHADOW/ON, Widerrufsschutz, Rückfrage statt Raten | M2-Spezialist nutzt **denselben** Schalter + `ContinuumBrainResult`-Vertrag |
| Besitzer-Attribut-Scope | `learning/OwnerAttributeEvidenceScope.kt`, `CertifiedLocationReflex` | Exakte Alters-/Ortsantworten, Negation als Barriere | M2 übernimmt Prinzip „Negation = Barriere“, „neuere Aussage ist kein Freibrief“ |
| **TemporalRoleEvidenceLedger** | `cognitive/TemporalRoleEvidenceLedger.kt` | Bitemporale Forschungs-Nachschlagefunktion (Konflikt statt Überschreiben) | **Wiederverwendet** als letzte Punktabfrage in M2 |
| **MemoryEcology** | `cognitive/MemoryEcology.kt` | Typen (`MemoryRecord`, `MemoryWritePolicy`, `ClaimGraph`, Reputation/Router), nur In-Memory, nicht am Ledger | Begriffe übernommen; Verdrahtung mit Ledger = nächstes Inkrement |
| ProofCarryingMemory | `cognitive/ProofCarryingMemory.kt` | Signierte Mutationskette (nicht verdrahtet) | Kandidat für signierte Korrekturen (M3) |
| HybridMemoryRetriever / MemoryRetrievalResearchGate | `cognitive/` | Lexikalisch/semantischer Retriever, LongMemEval-inspirierte Forschungsschranke | unverändert |
| **ExperienceCompiler** | `cognitive/ExperienceCompiler.kt` | Wiederholte erfolgreiche Werkzeugfolgen → Skill-Kandidat nur mit Besitzerfreigabe | M2 liefert prüfbare (SHA-belegte) Aussagen; Skill-Bezug unverändert |
| **ComputeFabric** | `cognitive/ComputeFabric.kt`, `ContinuumComputeReuseResearch.kt` | Begrenzte Rechenknoten, inhaltsadressierte Wiederverwendung | siehe Forschungsdokument (Sparse Compute) |
| **CRE1** | `learning/Cre1*.kt` | Forschungs-Encoder (Node-Checkpoint 4,2 MB), **wählt keine Antworten** | unverändert; kein Upgrade ohne Promotion-Gate |
| **CDX** | `cognitive/Cdx1EvidenceSlotBinder.kt`, `learning/ContinuumCdxLargeLab.kt`, CDX2-Schattenadapter | Beleg-Slot-Bindung / Forschungsgenerator, SHADOW | unverändert |
| **Gemma / LiteRT-LM** | `localmodel/LiteRtLmLocalModelRuntime.kt`, `ContinuumGemmaRestorePolicy.kt` | Lokales Sprachmodell; Formulierung, keine Faktenautorität | M2-Antworten sind deterministisch, nicht Modelltext |
| Kurzzeitkontext | `cognitive/LocalConversationContext.kt`, `LocalFollowUpTaskFrame.kt` | Flüchtiger Sitzungskontext (Grok 1 / anderer Bot arbeitet daran) | M2 liest nur Nutzerturns als Arbeitsgedächtnis (Übergabe unten) |

**Zentraler Befund (RED, `HOST_VERIFIED`):** „Merke dir: Wenn ich im Lotto gewinne, kaufe ich ein Haus“ wurde über
`BootstrapBrain` → `AgentActivity.rememberOwnerFact` → `ContinuumLearningCore.confirmOwnerFact` als
`OWNER_CONFIRMED`/`CONFIRMED` gespeichert. Im 10-Jahres-Fixture landeten **25/25** hypothetische, unsichere, geplante,
gefragte oder berichtete Aussagen als bestätigte Fakten; der bestehende Antwortpfad nannte **17-mal** eine nur
hypothetische Stadt.

## 2. Was gebaut wurde (keine zweite KI, keine zweite Datenbank)

| Datei | Inhalt |
|---|---|
| `learning/OwnerStatementModality.kt` | Aussageart: `ASSERTED`, `NEGATED`, `HYPOTHETICAL`, `UNCERTAIN`, `INTENTION`, `QUESTION`, `REPORTED` (nutzt EWM-Lexikon). Nur Behauptung und Negation (als Entfernungsbarriere) dürfen bestätigt werden. |
| `learning/ContinuumLearningCore.kt` (geändert) | Hypothesen-Gate auf **allen** Schreibpfaden (`confirmOwnerFact`, `recordOwnerFact`, `syncOwnerFacts`): Nicht-Behauptungen → `OBSERVED` + Annotation `unbestaetigt:<ART>`; neuer Rückgabewert `OwnerFactChange.HELD_UNCONFIRMED`; Altbestand wird bei `syncOwnerFacts` in Quarantäne gesetzt; `activeOwnerFacts()` filtert zusätzlich; `unconfirmedOwnerStatements()`, `temporalMemoryView()` (read-only). Gleiche Enum-Namen, gleiches Dateiformat (Downgrade-sicher). |
| `learning/ContinuumTemporalMemory.kt` | Ein zeitbewusstes Gedächtnis über dem kanonischen Ledger + Sitzungs-Arbeitsgedächtnis. `ClaimEvent` nach Dossier 6.1; zwei Zeitachsen (Gültigkeit / Wissensstand, Dossier 6.2); Änderung („jetzt“, „seit“, „gezogen“, „nicht mehr“) beendet, Korrektur („Korrektur:“, „nicht X, sondern Y“) zieht zurück; unmarkierter Widerspruch → Rückfrage; nicht lesbare Umzugsaussage → UNKLAR-Barriere; Alter nur „Stand der Angabe“; letzte Punktabfrage über `TemporalRoleEvidenceLedger`. Begrenzte deutsche Grammatik (Wohnort, Arbeit, Alter, „Mein X heißt Y“), sonst `null`. |
| `learning/ContinuumTemporalMemorySpecialist.kt` | Spezialist im **bestehenden** EWM2-Gehirnvertrag: `ContinuumBrainSettings` (OFF/SHADOW/ON), `ContinuumBrainResult`, Widerrufs-Nachprüfung, „Schalter während des Zugs aus“ → nie ON veröffentlichen. Schreibt nichts. |
| Tests | `OwnerStatementModalityTest` (8), `OwnerHypotheticalGateTest` (5), `ContinuumTemporalMemoryTest` (13), `ContinuumTemporalMemorySpecialistTest` (6), `ContinuumTemporalMemoryBenchmarkTest` (2), `ContinuumTemporalMemoryHoldoutTest` (1) |

## 3. RED → GREEN (`HOST_VERIFIED`, JDK 17.0.21, Gradle-Wrapper, `--no-daemon`)

| Schritt | Commit | Ergebnis |
|---|---|---|
| RED: Tests + Stubs | `24158455` | **34 Tests, 30 FAIL** (`docs/evidence/m2-cognitive-memory-20261103/red-run.txt`) |
| GREEN: Implementierung | `f16427c2` | **34/34** |
| RED: neu geschriebener Holdout (20 Formulierungen) | `c82a320b` | **1 FAIL: 4 falsche Antworten** (Adverb vor „ich“, „in Graz und in Wien“, „aber nur unter der Woche“, „Ich bin umgezogen, jetzt …“) |
| GREEN: Inversion, Einschränkungen, UNKLAR-Barriere | siehe PR | Holdout 18 korrekt, 2 Enthaltung, **0 falsch**; alle 35 M2-Tests grün |
| RED aus voller Suite: Präferenz „Ich wünsche Antworten auf Deutsch.“ als Plan erkannt | – | 1787 Tests, **1 FAIL** (`ContinuumPublicCurriculumTest`) |
| GREEN: Wunsch nur bei Zustandswechsel-Infinitiv = Plan | siehe PR | volle Suite **1787 Tests, 0 FAIL, 5 skip**; Node 362/359 pass/0 fail/3 skip |

Vollständige Suiten: `docs/evidence/m2-cognitive-memory-20261103/full-suite.txt`.

## 4. Messungen (`HOST_VERIFIED`, synthetisch, ehrlich eingeordnet)

10-Jahres-Fixture 2016–2026, 220 Aussagen über den **echten** `ContinuumLearningCore`-Schreibpfad
(Besitzer: 6 Wohnorte, 20 Personen mit 1–3 Wohnorten, 25 Hypothesen/Fragen/Berichte, 150 Füllfakten), 273 Fragen.
Wahrheitswerte stammen aus der Fixture-Zeitachse, nicht aus der Implementierung.

| Metrik | Bestehender Pfad (`replyFromMemory`) | M2-Spezialist |
|---|---|---|
| Hypothesen als bestätigter Fakt | **25/25** (vor Gate) → 0/25 (nach Gate) | 0/25 |
| Antworten mit nur hypothetischer Stadt | 17 (vor Gate) → 0 | 0 |
| Zeitfragen korrekt gesamt | 67/273 | **273/273** |
| – „aktuell“ | 13/21 | 21/21 |
| – „ursprünglich“ | 0/21 | 21/21 |
| – „im Jahr X“ | 54/231 (überwiegend korrekt leere Fälle) | 231/231 |
| Latenz p50 / p95 (JVM-Host, 2 Läufe) | 5,3–5,4 / 8,6–9,6 ms | 16,5–18,0 / 22,6–26,8 ms (liest Ledger zweimal wegen Widerrufsprüfung) |
| Ledger-Größe | 46.294 Bytes für 220 Datensätze | gleiche Datei, kein Zusatzspeicher |
| Kapazität | **256 Besitzerdatensätze**, der 257. wirft `IllegalStateException` | – |

**Einordnung:** 273/273 ist *in-distribution* (Fixture-Sätze liegen in der Grammatik). Der Holdout zeigt die echte
Grenze: vor der Korrektur 4/20 **falsch**, danach 0 falsch bei 2 Enthaltungen – der Holdout wurde dabei einmal zur
Reparatur benutzt und ist jetzt ein Regressionssatz, kein blinder Test mehr. Gerätelatenz Fold7: `NOT_RUN`.

## 5. Übergabe an Grok 1 (Routing-Dateien – hier **nicht** verändert)

Nicht angefasst: `AgentActivity.kt`, `BootstrapBrain.kt`, `OwnerChatIntentRouter.kt`, `LocalFollowUpTaskFrame.kt`,
`LocalClockIntent.kt`, `TaskScopedRingStateProjector.kt`, `LocalConversationFollowUpPolicy.kt`, `LocalPromptFrame.kt`,
`PublicKnowledgeTopicRouter.kt`, `LocalConversationContext.kt`, `LocalContextRetentionPolicy.kt`,
`.github/continuum/android-integration-wiring.test.mjs`.

**H1 – Zeitgedächtnis in den Chat-Zug** (`AgentActivity.kt`, lokaler Memory-Pfad, direkt nach
`ContinuumBrainBridge.decide(...)`, auf Basis `7316b1f5` ca. Zeile 3111):

```kotlin
val brainShown=runCatching {
    at.heinz.deviceai.learning.ContinuumBrainBridge.decide(filesDir,core,requestSessionId,
        request.take(512),baseline).outcome
}.getOrDefault(baseline)
val nowMs=System.currentTimeMillis()
val prior=localConversationContext.priorUserTurns(requestSessionId)
val shown=runCatching {
    at.heinz.deviceai.learning.ContinuumTemporalMemorySpecialist.decide(filesDir,core,request.take(512),brainShown,
        working=prior.mapIndexed { i,t -> at.heinz.deviceai.learning.WorkingTurn(t.take(4096),nowMs-(prior.size-i)) }
            .takeLast(64)).outcome
}.getOrDefault(brainShown)
```
Schnittstelle: `ContinuumTemporalMemorySpecialist.decide(appDirectory, core, request, baseline, working, settings, nowMillis): ContinuumBrainResult`.
Default-Schalter OFF ⇒ ohne Besitzeraktion **keine** Verhaltensänderung. Arbeitsgedächtnis wird nie gespeichert.

**H2 – Rückmeldung bei „Merke dir“** (`AgentActivity.rememberOwnerFact`, ca. Zeile 5230): vor
`bootstrapMemory.addFact(fact)` prüfen und neuen Wert behandeln, sonst landet die Hypothese weiter in der
SharedPreferences-Liste („Was weißt du?“) und die App sagt fälschlich „Gemerkt … Owner-Confirmed“:

```kotlin
if (at.heinz.deviceai.learning.OwnerStatementModality.heldAnnotation(fact) != null) {
    runCatching { ContinuumLearningCore(filesDir).confirmOwnerFact(fact) } // speichert nur als unbestätigt
    return@work "Nicht als Fakt gespeichert: Das klingt nach einer Möglichkeit, einem Plan, einer Frage oder einem " +
        "Bericht. Ich habe es als unbestätigte Aussage notiert. Wenn es stimmt, sag es als feste Aussage, " +
        "z. B. „Merke dir: Ich wohne in …“."
}
// … und im fold: change == OwnerFactChange.HELD_UNCONFIRMED -> derselbe Text
```

**H3 – optional** `BootstrapBrain.kt` „Was weißt du?“: unbestätigte Aussagen getrennt anzeigen
(`ContinuumLearningCore.unconfirmedOwnerStatements()`), nie unter „ausdrücklich gespeicherte Fakten“.

**H4 – Quelltextvertrag**: Falls `android-integration-wiring.test.mjs` den neuen Aufruf prüfen soll, dort
`ContinuumTemporalMemorySpecialist.decide` als erwarteten Aufruf nach `ContinuumBrainBridge.decide` ergänzen.

## 6. Sicherheitsgrenzen
- Keine neue Datenbank, kein neuer Speicherort, kein neues Enum im Dateiformat; Arbeitsgedächtnis nur im RAM des Aufrufers.
- Hypothetische/unsichere/geplante/gefragte/berichtete Aussagen werden nie `CONFIRMED`, nie Antwortwert, nie zitiert.
- Antworten nennen Ledger-ID, Datum der Aussage und SHA-256-Präfix; Arbeitsgedächtnis ist sichtbar als
  „nur in diesem Gespräch gesagt, nicht dauerhaft gespeichert“ markiert.
- Keine Netzwerk-, Werkzeug- oder Modellaufrufe; kein Einfluss auf ToolGate/CCC1.

## 7. Testbefehle
```
source /workspace/env.sh   # JAVA_HOME=JDK 17, ANDROID_HOME
./gradlew --no-daemon :app:testDebugUnitTest --tests 'at.heinz.deviceai.learning.OwnerStatementModalityTest' \
  --tests 'at.heinz.deviceai.learning.OwnerHypotheticalGateTest' --tests 'at.heinz.deviceai.learning.ContinuumTemporalMemory*'
./gradlew --no-daemon :app:testDebugUnitTest --continue          # volle JVM-Suite
node --test <58 Android-bezogene .mjs aus .github/continuum, scripts, backend>
```
Metriken: `M2-METRIC`-Zeilen im JUnit-XML (`app/build/test-results/testDebugUnitTest/`).

## 8. Rückfall
Revert der Commits dieses Branches. Bereits als `OBSERVED` quarantänierte Altdatensätze bleiben lesbar; ein älterer
Build behandelt sie als unbestätigt (alle Leser filtern auf CONFIRMED/LEARNED/REPLAY_PENDING). Wiederherstellen per
„Merke dir: <feste Aussage>“.

## 10. Nachtrag 03.11.2026 nachmittags (M2.1–M2.3, Chief-Muster Punkte 4–5)

| Schritt | Commit | RED → GREEN | Ergebnis |
|---|---|---|---|
| M2.1 Sicht-Cache (SHA-256 des Ledger-Inhalts, nicht mtime) | 8928031e | 2 rot → 9/9 grün | warm p95 30,1 ms → 0,6–3,4 ms (3 Läufe), 819/819 richtig |
| M2.2 Blinder deutscher LongMemEval-Holdout (40 Fälle, vor dem Lauf eingefroren: 0cccf5f6) | 6d50631d | einmal gewertet, **nicht getunt** | 34 richtig, 5 enthalten, 1 falsch, 0 verbotene Werte |
| M2.3 Signierte Korrekturen (ProofCarryingMemory-Sidecar `owner-corrections-pcm-v1.log`) | 005162e7 | 5 rot → 6/6 grün | LEX2-Ledger byteidentisch mit/ohne Signatur |
| Punkt 4 Gedächtnisschichten (`ContinuumMemoryLayers`) | 5f87d1e8 | 5 rot → 5/5 grün | Profil / Tagebuch / geteiltes Wissen (read-only) / Suche (enthält sich) |
| Punkt 5 Fähigkeiten-Bibliothek (`ContinuumSkillLibrary`) | 09e17de3 | 4 rot → 6/6 grün | nur ExperienceCompiler-Kandidaten, aktiv erst nach Freigabe mit Namen |

Holdout-Fehler (bewusst NICHT nachgebessert): A8 „Ich habe gekündigt.“ beendet den Job nicht (veraltete Antwort);
T3/T7/C2/C3/M8 enthalten sich. Fixes nur über ein separates Dev-Set und einen neuen Holdout.

### Hook-Punkte (nicht von mir verdrahtet)
- **Continuum HAUPT (Orchestrator/Werkzeug-Register, docs/CHIEF-PATTERN.md):** Arbeiter/Spezialisten erhalten
  `ContinuumMemoryLayers(core).shared()` (`SharedOwnerKnowledge`: nur `facts()`/`answer()`, keine Schreibmethode).
  Ältere Einträge: `layers.search(query, now)` → `abstained=true` heißt „nichts gefunden, nichts erfinden“.
  Skills: `ContinuumSkillLibrary(File(appDir,"skills")).match(request)` liefert nur Name + Anwendungsfall; die
  Anleitung erst per `load(name)`. Vorschläge kommen aus `VerifiedSkillReview.candidates(...)` → `propose(...)`;
  `approve(name, OwnerSkillApproval(utterance, at))` NUR nach ausdrücklichem Ja des Owners mit Skill-Namen.
  Nichts wird automatisch aktiviert; Skills führen nichts selbst aus (Werkzeuge laufen weiter über HAUPTs Register).
- **Continuum 1 (Integration, AgentActivity/BootstrapBrain):** signierte Korrekturen einschalten mit
  `ContinuumLearningCore(dir, AndroidKeystoreIdentitySigner()) { System.currentTimeMillis() }`;
  `core.correctionAudit()` in Diagnose/Status anzeigen (`UNSIGNED_LEDGER_CHANGE` = Ledger außerhalb des signierten
  Pfads geändert; Altbestände vor dem Einschalten erscheinen dort ebenfalls).
- **Avatar (Vertrag bei Continuum HAUPT, PR #373, Paket `avatar`):** ich sende KEINE Avatar-Events und lege
  nichts im Avatar-Paket an. Abbildung vorhandener Gedächtnis-Ergebnisfelder für AgentActivity/AvatarDirector:
  - *erinnert sich* = `ContinuumBrainStatus.ON_MEMORY` bzw. `TemporalAnswerKind.ANSWER`
  - *fragt nach* = `ContinuumBrainStatus.ON_CLARIFY` bzw. `TemporalAnswerKind.CLARIFY`
  - *hält unbestätigt / enthält sich* = `ON_ABSTAIN`, `TemporalAnswerKind.UNCONFIRMED_ONLY`/`NO_EVIDENCE`,
    `OwnerFactChange.HELD_UNCONFIRMED`
  - *korrigiert* = Journal-Eintrag `MemoryMutationReason.USER_CORRECTION` bzw. `FALLBACK_REVOKED`
  Braucht der Avatar dafür einen neuen Zustand, muss das über `AvatarRuntimeSignals` + Prioritätsliste mit Test
  laufen — Eigentum von Continuum HAUPT.

### Risiken
- Latenz-Budget (<5 ms) ist ein Host-JVM-Timing; auf langsamer CI kann es schwanken (p95 0,6–3,4 ms gemessen).
- Der Cache ist prozessweit (max. 8 Verzeichnisse); jeder Abruf hasht die Ledger-Datei (~46 KB) erneut.
- Journal-Verifikation liest bei jedem Schreiben die ganze Kette (O(n)); bei sehr vielen Korrekturen später
  eine geprüfte Kopfmarke cachen.
- Skill-Integrität ist nur Hash-basiert (lokale Datei); Signatur über IdentitySigner ist Folgearbeit.
- Kapazitätsgrenze 256 Owner-Datensätze unverändert (Entscheidung Heinz), Test dokumentiert sie weiterhin.

## 9. Nächster Einstieg
Siehe `docs/research/omega/M2-NEUROSYMBOLIC-SPARSE-LOCAL-INFERENCE-20261103.md`, Abschnitt „Nächste Inkremente“.

## 11. Übergabe an Jarvis Kern / Continuum 1: Cockpit-Gedächtniswerte (M2.7) und LEX3 (M2.5)

**Nur lesend, keine Verdrahtung durch Grok 2.** UI/Routing verdrahten Jarvis Kern bzw. Continuum 1.

```kotlin
// at.heinz.deviceai.learning.ContinuumMemoryStats.kt
data class ContinuumMemoryStats(val totalEntries:Int, val newToday:Int, val lastActivity:Instant?,
    val confirmed:Int, val unconfirmed:Int, val hypothetical:Int, val revoked:Int)
class ContinuumMemoryStatsReader(appDirectory:File, clock:Clock = Clock.system(ZoneId.of("Europe/Vienna"))) {
    fun read():ContinuumMemoryStats
}
```

- `appDirectory` = dasselbe Basisverzeichnis wie `ContinuumLearningCore` (liest `learning-core-v2/experiences-v2.bin`).
- Alle Werte aus dem echten Ledger; leer/fehlend → `0` bzw. `lastActivity = null`. Keine Defaults, keine erfundenen Zahlen.
- `newToday` ab Mitternacht in der Clock-Zone (Standard Europe/Vienna, Clock injizierbar für Tests).
- `confirmed/unconfirmed/hypothetical/revoked` zählen nur Besitzeraussagen (`USER_FACT`); hypothetisch = gehalten mit Modalität HYPOTHETICAL.
- Liest pro Aufruf die Datei (bei 1000 Einträgen ~11–16 ms p95 kalt) → im Cockpit nicht pro Frame, sondern z. B. alle paar Sekunden / bei Änderung abfragen.
- Thinking-Journal-Hypothesen (`ContinuumThinkingMemory`, Sidecar `thinking-journal-v1.log`) sind **nicht** in diesen Zahlen enthalten.

**LEX3:** Ledger-Format versioniert (Magic `LEX3` + Version 3), Kapazität 4096 statt 256, max. 48 MiB. LEX2-Dateien (≤256) werden verlustfrei gelesen und beim nächsten Schreiben nach LEX3 migriert; die LKG-Sicherung behält die alte LEX2-Generation. `PersonalLearningTransfer.inspectDocument` akzeptiert LEX2 und LEX3.

**PR #477 (ChatGPT):** nur Docs (`REFERENCE-COCKPIT-HANDOFF-20261104.md`, `device-experience-20261104/PLAN.md`), kein Gedächtnis-Code; dort explizit „No parallel memory“. Keine Überschneidung/Konflikt; das Cockpit soll seine Gedächtniszahlen aus `ContinuumMemoryStatsReader` beziehen.

## 12. Übergabe an Jarvis Kern: ContinuumMemoryEvents (M2.11)

Signatur exakt nach Kern-Vorgabe in `learning/ContinuumMemoryEvents.kt` (neben `ContinuumMemoryStatsReader`), plus Companion `ContinuumMemoryEvents.forApp(appDirectory)` (kanonischer Pfad = Schlüssel, eine Instanz pro App-Verzeichnis).

Auslöser (nur echte Aufrufe):
| Ereignis | Ort | Inhalt |
|---|---|---|
| `MemoryStored` | `FileLearningExperienceStore.save` (alle Ledger-Schreibwege: append, appendMissing, updateStatus, Korrekturen, Recovery-Salvage) | `entryId` = Ledger-ID `lx-…`, Status, `atEpochMs` = `updatedAt` |
| `DocumentLearned` | `ContinuumKnowledgeArchive.save` (neuer Quelltext, `kind` = Archiv-Kind, 1 Eintrag) und `importZip` (`kind` = `ARCHIVE_IMPORT`, `docId` = SHA-256 der neuen Record-IDs, Anzahl neu) | nur IDs |
| `RecallHits` | `EventEmittingRetriever.search` (`ContinuumMemoryRetriever.default(appDirectory = …)`; ThinkingMemory nutzt das) | `queryHash` = SHA-256(Salt ‖ Frage), Salt 32 Byte zufällig in `learning-core-v2/.memory-event-salt`, nie im Ereignis; Rang ab 1 |

Kein PDF-Importpfad im Repo gefunden; der echte Dokument-Import ist das Knowledge-Archiv. `learnFromSources` meldet über `MemoryStored`.
Status: REVOKED/REJECTED → REVOKED; gehalten mit Modalität HYPOTHETICAL → HYPOTHETICAL; CONFIRMED nur bei Besitzer-Vertrauen; sonst UNCONFIRMED. Hypothesen nie CONFIRMED.
Zustellung: eigener Daemon-Single-Thread pro App-Verzeichnis, nie auf dem Aufrufer-Thread; Listener-Exceptions werden gefangen und geloggt; nach `close()` keine Zustellung mehr (auch nicht für bereits eingereihte Ereignisse). Ohne Listener entstehen keine Kosten (kein Diff, keine Hash-Berechnung).

## 13. Ledger-Recovery: Persistenz, IO-Fehler, offene Punkte (M2.10/M2.12)

- `LedgerRecoveryReport` wird als `experiences-v2.recovery.properties` (nur Metadaten: Offsets, Zähler, Pfade, fester Grundtext, Fehlerklassen) neben dem Ledger abgelegt; `lastRecovery()` liest ihn nach Neustart wieder.
- Scheitert Sicherung, Quarantäne oder Report-Datei (voll/schreibgeschützt), wird nur das geloggt bzw. in `sideEffectFailures` vermerkt; das Laden klappt trotzdem. Konnte die Quarantäne nicht geschrieben werden, bleiben die beschädigten Bytes in der Hauptdatei, und Schreibzugriffe werden mit `IOException` abgelehnt, bis die Quarantäne beim nächsten Öffnen gelingt (keine Beweise überschreiben).
- Offen (nur dokumentiert): Quarantäne-Dateien werden nie automatisch aufgeräumt (Vorschlag: Owner-Aktion „Quarantäne prüfen/löschen“ mit Report-Anzeige); bei Wiederherstellung aus einer Sicherung gehen spätere Bestätigungen verloren (nur Widerrufe werden restriktiv übernommen); in einem lange laufenden Prozess wird die Sicherung nur alle 256 Anhänge erneuert; bei leerem gültigem Präfix ohne Sicherung startet das Gedächtnis leer (Daten nur in Quarantäne); Report hält nur die letzte Recovery.
