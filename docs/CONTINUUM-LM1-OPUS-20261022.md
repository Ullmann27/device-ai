# LM1 – eigener trainierbarer lokaler Sprachkern (Opus, 22.10.2026)

Grundlage ist der Intelligenz-Masterauftrag und die Spezifikation in PR #69, Abschnitt 6.5.

- Zweig: `opus/continuum-language-core-20261022`, Draft-PR #75.
- Basis: PR #57 (`1d92db03`); dort ist CDX1 bytegleich mit Release 0.3.12.
- Nur Forschung: keine App- oder Chat-Anbindung, keine Änderung an SWB1-, CDX1- oder CSX1-Gewichten.

## 1. Bausteine (`app/src/main/java/at/heinz/deviceai/learning/lm/`)

| Datei | Inhalt |
|---|---|
| `LmTensor.kt` | Reverse-Mode-Autodiff über dichte Float-Matrizen. Jede Operation hat einen exakten analytischen Rückwärtsschritt; Matmul, RMSNorm, GELU/SiLU, kausales Softmax, minGRU-Scan und Kreuzentropie sind mit finiten Differenzen geprüft. |
| `LmBpeTokenizer.kt` | Byte-BPE: Ids 0–255 sind Bytes, danach folgen Merges, BOS und EOS. Merges werden deterministisch und nur aus TRAIN gelernt; das Vokabular ist per SHA-256 gebunden. |
| `LmModel.kt` | Zwei Architekturen mit gebundener Ausgabe-Einbettung: **Transformer** (kausale Multi-Head-Attention, Pre-RMSNorm, gelernte Positionen) und **minGRU** (gegatete lineare Rekurrenz nach Feng et al. 2024, konstanter Zustand je Schritt). Dazu AdamW mit Clipping, Warmup und Kosinus sowie datenparalleles Training. |
| `LmCheckpointStore.kt` | Checkpoints mit Konfiguration, Tokenizer-Hash, Metadaten und SHA-256. Die Ablage `<sha256>.lm1` ist inhaltsadressiert, atomar, wird nie überschrieben und bei jedem Lesen geprüft. |
| `LmCorpus.kt` | Datenschutzfilter, Segmentierung (höchstens 255 Bytes) und Aufteilung nach Dokument. |

**JUnit 7/7 (`LmCoreTest`):**
- jede Operation und beide Gesamtmodelle per Finite-Differenzen-Prüfung;
- Kausalität: Zukunftstokens ändern frühere Vorhersagen nicht;
- Tokenizer-Rundlauf mit Umlauten und Emoji;
- beschädigte oder fremde Checkpoints werden abgelehnt;
- beide Architekturen lernen eine Phrase;
- Determinismus.

## 2. Korpus und Einfrieren (vor jedem Modelllauf festgelegt)

- **Quelle:** alle Markdown-Dokumente am Commit `1d92db03`, ohne `docs/evidence/` (Protokolle) und `THIRD_PARTY_NOTICES.md` (Lizenztexte).
  - Es sind 97 Dokumente mit Text.
  - Der Inhalt ist Projektdokumentation des Besitzers und damit rechtlich nutzbar.
- **Datenschutzfilter:**
  - Code-Blöcke und HTML-Kommentare entfernt;
  - Links → `<link>`, E-Mail → `<e-mail>`, Hex ≥ 10 → `<hash>`, Zahlen ≥ 7 Stellen → `<zahl>`;
  - Namen des Besitzers → „der Besitzer“ bzw. `<konto>`.
  - Nachkontrolle: 0 Namen und 0 Links übrig; die 2 übrigen `@` sind `"$@"` und `@Ignore`.
- **Einheiten:** Absätze von höchstens 255 UTF-8-Bytes, an Leerzeichen geteilt.
  - Jedes Segment wird von seinem Anfang aus bewertet, ohne Vorkontext.
  - Alle Modelle sehen dieselben Segmente; das passt auch zur 512-Byte-Grenze von CDX1.
- **Aufteilung nach Dokument:** erstes Byte von SHA-256(Pfad) mod 10 → 0 = TEST, 1 = VALIDATION, sonst TRAIN.

| Aufteilung | Dokumente | Segmente | Bytes | SHA-256 |
|---|---:|---:|---:|---|
| TRAIN | 77 | 2.520 | 428.014 | `f73f286c…` |
| VALIDATION | 10 | 485 | 82.396 | `5f671c8b…` |
| **TEST (eingefroren)** | **10** | **400** | **67.245** | **`0d11bf88f23df23c001c3e77616fe87567b3fa2a03e44f597bee9ef48b787188`** |

## 3. Messgröße

**Bits pro Byte (BPB)** über die Inhaltsbytes. Grundlage ist die Summe der −log₂-Wahrscheinlichkeiten aller Inhaltstoken bzw. -bytes; EOS zählt nicht. Kleiner ist besser.

Zusätzlich werden erfasst:
- Parameterzahl;
- Trainingszeit;
- ms je Token für Inferenz auf einem Thread (JVM x86_64, nicht ARM64);
- Token je Byte.

## 4. Kandidaten und Auswahlregel (vorab festgelegt)

**Baselines**
- Unigramm (add-one).
- Kneser-Ney-Byte-n-Gramm, interpoliert, n = 2…7. **n wird auf VALIDATION gewählt.**
- **CDX1** (`TinyContextDecoder`, unverändert, nur über die öffentliche API):
  - Seeds 1–3, Lernrate 0,035 wie in der App;
  - 1–6 Epochen in Korpora von höchstens 8 KB;
  - die Epochenzahl wird auf VALIDATION gewählt.

**LM1-Familien**
- Architektur T (Transformer) oder G (minGRU).
- Größe S (d = 32, 2 Schichten, 2 Köpfe, MLP 128) oder M (d = 64, 2 Schichten, 4 Köpfe, MLP 256).
- Kontext 256 Token.
- Vokabular 512 mit Seeds 1–3; Ablation für M mit Vokabular 258 (nur Bytes) und 1024, je Seed 1.

**Training**
- 8 Epochen, Batch 16 Segmente;
- AdamW mit Lernrate 0,002, Warmup 100, Kosinus bis 10 %, Weight Decay 0,1, Clipping 1;
- nach jeder Epoche ein Checkpoint und VALIDATION-BPB.

**Auswahl (nur VALIDATION)**
- Je Familie gilt die Epoche mit dem kleinsten mittleren VALIDATION-BPB über die Seeds.
- Vorab-Kandidat für TEST ist die Familie mit dem kleinsten Mittel. Ihre 3 Checkpoints dieser Epoche gehen einmal in den TEST.
- Zum Vergleich werden ebenfalls einmal gemessen:
  - die beste Familie der anderen Architektur;
  - das beste n-Gramm;
  - CDX1 mit der gewählten Epochenzahl, 3 Seeds.

**Klarstellung vom 22.10.2026, 15:58 UTC** (eingetragen, bevor die Ablationsläufe begonnen hatten): TEST-Kandidaten kommen nur aus den Familien mit 3 Seeds (Vokabular 512). Die Ablationen mit Vokabular 258 und 1024 laufen mit einem Seed. Sie zeigen die Richtung, sind aber keine TEST-Kandidaten.

**Zulässige Aussage**
- „LM1 schlägt CDX1 auf ungesehenen Dokumenten“ nur, wenn der TEST-BPB **jedes** Kandidaten-Seeds kleiner ist als der **jedes** CDX1-Seeds.
- Gegenüber dem n-Gramm gilt dieselbe Regel. Verliert LM1 gegen das n-Gramm, wird das als negatives Ergebnis berichtet.

**Nicht behauptet:** allgemeine Sprachkompetenz, Faktenwissen, Befolgen von Anweisungen. 500 KB Text reichen dafür nicht.

## 5. Typisierter Dialog Reasoner ↔ Coding-Agent (`.github/continuum/reasoner-dialog.mjs`)

**Nachrichten und Rollen**

| Typ | Rolle | Inhalt (streng geprüft) |
|---|---|---|
| `GOAL` | Besitzer | Ziel als Text (bis 600 Zeichen), optional Beispiele |
| `HYPOTHESIS` | Reasoner | Behauptung, Modul, Export, Begründung |
| `PLAN` | Reasoner | 1–8 Schritte, Fähigkeit `node-contract-test` |
| `TEST_REQUEST` | Reasoner | Bezug auf **die** Hypothese; 1–4 Fälle mit höchstens 3 einfachen Argumenten; JSON bis 300 Zeichen; Budget bis zum Besitzerlimit |
| `RECEIPT` | **nur der Runner** | Ergebnis des echten Jobs, unabhängig nachgeprüft; Urteil `CONFIRMED`, `REFUTED` oder `INCONCLUSIVE` |
| `CRITIQUE` | Reasoner | Bezug auf eine Quittung; **das Urteil muss dem der Quittung entsprechen** |
| `REVISE` | Reasoner | Bezug auf die verworfene Hypothese, Änderung |
| `ABSTAIN` | Reasoner | Begründung |

**Ausführung**
- Ein `TEST_REQUEST` läuft über den **bestehenden** `code-test`-Job aus PR #57: kanonisches Backlog, Teamleiter, Vertragstest im Git-Worktree, zweiter unabhängiger Worktree, Patch.
- Der Runner prüft jede Job-Quittung selbst:
  - Job, Commit und Auftrag (Modul, Export, Fall-Hash) stimmen;
  - die Patch-Bytes entsprechen dem SHA;
  - drei Läufe mit Exitcode 0 und ausgeführten Tests, die unabhängige Wiederholung mit mindestens so vielen Tests;
  - keine Behauptung von Merge, Installation oder Gerät.
- `GENERATED_TESTS_FAILED` widerlegt die Hypothese. Jeder andere Ausgang ist `INCONCLUSIVE`, also kein Befund.

**Veröffentlichte Fähigkeit:** Vor jedem Auftrag fragt der Runner den Critic des Coding-Agenten mit genau dem Vertrag, der erzeugt würde (`capabilityCheck`). Der Critic schützt unter anderem den eigenen Kontrollcode des Agenten. Liegt ein Auftrag außerhalb der Fähigkeit, wird er abgelehnt, bevor ein Job läuft.

**Integrität**
- Das Journal ist append-only, als JSON-Zeilen geführt und hash-verkettet.
- Quittungen tragen ein HMAC mit einem Schlüssel aus dem Runner-Zustand, den der Reasoner nicht bekommt.
- Erkannt werden Änderungen, falsche Rollen, gebrochene Ketten und gefälschte Quittungen.
- Grenze: Der HMAC schützt gegen einen Reasoner, der nur den Nachrichtenkanal hat. Gegen Code mit denselben Dateirechten schützt er nicht. Auf dem Gerät laufen App und Termux getrennt.

**Ablauf**
- Pro Runde ist höchstens ein Auftrag offen (Backpressure); die Zahl der Runden ist begrenzt.
- Nach einem Absturz wird fortgesetzt: Die Job-ID steht vor der Ausführung im Journal, und der Replay-Schutz des Jobs verhindert einen zweiten Lauf.
- Entschiedene Jobs werden im Backlog geschlossen. Sonst würde der Teamleiter eine widerlegte Hypothese vor der nächsten erneut versuchen; dieser Fehler ist im ersten End-to-End-Test aufgetreten und behoben.
- Ergebnis ist höchstens ein Vertragstest-Patch **zur Prüfung durch den Besitzer**. Es gibt kein Merge, kein Push, keine Installation und keine Promotion.

**Referenz-Reasoner (`hypothesis-reasoner.mjs`)**
- Beispiele werden aus deutschem Text gelesen, z. B. `(5) → 15` oder `aus "a" wird "A"`.
- Exportierte Funktionen werden mit Stelligkeit indiziert.
- Die Rangfolge ist lexikalisch (IDF, einfache Stammformen) und austauschbar.
- Nach einer Widerlegung gilt: nächster Kandidat. Bei `INCONCLUSIVE` gilt: Enthaltung ohne blinden Wiederholungsversuch.

**Tests (`node --test .github/continuum/reasoner-dialog.test.mjs`): 8/8**, davon 4 mit echten Coding-Jobs in einem Fixture-Repository:
- Schema, Rollen und Grenzen, einschließlich der Fähigkeit;
- Journal-Manipulation;
- Quittungsprüfung;
- Beispielparser und Index;
- **Widerlegung → Revision → Bestätigung** (12 Einträge, Quelle unverändert);
- ein **lügender Reasoner**, der eine widerlegte Quittung als bestätigt ausgibt, wird gestoppt;
- **Absturz nach der Ausführung**: die Fortsetzung läuft ohne zweite Ausführung (`replayed:true`);
- Rundenlimit führt zur Enthaltung.

## 6. Pilotstudien des Dialogs (`scripts/reasoner-dialog-pilot.mjs`, je einmal ausgeführt)

**Aufbau**
- 8 deutsche Ziele zu echten Continuum-Funktionen. Die erwarteten Werte stammen aus den Funktionen selbst und wurden vor dem Lauf geprüft.
- 3 adversariale Ziele: falsches Beispiel, kein Beispiel, Beispiel mit Liste.
- Suchraum: alle 75 Exporte von 13 abhängigkeitsfreien Modulen.
- Bedingungen: lexikalische Rangfolge oder zufällige Rangfolge (Seed). Je Bedingung eine Schleife mit 3 Runden; der Einzelversuch ist Runde 1.

**Pilot 1** (`eafbb2c1`, Belege in `docs/evidence/lm1-20261022/dialog-pilot1/`)
- Ergebnis lexikalisch 4/8, zufällig 1/8.
- 9 von 30 Jobs endeten `INCONCLUSIVE`. Grund: Der Critic des Coding-Agenten verweigert Vertragstests auf seinen eigenen Kontrollmodulen (`repair-*`, `kotlin-tokenizer`, `cefr-*`), und der Reasoner hatte solche vorgeschlagen.
- Das ist ein **Fehler des Instruments**. Behoben wurde er durch die veröffentlichte Fähigkeit in `66568cd2`, vorab eingetragen als Pilot 2.

**Pilot 2** (korrigiertes Protokoll, gleiche Aufgaben, Belege in `docs/evidence/lm1-20261022/dialog-pilot2/`)

| Bedingung | richtig nach 1. Versuch | richtig nach ≤ 3 Runden | ungewollte Bestätigung | adversarial korrekt enthalten | Jobs |
|---|---:|---:|---:|---:|---:|
| lexikalisch | **5/8** | 5/8 | **0** | **3/3** | 17 |
| zufällig | 1/8 | 1/8 | 0 | 3/3 | 25 |

**Befunde** (n = 8, eine Pilotstudie, keine statistische Aussage)
1. Das Protokoll trägt auf echtem Code.
   - Jede Bestätigung beruht auf einem unabhängig wiederholten Vertragstest.
   - Es gab **0 ungewollte Bestätigungen** und **0 Erfolgsbehauptungen ohne Quittung**.
   - Alle adversarialen Ziele enden in Enthaltung.
   - Ein Job dauert in der Fixture etwa 0,5 s.
2. **Die begrenzte Selbstkritik-Schleife brachte hier nichts** (5/8 → 5/8, bei 9 zusätzlichen Jobs). Die Misserfolge sind systematische Rangfehler und keine knappen Fehlgriffe. Die Hypothese, dass die Schleife hilft, ist für diese Aufgaben **nicht bestätigt**.
3. **Haupt-Fehlerquelle ist deutsch–englische Benennung:** „kanonisch“ ↔ `canonical`, „Befund“ ↔ `finding`, „Schlüsselwort“ ↔ `isKeyword`. Das Ziel von `isKeyword` liegt zudem im geschützten Modul und ist deshalb nicht prüfbar.
   - Hier könnte ein gelernter Sprachkern messbar helfen, etwa mit zweisprachigen Assoziationen aus den Projektdokumenten.
   - Das ist die nächste falsifizierbare Frage.

## 7. Auswahllauf: Ergebnis auf VALIDATION und Sicherung (Stand 22.10.2026, vor TEST)

**Lauf**
- Befehl: `LmExperiment <repo> select store=<dir>` mit Code-Stand `9630fd49`, JAR-SHA-256 `27f84b6b…706a`, 4 Threads.
- Start 15:40:34Z, Ende 17:02:38Z, **Exitcode 0**. Vollständiges Protokoll: `docs/evidence/lm1-20261022/select-log.txt` (Endung `.txt`, weil `*.log` im Repository ignoriert wird).
- Zusammenfassung mit `summarize.py` im selben Ordner: `select-summary.txt`.

**Sicherung aller Trainingsstände**
- Alle 128 Checkpoints (16 Läufe × 8 Epochen) liegen auf dem Datenzweig `opus/lm1-checkpoints-20261022`, Commit `977293a7`.
- Jeder Dateiname ist der SHA-256 des Inhalts; vor dem Push wurden alle 128 nachgerechnet (0 Abweichungen).
- `docs/evidence/lm1-20261022/CHECKPOINTS.tsv` nennt je Checkpoint Familie, Seed, Epoche, VALIDATION-BPB und Größe.
- Das Training ist bei gleicher Threadzahl deterministisch; jeder Stand ist also auch aus dem Code reproduzierbar.

**Ergebnisse auf VALIDATION** (kleiner ist besser; Mittel ± Standardabweichung über die Seeds)

| Familie | Parameter | beste Epoche | VALIDATION-BPB | Seeds | ms/Token | Token/Byte |
|---|---:|---:|---:|---:|---:|---:|
| Unigramm | – | – | 5,063 | – | – | – |
| CDX1 | 7.746 | 3 | 4,541 ± 0,038 | 3 | – | – |
| LM1-TS-v512 | 49.632 | 8 | 3,157 ± 0,010 | 3 | 0,059 | 0,555 |
| LM1-TM-v512 | 148.416 | 8 | 2,901 ± 0,008 | 3 | 0,174 | 0,555 |
| LM1-GS-v512 | 41.504 | 8 | 2,709 ± 0,012 | 3 | 0,058 | 0,555 |
| **LM1-GM-v512** | 132.160 | 8 | **2,225 ± 0,007** | 3 | 0,139 | 0,555 |
| Ablation LM1-TM-v258 | 132.160 | 8 | 3,648 | 1 | 0,151 | 1,000 |
| Ablation LM1-GM-v258 | 115.904 | 8 | 2,246 | 1 | 0,098 | 1,000 |
| Ablation LM1-TM-v1024 | 181.184 | 8 | 2,604 | 1 | 0,173 | 0,427 |
| Ablation LM1-GM-v1024 | 164.928 | 8 | 2,172 | 1 | 0,153 | 0,427 |
| **Kneser-Ney-7-Gramm** | – | – | **1,963** | – | – | – |

**Befunde (nur VALIDATION, noch keine TEST-Aussage)**
1. **minGRU schlägt den Transformer** bei gleicher Größe deutlich: S um 0,45 BPB, M um 0,68 BPB, mit weniger Parametern und ähnlicher Geschwindigkeit.
2. **Das Kneser-Ney-7-Gramm ist auf VALIDATION noch besser als jedes LM1-Modell** (1,963 gegen 2,225). Bei 428 KB Trainingstext merkt sich ein Zählmodell wiederkehrende Projektbegriffe besser als ein kleines neuronales Netz nach 8 Epochen.
3. **Alle Familien verbessern sich noch in Epoche 8.** Die vorab festgelegten 8 Epochen begrenzen also das Ergebnis; die Auswahl bleibt trotzdem regelkonform.
4. **Vokabular (je 1 Seed, nur Richtung):** Beim minGRU-M hilft ein größeres Vokabular leicht (258 → 512 → 1024: 2,246 → 2,225 → 2,172). Der reine Byte-Transformer (v258) ist deutlich schlechter (3,648), weil sein Kontext von 256 Token dann nur 255 Bytes umfasst und er pro Byte rechnen muss.

**Anwendung der vorab festgelegten Auswahlregel (Abschnitt 4)**
- TEST-Kandidat ist die beste 3-Seed-Familie: **LM1-GM-v512, Epoche 8**, Checkpoints
  `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`,
  `ad117ed5901a991941c220482cc486bf4e4dfdf9173e7f66f9cff320ec814eea`,
  `44c4d4f30fac9428117837a86b9ddec97994e277aef802342c762acb0bc3507e`.
- Vergleich (ebenfalls einmal):
  - beste Familie der anderen Architektur: **LM1-TM-v512, Epoche 8** (`e77c39a0…`, `3ff091fa…`, `5abe2416…`);
  - bestes n-Gramm: **KN-7**;
  - **CDX1 mit 3 Epochen**, Seeds 1–3.
- Der einmalige TEST-Befehl lautet damit:
  `LmExperiment <repo> final store=<checkpoints> kn=7 cdx1epochs=3 ckpt=512:<GM s1> ckpt=512:<GM s2> ckpt=512:<GM s3> ckpt=512:<TM s1> ckpt=512:<TM s2> ckpt=512:<TM s3>`
- **Stand dieses Commits: TEST nicht ausgeführt.**

### 7a. Einmalige TEST-Auswertung (23.10.2026, 01:38–01:41 UTC)

**Freigabe und Vorprüfung**
- Ausgeführt nach ausdrücklicher Freigabe des Besitzers (Master-Fortsetzungsauftrag M4/M5 vom 23.10.2026, Abschnitt 3: „Prüfe zunächst, ob der vorregistrierte finale TEST tatsächlich noch unberührt ist. Falls dies bestätigt ist: … Führe die einmalige abschließende TEST-Auswertung durch.“).
- **Unberührtheit vorher geprüft:**
  - Nur der Modus `final` bewertet TEST, und er bricht ab, wenn der TEST-Hash nicht dem eingefrorenen entspricht.
  - In keinem Protokoll, keiner Belegdatei und keinem Ausgabeverlauf dieser Sitzungen gibt es einen TEST-Wert. `testBPB=<Zahl>` kam vor diesem Lauf nirgends vor; der frühere Startversuch wurde vor der Ausführung abgewiesen.
  - Das NF-Labor schließt alle LM1-TEST-Dokumente aus.
  - Die sechs Checkpoints wurden gegen ihren Inhalts-Hash geprüft: 0 Abweichungen.
- Befehl wie vorregistriert, mit derselben JAR wie der Auswahllauf (SHA-256 `27f84b6b…706a`, Code `9630fd49`).
- Exitcode 0. TEST-Hash bestätigt (`0d11bf88…7188`).
- Protokoll: `docs/evidence/lm1-20261022/final-test-20261023.txt` (SHA-256 `d9a02f68…cc46`).
- Die bei der Gelegenheit neu berechneten VALIDATION-Werte stimmen exakt mit dem Auswahllauf überein; die Checkpoints sind also unverändert geladen.

**Ergebnis auf TEST** (10 ungesehene Dokumente, 400 Segmente, 67.245 Bytes; kleiner ist besser)

| Modell | TEST-BPB je Seed | Mittel | zum Vergleich VALIDATION |
|---|---|---:|---:|
| Unigramm | – | 4,987 | 5,063 |
| CDX1, 3 Epochen | 4,278 · 4,285 · 4,320 | 4,295 | 4,541 |
| LM1-TM-v512, Epoche 8 | 3,051 · 3,056 · 3,057 | 3,055 | 2,901 |
| **LM1-GM-v512, Epoche 8** (Kandidat) | **2,443 · 2,440 · 2,435** | **2,439** | 2,225 |
| **Kneser-Ney-7** | – | **2,150** | 1,963 |

**Zulässige Aussagen nach Abschnitt 4**
1. **„LM1 schlägt CDX1 auf ungesehenen Dokumenten“: gestützt.**
   - Jeder Kandidaten-Seed liegt unter jedem CDX1-Seed (2,443 < 4,278).
   - Der Abstand beträgt 1,86 BPB, also 43 % weniger Bits je Byte.
2. **Gegen das Kneser-Ney-7-Gramm: LM1 verliert, ein negatives Ergebnis.**
   - Kein Seed erreicht das n-Gramm; der Rückstand beträgt 0,289 BPB.
   - Auf dieser Datenmenge (428 KB) ist ein Zählmodell weiterhin das stärkere Sprachmodell.
3. **minGRU schlägt den Transformer auch auf TEST**, in allen Seeds, um 0,62 BPB.
4. **Verallgemeinerung:** Alle Modelle sind auf TEST schlechter als auf VALIDATION (LM1-GM +0,21, KN-7 +0,19, LM1-TM +0,15). Die TEST-Dokumente sind schwerer; die Reihenfolge der Modelle bleibt gleich.

**Nicht behauptet**
- Allgemeines Sprachverständnis, Faktenwissen, Befolgen von Anweisungen, Programmierfähigkeit.
- Gemessen ist nur die Vorhersage des nächsten Bytes auf Projekttext.

**Konsequenz für den nächsten Zyklus**
- Die beste Architektur ist LM1-G (minGRU). Sie verbesserte sich in Epoche 8 noch, und ein größeres Vokabular half: 1024 statt 512 ergab 2,172 statt 2,225 auf VALIDATION, bei einem Seed.
- Der nächste Zyklus hat eigene, neu eingefrorene Daten und wird vor jedem Lauf registriert. Er prüft, ob mehr Daten, längeres Training und ein größeres Vokabular den Abstand zum n-Gramm schließen. Dazu kommt ein Hybrid aus n-Gramm und neuronalem Modell (NF-6b).
- Der LM1-TEST ist damit verbraucht und wird nicht erneut für Entscheidungen verwendet.

### 7b. Vorregistrierung Schritt 7: Textgenerierung und Anweisungsverarbeitung auf neuen Daten (eingetragen 23.10.2026, vor jeder Bewertung)

Auftrag: M4/M5, Abschnitt 3, Punkt 7 („Prüfe danach auf getrennten neuen Testdaten die Fähigkeiten zur Textgenerierung und Anweisungsverarbeitung“). Code: `app/src/test/java/at/heinz/deviceai/learning/lm/LmStep7.kt` (Commit `86d4fd52`, Tests 7/7). Der LM1-TEST wird nicht berührt.

**Modelle (fest)**
- LM1-GM-v512, Epoche 8, Seeds 1–3: die TEST-Checkpoints `7a0af1e9…`, `ad117ed5…`, `44c4d4f3…`.
- LM1-TM-v512, Epoche 8, Seeds 1–3: `e77c39a0…`, `3ff091fa…`, `5abe2416…`.
- Baselines aus TRAIN: Kneser-Ney 7, Unigramm, CDX1 mit 3 Epochen (Seeds 1–3), häufigstes Wort, Wort-Bigramm.

**Neue Daten NEW (eingefroren)**
- 25 Markdown-Dokumente, die es beim Korpus-Commit `1d92db03` noch nicht gab. Sie sind per Blob-ID in `docs/evidence/lm1-20261022/step7-new-docs.tsv` festgelegt.
- Ausgeschlossen sind dieser Bericht und der NF-Bericht. Beide stammen vom Experimentator und beschreiben genau diese Modelle; sie werden noch bearbeitet, und diese Vorregistrierung steht darin.
- Datenschutzfilter und Segmentierung sind dieselben wie bei LM1.
- Entfernt wurden 10 Segmente, die ein 50-Byte-Fenster mit dem LM1-Korpus teilen. Wiederholungen gab es keine.
- Ergebnis: **1.278 Segmente, 242.488 Bytes, SHA-256 `2027cbbc274b3c76742da45d03d668195173b9487972d2adf4451b6214cd156b`.**

**Anweisungspaare (eingefroren)**
- 200 Paare in 5 Familien zu je 40:
  - erstes oder letztes Wort einer Liste;
  - Groß- oder Kleinschreibung;
  - größere oder kleinere Zahl;
  - „Ist m größer/kleiner als n?“ mit ja/nein;
  - Nachfolger oder Vorgänger einer Zahl.
- Beide Anweisungen eines Paares teilen Eingabe und Kandidaten, verlangen aber verschiedene Antworten.
- **SHA-256 `6672edb9671cccc02e5b25cedbfb049637c7faa153ee194c704cf5a60c10cade`.**

**Messungen**
- **A1:** BPB auf NEW.
- **A2:** gieriges nächstes Wort an 2.000 festen Wortanfängen.
  - Eine Position ist ein Wortanfang nach genau einem Leerzeichen, mit mindestens 16 Bytes Kontext.
  - Die Eingabe endet vor dem Leerzeichen. So zerlegt der BPE-Tokenizer sie genau wie im Training.
  - Richtig ist eine Position nur, wenn „ Wort“ vollständig erzeugt wird und das Wort danach endet.
- **A3:** 40 Anfänge von NEW-Segmenten (20–60 Zeichen), je 120 Bytes Fortsetzung.
  - Erzeugt wird gierig und mit t = 0,8, top-k 40, festem Seed je Anfang.
  - Kennzahlen: Anteil bekannter Wörter aus TRAIN, Anteil der 24-Byte-Fenster, die wörtlich in TRAIN vorkommen (Kopieren), und wiederholte Wort-3-Gramme.
  - Als Referenz dient der echte Text.
- **B:** Gewählt wird der Kandidat mit den wenigsten bedingten Bits nach der Anweisung. Je Paar wird gezählt:
  - **both:** beide Anweisungen richtig;
  - **swapped:** beide so beantwortet, als wären die Anweisungen vertauscht.
  - Wer die Anweisung nicht liest, kann nie beide richtig haben.

**Entscheidungsregeln**
- **A1:**
  - „LM1 schlägt CDX1 auf NEW“, wenn jeder GM-Seed unter jedem CDX1-Seed liegt.
  - Gegen KN-7 gilt dieselbe Regel; sonst wird ein negatives Ergebnis berichtet.
- **A2:** „LM1 sagt das nächste Wort besser vorher als X“ (X = KN-7 oder Wort-Bigramm) gilt, wenn für jeden GM-Seed
  - mehr Positionen nur LM1 richtig hat als nur X,
  - und der exakte McNemar-Test p < 0,01 ergibt.
- **B:** „LM1 verarbeitet diese Anweisungen“ gilt, wenn für jeden GM-Seed
  - both − swapped ≥ 20 ist (10 % der Paare),
  - und der einseitige Vorzeichentest p < 0,01 ergibt.
  - Dieselbe Regel wird zum Vergleich für TM angewandt.
- **Gegenprobe des Messgerüsts:** Unigramm, KN-7 (6 Byte Kontext) und CDX1 (8 Byte) können die Anweisung nicht sehen und müssen both = swapped = 0 liefern. Sonst ist das Gerüst fehlerhaft und B ungültig.
- **A3** ist beschreibend und hat keine Regel. Flüssigkeit, Zusammenhang oder Verständnis werden nicht behauptet.

**Erwartung (vorab)**
- A1 wie auf TEST.
- B auf Zufallsniveau: LM1 hat nur Projektdokumentation und nie Anweisungen gesehen.

**Grenzen**
- Es gibt einen einzigen Lauf.
- Die Anweisungsfamilien sind einfach und künstlich.
- NEW stammt aus demselben Projekt; es ist zeitlich und inhaltlich verschoben, aber kein fremder Text.

### 7c. Ergebnis Schritt 7 (einmaliger Lauf 23.10.2026, 06:49–06:54 UTC, Code `6494699a`)

- Protokoll: `docs/evidence/lm1-20261022/step7-run-20261023.txt`, Startskript `run-step7.sh.txt`.
- Exitcode 0. NEW- und Anweisungs-Hash entsprechen der Vorregistrierung.
- Die sechs Checkpoints wurden vor dem Lauf gegen ihren Inhalts-Hash geprüft.
- **Gegenprobe des Messgerüsts bestanden:** Unigramm, KN-7 und CDX1 liefern both = swapped = 0.

**A1 – BPB auf NEW (1.278 Segmente, 242.488 Bytes; kleiner ist besser)**

| Modell | NEW-BPB je Seed | zum Vergleich TEST |
|---|---|---:|
| Unigramm | 5,265 | 4,987 |
| CDX1, 3 Epochen | 4,688 · 4,700 · 4,778 | 4,295 |
| LM1-TM-v512 | 3,227 · 3,228 · 3,233 | 3,055 |
| **LM1-GM-v512** | **2,695 · 2,696 · 2,694** | 2,439 |
| **Kneser-Ney 7** | **2,489** | 2,150 |

- **„LM1 schlägt CDX1 auf NEW“: gestützt**, mit rund 2,0 BPB Abstand.
- **Gegen KN-7 verliert LM1 erneut, ein negatives Ergebnis.** Der Abstand ist aber kleiner: 0,206 statt 0,289 auf TEST.
- Alle Modelle sind auf den neueren Dokumenten schlechter als auf TEST; die Reihenfolge bleibt gleich.

**A2 – gieriges nächstes Wort (2.000 Positionen)**

| Modell | richtig |
|---|---:|
| häufigstes Wort | 3,8 % |
| Wort-Bigramm | 4,1 % |
| KN-7 | 2,3 % |
| LM1-GM (Seeds 1–3) | 0,45 · 0,55 · 0,60 % |
| LM1-TM (Seeds 1–3) | 0,15 · 0,20 · 0,05 % |

- **Nicht gestützt, gegen beide Baselines** (McNemar p < 10⁻⁷ zugunsten der Baselines).
- **Nachträglich, nicht vorregistriert** (`step7-posthoc-20261023.txt`, `LmStep7Posthoc.kt`, LM1-GM Seed 1). Der erste gierige Token an den 2.000 Wortgrenzen war:
  - 664-mal ein Leerzeichen mit Satz- oder Formatzeichen (etwa `` ` ``);
  - 432-mal eine Fortsetzung des aktuellen Worts;
  - 406-mal ein Satzzeichen;
  - nur 322-mal ein neues Wort;
  - 84-mal ein Zeilenumbruch, 48-mal das Segmentende, 44-mal eine Ziffer.
- Ohne Segmentende bleibt es bei 9 von 2.000 Treffern. **Das Ende-Symbol ist also nicht die Ursache.** LM1 wählt an Wortgrenzen selten ein neues Wort, und wenn, meist das falsche.

**A3 – Generierung (40 Anfänge, 120 Bytes; beschreibend)**

| Erzeugung | bekannte Wörter | 24-Byte-Fenster aus TRAIN | wiederholte 3-Gramme | mittlere Länge |
|---|---:|---:|---:|---:|
| echter Text | 0,78 | 0,004 | 0,005 | 97 B |
| KN-7 gierig | 0,97 | 0,070 | 0,080 | 120 B |
| KN-7, t = 0,8 | 0,88 | 0,012 | 0,000 | 120 B |
| LM1-GM gierig | 0,91–0,95 | 0,000 | 0,68–0,77 | 102–114 B |
| LM1-GM, t = 0,8 | 0,68–0,69 | 0,000–0,003 | 0,000 | 52–76 B |
| LM1-TM gierig | 0,57–0,80 | 0,000 | 0,26–0,40 | 70–96 B |

- **LM1-GM gierig** gerät in Schleifen, z. B. „dieselbe Quellen. ⏎ - Ein Quellen. ⏎ - Ein Quellen. …“.
- **LM1-GM mit Stichprobe:**
  - rund zwei Drittel bekannte Wörter;
  - dazu erfundene Zusammensetzungen wie „gesabhängiger“ oder „Fehlermeldet“;
  - kein wörtliches Kopieren aus TRAIN.
- **KN-7** erzeugt fast nur bekannte Wörter, kopiert aber gierig 7 % seiner 24-Byte-Fenster wörtlich aus TRAIN.
- Flüssigkeit, Zusammenhang und Verständnis werden ausdrücklich nicht behauptet; es gibt keine Regel.

**B – Anweisungen (200 Paare; Zufallsniveau der Einzelantworten 0,433)**

| Modell | both | swapped | Einzeltreffer |
|---|---|---|---:|
| Unigramm, KN-7, CDX1 (blind) | 0 | 0 | 0,40–0,45 |
| LM1-GM | 1 · 0 · 1 | 0 · 0 · 0 | 0,415–0,430 |
| LM1-TM | 1 · 3 · 3 | 2 · 6 · 2 | 0,388–0,398 |

- **„LM1 verarbeitet diese Anweisungen“: nicht gestützt**, für GM wie für TM. Das wurde so erwartet.

**Gesamtbefund Schritt 7**
- LM1 ist ein Byte-Vorhersager für Projekttext. Es ist besser als CDX1 und schlechter als ein Zählmodell.
- **Textgenerierung:** nicht brauchbar. Gierig entstehen Schleifen, mit Stichprobe viele Kunstwörter.
- **Anweisungsverarbeitung:** nicht vorhanden.
- Für den nächsten Zyklus folgt daraus:
  - mehr und andere Daten;
  - ein Training gezielt auf den Rest des n-Gramm-Modells (Meilenstein D);
  - für Antworten im Produkt nicht freie Generation, sondern belegte Bausteine (Gedächtnis mit Quellen, typisierte Aufgaben).

## 8. CCC1-Laufzeit (`.github/continuum/ccc1-*.mjs`) – getestet

- Bausteine: EnvelopeV1/GoalSpec (`ccc1-envelope`), hash-verkettetes Journal mit HMAC (`ccc1-journal`), Fähigkeitsprüfung (`ccc1-gate`), Quittungsprüfer (`ccc1-verifier`), Wiederanlauf (`ccc1-recovery`), Dialogsteuerung (`ccc1-orchestrator`), Zielinterpretation mit austauschbarem Sprachkern (`ccc1-interpreter`).
- **Tests `node --test .github/continuum/ccc1.test.mjs`: 14/14**, davon 9 mit dem **echten** Coding-Job (kanonisches Backlog, isolierte Worktrees, unabhängiger zweiter Lauf) in einem Wegwerf-Repository:
  - Schema, Rollen, Versionen; Journal erkennt Änderungen, gefälschte MACs, Kettenbruch, Bindungswechsel; Duplikate werden einmal angewendet;
  - Gate: geschützte Module bleiben gesperrt (auch mit Besitzerfreigabe), nicht freigegebene Module brauchen eine Freigabe, Frist/Runden/CPU-Budget;
  - Quittungsprüfer gegen echte Patch-Bytes, Läufe, festes Testprofil und genauen Auftrag;
  - Ende-zu-Ende: Widerlegung → Revision → Bestätigung; lügender Reasoner → FAILED; manipulierte Job-Quittung → nie bestätigt; ins Journal gefälschte Quittung → Stopp;
  - doppelte Zustellung, widersprüchliches Duplikat, verschobener Checkout (STALE_BASE), unerlaubte Aktion (CAPABILITY_DENIED), Freigabe erteilt/verweigert;
  - Rundenlimit → Enthaltung, Zuglimit → Pause, harte Journalgrenze (96 Einträge) → Abbruch;
  - **SIGKILL** vor/nach der Ausführung und mitten im Journal-Schreibvorgang → Fortsetzung ohne zweite Ausführung;
  - **Stromausfall mitten im Job:** keine Wiederholung ohne Besitzer; nach Freigabe endet die Aufgabe trotzdem verifiziert.
- **Gefundene und behobene Fehler:**
  1. Das Journal nahm beim Schreiben Nachrichten mit fremder Commit-Bindung an und war danach unlesbar → wird jetzt vor dem Schreiben abgewiesen.
  2. Ein gefälschtes Journal führte zu einer Ausnahme → jetzt sauberer Stopp `STOPPED_CORRUPT_JOURNAL`.
  3. Vorübergehende Worker-Blockaden (Teamleiter-Sperre, Backlog-Sperre) beendeten die Aufgabe → jetzt `WAITING_FOR_WORKER`, der Auftrag bleibt offen, nichts wird ausgeführt.
  4. **Produktfehler im Coding-Agenten:** Die gemeinsame Build-Sperre (`shared-build-lease.mjs`) bleibt nach einem Absturz für immer liegen; auf Android blockiert das jeden weiteren Job. Neu: `releaseDeadContinuumBuildLease` – nur nach ausdrücklicher Besitzerfreigabe und nur, wenn der Halteprozess auf diesem Gerät nachweislich nicht mehr existiert (Test 6/6). Alter allein gibt weiterhin nichts frei.
- `reasoner-dialog.mjs` (Abschnitt 5) bleibt unverändert und getestet; CCC1 ist der Nachfolger mit vollständiger Spezifikation.
