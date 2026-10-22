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

**Zulässige Aussage**
- „LM1 schlägt CDX1 auf ungesehenen Dokumenten“ nur, wenn der TEST-BPB **jedes** Kandidaten-Seeds kleiner ist als der **jedes** CDX1-Seeds.
- Gegenüber dem n-Gramm gilt dieselbe Regel. Verliert LM1 gegen das n-Gramm, wird das als negatives Ergebnis berichtet.

**Nicht behauptet:** allgemeine Sprachkompetenz, Faktenwissen, Befolgen von Anweisungen. 500 KB Text reichen dafür nicht.
