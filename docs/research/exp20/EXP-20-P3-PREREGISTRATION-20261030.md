# EXP-20 · P3 · Encoder-Vorbedingung und DG-SDR-Pilot · Vorregistrierung

**Stand:** 30.10.2026. Committet **vor** dem ersten Lauf. **Getrennt von P2:** Der P2-Holdout ist verbraucht und wird hier nicht verwendet. P2-Encoder, -Gewichte und -Regeln bleiben unverändert. Neuer Datensatz mit neuem Seed, neuen Namen und neuen Satzrahmen.

## Frage

Bevor DG-SDR (k-WTA) oder dendritisches Zwei-Strom-Gating entwickelt wird, ist zu klären: **Führt einer der real vorhandenen lokalen Encoder Paraphrasen zusammen und trennt dabei ähnliche, aber verschiedene Erinnerungen?** Ein Gate oder eine Zufallsprojektion kann fehlende Bedeutung nicht erzeugen.

## Reale Schnittstellen (geprüft auf `ed408a7` plus diesem Branch)

- `TrainableSubwordEncoder.representation(text)`:
  - Zeichen-2–4-Gramme → 256 Hash-Buckets × 16 Dimensionen, 4 113 Parameter.
  - Im Repo **nur untrainiert** (Seed 73, wie `ContinuumLearningCore` ohne Modelldatei). Ein trainierter Stand existiert nur auf dem Gerät: `NOT_AVAILABLE`.
- `Cre1InferenceEncoder.embed(text, side)` / `score(q, m)`:
  - 16 384 × 32 plus Projektionen q und m, Snapshot 4 210 736 B.
  - Der Gerätestand (`learning-core-v2/cre1-research/cre1-research-v1.cre1`) ist nicht im Repo: `NOT_AVAILABLE`.
  - Verwendet wird der **reproduzierbar erzeugte Parity-Checkpoint** aus `node .github/continuum/cre-parity.mjs prepare`: Seed 42, 24 Curriculum-Paare, 2 Epochen, 8 Schritte. SHA-256 `2290194d25cc98cf6cc42e6b57f5ddcb2105ba14115e2e5295ad50c906f10db6`; nicht committet, weil reproduzierbar.
  - Dazu `createResearch(73)` als untrainierte Kontrolle.
- Es gibt **keine** Gemma-Embedding-API und keine zweite Modelllaufzeit.

## Datensatz `docs/evidence/exp20-p3-20261030/p3-dataset.tsv`

- **Umfang:** 120 unabhängige Gruppen (Seed 2026103003). SHA-256 wird im Commit dieses Dokuments festgehalten.
- **Kandidaten je Gruppe:**
  - die Abfrage;
  - die wahre Erinnerung „{Titel} {Name} {Aufgabe} {Tag} {Ort}.“;
  - drei Near Misses: andere Person, anderer Ort, anderer Tag;
  - vier unverwandte Erinnerungen.
- **Typ L** (Gruppen 0–59): Die Abfrage nennt Person, Tag und Ort wörtlich.
- **Typ S** (Gruppen 60–119): Tag und Ort nur als Synonym, z. B. „zum Wochenausklang“ = am Freitag, „unten im Haus“ = im Keller.

## Verfahren

| Verfahren | Bedeutung |
|---|---|
| `A0_P2_FEATURES` | Kosinus im eingefrorenen 8-dim P2-Merkmalsraum (A0-Kontrolle, unverändert) |
| `JACCARD` | einfacher deterministischer Ähnlichkeitsvergleich auf Wortmengen |
| `SUBWORD` / `SUBWORD_SDR` | Kosinus bzw. DG-SDR auf `representation()` |
| `CRE1` / `CRE1_SDR` | `score(q, m)` bzw. DG-SDR auf `embed(q, "q")` / `embed(m, "m")` |
| `CRE1_UNTRAINED` | Kontrolle |

**DG-SDR:** feste dünne Zufallsprojektion auf 2 048 Bits (Fan-in 4, Vorzeichen ±1, Seed 2048041), k-WTA mit k = 41 (2,0 %), Ähnlichkeit = Überlappung/k. Kein Lernen.

## Metriken und Regeln

- **Streng:** korrekt nur, wenn die wahre Erinnerung **echt** höher bewertet ist als alle 7 anderen. Gleichstand zählt als Fehler.
- **Zusätzlich:** Near-Miss-Verwechslung (ein Near Miss ≥ wahr), Verwechslung mit Unverwandtem, Gleichstände, MRR (pessimistisch), Vollkosten pro Abfrage ohne Cache (Host-JVM, Blockmittel), Speicher analytisch.
- **Vorbedingung `PASS`:** Top-1 ≥ 80 % auf L **und** auf S, **und** Near-Miss-Verwechslung ≤ 10 % auf S.
- **Geplante Vergleiche:** SUBWORD_SDR–SUBWORD, CRE1_SDR–CRE1, CRE1–JACCARD, SUBWORD–JACCARD; exakter McNemar auf Gruppen, Holm über 4, Bootstrap-KI, MDE +5 pp. Urteile wie P2.
- **DG-SDR hat Zusatznutzen** nur bei `SUPERIOR` gegenüber dem eigenen Basis-Encoder **und** wenn die SDR-Variante zugleich Top-1 ≥ JACCARD + 5 pp erreicht. Sonst `REJECTED`.
- **Dendritisches Gating** (A ohne Gate / B deterministischer Kontextfilter / C begrenzte multiplikative Kontextverstärkung) wird **nur** ausgewertet, wenn mindestens ein Encoder die Vorbedingung besteht. Andernfalls bleibt es `NOT_STARTED` mit dem Grund „semantische Vorbedingung nicht erfüllt“; der Versuchsaufbau wird dokumentiert.
- **Keine produktive Integration**, unabhängig vom Ergebnis: Der Datensatz ist synthetisch und stammt vom selben Autor.

## Lauf

```sh
node --input-type=module -e "import('./.github/continuum/cre-parity.mjs').then(m=>m.prepare('<dir>'))"   # node-cre1.cre1
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.p3.P3Probe run docs/evidence/exp20-p3-20261030/p3-dataset.tsv <dir>/node-cre1.cre1 docs/evidence/exp20-p3-20261030/run
```

## Prüfsummen bei Registrierung

- `p3-dataset.tsv` SHA-256 `104cd929e2b4f3ced6a96a7a5cac29a78f0c0366dcb4d10a73b4824abf2f2939`
- `P3Probe.kt` SHA-256 `14d466686a87cb4a4cd4b4c81448bd8783f7e294cd23f55807e41e6096b08225`, Blob `8e322898122f14e3fea3994ab848fed6c6616e30`
- `app/src/main/java/at/heinz/deviceai/learning/TrainableSubwordEncoder.kt` (unverändert) Blob `6eec44f7183720f96721db755258bda144f0b620`
- `app/src/main/java/at/heinz/deviceai/learning/Cre1InferenceEncoder.kt` (unverändert) Blob `e811681f96f2683481b73c3e43ab103cf78bd108`
