# Continuum · Status M5/M6 und nächste ausführbare Schritte (30.10.2026, Opus)

**Kurz:**
- **M5 = NICHT ERREICHT** (Gate „Semantik-Vorbedingung“ gescheitert).
- **Datengate = BESTANDEN**, Medoid-Artefakt ausgeschlossen.
- **M6 = VORBEREITET, NICHT GESTARTET.**
- Kein Produktivcode geändert.

Details: [M5-A3-Ergebnis](EXP-20-M5-A3-RESULT-20261030.md).

## 1. Meilensteinleiste

| Meilenstein | Zustand | Beleg |
|---|---|---|
| M1 Basis · M2 Memory · M3 Coding | ERREICHT (frühere Linien, Draft) | AGENTS.md, PR-Kette |
| M4 P2-Labor | **ERREICHT (Host-Labor)** | P2: A 153 / A0 174 / B 146 / C 149; Regel → A; Sicherheit 7 680/7 680 |
| M5-A1 echter CRE1-Lauf | **ERREICHT** | `e698162e`, Runner Exit 0, Checkpoint-SHA reproduziert |
| M5-A2 Medoid ausschließen | **ERREICHT** | CRE1-Blind VAL 15/128 ≤ 17,5 % |
| M5-A3 semantischer Basisstrom | **REJECTED (für vorhandene Encoder)** | VAL-S ≤ 15/64; Zufall bei Gleichstand 16/64 |
| M5-A4 Hybrid+CRE1 | **INCONCLUSIVE** | formal `SUPERIOR` auf VAL, aber nur durch Gleichstands-Auflösung |
| M5-A5 fremder SEALED | **NICHT GESTARTET** (bewusst geschlossen) | Regel 1 |
| M6-B1 Anbindung prüfen | **ERREICHT (Analyse)** | Abschnitt 3 |
| M6-B2 EMA + gelernte Ereignisse | NICHT GESTARTET (wartet auf Semantik) | – |
| M6-B3 dendritisches Gating | NICHT GESTARTET (Vorbedingung) | P3 |
| M6-B4 Predictive Coding / Reset | NICHT GESTARTET | Abschnitt 4 |
| M7 Fold7-APK · M8 Worker · M9 JARVIS | NICHT GESTARTET (Infrastruktur: Android-SDK gesperrt, Gerät nötig) | – |
| Verworfen | **REJECTED** | CTRNN-Vorteil, DG-SDR, Sparse-CTRNN, INT8/Sign-Schlüssel, Hopfield-2 (Artefakt) |

## 2. Ist- und Soll-Architektur

```
IST (Branch, verifiziert)                                SOLL (M5/M6, nur nach bestandenen Gates)
──────────────────────────                               ─────────────────────────────────────────
Sprache/Text ─► AgentActivity                            Sprache ─► semantische Ereigniserkennung [fehlt: M5]
   │                                                         │
   ├─► LocalConversationContext ─► DialogStateKernel        ├─► Gesprächszustand (DialogStateKernel)
   │      (SHADOW, turn-basiert, nur Report)               │        + monotone Δt [erst nach fremdem Holdout]
   ├─► ChatReasoningRouter.advise ─► ComputeAllocation      ├─► zeitlicher Kontext: EMA (B) statt CTRNN
   │      Policy (SHADOW, argonShadowChoice ungelesen)      │        [M6-B2, nur mit Semantik]
   ├─► AiContextCompiler ─► HybridMemoryRetriever           ├─► HybridMemoryRetriever + semantischer Score
   │      (PRODUKTIV, lexikalisch)                          │        [produktiv erst nach M5-A5]
   ├─► ContinuumCognitiveRuntime ─► Authorized              ├─► Evidenz/EWM2 (autoritativ, unverändert)
   │      ConversationMemoryShadow: Hybrid + CRE1           │
   │      (SHADOW, Diagnose)                                └─► OwnerGate/ToolGate (unverändert) ─► Antwort
   └─► BoundedAgent ─► ToolGate (CONFIRM/DESTRUCTIVE)
```

## 3. M6-B1: tatsächliche Verbindungen (Code-Readback)

| Komponente | heutige Verbindung | Bedeutung für M6 |
|---|---|---|
| `HybridMemoryRetriever` | **produktiv** in `AiContextCompiler.kt:119` und `BoundedAgent.kt:236`, lexikalisch; **mit CRE1** nur in `AuthorizedConversationMemoryShadow.kt:100`, Diagnose über `ContinuumCognitiveRuntime.kt:134`, sowie in `MemoryRetrievalResearchGate` und `MemoryResearchShadowComparator` | Für Hybrid+CRE1 ist **keine neue Schnittstelle nötig**; die Lücke ist die Semantikqualität. |
| `DialogStateKernel` | SHADOW über `LocalConversationContext`; nur der Report wird gelesen (`AgentActivity.kt:3621`) | Als Rückfragesignal nachweislich schädlich (P2: 124 gegen 153) → nicht an `unresolvedReference` hängen. |
| `ComputeAllocationPolicy` | SHADOW in `ChatReasoningRouter.advise`; Ergebnis ungelesen | Einziger relevanter Kanal: `unresolvedReference` → CLARIFY (7 680/7 680). |
| Monotone Zeit | erreicht den Router **nicht** | Signaturänderung nur nach positivem fremdem Holdout und mit Besitzerfreigabe. |
| EWM2 zeitliche Gültigkeit | `intelligence/ewm/Ewm1Workspace.kt`, `Ewm1EvidenceWorkspace.kt` | M6-B4 („seit Dienstag in Graz“) muss **diese** Gültigkeitslogik prüfen und nutzen, **kein** zweiter Faktenspeicher. |

## 4. Nächste ausführbare Schritte (ohne Wiederholung widerlegter Experimente)

1. **WP2-Registrierung, neuer Datensatz `M5b`:**
   - Disjunkte Paraphrasen-Familien für TRAIN, VAL und SEALED: Die Umschreibungen für Tage und Orte sind je Split andere Formulierungen und **nie geteilt**.
   - Dazu dieselben Gates wie in M5: CRE1-Blind ≤ 17,5 %, positionelle Balance, Disjunktheit.
   - Neu: **Zufallsrate innerhalb des lexikalischen Gleichstands** als Pflichtreferenz.
   - Arme: (a) CRE1, isoliert auf TRAIN-Familien trainiert (Checkpoint-SHA festhalten); (b) deterministische Normalisierung, also Wochentags-Ordinalzahlen plus Orts-Glossar, **nur aus TRAIN-Familien** abgeleitet; (c) unverändertes CRE1; (d) JACCARD und HYBRID_LEX.
   - Vorbedingung wie M5: L ≥ 80 %, S ≥ 90 % auf VAL.
2. **Ergebnis-Gabelung:**
   - (a) oder (b) besteht auf VAL → Freeze, dann den **fremden** SEALED einmal öffnen (Format `EXP-20-M5-FOREIGN-SEALED-HOLDOUT-SPEC`).
   - Beide scheitern → **Besitzerentscheidung**, ob ein vortrainiertes lokales Embedding-Modell geprüft werden darf (Lizenz, Größe, RAM; LiteRT-LM exportiert heute **keine** Embeddings). Ohne Entscheidung keine Implementierung.
3. **M6-B4-Vorprüfung, ohne neuen Speicher:**
   - Testfälle „Ich bin in Wien“ → „Seit Dienstag wohne ich in Graz“ gegen die **vorhandene** EWM-Gültigkeitslogik (`Ewm1Workspace` / `Ewm1EvidenceWorkspace`).
   - Prüfen, ob nur der betroffene Slot zeitlich begrenzt wird und unbeteiligte Fakten bleiben.
   - Vergleich: deterministische Änderungsdetektion gegen EMA-Kontext. Ein Überraschungssignal führt **nie** zu Training oder Faktenänderung.
4. **M6-B2/B3 erst nach bestandenem Punkt 2:**
   - EMA mit gelernten Ereignisdetektoren auf dem neuen Basisstrom gegen A, A0 und D (deterministischer Automat).
   - Ablationen: diagonal, gleiche τ, ohne Semantik, ohne Zeit.
   - Bewertung auf einem fremden Holdout.
   - CTRNN nur, wenn es gegenüber EMA `SUPERIOR` ist.

## 5. Ressourcen (Host, bereits gemessen; kein Fold7)

| Messgröße | Wert |
|---|---|
| CRE1 je Abfrage ohne Cache | ~285 µs |
| Hybrid + CRE1 | ~550 µs |
| Jaccard | ~36 µs |
| CRE1-Snapshot | 4 210 736 B |
| Schlüssel je Erinnerung | 256 B |
| EMA-Zustandsupdate | p99 0,56 µs, 1 120 B |
| Pipeline je Turn | ~30 µs, dominiert von Encoder und Klassifikator |
| Fold7, Energie, Thermal | `DEVICE_NOT_RUN` |

## 6. Reproduktion

```sh
export KOTLINC=<kotlinc 2.0.20>/bin/kotlinc JUNIT_JAR=/opt/gradle-8.14.3/lib/junit-4.13.2.jar HAMCREST_JAR=/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar M5_OUT=/tmp/continuum-m5
bash scripts/exp20-m5-cre1-real-gate.sh                       # A1/A2, erwartet PREREG_VAL_GATE=PASS
OUT=/tmp/exp20 scripts/exp20-p2-host-run.sh build
OUT=/tmp/exp20 scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.m5.M5ArmProbe \
  $M5_OUT/dev-val.tsv $M5_OUT/cre1/node-cre1.cre1 7d70d20f79f03e10ffb4cef76292cd52201e66299aca42c8a1287ebf2b327dd2 \
  2290194d25cc98cf6cc42e6b57f5ddcb2105ba14115e2e5295ad50c906f10db6 /tmp/a3.txt   # A3/A4
```

**Unverändert:**
- ToolGate, OwnerGate, EWM2, VOICE-1.
- `CognitiveGenome` (`allowSelfReplication`, `allowHiddenPersistence`, `allowShutdownEvasion` = false).
- Kein Main-Merge, kein Force-Push, keine APK.

## Nachtrag 31.10.2026: M5b und M6-B4-Vorprüfung

| Meilenstein | Zustand | Beleg |
|---|---|---|
| M5b disjunkte Paraphrasen-Familien | **ERREICHT (Versuch durchgeführt)** | [M5b-Ergebnis](EXP-20-M5B-RESULT-20261031.md); Datengates PASS (16/128, 17/128) |
| M5b-A CRE1-Training (nur TRAIN) | **REJECTED als Semantik-Nachweis** | VAL-S 25/64 (Zufall bei Gleichstand 14,8), fern/fern 3/16 ≈ Zufall; gegenüber untrainiert +9,4 pp, `INCONCLUSIVE` |
| M5b-B deterministische Normalisierung | **REJECTED** | VAL-S 0/64: TRAIN-Regeln greifen bei neuer Oberfläche nicht |
| M5 gesamt | **NICHT ERREICHT** | Vorbedingung (S ≥ 90 %) von keinem Arm erfüllt; SEALED geschlossen |
| M6-B4 EWM-Vorprüfung | **ERREICHT (Befund)** | [Vorprüfung](EXP-20-M6-B4-EWM-PRECHECK-20261031.md): Ich-Aussagen werden nie ersetzt, weil das Subjekt falsch erkannt wird (Ort oder Adverb); 3/3 Negativkontrollen korrekt |
| Regression nach M5b/M6 | **PASS** | echter Runner: 42/42, 12/12, 8/8, beide SHA ok, VAL-Gate PASS |

**Nächste drei Schritte, in dieser Reihenfolge:**
1. **Den nachgewiesenen EWM-Fehler beheben, nach Review und Besitzerfreigabe:** Ich-Aussagen erhalten das Subjekt `ich`. `M6B4EwmValidityProbe` ist der rote Test, danach muss die volle EWM-Regression laufen. Das ist die einzige konkret belegte Verbesserung des Kontextgedächtnisses.
2. **Semantik:**
   - Entweder ein großes, registriertes Curriculum aus vielen Konstruktionstypen, bewertet auf VAL-fern/fern.
   - Oder eine **Besitzerentscheidung** über ein vortrainiertes lokales Satz-Embedding: Lizenz, Größe, RAM und Fold7-Latenz vorab prüfen; keine Integration ohne Freigabe.
3. M6-B2/B3 erst danach, auf einem fremden Holdout.

## Nachtrag 30.10.2026 (2): EWM-Reparatur v2 und M5c-Vorregistrierung

| Meilenstein | Zustand | Beleg |
|---|---|---|
| M4 Host-Forschungslabor | ERREICHT | unverändert |
| M5 Robuste Semantik | **NICHT ERREICHT** | Abnahme unverändert; SEALED geschlossen |
| M5c Grenzanalyse + Vorregistrierung | **ERREICHT (nur Vorregistrierung)** | [M5c-Vorregistrierung](EXP-20-M5C-PREREGISTRATION-20261030.md): CRE1 kann keine Rollen binden (gemessen, auch trainiert) und Wörter nicht ohne Vorwissen übertragen; noch kein M5c-Lauf |
| M6-B4 deterministische Ersetzung | **PATCHKANDIDAT, nicht übernommen** | [EWM-Fix v2](EXP-20-M6-B4-EWM-FIX-20261030.md): Orakel mit 50 976 Paaren, P 0,219 → 1,000, R 0,387 → 1,000 (Entwicklungsorakel, kein Holdout); Host-Regression 788/788; Branch `claude/ewm-owner-subject-fix-kxrxy2` @ `a3705004` |
| M6 neuronale Kontextschicht (B2/B3, Predictive Coding) | **GESPERRT** | Kein semantischer Basisstrom (M5 offen). Die deterministische Baseline lässt keinen gemessenen Bedarf erkennen. |

**Nächste Schritte, in dieser Reihenfolge:**
1. **Besitzer:** Review des EWM-Patches.
2. **Danach:**
   - Einen fremd formulierten Ersetzungs-Holdout vorab registrieren und einmal öffnen.
   - Android-Build und Fold7-Prüfung.
3. **M5c-Spur G-role:** Arme B und C gegen Bag-Referenzen auf neuem TRAIN/DEV/VAL.
4. **Besitzerentscheidung zu Arm D:** vortrainierter lokaler Satz-Encoder, nach Lizenz-, Größen- und Fold7-Prüfung.
5. **EWM-Versionierung:** Gültigkeitsbeginn und Einschränkung früherer Aussagen (R6), ohne zweiten Speicher.
